package dev.inventorymanagerplus.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Every call this mod makes that draws an <em>item</em> or a <em>tooltip</em> lives in this one
 * file, on purpose.
 *
 * <p>26.1 and 26.2 rewrote GUI rendering substantially: {@code GuiGraphics} became
 * {@code GuiGraphicsExtractor}, {@code Screen#render} became {@code Screen#extractRenderState},
 * text drawing became {@code graphics.text(...)}. Those four I verified against Fabric's 26.2
 * documentation. The item and tooltip entry points I could not verify from any published source,
 * so rather than scatter guesses through four screen classes, they are funnelled through the three
 * methods below. If the mod fails to compile, it will fail here and nowhere else, and the fix is
 * three one-line edits.
 *
 * <p>Names to try, in rough order of likelihood, following the 26.x convention of dropping the
 * {@code render}/{@code draw} prefix (as {@code drawString} → {@code text}):
 *
 * <pre>
 *   item          : graphics.item(stack, x, y)
 *                   graphics.renderItem(stack, x, y)
 *   decorations   : graphics.itemDecorations(font, stack, x, y)
 *                   graphics.renderItemDecorations(font, stack, x, y)
 *   tooltip       : graphics.setTooltipForNextFrame(font, stack, x, y)
 *                   graphics.setComponentTooltipForNextFrame(...)
 *                   graphics.itemTooltip(font, stack, x, y)
 * </pre>
 *
 * <p>Check the real signatures in the decompiled {@code GuiGraphicsExtractor}. mcsrc.dev is the
 * quickest way to look: it decompiles the jar in the browser.
 */
public final class Render {

    private Render() {
    }

    /** Draws an item icon at 16x16. */
    public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        g.item(stack, x, y); // <-- UNVERIFIED for 26.2, see class javadoc
    }

    /** Draws the icon plus the stack-count / durability overlay. */
    public static void itemWithCount(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        g.item(stack, x, y);                        // <-- UNVERIFIED
        g.itemDecorations(font, stack, x, y);       // <-- UNVERIFIED
    }

    /**
     * Queues the normal vanilla item tooltip for this frame — the full one, with enchantments,
     * lore and durability, so hovering a preset slot reads exactly like hovering the real item.
     */
    public static void tooltip(GuiGraphicsExtractor g, Font font, ItemStack stack, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        g.setTooltipForNextFrame(font, stack, mouseX, mouseY); // <-- UNVERIFIED
    }

    /**
     * Queues a custom multi-line tooltip box for this frame — the same bordered panel vanilla
     * uses for item hovers, but with lines we supply.
     */
    public static void componentTooltip(GuiGraphicsExtractor g, Font font,
                                        java.util.List<net.minecraft.network.chat.Component> lines,
                                        int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY); // <-- UNVERIFIED
    }

    // ---- verified helpers -------------------------------------------------------------------
    // fill / outline / text are confirmed against the 26.2 Fabric docs.

    /** A 16x16 inventory-style slot with a hover highlight. */
    public static void slotBackground(GuiGraphicsExtractor g, int x, int y, boolean hovered, boolean managed) {
        int base = managed ? 0xFF3A4A63 : 0xFF2B2B2B;
        g.fill(x, y, x + 16, y + 16, hovered ? 0xFF5A7099 : base);
        g.outline(x - 1, y - 1, 18, 18, 0xFF101010);
    }
}