package dev.inventorymanagerplus.autosort;

import dev.inventorymanagerplus.InventoryManagerPlus;
import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.inventory.ArrangementPlanner;
import dev.inventorymanagerplus.inventory.CreativeSupplier;
import dev.inventorymanagerplus.inventory.InventorySnapshot;
import dev.inventorymanagerplus.preset.Preset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps the active preset's slots correct while the player plays.
 *
 * <h2>How it avoids being annoying</h2>
 *
 * The failure mode of a feature like this is a mod that fights the player: they drag an item
 * somewhere, it snaps back, they drag it again, it snaps back. Four things prevent that here.
 *
 * <ul>
 *   <li>It only looks at the inventory every {@code autoSortIntervalTicks} ticks, and only does
 *       real work when a cheap fingerprint says something actually changed since last time.</li>
 *   <li>It plans, and does nothing at all if the plan is empty — which is the normal case, so the
 *       steady-state cost is one integer comparison twice a second.</li>
 *   <li>It corrects a few slots per cycle rather than everything at once, so a big disturbance
 *       heals over a second or two instead of in a packet burst.</li>
 *   <li>The temporary-disable key suspends it entirely while held, and for a short grace period
 *       afterwards, so the player can rearrange things by hand without a fight.</li>
 * </ul>
 *
 * <p>The fingerprint is also recorded after each queued correction. Without that, the mod's own
 * moves would look like an external change on the next cycle and it would re-plan against an
 * inventory that is still mid-update — the classic way these mods end up in a move loop.
 */
public final class AutoSortManager {

    /** Ticks after releasing the pause key before Auto Sort resumes. */
    private static final int RESUME_GRACE_TICKS = 20;

    private int tickCounter;
    private long lastFingerprint = Long.MIN_VALUE;
    /** Inventory the last batch of corrections was planned from. */
    private long lastSubmittedFrom = Long.MIN_VALUE;
    /**
     * Set when a batch of corrections changed nothing (the server refused it, or the game won't
     * allow it). Auto Sort then waits for the inventory to change instead of re-sending the same
     * clicks every half second.
     */
    private long stuckAt = Long.MIN_VALUE;
    private int pauseTicks;
    private boolean temporarilyDisabled;

    /** Held-key pause. Called every tick with the current key state. */
    public void setTemporarilyDisabled(boolean disabled) {
        if (disabled) {
            pauseTicks = RESUME_GRACE_TICKS;
        }
        temporarilyDisabled = disabled;
    }

    public boolean isPaused() {
        return temporarilyDisabled || pauseTicks > 0;
    }

    public void tick(Minecraft mc) {
        if (pauseTicks > 0) {
            pauseTicks--;
        }

        if (isPaused()) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying() || player.isSpectator()) {
            return;
        }

        // Mid-eat, mid-draw or holding something on the cursor: wait rather than interrupt.
        if (player.isUsingItem() || !player.containerMenu.getCarried().isEmpty()) {
            return;
        }

        // Something is already being applied; stay out of its way.
        if (InventoryManagerPlus.ops().isBusy()) {
            return;
        }

        // A container is open, or the player is in a menu where clicks would land elsewhere.
        // In Creative the Creative inventory screen swaps in its own menu, so that one screen is
        // allowed; a chest, furnace or any other container still pauses Auto Sort.
        if (player.containerMenu != player.inventoryMenu
                && !(player.isCreative() && mc.gui.screen() instanceof CreativeModeInventoryScreen)) {
            return;
        }

        // Player is looking at a screen — usually their inventory. Correcting slots out from under
        // a drag in progress is exactly the "mod fights the player" behaviour to avoid.
        if (Config.get().pauseWhileInventoryOpen && mc.gui.screen() != null) {
            return;
        }

        Preset preset = InventoryManagerPlus.presets().active();
        if (preset == null || !preset.autoSort()) {
            return;
        }

        if (++tickCounter < Config.get().autoSortIntervalTicks) {
            return;
        }
        tickCounter = 0;

        long fingerprint = InventorySnapshot.fingerprint(player);
        if (fingerprint == lastFingerprint || fingerprint == stuckAt) {
            return; // nothing moved since the last check, or nothing we can do about it
        }
        lastFingerprint = fingerprint;
        if (fingerprint == lastSubmittedFrom) {
            // Our last corrections were sent from exactly this inventory and it hasn't changed,
            // so they didn't take. Sending them again would just repeat forever.
            stuckAt = fingerprint;
            return;
        }

        // In Creative with auto-get on, top up anything the preset wants but the player lacks,
        // every cycle. Cheap in the steady state: supplyMissing skips items already owned, so once
        // the layout is satisfied this sends nothing at all. It only does work after something
        // actually goes missing — which is exactly when you want it to.
        int created = 0;
        if (player.isCreative() && Config.get().creativeAcquisition) {
            created = CreativeSupplier.supplyMissing(mc, preset);
        }

        ItemStack[] snapshot = InventorySnapshot.take(player);
        var plan = ArrangementPlanner.plan(snapshot, preset,
                Config.get().autoSortDrop);
        if (plan.isNoOp()) {
            // Nothing to do: keep this fingerprint so the next check is a cheap "unchanged". If
            // Creative just created items, though, the inventory has moved on since it was taken.
            if (created > 0) {
                lastFingerprint = Long.MIN_VALUE;
            }
            lastSubmittedFrom = Long.MIN_VALUE;
            return;
        }

        var limited = plan.moves().subList(0, Math.min(plan.moves().size(), Config.get().maxMovesPerAutoSortCycle));
        InventoryManagerPlus.ops().submit(limited, preset.name(), false);
        lastSubmittedFrom = fingerprint;

        // Invalidate: the next cycle must re-read rather than trust this fingerprint.
        lastFingerprint = Long.MIN_VALUE;
    }

    /** Called on world change / respawn / disconnect so stale state does not leak between sessions. */
    public void reset() {
        tickCounter = 0;
        lastFingerprint = Long.MIN_VALUE;
        lastSubmittedFrom = Long.MIN_VALUE;
        stuckAt = Long.MIN_VALUE;
        pauseTicks = 0;
        temporarilyDisabled = false;
    }
}