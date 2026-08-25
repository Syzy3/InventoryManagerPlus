package dev.inventorymanagerplus.preset;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which enchantments apply to which gear, and how high each one goes.
 *
 * <p>Hardcoded rather than read from the enchantment registry. The registry knows every
 * enchantment that exists but not which ones the player can sensibly put on a given item —
 * answering that needs {@code Enchantment#canEnchant} or the supported-items holder set, both of
 * which have moved repeatedly across versions. A static table costs nothing at runtime, works
 * without a bound registry, and cannot break on a Minecraft update.
 *
 * <p>The trade-off is that modded enchantments will not appear here. If that becomes a problem
 * the registry path can be added as a fallback for items this table does not recognise.
 */
public final class EnchantCatalog {

    /** One offer: the enchantment and the highest level it reaches on this gear. */
    public record Entry(Identifier id, int maxLevel) {
    }

    private EnchantCatalog() {
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    private static void put(Map<Identifier, Integer> into, String path, int max) {
        into.put(mc(path), max);
    }

    /** On everything with durability. */
    private static void durable(Map<Identifier, Integer> m) {
        put(m, "unbreaking", 3);
        put(m, "mending", 1);
        put(m, "vanishing_curse", 1);
    }

    /** Shared by every armour piece. */
    private static void armorBase(Map<Identifier, Integer> m) {
        put(m, "protection", 4);
        put(m, "fire_protection", 4);
        put(m, "blast_protection", 4);
        put(m, "projectile_protection", 4);
        put(m, "thorns", 3);
        put(m, "binding_curse", 1);
        durable(m);
    }

    /**
     * The enchantments offered for an item, in a stable display order.
     *
     * @param itemId registry id of the item, e.g. {@code minecraft:diamond_pickaxe}
     * @return possibly empty list; empty means "nothing to offer for this item"
     */
    public static List<Entry> forItem(Identifier itemId) {
        Map<Identifier, Integer> m = new LinkedHashMap<>();
        if (itemId == null) {
            return List.of();
        }
        String p = itemId.getPath();

        if (p.endsWith("_helmet") || p.equals("turtle_helmet")) {
            armorBase(m);
            put(m, "respiration", 3);
            put(m, "aqua_affinity", 1);
        } else if (p.endsWith("_chestplate")) {
            armorBase(m);
        } else if (p.endsWith("_leggings")) {
            armorBase(m);
            put(m, "swift_sneak", 3);
        } else if (p.endsWith("_boots")) {
            armorBase(m);
            put(m, "feather_falling", 4);
            put(m, "depth_strider", 3);
            put(m, "frost_walker", 2);
            put(m, "soul_speed", 3);
        } else if (p.endsWith("_sword")) {
            put(m, "sharpness", 5);
            put(m, "smite", 5);
            put(m, "bane_of_arthropods", 5);
            put(m, "fire_aspect", 2);
            put(m, "knockback", 2);
            put(m, "looting", 3);
            put(m, "sweeping_edge", 3);
            durable(m);
        } else if (p.equals("mace")) {
            put(m, "density", 5);
            put(m, "breach", 4);
            put(m, "wind_burst", 3);
            put(m, "smite", 5);
            put(m, "bane_of_arthropods", 5);
            put(m, "fire_aspect", 2);
            durable(m);
        } else if (p.equals("bow")) {
            put(m, "power", 5);
            put(m, "punch", 2);
            put(m, "flame", 1);
            put(m, "infinity", 1);
            durable(m);
        } else if (p.equals("crossbow")) {
            put(m, "quick_charge", 3);
            put(m, "multishot", 1);
            put(m, "piercing", 4);
            durable(m);
        } else if (p.equals("trident")) {
            put(m, "loyalty", 3);
            put(m, "impaling", 5);
            put(m, "riptide", 3);
            put(m, "channeling", 1);
            durable(m);
        } else if (p.endsWith("_pickaxe") || p.endsWith("_shovel") || p.endsWith("_hoe")) {
            // Checked before _axe, since "_pickaxe" also ends in "axe".
            put(m, "efficiency", 5);
            put(m, "fortune", 3);
            put(m, "silk_touch", 1);
            durable(m);
        } else if (p.endsWith("_axe")) {
            put(m, "efficiency", 5);
            put(m, "sharpness", 5);
            put(m, "smite", 5);
            put(m, "bane_of_arthropods", 5);
            put(m, "fortune", 3);
            put(m, "silk_touch", 1);
            durable(m);
        } else if (p.equals("fishing_rod")) {
            put(m, "luck_of_the_sea", 3);
            put(m, "lure", 3);
            durable(m);
        } else if (p.equals("shears")) {
            put(m, "efficiency", 5);
            durable(m);
        } else if (p.equals("elytra")) {
            put(m, "binding_curse", 1);
            durable(m);
        } else if (p.equals("flint_and_steel") || p.equals("shield") || p.equals("brush")
                || p.equals("carrot_on_a_stick") || p.equals("warped_fungus_on_a_stick")) {
            durable(m);
        } else if (p.equals("carved_pumpkin") || p.endsWith("_head") || p.endsWith("_skull")) {
            put(m, "binding_curse", 1);
            put(m, "vanishing_curse", 1);
        }

        List<Entry> out = new ArrayList<>(m.size());
        m.forEach((id, max) -> out.add(new Entry(id, max)));
        return out;
    }

    /** Whether this item has anything worth showing an enchantment screen for. */
    public static boolean isEnchantable(Identifier itemId) {
        return !forItem(itemId).isEmpty();
    }
}