package dev.inventorymanagerplus.gui;

import dev.inventorymanagerplus.InventoryManagerPlus;
import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.preset.Preset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
    /**
     * Buttons on the preset cards. They take clicks like any widget but are drawn by this screen
     * inside the list's clip area, so a card scrolled half out of the list shows half its buttons
     * instead of drawing them over the header or the footer buttons.
     */
    private final java.util.List<Button> cardButtons = new java.util.ArrayList<>();

    public PresetListScreen(Screen parent) {
        super(Component.literal("InventoryManager+"));
        this.parent = parent;
    }

    private int listWidth() {
        return Math.min(380, this.width - 40);
    }

    /** Left edge of the right-hand button strip, relative to the card's left edge. */
    private static final int BUTTONS_FROM_RIGHT = 198;
    /** Width of the reorder-arrow column at the card's left edge. */
    private static final int ARROWS_W = 17;

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
        // Deleting presets shrinks the list; without this the cards stay scrolled off the top.
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        themed.clear();
        cardButtons.clear();
        List<Preset> presets = InventoryManagerPlus.presets().all();
        int left = listLeft();
        int w = listWidth();

        for (int i = 0; i < presets.size(); i++) {
            Preset preset = presets.get(i);
            int y = cardY(i);
            // Any part of the card inside the list counts; the clip hides the rest.
            boolean visible = y + CARD_HEIGHT > LIST_TOP && y < listBottom();
            int firstButton = cardButtons.size();

            // Reorder arrows, stacked at the card's left edge.
            Button up = ThemedButton.create(Component.literal("▲"), b -> {
                InventoryManagerPlus.presets().move(preset, -1);
                rebuild();
            }).bounds(left + 3, y + 3, 12, 13).build();
            up.active = i > 0;
            up.setTooltip(Tooltip.create(Component.literal("Move up")));
            themed.add(card(up));

            Button down = ThemedButton.create(Component.literal("▼"), b -> {
                InventoryManagerPlus.presets().move(preset, 1);
                rebuild();
            }).bounds(left + 3, y + 18, 12, 13).build();
            down.active = i < presets.size() - 1;
            down.setTooltip(Tooltip.create(Component.literal("Move down")));
            themed.add(card(down));

            // Apply
            themed.add(card(ThemedButton.create(Component.literal("Apply"), b -> {
                Minecraft mc = Minecraft.getInstance();
                Component result = InventoryManagerPlus.presets().apply(mc, preset, true);
                InventoryManagerPlus.status(mc, result.getString());
                mc.gui.setScreen(null);
            }).bounds(left + w - 194, y + 7, 46, 20).build()));

            // Auto Sort toggle
            themed.add(card(ThemedButton.create(autoSortLabel(preset), b -> {
                // Only one preset sorts at a time, so only one switch can be on.
                InventoryManagerPlus.presets().setAutoSort(preset, !preset.autoSort());
                rebuild();
            }).bounds(left + w - 144, y + 7, 62, 20).build()));

            // Duplicate
            Button dup = ThemedButton.create(Component.literal("⧉"), b -> {
                InventoryManagerPlus.presets().duplicate(preset);
                rebuild();
            }).bounds(left + w - 78, y + 7, 22, 20).build();
            dup.setTooltip(Tooltip.create(Component.literal("Duplicate")));
            themed.add(card(dup));

            // Edit
            Button edit = ThemedButton.create(Component.literal("✎"), b ->
                    Minecraft.getInstance().gui.setScreen(new PresetEditorScreen(this, preset, false))
            ).bounds(left + w - 52, y + 7, 22, 20).build();
            edit.setTooltip(Tooltip.create(Component.literal("Edit")));
            themed.add(card(edit));

            // Delete
            Button del = ThemedButton.create(Component.literal("✖"), b -> confirmDelete(preset))
                    .bounds(left + w - 26, y + 7, 22, 20).build();
            del.setTooltip(Tooltip.create(Component.literal("Delete")));
            themed.add(card(del));

            for (Button b : cardButtons.subList(firstButton, cardButtons.size())) {
                b.visible = visible;
            }
        }

        // Footer
        themed.add(addRenderableWidget(ThemedButton.create(Component.literal("+ Create Preset"), b -> {
            Preset preset = Preset.createEmpty("New Preset");
            Minecraft.getInstance().gui.setScreen(new PresetEditorScreen(this, preset, true));
        }).bounds(this.width / 2 - 154, this.height - 30, 100, 20).build()));

        themed.add(addRenderableWidget(ThemedButton.create(Component.literal("Settings"), b ->
                Minecraft.getInstance().gui.setScreen(new SettingsScreen(this))
        ).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build()));

        themed.add(addRenderableWidget(ThemedButton.create(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 + 54, this.height - 30, 100, 20).build()));
    }

    /** Registers a card button for clicks only; it is drawn clipped in extractRenderState. */
    private Button card(Button button) {
        if (button instanceof ThemedButton themedButton) {
            themedButton.clipVertically(LIST_TOP, listBottom());
        }
        addWidget(button);
        cardButtons.add(button);
        return button;
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
        mc.gui.setScreen(new ThemedConfirmScreen(
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


        graphics.text(this.font, "InventoryManager+", this.width / 2 - 60, 16, accent, true);

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
            // Also stops short of the reorder arrows on the left, for the same reason.
            graphics.fill(left + ARROWS_W, y, left + w - BUTTONS_FROM_RIGHT, y + CARD_HEIGHT,
                    isActive ? palette.cardActive() : palette.card());
            graphics.outline(left, y, w, CARD_HEIGHT, isActive ? palette.accent() : Theme.border());

            ItemStack icon = iconStack(preset);
            Render.item(graphics, icon, left + ARROWS_W + 3, y + 9);

            int textX = left + ARROWS_W + 24;
            int textW = w - BUTTONS_FROM_RIGHT - ARROWS_W - 28;
            graphics.text(this.font, fit(preset.name(), textW), textX, y + 7, 0xFFFFFFFF, false);
            graphics.text(this.font, fit(describe(preset), textW), textX, y + 19, accent, false);
        }
        for (Button b : cardButtons) {
            b.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        graphics.disableScissor();
    }

    private String describe(Preset preset) {
        long filled = preset.slots().values().stream().filter(s -> !s.isBlank()).count();
        long blanks = preset.slots().size() - filled;
        return filled + " item" + (filled == 1 ? "" : "s")
                + (blanks > 0 ? ", " + blanks + " blank" : "")
                + (preset.hasHotkey()
                        ? "  •  Key: " + InputConstants.Type.KEYSYM.getOrCreate(preset.hotkey())
                                .getDisplayName().getString()
                        : "");
    }

    /** Shortens text to fit a width, ending in "..." when cut. */
    private String fit(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && this.font.width(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
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