package dev.inventorymanagerplus;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Everything keyboard-related that differs between Minecraft versions lives here.
 *
 * <p>Minecraft 26.3 replaced GLFW with SDL, so key codes are now SDL scancodes
 * ({@link InputConstants.Type#KEYBOARD}) instead of GLFW key codes ({@code KEYSYM}).
 * Inside the game this build only ever uses scancodes. Preset files keep storing the GLFW code,
 * converted here on load and save, so the same config folder works with both the 26.2 and the
 * 26.3 builds of the mod.
 */
public final class Keys {

    /** Scancode range worth scanning while listening for a new key (letters up to right GUI). */
    public static final int FIRST_SCAN = 4;
    public static final int LAST_SCAN = 231;

    /** Pairs of {GLFW code, SDL scancode}, built from both versions' InputConstants.KEY_* values. */
    private static final int[][] GLFW_TO_SDL = {
            {48, 39}, {49, 30}, {50, 31}, {51, 32}, {52, 33}, {53, 34}, {54, 35}, {55, 36},
            {56, 37}, {57, 38},
            {65, 4}, {66, 5}, {67, 6}, {68, 7}, {69, 8}, {70, 9}, {71, 10}, {72, 11}, {73, 12},
            {74, 13}, {75, 14}, {76, 15}, {77, 16}, {78, 17}, {79, 18}, {80, 19}, {81, 20},
            {82, 21}, {83, 22}, {84, 23}, {85, 24}, {86, 25}, {87, 26}, {88, 27}, {89, 28},
            {90, 29},
            {290, 58}, {291, 59}, {292, 60}, {293, 61}, {294, 62}, {295, 63}, {296, 64},
            {297, 65}, {298, 66}, {299, 67}, {300, 68}, {301, 69}, {302, 104}, {303, 105},
            {304, 106}, {305, 107}, {306, 108}, {307, 109}, {308, 110}, {309, 111}, {310, 112},
            {311, 113}, {312, 114}, {313, 115},
            {282, 83}, {320, 98}, {321, 89}, {322, 90}, {323, 91}, {324, 92}, {325, 93},
            {326, 94}, {327, 95}, {328, 96}, {329, 97}, {330, 220}, {331, 84}, {332, 85},
            {333, 86}, {334, 87}, {335, 88}, {336, 103},
            {264, 81}, {263, 80}, {262, 79}, {265, 82},
            {39, 52}, {92, 49}, {44, 54}, {61, 46}, {96, 53}, {91, 47}, {45, 45}, {46, 55},
            {93, 48}, {59, 51}, {47, 56}, {32, 44}, {258, 43},
            {342, 226}, {341, 224}, {340, 225}, {343, 227}, {346, 230}, {345, 228}, {344, 229},
            {347, 231}, {348, 101},
            {257, 40}, {256, 41}, {259, 42}, {261, 76}, {269, 77}, {268, 74}, {260, 73},
            {267, 78}, {266, 75}, {280, 57}, {284, 72}, {281, 71}, {283, 70},
    };

    private Keys() {
    }

    /** True while the key with this scancode is held. */
    public static boolean isDown(int code) {
        return InputConstants.isKeyDown(code);
    }

    /** The key with this scancode. */
    public static InputConstants.Key key(int code) {
        return InputConstants.Type.KEYBOARD.getOrCreate(code);
    }

    /** Converts a saved GLFW code to this version's scancode; -1 if it has none. */
    public static int fromSaved(int glfw) {
        for (int[] pair : GLFW_TO_SDL) {
            if (pair[0] == glfw) {
                return pair[1];
            }
        }
        return -1;
    }

    /** Converts a scancode to the GLFW code that is saved; -1 if it has none. */
    public static int toSaved(int scancode) {
        for (int[] pair : GLFW_TO_SDL) {
            if (pair[1] == scancode) {
                return pair[0];
            }
        }
        return -1;
    }
}
