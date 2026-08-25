package dev.inventorymanagerplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.inventorymanagerplus.ModKeys;
import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.config.Storage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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

    private final Screen parent;
    private final List<Button> themed = new ArrayList<>();
    /** True while waiting for the player to press the key they want. */
    private boolean listening;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Inventory Manager+ Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        themed.clear();
        int x = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = 48;
        int row = 24;

        add(Component.literal("Colour: " + Theme.current().name()), x, y, 150, b -> {
            Theme.cycle();
            save();
        });

        add(Component.literal(listening ? "Open key: ..." : "Open key: " + keyName(Config.get().openKeyCode)),
                right, y, 150, b -> {
                    listening = true;
                    rebuild();
                });

        y += row;

        add(Component.literal("Auto Sort: " + onOff(Config.get().autoSortEnabled)), x, y, 150, b -> {
            Config.get().autoSortEnabled = !Config.get().autoSortEnabled;
            save();
        });

        add(Component.literal("Check every: " + Config.get().autoSortIntervalTicks + " ticks"), right, y, 150, b -> {
            Config.get().autoSortIntervalTicks = cycle(INTERVALS, Config.get().autoSortIntervalTicks);
            save();
        });

        y += row;

        add(Component.literal("Move delay: " + Config.get().operationCooldownTicks + " ticks"), x, y, 150, b -> {
            Config.get().operationCooldownTicks = cycle(COOLDOWNS, Config.get().operationCooldownTicks);
            save();
        });

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

        add(Component.literal("Drop items: " + onOff(Config.get().creativeDropItems)), x, y, 150, b -> {
            Config.get().creativeDropItems = !Config.get().creativeDropItems;
            save();
        });

        add(Component.literal("Pause in inventory: " + onOff(Config.get().pauseWhileInventoryOpen)),
                right, y, 150, b -> {
                    Config.get().pauseWhileInventoryOpen = !Config.get().pauseWhileInventoryOpen;
                    save();
                });

        add(Component.literal("Done"), this.width / 2 - 75, this.height - 30, 150, b -> onClose());}

    private void add(Component label, int x, int y, int w, Button.OnPress action) {
        Button button = Button.builder(label, action).bounds(x, y, w, 20).build();
        addRenderableWidget(button);
        themed.add(button);
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

    private static String keyName(int code) {
        return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
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
     * GLFW state without depending on it. Escape cancels rather than binding, matching vanilla.
     */
    private void pollForNewKey() {
        var window = Minecraft.getInstance().getWindow();

        if (InputConstants.isKeyDown(window, KEY_ESCAPE)) {
            listening = false;
            rebuild();
            return;
        }

        for (int code = FIRST_KEY; code <= LAST_KEY; code++) {
            if (!InputConstants.isKeyDown(window, code)) {
                continue;
            }
            Config.get().openKeyCode = code;
            // Push it through Minecraft's own key system so it also shows in Options > Controls
            // and persists in options.txt like any vanilla binding.
            ModKeys.OPEN_MENU.setKey(InputConstants.Type.KEYSYM.getOrCreate(code));
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
            listening = false;
            save();
            return;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int accent = Theme.current().accent();

        // Vanilla buttons are textured sprites and cannot be recoloured without a custom widget,
        // so a low-alpha accent wash tints them while leaving the bevel and hover state intact.
        int wash = (accent & 0x00FFFFFF) | 0x38000000;
        for (Button b : themed) {
            graphics.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), wash);
        }

        graphics.text(this.font, "Settings", this.width / 2 - 20, 20, accent, true);
        graphics.text(this.font,
                listening
                        ? "Press any key to bind it, or Escape to cancel."
                        : "Higher move delay looks more like manual clicking — safer on strict servers.",
                this.width / 2 - 180, this.height - 48, accent, false);

        if (listening) {
            pollForNewKey();
        }
    }

    @Override
    public void onClose() {
        Storage.saveConfig();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}