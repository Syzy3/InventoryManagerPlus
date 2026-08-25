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
     * Order-sensitive fingerprint. Allocation-free and does not touch data components, so it costs
     * about the same as iterating a 41-element array.
     *
     * <p>Deliberately ignores component details: a sword losing durability should not be treated as
     * an inventory change worth re-planning for.
     */
    public static long fingerprint(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int size = Math.min(inv.getContainerSize(), 41);
        long h = 1125899906842597L;
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getItem(i);
            int itemHash = s.isEmpty() ? 0 : System.identityHashCode(s.getItem());
            h = 31 * h + itemHash;
            h = 31 * h + s.getCount();
        }
        return h;
    }
}
