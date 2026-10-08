package dev.inventorymanagerplus.kinetic;

import dev.inventorymanagerplus.gui.PresetListScreen;
import dev.inventorymanagerplus.gui.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opened by the Settings button on the Kinetic screen, named in fabric.mod.json under
 * {@code custom.kinetic.settings}. Kept free of Mod Menu classes so it works without Mod Menu.
 */
public final class KineticSettings {

    private KineticSettings() {
    }

    public static Screen open(Screen parent) {
        // Same choice as the Mod Menu button: presets need a world, so outside one show Settings.
        return Minecraft.getInstance().level == null
                ? new SettingsScreen(parent)
                : new PresetListScreen(parent);
    }
}
