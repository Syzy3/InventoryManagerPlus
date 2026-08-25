package dev.inventorymanagerplus.inventory;

import dev.inventorymanagerplus.preset.MatchMode;
import dev.inventorymanagerplus.preset.Preset;
import dev.inventorymanagerplus.preset.PresetSlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns "current inventory + preset" into a minimal list of swaps.
 *
 * <h2>Why every operation is a swap</h2>
 *
 * The hard requirement of this mod is that it must never lose an item. Rather than trying to check
 * for item loss after the fact, the planner makes it structurally impossible: the only operation it
 * can emit is {@link Move}, and a Move is executed as a <em>swap</em> of two slots. A swap is a
 * permutation of the inventory's contents, so the multiset of items before and after is identical,
 * by construction. There is no code path here that can drop, delete, merge away or overwrite a
 * stack, which means an unmanaged item can be pushed around but never destroyed — no matter how
 * badly a preset is configured, how full the inventory is, or where the algorithm gets interrupted.
 *
 * <h2>The algorithm</h2>
 *
 * Three passes over a simulated copy of the inventory:
 *
 * <ol>
 *   <li><b>Lock what is already right.</b> Any managed slot whose current contents already satisfy
 *       its requirement is marked final and never touched. This is what keeps re-applying a preset
 *       (and Auto Sort in particular) from generating pointless traffic.</li>
 *   <li><b>Fill the rest.</b> For each remaining managed slot, pick the best matching stack from
 *       an unlocked slot and swap it in. Swapping is what makes the "slot 0 and slot 1 hold each
 *       other's items" case collapse to a single operation instead of a shuffle through a temp
 *       slot — and it is also what handles cycles of any length for free, because after each swap
 *       the simulation is re-read rather than relying on a precomputed assignment.</li>
 *   <li><b>Honour intentional blanks.</b> Managed slots the player marked empty get their contents
 *       swapped into a genuinely free slot. If there is no free slot the item stays exactly where
 *       it is: an incomplete preset is always preferable to a dropped item.</li>
 * </ol>
 *
 * <p>Each pass marks its target slot as locked before moving on, so a later step can never undo an
 * earlier one. That bounds the number of moves at one per managed slot and guarantees termination.
 *
 * <p>This class is pure: it reads a snapshot and returns a plan. It performs no clicks, touches no
 * Minecraft state, and can be unit-tested with a plain array of stacks.
 */
public final class ArrangementPlanner {

    /** A single swap between two inventory indices. */
    public record Move(int from, int to) {
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

    /**
     * @param inventory snapshot of inventory indices 0..40; the array is copied, not mutated
     */
    public static Plan plan(ItemStack[] inventory, Preset preset,
                            HolderLookup.Provider registries) {
        final MatchMode mode = preset.matchMode();
        final ItemStack[] sim = inventory.clone();

        // TreeMap so the plan is deterministic: same inventory + same preset => same clicks.
        // Determinism matters for debugging desyncs and for the "did anything change?" check
        // Auto Sort relies on.
        final Map<Integer, PresetSlot> managed = new TreeMap<>();
        for (var e : preset.slots().entrySet()) {
            if (InvSlots.isManageable(e.getKey()) && e.getKey() < sim.length) {
                managed.put(e.getKey(), e.getValue());
            }
        }

        final List<Move> moves = new ArrayList<>();
        final List<PresetSlot> missing = new ArrayList<>();
        final List<Integer> blockedBlanks = new ArrayList<>();
        final Set<Integer> locked = new HashSet<>();

        // ---- Pass 1: everything already in place stays in place ------------------------------
        for (var e : managed.entrySet()) {
            PresetSlot spec = e.getValue();
            if (spec.isBlank()) {
                continue; // handled in pass 3
            }
            if (ItemMatcher.matches(spec, sim[e.getKey()], mode, registries)) {
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

            int source = findBestSource(sim, spec, mode, registries, locked, managed, target);
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

        // ---- Pass 3: clear slots the player marked as intentionally empty ---------------------
        for (var e : managed.entrySet()) {
            int target = e.getKey();
            if (!e.getValue().isBlank() || locked.contains(target)) {
                continue;
            }
            if (sim[target].isEmpty()) {
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
    private static int findBestSource(ItemStack[] sim, PresetSlot spec, MatchMode mode,
                                      HolderLookup.Provider registries,
                                      Set<Integer> locked, Map<Integer, PresetSlot> managed,
                                      int target) {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < sim.length; i++) {
            if (i == target || locked.contains(i) || !InvSlots.isManageable(i)) {
                continue;
            }
            if (!ItemMatcher.matches(spec, sim[i], mode, registries)) {
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

    private static void swap(ItemStack[] sim, int a, int b) {
        ItemStack tmp = sim[a];
        sim[a] = sim[b];
        sim[b] = tmp;
    }
}
