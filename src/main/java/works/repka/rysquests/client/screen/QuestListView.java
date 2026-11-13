package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.task.AdvancementTask;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.FluidTask;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.KillTask;
import works.repka.rysquests.task.StatTask;
import works.repka.rysquests.task.VisitTask;
import works.repka.rysquests.task.XpTask;
import works.repka.ryscore.ui.Icons;

/**
 * The list mode of the "Map" tab (plan, section 3, "List"; pictures 19, 19b, 19c): the same sidebar, "Карта | Список" above, and the
 * level the sidebar chose. A chapter is a ribbon of its main quests in order, each with the count of its side quests, which unfold
 * under it; a chapter off the map lists its groups as sections; the whole book and an epoch list chapters as headers, the current one
 * open, and a filter over all of them hides chapters it leaves empty.
 *
 * <p>Only the rows in sight are drawn and a folded chapter is one row, so a book of 4 853 quests scrolls as one chapter does: the
 * rows are laid out once per book, progress, filter and unfolding, and each frame draws the slice the scroll shows.
 */
final class QuestListView implements BookView {
    /** What the list shows: the level chosen in the sidebar. */
    enum Level {
        BOOK, EPOCH, CHAPTER
    }

    /** The filter chips; every page says "можно делать" for what can be done. */
    enum Filter {
        ALL, CAN, DONE
    }

    private static final float PAD_X = 16;
    private static final float PAD_Y = 12;
    private static final float ROW_GAP = 6;
    private static final int BOOK_ROWS = 5;
    private static final int KIDS = 4;
    private static final float WHEEL = 48;

    // --- state that outlives the open book ---

    private static Level level = Level.CHAPTER;
    private static int epoch = -1;
    private static Filter filter = Filter.ALL;
    /** Chapters unfolded in the whole book or an epoch; null until the current chapter is unfolded by default. */
    @Nullable
    private static Set<ResourceLocation> openChapters;
    /** Chapters shown whole rather than their first five rows. */
    private static final Set<ResourceLocation> FULL_CHAPTERS = new HashSet<>();
    /** Main quests of a chapter unfolded to their side quests, and those showing all of them. */
    private static final Set<ResourceLocation> OPEN_MAINS = new HashSet<>();
    private static final Set<ResourceLocation> ALL_KIDS = new HashSet<>();
    @Nullable
    private static QuestBook stateBook;

    /** Shows the whole book in the list. */
    static void showBook() {
        level = Level.BOOK;
    }

    static void showEpoch(int number) {
        level = Level.EPOCH;
        epoch = number;
    }

    static void showChapter() {
        level = Level.CHAPTER;
        filter = Filter.ALL;
    }

    /** "Всё, что можно делать · N" on "Now": the list of the whole book with the filter on. */
    static void openWholeBook(Filter chosen) {
        BookState.section = Chapter.STORY;
        BookState.view = ViewMode.LIST;
        level = Level.BOOK;
        filter = chosen;
    }

    static Level level() {
        return level;
    }

    /** The chapter the sidebar shows as chosen: none on the level of the book or an epoch. */
    @Nullable
    static ResourceLocation chosenChapter() {
        return level == Level.CHAPTER ? BookState.chapter : null;
    }

    // --- the view ---

    private final BookScreen screen;
    private final Scroll scroll = new Scroll();
    private final QuestPanel panel;
    private boolean overlay;
    private float listLeft;
    private float listTop;
    private float listRight;
    private float listBottom;
    private float panelLeft = Float.MAX_VALUE;

    private List<Row> rows = List.of();
    private float[] tops = new float[0];
    private float total;
    private int[] counts = new int[3];
    private int hidden;
    @Nullable
    private Object builtKey;
    @Nullable
    private Object shownScope;

    QuestListView(BookScreen screen) {
        this.screen = screen;
        this.panel = new QuestPanel(new QuestPanel.Events() {
            @Override
            public void select(Quest quest) {
                QuestListView.this.select(quest);
            }

            @Override
            public void close() {
                BookState.quest = null;
                overlay = false;
            }
        });
    }

