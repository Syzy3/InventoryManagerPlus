package dev.inventorymanagerplus.inventory;

import dev.inventorymanagerplus.InventoryManagerPlus;
import dev.inventorymanagerplus.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Executes a {@link ArrangementPlanner.Plan} as ordinary inventory clicks, spread over time.
 *
 * <h2>Multiplayer behaviour</h2>
 *
 * Every action taken here is a click a player could physically perform: pick up a stack, put it
 * down, or use the number-key swap. Nothing is faked client-side — the click is handed to
 * {@code MultiPlayerGameMode}, which sends the packet and lets the server confirm or reject it in
 * the normal way. If the server rejects an action the resulting resync simply shows up as the
 * inventory not matching the plan, and the queue is abandoned rather than retried in a loop.
 *
 * <h2>When it refuses to run</h2>
 *
 * In Survival, a chest or crafting table means the player's slots are only part of a larger menu
 * and a mistimed click could shift-move an item into someone's storage, so the executor operates
 * only on the player's own inventory menu and pauses whenever anything else is open. That single
 * check covers chests, crafting tables and every other container at once.
 *
 * <p>Creative takes a different route entirely — see {@link #creativeSwap} — and does not need
 * that guard, because creative packets cannot address a foreign container in the first place.
 */
public final class InventoryOps {

    /**
     * Runaway guard only. A plan for even the fullest inventory is a few hundred steps at most,
     * so this never cuts a real preset short.
     */
    private static final int SAFETY_CAP = 2000;

    private final Deque<ArrangementPlanner.Move> queue = new ArrayDeque<>();
    private int cooldown;
    private int abortedStreak;
    private String label = "";
    private boolean announceCompletion;
    /** Runs once the queue has emptied and settled; used by Apply to check its work. */
    private Runnable onDrained;
    /** Inventory fingerprint taken when the queue was first put on hold, or MIN_VALUE if not held. */
    private long heldFingerprint = Long.MIN_VALUE;

    public boolean isBusy() {
        return !queue.isEmpty() || onDrained != null;
    }

    public void cancel() {
        queue.clear();
        cooldown = 0;
        onDrained = null;
        heldFingerprint = Long.MIN_VALUE;
    }

    /**
     * Queues a plan. An in-flight queue is discarded first: the newer request reflects what the
     * player wants now, and half-finished plans are safe to abandon because every individual move
     * leaves the inventory in a consistent state.
     */
    public void submit(List<ArrangementPlanner.Move> moves, String label, boolean announce) {
        submit(moves, label, announce, null);
    }

    /**
     * @param onDrained run once every move has been sent and the inventory has settled, instead
     *                  of the completion message; it may submit more work
     */
    public void submit(List<ArrangementPlanner.Move> moves, String label, boolean announce,
                       Runnable onDrained) {
        queue.clear();
        int n = 0;
        for (ArrangementPlanner.Move m : moves) {
            if (n++ >= SAFETY_CAP) {
                break;
            }
            queue.add(m);
        }
        this.label = label;
        this.announceCompletion = announce;
        this.abortedStreak = 0;
        this.onDrained = onDrained;
        this.heldFingerprint = Long.MIN_VALUE;
    }

    /** Called once per client tick. */
    public void tick(Minecraft mc) {
        if (queue.isEmpty() && onDrained == null) {
            return;
        }

        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null || player.isRemoved()) {
            cancel();
            return;
        }

        // Player died, changed dimension, or is otherwise mid-transition: the inventory we planned
        // against no longer exists.
        if (player.isDeadOrDying() || player.isSpectator()) {
            cancel();
            return;
        }

        // Swapping the held item mid-use would cancel eating, drawing a bow or blocking, and a
        // click while something sits on the cursor would pick up or drop the wrong stack. Wait.
        if (player.isUsingItem() || !player.containerMenu.getCarried().isEmpty()) {
            return;
        }

        // Survival must go through the open menu, so a foreign container means stop. Creative
        // does not: creative slot packets address the player's inventory directly and never
        // reference the open container, so they stay safe even with a chest on screen.
        if (!player.isCreative() && player.containerMenu != player.inventoryMenu) {
            // Hold the queue rather than dropping it, so closing the chest resumes the work.
            markHeld(player);
            return;
        }

        if (Config.get().pauseWhileInventoryOpen && mc.gui.screen() != null) {
            // The player has a screen open — most likely their own inventory, mid-rearrange.
            // Hold rather than fight them for control of the same slots.
            markHeld(player);
            return;
        }

        // Resuming after a hold. Drops were planned against the inventory as it was before; if the
        // player moved things around in the meantime, a planned drop could now hit an item they
        // just put there. Swaps and merges are harmless to replay, drops are not, so stop.
        if (heldFingerprint != Long.MIN_VALUE) {
            boolean changed = InventorySnapshot.fingerprint(player) != heldFingerprint;
            heldFingerprint = Long.MIN_VALUE;
            if (changed && queue.stream().anyMatch(ArrangementPlanner.Move::isDrop)) {
                cancel();
                notifyPlayer(player, Component.literal("InventoryManager+: inventory changed, stopped without dropping anything."));
                return;
            }
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (queue.isEmpty()) {
            // Every move has gone out and had its cooldown to land. Hand over to whoever
            // asked to be told; that is how Apply re-checks the inventory and finishes off.
            Runnable done = onDrained;
            onDrained = null;
            done.run();
            return;
        }

        ArrangementPlanner.Move move = queue.poll();
        if (move == null) {
            return;
        }

        if (!performSwap(mc, player, gameMode, move)) {
            abortedStreak++;
            if (abortedStreak >= 3) {
                // Three consecutive failures means the world moved under us — a rejected packet,
                // a resync, items consumed elsewhere. Stop instead of hammering the server.
                cancel();
                notifyPlayer(player, Component.literal("InventoryManager+: stopped, the server rejected an inventory action."));
                return;
            }
        } else {
            abortedStreak = 0;
        }

        cooldown = Math.max(0, Config.get().operationCooldownTicks);

        if (queue.isEmpty() && onDrained == null && announceCompletion) {
            notifyPlayer(player, Component.literal("InventoryManager+: applied " + label));
        }
    }

    private void markHeld(LocalPlayer player) {
        if (heldFingerprint == Long.MIN_VALUE) {
            heldFingerprint = InventorySnapshot.fingerprint(player);
        }
    }

    /**
     * Performs one swap.
     *
     * <p>Two encodings are possible and the cheaper one is chosen. If either end of the swap is a
     * hotbar slot, a single {@code SWAP} click (the number-key action) does the whole job. That is
     * one packet instead of three and it is atomic on the server, so it cannot leave an item
     * stranded on the cursor if the connection hiccups mid-sequence. Everything else falls back to
     * the classic three-click pick-up / put-down / pick-up dance.
     *
     * @return false if the move could not be attempted at all
     */
    private boolean performSwap(Minecraft mc, LocalPlayer player, MultiPlayerGameMode gameMode,
                                ArrangementPlanner.Move move) {
        if (player.isCreative()) {
            if (move.isDrop()) {
                return creativeDrop(player, gameMode, move);
            }
            return move.isMerge() ? creativeMerge(player, gameMode, move) : creativeSwap(player, gameMode, move);
        }

        AbstractContainerMenu menu = player.containerMenu;
        int containerId = menu.containerId;

        if (move.isDrop()) {
            // Same as pressing Ctrl+Q over the slot: throws the whole stack.
            int slot = InvSlots.toMenuSlot(menu, player, move.from());
            if (slot < 0 || player.getInventory().getItem(move.from()).isEmpty()) {
                return false;
            }
            gameMode.handleContainerInput(containerId, slot, 1, ContainerInput.THROW, player);
            return true;
        }

        int fromMenu = InvSlots.toMenuSlot(menu, player, move.from());
        int toMenu = InvSlots.toMenuSlot(menu, player, move.to());
        if (fromMenu < 0 || toMenu < 0) {
            return false;
        }

        // Guard against the item having vanished since the plan was made (dropped by the player,
        // eaten, consumed by another mod). Swapping an already-empty slot is harmless but pointless.
        ItemStack source = player.getInventory().getItem(move.from());
        ItemStack dest = player.getInventory().getItem(move.to());
        if (source.isEmpty() && dest.isEmpty()) {
            return false;
        }

        if (move.isMerge()) {
            // Pick up the donor stack, drop it on the target (vanilla adds as many as fit and
            // keeps the rest on the cursor), then put the rest back where it came from. Same
            // three clicks as a swap; the number-key shortcut is skipped because it never merges.
            if (source.isEmpty()) {
                return false;
            }
            gameMode.handleContainerInput(containerId, fromMenu, 0, ContainerInput.PICKUP, player);
            gameMode.handleContainerInput(containerId, toMenu, 0, ContainerInput.PICKUP, player);
            gameMode.handleContainerInput(containerId, fromMenu, 0, ContainerInput.PICKUP, player);
            return true;
        }

        if (InvSlots.isHotbar(move.to())) {
            gameMode.handleContainerInput(containerId, fromMenu, move.to(), ContainerInput.SWAP, player);
            return true;
        }
        if (InvSlots.isHotbar(move.from())) {
            gameMode.handleContainerInput(containerId, toMenu, move.from(), ContainerInput.SWAP, player);
            return true;
        }

        // Three PICKUPs = swap. Note the cursor is empty again at the end, so an interrupted
        // sequence can at worst leave a stack on the cursor, which vanilla returns to the
        // inventory when the screen closes. Nothing is ever dropped.
        gameMode.handleContainerInput(containerId, fromMenu, 0, ContainerInput.PICKUP, player);
        gameMode.handleContainerInput(containerId, toMenu, 0, ContainerInput.PICKUP, player);
        gameMode.handleContainerInput(containerId, fromMenu, 0, ContainerInput.PICKUP, player);
        return true;
    }

    /**
     * Swaps two slots in Creative using set-slot packets instead of clicks.
     *
     * <p>The Creative inventory is a client-side menu the server never sees, so ordinary click
     * packets aimed at it reference a container that does not exist server-side and get dropped
     * or cause a desync. Vanilla's own Creative screen has the same problem and solves it the
     * same way: write the slot locally, then send a creative set-slot packet.
     *
     * <p>Slots resolve against {@code inventoryMenu} rather than whatever is open, because that is
     * the addressing creative packets use. A useful consequence: this can only ever write to the
     * player's own inventory, so it stays correct even with a chest open — the chest is simply not
     * addressable this way.
     *
     * <p>This is the one place the mod writes client-side before the server confirms. That is
     * unavoidable for creative slot setting and is exactly what vanilla does.
     */
    private boolean creativeSwap(LocalPlayer player, MultiPlayerGameMode gameMode,
                                 ArrangementPlanner.Move move) {
        int fromMenu = InvSlots.toMenuSlot(player.inventoryMenu, player, move.from());
        int toMenu = InvSlots.toMenuSlot(player.inventoryMenu, player, move.to());
        if (fromMenu < 0 || toMenu < 0) {
            return false;
        }

        // Copies, because the originals are about to be overwritten in place.
        ItemStack source = player.getInventory().getItem(move.from()).copy();
        ItemStack dest = player.getInventory().getItem(move.to()).copy();
        if (source.isEmpty() && dest.isEmpty()) {
            return false;
        }

        player.getInventory().setItem(move.from(), dest);
        player.getInventory().setItem(move.to(), source);
        gameMode.handleCreativeModeItemAdd(dest, fromMenu);
        gameMode.handleCreativeModeItemAdd(source, toMenu);
        return true;
    }

    /**
     * Creative version of a merge: moves as many items as fit from one stack onto a matching
     * stack, using set-slot packets for the same reasons as {@link #creativeSwap}.
     */
    private boolean creativeMerge(LocalPlayer player, MultiPlayerGameMode gameMode,
                                  ArrangementPlanner.Move move) {
        int fromMenu = InvSlots.toMenuSlot(player.inventoryMenu, player, move.from());
        int toMenu = InvSlots.toMenuSlot(player.inventoryMenu, player, move.to());
        if (fromMenu < 0 || toMenu < 0) {
            return false;
        }
        ItemStack source = player.getInventory().getItem(move.from()).copy();
        ItemStack dest = player.getInventory().getItem(move.to()).copy();
        if (source.isEmpty() || dest.isEmpty() || !ItemStack.isSameItemSameComponents(source, dest)) {
            return false;
        }
        int n = Math.min(source.getCount(), dest.getMaxStackSize() - dest.getCount());
        if (n <= 0) {
            return false;
        }
        dest.grow(n);
        source.shrink(n);
        ItemStack rest = source.isEmpty() ? ItemStack.EMPTY : source;
        player.getInventory().setItem(move.from(), rest);
        player.getInventory().setItem(move.to(), dest);
        gameMode.handleCreativeModeItemAdd(rest, fromMenu);
        gameMode.handleCreativeModeItemAdd(dest, toMenu);
        return true;
    }

    /** Creative version of a drop, done the way the Creative inventory screen throws items. */
    private boolean creativeDrop(LocalPlayer player, MultiPlayerGameMode gameMode,
                                 ArrangementPlanner.Move move) {
        int menuSlot = InvSlots.toMenuSlot(player.inventoryMenu, player, move.from());
        ItemStack stack = player.getInventory().getItem(move.from()).copy();
        if (menuSlot < 0 || stack.isEmpty()) {
            return false;
        }
        player.getInventory().setItem(move.from(), ItemStack.EMPTY);
        player.drop(stack, true, net.minecraft.util.Prediction.PREDICTED);
        gameMode.handleCreativeModeItemDrop(stack);
        gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, menuSlot);
        return true;
    }

    private void notifyPlayer(LocalPlayer player, Component text) {
        if (Config.get().showStatusMessages) {
            // The action bar moved off Player and onto Gui#hud in 26.x, so status
            // messages never spam the chat log.
            Minecraft.getInstance().gui.hud.setOverlayMessage(text, false);
        }
        InventoryManagerPlus.LOGGER.debug("{}", text.getString());
    }
}