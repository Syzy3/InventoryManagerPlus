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
}