package dev.inventorymanagerplus.gui;

import dev.inventorymanagerplus.preset.EnchantCatalog;
import dev.inventorymanagerplus.preset.EnchantRequirement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Chooses the enchantment condition for one preset slot.
 *
 * <p>The list is the screen — it appears immediately rather than behind a mode switch, because
 * choosing enchantments is the only reason to be here. The stored mode is inferred from what gets
 * picked: nothing selected means the slot ignores enchantments entirely.
 *
 * <p>Row count is computed from the window height so the list can never run underneath the
 * Done/Cancel row, which is anchored to the bottom. A short window shows fewer rows and more
 * pages rather than overlapping.
 *
 * <p>Enchantment <em>names</em> are derived from the registry id rather than looked up for
 * translation. Real names live behind {@code Enchantment#getFullname}, whose shape has moved
 * repeatedly across versions; "minecraft:fire_aspect" to "Fire Aspect" is stable.
 */
public final class EnchantPickerScreen extends Screen {

    private static final int ROW_H = 20;
    private static final int LIST_TOP = 92;
    /** Height reserved at the bottom for the nav row and the Done/Cancel row. */
    private static final int BOTTOM_RESERVED = 56;
    private static final int MIN_ROWS = 3;

    private final Screen parent;
    private final String itemLabel;
    private final EnchantRequirement working;
    private final Consumer<EnchantRequirement> onDone;

    private final Identifier itemId;
    private final List<EnchantCatalog.Entry> matches = new ArrayList<>();
    private EditBox search;
    private int page;

    public EnchantPickerScreen(Screen parent, Identifier itemId, String itemLabel,
                               EnchantRequirement current,
                               Consumer<EnchantRequirement> onDone) {
        super(Component.literal("Enchantments"));
        this.parent = parent;
        this.itemId = itemId;
        this.itemLabel = itemLabel;
        // Edit a copy so Cancel genuinely cancels.
        this.working = current == null ? EnchantRequirement.ignore() : current.copy();
        this.onDone = onDone;
    }

    /** How many rows fit between the list top and the reserved bottom strip. */
    private int rowsPerPage() {
        int available = this.height - BOTTOM_RESERVED - LIST_TOP;
        return Math.max(MIN_ROWS, available / ROW_H);
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        refilter(query);

        int rows = rowsPerPage();
        clampPage(rows);

        search = new EditBox(this.font, this.width / 2 - 150, 52, 300, 20,
                Component.literal("Search"));
        search.setValue(query);
        search.setResponder(s -> {
            page = 0;
            rebuild();
        });
        addRenderableWidget(search);

        int start = page * rows;
        for (int i = 0; i < rows; i++) {
            int index = start + i;
            if (index >= matches.size()) {
                break;
            }
            EnchantCatalog.Entry entry = matches.get(index);
            addRenderableWidget(Button.builder(Component.literal(rowLabel(entry)), b -> {
                cycle(entry);
                rebuild();
            }).bounds(this.width / 2 - 150, LIST_TOP + i * ROW_H, 300, 18).build());
        }

        int navY = this.height - BOTTOM_RESERVED + 4;
        addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
            if (page > 0) {
                page--;
                rebuild();
            }
        }).bounds(this.width / 2 - 150, navY, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Clear all"), b -> {
            working.levels().clear();
            working.setMode(EnchantRequirement.Mode.IGNORE);
            rebuild();
        }).bounds(this.width / 2 - 45, navY, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
            if ((page + 1) * rows < matches.size()) {
                page++;
                rebuild();
            }
        }).bounds(this.width / 2 + 90, navY, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            normalise();
            onDone.accept(working);
            onClose();
        }).bounds(this.width / 2 - 104, this.height - 28, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 100, 20).build());
    }

    private void clampPage(int rows) {
        int maxPage = Math.max(0, (matches.size() - 1) / Math.max(1, rows));
        page = Math.min(page, maxPage);
    }

    /**
     * Derives the stored mode from what was actually chosen, so the player never has to think
     * about modes: picking nothing means the slot stops caring about enchantments.
     */
    private void normalise() {
        working.setMode(working.levels().isEmpty()
                ? EnchantRequirement.Mode.IGNORE
                : EnchantRequirement.Mode.SPECIFIC);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    /** off -> any level -> I -> II -> ... -> this enchantment's max -> off */
    private void cycle(EnchantCatalog.Entry entry) {
        Identifier id = entry.id();
        Integer cur = working.levels().get(id);
        if (cur == null) {
            working.levels().put(id, EnchantRequirement.ANY_LEVEL);
        } else if (cur >= entry.maxLevel()) {
            working.levels().remove(id);
        } else {
            working.levels().put(id, cur + 1);
        }
        normalise();
    }

    private String rowLabel(EnchantCatalog.Entry entry) {
        Identifier id = entry.id();
        Integer lvl = working.levels().get(id);
        String state;
        if (lvl == null) {
            state = "—";
        } else if (lvl == EnchantRequirement.ANY_LEVEL) {
            state = "any level";
        } else {
            state = EnchantRequirement.roman(lvl) + "+";
        }
        String max = entry.maxLevel() > 1 ? " (max " + EnchantRequirement.roman(entry.maxLevel()) + ")" : "";
        return EnchantRequirement.prettyName(id) + max + "   [" + state + "]";
    }

    /** Only the enchantments this particular item can actually take. */
    private void refilter(String query) {
        matches.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (EnchantCatalog.Entry entry : EnchantCatalog.forItem(itemId)) {
            String pretty = EnchantRequirement.prettyName(entry.id());
            if (q.isEmpty()
                    || pretty.toLowerCase(Locale.ROOT).contains(q)
                    || entry.id().toString().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(entry);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, "Enchantments for " + itemLabel,
                this.width / 2 - 150, 16, 0xFFFFFFFF, true);

        // Wrapped to the width of the list below and capped at three lines, so a long
        // selection uses the empty space above the search box instead of running off-screen.
        int y = 28;
        for (String line : wrap("Requires: " + working.describe(), 300, 3)) {
            graphics.text(this.font, line, this.width / 2 - 150, y, Theme.current().accent(), false);
            y += 10;
        }

    }

    /**
     * Greedy word wrap using only {@code font.width}, which the rest of the mod already relies on.
     * Anything past {@code maxLines} is dropped with an ellipsis; the rows below show the full
     * state anyway, so the summary is a convenience rather than the source of truth.
     */
    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.length() == 0 ? word : cur + " " + word;
            if (this.font.width(candidate) <= maxWidth || cur.length() == 0) {
                cur.setLength(0);
                cur.append(candidate);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
                if (lines.size() == maxLines) {
                    break;
                }
            }
        }
        if (lines.size() < maxLines && cur.length() > 0) {
            lines.add(cur.toString());
        } else if (lines.size() == maxLines && cur.length() > 0) {
            lines.set(maxLines - 1, lines.get(maxLines - 1) + " ...");
        }
        return lines;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}