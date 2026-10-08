package dev.inventorymanagerplus.kinetic;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The Kinetic button on the pause screen: a 20x20 icon in the row of small buttons that opens
 * {@link KineticScreen}.
 *
 * <p>Every Kinetic mod carries its own copy of this class (in its own package), so any one of
 * them works alone. When several are installed, only the first to run adds the button: the
 * others find a widget already labelled {@link #LABEL} and stop.
 */
public final class KineticButton {

    /** Shared by every Kinetic mod's copy. Changing it would give players duplicate buttons. */
    public static final String LABEL = "Kinetic";

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

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof PauseScreen)) {
                return;
            }

            List<AbstractWidget> widgets = Screens.getWidgets(screen);
            for (AbstractWidget widget : widgets) {
                if (LABEL.equals(widget.getMessage().getString())) {
                    return; // another Kinetic mod already added it
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

            ImageButton button = new ImageButton(x, y, SIZE, SIZE, SPRITES,
                    b -> Minecraft.getInstance().gui.setScreen(new KineticScreen(screen)),
                    Component.literal(LABEL));
            button.setTooltip(Tooltip.create(Component.literal(LABEL)));

            widgets.add(button);
        });
    }
}
