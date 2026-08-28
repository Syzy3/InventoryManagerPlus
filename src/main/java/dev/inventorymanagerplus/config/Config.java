package dev.inventorymanagerplus.config;

import dev.inventorymanagerplus.preset.MatchMode;

/**
 * Plain data holder, serialised straight to JSON by Gson. Field names are the config keys, so
 * renaming a field silently resets it — add a migration instead if that ever becomes necessary.
 */
public final class Config {

    private static Config instance = new Config();

    public static Config get() {
        return instance;
    }

    static void set(Config c) {
        instance = c == null ? new Config() : c.sanitised();
    }

    // ---- Auto Sort -------------------------------------------------------------------------

    /** Master switch. Presets can still be applied manually while this is off. */
    public boolean autoSortEnabled = true;

    /**
     * How often Auto Sort compares the inventory against the active preset, in ticks.
     * 10 ticks (half a second) is far below human reaction time but 1/10th the cost of checking
     * every tick.
     */
    public int autoSortIntervalTicks = 10;

    /** Ticks to wait between two inventory operations. Keeps click packets from bursting. */
    public int operationCooldownTicks = 2;

    /** Upper bound on moves queued from a single apply, as a runaway guard. */
    public int maxMovesPerApply = 64;

    /**
     * Auto Sort only corrects this many slots per cycle, so a large disturbance is repaired
     * gradually rather than in one burst of packets.
     */
    public int maxMovesPerAutoSortCycle = 4;

    // ---- Behaviour -------------------------------------------------------------------------

    /** Default matching mode for newly created presets. */
    public MatchMode defaultMatchMode = MatchMode.BASIC;

    /** Ask before deleting a preset. */
    public boolean confirmDelete = true;

    /** In Creative, obtain items the preset needs but the player lacks. */
    public boolean creativeAcquisition = false;

    /**
     * Pause all inventory activity while any screen is open, including the player's own inventory.
     *
     * <p>This is what lets you rearrange things by hand without Auto Sort undoing the work as you
     * go. Chests and other containers are always paused regardless of this setting — that guard is
     * not optional, because clicking inside someone else's storage is not a mistake worth risking.
     */
    public boolean pauseWhileInventoryOpen = true;

    /** Show apply/toggle feedback on the action bar. */
    public boolean showStatusMessages = true;

    /** Index into {@link dev.inventorymanagerplus.gui.Theme}'s palette list. 0 = Grey. */
    public int accentIndex = 0;

    /** GLFW key code bound to opening the menu. 90 = Z. */
    public int openKeyCode = 90;

    Config sanitised() {
        autoSortIntervalTicks = clamp(autoSortIntervalTicks, 1, 200);
        operationCooldownTicks = clamp(operationCooldownTicks, 0, 40);
        maxMovesPerApply = clamp(maxMovesPerApply, 1, 256);
        maxMovesPerAutoSortCycle = clamp(maxMovesPerAutoSortCycle, 1, 64);
        if (defaultMatchMode == null) {
            defaultMatchMode = MatchMode.BASIC;
        }
        accentIndex = Math.max(0, accentIndex);
        return this;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}