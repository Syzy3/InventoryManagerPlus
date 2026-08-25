package dev.inventorymanagerplus.inventory;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.inventorymanagerplus.preset.EnchantRequirement;
import dev.inventorymanagerplus.preset.MatchMode;
import dev.inventorymanagerplus.preset.PresetSlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.Map;


/**
 * Decides whether a stack in the inventory satisfies a preset slot.
 *
 * <p>Stack size is never part of the decision. A preset saved with a full stack of 64 Cobblestone
 * is satisfied by a single Cobblestone; the count is a hint for Creative acquisition, not a
 * requirement. Durability is likewise ignored in both modes — a half-worn pickaxe is still the
 * pickaxe the player meant.
 */
public final class ItemMatcher {

    private ItemMatcher() {
    }

    public static boolean matches(PresetSlot spec, ItemStack stack, MatchMode mode,
                                  HolderLookup.Provider registries) {
        if (spec.isBlank()) {
            return stack.isEmpty();
        }
        if (stack.isEmpty()) {
            return false;
        }

        Item wanted = spec.resolveItem().orElse(null);
        if (wanted == null || stack.getItem() != wanted) {
            return false;
        }

        // Enchantment conditions are checked in both modes. BASIC deliberately ignores every
        // other component, but "any Efficiency pickaxe" is a request players make constantly and
        // there is no way to express it through EXACT, which demands the whole blob match.
        if (!enchantsSatisfied(spec.enchants(), stack)) {
            return false;
        }

        if (mode == MatchMode.BASIC) {
            return true;
        }

        // EXACT: every component recorded in the preset must be present and equal on the stack.
        // Components the preset does not mention are ignored, so a stack that merely gained a
        // repair-cost bump still matches.
        DataComponentPatch wantedPatch = decodePatch(spec.rawComponents(), registries);
        if (wantedPatch == null || wantedPatch.isEmpty()) {
            // Preset recorded no components: in EXACT mode require the stack to be plain too,
            // so "plain Diamond Sword" does not silently swallow the enchanted one.
            return stack.getComponentsPatch().isEmpty();
        }
        return patchSatisfiedBy(wantedPatch, stack);
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

    private static boolean patchSatisfiedBy(DataComponentPatch wanted, ItemStack stack) {
        for (var entry : wanted.entrySet()) {
            var type = entry.getKey();
            var expected = entry.getValue();
            Object actual = stack.get(type);
            if (expected.isEmpty()) {
                // The preset explicitly recorded the component as removed.
                if (actual != null) {
                    return false;
                }
            } else {
                if (actual == null || !expected.get().equals(actual)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static DataComponentPatch decodePatch(JsonElement json,
                                                  HolderLookup.Provider registries) {
        if (json == null || json.isJsonNull() || registries == null) {
            return null;
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return DataComponentPatch.CODEC.parse(ops, json).result().orElse(null);
    }

    /**
     * Score used to pick between several stacks that all satisfy a requirement.
     *
     * <p>Higher wins. The ranking encodes two preferences a player would voice if asked:
     * take the bigger stack (so the hotbar gets the 64 blocks, not the leftover 3), and prefer
     * a stack that is already close to where it needs to go, which shortens the click sequence
     * and reduces the chance of a mid-sequence desync.
     */
    public static int score(ItemStack candidate, int candidateSlot, int targetSlot, boolean unmanaged) {
        int s = 0;
        if (unmanaged) {
            s += 10_000;                       // taking from an unmanaged slot disturbs nothing else
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