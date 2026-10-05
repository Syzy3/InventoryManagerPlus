package dev.inventorymanagerplus.preset;

import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.InputConstants;
import dev.inventorymanagerplus.Keys;
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
    private boolean autoSort;
    /**
     * Keyboard key (scancode, see {@link dev.inventorymanagerplus.Keys}) that applies this preset
     * from anywhere in game; -1 for none.
     */
    private int hotkey = -1;

    public Preset(String id, String name, Map<Integer, PresetSlot> slots,
                  Identifier icon, boolean autoSort) {
        this.id = id;
        this.name = name;
        this.slots = new HashMap<>(slots);
        this.icon = icon;
        this.autoSort = autoSort;
    }

    public static Preset createEmpty(String name) {
        return new Preset(UUID.randomUUID().toString(), name, Map.of(), null, false);
    }

    /**
     * A copy with a new id and " (copy)" on the name. Auto Sort and the hotkey are not copied:
     * only one preset can sort at a time, and one key should apply one preset.
     */
    public Preset duplicate() {
        return new Preset(UUID.randomUUID().toString(), name + " (copy)", slots, icon, false);
    }

    public int hotkey() {
        return hotkey;
    }

    public void setHotkey(int key) {
        this.hotkey = key;
    }

    public boolean hasHotkey() {
        return hotkey >= 0;
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
                return s.isCategory() ? Identifier.tryParse(s.category().iconId()) : s.itemId();
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
        o.addProperty("autoSort", autoSort);
        if (icon != null) {
            o.addProperty("icon", icon.toString());
        }
        if (hotkey >= 0) {
            // Saved as the GLFW code so 26.2 and 26.3 builds can share preset files. The key
            // name is saved too, for keys that have no GLFW code.
            int saved = Keys.toSaved(hotkey);
            if (saved >= 0) {
                o.addProperty("hotkey", saved);
            }
            o.addProperty("hotkeyName", Keys.key(hotkey).getName());
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

        // Older saves carry a "matchMode" field; it no longer means anything and is ignored.

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
                PresetSlot spec = PresetSlot.fromJson(entry);
                // Armour (36-39) and the off-hand (40) never use "keep empty".
                if (spec.isBlank() && slot >= 36) {
                    continue;
                }
                slots.put(slot, spec);
            }
        }
        Preset preset = new Preset(id, name, slots, icon, auto);
        int key = o.has("hotkey") ? Keys.fromSaved(o.get("hotkey").getAsInt()) : -1;
        if (key < 0 && o.has("hotkeyName")) {
            InputConstants.Key named = InputConstants.getKey(o.get("hotkeyName").getAsString());
            if (named.getType() == InputConstants.Type.KEYBOARD && named != InputConstants.UNKNOWN) {
                key = named.getValue();
            }
        }
        if (key >= 0) {
            preset.setHotkey(key);
        }
        return preset;
    }
}
