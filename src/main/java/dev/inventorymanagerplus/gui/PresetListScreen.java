package dev.inventorymanagerplus.gui;

import dev.inventorymanagerplus.InventoryManagerPlus;
import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.config.Storage;
import dev.inventorymanagerplus.preset.Preset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The main screen: one card per preset, each with an icon, an Apply button, an Auto Sort toggle,
 * and edit/delete actions.
 *
 * <p>Cards are laid out in a fixed-height scrolling column. Rather than building a widget list that
 * has to be rebuilt on every scroll, the buttons for each card are created once and repositioned
 * as the scroll offset changes, with off-screen cards' buttons parked outside the visible area.
 */
public final class PresetListScreen extends Screen {

    private static final int CARD_HEIGHT = 34;
    private static final int CARD_GAP = 4;
    private static final int LIST_TOP = 44;
    private static final int LIST_BOTTOM_MARGIN = 40;

    private final Screen parent;
    private int scroll;
    private final java.util.List<Button> themed = new java.util.ArrayList<>();

    public PresetListScreen(Screen parent) {
        super(Component.literal("Inventory Manager+"));
        this.parent = parent;
    }

    private int listWidth() {
        return Math.min(340, this.width - 40);
    }

    private int listLeft() {
        return (this.width - listWidth()) / 2;
    }

    private int listBottom() {
        return this.height - LIST_BOTTOM_MARGIN;
    }

    private int visibleHeight() {
        return listBottom() - LIST_TOP;
    }

