package dev.inventorymanagerplus.gui;

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
 * <p>Search matches both display name and registry id, because "netherite" and
 * "minecraft:netherite_pickaxe" are both things people type. Paging uses Prev/Next buttons rather
 * than a scroll handler, keeping the screen free of mouse-event overrides.
 */
public final class ItemPickerScreen extends Screen {

    private static final int COLS = 12;
    private static final int ROWS = 8;
    private static final int SLOT = 18;
    private static final int PER_PAGE = COLS * ROWS;

    private final Screen parent;
    private final Consumer<Identifier> onPick;
    /** Optional restriction on what may be chosen; null means anything. */
    private final java.util.function.Predicate<Item> allowed;
    private final List<Item> matches = new ArrayList<>();

    private EditBox search;
    private int page;

    public ItemPickerScreen(Screen parent, Consumer<Identifier> onPick) {
        this(parent, onPick, null);
    }

    /**
     * @param allowed when supplied, only items passing this test are listed — used by armour
     *                slots, which cannot hold anything else
     */
    public ItemPickerScreen(Screen parent, Consumer<Identifier> onPick,
                            java.util.function.Predicate<Item> allowed) {
        super(Component.literal("Choose an item"));
        this.parent = parent;
        this.onPick = onPick;
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
            if (index >= matches.size()) {
                break;
            }
            int x = left + (i % COLS) * SLOT;
            int y = top + (i / COLS) * SLOT;
            Item item = matches.get(index);
            addRenderableWidget(Button.builder(Component.empty(), b -> {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null) {
                    onPick.accept(id);
                }
                onClose();
            }).bounds(x, y, 16, 16).build());
        }

        int navY = top + ROWS * SLOT + 6;
        addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
            if (page > 0) {
                page--;
                rebuild();
            }
        }).bounds(this.width / 2 - 154, navY, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
            if ((page + 1) * PER_PAGE < matches.size()) {
                page++;
                rebuild();
            }
        }).bounds(this.width / 2 + 94, navY, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 - 50, navY, 100, 20).build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void refilter(String query) {
        matches.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Item item : BuiltInRegistries.ITEM) {
            if (allowed != null && !allowed.test(item)) {
                continue;
            }
            if (q.isEmpty()) {
                matches.add(item);
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            String idStr = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
            String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
            if (idStr.contains(q) || name.contains(q)) {
                matches.add(item);
            }
        }
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

        int pages = Math.max(1, (matches.size() + PER_PAGE - 1) / PER_PAGE);
        graphics.text(this.font,
                "Choose an item — " + matches.size() + " match(es), page " + (page + 1) + " of " + pages,
                this.width / 2 - 90, 14, 0xFFFFFFFF, true);

        int left = gridLeft();
        int top = gridTop();
        int start = page * PER_PAGE;

        // Icons drawn after super so they sit on top of the buttons.
        for (int i = 0; i < PER_PAGE; i++) {
            int index = start + i;
            if (index >= matches.size()) {
                break;
            }
            Render.item(graphics, new ItemStack(matches.get(index)),
                    left + (i % COLS) * SLOT, top + (i / COLS) * SLOT);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}