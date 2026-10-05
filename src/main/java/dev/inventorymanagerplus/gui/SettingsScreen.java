package dev.inventorymanagerplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.inventorymanagerplus.ModKeys;
import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.config.Storage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings. Controls cycle through fixed values rather than using sliders or text fields, which
 * keeps the screen free of mouse- and key-event overrides — those signatures changed in 26.x and
 * are the least stable part of the GUI API.
 */
public final class SettingsScreen extends Screen {

    /** Ticks between Auto Sort checks. Lower reacts faster and costs more. */
    private static final int[] INTERVALS = {5, 10, 20, 40};

    /**
     * Ticks between individual inventory operations. Higher looks more like a human clicking,
     * which matters on servers that rate-limit or watch for automation.
     */
    private static final int[] COOLDOWNS = {1, 2, 4, 6, 10};

    /** GLFW key code range worth scanning while listening for a new binding. */
    private static final int FIRST_KEY = 32;
    private static final int LAST_KEY = 348;
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;

    private final Screen parent;
    private final List<Button> themed = new ArrayList<>();
    /** True while waiting for the player to press the key they want. */
    private KeyMapping listening;

    public SettingsScreen(Screen parent) {
        super(Component.literal("InventoryManager+ Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        themed.clear();
        int x = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = 48;
        int row = 24;

        add(Component.literal("Color: " + Theme.current().name()), x, y, 150, b -> {
            Theme.cycle();
            save();
        });

        // Reads the real key binding, the same one Options > Controls shows and edits, so the two
        // screens can never disagree.
        keyButton("Open key", ModKeys.OPEN_MENU, right, y);

        y += row;

        add(Component.literal("Drop: " + onOff(Config.get().dropFromEmptySlots)), x, y, 150, b -> {
            Config.get().dropFromEmptySlots = !Config.get().dropFromEmptySlots;
            save();
        }).setTooltip(Tooltip.create(Component.literal(
                "When you apply a preset, items in slots marked empty get stacked onto the same "
                        + "item elsewhere or moved to a free slot. Only if there's no room left "
                        + "are they thrown on the ground. Auto Sort has its own switch.")));

        add(Component.literal("Check every: " + Config.get().autoSortIntervalTicks + " ticks"), right, y, 150, b -> {
            Config.get().autoSortIntervalTicks = cycle(INTERVALS, Config.get().autoSortIntervalTicks);
            save();
        });

        y += row;

        add(Component.literal("Move delay: " + Config.get().operationCooldownTicks + " ticks"), x, y, 150, b -> {
            Config.get().operationCooldownTicks = cycle(COOLDOWNS, Config.get().operationCooldownTicks);
            save();
        }).setTooltip(Tooltip.create(Component.literal(
                "Ticks between inventory actions. A higher delay looks more like manual clicking,"
                        + " which is safer on strict servers.")));

        add(Component.literal("Status messages: " + onOff(Config.get().showStatusMessages)), right, y, 150, b -> {
            Config.get().showStatusMessages = !Config.get().showStatusMessages;
            save();
        });

        y += row;

        add(Component.literal("Confirm delete: " + onOff(Config.get().confirmDelete)), x, y, 150, b -> {
            Config.get().confirmDelete = !Config.get().confirmDelete;
            save();
        });

        add(Component.literal("Creative auto-get: " + onOff(Config.get().creativeAcquisition)), right, y, 150, b -> {
            Config.get().creativeAcquisition = !Config.get().creativeAcquisition;
            save();
        });

        y += row;

        add(Component.literal("Auto Sort drop: " + onOff(Config.get().autoSortDrop)), x, y, 150, b -> {
            Config.get().autoSortDrop = !Config.get().autoSortDrop;
            save();
        }).setTooltip(Tooltip.create(Component.literal(
                "Lets Auto Sort drop items from slots marked empty too, when there's no free "
                        + "slot to move them to. Careful: with a full inventory, anything you pick "
                        + "up into one of those slots gets thrown right back out.")));

        add(Component.literal("Pause in inventory: " + onOff(Config.get().pauseWhileInventoryOpen)),
                right, y, 150, b -> {
                    Config.get().pauseWhileInventoryOpen = !Config.get().pauseWhileInventoryOpen;
                    save();
                });

        y += row;

        // Quick keys. The same bindings as Options > Controls, so changing one here changes it
        // there too.
        keyButton("Apply key", ModKeys.APPLY_ACTIVE, x, y).setTooltip(Tooltip.create(Component.literal(
                "Applies the active preset (the one you applied last) without opening the menu. "
                        + "Click, then press a key. Backspace clears it, Escape cancels.")));
        keyButton("Auto Sort key", ModKeys.TOGGLE_AUTO_SORT, right, y).setTooltip(Tooltip.create(
                Component.literal("Turns Auto Sort on or off for the active preset. "
                        + "Click, then press a key. Backspace clears it, Escape cancels.")));

        add(Component.literal("Done"), this.width / 2 - 75, this.height - 30, 150, b -> onClose());}

    /**
     * A button showing a key binding. Reads the real binding, the same one Options > Controls
     * shows and edits, so the two screens can never disagree. Click it, then press a key.
     */
    private Button keyButton(String label, KeyMapping key, int x, int y) {
        String shown = listening == key ? "..." : key.getTranslatedKeyMessage().getString();
        return add(Component.literal(label + ": " + shown), x, y, 150, b -> {
            listening = key;
            rebuild();
        });
    }

    private Button add(Component label, int x, int y, int w, Button.OnPress action) {
        Button button = ThemedButton.create(label, action).bounds(x, y, w, 20).build();
        addRenderableWidget(button);
        themed.add(button);
        return button;
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void save() {
        Storage.saveConfig();
        rebuild();
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    /** Next value in the list, wrapping; falls back to the first if the current value is custom. */
    private static int cycle(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    /**
     * Watches the raw keyboard while listening for a new binding.
     *
     * <p>Polling the key state each frame instead of overriding {@code keyPressed} is deliberate:
     * the key-event signature changed in 26.x along with the mouse one, and polling reads the same
     * GLFW state without depending on it. Escape cancels; Backspace clears the binding.
     */
    private void pollForNewKey() {
        var window = Minecraft.getInstance().getWindow();

        if (InputConstants.isKeyDown(window, KEY_ESCAPE)) {
            listening = null;
            rebuild();
            return;
        }
        if (InputConstants.isKeyDown(window, KEY_BACKSPACE)) {
            bind(InputConstants.UNKNOWN);
            return;
        }

        for (int code = FIRST_KEY; code <= LAST_KEY; code++) {
            if (InputConstants.isKeyDown(window, code)) {
                bind(InputConstants.Type.KEYSYM.getOrCreate(code));
                return;
            }
        }
    }

    /**
     * Sets the key on Minecraft's own binding, exactly as the Controls screen does, and saves
     * options.txt. There is no separate copy in the mod's config to fall out of step.
     */
    private void bind(InputConstants.Key key) {
        listening.setKey(key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        listening = null;
        save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int accent = Theme.current().accent();


        graphics.text(this.font, "Settings", this.width / 2 - 20, 20, accent, true);

        if (listening != null) {
            pollForNewKey();
        }
    }

    @Override
    public void onClose() {
        if (listening != null) {
            // Escape while choosing a key cancels the choice instead of leaving the screen.
            listening = null;
            rebuild();
            return;
        }
        Storage.saveConfig();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}