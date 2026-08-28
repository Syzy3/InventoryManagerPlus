package dev.inventorymanagerplus.gui;

import com.google.gson.JsonObject;
import dev.inventorymanagerplus.InventoryManagerPlus;
import dev.inventorymanagerplus.inventory.InvSlots;
import dev.inventorymanagerplus.inventory.ItemMatcher;
import dev.inventorymanagerplus.preset.EnchantCatalog;
import dev.inventorymanagerplus.preset.ItemCategory;
import dev.inventorymanagerplus.preset.Preset;
import dev.inventorymanagerplus.preset.PresetSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Preset editor. The slot grid mirrors the vanilla inventory — three rows of nine, the hotbar
 * underneath, off-hand to its left and the four armour slots to its right — so a preset can be
 * read at a glance without translating slot numbers.
 *
 * <p>Each slot is a real {@link Button} widget rather than a hit-test inside a mouse handler,
 * because {@code Screen#mouseClicked} changed shape in 26.x and {@code hasShiftDown} was removed.
 * Widgets handle their own input and are stable across those changes.
 */
public final class PresetEditorScreen extends Screen {

    /** What clicking a slot does. */
    private enum ClickMode {
        COPY("Copy from inventory"),
        BROWSE("Browse items");

        final String label;

        ClickMode(String label) {
            this.label = label;
        }

        ClickMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final int SLOT = 20;
    /** Armour drawn helmet-first, left to right, which is the order players think in. */
    private static final int[] ARMOR_DISPLAY_ORDER = {39, 38, 37, 36};

    private final Screen parent;
    private final Preset preset;
    private final boolean isNew;

    /**
     * The preset exactly as it was when this screen opened.
     *
     * <p>Needed because {@link Preset#slots()} is a live map: when editing an existing preset this
     * screen writes straight into the object {@code PresetManager} is holding. Without a snapshot
     * to restore, Cancel would only skip the write to disk — the edits would still be sitting in
     * memory, ready to be persisted by the next unrelated save.
     */
    private final JsonObject original;

    private ClickMode mode = ClickMode.COPY;
    private EditBox nameBox;
    private int gridLeft;
    private int gridTop;

    // ---- amount prompt ---------------------------------------------------------------------

    private static final int PROMPT_W = 200;
    private static final int PROMPT_H = 104;

    /** Slot the amount prompt is filling in, or -1 when no prompt is open. */
    private int amountSlot = -1;
    /** Item the prompt is asking about. */
    private Identifier amountItemId;
    /** Largest stack this item allows: 16 for eggs, 64 for obsidian, 1 for a sword. */
    private int amountMax = 1;
    /** Value the box starts on — the count copied from the inventory, or 1 when browsing. */
    private int amountStart = 1;
    private EditBox amountBox;
    /**
     * The prompt's widgets, registered for input only.
     *
     * <p>They are added with {@code addWidget} rather than {@code addRenderableWidget} because
     * everything a screen draws in {@link #extractRenderState} lands on top of every widget it
     * renders. Registered normally, the panel and its dimming would cover its own text box. Held
     * here instead, they are drawn by hand after the dimming, which puts them where they belong.
     */
    private final java.util.List<AbstractWidget> promptWidgets = new java.util.ArrayList<>();

    public PresetEditorScreen(Screen parent, Preset preset, boolean isNew) {
        super(Component.literal(isNew ? "Create Preset" : "Edit Preset"));
        this.parent = parent;
        this.preset = preset;
        this.isNew = isNew;
        this.original = preset.toJson();
    }

    @Override
    protected void init() {
        gridLeft = this.width / 2 - (9 * SLOT) / 2;
        gridTop = 84;

        nameBox = new EditBox(this.font, this.width / 2 - 100, 34, 200, 20, Component.literal("Preset name"));
        nameBox.setMaxLength(48);
        nameBox.setValue(preset.name());
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.literal("Click does: " + mode.label), b -> {
            mode = mode.next();
            rebuild();
        }).bounds(this.width / 2 - 100, 58, 200, 20).build());

        // One button per slot. Item icons are drawn afterwards, on top of them.
        for (int i = 0; i < InvSlots.STORAGE_SIZE; i++) {
            addSlotButton(i);
        }
        addSlotButton(InvSlots.OFFHAND);
        for (int armor : ARMOR_DISPLAY_ORDER) {
            addSlotButton(armor);
        }

        int controlsTop = gridTop + 3 * SLOT + 8 + SLOT + 16;

        addRenderableWidget(Button.builder(Component.literal("Capture Hotbar"), b -> {
            captureHotbar();
            rebuild();
        }).bounds(this.width / 2 - 154, controlsTop, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Capture Inventory"), b -> {
            captureInventory();
            rebuild();
        }).bounds(this.width / 2 - 50, controlsTop, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Clear All"), b -> {
            preset.slots().clear();
            rebuild();
        }).bounds(this.width / 2 + 54, controlsTop, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 - 104, this.height - 28, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Save Preset"), b -> save())
                .bounds(this.width / 2 + 4, this.height - 28, 100, 20).build());

        if (promptOpen()) {
            buildAmountPrompt();
        }
    }

    private void addSlotButton(int index) {
        int[] p = slotPos(index);
        if (p == null) {
            return;
        }
        addRenderableWidget(Button.builder(Component.empty(), b -> {
            handleSlotClick(index);
            rebuild();
        }).bounds(p[0], p[1], Render.SLOT_INNER, Render.SLOT_INNER).build());
    }

    private void rebuild() {
        if (nameBox != null) {
            preset.setName(nameBox.getValue());
        }
        clearWidgets();
        init();
    }

    private void save() {
        preset.setName(nameBox.getValue());
        if (isNew) {
            InventoryManagerPlus.presets().add(preset);
        } else {
            InventoryManagerPlus.presets().save();
        }
        closeToParent();
    }

    /**
     * Snapshots hotbar slots 0-8. Empty slots become explicit blanks, because on a nine-slot bar
     * "leave this one free" is usually deliberate.
     */
    private void captureHotbar() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                preset.slots().put(i, PresetSlot.blank());
            } else {
                record(i, stack);
            }
        }
    }

    /**
     * Snapshots the three main rows plus the off-hand, leaving the hotbar untouched.
     *
     * <p>Empty slots are skipped rather than marked blank. Twenty-seven forced blanks would make
     * the preset demand a nearly empty backpack, and every unrelated item picked up afterwards
     * would get shuffled around trying to satisfy it.
     */
    private void captureInventory() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (int i = InvSlots.MAIN_START; i <= InvSlots.MAIN_END; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                record(i, stack);
            }
        }
        ItemStack offhand = player.getInventory().getItem(InvSlots.OFFHAND);
        if (!offhand.isEmpty()) {
            record(InvSlots.OFFHAND, offhand);
        }
    }

    private void record(int slot, ItemStack stack) {
        var id = ItemMatcher.idOf(stack);
        if (id != null) {
            preset.slots().put(slot, PresetSlot.of(id, stack.getCount(), null));
        }
    }

    private void handleSlotClick(int slot) {
        if (mode == ClickMode.BROWSE) {
            // Armour slots list only wearable items, so an unwearable choice is impossible
            // rather than merely rejected after the fact.
            java.util.function.Predicate<net.minecraft.world.item.Item> filter =
                    InvSlots.isArmor(slot)
                            ? item -> ItemCategory.fitsArmorSlot(item, slot)
                            : null;
            // The prompt is opened rather than shown here: the picker is still the active screen
            // at this point, and closes to the editor immediately afterwards, whose init() builds
            // the panel.
            Minecraft.getInstance().gui.setScreen(new ItemPickerScreen(this,
                    id -> openAmountPrompt(slot, id, 1), filter));
            return;
        }

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack live = player.getInventory().getItem(slot);
        if (!live.isEmpty() && InvSlots.isArmor(slot)
                && !ItemCategory.fitsArmorSlot(live.getItem(), slot)) {
            // Nothing but armour belongs in an armour slot, however it got there.
            return;
        }
        if (live.isEmpty()) {
            // Copying an empty slot clears the entry, so a click is also how you undo one.
            preset.slots().remove(slot);
            return;
        }
        Identifier id = ItemMatcher.idOf(live);
        if (id != null) {
            // Seeded with the count actually held, since that is usually the amount wanted.
            openAmountPrompt(slot, id, live.getCount());
        }
    }

    /**
     * Right-click opens the enchantment picker for whichever slot is under the cursor.
     *
     * <p>Handled here rather than on the slot widgets because vanilla {@link Button} only reacts
     * to the left mouse button, and subclassing it in 26.2 would mean reimplementing
     * {@code extractContents} — a lot of drawing code to gain one extra click.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (promptOpen()) {
            // The panel owns the screen while it is up, so a right-click must not reach the grid.
            // Left clicks still go to super, which is how the box and its buttons work.
            return event.button() == 1 || super.mouseClicked(event, doubleClick);
        }
        if (event.button() == 1) {
            int slot = hoveredSlot((int) event.x(), (int) event.y());
            if (slot >= 0) {
                openSlotOptions(slot);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Right-click target. Routed by what the slot holds, so the gesture always lands on the
     * thing that makes sense there: enchantable gear goes straight to the enchantment picker,
     * anything else to the category picker.
     */
    private void openSlotOptions(int slot) {
        PresetSlot spec = preset.slots().get(slot);

        boolean enchantable = spec != null && !spec.isCategory() && !spec.isBlank()
                && EnchantCatalog.isEnchantable(spec.itemId());
        if (enchantable) {
            openEnchantPicker(slot);
            return;
        }

        // Armour slots take a specific piece or nothing. "Any Armor" cannot work here: the
        // helmet slot would happily accept boots, and the preset would report itself satisfied
        // while leaving the player bare-headed.
        if (InvSlots.isArmor(slot)) {
            return;
        }

        ItemCategory currentCat = spec != null && spec.isCategory() ? spec.category() : null;
        Minecraft.getInstance().gui.setScreen(new CategoryPickerScreen(this, currentCat,
                cat -> preset.slots().put(slot, PresetSlot.ofCategory(cat))));
    }

    /**
     * Opens the enchantment picker for a slot, if that slot holds something enchantable.
     *
     * <p>Silently does nothing otherwise: a click on an empty or non-enchantable slot in this
     * mode is almost certainly a misclick, and swapping the screen out from under the player
     * would be worse than ignoring it.
     */
    private void openEnchantPicker(int slot) {
        PresetSlot spec = preset.slots().get(slot);
        if (spec == null || spec.isBlank()) {
            return;
        }
        if (!EnchantCatalog.isEnchantable(spec.itemId())) {
            return;
        }
        ItemStack stack = presetStack(slot);
        String label = stack.isEmpty() ? "this slot" : stack.getHoverName().getString();
        Minecraft.getInstance().gui.setScreen(new EnchantPickerScreen(this, spec.itemId(), label,
                spec.enchants(), req -> preset.slots().put(slot, spec.withEnchants(req))));
    }

    /** GLFW code for F. */
    private static final int KEY_F = 70;

    /** True while F is held, so one press clears one slot rather than repeating every frame. */
    private boolean clearKeyHeld;

    /**
     * Clears whichever slot the mouse is over when F is pressed.
     *
     * <p>Polled rather than handled through {@code keyPressed}, matching SettingsScreen: the
     * key-event signature changed in 26.x and polling reads the same GLFW state without
     * depending on it. The held flag makes this edge-triggered — without it, holding F would
     * clear a slot every frame.
     */
    private void pollClearKey(int mouseX, int mouseY) {
        boolean down = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), KEY_F);
        if (down && !clearKeyHeld) {
            int slot = hoveredSlot(mouseX, mouseY);
            if (slot >= 0 && preset.slots().containsKey(slot)) {
                preset.slots().remove(slot);
            }
        }
        clearKeyHeld = down;
    }

    // ---------------------------------------------------------------- amount prompt

    private boolean promptOpen() {
        return amountSlot >= 0;
    }

    /**
     * Asks how many of {@code id} the slot should want, or records it outright when there is
     * nothing to ask.
     *
     * <p>A single-stacking item has exactly one legal answer, so putting a box on screen to type
     * "1" into would be a step that never changes the outcome.
     */
    private void openAmountPrompt(int slot, Identifier id, int initial) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        int max = item == null ? 1 : item.getDefaultMaxStackSize();
        if (max <= 1) {
            preset.slots().put(slot, PresetSlot.of(id, 1, null));
            return;
        }
        amountSlot = slot;
        amountItemId = id;
        amountMax = max;
        amountStart = Math.max(1, Math.min(max, initial));
    }

    private void closeAmountPrompt(boolean apply) {
        if (apply) {
            preset.slots().put(amountSlot, PresetSlot.of(amountItemId, typedAmount(), null));
        }
        amountSlot = -1;
        amountItemId = null;
        amountBox = null;
        promptWidgets.clear();
        rebuild();
    }

    /**
     * What is in the box, read as an amount.
     *
     * <p>Non-digits are dropped and the result is clamped rather than rejected, so there is no way
     * to get an error message out of this: an empty box means one, and anything above the item's
     * stack limit means that limit. Filtering keystrokes as they are typed would be the other
     * approach, but rewriting the box's contents from inside its own change callback re-enters it.
     */
    private int typedAmount() {
        if (amountBox == null) {
            return amountStart;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : amountBox.getValue().toCharArray()) {
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        if (digits.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Math.min(amountMax, Integer.parseInt(digits.toString())));
        } catch (NumberFormatException overflow) {
            // More digits than an int holds; the intent was clearly "lots".
            return amountMax;
        }
    }

    private int promptLeft() {
        return this.width / 2 - PROMPT_W / 2;
    }

    private int promptTop() {
        return this.height / 2 - PROMPT_H / 2;
    }

    private void buildAmountPrompt() {
        // Everything underneath goes inert. Vanilla draws an inactive widget greyed out, which is
        // half the dimming for free, and it means a click landing outside the panel does nothing
        // rather than quietly editing the slot behind it.
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget widget) {
                widget.active = false;
            }
        }

        promptWidgets.clear();
        int px = promptLeft();
        int py = promptTop();

        amountBox = new EditBox(this.font, px + 12, py + 44, PROMPT_W - 24, 20,
                Component.literal("Amount"));
        // Three digits covers vanilla's 64 and any modded limit short of a thousand.
        amountBox.setMaxLength(3);
        amountBox.setValue(Integer.toString(amountStart));
        amountBox.moveCursorToEnd(false);
        promptWidgets.add(addWidget(amountBox));

        int buttonW = (PROMPT_W - 32) / 2;
        promptWidgets.add(addWidget(Button.builder(Component.literal("Cancel"),
                        b -> closeAmountPrompt(false))
                .bounds(px + 12, py + 72, buttonW, 20).build()));
        promptWidgets.add(addWidget(Button.builder(Component.literal("Set"),
                        b -> closeAmountPrompt(true))
                .bounds(px + PROMPT_W - 12 - buttonW, py + 72, buttonW, 20).build()));

        setInitialFocus(amountBox);
    }

    /**

     */
    private void drawAmountPrompt(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);

        int px = promptLeft();
        int py = promptTop();
        graphics.fill(px, py, px + PROMPT_W, py + PROMPT_H, 0xF0100010);
        graphics.outline(px - 1, py - 1, PROMPT_W + 2, PROMPT_H + 2, markingColour());

        ItemStack icon = BuiltInRegistries.ITEM.getOptional(amountItemId)
                .map(item -> new ItemStack(item, 1))
                .orElse(ItemStack.EMPTY);
        Render.item(graphics, icon, px + 12, py + 12);
        if (!icon.isEmpty()) {
            graphics.text(this.font, icon.getHoverName().getString(), px + 36, py + 16, 0xFFFFFFFF, true);
        }
        graphics.text(this.font, "How many? 1 to " + amountMax, px + 12, py + 32, 0xFF9AA0A6, false);

        for (AbstractWidget widget : promptWidgets) {
            widget.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    /** The preset slot the mouse is currently over, or -1. */
    private int hoveredSlot(int mouseX, int mouseY) {
        for (int index : allSlotIndices()) {
            int[] p = slotPos(index);
            if (p == null) {
                continue;
            }
            if (mouseX >= p[0] && mouseX < p[0] + Render.SLOT_INNER
                    && mouseY >= p[1] && mouseY < p[1] + Render.SLOT_INNER) {
                return index;
            }
        }
        return -1;
    }

    private int[] allSlotIndices() {
        int[] all = new int[InvSlots.STORAGE_SIZE + 1 + ARMOR_DISPLAY_ORDER.length];
        int n = 0;
        for (int i = 0; i < InvSlots.STORAGE_SIZE; i++) {
            all[n++] = i;
        }
        all[n++] = InvSlots.OFFHAND;
        for (int armor : ARMOR_DISPLAY_ORDER) {
            all[n++] = armor;
        }
        return all;
    }

    // ---------------------------------------------------------------- grid geometry

    private int[] slotPos(int inventoryIndex) {
        if (InvSlots.isHotbar(inventoryIndex)) {
            return new int[]{gridLeft + inventoryIndex * SLOT, gridTop + 3 * SLOT + 8};
        }
        if (inventoryIndex >= InvSlots.MAIN_START && inventoryIndex <= InvSlots.MAIN_END) {
            int rel = inventoryIndex - InvSlots.MAIN_START;
            return new int[]{gridLeft + (rel % 9) * SLOT, gridTop + (rel / 9) * SLOT};
        }
        if (inventoryIndex == InvSlots.OFFHAND) {
            return new int[]{gridLeft - SLOT - 8, gridTop + 3 * SLOT + 8};
        }
        if (InvSlots.isArmor(inventoryIndex)) {
            for (int i = 0; i < ARMOR_DISPLAY_ORDER.length; i++) {
                if (ARMOR_DISPLAY_ORDER[i] == inventoryIndex) {
                    return new int[]{gridLeft + 9 * SLOT + 8 + i * SLOT, gridTop + 3 * SLOT + 8};
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, isNew ? "Create Preset" : "Edit Preset", this.width / 2 - 40, 14, 0xFFFFFFFF, true);
        graphics.text(this.font, "Preset Name:", this.width / 2 - 100, 24, 0xFF9AA0A6, false);

        drawSlotContents(graphics, InvSlots.OFFHAND);
        for (int armor : ARMOR_DISPLAY_ORDER) {
            drawSlotContents(graphics, armor);
        }
        for (int i = 0; i < InvSlots.STORAGE_SIZE; i++) {
            drawSlotContents(graphics, i);
        }

        // Sits below the capture row rather than across it.
        // Centred in the gap between the capture row and the Save/Cancel row, both of which
        // are anchored to the screen bottom — so measure from there, not from the grid.
        int captureBottom = gridTop + 3 * SLOT + 8 + SLOT + 16 + 20;
        int hintY = (captureBottom + (this.height - 28)) / 2 - 8;

        if (promptOpen()) {
            // F would otherwise clear whatever slot the cursor happens to sit over, and a tooltip
            // would surface from under the panel.
            drawAmountPrompt(graphics, mouseX, mouseY, delta);
        } else {
            pollClearKey(mouseX, mouseY);
            drawHoverTooltip(graphics, mouseX, mouseY);
        }
    }

    /**
     * Vanilla-style hover panel for the slot under the cursor: item name, what the slot demands,
     * and the right-click hint on anything enchantable.
     */
    private void drawHoverTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int hovered = hoveredSlot(mouseX, mouseY);
        if (hovered < 0) {
            return;
        }
        PresetSlot spec = preset.slots().get(hovered);
        java.util.List<Component> lines = new java.util.ArrayList<>();

        // An unmanaged slot is where the category feature is least discoverable — there is no
        // icon to right-click and nothing on screen suggesting the gesture exists — so the
        // tooltip is the one place it can be surfaced without permanent clutter.
        if (spec == null) {
            lines.add(Component.literal("Unmanaged slot").withStyle(ChatFormatting.GRAY));
            if (!InvSlots.isArmor(hovered)) {
                lines.add(Component.literal("Right-click for categories")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            Render.componentTooltip(graphics, this.font, lines, mouseX, mouseY);
            return;
        }

        if (spec.isBlank()) {
            lines.add(Component.literal("Kept empty").withStyle(ChatFormatting.GRAY));
            if (!InvSlots.isArmor(hovered)) {
                lines.add(Component.literal("Right-click to set a category")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.literal("F to remove").withStyle(ChatFormatting.DARK_GRAY));
            Render.componentTooltip(graphics, this.font, lines, mouseX, mouseY);
            return;
        }

        ItemStack stack = presetStack(hovered);
        if (stack.isEmpty()) {
            return;
        }

        if (spec.isCategory()) {
            lines.add(Component.literal("Any " + spec.category().label()));
            lines.add(Component.literal("Accepts any item of this kind")
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal("Right-click to change category")
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal("F to remove").withStyle(ChatFormatting.DARK_GRAY));
            Render.componentTooltip(graphics, this.font, lines, mouseX, mouseY);
            return;
        }
        lines.add(stack.getHoverName());

        // Laid out the way vanilla shows enchantments: one grey line each, directly under the
        // item name, so a preset slot reads like the item it is asking for.
        var req = spec.enchants();
        for (String line : req.describeLines()) {
            lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        if (EnchantCatalog.isEnchantable(spec.itemId())) {
            lines.add(Component.literal("Right-click to set enchantments")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (!InvSlots.isArmor(hovered)) {
            lines.add(Component.literal("Right-click to set a category")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.literal("F to remove").withStyle(ChatFormatting.DARK_GRAY));

        Render.componentTooltip(graphics, this.font, lines, mouseX, mouseY);
    }

    /** Drawn after super so icons sit on top of the slot buttons. */
    private void drawSlotContents(GuiGraphicsExtractor graphics, int index) {
        int[] p = slotPos(index);
        if (p == null) {
            return;
        }
        PresetSlot spec = preset.slots().get(index);
        if (spec == null) {
            return;
        }
        if (spec.isBlank()) {
            // A dot reads as "deliberately nothing", distinct from "not managed".
            graphics.fill(p[0] + 6, p[1] + 6, p[0] + 10, p[1] + 10, 0xFF8899AA);
            return;
        }
        // Centre the 16px icon inside the slot interior, leaving vanilla's 1px margin.
        int inset = (Render.SLOT_INNER - 16) / 2;
        Render.itemWithCount(graphics, this.font, presetStack(index), p[0] + inset, p[1] + inset);

        if (spec.isCategory()) {
            drawCategoryMarking(graphics, p[0], p[1], spec.category());
        }
    }

    /**
     * Marks a slot as asking for a <em>kind</em> of item rather than that specific item.
     *
     * <p>Without this an oak plank icon reads as "put oak planks here", which is exactly wrong —
     * it is a stand-in for "any block". The icon is left fully visible so it can be recognised at
     * 16 pixels; the distinguishing is carried by a coloured frame and the category's initial.
     */
    private void drawCategoryMarking(GuiGraphicsExtractor graphics, int x, int y, ItemCategory cat) {
        int inner = Render.SLOT_INNER;
        int colour = markingColour();

        // Coloured frame, distinct from the plain dark border on ordinary slots.
        graphics.outline(x - 1, y - 1, inner + 2, inner + 2, colour);

        // Short code, bottom-left, where a stack count never sits.
        drawTinyText(graphics, cat.shortCode(), x + 2, y + inner - 7, colour);
    }

    /**
     * A 3x5 pixel alphabet, drawn with {@code fill}.
     *
     * <p>Minecraft's font renders at one size only, and shrinking it needs a pose transform.
     * Painting the glyphs as pixels instead keeps the marker genuinely small — about half the
     * height of the normal font — and uses nothing beyond the fill call already relied on
     * elsewhere in this screen.
     *
     * <p>Each entry is five rows of three bits, top to bottom, high bit leftmost.
     */
    private static final java.util.Map<Character, int[]> TINY_GLYPHS = java.util.Map.of(
            'A', new int[]{0b111, 0b101, 0b111, 0b101, 0b101},
            'B', new int[]{0b110, 0b101, 0b110, 0b101, 0b110},
            'C', new int[]{0b011, 0b100, 0b100, 0b100, 0b011},
            'F', new int[]{0b111, 0b100, 0b110, 0b100, 0b100},
            'M', new int[]{0b101, 0b111, 0b111, 0b101, 0b101},
            'S', new int[]{0b011, 0b100, 0b010, 0b001, 0b110},
            'T', new int[]{0b111, 0b010, 0b010, 0b010, 0b010},
            'W', new int[]{0b101, 0b101, 0b111, 0b111, 0b101});

    /**
     * Draws {@code text} at 3x5 pixels per character, one pixel of spacing between.
     *
     * <p>Each glyph is painted twice: once in black at every one-pixel offset around it, then in
     * the real colour on top. At this size an unoutlined glyph disappears against a busy item
     * texture, and the usual drop shadow is not enough — the outline surrounds it completely.
     */
    private static void drawTinyText(GuiGraphicsExtractor graphics, String text,
                                     int x, int y, int colour) {
        drawTinyPass(graphics, text, x, y, 0xFF000000, true);
        drawTinyPass(graphics, text, x, y, colour, false);
    }

    /** One pass of {@link #drawTinyText}; {@code outline} spreads each pixel into its neighbours. */
    private static void drawTinyPass(GuiGraphicsExtractor graphics, String text,
                                     int x, int y, int colour, boolean outline) {
        int cursor = x;
        for (char ch : text.toCharArray()) {
            int[] rows = TINY_GLYPHS.get(Character.toUpperCase(ch));
            if (rows == null) {
                cursor += 4;
                continue;
            }
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < 3; col++) {
                    if ((rows[row] & (1 << (2 - col))) == 0) {
                        continue;
                    }
                    int px = cursor + col;
                    int py = y + row;
                    if (outline) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                graphics.fill(px + dx, py + dy, px + dx + 1, py + dy + 1, colour);
                            }
                        }
                    } else {
                        graphics.fill(px, py, px + 1, py + 1, colour);
                    }
                }
            }
            cursor += 4;
        }
    }

    /**
     * Marking colour: the player's chosen accent, except on the Grey palette where the accent is
     * close enough to the slot fill to disappear. White stands off it cleanly and still reads as
     * "no colour chosen".
     */
    private static int markingColour() {
        Theme.Palette p = Theme.current();
        return "Grey".equals(p.name()) ? 0xFFFFFFFF : p.accent();
    }

    private ItemStack presetStack(int index) {
        PresetSlot spec = preset.slots().get(index);
        if (spec == null || spec.isBlank()) {
            return ItemStack.EMPTY;
        }
        // Checked before the itemId guard below: a category slot has no item id by design.
        if (spec.isCategory()) {
            // No single item to show, so a stand-in is drawn and the tooltip explains the rule.
            Identifier iconId = Identifier.tryParse(spec.category().iconId());
            return BuiltInRegistries.ITEM.getOptional(iconId)
                    .map(item -> new ItemStack(item, 1))
                    .orElse(ItemStack.EMPTY);
        }
        if (spec.itemId() == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(spec.itemId())
                .map(item -> new ItemStack(item, Math.max(1, spec.count())))
                .orElse(ItemStack.EMPTY);
        // The preset stores a requirement, not real enchantments, so there is nothing for the
        // renderer to glint off. Forcing the override makes a slot that demands enchantments
        // look the part at a glance.
        if (!stack.isEmpty() && !spec.enchants().isNoop()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    /**
     * True if the preset now differs from how it opened.
     *
     * <p>Compares the serialised forms, which is precisely what Save would write: slots, name,
     * icon, match mode and auto-sort in one comparison. It also avoids false positives that a
     * map comparison would produce — {@link PresetSlot} has no {@code equals}, so re-copying an
     * identical item into a slot would otherwise register as a change.
     *
     * <p>Syncs the name box first, the same way {@link #rebuild()} does, since a name typed but
     * not yet committed is still an edit worth warning about.
     */
    private boolean hasUnsavedChanges() {
        if (nameBox != null) {
            preset.setName(nameBox.getValue());
        }
        return !preset.toJson().equals(original);
    }

    /** Puts the preset back to {@link #original}, undoing everything this screen changed. */
    private void revert() {
        Preset restored = Preset.fromJson(original);
        preset.setName(restored.name());
        preset.setMatchMode(restored.matchMode());
        preset.setAutoSort(restored.autoSort());
        // Read the icon from the snapshot rather than the restored preset: effectiveIcon() falls
        // back to the first filled slot, which would turn a derived icon into an explicit one.
        preset.setIcon(original.has("icon") ? Identifier.tryParse(original.get("icon").getAsString()) : null);
        preset.slots().clear();
        preset.slots().putAll(restored.slots());
    }

    private void closeToParent() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /**
     * Leaves the editor, asking first if anything would be lost.
     *
     * <p>Covers Escape as well as the Cancel button, since both land here. An untouched preset
     * closes silently — a prompt on a screen the player only glanced at is noise, not a guard.
     */
    @Override
    public void onClose() {
        if (promptOpen()) {
            // Escape backs out of the panel rather than the whole editor.
            closeAmountPrompt(false);
            return;
        }
        if (!hasUnsavedChanges()) {
            closeToParent();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ConfirmScreen(
                discard -> {
                    if (discard) {
                        revert();
                        closeToParent();
                    } else {
                        // Back to the editor with everything intact.
                        mc.gui.setScreen(this);
                    }
                },
                Component.literal(isNew
                        ? "Discard this preset?"
                        : "Discard changes to \"" + preset.name() + "\"?"),
                Component.literal("Your edits have not been saved. Your items are not affected."),
                Component.literal("Discard"),
                Component.literal("Keep editing")));
    }
}