    private void select(Quest quest) {
        BookState.select(quest);
        if (level == Level.CHAPTER && quest.chapter().isPresent() && !quest.chapter().get().equals(BookState.chapter)) {
            BookState.chapter = quest.chapter().get();
        }
        overlay = true;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        QuestBook book = BookData.book();
        if (stateBook != book) {
            stateBook = book;
            openChapters = null;
            FULL_CHAPTERS.clear();
            OPEN_MAINS.clear();
            ALL_KIDS.clear();
        }
        Chapter chapter = level == Level.CHAPTER && BookState.chapter != null ? book.chapters().get(BookState.chapter) : null;
        if (level == Level.CHAPTER && chapter == null) {
            level = Level.BOOK;
        }
        if (level == Level.EPOCH && Sidebar.model().groups().stream().noneMatch(group -> group.number() == epoch)) {
            level = Level.BOOK;
        }
        Object scope = List.of(level, epoch, filter, Objects.toString(chapter == null ? null : chapter.id()));
        if (!scope.equals(shownScope)) {
            shownScope = scope;
            scroll.reset();
        }
        boolean compact = screen.compact();
        Quest shown = shownQuest();
        boolean side = !compact && shown != null;
        float panelW = side ? DetailPanel.WIDTH : 0;
        float mainW = w - panelW;
        ui.rect(x, y, mainW, h, ui.c("panel.bg"));
        float left = x + PAD_X;
        float inner = mainW - PAD_X * 2;
        float pen = y + PAD_Y;
        // "Карта | Список" and the crumbs.
        boolean offMap = chapter != null && Sidebar.isOffMap(chapter);
        float segW = MapTab.modeSwitch(ui, left, pen, true, offMap);
        crumbs(ui, chapter, left + segW + 8, pen + 2, inner - segW - 8);
        pen += Segments.HEIGHT + 10;
        pen = title(ui, chapter, left, pen, inner) + 10;
        ensureRows(ui, chapter, inner);
        pen = chips(ui, left, pen) + 10;
        listLeft = left;
        listRight = left + inner;
        listTop = pen;
        listBottom = y + h - PAD_Y;
        renderRows(ui, left, pen, inner, listBottom - pen, chapter, shown);
        panelLeft = Float.MAX_VALUE;
        if (side) {
            panelLeft = x + mainW;
            panel.render(ui, panelLeft, y, panelW, h, shown, QuestPanel.Look.LIST);
        } else if (compact && overlay && shown != null) {
            panelLeft = x + w - DetailPanel.WIDTH;
            ui.shadow(panelLeft, y, DetailPanel.WIDTH, h, 0, 0.6f);
            panel.render(ui, panelLeft, y, DetailPanel.WIDTH, h, shown, QuestPanel.Look.OVER);
        } else {
            panel.clear();
        }
    }

    /** The quest in the panel: the one chosen, else the next step of the book. */
    @Nullable
    private static Quest shownQuest() {
        Quest chosen = BookState.quest == null ? null : BookData.book().quests().get(BookState.quest);
        if (chosen != null && BookData.shown(chosen)) {
            return chosen;
        }
        return NowView.nextStep().map(works.repka.rysquests.progress.NextStep.Result::quest).orElse(null);
    }

    // --- the head of the page ---

    private void crumbs(Ui ui, @Nullable Chapter chapter, float x, float y, float room) {
        if (level == Level.BOOK) {
            return;
        }
        List<String> parts = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        parts.add(t("rysquests.qlist.whole"));
        actions.add(QuestListView::showBook);
        Sidebar.Group group = level == Level.EPOCH ? groupOf(epoch) : chapter == null ? null : groupOf(chapter);
        if (group != null) {
            int number = group.number();
            parts.add(groupLabel(group));
            actions.add(() -> showEpoch(number));
        }
        if (chapter != null) {
            parts.add(QuestFacts.plain(Component.translatable(chapter.title()).getString()));
            actions.add(null);
        }
        float h = 30;
        float w = 22;
        for (int i = 0; i < parts.size(); i++) {
            w += ui.width(parts.get(i), 12, i == parts.size() - 1 ? Ui.SEMIBOLD : Ui.MEDIUM) + (i > 0 ? 6 + 11 + 6 : 0);
        }
        w = Math.min(w, room);
        ui.box(x, y, w, h, 9, ui.c("button.bg"), ui.c("button.border"));
        float pen = x + 11;
        float cy = y + h / 2f;
        for (int i = 0; i < parts.size(); i++) {
            boolean last = i == parts.size() - 1;
            if (i > 0) {
                ui.icon(Icons.Shape.CHEVRON_RIGHT, pen + 6, cy - 5.5f, 11, ui.c("text.muted"));
                pen += 6 + 11 + 6;
            }
            String part = parts.get(i);
            float pw = Math.min(ui.width(part, 12, last ? Ui.SEMIBOLD : Ui.MEDIUM), x + w - 11 - pen);
            Runnable action = actions.get(i);
            boolean over = action != null && ui.over(pen, y, pw, h);
            ui.labelFit(part, pen, cy, pw, 12, last ? Ui.SEMIBOLD : Ui.MEDIUM, ui.c(last || over ? "text.primary" : "text.secondary"));
            if (action != null) {
                ui.hit(pen, y, pw, h, action);
            }
            pen += pw;
        }
    }

