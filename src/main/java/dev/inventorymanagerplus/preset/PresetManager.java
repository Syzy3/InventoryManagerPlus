package dev.inventorymanagerplus.preset;

import dev.inventorymanagerplus.InventoryManagerPlus;
import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.config.Storage;
import dev.inventorymanagerplus.inventory.ArrangementPlanner;
import dev.inventorymanagerplus.inventory.CreativeSupplier;
import dev.inventorymanagerplus.inventory.InventorySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Owns the preset list and the "apply this preset" entry point. */
public final class PresetManager {

    private final List<Preset> presets = new ArrayList<>();
    private String activePresetId;

    public void load() {
        presets.clear();
        presets.addAll(Storage.loadPresets());

        // Pick up where the last session left off: the same active preset, and its Auto Sort
        // switch as it was. Only the active preset can be sorting, so any other preset that was
        // saved with Auto on (from an older version) is switched off.
        String saved = Config.get().activePresetId;
        activePresetId = byId(saved).isPresent() ? saved : null;
        for (Preset p : presets) {
            if (!p.id().equals(activePresetId)) {
                p.setAutoSort(false);
            }
        }

        InventoryManagerPlus.LOGGER.info("Loaded {} preset(s)", presets.size());
    }

    public void save() {
        Storage.savePresets(presets);
    }

    public List<Preset> all() {
        return presets;
    }

    public Optional<Preset> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return presets.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public void add(Preset preset) {
        presets.add(preset);
        save();
    }

    /** Adds a copy of a preset directly below it in the list. */
    public Preset duplicate(Preset preset) {
        Preset copy = preset.duplicate();
        int at = presets.indexOf(preset);
        presets.add(at < 0 ? presets.size() : at + 1, copy);
        save();
        return copy;
    }

