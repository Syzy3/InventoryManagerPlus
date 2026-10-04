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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

        // This preset's own hotkey, next to its name.
        Button keyButton = ThemedButton.create(Component.literal(hotkeyLabel()), b -> {
            listeningForHotkey = true;
            rebuild();
        }).bounds(this.width / 2 + 104, 34, 96, 20).build();
        // A clash with another preset or a Controls key is mentioned here, on hover, rather than
        // as a message on screen.
        String clash = preset.hasHotkey() ? conflictsFor(preset.hotkey()) : null;
        keyButton.setTooltip(Tooltip.create(Component.literal(
                "A key that applies this preset straight away, without opening the menu. "
                        + "Click, then press a key. Backspace clears it, Escape cancels."
                        + (clash != null ? "\n\n" + clash : ""))));
        addRenderableWidget(keyButton);

        addRenderableWidget(ThemedButton.create(Component.literal("Click does: " + mode.label), b -> {
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

        addRenderableWidget(ThemedButton.create(Component.literal("Capture Hotbar"), b -> {
            captureHotbar();
            rebuild();
        }).bounds(this.width / 2 - 154, controlsTop, 100, 20).build());

        addRenderableWidget(ThemedButton.create(Component.literal("Capture Inventory"), b -> {
            captureInventory();
            rebuild();
        }).bounds(this.width / 2 - 50, controlsTop, 100, 20).build());

        addRenderableWidget(ThemedButton.create(Component.literal("Clear All"), b -> confirmClearAll())
                .bounds(this.width / 2 + 54, controlsTop, 100, 20).build());

        addRenderableWidget(ThemedButton.create(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 - 104, this.height - 28, 100, 20).build());

        addRenderableWidget(ThemedButton.create(Component.literal("Save Preset"), b -> save())
                .bounds(this.width / 2 + 4, this.height - 28, 100, 20).build());
    }

    private void addSlotButton(int index) {
        int[] p = slotPos(index);
        if (p == null) {
            return;
        }
        addRenderableWidget(ThemedButton.create(Component.empty(), b -> {
            handleSlotClick(index);
            rebuild();
        }).bounds(p[0], p[1], Render.SLOT_INNER, Render.SLOT_INNER)
                .slot(() -> preset.slots().containsKey(index)).build());
    }

    private void rebuild() {
        if (nameBox != null) {
            preset.setName(nameBox.getValue());
        }
        clearWidgets();
        init();
    }

    /** Clear All wipes every slot, so it asks first. Nothing is asked when there's nothing to clear. */
    private void confirmClearAll() {
        if (preset.slots().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ThemedConfirmScreen(
                clear -> {
                    if (clear) {
                        preset.slots().clear();
                    }
                    mc.gui.setScreen(this);
                },
                Component.literal("Clear every slot in this preset?"),
                Component.literal("You can still Cancel the editor afterwards to undo it. Your items are not affected."),
                Component.literal("Clear All"),
                Component.literal("Keep slots")));
    }

    // ---------------------------------------------------------------- hotkey

    /** True while waiting for the player to press this preset's new hotkey. */
    private boolean listeningForHotkey;

    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;

    private String hotkeyLabel() {
        if (listeningForHotkey) {
            return "Key: ...";
        }
        return "Key: " + (preset.hasHotkey()
                ? InputConstants.Type.KEYSYM.getOrCreate(preset.hotkey()).getDisplayName().getString()
                : "None");
    }

    /**
     * Watches the keyboard for the new hotkey. Polled like the F key and the Settings key
     * buttons, since the key-event signature changed in 26.x. Escape cancels, Backspace clears.
     */
    private void pollHotkey() {
        var window = Minecraft.getInstance().getWindow();
        if (InputConstants.isKeyDown(window, KEY_ESCAPE)) {
            listeningForHotkey = false;
            rebuild();
            return;
        }
        if (InputConstants.isKeyDown(window, KEY_BACKSPACE)) {
            preset.setHotkey(-1);
            listeningForHotkey = false;
            rebuild();
            return;
        }
        for (int code = 32; code <= 348; code++) {
            if (InputConstants.isKeyDown(window, code)) {
                preset.setHotkey(code);
                listeningForHotkey = false;
                // The key is still down; don't let it also count as F-to-clear on this frame.
                clearKeyHeld = true;
                rebuild();
                return;
            }
        }
    }

    /** What else the key already does, so the player can pick another one if that's a problem. */
    private String conflictsFor(int code) {
        var other = InventoryManagerPlus.presets().withHotkey(code, preset);
        if (other.isPresent()) {
            return "That key also applies \"" + other.get().name() + "\".";
        }
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(code);
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.matches(key)) {
                return "That key is also " + Component.translatable(mapping.getName()).getString()
                        + " in Controls.";
            }
        }
        return null;
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
     * <p>Empty rows slots become "keep empty", the same as Capture Hotbar, so the capture is an
     * exact copy of the layout. An empty off-hand is left unmanaged: armour and the off-hand
     * never use "keep empty".
     */
    private void captureInventory() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (int i = InvSlots.MAIN_START; i <= InvSlots.MAIN_END; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                preset.slots().put(i, PresetSlot.blank());
            } else {
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
            preset.slots().put(slot, PresetSlot.of(id));
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
            Minecraft.getInstance().gui.setScreen(new ItemPickerScreen(this,
                    id -> preset.slots().put(slot, PresetSlot.of(id)),
                    // Armour and the off-hand don't offer "keep empty".
                    slot < InvSlots.STORAGE_SIZE ? () -> preset.slots().put(slot, PresetSlot.blank()) : null,
                    filter));
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
            // Only the item is recorded, never the amount: a preset slot is "steak goes here".
            preset.slots().put(slot, PresetSlot.of(id));
        }
    }

    /**
     * Right-click opens Slot options for whichever slot is under the cursor.
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
                openSlotOptions(slot);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Right-click target. Always the same menu, whatever the slot holds, so right-click is
     * predictable: enchantments (when there is an enchantable item), categories, and clear.
     */
    private void openSlotOptions(int slot) {
        PresetSlot spec = preset.slots().get(slot);

        boolean enchantable = spec != null && !spec.isCategory() && !spec.isBlank()
                && EnchantCatalog.isEnchantable(spec.itemId());

        // Armour slots only offer "Any Armor", and the matcher narrows it to that slot: the
        // helmet slot takes any helmet, never boots.
        ItemCategory currentCat = spec != null && spec.isCategory() ? spec.category() : null;
        java.util.List<ItemCategory> offered = InvSlots.isArmor(slot)
                ? java.util.List.of(ItemCategory.ARMOR)
                : java.util.List.of(ItemCategory.values());

        Minecraft.getInstance().gui.setScreen(new SlotOptionsScreen(this,
                InvSlots.describe(slot), describeSlot(slot, spec), currentCat, offered,
                enchantable ? () -> openEnchantPicker(slot) : null,
                spec != null ? () -> preset.slots().remove(slot) : null,
                cat -> preset.slots().put(slot, PresetSlot.ofCategory(cat))));
    }

    /** One-line summary of what a slot is set to, for the Slot options header. */
    private String describeSlot(int slot, PresetSlot spec) {
        if (spec == null) {
            return "not managed";
        }
        if (spec.isBlank()) {
            return "keep empty";
        }
        if (spec.isCategory()) {
            return "Any " + spec.category().label();
        }
        ItemStack stack = presetStack(slot);
        return stack.isEmpty() ? String.valueOf(spec.itemId()) : stack.getHoverName().getString();
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
        if (down && !clearKeyHeld && !(nameBox != null && nameBox.isFocused())) {
            int slot = hoveredSlot(mouseX, mouseY);
            if (slot >= 0 && preset.slots().containsKey(slot)) {
                preset.slots().remove(slot);
            }
        }
        clearKeyHeld = down;
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
        graphics.text(this.font, "Preset Name:", this.width / 2 - 100, 24, Theme.current().accent(), false);

        drawSlotContents(graphics, InvSlots.OFFHAND);
        for (int armor : ARMOR_DISPLAY_ORDER) {
            drawSlotContents(graphics, armor);
        }
        for (int i = 0; i < InvSlots.STORAGE_SIZE; i++) {
            drawSlotContents(graphics, i);
        }

        if (listeningForHotkey) {
            // The button itself reads "Key: ..." while waiting; its tooltip explains the keys.
            pollHotkey();
            return;
        }

        pollClearKey(mouseX, mouseY);
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
        java.util.List<Component> lines = new java.util.ArrayList<>();

        // An unmanaged slot is where the category feature is least discoverable — there is no
        // icon to right-click and nothing on screen suggesting the gesture exists — so the
        // tooltip is the one place it can be surfaced without permanent clutter.
        if (spec == null) {
            lines.add(Component.literal("Unmanaged slot").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Right-click for options").withStyle(ChatFormatting.DARK_GRAY));
            Render.componentTooltip(graphics, this.font, lines, mouseX, mouseY);
            return;
        }

        if (spec.isBlank()) {
            lines.add(Component.literal("Kept empty").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Right-click for options").withStyle(ChatFormatting.DARK_GRAY));
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
            lines.add(Component.literal(InvSlots.isArmor(hovered)
                            ? "Accepts any " + armorPieceName(hovered) + " you can wear"
                            : "Accepts any item of this kind")
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal("Right-click for options").withStyle(ChatFormatting.DARK_GRAY));
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
        lines.add(Component.literal(EnchantCatalog.isEnchantable(spec.itemId())
                        ? "Right-click for options and enchantments" : "Right-click for options")
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.literal("F to remove").withStyle(ChatFormatting.DARK_GRAY));

        Render.componentTooltip(graphics, this.font, lines, mouseX, mouseY);
    }

    private static String armorPieceName(int slot) {
        return switch (slot) {
            case 39 -> "helmet";
            case 38 -> "chestplate";
            case 37 -> "leggings";
            default -> "boots";
        };
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
            // Same marked glass pane as the "Keep empty" choice in the item browser.
            Render.keepEmptyMarker(graphics, p[0], p[1]);
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
        int colour = Render.markingColour();

        // Coloured frame, distinct from the plain dark border on ordinary slots.
        graphics.outline(x - 1, y - 1, inner + 2, inner + 2, colour);

        // Short code, bottom-left, where a stack count never sits.
        Render.tinyText(graphics, cat.shortCode(), x + 2, y + inner - 7, colour);
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
                .map(item -> new ItemStack(item, 1))
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
        preset.setAutoSort(restored.autoSort());
        preset.setHotkey(restored.hotkey());
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
        if (listeningForHotkey) {
            // Escape while choosing a key cancels the choice, not the whole editor.
            listeningForHotkey = false;
            rebuild();
            return;
        }
        if (!hasUnsavedChanges()) {
            closeToParent();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ThemedConfirmScreen(
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