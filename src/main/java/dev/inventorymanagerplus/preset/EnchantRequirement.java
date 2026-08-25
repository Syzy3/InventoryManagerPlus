package dev.inventorymanagerplus.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An optional enchantment condition attached to a preset slot.
 *
 * <p>Deliberately independent of {@link MatchMode}. EXACT already compares the whole component
 * blob, which is all-or-nothing: it distinguishes a Sharpness V sword from a plain one, but it
 * cannot express "any Efficiency pickaxe, I don't care what level or what else is on it". That
 * middle ground is the useful one in practice, so it lives here instead.
 *
 * <p>Like {@link PresetSlot}, this stores enchantment <em>identifiers</em> rather than registry
 * objects. Presets are read from disk at client startup, long before the enchantment registry is
 * bound, so anything holding a live {@code Holder<Enchantment>} would blow up on load.
 */
public final class EnchantRequirement {

    /** Level value meaning "any level of this enchantment will do". */
    public static final int ANY_LEVEL = 0;

    public enum Mode {
        /** No condition. The slot matches regardless of enchantments. */
        IGNORE("Ignore enchantments"),
        /** The stack must carry at least one enchantment, whatever it is. */
        ANY("Must be enchanted"),
        /** Every entry in {@link #levels()} must be present on the stack. */
        SPECIFIC("Specific enchantments");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private Mode mode = Mode.IGNORE;
    /** Enchantment id to required level, or {@link #ANY_LEVEL}. Insertion-ordered for stable UI. */
    private final Map<Identifier, Integer> levels = new LinkedHashMap<>();

    public static EnchantRequirement ignore() {
        return new EnchantRequirement();
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Map<Identifier, Integer> levels() {
        return levels;
    }

    /** True when this requirement would never reject anything, and so needn't be saved. */
    public boolean isNoop() {
        return mode == Mode.IGNORE || (mode == Mode.SPECIFIC && levels.isEmpty());
    }

    public EnchantRequirement copy() {
        EnchantRequirement c = new EnchantRequirement();
        c.mode = this.mode;
        c.levels.putAll(this.levels);
        return c;
    }

    /** Short text for the editor, e.g. "Sharpness V, Unbreaking (any)". */
    public String describe() {
        // Driven by what has actually been picked rather than by the stored mode: while the
        // picker is open the mode is not settled until Done, so keying off it made the summary
        // sit on "any" no matter what you selected.
        if (!levels.isEmpty()) {
            return describeLevels();
        }
        if (mode == Mode.ANY) {
            return "enchanted";
        }
        return "any";
    }

    /** One line listing the chosen enchantments, e.g. "Sharpness V+, Unbreaking (any)". */
    private String describeLevels() {
        StringBuilder sb = new StringBuilder();
        for (var e : levels.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(prettyName(e.getKey()));
            sb.append(e.getValue() == ANY_LEVEL ? " (any)" : " " + roman(e.getValue()) + "+");
        }
        return sb.toString();
    }

    /** Each chosen enchantment as its own display line, in the order they were added. */
    public java.util.List<String> describeLines() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (mode == Mode.ANY && levels.isEmpty()) {
            out.add("Any enchantment");
            return out;
        }
        for (var e : levels.entrySet()) {
            out.add(prettyName(e.getKey())
                    + (e.getValue() == ANY_LEVEL ? " (any level)" : " " + roman(e.getValue()) + " or higher"));
        }
        return out;
    }

    /** "minecraft:fire_aspect" -> "Fire Aspect", without touching the registry. */
    public static String prettyName(Identifier id) {
        String path = id.getPath().replace('_', ' ');
        StringBuilder sb = new StringBuilder(path.length());
        boolean upper = true;
        for (char c : path.toCharArray()) {
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return sb.toString();
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    // ---------------------------------------------------------------- serialisation

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("mode", mode.name());
        if (!levels.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (var e : levels.entrySet()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", e.getKey().toString());
                entry.addProperty("level", e.getValue());
                arr.add(entry);
            }
            o.add("entries", arr);
        }
        return o;
    }

    public static EnchantRequirement fromJson(JsonObject o) {
        EnchantRequirement r = new EnchantRequirement();
        if (o == null) {
            return r;
        }
        if (o.has("mode")) {
            try {
                r.mode = Mode.valueOf(o.get("mode").getAsString());
            } catch (IllegalArgumentException ignored) {
                // Unknown mode from a newer version of the mod: fall back to no condition
                // rather than dropping the whole preset.
                r.mode = Mode.IGNORE;
            }
        }
        if (o.has("entries") && o.get("entries").isJsonArray()) {
            for (var el : o.getAsJsonArray("entries")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject entry = el.getAsJsonObject();
                if (!entry.has("id")) {
                    continue;
                }
                Identifier id = Identifier.tryParse(entry.get("id").getAsString());
                if (id == null) {
                    continue;
                }
                int lvl = entry.has("level") ? entry.get("level").getAsInt() : ANY_LEVEL;
                r.levels.put(id, Math.max(ANY_LEVEL, lvl));
            }
        }
        return r;
    }
}