    /** Moves a preset up (-1) or down (+1) in the list. */
    public void move(Preset preset, int delta) {
        int from = presets.indexOf(preset);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= presets.size()) {
            return;
        }
        presets.remove(from);
        presets.add(to, preset);
        save();
    }

    /** The other preset already using a hotkey, if any. */
    public Optional<Preset> withHotkey(int key, Preset except) {
        return presets.stream()
                .filter(p -> p != except && p.hotkey() == key && key >= 0)
                .findFirst();
    }

    public void remove(Preset preset) {
        presets.remove(preset);
        if (preset.id().equals(activePresetId)) {
            setActive(null);
        }
        save();
    }

    /** The preset most recently applied; the one Auto Sort and the "apply active" keybind use. */
    public Preset active() {
        return byId(activePresetId).orElse(null);
    }

    public void setActive(Preset preset) {
        activePresetId = preset == null ? null : preset.id();
        if (!java.util.Objects.equals(Config.get().activePresetId, activePresetId)) {
            Config.get().activePresetId = activePresetId;
            Storage.saveConfig();
        }
    }

    /**
     * Turns Auto Sort on or off for a preset. Turning it on also makes the preset the active one
     * (the one Auto Sort follows) and turns it off on every other preset, so at most one switch
     * ever reads ON and that is the one actually sorting.
     */
    public void setAutoSort(Preset preset, boolean on) {
        if (on) {
            for (Preset p : presets) {
                p.setAutoSort(false);
            }
            setActive(preset);
        }
        preset.setAutoSort(on);
        save();
    }

    /**
     * Extra passes Apply makes after its moves land. Each pass re-reads the real inventory and
     * plans again, which catches anything the first plan could not see coming: a click the
     * server refused, an item picked up mid-apply, a stack that merged differently. Two passes
     * are plenty in practice; the limit only stops a preset that can never be satisfied from
     * looping.
     */
    private static final int FOLLOW_UP_PASSES = 3;

    /**
     * Applies a preset: acquire in Creative if needed, plan, then queue the moves.
     *
     * @return a short human-readable result for the action bar
     */
    public Component apply(Minecraft mc, Preset preset, boolean announce) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return Component.literal("No player");
        }
        if (player.containerMenu != player.inventoryMenu) {
            return Component.literal("Close the open container first");
        }

        // Auto Sort follows the preset it was switched on for. Applying a different one turns
        // the old one's switch off, so its button never claims to be sorting when it isn't.
        Preset previous = active();
        if (previous != null && previous != preset && previous.autoSort()) {
            previous.setAutoSort(false);
            save();
        }
        setActive(preset);

        // Creative acquisition happens before planning so the newly created stacks are visible to
        // the planner in the same operation, which is what makes "click preset -> get items ->
        // arrange them" feel like one action instead of two.
        if (player.isCreative()) {
            CreativeSupplier.supplyMissing(mc, preset);
        }

        var plan = planNow(mc, player, preset);

        if (plan.isNoOp()) {
            return Component.literal(plan.isComplete()
                    ? preset.name() + ": already arranged"
                    : preset.name() + ": nothing to move" + problems(plan, 0));
        }

        int dropped = countDrops(plan);
        InventoryManagerPlus.ops().submit(plan.moves(), preset.name(), false,
                () -> followUp(mc, preset, announce, 1, dropped));

        // Shown while the moves run; the full result replaces it once they finish.
        return Component.literal(preset.name() + ": applying " + plan.moves().size() + " move(s)...");
    }

    /** Re-checks after a batch of moves and queues whatever is still left to do. */
    private void followUp(Minecraft mc, Preset preset, boolean announce, int pass, int dropped) {
        LocalPlayer player = mc.player;
        if (player == null || player.containerMenu != player.inventoryMenu) {
            return;
        }
        var plan = planNow(mc, player, preset);
        if (plan.isNoOp() || pass > FOLLOW_UP_PASSES) {
            if (announce) {
                InventoryManagerPlus.status(mc, "Applied " + preset.name() + problems(plan, dropped));
            }
            return;
        }
        int droppedNow = dropped + countDrops(plan);
        InventoryManagerPlus.ops().submit(plan.moves(), preset.name(), false,
                () -> followUp(mc, preset, announce, pass + 1, droppedNow));
    }

    /** How many names the result message lists before cutting off with "...". */
    private static final int MAX_NAMES = 3;

    /**
     * The tail of a result message: which items were missing, slots that couldn't be emptied,
     * and how many stacks were thrown out. Empty when everything went to plan.
     */
    private static String problems(ArrangementPlanner.Plan plan, int dropped) {
        StringBuilder sb = new StringBuilder();
        if (!plan.missing().isEmpty()) {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            for (PresetSlot slot : plan.missing()) {
                names.add(nameOf(slot));
            }
            sb.append("  •  Missing: ");
            int n = 0;
            for (String name : names) {
                if (n == MAX_NAMES) {
                    sb.append("...");
                    break;
                }
                sb.append(n == 0 ? "" : ", ").append(name);
                n++;
            }
        }
        if (!plan.blockedBlanks().isEmpty()) {
            int b = plan.blockedBlanks().size();
            sb.append("  •  ").append(b).append(b == 1 ? " slot" : " slots").append(" couldn't be emptied");
        }
        if (dropped > 0) {
            sb.append("  •  Dropped ").append(dropped).append(dropped == 1 ? " stack" : " stacks");
        }
        return sb.toString();
    }

    private static String nameOf(PresetSlot slot) {
        if (slot.isCategory()) {
            return "Any " + slot.category().label();
        }
        return slot.resolveItem()
                .map(item -> new ItemStack(item).getHoverName().getString())
                .orElse(String.valueOf(slot.itemId()));
    }

    private static int countDrops(ArrangementPlanner.Plan plan) {
        int n = 0;
        for (ArrangementPlanner.Move m : plan.moves()) {
            if (m.isDrop()) {
                n++;
            }
        }
        return n;
    }

    private static ArrangementPlanner.Plan planNow(Minecraft mc, LocalPlayer player, Preset preset) {
        ItemStack[] snapshot = InventorySnapshot.take(player);
        return ArrangementPlanner.plan(snapshot, preset,
                dev.inventorymanagerplus.config.Config.get().dropFromEmptySlots);
    }
}
