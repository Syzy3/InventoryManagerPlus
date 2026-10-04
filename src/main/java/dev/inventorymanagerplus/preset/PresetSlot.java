package dev.inventorymanagerplus.preset;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.Optional;

/**
 * What a preset wants in one particular inventory slot: a specific item (optionally with an
 * enchantment condition), any item of a category, or nothing at all ("keep empty").
 *
 * <p>Stores an item <em>identifier</em> rather than an {@code ItemStack}. Since 26.1 a stack
 * can't be built before a world is loaded, and presets are read at client startup; a Creative
 * preset also has to work when the player owns none of the item.
 *
 * <p>There is no amount: a slot says which item goes there, and applying fills it as full as it
 * can be. Older saves carry "count" and "components" fields; they are ignored.
 */
public final class PresetSlot {

    /** Marker meaning "the player wants this slot left empty". */
    private static final PresetSlot BLANK = new PresetSlot(null, null, null);

    private final Identifier itemId;
    /** Enchantment condition. Never null. */
    private final EnchantRequirement enchants;
    /** When set, the slot accepts any item of this category and has no item id. */
    private final ItemCategory category;

    private PresetSlot(Identifier itemId, EnchantRequirement enchants, ItemCategory category) {
        this.itemId = itemId;
        this.enchants = enchants == null ? EnchantRequirement.ignore() : enchants;
        this.category = category;
    }

    public static PresetSlot blank() {
        return BLANK;
    }

    public static PresetSlot of(Identifier itemId) {
        return new PresetSlot(itemId, null, null);
    }

    public static PresetSlot of(Identifier itemId, EnchantRequirement enchants) {
        return new PresetSlot(itemId, enchants, null);
    }

    /**
     * A slot that accepts any item of a category.
     *
     * <p>No enchantment condition: the enchantment picker needs a specific item to know which
     * enchantments are valid, and "any weapon with Sharpness" would have to mean something
     * different for a bow than for a sword.
     */
    public static PresetSlot ofCategory(ItemCategory category) {
        return new PresetSlot(null, null, category);
    }

    /** Same item, with a different enchantment condition. */
    public PresetSlot withEnchants(EnchantRequirement req) {
        return new PresetSlot(itemId, req, category);
    }

    public ItemCategory category() {
        return category;
    }

    public boolean isCategory() {
        return category != null;
    }

    /**
     * True when the player explicitly asked for this slot to hold nothing.
     *
     * <p>A category slot also has no item id — it asks for a kind of item, not a specific one —
     * so it must be excluded here, or it reads as an intentional blank everywhere blankness is
     * checked: drawing, matching, and the preset summary.
     */
    public boolean isBlank() {
        return itemId == null && category == null;
    }

    public Identifier itemId() {
        return itemId;
    }

    public EnchantRequirement enchants() {
        return enchants;
    }

    /** The item this slot wants, or empty if the id is blank or refers to an unknown/removed item. */
    public Optional<Item> resolveItem() {
        if (itemId == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(itemId);
    }

    // ---------------------------------------------------------------- serialisation

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (category != null) {
            // Written with the category's stand-in icon as "item", so older versions of the mod
            // still find an item here when they read the file.
            o.addProperty("item", category.iconId());
            o.addProperty("category", category.name());
            return o;
        }
        if (itemId == null) {
            o.addProperty("blank", true);
            return o;
        }
        o.addProperty("item", itemId.toString());
        if (!enchants.isNoop()) {
            o.add("enchants", enchants.toJson());
        }
        return o;
    }

    public static PresetSlot fromJson(JsonObject o) {
        if (o.has("blank") && o.get("blank").getAsBoolean()) {
            return blank();
        }
        if (o.has("category")) {
            ItemCategory cat = ItemCategory.byName(o.get("category").getAsString());
            if (cat != null) {
                return ofCategory(cat);
            }
        }
        if (!o.has("item")) {
            return blank();
        }
        Identifier id = Identifier.tryParse(o.get("item").getAsString());
        if (id == null) {
            return blank();
        }
        EnchantRequirement req = o.has("enchants") && o.get("enchants").isJsonObject()
                ? EnchantRequirement.fromJson(o.getAsJsonObject("enchants"))
                : EnchantRequirement.ignore();
        return of(id, req);
    }
}
