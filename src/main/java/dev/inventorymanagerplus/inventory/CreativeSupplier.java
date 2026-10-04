package dev.inventorymanagerplus.inventory;

import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.preset.EnchantRequirement;
import dev.inventorymanagerplus.preset.Preset;
import dev.inventorymanagerplus.preset.PresetSlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Fills in preset items the player does not own, using the Creative-mode "set slot" action.
 *
 * <p>This is the same mechanism the Creative inventory screen itself uses when you drag an item
 * out of a tab, so it is legitimate in exactly the situations vanilla allows it: Creative mode, and
 * a server that accepts Creative slot packets from that player. It is not a way to obtain items in
 * Survival, and no attempt is made to use it there.
 *
 * <p>Two behaviours worth calling out. Items the player already has are skipped entirely, so
 * clicking a preset repeatedly in Creative does not pile up duplicates. And items are only written
 * into slots that are already empty or already hold the right item — never over the top of
 * something else — which keeps the "never destroy an item" guarantee intact even here, where the
 * API would happily let us overwrite.
 */
public final class CreativeSupplier {

    private CreativeSupplier() {
    }

    /**
     * @return number of stacks created
     */
    public static int supplyMissing(Minecraft mc, Preset preset) {
        if (!Config.get().creativeAcquisition) {
            return 0;
        }

        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null || !player.isCreative()) {
            return 0;
        }

        java.util.Set<Integer> claimed = new java.util.HashSet<>();

        HolderLookup.Provider registries = registriesOf(mc);
        ItemStack[] snapshot = InventorySnapshot.take(player);
        int created = 0;

        for (Map.Entry<Integer, PresetSlot> entry : preset.slots().entrySet()) {
            int target = entry.getKey();
            PresetSlot spec = entry.getValue();
            if (spec.isBlank() || !InvSlots.isManageable(target)) {
                continue;
            }

            // Already owned somewhere? Then the planner will move it; do not conjure a second one.
            // Each requirement needs its own stack. Claiming the one it will use stops a second
            // slot asking for the same item from seeing it as already satisfied — which is why a
            // preset wanting four totems used to get exactly one.
            int owned = findUnclaimedMatch(snapshot, preset, spec, target, claimed);
            if (owned >= 0) {
                claimed.add(owned);
                continue;
            }

            Item item = spec.resolveItem().orElse(null);
            if (item == null) {
                continue;
            }

            // Acquisition works around whatever is already sitting in the slot, so a preset may
            // come out partial rather than take a slot by force.
            int writeSlot = pickWritableSlot(snapshot, preset, target);
            if (writeSlot < 0) {
                continue;
            }

            ItemStack stack = buildStack(item, spec, registries);
            // Category slots can't be created, and a requirement the game can't build (an
            // enchantment id that no longer exists, say) would produce a stack the matcher then
            // rejects. Writing either would only fill the inventory with things nobody asked for.
            if (stack.isEmpty() || !ItemMatcher.matches(spec, stack, target)) {
                continue;
            }
            int menuSlot = InvSlots.toMenuSlot(player.inventoryMenu, player, writeSlot);
            if (menuSlot < 0) {
                continue;
            }

            // Write locally *before* sending, mirroring what the Creative inventory screen does.
            // Without this the client's own inventory still looks empty when the planner runs a
            // moment later, so it plans nothing and the newly created stacks never get arranged.
            player.getInventory().setItem(writeSlot, stack);
            gameMode.handleCreativeModeItemAdd(stack, menuSlot);
            snapshot[writeSlot] = stack;
            created++;
        }
        return created;
    }

    /**
     * Finds a matching stack that no earlier requirement has already spoken for, or -1 if every
     * match is claimed. Claiming matters because a preset may want the same item in several slots.
     */
    private static int findUnclaimedMatch(ItemStack[] inv, Preset preset, PresetSlot spec, int target,
                                          java.util.Set<Integer> claimed) {
        for (int i = 0; i < inv.length; i++) {
            if (claimed.contains(i) || !InvSlots.isManageable(i)) {
                continue;
            }
            // Worn armour and the off-hand item only count as owned when the preset manages that
            // slot. The planner never takes them otherwise, so counting them here would mean the
            // chestplate you're wearing stops a preset from getting its own.
            if (i >= InvSlots.STORAGE_SIZE && i != target && !preset.manages(i)) {
                continue;
            }
            if (ItemMatcher.matches(spec, inv[i], target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Chooses where to write a newly created stack.
     *
     * <p>Order: the preset's own target slot if free, then any empty unmanaged slot. With no empty
     * slot the item is simply not created: overwriting would destroy whatever is there (which in
     * Creative can still be a filled shulker box), and the mod never destroys items.
     */
    private static int pickWritableSlot(ItemStack[] inv, Preset preset, int preferred) {
        if (preferred < inv.length && inv[preferred].isEmpty()) {
            return preferred;
        }
        for (int i = 0; i < InvSlots.STORAGE_SIZE; i++) {
            if (inv[i].isEmpty() && !preset.manages(i)) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack buildStack(Item item, PresetSlot spec, HolderLookup.Provider registries) {
        // Category slots have no single item to create — "any block" could be a thousand things —
        // so they are reported unfilled rather than guessed at.
        if (spec.isCategory()) {
            return ItemStack.EMPTY;
        }
        // Presets no longer store an amount, so Creative hands over a full stack, the same as
        // middle-clicking an item in the Creative menu.
        ItemStack stack = new ItemStack(item, Math.max(1, item.getDefaultMaxStackSize()));
        applyRequiredEnchantments(stack, spec, registries);
        return stack;
    }

    /**
     * Puts the slot's required enchantments onto a freshly conjured stack.
     *
     * <p>Without this the Creative path hands over a plain item, which the matcher then rejects
     * for missing the very enchantments the preset asked for — the preset reports the item as
     * unavailable while standing next to the copy it just created.
     *
     * <p>A requirement of "any level" is granted at level 1, and a level requirement is granted
     * exactly, since it is a minimum and the cheapest satisfying stack is the honest one to make.
     */
    private static void applyRequiredEnchantments(ItemStack stack, PresetSlot spec,
                                                  HolderLookup.Provider registries) {
        EnchantRequirement req = spec.enchants();
        if (registries == null || req == null || req.levels().isEmpty()) {
            return;
        }
        var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable built = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        boolean any = false;
        for (var entry : req.levels().entrySet()) {
            var holder = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()));
            if (holder.isEmpty()) {
                continue;
            }
            int level = entry.getValue() == EnchantRequirement.ANY_LEVEL ? 1 : entry.getValue();
            built.set(holder.get(), level);
            any = true;
        }
        if (any) {
            stack.set(DataComponents.ENCHANTMENTS, built.toImmutable());
        }
    }

    public static HolderLookup.Provider registriesOf(Minecraft mc) {
        return mc.level == null ? null : mc.level.registryAccess();
    }
}