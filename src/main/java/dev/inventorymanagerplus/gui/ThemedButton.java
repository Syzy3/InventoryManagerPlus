package dev.inventorymanagerplus.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * A button drawn in the current colour theme instead of vanilla's grey texture.
 *
 * <p>Vanilla buttons are a fixed grey sprite, which is why tinting them only ever half worked.
 * This draws its own background and border from {@link Theme}, and keeps vanilla's label
 * drawing, sounds, keyboard focus and narration. Every button in the mod's screens is one of
 * these, made through {@link #create}, which reads exactly like {@code Button.builder}.
 *
 * <p>The slot style is for preset slot squares: darker, with no bevel, and brighter when the
 * preset uses that slot.
 */
public final class ThemedButton extends Button {

    /** Non-null for slot squares: whether the slot is used by the preset right now. */
    private final BooleanSupplier slotManaged;
    /** Rows outside [clipTop, clipBottom) don't count as "over" the button. */
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;

    private ThemedButton(int x, int y, int w, int h, Component message, OnPress onPress,
                         BooleanSupplier slotManaged) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.slotManaged = slotManaged;
    }

    public static Builder create(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    /**
     * Limits clicks to a vertical band. For buttons inside a scrolling list: the part of the
     * button scrolled out of the list is not drawn, so it must not be clickable either.
     */
    public void clipVertically(int top, int bottom) {
        this.clipTop = top;
        this.clipBottom = bottom;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseY >= clipTop && mouseY < clipBottom && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused();

        if (slotManaged != null) {
            graphics.fill(x, y, x + w, y + h, Theme.slotFill(slotManaged.getAsBoolean(), hovered && active));
            graphics.outline(x - 1, y - 1, w + 2, h + 2, hovered && active ? Theme.highlight() : 0xFF0C0C0C);
        } else {
            int fill = Theme.buttonFill(active, hovered);
            graphics.fill(x, y, x + w, y + h, fill);
            // A lighter top edge gives the same raised look as vanilla's buttons.
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, Theme.mix(fill, 0xFFFFFFFF, 0.18f));
            graphics.outline(x, y, w, h, Theme.buttonBorder(active, hovered));
        }

        extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    /** Same shape as {@code Button.Builder}, so call sites read the same. */
    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private Tooltip tooltip;
        private BooleanSupplier slotManaged;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        /** Draw as a preset slot square; {@code managed} says whether the preset uses it. */
        public Builder slot(BooleanSupplier managed) {
            this.slotManaged = managed;
            return this;
        }

        public Button build() {
            ThemedButton button = new ThemedButton(x, y, width, height, message, onPress, slotManaged);
            button.setTooltip(tooltip);
            return button;
        }
    }
}
