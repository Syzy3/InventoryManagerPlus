package dev.inventorymanagerplus.preset;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Broad item groupings a preset slot can ask for instead of one specific item.
 *
 * <p>Membership is worked out at runtime from what the item actually <em>is</em>, not from a
 * hand-written list of every item in the game. A list of ~1,500 ids would be wrong in places the
 * day it was written and stale the moment anything is added, and it would miss every modded item.
 * Asking the item about itself costs nothing and keeps working.
 *
 * <p>Order matters, because items belong to more than one group: an axe is a weapon and a tool, a
 * golden apple is food and arguably a material. {@link #classify} runs the checks in the order the
 * constants are declared and takes the first hit, so the declaration order <em>is</em> the
 * precedence rule.
 */
public enum ItemCategory {

    /** Swords, axes, bows, crossbows, tridents, the mace. Checked before tools so axes land here. */
    WEAPON("W", "Weapon", "minecraft:diamond_sword"),
    /** Anything wearable, including elytra and mob heads. */
    ARMOR("A", "Armor", "minecraft:diamond_chestplate"),
    /** Pickaxes, shovels, hoes, shears, flint and steel, fishing rods. */
    TOOL("T", "Tool", "minecraft:iron_pickaxe"),
    /** Anything edible. */
    FOOD("F", "Food", "minecraft:cooked_beef"),
    /** Anything placeable. */
    BLOCK("B", "Block", "minecraft:oak_planks"),
    /** Ingots, gems, dusts, rods and other crafting inputs. */
    MATERIAL("MAT", "Material", "minecraft:iron_ingot"),
    /** Everything else, so nothing is ever unclassified. */
    MISC("MSC", "Miscellaneous", "minecraft:feather");

    private final String shortCode;
    private final String label;
    private final String iconId;

    ItemCategory(String shortCode, String label, String iconId) {
        this.shortCode = shortCode;
        this.label = label;
        this.iconId = iconId;
    }

    /**
     * Corner marker for the slot. Explicit rather than derived from the label, because Material
     * and Miscellaneous both start with M and a single letter could not tell them apart.
     */
    public String shortCode() {
        return shortCode;
    }

    public String label() {
        return label;
    }

    /** Representative item drawn in the editor for a slot asking for this category. */
    public String iconId() {
        return iconId;
    }

    public static ItemCategory byName(String name) {
        if (name == null) {
            return null;
        }
        for (ItemCategory c : values()) {
            if (c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Hand-picked exceptions, checked before anything else.
     *
     * <p>The automatic rules answer "what is this item" correctly but not always usefully. Leaf
     * litter is a placeable block by every technical measure, yet nobody building a block loadout
     * means it. Rather than bending the rules and breaking genuine cases, disagreements get listed
     * here — add a line when one turns up.
     */
    private static final java.util.Map<String, ItemCategory> OVERRIDES = java.util.Map.ofEntries(
            java.util.Map.entry("leaf_litter", MISC),
            java.util.Map.entry("wildflowers", MISC),
            java.util.Map.entry("pink_petals", MISC),
            java.util.Map.entry("torch", MISC),
            java.util.Map.entry("soul_torch", MISC),
            java.util.Map.entry("redstone_torch", MISC),
            java.util.Map.entry("lever", MISC),
            java.util.Map.entry("ladder", MISC),
            java.util.Map.entry("string", MATERIAL),
            java.util.Map.entry("cobweb", MISC));

    /**
     * The single category an item belongs to.
     *
     * <p>Never returns null: anything that matches nothing else is {@link #MISC}.
     */
    public static ItemCategory classify(Item item) {
        if (item == null) {
            return MISC;
        }
        String path = idPath(item);

        ItemCategory override = OVERRIDES.get(path);
        if (override != null) {
            return override;
        }

        if (isWeapon(path)) {
            return WEAPON;
        }
        if (isArmor(item)) {
            return ARMOR;
        }
        if (isTool(path)) {
            return TOOL;
        }
        if (isFood(item)) {
            return FOOD;
        }
        // Before the BlockItem test: ores and raw-ore blocks are placeable, but nobody thinks of
        // them as building blocks. They are what you mine to get materials.
        if (isMaterial(path)) {
            return MATERIAL;
        }
        if (item instanceof BlockItem) {
            return BLOCK;
        }
        return MISC;
    }

    /**
     * Whether an item may go in a particular player armour slot.
     *
     * <p>Deliberately not the {@code EQUIPPABLE} component: since 26.x carpets carry it too,
     * because llamas wear them, so an equippable test lets a pink carpet into the chestplate
     * slot. Name suffixes are narrower and also answer the sharper question — the helmet slot
     * wants a helmet, not merely something wearable.
     *
     * @param armorIndex inventory index 36-39: boots, leggings, chestplate, helmet
     */
    public static boolean fitsArmorSlot(Item item, int armorIndex) {
        if (item == null) {
            return false;
        }
        String p = idPath(item);
        return switch (armorIndex) {
            case 39 -> p.endsWith("_helmet") || p.equals("turtle_helmet")
                    || p.equals("carved_pumpkin") || p.endsWith("_head") || p.endsWith("_skull");
            case 38 -> p.endsWith("_chestplate") || p.equals("elytra");
            case 37 -> p.endsWith("_leggings");
            case 36 -> p.endsWith("_boots");
            default -> false;
        };
    }

    /** Whether a stack falls into this category. */
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && classify(stack.getItem()) == this;
    }

    // ---------------------------------------------------------------- tests

    private static String idPath(Item item) {
        // Registry lookup avoided here: the item's own toString is stable enough for suffix tests
        // and works before registries are bound, which is when presets get loaded.
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)
                .getPath().toLowerCase(Locale.ROOT);
    }

    private static boolean isWeapon(String p) {
        return p.endsWith("_sword")
                || p.endsWith("_axe") && !p.endsWith("_pickaxe")
                || p.equals("bow")
                || p.equals("crossbow")
                || p.equals("trident")
                || p.equals("mace");
    }

    /**
     * Wearable check via the equippable component, so every helmet, elytra, mob head and modded
     * armour piece is covered without naming any of them.
     */
    private static boolean isArmor(Item item) {
        return item.components().has(DataComponents.EQUIPPABLE);
    }

    private static boolean isTool(String p) {
        return p.endsWith("_pickaxe")
                || p.endsWith("_shovel")
                || p.endsWith("_hoe")
                || p.equals("shears")
                || p.equals("flint_and_steel")
                || p.equals("fishing_rod")
                || p.equals("brush")
                || p.equals("spyglass")
                || p.equals("compass")
                || p.equals("clock");
    }

    private static boolean isFood(Item item) {
        return item.components().has(DataComponents.FOOD);
    }

    private static boolean isMaterial(String p) {
        // Storage blocks are building materials in the literal sense but belong with blocks:
        // a chest of iron blocks is scenery, a stack of ingots is stock.
        if (p.endsWith("_block") || p.equals("raw_iron_block") || p.equals("raw_gold_block")
                || p.equals("raw_copper_block")) {
            return false;
        }

        // Raw ore drops: raw_iron, raw_gold, raw_copper.
        if (p.startsWith("raw_")) {
            return true;
        }

        for (String suffix : MATERIAL_SUFFIXES) {
            if (p.endsWith(suffix)) {
                return true;
            }
        }
        return MATERIAL_NAMES.contains(p);
    }

    /** Endings that reliably mark a crafting input, including modded equivalents. */
    private static final String[] MATERIAL_SUFFIXES = {
            "_ore",          // iron_ore, deepslate_diamond_ore, nether_gold_ore
            "_ingot", "_nugget", "_dust", "_powder", "_scrap",
            "_shard", "_crystals", "_rod", "_dye",
            "_hide", "_scute", "_shell", "_membrane", "_cream", "_tear",
    };

    /**
     * Materials whose names follow no useful pattern.
     *
     * <p>A {@code HashSet} rather than {@code Set.of}: the immutable factory throws on a repeated
     * entry, so one accidental duplicate in this list takes the whole class down at load time and
     * every category lookup with it.
     */
    private static final java.util.Set<String> MATERIAL_NAMES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "diamond", "emerald", "quartz", "coal", "charcoal", "lapis_lazuli", "redstone",
                    "amethyst_shard", "ancient_debris", "netherite_scrap", "string", "leather",
                    "feather", "bone", "gunpowder", "slime_ball", "ink_sac", "glow_ink_sac",
                    "spider_eye", "rotten_flesh", "blaze_rod", "ghast_tear", "magma_cream",
                    "phantom_membrane", "nautilus_shell", "heart_of_the_sea", "shulker_shell",
                    "rabbit_hide", "prismarine_crystals", "prismarine_shard", "echo_shard",
                    "breeze_rod", "wind_charge", "stick", "paper", "flint", "clay_ball", "brick",
                    "nether_brick", "sugar", "wheat", "sugar_cane", "bamboo", "honeycomb",
                    "nether_wart", "blaze_powder", "glowstone_dust", "bone_meal", "egg", "milk_bucket",
                    "copper_ingot"));
}