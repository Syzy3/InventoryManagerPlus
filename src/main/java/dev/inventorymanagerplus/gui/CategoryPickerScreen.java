package dev.inventorymanagerplus.gui;
import dev.inventorymanagerplus.preset.ItemCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Chooses which kind of item a slot accepts.
 *
 * <p>Reached by right-clicking a slot that has no specific item to enchant — an empty slot, a
 * slot already set to a category, or one holding something with no enchantments of its own. Slots
 * holding enchantable gear send right-click to the enchantment picker instead, so the gesture
 * always goes straight to the thing that makes sense for what is there.
 */
public final class CategoryPickerScreen extends Screen {

    private static final int BTN_W = 200;
    private static final int BTN_H = 20;
    private static final int GAP = 4;

    private final Screen parent;
    private final ItemCategory current;
    private final Consumer<ItemCategory> onPick;

    public CategoryPickerScreen(Screen parent, ItemCategory current,
                                Consumer<ItemCategory> onPick) {
        super(Component.literal("Choose a category"));
        this.parent = parent;
        this.current = current;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        int rows = ItemCategory.values().length + 1;
        int totalH = rows * (BTN_H + GAP);
        int y = Math.max(34, this.height / 2 - totalH / 2);
        int x = this.width / 2 - BTN_W / 2;

        for (ItemCategory cat : ItemCategory.values()) {
            boolean set = cat == current;
            addRenderableWidget(Button.builder(
                    Component.literal("Any " + cat.label() + (set ? "   [set]" : "")), b -> {
                        onPick.accept(cat);
                        onClose();
                    }).bounds(x, y, BTN_W, BTN_H).build());
            y += BTN_H + GAP;
        }

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x, y, BTN_W, BTN_H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "This slot accepts...",
                this.width / 2 - BTN_W / 2, 16, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}