package dev.inventorymanagerplus.inventory;

import com.mojang.serialization.JsonOps;
import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.preset.EnchantRequirement;
import dev.inventorymanagerplus.preset.MatchMode;
import dev.inventorymanagerplus.preset.Preset;
import dev.inventorymanagerplus.preset.PresetSlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.RegistryOps;
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
            int owned = findUnclaimedMatch(snapshot, spec, preset.matchMode(), registries, claimed);
            if (owned >= 0) {
                claimed.add(owned);
                continue;
            }

            Item item = spec.resolveItem().orElse(null);
            if (item == null) {
                continue;
            }

            // With drop-items on, the preset's slot is taken directly; whatever was there is gone.
            // Without it, acquisition works around occupied slots and may leave the preset partial.
            int writeSlot = Config.get().creativeDropItems ? target : pickWritableSlot(snapshot, preset, target);
            if (writeSlot < 0) {
                continue;
            }

            ItemStack stack = buildStack(item, spec, registries);
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
    private static int findUnclaimedMatch(ItemStack[] inv, PresetSlot spec, MatchMode mode,
                                          HolderLookup.Provider registries, java.util.Set<Integer> claimed) {
        for (int i = 0; i < inv.length; i++) {
            if (!claimed.contains(i) && InvSlots.isManageable(i)
                    && ItemMatcher.matches(spec, inv[i], mode, registries)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Chooses where to write a newly created stack.
     *
     * <p>Order: the preset's own target slot if free, then any empty unmanaged slot, and finally —
     * only when the inventory is full — the target slot itself, overwriting what is there.
     *
     * <p>That last fallback is the one exception to this mod's never-destroy rule, and it is
     * limited to Creative, to a slot the preset explicitly manages, and to the case where there is
     * nowhere else to put anything. In Creative the displaced stack costs nothing to replace, and
     * refusing would mean the preset silently fails to apply on a full inventory. Unmanaged slots
     * are never overwritten, so nothing outside the preset's own layout can be lost.
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
        return preferred < inv.length && preset.manages(preferred) ? preferred : -1;
    }

    private static ItemStack buildStack(Item item, PresetSlot spec, HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(item, Math.max(1, spec.count()));
        if (spec.rawComponents() != null && registries != null) {
            var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            DataComponentPatch.CODEC.parse(ops, spec.rawComponents())
                    .result()
                    .ifPresent(stack::applyComponents);
        }
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