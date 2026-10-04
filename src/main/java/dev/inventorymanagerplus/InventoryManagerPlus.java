package dev.inventorymanagerplus;

import dev.inventorymanagerplus.autosort.AutoSortManager;
import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.config.Storage;
import dev.inventorymanagerplus.gui.PresetListScreen;
import dev.inventorymanagerplus.inventory.InventoryOps;
import dev.inventorymanagerplus.preset.Preset;
import dev.inventorymanagerplus.preset.PresetManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.inventorymanagerplus.gui.PauseMenuButton;

/**
 * Client entry point.
 *
 * <p>Client-only by design: the mod does nothing a vanilla player could not do by clicking, so
 * there is no server component and no packets of its own. It works in single-player and on any
 * server that permits normal inventory interaction.
 */
public final class InventoryManagerPlus implements ClientModInitializer {

    public static final String MOD_ID = "inventory-manager-plus";
    public static final Logger LOGGER = LoggerFactory.getLogger("InventoryManager+");

    private static final PresetManager PRESETS = new PresetManager();
    private static final InventoryOps OPS = new InventoryOps();
    private static final AutoSortManager AUTO_SORT = new AutoSortManager();

    public static PresetManager presets() {
        return PRESETS;
    }

    public static InventoryOps ops() {
        return OPS;
    }

    public static AutoSortManager autoSort() {
        return AUTO_SORT;
    }

    @Override
    public void onInitializeClient() {
        Storage.loadConfig();

        PRESETS.load();
        ModKeys.init();

        ClientTickEvents.END_CLIENT_TICK.register(InventoryManagerPlus::onClientTick);

        PauseMenuButton.register();

        LOGGER.info("InventoryManager+ ready");
    }

    private static void onClientTick(Minecraft mc) {
        if (mc.player == null) {
            // Between worlds: drop any queued work so it cannot resume against a different
            // inventory after joining a server or changing dimension.
            OPS.cancel();
            AUTO_SORT.reset();
            return;
        }

        // The pause key is a held modifier, so it is polled rather than consumed.
        AUTO_SORT.setTemporarilyDisabled(ModKeys.PAUSE_AUTO_SORT.isDown());

        while (ModKeys.OPEN_MENU.consumeClick()) {
            mc.gui.setScreen(new PresetListScreen(null));
        }

        while (ModKeys.APPLY_ACTIVE.consumeClick()) {
            Preset active = PRESETS.active();
            if (active == null) {
                status(mc, "No active preset — open the menu and apply one first");
            } else {
                status(mc, PRESETS.apply(mc, active, true).getString());
            }
        }

        while (ModKeys.TOGGLE_AUTO_SORT.consumeClick()) {
            // Same switch as the preset's "Auto" button, for the active preset.
            Preset active = PRESETS.active();
            if (active == null) {
                status(mc, "No active preset — open the menu and apply one first");
            } else {
                PRESETS.setAutoSort(active, !active.autoSort());
                status(mc, "Auto Sort: " + (active.autoSort() ? "ON" : "OFF") + "  •  Preset: " + active.name());
            }
        }

        pollPresetHotkeys(mc);

        AUTO_SORT.tick(mc);
        OPS.tick(mc);
    }

    /** Keys held down last tick, so a held key applies its preset once, not every tick. */
    private static final java.util.Set<Integer> HELD_HOTKEYS = new java.util.HashSet<>();

    /**
     * Applies a preset when its own hotkey is pressed. Polled rather than registered as key
     * bindings because presets come and go while the game runs, and Minecraft's key bindings
     * can only be registered at startup. Only works with no screen open, so typing in chat or a
     * sign never triggers one.
     */
    private static void pollPresetHotkeys(Minecraft mc) {
        if (mc.gui.screen() != null) {
            HELD_HOTKEYS.clear();
            return;
        }
        var window = mc.getWindow();
        for (Preset preset : PRESETS.all()) {
            if (!preset.hasHotkey()) {
                continue;
            }
            int key = preset.hotkey();
            boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, key);
            if (down && HELD_HOTKEYS.add(key)) {
                status(mc, PRESETS.apply(mc, preset, true).getString());
            } else if (!down) {
                HELD_HOTKEYS.remove(key);
            }
        }
    }

    public static void status(Minecraft mc, String message) {
        if (mc.player != null && Config.get().showStatusMessages) {
            mc.gui.hud.setOverlayMessage(Component.literal(message), false);
        }
    }
}