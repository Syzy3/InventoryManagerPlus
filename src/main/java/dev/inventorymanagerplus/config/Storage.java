package dev.inventorymanagerplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.inventorymanagerplus.InventoryManagerPlus;
import dev.inventorymanagerplus.preset.Preset;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code config/inventory-manager-plus/}.
 *
 * <p>Writes go to a temporary file that is then moved into place, so a crash or a full disk during
 * a save cannot leave a half-written presets file behind. Losing a session's worth of layout work
 * to a truncated JSON file is the kind of thing that makes people uninstall a mod.
 */
public final class Storage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Storage() {
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("inventory-manager-plus");
    }

    private static Path configFile() {
        return dir().resolve("config.json");
    }

    private static Path presetsFile() {
        return dir().resolve("presets.json");
    }

    // ---------------------------------------------------------------- config

    public static void loadConfig() {
        Path file = configFile();
        if (!Files.exists(file)) {
            Config.set(new Config());
            saveConfig();
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Config.set(GSON.fromJson(r, Config.class));
        } catch (Exception e) {
            InventoryManagerPlus.LOGGER.error("Could not read config.json, using defaults", e);
            Config.set(new Config());
        }
    }

    public static void saveConfig() {
        try {
            writeAtomically(configFile(), GSON.toJson(Config.get()));
        } catch (IOException e) {
            InventoryManagerPlus.LOGGER.error("Could not write config.json", e);
        }
    }

    // ---------------------------------------------------------------- presets

    public static List<Preset> loadPresets() {
        List<Preset> out = new ArrayList<>();
        Path file = presetsFile();
        if (!Files.exists(file)) {
            return out;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(r);
            if (!root.isJsonObject()) {
                return out;
            }
            JsonArray arr = root.getAsJsonObject().getAsJsonArray("presets");
            if (arr == null) {
                return out;
            }
            for (var el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                try {
                    out.add(Preset.fromJson(el.getAsJsonObject()));
                } catch (Exception e) {
                    // One malformed preset must not take the rest of the file down with it.
                    InventoryManagerPlus.LOGGER.warn("Skipping unreadable preset entry", e);
                }
            }
        } catch (Exception e) {
            InventoryManagerPlus.LOGGER.error("Could not read presets.json", e);
        }
        return out;
    }

    public static void savePresets(List<Preset> presets) {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", 1);
        JsonArray arr = new JsonArray();
        for (Preset p : presets) {
            arr.add(p.toJson());
        }
        root.add("presets", arr);
        try {
            writeAtomically(presetsFile(), GSON.toJson(root));
        } catch (IOException e) {
            InventoryManagerPlus.LOGGER.error("Could not write presets.json", e);
        }
    }

    // ---------------------------------------------------------------- io

    private static void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            w.write(content);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some filesystems (notably certain network mounts) cannot do this; a plain replace
            // is still better than writing in place.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
