package dev.inventorymanagerplus.gui;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/** Minecraft's yes/no confirm screen, with its two buttons drawn in the mod's colour theme. */
public final class ThemedConfirmScreen extends ConfirmScreen {

    public ThemedConfirmScreen(BooleanConsumer callback, Component title, Component message,
                               Component yes, Component no) {
        super(callback, title, message, yes, no);
    }

    @Override
    protected void addButtons(LinearLayout buttonLayout) {
        this.yesButton = buttonLayout.addChild(
                ThemedButton.create(this.yesButtonComponent, b -> this.callback.accept(true)).build());
        this.noButton = buttonLayout.addChild(
                ThemedButton.create(this.noButtonComponent, b -> this.callback.accept(false)).build());
    }
}
