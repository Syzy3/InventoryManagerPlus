package dev.inventorymanagerplus.kinetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Purple-on-black buttons for the Kinetic screen, the same look in every Kinetic mod. */
final class KineticStyle extends Button {

    private static final int PURPLE = 0xFF8A1CFF;

    private KineticStyle(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    static Builder button(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused() && active;

        int fill = !active ? 0xFF1C1C1C : hovered ? 0xFF4A1585 : 0xFF241036;
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.outline(x, y, w, h, !active ? 0xFF333333 : hovered ? 0xFFFFFFFF : PURPLE);

        extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        Button build() {
            return new KineticStyle(x, y, width, height, message, onPress);
        }
    }
}
