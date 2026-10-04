package dev.inventorymanagerplus.inventory;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Reads the player's inventory into a plain array, and produces a cheap fingerprint of it.
 *
 * <p>The fingerprint exists so Auto Sort can answer "did anything actually change?" without
 * building a plan. Planning is not expensive in absolute terms, but doing it every few ticks
 * forever is exactly the kind of steady background cost that shows up as a frame-time bump, so the
 * common case — nothing moved — has to be a single cheap loop with no allocation at all.
 */
public final class InventorySnapshot {

    private InventorySnapshot() {
    }

    /** Slots 0..40 inclusive. Stacks are shared, not copied — the planner only reorders references. */
    public static ItemStack[] take(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int size = Math.min(inv.getContainerSize(), 41);
        ItemStack[] out = new ItemStack[41];
        for (int i = 0; i < out.length; i++) {
            out[i] = i < size ? inv.getItem(i) : ItemStack.EMPTY;
        }
        return out;
    }

    /**
     * Order-sensitive fingerprint of every slot: item, item details and count.
     *
     * <p>Item details are included so that two diamond swords trading places is seen as a change
     * when only one of them has the enchantment a preset asks for. That also means a tool losing
     * durability counts as a change, which only costs one extra (empty) plan.
     */
    public static long fingerprint(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int size = Math.min(inv.getContainerSize(), 41);
        long h = 1125899906842597L;
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getItem(i);
            int itemHash = s.isEmpty() ? 0 : ItemStack.hashItemAndComponents(s);
            h = 31 * h + itemHash;
            h = 31 * h + s.getCount();
        }
        return h;
    }
}
