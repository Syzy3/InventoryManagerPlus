package dev.inventorymanagerplus.inventory;

import dev.inventorymanagerplus.preset.EnchantRequirement;
import dev.inventorymanagerplus.preset.PresetSlot;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.Map;


/**
 * Decides whether a stack in the inventory satisfies a preset slot.
 *
 * <p>Stack size is never part of the decision, and neither is durability: a half-worn pickaxe
 * is still the pickaxe the player meant.
 */
public final class ItemMatcher {

    private ItemMatcher() {
    }

    /**
     * @param slot the inventory slot the stack would sit in; lets "Any Armor" in an armour slot
     *             accept only armour for that slot
     */
    public static boolean matches(PresetSlot spec, ItemStack stack, int slot) {
        if (spec.isBlank()) {
            return stack.isEmpty();
        }
        if (stack.isEmpty()) {
            return false;
        }

        // A category slot asks a question about the kind of item, so item identity and the
        // recorded component blob are both irrelevant to it.
        if (spec.isCategory()) {
            return spec.category().matches(stack, slot);
        }

        Item wanted = spec.resolveItem().orElse(null);
        if (wanted == null || stack.getItem() != wanted) {
            return false;
        }

        // Only the item type and the slot's enchantment condition count. Durability, custom
        // names and other item details are ignored: "put my sword here" means any sword.
        return enchantsSatisfied(spec.enchants(), stack);
    }

    /**
     * Reads the enchantments actually present on a stack, keyed by registry id.
     *
     * <p>Ids rather than {@code Holder}s so the comparison works against what the preset stored
     * on disk without needing a bound registry.
     */
    private static Map<Identifier, Integer> enchantmentsOn(ItemStack stack) {
        Map<Identifier, Integer> out = new HashMap<>();
        ItemEnchantments present = stack.get(DataComponents.ENCHANTMENTS);
        if (present == null) {
            return out;
        }
        for (var entry : present.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            holder.unwrapKey().ifPresent(key -> out.put(key.identifier(), entry.getIntValue()));
        }
        return out;
    }

    /** Whether a stack meets the slot's enchantment condition. */
    public static boolean enchantsSatisfied(EnchantRequirement req, ItemStack stack) {
        if (req == null || req.mode() == EnchantRequirement.Mode.IGNORE) {
            return true;
        }
        Map<Identifier, Integer> present = enchantmentsOn(stack);
        if (req.mode() == EnchantRequirement.Mode.ANY) {
            return !present.isEmpty();
        }
        for (var wantedEntry : req.levels().entrySet()) {
            Integer actual = present.get(wantedEntry.getKey());
            if (actual == null) {
                return false;
            }
            int wantedLevel = wantedEntry.getValue();
            // A specific level is a minimum, not an equality test: a player asking for
            // Efficiency IV is not disappointed by Efficiency V.
            if (wantedLevel != EnchantRequirement.ANY_LEVEL && actual < wantedLevel) {
                return false;
            }
        }
        return true;
    }

    /**
     * Score used to pick between several stacks that all satisfy a requirement.
     *
     * <p>Higher wins. The ranking encodes preferences a player would voice if asked: take the
     * bigger stack (so the hotbar gets the 64 blocks, not the leftover 3), prefer a stack that is
     * already close to where it needs to go, and — when the target is a hotbar slot — pull from
     * the main inventory rather than from elsewhere in the hotbar.
     *
     * <p>That last rule is what makes "obsidian should fill the hotbar first" work. Without it,
     * filling a hotbar slot could rob another hotbar slot, shuffling the bar around instead of
     * drawing stock up from the backpack.
     */
    public static int score(ItemStack candidate, int candidateSlot, int targetSlot, boolean unmanaged) {
        int s = 0;
        if (unmanaged) {
            s += 10_000;                       // taking from an unmanaged slot disturbs nothing else
        }
        if (InvSlots.isHotbar(targetSlot) && !InvSlots.isHotbar(candidateSlot)) {
            s += 5_000;                        // stock the hotbar from storage, not from itself
        }
        s += Math.min(candidate.getCount(), 64) * 10;
        s -= Math.abs(candidateSlot - targetSlot);
        return s;
    }

    /** Convenience for the editor: turn a live stack into a storable identifier. */
    public static Identifier idOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}