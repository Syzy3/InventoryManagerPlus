package dev.inventorymanagerplus.kinetic;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Kinetic organization screen: the logo, a link to the Modrinth page, and a card for every
 * installed Kinetic mod with a button to open its settings.
 *
 * <p>A mod counts as Kinetic when its {@code fabric.mod.json} has a {@code custom.kinetic}
 * block. That block may name a settings screen as {@code "settings": "some.Class#method"},
 * where the method is {@code public static Screen method(Screen parent)}. It is called through
 * reflection, so Kinetic mods never need each other's classes or a shared library.
 */
public final class KineticScreen extends Screen {

    public static final String ORG_URL = "https://modrinth.com/organization/kinetic";
    private static final String TAGLINE = "Kinetic makes Minecraft mods that just work.";

    private static final Identifier LOGO =
            Identifier.fromNamespaceAndPath("inventory-manager-plus", "textures/gui/kinetic_logo.png");

    private static final int PURPLE = 0xFF8A1CFF;
    private static final int PURPLE_DARK = 0xFF3D0F70;
    private static final int CARD = 0xE0100C16;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA9A3B3;

    private static final int LOGO_SIZE = 40;
    private static final int CARD_WIDTH = 300;
    private static final int CARD_HEIGHT = 44;
    private static final int CARD_GAP = 6;
    private static final int LIST_TOP = 78;

    private record Entry(String name, String version, String description, String settings) {
    }

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Button> cardButtons = new ArrayList<>();
    private int scroll;

    public KineticScreen(Screen parent) {
        super(Component.literal("Kinetic"));
        this.parent = parent;
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = mod.getMetadata();
            if (!meta.containsCustomValue("kinetic")) {
                continue;
            }
            String settings = null;
            CustomValue value = meta.getCustomValue("kinetic");
            if (value.getType() == CustomValue.CvType.OBJECT) {
                CustomValue s = value.getAsObject().get("settings");
                if (s != null && s.getType() == CustomValue.CvType.STRING) {
                    settings = s.getAsString();
                }
            }
            entries.add(new Entry(meta.getName(), meta.getVersion().getFriendlyString(),
                    meta.getDescription(), settings));
        }
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    protected void init() {
        cardButtons.clear();
        int left = this.width / 2 - CARD_WIDTH / 2;

        addRenderableWidget(KineticStyle.button(Component.literal("Modrinth Page"),
                ConfirmLinkScreen.confirmLink(this, URI.create(ORG_URL)))
                .bounds(left + CARD_WIDTH - 90, 26, 90, 20).build());

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            Button settings = KineticStyle.button(Component.literal("Open"), b -> openSettings(entry))
                    .bounds(left + CARD_WIDTH - 70, 0, 62, 20).build();
            if (entry.settings() == null) {
                settings.active = false;
                settings.setTooltip(Tooltip.create(Component.literal("This mod has no settings yet.")));
            }
            addRenderableWidget(settings);
            cardButtons.add(settings);
        }
        layoutCards();

        addRenderableWidget(KineticStyle.button(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
    }

    private int listBottom() {
        return this.height - 36;
    }

    private int maxScroll() {
        int content = entries.size() * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
        return Math.max(0, content - (listBottom() - LIST_TOP));
    }

    private int cardTop(int index) {
        return LIST_TOP + index * (CARD_HEIGHT + CARD_GAP) - scroll;
    }

    /** Moves each card's button with the scroll, hiding the ones scrolled out of the list. */
    private void layoutCards() {
        for (int i = 0; i < cardButtons.size(); i++) {
            Button b = cardButtons.get(i);
            int y = cardTop(i) + (CARD_HEIGHT - 20) / 2;
            b.setY(y);
            b.visible = y >= LIST_TOP && y + 20 <= listBottom();
        }
    }

    private void openSettings(Entry entry) {
        Minecraft mc = Minecraft.getInstance();
        try {
            int hash = entry.settings().indexOf('#');
            Class<?> owner = Class.forName(entry.settings().substring(0, hash), true,
                    KineticScreen.class.getClassLoader());
            Method method = owner.getMethod(entry.settings().substring(hash + 1), Screen.class);
            Screen screen = (Screen) method.invoke(null, this);
            if (screen != null) {
                mc.gui.setScreen(screen);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            org.slf4j.LoggerFactory.getLogger("Kinetic")
                    .warn("Could not open settings for {} ({})", entry.name(), entry.settings(), e);
        }
    }

    /** The text cut to fit {@code width}, ending in "..." when it was too long. */
    private String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, width - this.font.width("...")).stripTrailing() + "...";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mouseY >= LIST_TOP && mouseY < listBottom()) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (dy * 14)));
            layoutCards();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int left = this.width / 2 - CARD_WIDTH / 2;

        // Header: logo, name, tagline.
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, left, 16, 0, 0,
                LOGO_SIZE, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE, LOGO_SIZE);
        graphics.text(this.font, "Kinetic", left + LOGO_SIZE + 8, 22, PURPLE, true);
        // Wrapped to the space left of the Modrinth button; two lines fit above the divider.
        int lineY = 34;
        for (FormattedCharSequence line : this.font.split(Component.literal(TAGLINE),
                CARD_WIDTH - LOGO_SIZE - 106)) {
            graphics.text(this.font, line, left + LOGO_SIZE + 8, lineY, MUTED, false);
            lineY += 10;
        }
        graphics.fill(left, LIST_TOP - 10, left + CARD_WIDTH, LIST_TOP - 9, PURPLE_DARK);

        // Cards, clipped to the list area.
        graphics.enableScissor(left - 1, LIST_TOP - 1, left + CARD_WIDTH + 1, listBottom() + 1);
        if (entries.isEmpty()) {
            graphics.centeredText(this.font, Component.literal("No Kinetic mods found."),
                    this.width / 2, LIST_TOP + 10, MUTED);
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int top = cardTop(i);
            if (top + CARD_HEIGHT < LIST_TOP || top > listBottom()) {
                continue;
            }
            graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, CARD);
            graphics.outline(left, top, CARD_WIDTH, CARD_HEIGHT, PURPLE_DARK);
            graphics.fill(left, top, left + 3, top + CARD_HEIGHT, PURPLE);

            int textX = left + 10;
            int textWidth = CARD_WIDTH - 90;
            String title = entry.name() + "  v" + entry.version();
            graphics.text(this.font, fit(title, textWidth), textX, top + 9, TEXT, true);
            graphics.text(this.font, fit(entry.description(), textWidth), textX, top + 25, MUTED, false);
        }
        graphics.disableScissor();

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
