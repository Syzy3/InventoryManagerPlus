package dev.inventorymanagerplus.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Every call this mod makes that draws an <em>item</em> or a <em>tooltip</em>, plus the small
 * slot markings (tiny letters, the keep-empty pane) shared by several screens.
 *
 * <p>26.1 and 26.2 rewrote GUI rendering ({@code GuiGraphics} became
 * {@code GuiGraphicsExtractor}, {@code Screen#render} became {@code Screen#extractRenderState}),
 * so the item and tooltip calls are kept in this one file. If a future version renames them,
 * this is the only place to fix.
 */
public final class Render {

    private Render() {
    }

    /** Draws an item icon at 16x16. */
    public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        g.item(stack, x, y);
    }

    /** Draws the icon plus the stack-count / durability overlay. */
    public static void itemWithCount(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        g.item(stack, x, y);
        g.itemDecorations(font, stack, x, y);
    }

    /**
     * Queues the normal vanilla item tooltip for this frame — the full one, with enchantments,
     * lore and durability, so hovering a preset slot reads exactly like hovering the real item.
     */
    public static void tooltip(GuiGraphicsExtractor g, Font font, ItemStack stack, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
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
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    // ---- slot drawing ----------------------------------------------------------------------

    /** Interior size of a slot. A 16px icon sits inside with a 1px margin, as vanilla does. */
    public static final int SLOT_INNER = 18;

    // ---- slot markings --------------------------------------------------------------------

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
            'E', new int[]{0b111, 0b100, 0b110, 0b100, 0b111},
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
    public static void tinyText(GuiGraphicsExtractor graphics, String text,
                                     int x, int y, int colour) {
        drawTinyPass(graphics, text, x, y, 0xFF000000, true);
        drawTinyPass(graphics, text, x, y, colour, false);
    }

    /** One pass of {@link #tinyText}; {@code outline} spreads each pixel into its neighbours. */
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
    public static int markingColour() {
        return Theme.highlight();
    }

    /**
     * "Keep this slot empty": a grey glass pane with the same coloured frame as category slots,
     * plus an E in the corner, so it can't be mistaken for a preset that wants actual glass panes.
     *
     * @param x left edge of the slot interior ({@link #SLOT_INNER} wide)
     * @param y top edge of the slot interior
     */
    public static void keepEmptyMarker(GuiGraphicsExtractor g, int x, int y) {
        int inset = (SLOT_INNER - 16) / 2;
        // Built per call rather than cached: item stacks can't be made before a world has loaded,
        // and this class is first touched much earlier than that.
        item(g, new ItemStack(net.minecraft.world.item.Items.STAINED_GLASS_PANE.gray()), x + inset, y + inset);
        int colour = markingColour();
        g.outline(x - 1, y - 1, SLOT_INNER + 2, SLOT_INNER + 2, colour);
        tinyText(g, "E", x + 2, y + SLOT_INNER - 7, colour);
    }
}
