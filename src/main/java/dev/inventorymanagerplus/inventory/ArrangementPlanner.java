package dev.inventorymanagerplus.inventory;

import dev.inventorymanagerplus.preset.ItemCategory;
import dev.inventorymanagerplus.preset.Preset;
import dev.inventorymanagerplus.preset.PresetSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns "current inventory + preset" into a list of swaps, merges and (only if the player turned
 * Drop on) drops.
 *
 * <h2>Why items can't be lost</h2>
 *
 * Swaps and merges only ever move items between slots of the player's own inventory, so the
 * items before and after are the same. A swap is never planned into an armour slot with
 * something that can't be worn there, which is the one place the game would refuse a click
 * half-way through. The only step that removes items is a drop, and the planner emits drops only
 * for slots the player marked "keep empty", and only when Drop is switched on.
 *
 * <h2>The algorithm</h2>
 *
 * Passes over a simulated copy of the inventory:
 *
 * <ol>
 *   <li><b>Lock what is already right.</b> Any managed slot whose current contents already satisfy
 *       its requirement is never swapped out. Armour with Curse of Binding is locked too, since
 *       the game won't let it come off.</li>
 *   <li><b>Fill the rest.</b> For each remaining managed slot, swap in the best matching stack
 *       from an unlocked slot. After each swap the simulation is re-read, which handles chains of
 *       any length without a precomputed assignment.</li>
 *   <li><b>Top up.</b> Each filled slot takes more of the same item from unlocked stacks until it
 *       is full.</li>
 *   <li><b>Honour "keep empty".</b> Whatever sits in a slot marked empty is first stacked onto the
 *       same item elsewhere; the rest is dropped (Drop on) or moved to a free slot. With no free
 *       slot it stays put.</li>
 * </ol>
 *
 * <p>Each pass locks its target before moving on, so a later step never undoes an earlier one,
 * which bounds the plan and guarantees it ends. Apply re-plans after the moves land and finishes
 * anything the first plan couldn't foresee.
 *
 * <p>This class is pure: it reads a snapshot and returns a plan. It performs no clicks and touches
 * no Minecraft state.
 */
public final class ArrangementPlanner {

    /** What a {@link Move} does. */
    public enum Kind {
        /** Swap the two slots. */
        SWAP,
        /** Pour as much of {@code from} onto the same item in {@code to} as fits. */
        MERGE,
        /** Throw the whole stack in {@code from} on the ground. {@code to} is unused. */
        DROP
    }

    /**
     * One step of a plan, performed with ordinary clicks. Swaps and merges only move items
     * between slots. Drops are the one step that takes items out of the inventory, and the
     * planner only emits them when the player has turned Drop on.
     */
    public record Move(int from, int to, Kind kind) {
        public Move(int from, int to) {
            this(from, to, Kind.SWAP);
        }

        public static Move merge(int from, int to) {
            return new Move(from, to, Kind.MERGE);
        }

        public static Move drop(int from) {
            return new Move(from, -1, Kind.DROP);
        }

        public boolean isMerge() {
            return kind == Kind.MERGE;
        }

        public boolean isDrop() {
            return kind == Kind.DROP;
        }
    }

    /**
     * @param moves        swaps to perform, in order
     * @param missing      requirements no matching item could be found for
     * @param blockedBlanks slots the player wanted empty but which could not be cleared safely
     */
    public record Plan(List<Move> moves, List<PresetSlot> missing, List<Integer> blockedBlanks) {

        public boolean isNoOp() {
            return moves.isEmpty();
        }

        public boolean isComplete() {
            return missing.isEmpty() && blockedBlanks.isEmpty();
        }
    }

    private ArrangementPlanner() {
    }

    /** Plans without dropping anything. */
    public static Plan plan(ItemStack[] inventory, Preset preset) {
        return plan(inventory, preset, false);
    }

