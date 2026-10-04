package dev.inventorymanagerplus.gui;

import dev.inventorymanagerplus.preset.ItemCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * What right-click on a preset slot opens, whatever the slot holds, so the gesture always does
 * the same thing: enchantment requirements (when the slot has an enchantable item), the
 * categories the slot may use, and a way to clear the slot.
 */
public final class SlotOptionsScreen extends Screen {

    private static final int BTN_W = 220;
    private static final int BTN_H = 20;
    private static final int GAP = 2;

    private final Screen parent;
    private final String slotName;
    private final String currently;
    private final ItemCategory current;
    /** The categories this slot may use; armour slots only get Armor. */
    private final List<ItemCategory> offered;
    /** Opens the enchantment picker; null when the slot has nothing enchantable. */
    private final Runnable onEnchant;
    private final Consumer<ItemCategory> onPick;

    public SlotOptionsScreen(Screen parent, String slotName, String currently, ItemCategory current,
                             List<ItemCategory> offered, Runnable onEnchant, Runnable onClear,
                             Consumer<ItemCategory> onPick) {
        super(Component.literal("Slot options"));
        this.parent = parent;
        this.slotName = slotName;
        this.currently = currently;
        this.current = current;
        this.offered = offered;
        this.onEnchant = onEnchant;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        // Categories sit two to a row so the whole menu fits on screen at large GUI scales.
        // Cancel takes the next free half-row after the categories, so every button is the same
        // size: with an odd number of categories it sits beside the last one.
        int rows = (offered.size() + 1 + 1) / 2;
        int totalH = (onEnchant != null ? BTN_H + GAP + 4 : 0) + rows * (BTN_H + GAP) - GAP;
        int y = Math.max(40, (this.height - totalH) / 2);
        int x = this.width / 2 - BTN_W / 2;
        int half = (BTN_W - GAP) / 2;

        if (onEnchant != null) {
            // The enchantment picker takes over the screen and returns to the editor itself.
            addRenderableWidget(ThemedButton.create(Component.literal("Enchantments..."), b -> onEnchant.run())
                    .bounds(x, y, BTN_W, BTN_H).build());
            y += BTN_H + GAP + 4;
        }

        for (int i = 0; i < offered.size(); i++) {
            ItemCategory cat = offered.get(i);
            boolean set = cat == current;
            boolean right = i % 2 == 1;
            int bx = right ? x + half + GAP : x;
            addRenderableWidget(ThemedButton.create(
                    Component.literal((set ? "✔ " : "") + "Any " + cat.label()), b -> {
                        onPick.accept(cat);
                        onClose();
                    }).bounds(bx, y, half, BTN_H).build());
            if (right) {
                y += BTN_H + GAP;
            }
        }

        // Cancel fills the empty right half of the last row, or starts a new row on the right.
        addRenderableWidget(ThemedButton.create(Component.literal("Cancel"), b -> onClose())
                .bounds(x + half + GAP, y, half, BTN_H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int x = this.width / 2 - BTN_W / 2;
        graphics.text(this.font, "Slot options: " + slotName, x, 14, 0xFFFFFFFF, true);
        graphics.text(this.font, "Now: " + currently, x, 26, Theme.current().accent(), false);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
