package dev.inventorymanagerplus.autosort;

import dev.inventorymanagerplus.InventoryManagerPlus;
import dev.inventorymanagerplus.config.Config;
import dev.inventorymanagerplus.inventory.ArrangementPlanner;
import dev.inventorymanagerplus.inventory.CreativeSupplier;
import dev.inventorymanagerplus.inventory.InventorySnapshot;
import dev.inventorymanagerplus.preset.Preset;
import net.minecraft.client.Minecraft;
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

        if (!Config.get().autoSortEnabled || isPaused()) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying()) {
            return;
        }

        // Something is already being applied; stay out of its way.
        if (InventoryManagerPlus.ops().isBusy()) {
            return;
        }

        // A container is open, or the player is in a menu where clicks would land elsewhere.
        if (!player.isCreative() && player.containerMenu != player.inventoryMenu) {
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
        if (fingerprint == lastFingerprint) {
            return; // nothing moved since the last check
        }
        lastFingerprint = fingerprint;

        // In Creative with auto-get on, top up anything the preset wants but the player lacks,
        // every cycle. Cheap in the steady state: supplyMissing skips items already owned, so once
        // the layout is satisfied this sends nothing at all. It only does work after something
        // actually goes missing — which is exactly when you want it to.
        if (player.isCreative() && Config.get().creativeAcquisition) {
            CreativeSupplier.supplyMissing(mc, preset);
        }

        ItemStack[] snapshot = InventorySnapshot.take(player);
        var plan = ArrangementPlanner.plan(snapshot, preset, CreativeSupplier.registriesOf(mc));
        if (plan.isNoOp()) {
            // Items may still have been created above, so do not trust the fingerprint taken
            // before that ran.
            lastFingerprint = Long.MIN_VALUE;
            return;
        }

        var limited = plan.moves().subList(0, Math.min(plan.moves().size(), Config.get().maxMovesPerAutoSortCycle));
        InventoryManagerPlus.ops().submit(limited, preset.name(), false);

        // Invalidate: the next cycle must re-read rather than trust this fingerprint.
        lastFingerprint = Long.MIN_VALUE;
    }

    /** Called on world change / respawn / disconnect so stale state does not leak between sessions. */
    public void reset() {
        tickCounter = 0;
        lastFingerprint = Long.MIN_VALUE;
        pauseTicks = 0;
        temporarilyDisabled = false;
    }
}