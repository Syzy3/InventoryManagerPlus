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

        // Auto Sort is session-scoped on purpose. A mod that starts rearranging your inventory
        // the moment you log in — before you have asked it for anything — is the wrong default
        // for something that moves your items around. Nothing is being watched at startup, so no
        // preset may claim to be.
        activePresetId = null;
        for (Preset p : presets) {
            p.setAutoSort(false);
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

    public Optional<Preset> byName(String name) {
        return presets.stream()
                .filter(p -> p.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public void add(Preset preset) {
        presets.add(preset);
        save();
    }

    public void remove(Preset preset) {
        presets.remove(preset);
        if (preset.id().equals(activePresetId)) {
            activePresetId = null;
        }
        save();
    }

    /** The preset most recently applied; the one Auto Sort and the "apply active" keybind use. */
    public Preset active() {
        return byId(activePresetId).orElse(null);
    }

    public void setActive(Preset preset) {
        activePresetId = preset == null ? null : preset.id();
    }

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

        setActive(preset);

        // Creative acquisition happens before planning so the newly created stacks are visible to
        // the planner in the same operation, which is what makes "click preset -> get items ->
        // arrange them" feel like one action instead of two.
        if (player.isCreative()) {
            CreativeSupplier.supplyMissing(mc, preset);
        }

        ItemStack[] snapshot = InventorySnapshot.take(player);
        var plan = ArrangementPlanner.plan(snapshot, preset, CreativeSupplier.registriesOf(mc));

        if (plan.isNoOp()) {
            return plan.isComplete()
                    ? Component.literal(preset.name() + ": already arranged")
                    : Component.literal(preset.name() + ": nothing to move (" + plan.missing().size() + " item(s) unavailable)");
        }

        InventoryManagerPlus.ops().submit(plan.moves(), preset.name(), announce);

        StringBuilder sb = new StringBuilder(preset.name() + ": " + plan.moves().size() + " move(s)");
        if (!plan.missing().isEmpty()) {
            sb.append(", ").append(plan.missing().size()).append(" missing");
        }
        if (!plan.blockedBlanks().isEmpty()) {
            sb.append(", ").append(plan.blockedBlanks().size()).append(" slot(s) could not be cleared");
        }
        return Component.literal(sb.toString());
    }

    /** Builds a preset from whatever the player is currently holding, hotbar only. */
    public static Preset captureHotbar(LocalPlayer player, String name) {
        Preset preset = Preset.createEmpty(name);
        preset.setMatchMode(Config.get().defaultMatchMode);
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                preset.slots().put(i, PresetSlot.blank());
            } else {
                var id = dev.inventorymanagerplus.inventory.ItemMatcher.idOf(stack);
                if (id != null) {
                    preset.slots().put(i, PresetSlot.of(id, stack.getCount(), null));
                }
            }
        }
        return preset;
    }
}