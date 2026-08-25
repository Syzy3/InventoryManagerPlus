package dev.inventorymanagerplus.gui;

import dev.inventorymanagerplus.InventoryManagerPlus;
import dev.inventorymanagerplus.inventory.InvSlots;
import dev.inventorymanagerplus.inventory.ItemMatcher;
import dev.inventorymanagerplus.preset.EnchantCatalog;
import dev.inventorymanagerplus.preset.EnchantRequirement;
import dev.inventorymanagerplus.preset.Preset;
import dev.inventorymanagerplus.preset.PresetSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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

    private ClickMode mode = ClickMode.COPY;
    private EditBox nameBox;
    private int gridLeft;
    private int gridTop;

    public PresetEditorScreen(Screen parent, Preset preset, boolean isNew) {
        super(Component.literal(isNew ? "Create Preset" : "Edit Preset"));
        this.parent = parent;
        this.preset = preset;
        this.isNew = isNew;
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

        addRenderableWidget(Button.builder(Component.literal("Save Preset"), b -> save())
                .bounds(this.width / 2 - 104, this.height - 28, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 100, 20).build());
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
        onClose();
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
            Minecraft.getInstance().gui.setScreen(new ItemPickerScreen(this,
                    id -> preset.slots().put(slot, PresetSlot.of(id, 1, null))));
            return;
        }

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack live = player.getInventory().getItem(slot);
        if (live.isEmpty()) {
            // Copying an empty slot clears the entry, so a click is also how you undo one.
            preset.slots().remove(slot);
        } else {
            record(slot, live);
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
        if (event.button() == 1) {
            int slot = hoveredSlot((int) event.x(), (int) event.y());
            if (slot >= 0) {
                openEnchantPicker(slot);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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
        int hintY = (captureBottom + (this.height - 28)) / 2 - 4;

        drawHoverTooltip(graphics, mouseX, mouseY);
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
        if (spec == null || spec.isBlank()) {
            return;
        }
        ItemStack stack = presetStack(hovered);
        if (stack.isEmpty()) {
            return;
        }

        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(stack.getHoverName());

        // Laid out the way vanilla shows enchantments: one grey line each, directly under the
        // item name, so a preset slot reads like the item it is asking for.
        var req = spec.enchants();
        for (String line : req.describeLines()) {
            lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        if (EnchantCatalog.isEnchantable(spec.itemId())) {
            lines.add(Component.literal(req.isNoop()
                    ? "Right-click to set enchantments"
                    : "Right-click to change enchantments").withStyle(ChatFormatting.DARK_GRAY));
        }

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
    }

    private ItemStack presetStack(int index) {
        PresetSlot spec = preset.slots().get(index);
        if (spec == null || spec.isBlank() || spec.itemId() == null) {
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

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}