package dev.inventorymanagerplus.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * The two different "slot number" spaces this mod has to move between, and nothing else.
 *
 * <p><strong>Inventory indices</strong> ({@code Inventory#getItem}) are stable and are what
 * presets are stored in:
 * <pre>
 *   0  ..  8   hotbar (left to right)
 *   9  .. 35   main inventory (three rows, top to bottom)
 *   36 .. 39   armour
 *   40         off-hand
 * </pre>
 *
 * <p><strong>Menu slot indices</strong> ({@code AbstractContainerMenu#slots}) are what click
 * packets use, and they are <em>not</em> the same numbers — in the player's own inventory screen
 * the hotbar lives at 36-44 while the main inventory lives at 9-35, and when a chest is open
 * everything shifts by the size of the chest. Rather than hard-coding either layout, this class
 * asks the menu itself which of its slots is backed by the player's inventory. That keeps working
 * if a future version reshuffles the ordering.
 */
public final class InvSlots {

    public static final int HOTBAR_START = 0;
    public static final int HOTBAR_END = 8;
    public static final int MAIN_START = 9;
    public static final int MAIN_END = 35;
    /** Armour, in inventory order: 36 boots, 37 leggings, 38 chestplate, 39 helmet. */
    public static final int ARMOR_START = 36;
    public static final int ARMOR_END = 39;
    public static final int OFFHAND = 40;

    /**
     * Hotbar + main inventory. Used as the search space for parking displaced items — armour and
     * off-hand are excluded there because dumping a random stack into a helmet slot is not a
     * legal move, even though a preset may deliberately target those slots.
     */
    public static final int STORAGE_SIZE = 36;

    private InvSlots() {
    }

    public static boolean isHotbar(int inventoryIndex) {
        return inventoryIndex >= HOTBAR_START && inventoryIndex <= HOTBAR_END;
    }

    public static boolean isArmor(int inventoryIndex) {
        return inventoryIndex >= ARMOR_START && inventoryIndex <= ARMOR_END;
    }

    /** Every slot a preset may target: hotbar, main inventory, armour and off-hand. */
    public static boolean isManageable(int inventoryIndex) {
        return inventoryIndex >= 0 && inventoryIndex <= OFFHAND;
    }

    /**
     * Finds the menu slot that maps onto the given inventory index, or -1 if this menu does not
     * expose it (e.g. the off-hand is not present in every screen).
     *
     * <p>Linear scan over at most ~90 slots, only run while building a click plan, so the cost is
     * irrelevant next to the network round trip.
     */
    public static int toMenuSlot(AbstractContainerMenu menu, Player player, int inventoryIndex) {
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == inventoryIndex) {
                return slot.index;
            }
        }
        return -1;
    }

    /** Human-readable label used in tooltips and error messages. */
    public static String describe(int inventoryIndex) {
        if (inventoryIndex == OFFHAND) {
            return "Off-hand";
        }
        if (isArmor(inventoryIndex)) {
            return switch (inventoryIndex) {
                case 39 -> "Helmet";
                case 38 -> "Chestplate";
                case 37 -> "Leggings";
                default -> "Boots";
            };
        }
        if (isHotbar(inventoryIndex)) {
            return "Hotbar " + (inventoryIndex + 1);
        }
        int row = (inventoryIndex - MAIN_START) / 9 + 1;
        int col = (inventoryIndex - MAIN_START) % 9 + 1;
        return "Row " + row + ", column " + col;
    }
}