    private float title(Ui ui, @Nullable Chapter chapter, float x, float y, float w) {
        ItemStack icon;
        String title;
        String sub;
        if (chapter != null) {
            icon = chapter.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
            Sidebar.Entry entry = entryOf(chapter);
            String name = QuestFacts.plain(Component.translatable(chapter.title()).getString());
            title = entry != null && entry.number() > 0 ? String.format("%02d · %s", entry.number(), name) : name;
            sub = chapter.subtitle().map(s -> Component.translatable(s).getString()).orElse(null);
        } else if (level == Level.EPOCH) {
            Sidebar.Group group = groupOf(epoch);
            icon = group == null || group.chapters().isEmpty() ? ItemStack.EMPTY
                    : group.chapters().getFirst().chapter().icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
            title = group == null ? "" : groupLabel(group);
            int quests = group == null ? 0 : group.chapters().stream().mapToInt(entry -> questsOf(entry.chapter().id()).size()).sum();
            sub = t("rysquests.qlist.book_sub", Ui.counted("rysquests.qlist.plural.chapter", group == null ? 0 : group.chapters().size()),
                    Ui.counted("rysquests.plural.quest", quests));
        } else {
            icon = BookData.book().meta().icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
            title = t("rysquests.qlist.whole");
            int chapters = BookData.chapters().size();
            int quests = 0;
            for (Chapter each : BookData.chapters()) {
                quests += questsOf(each.id()).size();
            }
            sub = t("rysquests.qlist.book_sub", Ui.counted("rysquests.qlist.plural.chapter", chapters), Ui.counted("rysquests.plural.quest", quests));
        }
        float size = 40;
        ui.box(x, y, size, size, 6, ui.c("slot.bg"), ui.c("slot.border"));
        if (!icon.isEmpty()) {
            ui.item(icon, x + 8, y + 8, 24);
        } else {
            ui.icon(chapter == null ? Icons.Shape.COMPASS : Icons.Shape.FLAG, x + 11, y + 11, 18, ui.c("text.secondary"));
        }
        float textX = x + size + 12;
        float textW = w - size - 12;
        float titleH = Ui.headingHeight(19) - 3;
        float subH = sub == null ? 0 : 3 + Ui.lineHeight(12);
        float top = y + (size - titleH - subH) / 2f;
        ui.labelFit(title, textX, top + titleH / 2f, textW, 19, Ui.DISPLAY, ui.c("text.primary"));
        if (sub != null) {
            ui.labelFit(sub, textX, top + titleH + 3 + Ui.lineHeight(12) / 2f, textW, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        return y + size;
    }

    /** "Все · 43", "Можно делать · 7", "Выполненные · 10" (mockup {@code .chips}). @return the y below */
    private float chips(Ui ui, float x, float y) {
        float h = 28;
        float pen = x;
        Filter[] all = Filter.values();
        for (int i = 0; i < all.length; i++) {
            Filter each = all[i];
            String label = t("rysquests.qlist.filter." + each.name().toLowerCase(java.util.Locale.ROOT)) + " · " + Sidebar.full(counts[i]);
            boolean on = each == filter;
            float cw = ui.width(label, 12.5f, Ui.REGULAR) + 22;
            boolean over = ui.over(pen, y, cw, h);
            if (on || over) {
                ui.fill(pen, y, cw, h, 6, ui.c(on ? "tab.active.bg" : "button.bg"));
            }
            ui.label(label, pen + 11, y + h / 2f, 12.5f, Ui.REGULAR, ui.c(on ? "text.primary" : "text.muted"));
            ui.hit(pen, y, cw, h, () -> {
                filter = each;
                scroll.reset();
            });
            pen += cw + 4;
        }
        return y + h;
    }

    // --- the rows ---

    /** One line of the list, laid out once. */
    private sealed interface Row permits Head, QuestLine, More, Rubric, Note {
        float height();
    }

    /** A chapter as a header in the whole book or an epoch (mockup {@code .lhead}). */
    private record Head(Chapter chapter, int number, String epochLabel, String count, boolean open) implements Row {
        @Override
        public float height() {
            return 34;
        }
    }

    /**
     * A quest. {@code look} is CARD in a chapter, CHILD under its main quest, TIGHT inside a chapter header.
     *
     * @param kids the side quests under a main quest, -1 for a row that has none to fold
     */
    private record QuestLine(Quest quest, ListRow.Look look, float indent, String sub, @Nullable String right, String rightToken, boolean lock,
            boolean locked, int kids, boolean open, float height) implements Row {
    }

    /** A dashed row that unfolds more: "ещё 1 побочный", "ещё 2". */
    private record More(String text, float indent, Runnable action) implements Row {
        @Override
        public float height() {
            return 30;
        }
    }

    /** A group of a chapter off the map as a section heading: "ПРИКЛЮЧЕНИЯ" and "3 из 20". */
    private record Rubric(String left, String right, boolean first) implements Row {
        @Override
        public float height() {
            return Ui.lineHeight(11.5f) + (first ? 0 : 8);
        }
    }

    /** A quiet line under the list: "Скрыто глав: 25. ..." */
    private record Note(String text) implements Row {
        @Override
        public float height() {
            return Ui.lineHeight(11.5f) + 8;
        }
    }

    private void ensureRows(Ui ui, @Nullable Chapter chapter, float w) {
        Object key = List.of(BookData.book(), ClientProgress.version(), filter, level, epoch, Objects.toString(chapter == null ? null : chapter.id()),
                Objects.toString(openChapters), FULL_CHAPTERS.size(), FULL_CHAPTERS.hashCode(), OPEN_MAINS.hashCode(), ALL_KIDS.hashCode(), w);
        if (key.equals(builtKey)) {
            return;
        }
        builtKey = key;
        List<Row> out = new ArrayList<>();
        hidden = 0;
        counts = new int[3];
        if (chapter != null) {
            buildChapter(chapter, out);
        } else {
            buildChapters(out);
        }
        rows = out;
        tops = new float[rows.size()];
        float pen = 0;
        for (int i = 0; i < rows.size(); i++) {
            tops[i] = pen;
            pen += rows.get(i).height() + ROW_GAP;
        }
        total = Math.max(0, pen - ROW_GAP);
    }

    /** The quests a list shows: what the book shows of a chapter, in chapter order, without drafts and archived ones. */
    private static List<Quest> questsOf(ResourceLocation chapter) {
        return BookTallies.questsOf(chapter).stream().filter(QuestLogic::isLive).toList();
    }

    private static boolean matches(Quest quest, Filter chosen) {
        return switch (chosen) {
            case ALL -> true;
            case CAN -> Epochs.isOpen(quest);
            case DONE -> ClientProgress.get(quest.id()).everDone();
        };
    }

    private void count(List<Quest> quests) {
        for (Quest quest : quests) {
            counts[0]++;
            if (Epochs.isOpen(quest)) {
                counts[1]++;
            }
            if (ClientProgress.get(quest.id()).everDone()) {
                counts[2]++;
            }
        }
    }

    /** A chapter: main quests in order with their side quests under them, or the groups of a chapter off the map (19, 19b). */
    private void buildChapter(Chapter chapter, List<Row> out) {
        List<Quest> quests = questsOf(chapter.id());
        count(quests);
        if (Sidebar.isOffMap(chapter)) {
            buildGroups(chapter, quests, out);
            return;
        }
        List<Quest> mains = quests.stream().filter(QuestFacts::isMain).toList();
        Map<ResourceLocation, List<Quest>> kids = anchors(quests, mains);
        if (filter != Filter.ALL) {
            for (Quest quest : quests) {
                if (matches(quest, filter)) {
                    int step = mains.indexOf(quest) + 1;
                    out.add(line(quest, ListRow.Look.CARD, 0, step > 0 ? stepSub(quest, step, List.of()) : kindWord(quest), -1, false, true));
                }
            }
            return;
        }
        for (int i = 0; i < mains.size(); i++) {
            Quest main = mains.get(i);
            List<Quest> under = kids.getOrDefault(main.id(), List.of());
            boolean open = OPEN_MAINS.contains(main.id()) && !under.isEmpty();
            out.add(line(main, ListRow.Look.CARD, 0, stepSub(main, i + 1, under), under.isEmpty() ? -1 : under.size(), open, true));
            if (open) {
                boolean all = ALL_KIDS.contains(main.id()) || under.size() <= KIDS + 1;
                List<Quest> shownKids = all ? under : under.subList(0, KIDS);
                for (Quest kid : shownKids) {
                    out.add(line(kid, ListRow.Look.CHILD, 0, null, -1, false, true));
                }
                if (!all) {
                    int more = under.size() - KIDS;
                    ResourceLocation id = main.id();
                    out.add(new More(t("rysquests.qlist.more", Ui.counted("rysquests.qlist.plural.side", more)), ListRow.CHILD_INDENT, () -> ALL_KIDS.add(id)));
                }
            }
        }
        // Side quests no main quest leads to stand after the ribbon on their own.
        Set<Quest> placed = new HashSet<>(mains);
        kids.values().forEach(placed::addAll);
        for (Quest quest : quests) {
            if (!placed.contains(quest)) {
                out.add(line(quest, ListRow.Look.CARD, 0, kindWord(quest), -1, false, true));
            }
        }
    }

    /** "шаг 6 · побочных 4 из 5", "веха · шаг 1". */
    private static String stepSub(Quest quest, int step, List<Quest> kids) {
        List<String> parts = new ArrayList<>();
        if (quest.options().milestone()) {
            parts.add(t("rysquests.qlist.sub_milestone"));
        }
        parts.add(t("rysquests.qlist.sub_step", step));
        if (!kids.isEmpty()) {
            long done = kids.stream().filter(kid -> ClientProgress.get(kid.id()).everDone()).count();
            parts.add(t("rysquests.qlist.sub_sides", done, kids.size()));
        }
        return String.join(" · ", parts);
    }

    private static String kindWord(Quest quest) {
        return t(quest.options().optional() ? "rysquests.qlist.side" : quest.options().milestone() ? "rysquests.qlist.milestone" : "rysquests.qlist.main");
    }

    /**
     * Each side quest under its main quest: the nearest main quest it needs, walking its requirements; else the first main quest
     * that needs it. The mockups' rule (19): side quests hang on the ribbon where they branch off.
     */
    private static Map<ResourceLocation, List<Quest>> anchors(List<Quest> quests, List<Quest> mains) {
        Map<ResourceLocation, Quest> byId = new HashMap<>();
        quests.forEach(quest -> byId.put(quest.id(), quest));
        Set<ResourceLocation> mainIds = new HashSet<>();
        mains.forEach(main -> mainIds.add(main.id()));
        Map<ResourceLocation, List<Quest>> out = new LinkedHashMap<>();
        for (Quest quest : quests) {
            if (mainIds.contains(quest.id())) {
                continue;
            }
            ResourceLocation anchor = null;
            java.util.ArrayDeque<ResourceLocation> queue = new java.util.ArrayDeque<>(quest.requires());
            Set<ResourceLocation> seen = new HashSet<>();
            while (!queue.isEmpty() && anchor == null) {
                ResourceLocation id = queue.poll();
                if (!seen.add(id) || !byId.containsKey(id)) {
                    continue;
                }
                if (mainIds.contains(id)) {
                    anchor = id;
                } else {
                    queue.addAll(byId.get(id).requires());
                }
            }
            if (anchor == null) {
                for (Quest main : mains) {
                    if (main.requires().contains(quest.id())) {
                        anchor = main.id();
                        break;
                    }
                }
            }
            if (anchor != null) {
                out.computeIfAbsent(anchor, id -> new ArrayList<>()).add(quest);
            }
        }
        return out;
    }

    /** A chapter off the map: its groups as sections of the list, the action of each quest under its name (19b). */
    private void buildGroups(Chapter chapter, List<Quest> quests, List<Row> out) {
        Map<String, List<Quest>> groups = new LinkedHashMap<>();
        chapter.groups().forEach(group -> groups.put(group.id(), new ArrayList<>()));
        List<Quest> rest = new ArrayList<>();
        for (Quest quest : quests) {
            List<Quest> group = quest.group().map(groups::get).orElse(null);
            (group != null ? group : rest).add(quest);
        }
        boolean first = true;
        for (Chapter.Group group : chapter.groups()) {
            first = section(QuestFacts.plain(Component.translatable(group.title()).getString()), groups.get(group.id()), first, out) && first;
        }
        if (!rest.isEmpty()) {
            if (chapter.groups().isEmpty()) {
                for (Quest quest : rest) {
                    if (matches(quest, filter)) {
                        out.add(line(quest, ListRow.Look.CARD, 0, verb(quest), -1, false, true));
                    }
                }
            } else {
                section(t("rysquests.qlist.other"), rest, first, out);
            }
        }
    }

    /** @return true when nothing was added, so the next section is still the first */
    private boolean section(String title, List<Quest> quests, boolean first, List<Row> out) {
        List<Quest> shown = quests.stream().filter(quest -> matches(quest, filter)).toList();
        if (shown.isEmpty()) {
            return true;
        }
        long done = quests.stream().filter(quest -> ClientProgress.get(quest.id()).everDone()).count();
        out.add(new Rubric(title, t("rysquests.count.of", done, quests.size()), first));
        for (Quest quest : shown) {
            out.add(line(quest, ListRow.Look.CARD, 0, verb(quest), -1, false, true));
        }
        return false;
    }

    /** The whole book or an epoch: chapters as headers, the open ones with their rows (19c). */
    private void buildChapters(List<Row> out) {
        Sidebar.Model model = Sidebar.model();
        List<Sidebar.Entry> entries = new ArrayList<>();
        if (level == Level.EPOCH) {
            Sidebar.Group group = groupOf(epoch);
            if (group != null) {
                entries.addAll(group.chapters());
            }
        } else {
            model.groups().forEach(group -> entries.addAll(group.chapters()));
            entries.addAll(model.flat());
            entries.addAll(model.offMap());
        }
        if (openChapters == null) {
            openChapters = new HashSet<>();
            if (model.here() != null) {
                openChapters.add(model.here());
            }
        }
        for (Sidebar.Entry entry : entries) {
            Chapter chapter = entry.chapter();
            List<Quest> quests = questsOf(chapter.id());
            count(quests);
            List<Quest> shown = quests.stream().filter(quest -> matches(quest, filter)).toList();
            if (filter != Filter.ALL && shown.isEmpty()) {
                hidden++;
                continue;
            }
            boolean open = openChapters.contains(chapter.id());
            String count = filter == Filter.ALL ? t("rysquests.count.of", Sidebar.full(entry.tally().done()), Sidebar.full(entry.tally().total()))
                    : Sidebar.full(shown.size());
            Sidebar.Group group = groupOf(chapter);
            String epochLabel = entry.number() == 0 ? t("rysquests.qlist.off_map") : group == null || level == Level.EPOCH ? "" : groupLabel(group);
            out.add(new Head(chapter, entry.number(), epochLabel, count, open));
            if (!open) {
                continue;
            }
            List<Quest> ordered = new ArrayList<>(shown);
            if (filter == Filter.CAN) {
                ordered.sort(Comparator.comparing((Quest quest) -> quest.options().optional()));
            }
            boolean full = FULL_CHAPTERS.contains(chapter.id()) || ordered.size() <= BOOK_ROWS + 1;
            List<Quest> rowsOf = full ? ordered : ordered.subList(0, BOOK_ROWS);
            for (Quest quest : rowsOf) {
                out.add(line(quest, ListRow.Look.TIGHT, 22, kindWord(quest), -1, false, filter != Filter.CAN));
            }
            if (!full) {
                ResourceLocation id = chapter.id();
                out.add(new More(t("rysquests.qlist.more", ordered.size() - BOOK_ROWS), 22, () -> FULL_CHAPTERS.add(id)));
            }
        }
        if (hidden > 0) {
            out.add(new Note(t(filter == Filter.DONE ? "rysquests.qlist.hidden_done" : "rysquests.qlist.hidden", hidden)));
        }
    }

    /** A quest row with its state on the right: "выполнен", "награда ждёт", "можно делать", "закрыт". */
    private static QuestLine line(Quest quest, ListRow.Look look, float indent, @Nullable String sub, int kids, boolean open, boolean withState) {
        QuestStatus status = BookState.status(quest);
        String right = null;
        String token = "text.muted";
        boolean lock = false;
        if (withState) {
            switch (status) {
                case CLAIMED -> {
                    right = look == ListRow.Look.CHILD ? "" : t("rysquests.qlist.state_done");
                    token = "state.ok";
                }
                case COMPLETED -> {
                    right = t("rysquests.qlist.state_wait");
                    token = "text.primary";
                }
                case AVAILABLE, IN_PROGRESS -> {
                    right = t("rysquests.qlist.state_can");
                    token = look == ListRow.Look.CHILD ? "text.secondary" : "text.primary";
                }
                case LOCKED -> {
                    right = look == ListRow.Look.CHILD ? "" : t("rysquests.qlist.state_locked");
                    lock = true;
                }
            }
        }
        float height = switch (look) {
            case CHILD -> 38;
            case TIGHT -> sub == null ? 34 : Math.max(34, 4 + Math.max(30, Ui.lineHeight(13) + 1 + Ui.lineHeight(11.5f)));
            default -> Math.max(48, 14 + Math.max(32, Ui.lineHeight(13) + (sub == null ? 0 : 1 + Ui.lineHeight(11.5f))));
        };
        return new QuestLine(quest, look, indent, sub == null ? "" : sub, right, token, lock, status == QuestStatus.LOCKED, kids, open, height);
    }

    /** The action of a quest in one word, for a chapter off the map: "побывать", "победить", "получить". */
    private static String verb(Quest quest) {
        if (quest.tasks().isEmpty()) {
            return kindWord(quest);
        }
        TaskDef task = quest.tasks().getFirst();
        String key = switch (task) {
            case KillTask kill -> "kill";
            case VisitTask visit -> "visit";
            case ItemTask item -> item.mode() == ItemTask.Mode.CONSUME ? "give" : item.mode() == ItemTask.Mode.CRAFT ? "craft" : "get";
            case FluidTask fluid -> fluid.mode() == ItemTask.Mode.CONSUME ? "give" : "get";
            case CheckboxTask check -> "check";
            case XpTask xp -> "xp";
            case StatTask stat -> "stat";
            case AdvancementTask advancement -> "advancement";
            default -> "other";
        };
        return t("rysquests.qlist.verb." + key);
    }

    private void renderRows(Ui ui, float x, float y, float w, float h, @Nullable Chapter chapter, @Nullable Quest selected) {
        scroll.content(total, h);
        int offset = scroll.offset();
        ui.clip(Math.round(x - 2), Math.round(y), Math.round(w + 4), Math.round(h));
        if (rows.isEmpty()) {
            emptyFilter(ui, chapter, x, y, w, h);
        }
        int first = firstVisible(offset);
        for (int i = first; i < rows.size(); i++) {
            float top = y + tops[i] - offset;
            if (top > y + h) {
                break;
            }
            drawRow(ui, rows.get(i), x, top, w, selected);
        }
        ui.unclip();
    }

    /** The first row reaching into sight: a binary search over the laid out tops. */
    private int firstVisible(float offset) {
        int low = 0;
        int high = rows.size() - 1;
        int found = rows.size();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (tops[mid] + rows.get(mid).height() >= offset) {
                found = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return found;
    }

    private void emptyFilter(Ui ui, @Nullable Chapter chapter, float x, float y, float w, float h) {
        String line = t(filter == Filter.DONE ? "rysquests.qlist.empty_done" : filter == Filter.CAN ? "rysquests.qlist.empty_can" : "rysquests.qlist.empty");
        EmptyFilter.render(ui, x, y, w, h, line, filter == Filter.ALL ? null : t("rysquests.qlist.show_all"), () -> filter = Filter.ALL);
    }

    private void drawRow(Ui ui, Row row, float x, float y, float w, @Nullable Quest selected) {
        switch (row) {
            case Head head -> drawHead(ui, head, x, y, w);
            case QuestLine line -> drawQuest(ui, line, x, y, w, selected);
            case More more -> {
                float mx = x + more.indent();
                float mw = w - more.indent();
                boolean over = ui.over(mx, y, mw, more.height());
                ui.dashed(mx, y, mw, more.height(), ui.c(over ? "button.border" : "panel.border"));
                ui.label(more.text(), mx + 10, y + more.height() / 2f, 12, Ui.REGULAR, ui.c(over ? "text.primary" : "text.muted"));
                ui.hit(mx, y, mw, more.height(), more.action());
            }
            case Rubric rubric -> {
                float top = y + (rubric.first() ? 0 : 8);
                DetailPanel.rubric(ui, x, top, w, rubric.left(), rubric.right());
            }
            case Note note -> ui.labelFit(note.text(), x + 8, y + 4 + Ui.lineHeight(11.5f) / 2f, w - 16, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
    }

    private void drawHead(Ui ui, Head head, float x, float y, float w) {
        float h = head.height();
        boolean over = ui.over(x, y, w, h);
        ui.box(x, y, w, h, 9, head.open() ? ui.c("sidebar.bg") : over ? ui.c("button.bg") : 0, ui.c("panel.border"));
        float cy = y + h / 2f;
        float pen = x + 8;
        ui.chevron(head.open(), pen, cy, 13, ui.c("text.dim"));
        pen += 13 + 8;
        if (head.number() > 0) {
            ui.labelRight(String.format("%02d", head.number()), pen + 18, cy, 11, Ui.REGULAR, ui.c("text.dim"));
        } else {
            ui.icon(Icons.Shape.FLAG, pen + 6, cy - 6, 12, ui.c("text.dim"));
        }
        pen += 18 + 8;
        String countText = head.count();
        float countW = ui.width(countText, 11.5f, Ui.REGULAR);
        ui.labelRight(countText, x + w - 12, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        String name = Component.translatable(head.chapter().title()).getString();
        float room = x + w - 12 - countW - 12 - pen;
        float nameW = Math.min(ui.width(name, 12.5f, Ui.SEMIBOLD), room);
        ui.labelFit(name, pen, cy, nameW, 12.5f, Ui.SEMIBOLD, ui.c(head.open() ? "text.primary" : "text.secondary"));
        if (!head.epochLabel().isEmpty() && room - nameW > 30) {
            ui.labelFit(head.epochLabel(), pen + nameW + 8, cy, room - nameW - 8, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        ResourceLocation id = head.chapter().id();
        ui.hit(x, y, w, h, () -> {
            if (openChapters != null && !openChapters.remove(id)) {
                openChapters.add(id);
            }
        });
    }

    private void drawQuest(Ui ui, QuestLine line, float x, float y, float w, @Nullable Quest selected) {
        Quest quest = line.quest();
        boolean known = BookData.shown(quest);
        ListRow row = ListRow.of(known ? QuestFacts.plain(Component.translatable(quest.title()).getString()) : "???")
                .icon(QuestFacts.icon(quest)).look(line.look()).selected(selected != null && selected.id().equals(quest.id()))
                .locked(line.locked()).onClick(() -> {
                    select(quest);
                    if (line.kids() > 0 && !line.open()) {
                        OPEN_MAINS.add(quest.id());
                    }
                }).onRightClick(() -> BookData.togglePin(quest.id()));
        row.iconSize(switch (line.look()) {
            case CHILD -> 26;
            case TIGHT -> 30;
            default -> 32;
        });
        if (!line.sub().isEmpty()) {
            row.sub(line.sub());
        }
        if (line.right() != null) {
            String words = line.right().isEmpty() ? null : line.right();
            if (line.lock()) {
                row.rightLock(true).right(null, words, "text.muted");
            } else if (line.rightToken().equals("state.ok")) {
                row.right(Icons.Shape.CHECK, words, "state.ok");
            } else {
                row.right(null, words, line.rightToken());
            }
        }
        if (line.look() == ListRow.Look.CARD) {
            ResourceLocation id = quest.id();
            boolean open = line.open();
            int kids = line.kids();
            row.lead(13, (u, lx, cy) -> {
                if (kids > 0) {
                    u.chevron(open, lx, cy, 13, u.c("text.dim"));
                    u.hit(lx - 6, cy - 12, 25, 24, () -> {
                        if (!OPEN_MAINS.remove(id)) {
                            OPEN_MAINS.add(id);
                        }
                    });
                }
            });
        }
        float rx = x + line.indent();
        float rw = w - line.indent();
        if (ui.rowVisible(y, line.height())) {
            row.render(ui, rx, y, rw);
        }
    }

    // --- sidebar lookups ---

    @Nullable
    private static Sidebar.Group groupOf(int number) {
        return Sidebar.model().groups().stream().filter(group -> group.number() == number).findFirst().orElse(null);
    }

    @Nullable
    private static Sidebar.Group groupOf(Chapter chapter) {
        for (Sidebar.Group group : Sidebar.model().groups()) {
            for (Sidebar.Entry entry : group.chapters()) {
                if (entry.chapter().id().equals(chapter.id())) {
                    return group;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Sidebar.Entry entryOf(Chapter chapter) {
        Sidebar.Model model = Sidebar.model();
        List<Sidebar.Entry> all = new ArrayList<>(model.flat());
        all.addAll(model.offMap());
        model.groups().forEach(group -> all.addAll(group.chapters()));
        return all.stream().filter(entry -> entry.chapter().id().equals(chapter.id())).findFirst().orElse(null);
    }

    /** "II Индустрия", or the title alone for a section of a galaxy. */
    static String groupLabel(Sidebar.Group group) {
        return Sidebar.model().numerals() ? Ui.roman(group.number()) + " " + group.title() : group.title();
    }

    // --- input ---

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (mouseX >= panelLeft) {
            panel.scroll(amount);
        } else {
            scroll.wheel(amount, WHEEL);
        }
    }

    @Override
    public boolean key(int key) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && overlay) {
            overlay = false;
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        int right = panelLeft == Float.MAX_VALUE ? x + w : Math.round(panelLeft);
        return new int[] {x, y, right - x, h};
    }

    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // --- for tests ---

    int rowsForTest() {
        return rows.size();
    }

    float contentForTest() {
        return total;
    }

    static void filterForTest(Filter chosen) {
        filter = chosen;
    }

    static void openAllForTest() {
        stateBook = BookData.book();
        if (openChapters == null) {
            openChapters = new HashSet<>();
        }
        BookData.chapters().forEach(chapter -> {
            openChapters.add(chapter.id());
            FULL_CHAPTERS.add(chapter.id());
        });
    }

    static void expandForTest(ResourceLocation main) {
        stateBook = BookData.book();
        OPEN_MAINS.add(main);
    }

    void scrollForTest(double notches) {
        scroll.wheel(notches, WHEEL);
    }
}
