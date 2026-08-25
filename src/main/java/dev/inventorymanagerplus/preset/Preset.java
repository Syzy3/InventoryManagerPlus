package dev.inventorymanagerplus.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A named, <strong>partial</strong> description of where certain items should live.
 *
 * <p>The partiality is the whole point. Slots absent from {@link #slots()} are <em>unmanaged</em>:
 * the mod has no opinion about them and must preserve whatever they contain.
 */
public final class Preset {

    private final String id;
    private String name;
    /** inventory slot index -> requirement. Sparse. Keys are validated against {@link dev.inventorymanagerplus.inventory.InvSlots}. */
    private final Map<Integer, PresetSlot> slots;
    private Identifier icon;
    private MatchMode matchMode;
    private boolean autoSort;

    public Preset(String id, String name, Map<Integer, PresetSlot> slots,
                  Identifier icon, MatchMode matchMode, boolean autoSort) {
        this.id = id;
        this.name = name;
        this.slots = new HashMap<>(slots);
        this.icon = icon;
        this.matchMode = matchMode;
        this.autoSort = autoSort;
    }

    public static Preset createEmpty(String name) {
        return new Preset(UUID.randomUUID().toString(), name, Map.of(), null, MatchMode.BASIC, false);
    }

    public Preset duplicate() {
        return new Preset(UUID.randomUUID().toString(), name + " (copy)", slots, icon, matchMode, autoSort);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "Unnamed" : name;
    }

    /** Live, mutable view — the editor writes straight into it. */
    public Map<Integer, PresetSlot> slots() {
        return slots;
    }

    public boolean manages(int slot) {
        return slots.containsKey(slot);
    }

    public MatchMode matchMode() {
        return matchMode;
    }

    public void setMatchMode(MatchMode m) {
        this.matchMode = m;
    }

    public boolean autoSort() {
        return autoSort;
    }

    public void setAutoSort(boolean b) {
        this.autoSort = b;
    }

    /**
     * Explicit icon if one was chosen, otherwise the first non-blank item scanning the hotbar
     * first (slots 0-8), which is almost always the item the player thinks of as defining the
     * preset — the sword in a PvP layout, the pickaxe in a Mining layout.
     */
    public Identifier effectiveIcon() {
        if (icon != null) {
            return icon;
        }
        for (int i = 0; i < 41; i++) {
            PresetSlot s = slots.get(i);
            if (s != null && !s.isBlank()) {
                return s.itemId();
            }
        }
        return null;
    }

    public void setIcon(Identifier icon) {
        this.icon = icon;
    }

    // ---------------------------------------------------------------- serialisation

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("matchMode", matchMode.name());
        o.addProperty("autoSort", autoSort);
        if (icon != null) {
            o.addProperty("icon", icon.toString());
        }
        JsonArray arr = new JsonArray();
        slots.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    JsonObject entry = e.getValue().toJson();
                    entry.addProperty("slot", e.getKey());
                    arr.add(entry);
                });
        o.add("slots", arr);
        return o;
    }

    public static Preset fromJson(JsonObject o) {
        String id = o.has("id") ? o.get("id").getAsString() : UUID.randomUUID().toString();
        String name = o.has("name") ? o.get("name").getAsString() : "Unnamed";

        MatchMode mode = MatchMode.BASIC;
        if (o.has("matchMode")) {
            try {
                mode = MatchMode.valueOf(o.get("matchMode").getAsString());
            } catch (IllegalArgumentException ignored) {
                // Unknown mode from a newer version of the mod: fall back rather than lose the preset.
            }
        }

        boolean auto = o.has("autoSort") && o.get("autoSort").getAsBoolean();
        Identifier icon = o.has("icon") ? Identifier.tryParse(o.get("icon").getAsString()) : null;

        Map<Integer, PresetSlot> slots = new HashMap<>();
        if (o.has("slots")) {
            for (var el : o.getAsJsonArray("slots")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject entry = el.getAsJsonObject();
                if (!entry.has("slot")) {
                    continue;
                }
                int slot = entry.get("slot").getAsInt();
                slots.put(slot, PresetSlot.fromJson(entry));
            }
        }
        return new Preset(id, name, slots, icon, mode, auto);
    }
}
