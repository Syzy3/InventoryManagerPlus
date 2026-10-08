package dev.inventorymanagerplus.kinetic;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The Kinetic button on the pause screen: a 20x20 icon in the row of small buttons that opens
 * {@link KineticScreen}.
 *
 * <p>Every Kinetic mod carries its own copy of this class (in its own package), so any one of
 * them works alone. When several are installed, the copy with the highest {@link #VERSION}
 * adds the button, so players always get the newest Kinetic screen from whichever of their
 * mods has it. Older copies (InventoryManager+ 1.1.2 has none) add a button labelled
 * {@link #LABEL} if none is there yet; the newest copy hides that one and puts its own in
 * the same spot.
 */
public final class KineticButton {

    /** Shared by every Kinetic mod's copy. Changing it would give players duplicate buttons. */
    public static final String LABEL = "Kinetic";

    /**
     * Raise this whenever the Kinetic button or screen changes. 1 was the first copy (no number,
     * InventoryManager+ 1.1.2); 2 = "Open" buttons and "..." for long descriptions.
     */
    public static final int VERSION = 2;

    /** System properties every copy uses to agree on which copy adds the button. */
    private static final String OWNER_VERSION = "kinetic.button.version";
    private static final String OWNER_CLASS = "kinetic.button.owner";

    private static final String MOD_ID = "inventory-manager-plus";

    private static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(MOD_ID, "kinetic"),
            Identifier.fromNamespaceAndPath(MOD_ID, "kinetic-highlighted"));

    /** Half-width of the centre region, used to ignore the button column on the right edge. */
    private static final int CENTRE_REGION = 120;

    private static final int SIZE = 20;
    private static final int GAP = 4;

    private KineticButton() {
    }

    /** Our own button, so we can tell it apart from an older copy's. */
    private static final class OwnButton extends ImageButton {
        OwnButton(int x, int y, Screen parent) {
            super(x, y, SIZE, SIZE, SPRITES,
                    b -> Minecraft.getInstance().gui.setScreen(new KineticScreen(parent)),
                    Component.literal(LABEL));
            setTooltip(Tooltip.create(Component.literal(LABEL)));
        }
    }

    public static void register() {
        synchronized (System.getProperties()) {
            if (VERSION > Integer.getInteger(OWNER_VERSION, 0)) {
                System.setProperty(OWNER_VERSION, Integer.toString(VERSION));
                System.setProperty(OWNER_CLASS, KineticButton.class.getName());
            }
        }

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof PauseScreen)) {
                return;
            }
            if (!KineticButton.class.getName().equals(System.getProperty(OWNER_CLASS))) {
                return; // a newer copy in another Kinetic mod adds the button
            }

            List<AbstractWidget> widgets = Screens.getWidgets(screen);
            for (AbstractWidget widget : widgets) {
                if (widget instanceof OwnButton) {
                    return; // already added
                }
                if (widget.visible && LABEL.equals(widget.getMessage().getString())) {
                    // An older copy got there first: hide its button and use the same spot.
                    widget.visible = false;
                    widget.active = false;
                    widgets.add(new OwnButton(widget.getX(), widget.getY(), screen));
                    return;
                }
            }

            int centreX = scaledWidth / 2;
            List<AbstractWidget> row = new ArrayList<>();
            int rowY = -1;
            int rowRight = Integer.MIN_VALUE;

            for (AbstractWidget widget : widgets) {
                if (widget.getWidth() != SIZE || widget.getHeight() != SIZE) {
                    continue;
                }
                // Skip the vertical column of buttons hugging the right edge.
                if (Math.abs(widget.getX() + SIZE / 2 - centreX) > CENTRE_REGION) {
                    continue;
                }
                row.add(widget);
                rowY = widget.getY();
                rowRight = Math.max(rowRight, widget.getX() + SIZE);
            }

            int x;
            int y;
            if (row.isEmpty()) {
                // Vanilla layout changed and we didn't recognise the row. Park it under the title
                // rather than dropping the button entirely.
                x = centreX + 110;
                y = scaledHeight / 4 + 8;
            } else {
                int shift = (SIZE + GAP) / 2;
                for (AbstractWidget widget : row) {
                    widget.setX(widget.getX() - shift);
                }
                x = rowRight - shift + GAP;
                y = rowY;
            }

            widgets.add(new OwnButton(x, y, screen));
        });
    }
}
