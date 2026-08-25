package dev.inventorymanagerplus.preset;

/**
 * How strictly a preset slot's saved item is compared against a stack in the player's inventory.
 */
public enum MatchMode {
    /**
     * Item type only. A Diamond Sword matches any Diamond Sword regardless of durability,
     * enchantments, custom name or other data components. This is the sane default: players
     * expect "put my sword in slot 0", not "put that exact sword in slot 0".
     */
    BASIC,

    /**
     * Item type plus all data components (enchantments, custom name, potion contents, dyed
     * colour, ...). Durability is still ignored, because a pickaxe that has been used one
     * block is still "the same pickaxe" to a player. Use this to distinguish e.g. a Sharpness V
     * sword from a plain one, or Splash Healing II from Splash Poison.
     */
    EXACT;

    public MatchMode other() {
        return this == BASIC ? EXACT : BASIC;
    }
}
