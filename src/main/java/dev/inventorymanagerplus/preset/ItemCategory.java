package dev.inventorymanagerplus.preset;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;
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

    /** Swords, spears, axes, bows, crossbows, tridents, the mace and shields. */
    WEAPON("W", "Weapon", "minecraft:diamond_sword"),
    /**
     * Armour pieces a player wears: helmets, chestplates, leggings, boots, turtle shell.
     * Not elytra, mob heads, carved pumpkins, saddles, harnesses, carpets or horse/wolf armour.
     */
    ARMOR("A", "Armor", "minecraft:diamond_chestplate"),
    /** Pickaxes, shovels, hoes, shears, flint and steel, fishing rods, brushes, compasses... */
    TOOL("T", "Tool", "minecraft:iron_pickaxe"),
    /** Anything edible. */
    FOOD("F", "Food", "minecraft:cooked_beef"),
    /** Anything placeable that isn't one of the above. */
    BLOCK("B", "Block", "minecraft:oak_planks"),
    /** Ingots, gems, ores, dyes, seeds, brewing ingredients and other crafting inputs. */
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
            java.util.Map.entry("copper_torch", MISC),
            java.util.Map.entry("redstone_torch", MISC),
            java.util.Map.entry("lever", MISC),
            java.util.Map.entry("ladder", MISC),
            java.util.Map.entry("cobweb", MISC),
            // Placeable, but they are crafting/farming inputs, not building blocks.
            java.util.Map.entry("string", MATERIAL),
            java.util.Map.entry("redstone", MATERIAL),
            java.util.Map.entry("wheat_seeds", MATERIAL),
            java.util.Map.entry("beetroot_seeds", MATERIAL),
            java.util.Map.entry("melon_seeds", MATERIAL),
            java.util.Map.entry("pumpkin_seeds", MATERIAL),
            java.util.Map.entry("torchflower_seeds", MATERIAL),
            java.util.Map.entry("pitcher_pod", MATERIAL),
            java.util.Map.entry("cocoa_beans", MATERIAL),
            java.util.Map.entry("nether_wart", MATERIAL),
            java.util.Map.entry("sugar_cane", MATERIAL),
            java.util.Map.entry("bamboo", MATERIAL),
            java.util.Map.entry("resin_clump", MATERIAL));

    /** Classification never changes for an item, so each one is worked out once. */
    private static final java.util.Map<Item, ItemCategory> CACHE = new java.util.IdentityHashMap<>();

    /**
     * The single category an item belongs to.
     *
     * <p>Never returns null: anything that matches nothing else is {@link #MISC}.
     */
    public static ItemCategory classify(Item item) {
        if (item == null) {
            return MISC;
        }
        ItemCategory cached = CACHE.get(item);
        if (cached == null) {
            cached = compute(item);
            CACHE.put(item, cached);
        }
        return cached;
    }

    private static ItemCategory compute(Item item) {
        String path = idPath(item);

        ItemCategory override = OVERRIDES.get(path);
        if (override != null) {
            return override;
        }
        if (isWeapon(item, path)) {
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
        // Before the BlockItem test: ores are placeable, but nobody thinks of them as building
        // blocks. They are what you mine to get materials.
        if (isMaterial(path)) {
            return MATERIAL;
        }
        if (item instanceof BlockItem) {
            return BLOCK;
        }
        return MISC;
    }

    /** The armour slot an item goes in, or null if a player can't wear it there. */
    private static EquipmentSlot wornSlot(Item item) {
        Equippable eq = item.components().get(DataComponents.EQUIPPABLE);
        if (eq == null || eq.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            return null;
        }
        return eq.slot();
    }

    private static EquipmentSlot slotFor(int armorIndex) {
        return switch (armorIndex) {
            case 39 -> EquipmentSlot.HEAD;
            case 38 -> EquipmentSlot.CHEST;
            case 37 -> EquipmentSlot.LEGS;
            case 36 -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    /**
     * Whether an item may go in a particular player armour slot: anything the game lets a player
     * wear there, which includes carved pumpkins and mob heads on the head and elytra on the chest.
     *
     * <p>Read from the item's equippable slot, so carpets and saddles (equippable, but by llamas
     * and horses) are kept out, and modded armour works without being named.
     *
     * @param armorIndex inventory index 36-39: boots, leggings, chestplate, helmet
     */
    public static boolean fitsArmorSlot(Item item, int armorIndex) {
        if (item == null) {
            return false;
        }
        EquipmentSlot wanted = slotFor(armorIndex);
        return wanted != null && wornSlot(item) == wanted;
    }

    /** Whether a stack falls into this category. */
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && classify(stack.getItem()) == this;
    }

    /**
     * Whether a stack satisfies this category in a particular inventory slot. In an armour slot,
     * "Any Armor" means armour for that slot: a helmet slot only takes helmets.
     */
    public boolean matches(ItemStack stack, int slot) {
        if (!matches(stack)) {
            return false;
        }
        if (this == ARMOR && slot >= 36 && slot <= 39) {
            return fitsArmorSlot(stack.getItem(), slot);
        }
        return true;
    }

    // ---------------------------------------------------------------- tests

    private static String idPath(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)
                .getPath().toLowerCase(Locale.ROOT);
    }

    private static boolean isWeapon(Item item, String p) {
        return p.endsWith("_sword")
                || p.endsWith("_spear")
                || p.endsWith("_axe") && !p.endsWith("_pickaxe")
                || p.equals("bow")
                || p.equals("crossbow")
                || p.equals("trident")
                || p.equals("mace")
                || p.equals("shield")
                // Modded spears and shields, recognised by what they do rather than their name.
                || item.components().has(DataComponents.KINETIC_WEAPON)
                || item.components().has(DataComponents.PIERCING_WEAPON)
                || item.components().has(DataComponents.BLOCKS_ATTACKS);
    }

    /**
     * Real armour: worn in a player armour slot and wears out. That rules out carved pumpkins and
     * mob heads (no durability), elytra (a glider, not armour) and anything worn by mounts.
     */
    private static boolean isArmor(Item item) {
        return wornSlot(item) != null
                && item.components().has(DataComponents.MAX_DAMAGE)
                && !item.components().has(DataComponents.GLIDER);
    }

    private static boolean isTool(String p) {
        return p.endsWith("_pickaxe")
                || p.endsWith("_shovel")
                || p.endsWith("_hoe")
                || TOOL_NAMES.contains(p);
    }

    private static final java.util.Set<String> TOOL_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
            "shears", "flint_and_steel", "fishing_rod", "brush", "spyglass", "compass",
            "recovery_compass", "clock", "carrot_on_a_stick", "warped_fungus_on_a_stick"));

    private static boolean isFood(Item item) {
        return item.components().has(DataComponents.FOOD);
    }

    private static boolean isMaterial(String p) {
        // Storage blocks (iron block, raw iron block...) are blocks, not stock.
        if (p.endsWith("_block")) {
            return false;
        }
        if (p.startsWith("raw_") || p.endsWith("_ore")) {
            return true;
        }
        for (String suffix : MATERIAL_SUFFIXES) {
            if (p.endsWith(suffix)) {
                return true;
            }
        }
        return MATERIAL_NAMES.contains(p);
    }

    /**
     * Endings that only ever mark a crafting input. Kept short on purpose: looser endings like
     * "_powder" or "_rod" also catch blocks (concrete powder, end rods).
     */
    private static final String[] MATERIAL_SUFFIXES = {
            "_ingot", "_nugget", "_dye", "_smithing_template",
    };

    /**
     * Materials whose names follow no useful pattern, checked against every Minecraft 26.2 item.
     *
     * <p>A {@code HashSet} rather than {@code Set.of}: the immutable factory throws on a repeated
     * entry, so one accidental duplicate in this list takes the whole class down at load time and
     * every category lookup with it.
     */
    private static final java.util.Set<String> MATERIAL_NAMES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "diamond", "emerald", "quartz", "coal", "charcoal", "lapis_lazuli",
                    "amethyst_shard", "echo_shard", "ancient_debris", "netherite_scrap", "leather",
                    "feather", "bone", "bone_meal", "gunpowder", "slime_ball", "ink_sac", "glow_ink_sac",
                    "blaze_rod", "blaze_powder", "breeze_rod", "ghast_tear", "magma_cream",
                    "fermented_spider_eye", "glistering_melon_slice", "rabbit_foot", "dragon_breath",
                    "phantom_membrane", "nautilus_shell", "heart_of_the_sea", "shulker_shell",
                    "rabbit_hide", "turtle_scute", "armadillo_scute", "prismarine_crystals",
                    "prismarine_shard", "nether_star", "stick", "paper", "flint", "clay_ball", "brick",
                    "nether_brick", "resin_brick", "sugar", "wheat", "honeycomb", "glowstone_dust",
                    "popped_chorus_fruit"));
}