    private int contentHeight() {
        return InventoryManagerPlus.presets().all().size() * (CARD_HEIGHT + CARD_GAP);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - visibleHeight());
    }

    @Override
    protected void init() {
        themed.clear();
        List<Preset> presets = InventoryManagerPlus.presets().all();
        int left = listLeft();
        int w = listWidth();

        for (int i = 0; i < presets.size(); i++) {
            Preset preset = presets.get(i);
            int y = cardY(i);
            boolean visible = y >= LIST_TOP - CARD_HEIGHT && y <= listBottom();

            // Apply
            themed.add(addRenderableWidget(Button.builder(Component.literal("Apply"), b -> {
                Minecraft mc = Minecraft.getInstance();
                Component result = InventoryManagerPlus.presets().apply(mc, preset, true);
                InventoryManagerPlus.status(mc, result.getString());
                mc.gui.setScreen(null);
            }).bounds(left + w - 168, offscreen(y + 7, visible), 46, 20).build()));

            // Auto Sort toggle
            themed.add(addRenderableWidget(Button.builder(autoSortLabel(preset), b -> {
                preset.setAutoSort(!preset.autoSort());
                if (preset.autoSort()) {
                    // Turning the switch on implies "watch this one" — without this the player
                    // has to click Apply first for anything to happen, which reads as broken.
                    InventoryManagerPlus.presets().setActive(preset);
                    Config.get().autoSortEnabled = true;
                    Storage.saveConfig();
                }
                InventoryManagerPlus.presets().save();
                rebuild();
            }).bounds(left + w - 118, offscreen(y + 7, visible), 62, 20).build()));

            // Edit
            themed.add(addRenderableWidget(Button.builder(Component.literal("✎"), b ->
                    Minecraft.getInstance().gui.setScreen(new PresetEditorScreen(this, preset, false))
            ).bounds(left + w - 52, offscreen(y + 7, visible), 22, 20).build()));

            // Delete
            themed.add(addRenderableWidget(Button.builder(Component.literal("✖"), b -> confirmDelete(preset))
                    .bounds(left + w - 26, offscreen(y + 7, visible), 22, 20).build()));
        }

        // Footer
        themed.add(addRenderableWidget(Button.builder(Component.literal("+ Create Preset"), b -> {
            Preset preset = Preset.createEmpty("New Preset");
            preset.setMatchMode(Config.get().defaultMatchMode);
            Minecraft.getInstance().gui.setScreen(new PresetEditorScreen(this, preset, true));
        }).bounds(this.width / 2 - 154, this.height - 30, 100, 20).build()));

        themed.add(addRenderableWidget(Button.builder(Component.literal("Settings"), b ->
                Minecraft.getInstance().gui.setScreen(new SettingsScreen(this))
        ).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build()));

        themed.add(addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 + 54, this.height - 30, 100, 20).build()));
    }

    /** Buttons for cards scrolled out of view are moved far off-screen so they cannot be clicked. */
    private static int offscreen(int y, boolean visible) {
        return visible ? y : -1000;
    }

    private int cardY(int index) {
        return LIST_TOP + index * (CARD_HEIGHT + CARD_GAP) - scroll;
    }

    private Component autoSortLabel(Preset preset) {
        return Component.literal("Auto: " + (preset.autoSort() ? "ON" : "OFF"));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void confirmDelete(Preset preset) {
        if (!Config.get().confirmDelete) {
            InventoryManagerPlus.presets().remove(preset);
            rebuild();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ConfirmScreen(
                accepted -> {
                    if (accepted) {
                        InventoryManagerPlus.presets().remove(preset);
                    }
                    mc.gui.setScreen(this);
                    rebuild();
                },
                Component.literal("Delete \"" + preset.name() + "\"?"),
                Component.literal("This cannot be undone. Your items are not affected."),
                Component.literal("Delete"),
                Component.literal("Cancel")));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (dy * 14)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int accent = Theme.current().accent();

        // Low-alpha accent wash over each button: vanilla buttons are textured sprites and cannot
        // be recoloured without a custom widget, but this tints them and keeps the hover state.
        int wash = (accent & 0x00FFFFFF) | 0x38000000;
        for (Button b : themed) {
            graphics.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), wash);
        }

        graphics.text(this.font, "Inventory Manager+", this.width / 2 - 60, 16, accent, true);

        List<Preset> presets = InventoryManagerPlus.presets().all();
        if (presets.isEmpty()) {
            graphics.text(this.font, "No presets yet — create one below.",
                    this.width / 2 - 90, LIST_TOP + 20, accent, false);
            return;
        }

        int left = listLeft();
        int w = listWidth();
        Preset active = InventoryManagerPlus.presets().active();

        // Clip the card area so partially scrolled cards do not bleed into the header/footer.
        graphics.enableScissor(left, LIST_TOP, left + w, listBottom());
        for (int i = 0; i < presets.size(); i++) {
            Preset preset = presets.get(i);
            int y = cardY(i);
            if (y + CARD_HEIGHT < LIST_TOP || y > listBottom()) {
                continue;
            }

            boolean isActive = active != null && active.id().equals(preset.id());
            // Stop short of the button strip. super() draws the widgets before this runs,
            // so a full-width fill would paint straight over them.
            var palette = Theme.current();
            graphics.fill(left, y, left + w - 172, y + CARD_HEIGHT, isActive ? palette.cardActive() : palette.card());
            graphics.outline(left, y, w, CARD_HEIGHT, isActive ? palette.accent() : 0xFF3A3A3A);

            ItemStack icon = iconStack(preset);
            Render.item(graphics, icon, left + 8, y + 9);

            graphics.text(this.font, preset.name(), left + 30, y + 7, 0xFFFFFFFF, false);
            graphics.text(this.font, describe(preset), left + 30, y + 19, accent, false);
        }
        graphics.disableScissor();
    }

    private String describe(Preset preset) {
        long filled = preset.slots().values().stream().filter(s -> !s.isBlank()).count();
        long blanks = preset.slots().size() - filled;
        return filled + " item" + (filled == 1 ? "" : "s")
                + (blanks > 0 ? ", " + blanks + " blank" : "")
                + "  •  " + (preset.matchMode() == dev.inventorymanagerplus.preset.MatchMode.BASIC ? "Basic" : "Exact");
    }

    private ItemStack iconStack(Preset preset) {
        var id = preset.effectiveIcon();
        if (id == null) {
            return ItemStack.EMPTY;
        }
        try {
            return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}