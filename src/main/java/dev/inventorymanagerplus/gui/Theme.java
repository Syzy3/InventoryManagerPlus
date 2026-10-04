package dev.inventorymanagerplus.gui;

import dev.inventorymanagerplus.config.Config;

/**
 * Colour palettes for the mod's screens.
 *
 * <p>Kept as a fixed list rather than a free colour picker: a picker lets you choose colours that
 * are unreadable against Minecraft's blurred background, and every palette here has been paired
 * with a card colour that stays legible.
 */
public final class Theme {

    /**
     * @param name        shown on the settings button
     * @param accent      borders, highlights, active markers
     * @param card        normal preset card background
     * @param cardActive  background of the currently active preset
     */
    public record Palette(String name, int accent, int card, int cardActive) {
    }

    private static final Palette[] ALL = {
            // Grey is first, so index 0 (the config default) is the neutral vanilla-looking theme.
            new Palette("Grey", 0xFFB6B6B6, 0xFF1E1E1E, 0xFF3A3A3A),
            new Palette("Blue", 0xFF6E9BD8, 0xFF1E1E1E, 0xFF2E3F55),
            new Palette("Green", 0xFF6ED88F, 0xFF1B211D, 0xFF27412F),
            new Palette("Purple", 0xFFA96ED8, 0xFF1F1B23, 0xFF39294A),
            new Palette("Red", 0xFFD86E6E, 0xFF231B1B, 0xFF4A2929),
            new Palette("Amber", 0xFFD8A96E, 0xFF231F1B, 0xFF4A3B29),
    };

    private Theme() {
    }

    public static Palette current() {
        return ALL[Math.floorMod(Config.get().accentIndex, ALL.length)];
    }

    public static void cycle() {
        Config.get().accentIndex = Math.floorMod(Config.get().accentIndex + 1, ALL.length);
    }

    // ---- colours derived from the accent ----------------------------------------------------
    // Everything the mod draws takes its colour from the current palette's accent, so picking a
    // colour in Settings recolours buttons, slots and borders together.

    private static final int DARK = 0xFF101010;

    /** Blends two colours: t = 0 gives {@code a}, t = 1 gives {@code b}. Result is opaque. */
    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static int buttonFill(boolean active, boolean hovered) {
        if (!active) {
            return 0xFF262626;
        }
        return mix(current().accent(), DARK, hovered ? 0.50f : 0.72f);
    }

    public static int buttonBorder(boolean active, boolean hovered) {
        if (!active) {
            return 0xFF3A3A3A;
        }
        return hovered ? highlight() : mix(current().accent(), 0xFF000000, 0.35f);
    }

    /** Fill for a preset slot square. Slots the preset uses are a little brighter. */
    public static int slotFill(boolean managed, boolean hovered) {
        float t = hovered ? 0.45f : managed ? 0.64f : 0.84f;
        return mix(current().accent(), DARK, t);
    }

    /** Border for cards and panels that aren't highlighted. */
    public static int border() {
        return mix(current().accent(), 0xFF000000, 0.55f);
    }

    /**
     * The bright edge used for hover and markings: the accent, except on Grey where the accent is
     * too close to the fills to stand out, so white is used instead.
     */
    public static int highlight() {
        return "Grey".equals(current().name()) ? 0xFFFFFFFF : current().accent();
    }
}