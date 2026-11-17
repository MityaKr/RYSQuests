package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.QuestBook;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;

/**
 * The book's contents on the left (mockup {@code .side}, pictures 13, 14, 14b, 19, 42, 43): "Вся книга" with the count of main
 * quests, epochs that fold, each with its chapters, numbers, a check on a finished chapter, a padlock on a locked one, a dot where the
 * player is, and "Вне карты" below. Folded, it is a rail of epoch numerals; hovering a numeral lists its chapters. The same sidebar
 * serves the map, the list and the editor.
 *
 * <p>The sidebar only reports what was clicked, through its {@link Listener}; the page decides what that means (the map flies to
 * the chapter, the list opens it). One instance per page:
 *
 * <pre>
 * private final Sidebar sidebar = new Sidebar(new Sidebar.Listener() {
 *     public void chapter(ResourceLocation id) { map.flyTo(id); }
 *     public void wholeBook() { map.showAll(); }
 * }, () -> BookState.chapter);
 * ...
 * int w = sidebar.width(compact);
 * sidebar.render(ui, x, y, h, compact);
 * ... draw the page ...
 * sidebar.renderOverlay(ui);          // last: the epoch popover of the rail
 * // in scroll(): if (sidebar.contains(mouseX, mouseY)) sidebar.scroll(mouseX, mouseY, amount);
 * </pre>
 */
final class Sidebar {
    static final int WIDTH = 244;
    static final int RAIL_WIDTH = 48;
    private static final int HEAD_H = 40;
    private static final int ALL_H = 45;
    private static final int EPOCH_H = 32;
    private static final int CHAPTER_H = 28;
    private static final int ROW_GAP = 1;
    private static final int RAIL_BUTTON_W = 34;
    private static final int RAIL_BUTTON_H = 30;
    private static final int POP_W = 232;
    private static final int WHEEL = 40;

    /** What a click in the sidebar asks for. All optional. */
    interface Listener {
        /** "Вся книга": the whole book, the map zoomed all the way out. */
        default void wholeBook() {
        }

        /** An epoch was clicked (its number from 1); it has unfolded already. */
        default void epoch(int number) {
        }

        /** A chapter was clicked, on the map or off it ({@link #isOffMap}). */
        default void chapter(ResourceLocation id) {
        }
    }

    /** A chapter as the sidebar shows it. @param number its number on the map from 1, 0 for a chapter off the map */
    record Entry(Chapter chapter, int number, BookTallies.Tally tally, boolean here) {
    }

    /** An epoch, or an imported section of a galaxy, with its chapters. @param number from 1 */
    record Group(int number, String title, List<Entry> chapters, BookTallies.Tally tally, BookTallies.State state, boolean current) {
    }

    /**
     * Everything the sidebar draws, worked out once per book and progress version.
     *
     * @param numerals whether groups show a roman numeral (epochs) or only a title (sections of a galaxy)
     * @param flat     a book without epochs: one list of chapters, titled "Главы"
     */
    record Model(List<Group> groups, List<Entry> flat, List<Entry> offMap, BookTallies.Tally total, boolean numerals, boolean flat_,
            @Nullable ResourceLocation here) {
        String title() {
            return Component.translatable(flat_ ? "rysquests.sidebar.chapters" : numerals ? "rysquests.sidebar.epochs" : "rysquests.sidebar.sections").getString();
        }
    }

    /** What the player folded or unfolded, kept while the game runs. */
    static final Map<Integer, Boolean> UNFOLDED = new HashMap<>();
    /** The sidebar folded into the rail by the player. */
    static boolean folded;

    private static Model cached;
    private static int cachedVersion = -1;
    @Nullable
    private static QuestBook cachedBook;

    private final Listener listener;
    private final Supplier<ResourceLocation> selected;
    private final Scroll scroll = new Scroll();
    private int[] area = new int[4];
    private int[] list = new int[4];
    private boolean rail;
    private float railX;
    private int hoverEpoch = -1;
    private float hoverY;
    @Nullable
    private int[] popover;

