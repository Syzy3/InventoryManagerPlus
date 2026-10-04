package dev.inventorymanagerplus.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Searchable list of every registered item.
 *
 * <p>This is what makes Creative presets work as specified: the preset stores item information
 * rather than a reference to a stack the player happens to own, so a layout can be designed for
 * gear that will only be acquired later.
 *
 * <p>The first two squares are always the same, whatever the search: an empty square that backs
 * out without changing anything, and a marked grey glass pane that sets the slot to "keep empty".
 *
 * <p>Search matches both display name and registry id, because "netherite" and
 * "minecraft:netherite_pickaxe" are both things people type. Paging uses Prev/Next buttons rather
 * than a scroll handler, keeping the screen free of mouse-event overrides.
 */
public final class ItemPickerScreen extends Screen {

    private static final int COLS = 12;
    private static final int ROWS = 8;
    private static final int SLOT = 18;
    private static final int PER_PAGE = COLS * ROWS;

    /** One square in the grid: a real item, or one of the two fixed choices at the start. */
    private record Entry(Item item, boolean back, boolean keepEmpty) {
        static Entry of(Item item) {
            return new Entry(item, false, false);
        }
    }

    private final Screen parent;
    private final Consumer<Identifier> onPick;
    /** Called when "keep empty" is chosen; null hides that choice. */
    private final Runnable onPickEmpty;
    /** Optional restriction on what may be chosen; null means anything. */
    private final java.util.function.Predicate<Item> allowed;
    private final List<Entry> entries = new ArrayList<>();

    private EditBox search;
    private int page;

    /**
     * @param onPickEmpty run when the player picks "keep this slot empty"; null hides the choice
     * @param allowed     when supplied, only items passing this test are listed — used by armour
     *                    slots, which cannot hold anything else
     */
    public ItemPickerScreen(Screen parent, Consumer<Identifier> onPick, Runnable onPickEmpty,
                            java.util.function.Predicate<Item> allowed) {
        super(Component.literal("Choose an item"));
        this.parent = parent;
        this.onPick = onPick;
        this.onPickEmpty = onPickEmpty;
        this.allowed = allowed;
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        refilter(query);

        search = new EditBox(this.font, this.width / 2 - 100, 28, 200, 20, Component.literal("Search"));
        search.setValue(query);
        search.setResponder(s -> {
            page = 0;
            rebuild();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        int left = gridLeft();
        int top = gridTop();
        int start = page * PER_PAGE;

        for (int i = 0; i < PER_PAGE; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }
            int x = left + (i % COLS) * SLOT;
            int y = top + (i / COLS) * SLOT;
            Entry entry = entries.get(index);
            addRenderableWidget(ThemedButton.create(Component.empty(), b -> choose(entry))
                    .bounds(x, y, 16, 16).slot(() -> false).build());
        }

        int navY = top + ROWS * SLOT + 6;
        addRenderableWidget(ThemedButton.create(Component.literal("< Prev"), b -> {
            if (page > 0) {
                page--;
                rebuild();
            }
        }).bounds(this.width / 2 - 154, navY, 60, 20).build());

        addRenderableWidget(ThemedButton.create(Component.literal("Next >"), b -> {
            if ((page + 1) * PER_PAGE < entries.size()) {
                page++;
                rebuild();
            }
        }).bounds(this.width / 2 + 94, navY, 60, 20).build());

        addRenderableWidget(ThemedButton.create(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 - 50, navY, 100, 20).build());
    }

    private void choose(Entry entry) {
        if (entry.keepEmpty()) {
            onPickEmpty.run();
        } else if (!entry.back()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(entry.item());
            if (id != null) {
                onPick.accept(id);
            }
        }
        // The back square changes nothing; every choice returns to the editor.
        onClose();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void refilter(String query) {
        entries.clear();
        entries.add(new Entry(null, true, false));
        if (onPickEmpty != null) {
            entries.add(new Entry(null, false, true));
        }

        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Item item : BuiltInRegistries.ITEM) {
            // Air is "no item"; the back square replaces it.
            if (item == Items.AIR) {
                continue;
            }
            if (allowed != null && !allowed.test(item)) {
                continue;
            }
            if (q.isEmpty()) {
                entries.add(Entry.of(item));
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            String idStr = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
            String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
            if (idStr.contains(q) || name.contains(q)) {
                entries.add(Entry.of(item));
            }
        }
    }

    private int itemCount() {
        return (int) entries.stream().filter(e -> e.item() != null).count();
    }

    private int gridLeft() {
        return this.width / 2 - (COLS * SLOT) / 2;
    }

    private int gridTop() {
        return 56;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        graphics.text(this.font,
                "Choose an item — " + itemCount() + " match(es), page " + (page + 1) + " of " + pages,
                this.width / 2 - 90, 14, 0xFFFFFFFF, true);

        int left = gridLeft();
        int top = gridTop();
        int start = page * PER_PAGE;
        Entry hovered = null;

        // Icons drawn after super so they sit on top of the buttons.
        for (int i = 0; i < PER_PAGE; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            int x = left + (i % COLS) * SLOT;
            int y = top + (i / COLS) * SLOT;
            if (entry.keepEmpty()) {
                // The marker is laid out for an 18px slot interior; the button's 16px icon area
                // sits one pixel inside that.
                Render.keepEmptyMarker(graphics, x - 1, y - 1);
            } else if (!entry.back()) {
                Render.item(graphics, new ItemStack(entry.item()), x, y);
            }
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                hovered = entry;
            }
        }

        if (hovered != null && (hovered.back() || hovered.keepEmpty())) {
            List<Component> lines = new ArrayList<>();
            if (hovered.back()) {
                lines.add(Component.literal("Back"));
                lines.add(Component.literal("Leave this slot as it is").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.literal("Keep empty"));
                lines.add(Component.literal("This slot should hold nothing").withStyle(ChatFormatting.DARK_GRAY));
            }
            Render.componentTooltip(graphics, this.font, lines, mouseX, mouseY);
        } else if (hovered != null) {
            // The normal item tooltip, so look-alikes (logs, potions, discs) can be told apart.
            Render.tooltip(graphics, this.font, new ItemStack(hovered.item()), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
