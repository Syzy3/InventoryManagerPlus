package dev.inventorymanagerplus.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * What a preset wants in one particular inventory slot.
 *
 * <p>Deliberately stores an item <em>identifier</em> plus an optional raw component blob rather
 * than an {@link ItemStack}. Two reasons:
 *
 * <ol>
 *   <li>Since 26.1 an {@code ItemStack} cannot even be constructed before a world is loaded, and
 *       presets are read from disk at client startup.</li>
 *   <li>A Creative preset must be usable when the player owns none of the item, so the preset
 *       cannot be a reference to a stack that happens to exist right now.</li>
 * </ol>
 *
 * <p>{@code count} is stored for display and for Creative acquisition only. It is never used as a
 * matching requirement — a preset asking for 64 Cobblestone is satisfied by 37 Cobblestone.
 */
public final class PresetSlot {

    /** Marker meaning "the player wants this slot left empty". */
    private static final PresetSlot BLANK = new PresetSlot(null, 0, null, null, null);

    private final Identifier itemId;
    private final int count;
    /** Raw serialised {@code DataComponentPatch}; only consulted in {@link MatchMode#EXACT}. */
    private final JsonElement components;
    /** Enchantment condition, applied in both match modes. Never null. */
    private final EnchantRequirement enchants;
    /** When set, the slot accepts any item of this category and {@link #itemId} is only a hint. */
    private final ItemCategory category;

    private PresetSlot(Identifier itemId, int count, JsonElement components,
                       EnchantRequirement enchants, ItemCategory category) {
        this.itemId = itemId;
        this.count = count;
        this.components = components;
        this.enchants = enchants == null ? EnchantRequirement.ignore() : enchants;
        this.category = category;
    }

    public static PresetSlot blank() {
        return BLANK;
    }

    public static PresetSlot of(Identifier itemId, int count, JsonElement components) {
        return new PresetSlot(itemId, Math.max(1, count), components, null, null);
    }

    public static PresetSlot of(Identifier itemId, int count, JsonElement components,
                                EnchantRequirement enchants) {
        return new PresetSlot(itemId, Math.max(1, count), components, enchants, null);
    }

    /**
     * A slot that accepts any item of a category.
     *
     * <p>No enchantment condition: the enchantment picker needs a specific item to know which
     * enchantments are valid, and "any weapon with Sharpness" would have to mean something
     * different for a bow than for a sword.
     */
    public static PresetSlot ofCategory(ItemCategory category) {
        return new PresetSlot(null, 1, null, null, category);
    }

    /** Same item and count, with a different enchantment condition. */
    public PresetSlot withEnchants(EnchantRequirement req) {
        return new PresetSlot(itemId, count, components, req, category);
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

    public int count() {
        return count;
    }

    public JsonElement rawComponents() {
        return components;
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
        if (itemId == null) {
            o.addProperty("blank", true);
            return o;
        }
        o.addProperty("item", itemId.toString());
        o.addProperty("count", count);
        if (components != null && !components.isJsonNull()) {
            o.add("components", components);
        }
        if (!enchants.isNoop()) {
            o.add("enchants", enchants.toJson());
        }
        if (category != null) {
            o.addProperty("category", category.name());
        }
        return o;
    }

    public static PresetSlot fromJson(JsonObject o) {
        if (o.has("blank") && o.get("blank").getAsBoolean()) {
            return blank();
        }
        if (!o.has("item")) {
            return blank();
        }
        Identifier id = Identifier.tryParse(o.get("item").getAsString());
        if (id == null) {
            return blank();
        }
        int c = o.has("count") ? o.get("count").getAsInt() : 1;
        JsonElement comps = o.has("components") ? o.get("components") : null;
        EnchantRequirement req = o.has("enchants") && o.get("enchants").isJsonObject()
                ? EnchantRequirement.fromJson(o.getAsJsonObject("enchants"))
                : EnchantRequirement.ignore();
        ItemCategory cat = o.has("category")
                ? ItemCategory.byName(o.get("category").getAsString())
                : null;
        if (cat != null) {
            return ofCategory(cat);
        }
        return of(id, c, comps, req);
    }
}