    /** @param selected the chapter shown as chosen, or null when the whole book is */
    Sidebar(Listener listener, Supplier<ResourceLocation> selected) {
        this.listener = listener;
        this.selected = selected;
    }

    // --- the model ---

    static Model model() {
        QuestBook book = BookData.book();
        if (cached == null || cachedBook != book || cachedVersion != ClientProgress.version()) {
            cached = build(book);
            cachedBook = book;
            cachedVersion = ClientProgress.version();
        }
        return cached;
    }

    /** Whether a chapter lives off the map: marked so, or without an epoch in a book that has epochs. */
    static boolean isOffMap(Chapter chapter) {
        if (chapter.offMap()) {
            return true;
        }
        return authorEpochs(BookData.book()) && chapter.epoch().isEmpty();
    }

    /**
     * Whether the author gave the book epochs. Only then is a chapter without an epoch off the map; in a book whose epochs the FTB
     * Quests import made from chapter groups (a galaxy), chapters without a group are a section of their own on the map. The canvas
     * follows the same rule ({@code MapBook}).
     */
    static boolean authorEpochs(QuestBook book) {
        return book.chapters().values().stream().anyMatch(other -> other.epoch().isPresent() && book.meta().authorEpoch(other.epoch()));
    }

    private static Model build(QuestBook book) {
        List<Chapter> chapters = BookData.chapters();
        boolean epochs = chapters.stream().anyMatch(chapter -> chapter.epoch().isPresent());
        boolean authorEpochs = authorEpochs(book);
        ResourceLocation here = ClientProgress.lastChapter().filter(id -> chapters.stream().anyMatch(c -> c.id().equals(id))).orElse(null);
        if (here == null) {
            here = chapters.stream().filter(chapter -> !chapter.offMap() && BookTallies.chapter(chapter.id()).state() != BookTallies.State.DONE)
                    .map(Chapter::id).findFirst().orElse(null);
        }
        Map<Integer, List<Entry>> byEpoch = new LinkedHashMap<>();
        List<Entry> flat = new ArrayList<>();
        List<Entry> off = new ArrayList<>();
        int number = 0;
        for (Chapter chapter : chapters) {
            boolean outside = chapter.offMap() || authorEpochs && chapter.epoch().isEmpty();
            BookTallies.Tally tally = BookTallies.chapter(chapter.id());
            Entry entry = new Entry(chapter, outside ? 0 : ++number, tally, chapter.id().equals(here));
            if (outside) {
                off.add(entry);
            } else if (epochs) {
                // chapters without a section in a galaxy come last as their own section, number -1
                byEpoch.computeIfAbsent(chapter.epoch().orElse(-1), key -> new ArrayList<>()).add(entry);
            } else {
                flat.add(entry);
            }
        }
        BookMeta meta = book.meta();
        boolean numerals = meta.mapForm() != MapForm.GALAXY && !(meta.epochs().size() > 0 && meta.epochs().stream().allMatch(BookMeta.Epoch::imported));
        List<Group> groups = new ArrayList<>();
        byEpoch.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparingInt(key -> key < 0 ? Integer.MAX_VALUE : key))).forEach(group -> {
            List<Entry> entries = group.getValue();
            BookTallies.Tally tally = BookTallies.Tally.EMPTY;
            boolean allDone = true;
            boolean allLocked = true;
            boolean current = false;
            for (Entry entry : entries) {
                tally = tally.plus(entry.tally());
                allDone &= entry.tally().state() == BookTallies.State.DONE;
                allLocked &= entry.tally().state() == BookTallies.State.LOCKED;
                current |= entry.here();
            }
            BookTallies.State state = allDone ? BookTallies.State.DONE : allLocked ? BookTallies.State.LOCKED : BookTallies.State.OPEN;
            String title = group.getKey() < 0 ? Component.translatable("rysquests.canvas.no_section").getString() : meta.epoch(group.getKey()).map(BookMeta.Epoch::title)
                    .map(key -> Component.translatable(key).getString()).orElse(Component.translatable(entries.getFirst().chapter().title()).getString());
            groups.add(new Group(group.getKey(), title, List.copyOf(entries), tally, state, current));
        });
        BookTallies.Tally total = BookTallies.book();
        return new Model(List.copyOf(groups), List.copyOf(flat), List.copyOf(off), total, numerals, !epochs, here);
    }

    /** Unfolded by default: the epoch finished last, the current one and the next; then what the player chose. */
    static boolean unfolded(Model model, Group group) {
        Boolean chosen = UNFOLDED.get(group.number());
        if (chosen != null) {
            return chosen;
        }
        int current = -1;
        for (int i = 0; i < model.groups().size(); i++) {
            if (model.groups().get(i).current()) {
                current = i;
            }
        }
        int index = model.groups().indexOf(group);
        if (current < 0) {
            return index == 0;
        }
        return index >= current - 1 && index <= current + 1;
    }

    // --- layout ---

    /** The width the sidebar takes: the rail when folded or when the page is compact. */
    int width(boolean compact) {
        return compact || folded ? RAIL_WIDTH : WIDTH;
    }

    boolean contains(double x, double y) {
        return x >= area[0] && x < area[0] + area[2] && y >= area[1] && y < area[1] + area[3];
    }

    void scroll(double x, double y, double amount) {
        if (!rail && x >= list[0] && x < list[0] + list[2] && y >= list[1] && y < list[1] + list[3]) {
            scroll.wheel(amount, WHEEL);
        }
    }

    /** Draws the sidebar at (x, y), {@link #width} wide and h high, its right border included. */
    void render(Ui ui, int x, int y, int h, boolean compact) {
        Model model = model();
        rail = compact || folded;
        int w = width(compact);
        area = new int[] {x, y, w, h};
        ui.rect(x, y, w, h, ui.c("sidebar.bg"));
        ui.rect(x + w - 1, y, 1, h, ui.c("panel.border"));
        if (rail) {
            renderRail(ui, model, x, y, w, h, compact);
        } else {
            renderFull(ui, model, x, y, w, h);
        }
    }

    private void renderFull(Ui ui, Model model, int x, int y, int w, int h) {
        float headCy = y + HEAD_H / 2f;
        ui.caps(model.title(), x + 14, headCy, ui.c("text.dim"));
        quietButton(ui, Icons.Shape.SIDEBAR, x + w - 8 - 28, headCy - 14, Component.translatable("rysquests.sidebar.fold"), () -> {
            folded = true;
            hoverEpoch = -1;
        });
        renderWholeBook(ui, model, x + 8, y + HEAD_H, w - 16);
        renderCountMode(ui, x + 8, y + HEAD_H + ALL_H + 4, w - 16);
        float offH = model.offMap().isEmpty() ? 0 : 6 + 22 + model.offMap().size() * (CHAPTER_H + ROW_GAP) - ROW_GAP + 8 + 1;
        int listTop = y + HEAD_H + ALL_H + 4 + COUNT_H + 4;
        int listH = Math.round(y + h - offH - listTop);
        list = new int[] {x, listTop, w - 1, listH};
        ui.clip(x, listTop, w - 1, listH);
        int offset = scroll.offset();
        float pen = listTop - offset;
        ResourceLocation chosen = selected.get();
        if (model.flat_()) {
            for (Entry entry : model.flat()) {
                if (ui.rowVisible(pen, CHAPTER_H)) {
                    renderChapter(ui, entry, x + 8, pen, w - 17, 22, chosen);
                }
                pen += CHAPTER_H + ROW_GAP;
            }
        } else {
            for (Group group : model.groups()) {
                boolean open = unfolded(model, group);
                if (ui.rowVisible(pen, EPOCH_H)) {
                    renderGroup(ui, model, group, open, x + 8, pen, w - 17);
                }
                pen += EPOCH_H + ROW_GAP;
                if (open) {
                    for (Entry entry : group.chapters()) {
                        if (ui.rowVisible(pen, CHAPTER_H)) {
                            renderChapter(ui, entry, x + 8, pen, w - 17, model.numerals() ? 22 : 26, chosen);
                        }
                        pen += CHAPTER_H + ROW_GAP;
                    }
                }
            }
        }
        ui.unclip();
        scroll.content(pen + offset - listTop + 8, listH);
        if (!model.offMap().isEmpty()) {
            float top = y + h - offH;
            ui.rect(x, top, w - 1, 1, ui.c("panel.border"));
            ui.caps(Component.translatable("rysquests.sidebar.off_map").getString(), x + 14, top + 1 + 6 + 11, ui.c("text.dim"));
            float row = top + 1 + 6 + 22;
            for (Entry entry : model.offMap()) {
                renderChapter(ui, entry, x + 8, row, w - 17, 12, chosen);
                row += CHAPTER_H + ROW_GAP;
            }
        }
    }

    private static final int COUNT_H = 24;

    /** "Считать: Все | Основные": side quests in the counts and the checks of finished chapters, or main ones only. */
    private static void renderCountMode(Ui ui, float x, float y, float w) {
        float cy = y + COUNT_H / 2f;
        ui.label(Component.translatable("rysquests.sidebar.count").getString(), x + 10, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        String[] labels = {Component.translatable("rysquests.sidebar.count_all").getString(), Component.translatable("rysquests.sidebar.count_main").getString()};
        boolean all = BookTallies.countAll();
        float[] widths = new float[2];
        float total = 4;
        for (int i = 0; i < 2; i++) {
            widths[i] = ui.width(labels[i], 11.5f, Ui.MEDIUM) + 16;
            total += widths[i];
        }
        float bx = x + w - total;
        ui.box(bx, y, total, COUNT_H, 7, ui.c("sidebar.bg"), ui.c("panel.border"));
        float pen = bx + 2;
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 0) == all;
            boolean over = ui.over(pen, y + 2, widths[i], COUNT_H - 4);
            if (on) {
                ui.fill(pen, y + 2, widths[i], COUNT_H - 4, 5, ui.c("tab.active.bg"));
            } else if (over) {
                ui.fill(pen, y + 2, widths[i], COUNT_H - 4, 5, ui.c("button.bg"));
            }
            ui.label(labels[i], pen + 8, cy, 11.5f, Ui.MEDIUM, ui.c(on || over ? "text.primary" : "text.secondary"));
            boolean value = i == 0;
            ui.hit(pen, y + 2, widths[i], COUNT_H - 4, () -> BookTallies.setCountAll(value));
            pen += widths[i];
        }
    }

    private void renderWholeBook(Ui ui, Model model, float x, float y, float w) {
        boolean on = selected.get() == null;
        boolean over = ui.over(x, y, w, ALL_H);
        if (on || over) {
            ui.fill(x, y, w, ALL_H, 8, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        float cy = y + 8 + 8.5f;
        ui.icon(Icons.Shape.COMPASS, x + 10, cy - 7.5f, 15, ui.c(on || over ? "text.primary" : "text.secondary"));
        ui.label(Component.translatable("rysquests.sidebar.whole_book").getString(), x + 10 + 15 + 8, cy, 12.5f, Ui.SEMIBOLD,
                ui.c(on || over ? "text.primary" : "text.secondary"));
        ui.labelRight(of(model.total().shownDone(), model.total().shownTotal()), x + w - 10, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        ui.bar(x + 10, y + ALL_H - 9 - 4, w - 20, 4, 2, model.total().ratio(), ui.c("state.ok"));
        ui.tooltipIfOver(x, y, w, ALL_H, Component.translatable("rysquests.sidebar.whole_book_tip",
                of(model.total().done(), model.total().total()), of(model.total().optionalDone(), model.total().optionalTotal())));
        ui.hit(x, y, w, ALL_H, listener::wholeBook);
    }

    private void renderGroup(Ui ui, Model model, Group group, boolean open, float x, float y, float w) {
        boolean over = ui.over(x, y, w, EPOCH_H);
        boolean locked = group.state() == BookTallies.State.LOCKED;
        if (over) {
            ui.fill(x, y, w, EPOCH_H, 8, ui.c("button.bg"));
        }
        float cy = y + EPOCH_H / 2f;
        ui.chevron(open, x + 4, cy, 12, ui.c("text.dim"));
        int fg = ui.c(locked ? "text.dim" : group.current() ? "text.primary" : "text.secondary");
        float pen = x + 4 + 12 + 7;
        if (model.numerals()) {
            String numeral = group.number() < 0 ? "" : Ui.roman(group.number());
            ui.labelCentered(numeral, pen + 14, cy, 12, Ui.DISPLAY, ui.c(locked ? "text.dim" : group.current() ? "text.primary" : "text.muted"));
            pen += 28 + 7;
        } else {
            pen += 6 - 7;
        }
        float stateW = renderCount(ui, group.tally(), group.state(), x + w - 8, cy, true);
        ui.labelFit(group.title(), pen, cy, x + w - 8 - stateW - 6 - pen, 12.5f, Ui.SEMIBOLD, fg);
        ui.hit(x, y, 24, EPOCH_H, () -> UNFOLDED.put(group.number(), !open));
        ui.hit(x + 24, y, w - 24, EPOCH_H, () -> {
            UNFOLDED.put(group.number(), true);
            listener.epoch(group.number());
        });
    }

    private void renderChapter(Ui ui, Entry entry, float x, float y, float w, float padLeft, @Nullable ResourceLocation chosen) {
        boolean on = entry.chapter().id().equals(chosen);
        boolean over = ui.over(x, y, w, CHAPTER_H);
        boolean locked = entry.tally().state() == BookTallies.State.LOCKED;
        if (on || over) {
            ui.fill(x, y, w, CHAPTER_H, 7, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        float cy = y + CHAPTER_H / 2f;
        if (entry.here()) {
            ui.circle(x + 10, y + 11, 6, ui.c("text.primary"));
        }
        float pen = x + padLeft;
        if (entry.number() > 0) {
            ui.labelRight(String.format("%02d", entry.number()), pen + 20, cy, 11, Ui.REGULAR, ui.c("text.dim"));
            pen += 20 + 8;
        } else {
            ui.icon(Icons.Shape.FLAG, pen + 4, cy - 6, 12, ui.c("text.dim"));
            pen += 20 + 8;
        }
        float stateW = renderCount(ui, entry.tally(), entry.tally().state(), x + w - 8, cy, false);
        int fg = ui.c(locked ? "text.dim" : on || over ? "text.primary" : "text.secondary");
        ui.labelFit(Component.translatable(entry.chapter().title()).getString(), pen, cy, x + w - 8 - stateW - 6 - pen, 12.5f, Ui.REGULAR, fg);
        ui.hit(x, y, w, CHAPTER_H, () -> listener.chapter(entry.chapter().id()));
    }

    /** The count on the right of a row, or a check when finished, or a padlock when locked. @return its width */
    private static float renderCount(Ui ui, BookTallies.Tally tally, BookTallies.State state, float right, float cy, boolean group) {
        return switch (state) {
            case DONE -> {
                ui.check(right - 12, cy - 6, 12, ui.c("state.ok"));
                yield 12;
            }
            case LOCKED -> {
                ui.lock(right - 8.5f, cy - 5.5f, 11, ui.c("text.dim"));
                yield 9;
            }
            case OPEN -> {
                // a chapter of side quests only ("Летопись инженера") counts them rather than "0 из 0"
                boolean sideOnly = tally.total() == 0 && tally.optionalTotal() > 0;
                String text = sideOnly ? of(tally.optionalDone(), tally.optionalTotal()) : of(tally.shownDone(), tally.shownTotal());
                ui.labelRight(text, right, cy, 11, Ui.REGULAR, ui.c("text.muted"));
                yield ui.width(text, 11, Ui.REGULAR);
            }
        };
    }

    // --- the rail ---

    private void renderRail(Ui ui, Model model, int x, int y, int w, int h, boolean compact) {
        railX = x;
        float cx = x + (w - 1) / 2f;
        quietButton(ui, Icons.Shape.SIDEBAR, cx - 14, y + 8, Component.translatable("rysquests.sidebar.unfold"), compact ? null : () -> folded = false);
        float pen = y + 8 + 28 + 2 + 4;
        boolean whole = selected.get() == null;
        railButton(ui, cx, pen, null, Icons.Shape.COMPASS, whole ? "text.primary" : "text.muted", whole, Component.translatable("rysquests.sidebar.whole_book"),
                listener::wholeBook);
        pen += RAIL_BUTTON_H + 4;
        int hovered = -1;
        float flagY = y + h - 8 - RAIL_BUTTON_H;
        List<Group> groups = model.flat_() ? List.of() : model.groups();
        for (int i = 0; i < groups.size() && pen + RAIL_BUTTON_H <= flagY - 4; i++) {
            Group group = groups.get(i);
            String token = group.state() == BookTallies.State.DONE ? "state.ok" : group.state() == BookTallies.State.LOCKED ? "text.dim"
                    : group.current() ? "text.primary" : "text.muted";
            // chapters without a section (number -1) have no number of their own: an ellipsis; the popover beside the rail names them
            boolean noSection = group.number() < 0;
            String label = noSection ? "…" : model.numerals() ? Ui.roman(group.number()) : String.valueOf(group.number());
            boolean over = ui.over(cx - RAIL_BUTTON_W / 2f, pen, RAIL_BUTTON_W, RAIL_BUTTON_H);
            if (over) {
                hovered = i;
                hoverY = pen;
            }
            int number = group.number();
            railButton(ui, cx, pen, label, null, token, false, null, () -> {
                UNFOLDED.put(number, true);
                listener.epoch(number);
            });
            pen += RAIL_BUTTON_H + 4;
        }
        if (model.flat_()) {
            for (Entry entry : model.flat()) {
                if (pen + RAIL_BUTTON_H > flagY - 4) {
                    break;
                }
                String token = entry.tally().state() == BookTallies.State.DONE ? "state.ok" : entry.tally().state() == BookTallies.State.LOCKED ? "text.dim"
                        : entry.here() ? "text.primary" : "text.muted";
                boolean on = entry.chapter().id().equals(selected.get());
                railButton(ui, cx, pen, String.format("%02d", entry.number()), null, token, on, Component.translatable(entry.chapter().title()),
                        () -> listener.chapter(entry.chapter().id()));
                pen += RAIL_BUTTON_H + 4;
            }
        }
        if (!model.offMap().isEmpty()) {
            String names = String.join(" · ", model.offMap().stream().map(entry -> Component.translatable(entry.chapter().title()).getString()).toList());
            ResourceLocation first = model.offMap().getFirst().chapter().id();
            railButton(ui, cx, flagY, null, Icons.Shape.FLAG, "text.muted", false,
                    Component.translatable("rysquests.sidebar.off_map_tip", names), () -> listener.chapter(first));
        }
        boolean keep = popover != null && ui.over(popover[0], popover[1], popover[2], popover[3]);
        if (hovered >= 0) {
            hoverEpoch = hovered;
        } else if (!keep) {
            hoverEpoch = -1;
        }
    }

    /** The list of an epoch's chapters beside the rail, while its numeral or the list is under the cursor. Call after the page. */
    void renderOverlay(Ui ui) {
        Model model = model();
        if (!rail || hoverEpoch < 0 || hoverEpoch >= model.groups().size() || model.flat_()) {
            popover = null;
            return;
        }
        Group group = model.groups().get(hoverEpoch);
        int rows = group.chapters().size();
        int h = 12 + 20 + 6 + rows * CHAPTER_H + 12;
        int ax = Math.round((railX + RAIL_WIDTH - 1) / 2f);
        int ay = Math.round(hoverY / 2f);
        ResourceLocation chosen = selected.get();
        int[] box = Kit.popover(ui.g, ui.theme, ax, ay, 1, RAIL_BUTTON_H / 2, POP_W / 2, h / 2, Kit.Side.RIGHT, null, (g, bx, by, bw, bh) -> {
            float px = bx * 2f;
            float py = by * 2f;
            float pw = bw * 2f;
            String title = (model.numerals() && group.number() >= 0 ? Ui.roman(group.number()) + " " : "") + group.title();
            float stateW = renderCount(ui, group.tally(), BookTallies.State.OPEN, px + pw - 14, py + 12 + 10, true);
            ui.labelFit(title, px + 14, py + 12 + 10, pw - 28 - stateW - 8, 14, Ui.DISPLAY, ui.c("text.primary"));
            float row = py + 12 + 20 + 6;
            for (Entry entry : group.chapters()) {
                renderChapter(ui, entry, px + 6, row, pw - 12, 22, chosen);
                row += CHAPTER_H;
            }
        });
        popover = new int[] {box[0] * 2, box[1] * 2, box[2] * 2, box[3] * 2};
    }

    private void railButton(Ui ui, float cx, float y, @Nullable String label, @Nullable Icons.Shape icon, String token, boolean on,
            @Nullable Component tooltip, Runnable action) {
        float x = cx - RAIL_BUTTON_W / 2f;
        boolean over = ui.over(x, y, RAIL_BUTTON_W, RAIL_BUTTON_H);
        if (on || over) {
            ui.fill(x, y, RAIL_BUTTON_W, RAIL_BUTTON_H, 8, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        int color = ui.c(over ? "text.primary" : token);
        if (icon != null) {
            float size = icon == Icons.Shape.FLAG ? 14 : 15;
            ui.icon(icon, cx - size / 2f, y + (RAIL_BUTTON_H - size) / 2f, size, color);
        } else if (label != null) {
            ui.labelCentered(label, cx, y + RAIL_BUTTON_H / 2f, 11.5f, Ui.DISPLAY, color);
        }
        if (tooltip != null) {
            ui.tooltipIfOver(x, y, RAIL_BUTTON_W, RAIL_BUTTON_H, tooltip);
        }
        ui.hit(x, y, RAIL_BUTTON_W, RAIL_BUTTON_H, action);
    }

    /** A 28 square icon button without a frame (mockup {@code .ib.s.q}). A null action draws it inactive. */
    private static void quietButton(Ui ui, Icons.Shape icon, float x, float y, Component tooltip, @Nullable Runnable action) {
        boolean over = action != null && ui.over(x, y, 28, 28);
        if (over) {
            ui.fill(x, y, 28, 28, 9, ui.c("button.bg"));
        }
        ui.icon(icon, x + 6.5f, y + 6.5f, 15, action == null ? Draw.alpha(ui.c("text.muted"), 0.45f) : ui.c(over ? "text.primary" : "text.muted"));
        if (action != null) {
            ui.tooltipIfOver(x, y, 28, 28, tooltip);
            ui.hit(x, y, 28, 28, action);
        }
    }

    private static String of(int done, int total) {
        return Component.translatable("rysquests.count.of", full(done), full(total)).getString();
    }

    /** A whole number with a space between thousands: "4 248", as the sidebar and detail panels write it (plan, section 1). */
    static String full(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder(value < 0 ? "-" : "");
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    int scrollForTest() {
        return scroll.position();
    }
}
