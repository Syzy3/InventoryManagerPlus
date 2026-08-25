package dev.inventorymanagerplus;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Key bindings. All four appear in Options &gt; Controls &gt; Key Binds under their own category and
 * are rebindable there; nothing here hard-codes a key at use time.
 *
 * <p>Note the 26.x API shape: categories are registered objects keyed by an {@link Identifier}
 * rather than free-form strings, and the helper is {@code KeyMappingHelper} in the
 * {@code keymapping.v1} package (it was {@code KeyBindingHelper} in {@code keybinding.v1} before
 * Fabric API moved to Mojang's names).
 */
public final class ModKeys {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(InventoryManagerPlus.MOD_ID, "main"));

    public static final KeyMapping OPEN_MENU = register("open_menu", InputConstants.KEY_Z);
    public static final KeyMapping APPLY_ACTIVE = register("apply_active", -1 /* unbound */);
    public static final KeyMapping TOGGLE_AUTO_SORT = register("toggle_auto_sort", -1 /* unbound */);
    /** Held, not tapped: suspends Auto Sort while down. Defaults to left Alt. */
    public static final KeyMapping PAUSE_AUTO_SORT = register("pause_auto_sort", InputConstants.KEY_LALT);

    private ModKeys() {
    }

    private static KeyMapping register(String name, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + InventoryManagerPlus.MOD_ID + "." + name,
                InputConstants.Type.KEYSYM,
                defaultKey,
                CATEGORY));
    }

    /** Touch the class so the static initialisers run during client init. */
    public static void init() {
    }
}