    /**
     * @param inventory snapshot of inventory indices 0..40; the array is copied, not mutated
     * @param dropFromEmptySlots whether items left in a slot marked empty are thrown out, after
     *                           first being stacked onto the same item elsewhere
     */
    public static Plan plan(ItemStack[] inventory, Preset preset, boolean dropFromEmptySlots) {
        final ItemStack[] sim = inventory.clone();

        // TreeMap so the plan is deterministic: same inventory + same preset => same clicks.
        // Determinism matters for debugging desyncs and for the "did anything change?" check
        // Auto Sort relies on.
        final Map<Integer, PresetSlot> managed = new TreeMap<>();
        for (var e : preset.slots().entrySet()) {
            int slot = e.getKey();
            if (!InvSlots.isManageable(slot) || slot >= sim.length) {
                continue;
            }
            // "Keep empty" only exists for the hotbar and main inventory. Older presets may still
            // carry one on armour or the off-hand; it is ignored rather than acted on.
            if (e.getValue().isBlank() && slot >= InvSlots.STORAGE_SIZE) {
                continue;
            }
            managed.put(slot, e.getValue());
        }

        final List<Move> moves = new ArrayList<>();
        final List<PresetSlot> missing = new ArrayList<>();
        final List<Integer> blockedBlanks = new ArrayList<>();
        final Set<Integer> locked = new HashSet<>();

        // Armour with Curse of Binding can't be taken off, so it is never part of a move.
        for (int i = InvSlots.ARMOR_START; i <= InvSlots.ARMOR_END && i < sim.length; i++) {
            if (EnchantmentHelper.has(sim[i], EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
                locked.add(i);
            }
        }

        // ---- Pass 1: everything already in place stays in place ------------------------------
        for (var e : managed.entrySet()) {
            PresetSlot spec = e.getValue();
            if (spec.isBlank()) {
                continue; // handled in pass 3
            }
            if (ItemMatcher.matches(spec, sim[e.getKey()], e.getKey())) {
                locked.add(e.getKey());
            }
        }

        // ---- Pass 2: pull in what is missing --------------------------------------------------
        for (var e : managed.entrySet()) {
            int target = e.getKey();
            PresetSlot spec = e.getValue();
            if (spec.isBlank() || locked.contains(target)) {
                continue;
            }

            int source = findBestSource(sim, spec, locked, managed, target);
            if (source < 0) {
                // The player simply does not have this item. Not an error: arrange what we can and
                // leave the slot alone. Reported so the UI can say which items were unavailable.
                missing.add(spec);
                continue;
            }

            moves.add(new Move(source, target));
            swap(sim, source, target);
            locked.add(target);
        }

        // ---- Pass 2b: top up filled slots from other stacks of the same item ------------------
        // A preset slot is "steak goes here", so it should hold as much steak as a slot can: two
        // stacks of 4 become one stack of 8, and a 10 + 60 pair becomes 64 + 6. Only unlocked
        // slots donate, so a slot the preset already filled is never robbed to fill another.
        for (var e : managed.entrySet()) {
            int target = e.getKey();
            if (e.getValue().isBlank() || !locked.contains(target)) {
                continue;
            }
            topUp(sim, target, locked, managed, moves);
        }

        // ---- Pass 3: clear slots the player marked as intentionally empty ---------------------
        for (var e : managed.entrySet()) {
            int target = e.getKey();
            if (!e.getValue().isBlank() || locked.contains(target)) {
                continue;
            }
            // First stack it onto the same item somewhere else, so e.g. diamonds sitting in an
            // empty-marked slot join the diamonds already in the inventory.
            pourOut(sim, target, managed, moves);
            if (sim[target].isEmpty()) {
                locked.add(target);
                continue;
            }

            if (dropFromEmptySlots) {
                moves.add(Move.drop(target));
                sim[target] = ItemStack.EMPTY;
                locked.add(target);
                continue;
            }

            int free = findFreeSlot(sim, locked, managed);
            if (free < 0) {
                // Inventory is full and every free slot is spoken for. Leaving the item where it
                // is, is the only non-destructive option.
                blockedBlanks.add(target);
                continue;
            }
            moves.add(new Move(target, free));
            swap(sim, target, free);
            locked.add(target);
        }

        return new Plan(List.copyOf(moves), List.copyOf(missing), List.copyOf(blockedBlanks));
    }

    /**
     * Picks the stack that should be moved into {@code target}.
     *
     * <p>Locked slots are off limits, which is what stops the planner from robbing a slot it
     * already satisfied. Among the rest, {@link ItemMatcher#score} prefers unmanaged sources and
     * larger stacks.
     */
    private static int findBestSource(ItemStack[] sim, PresetSlot spec,
                                      Set<Integer> locked, Map<Integer, PresetSlot> managed,
                                      int target) {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < sim.length; i++) {
            if (i == target || locked.contains(i) || !InvSlots.isManageable(i)) {
                continue;
            }
            // Armour being worn and the off-hand item are left alone unless the preset manages
            // that slot: a spare chestplate in the inventory must not pull the one off your body.
            if (i >= InvSlots.STORAGE_SIZE && !managed.containsKey(i)) {
                continue;
            }
            if (!ItemMatcher.matches(spec, sim[i], target)) {
                continue;
            }
            // A swap sends the target's current item back into slot i. If i is an armour slot,
            // that item has to be wearable there, or the game refuses the click halfway through.
            if (InvSlots.isArmor(i) && !sim[target].isEmpty()
                    && !ItemCategory.fitsArmorSlot(sim[target].getItem(), i)) {
                continue;
            }
            int score = ItemMatcher.score(sim[i], i, target, !managed.containsKey(i));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /**
     * An empty slot that no preset requirement depends on. Managed slots are excluded even when
     * currently empty, because parking an item there would immediately violate the preset.
     */
    private static int findFreeSlot(ItemStack[] sim, Set<Integer> locked, Map<Integer, PresetSlot> managed) {
        for (int i = 0; i < InvSlots.STORAGE_SIZE; i++) {
            if (locked.contains(i) || managed.containsKey(i)) {
                continue;
            }
            if (sim[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Pours matching stacks into {@code target} until it is full or nothing matching is left.
     *
     * <p>Smallest donors go first, so topping up empties whole slots where it can instead of
     * nibbling a little off many stacks.
     */
    private static void topUp(ItemStack[] sim, int target, Set<Integer> locked,
                              Map<Integer, PresetSlot> managed, List<Move> moves) {
        while (true) {
            ItemStack into = sim[target];
            if (into.isEmpty() || into.getCount() >= into.getMaxStackSize()) {
                return;
            }
            int donor = -1;
            for (int i = 0; i < sim.length; i++) {
                if (i == target || locked.contains(i) || !InvSlots.isManageable(i)) {
                    continue;
                }
                if (i >= InvSlots.STORAGE_SIZE && !managed.containsKey(i)) {
                    continue; // never take from worn armour or the off-hand unless managed
                }
                if (sim[i].isEmpty() || !ItemStack.isSameItemSameComponents(into, sim[i])) {
                    continue;
                }
                if (donor < 0 || sim[i].getCount() < sim[donor].getCount()) {
                    donor = i;
                }
            }
            if (donor < 0) {
                return;
            }
            int n = Math.min(sim[donor].getCount(), into.getMaxStackSize() - into.getCount());
            ItemStack grown = into.copy();
            grown.grow(n);
            ItemStack shrunk = sim[donor].copy();
            shrunk.shrink(n);
            sim[target] = grown;
            sim[donor] = shrunk.isEmpty() ? ItemStack.EMPTY : shrunk;
            moves.add(Move.merge(donor, target));
        }
    }

    /**
     * Stacks the contents of {@code from} onto matching stacks elsewhere until it is empty or
     * nothing has room. Preset slots for the same item are filled first, then the fullest
     * unmanaged stacks. Other slots marked empty are never used as a destination.
     */
    private static void pourOut(ItemStack[] sim, int from, Map<Integer, PresetSlot> managed,
                                List<Move> moves) {
        while (!sim[from].isEmpty()) {
            ItemStack stack = sim[from];
            int best = -1;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < sim.length; i++) {
                if (i == from || !InvSlots.isManageable(i) || sim[i].isEmpty()) {
                    continue;
                }
                PresetSlot spec = managed.get(i);
                if (spec != null && spec.isBlank()) {
                    continue;
                }
                if (sim[i].getCount() >= sim[i].getMaxStackSize()
                        || !ItemStack.isSameItemSameComponents(stack, sim[i])) {
                    continue;
                }
                int score = (spec != null ? 1000 : 0) + sim[i].getCount();
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            if (best < 0) {
                return;
            }
            int n = Math.min(stack.getCount(), sim[best].getMaxStackSize() - sim[best].getCount());
            ItemStack grown = sim[best].copy();
            grown.grow(n);
            ItemStack rest = stack.copy();
            rest.shrink(n);
            sim[best] = grown;
            sim[from] = rest.isEmpty() ? ItemStack.EMPTY : rest;
            moves.add(Move.merge(from, best));
        }
    }

    private static void swap(ItemStack[] sim, int a, int b) {
        ItemStack tmp = sim[a];
        sim[a] = sim[b];
        sim[b] = tmp;
    }
}
