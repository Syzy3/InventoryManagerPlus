package dev.inventorymanagerplus;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.inventorymanagerplus.gui.PresetListScreen;
import dev.inventorymanagerplus.gui.SettingsScreen;
import net.minecraft.client.Minecraft;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Presets depend on item data that isn't bound outside a world, so from the title
        // screen we send the user to Settings instead.
        return parent -> Minecraft.getInstance().level == null
                ? new SettingsScreen(parent)
                : new PresetListScreen(parent);
    }
}