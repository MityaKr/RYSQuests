package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;

/**
 * The editor's contents on the left (pictures 33, 35, 09; mockup {@code sidebar(editor=True)}): the same rows as the player's
 * {@link Sidebar} at its sizes, but over the draft and with what the author needs: chapter counts in quests, epochs in chapters,
 * "+ Глава" in an open epoch, "+ Эпоха", "Вне карты" with the archive, and at the foot "Вид карты: … сменить". A book that is not open
 * to players yet shows "Первые шаги" above the foot (picture 09). Folded or compact, it is the player's rail of numerals.
 */
final class EditorSidebar {
    static final int WIDTH = Sidebar.WIDTH;
    private static final int HEAD_H = 40;
    private static final int EPOCH_H = 32;
    private static final int CHAPTER_H = 28;
    private static final int GAP = 1;
    private static final int FOOT_H = 37;
    private static final int TODO_H = 26;
    private static final int WHEEL = 40;
    /** "Посмотреть как игрок": the author left the editor once since the book was made. */
    static boolean lookedAsPlayer;

    /** What a click in the sidebar asks for. */
    interface Listener {
        void chapter(ResourceLocation id);

        void epoch(int number);

        void archive();

        void mapForm();
    }

    private final Listener listener;
    private final Scroll scroll = new Scroll();
    private final Map<Integer, Boolean> unfolded = new HashMap<>();
    private int[] list = new int[4];
    private int[] area = new int[4];

    EditorSidebar(Listener listener) {
        this.listener = listener;
    }

    int width(boolean compact) {
        return compact || Sidebar.folded ? Sidebar.RAIL_WIDTH : WIDTH;
    }

    boolean contains(double x, double y) {
        return x >= area[0] && x < area[0] + area[2] && y >= area[1] && y < area[1] + area[3];
    }

    void scroll(double x, double y, double amount) {
        if (x >= list[0] && x < list[0] + list[2] && y >= list[1] && y < list[1] + list[3]) {
            scroll.wheel(amount, WHEEL);
        }
    }

    /** The epochs of the book with their chapters, chapters without an epoch apart. */
    private record Model(Map<Integer, List<Chapter>> epochs, List<Chapter> flat, List<Chapter> offMap, boolean hasEpochs) {
    }

    private static Model model(QuestBook book) {
        Map<Integer, List<Chapter>> epochs = new LinkedHashMap<>();
        int epochCount = book.meta().epochs().size();
        for (int i = 1; i <= epochCount; i++) {
            epochs.put(i, new ArrayList<>());
        }
        List<Chapter> flat = new ArrayList<>();
        List<Chapter> offMap = new ArrayList<>();
        for (Chapter chapter : ordered(book)) {
            if (!chapter.isStory()) {
                continue;
            }
            if (chapter.offMap()) {
                offMap.add(chapter);
            } else if (chapter.epoch().filter(n -> n >= 1).isPresent() && epochCount > 0) {
                epochs.computeIfAbsent(chapter.epoch().get(), key -> new ArrayList<>()).add(chapter);
            } else if (epochCount > 0) {
                offMap.add(chapter);
            } else {
                flat.add(chapter);
            }
        }
        return new Model(epochs, flat, offMap, epochCount > 0);
    }

    static int quests(QuestBook book, ResourceLocation chapter) {
        return (int) book.quests().values().stream().filter(q -> q.chapter().filter(chapter::equals).isPresent() && !q.options().archived()).count();
    }

    static long archived(QuestBook book) {
        return book.quests().values().stream().filter(q -> q.options().archived() && q.chapter().map(id -> book.chapters().containsKey(id) && book.chapters().get(id).isStory()).orElse(false)).count()
                + book.achievements().values().stream().filter(a -> a.archived()).count();
    }

    void render(Ui ui, int x, int y, int h, boolean compact, @Nullable ResourceLocation chosen) {
        EditorBook editor = EditorBook.get();
        QuestBook book = editor.view();
        Model model = model(book);
        int w = width(compact);
        area = new int[] {x, y, w, h};
        ui.rect(x, y, w, h, ui.c("sidebar.bg"));
        ui.rect(x + w - 1, y, 1, h, ui.c("panel.border"));
        if (w == Sidebar.RAIL_WIDTH) {
            renderRail(ui, model, x, y, w, h, compact);
            return;
        }
        float headCy = y + HEAD_H / 2f;
        ui.caps(EditorBook.tr(model.hasEpochs() ? "rysquests.sidebar.epochs" : "rysquests.sidebar.chapters"), x + 14, headCy, ui.c("text.dim"));
        float bx = x + w - 8 - 28;
        boolean overFold = ui.over(bx, headCy - 14, 28, 28);
        ui.icon(Icons.Shape.SIDEBAR, bx + 7, headCy - 7, 14, ui.c(overFold ? "text.primary" : "text.muted"));
        ui.hit(bx, headCy - 14, 28, 28, () -> Sidebar.folded = true);
        // the foot: off the map and the archive, or the first steps of a new book; then the form of the map
        boolean firstSteps = !book.meta().published() && !model.hasEpochs() && book.chapters().size() <= 3;
        long archive = archived(book);
        int offRows = model.offMap().size() + (archive > 0 ? 1 : 0);
        float lowerH = firstSteps ? 1 + 6 + 22 + 6 * TODO_H + 8 : offRows == 0 ? 0 : 1 + 6 + 22 + offRows * (CHAPTER_H + GAP) + 8;
        int listTop = y + HEAD_H;
        int listH = Math.round(y + h - FOOT_H - lowerH - listTop);
        list = new int[] {x, listTop, w - 1, listH};
        ui.clip(x, listTop, w - 1, listH);
        int offset = scroll.offset();
        float pen = listTop - offset;
        if (model.hasEpochs()) {
            for (Map.Entry<Integer, List<Chapter>> epoch : model.epochs().entrySet()) {
                int number = epoch.getKey();
                boolean open = unfolded.getOrDefault(number, epoch.getValue().stream().anyMatch(c -> c.id().equals(chosen)));
                renderEpoch(ui, book, number, epoch.getValue().size(), open, x + 8, pen, w - 17);
                pen += EPOCH_H + GAP;
                if (open) {
                    for (Chapter chapter : epoch.getValue()) {
                        renderChapter(ui, book, chapter, x + 8, pen, w - 17, 22, chosen, null);
                        pen += CHAPTER_H + GAP;
                    }
                    renderAdd(ui, EditorBook.tr("rysquests.ed.side.chapter"), null, x + 8, pen, w - 17, 22 + 20 + 8 - 18, () -> listener.chapter(editor.addChapter(
                            EditorBook.tr("rysquests.ed.side.new_chapter"), Optional.of(number))));
                    pen += CHAPTER_H + GAP;
                }
            }
        } else {
            for (Chapter chapter : model.flat()) {
                renderChapter(ui, book, chapter, x + 8, pen, w - 17, 12, chosen, null);
                pen += CHAPTER_H + GAP;
            }
            renderAdd(ui, EditorBook.tr("rysquests.ed.side.chapter"), null, x + 8, pen, w - 17, 12, () -> listener.chapter(editor.addChapter(
                    EditorBook.tr("rysquests.ed.side.chapter_n", model.flat().size() + 1), Optional.empty())));
            pen += CHAPTER_H + GAP;
        }
        pen += 4;
        renderAdd(ui, EditorBook.tr("rysquests.ed.side.epoch"), model.hasEpochs() ? null : EditorBook.tr("rysquests.ed.side.optional"), x + 8, pen, w - 17, 12,
                () -> addEpoch(book));
        pen += CHAPTER_H + GAP;
        ui.unclip();
        scroll.content(pen + offset - listTop + 8, listH);
        float lower = y + h - FOOT_H - lowerH;
        if (firstSteps) {
            renderFirstSteps(ui, book, x, lower, w);
        } else if (offRows > 0) {
            ui.rect(x, lower, w - 1, 1, ui.c("panel.border"));
            ui.caps(EditorBook.tr("rysquests.sidebar.off_map"), x + 14, lower + 1 + 6 + 11, ui.c("text.dim"));
            float row = lower + 1 + 6 + 22;
            for (Chapter chapter : model.offMap()) {
                renderChapter(ui, book, chapter, x + 8, row, w - 17, 12, chosen, chapter.offMap() ? Icons.Shape.FLAG : Icons.Shape.REPEAT);
                row += CHAPTER_H + GAP;
            }
            if (archive > 0) {
                renderRow(ui, EditorBook.tr("rysquests.ed.side.archive"), String.valueOf(archive), Icons.Shape.ARCHIVE, x + 8, row, w - 17, false, listener::archive);
            }
        }
        renderFoot(ui, book, x, y + h - FOOT_H, w);
    }

    private void addEpoch(QuestBook book) {
        EditorBook editor = EditorBook.get();
        List<BookMeta.Epoch> epochs = new ArrayList<>(book.meta().epochs());
        String title = EditorBook.tr("rysquests.ed.side.epoch_n", epochs.size() + 1);
        epochs.add(new BookMeta.Epoch(title, false));
        int number = epochs.size();
        editor.setMeta(EditorBook.tr("rysquests.ed.log.new_epoch", title), meta -> meta.withEpochs(epochs));
        unfolded.put(number, true);
    }

    private void renderEpoch(Ui ui, QuestBook book, int number, int chapters, boolean open, float x, float y, float w) {
        if (!ui.rowVisible(y, EPOCH_H)) {
            return;
        }
        boolean over = ui.over(x, y, w, EPOCH_H);
        if (over) {
            ui.fill(x, y, w, EPOCH_H, 8, ui.c("button.bg"));
        }
        float cy = y + EPOCH_H / 2f;
        ui.chevron(open, x + 4, cy, 12, ui.c("text.dim"));
        float pen = x + 4 + 12 + 7;
        ui.labelCentered(Ui.roman(number), pen + 14, cy, 12, Ui.DISPLAY, ui.c(open ? "text.primary" : "text.muted"));
        pen += 28 + 7;
        String count = EditorBook.tr("rysquests.ed.side.chapters", chapters);
        float countW = ui.width(count, 11, Ui.REGULAR);
        ui.labelRight(count, x + w - 8, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        String title = book.meta().epoch(number).map(BookMeta.Epoch::title).map(EditorBook.get()::text).orElse(Ui.roman(number));
        ui.labelFitTip(title, pen, cy, x + w - 8 - countW - 6 - pen, 12.5f, Ui.SEMIBOLD, ui.c(open ? "text.primary" : "text.secondary"));
        ui.hit(x, y, 24, EPOCH_H, () -> unfolded.put(number, !open));
        ui.hit(x + 24, y, w - 24, EPOCH_H, () -> {
            unfolded.put(number, true);
            listener.epoch(number);
        });
    }

    private void renderChapter(Ui ui, QuestBook book, Chapter chapter, float x, float y, float w, float padLeft, @Nullable ResourceLocation chosen,
            @Nullable Icons.Shape glyph) {
        if (!ui.rowVisible(y, CHAPTER_H)) {
            return;
        }
        boolean on = chapter.id().equals(chosen);
        boolean over = ui.over(x, y, w, CHAPTER_H);
        if (on || over) {
            ui.fill(x, y, w, CHAPTER_H, 7, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        float cy = y + CHAPTER_H / 2f;
        if (on && glyph == null) {
            ui.icon(Icons.Shape.GRIP, x + 4, cy - 6, 12, ui.c("text.dim"));
        }
        float pen = x + padLeft;
        if (glyph != null) {
            ui.icon(glyph, pen + 4, cy - 6, 12, ui.c("text.dim"));
        } else {
            ui.labelRight(String.format("%02d", number(book, chapter)), pen + 20, cy, 11,
                    Ui.REGULAR, ui.c("text.dim"));
        }
        pen += 20 + 8;
        String count = String.valueOf(quests(book, chapter.id()));
        float countW = ui.width(count, 11, Ui.REGULAR);
        ui.labelRight(count, x + w - 8, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        ui.labelFitTip(EditorBook.get().text(chapter.title()), pen, cy, x + w - 8 - countW - 6 - pen, 12.5f, on ? Ui.MEDIUM : Ui.REGULAR,
                ui.c(on || over ? "text.primary" : "text.secondary"));
        ui.hit(x, y, w, CHAPTER_H, () -> listener.chapter(chapter.id()));
    }

    /** The chapters in the book's order: those {@code chapter_order} names first, the rest as the book lists them. */
    static List<Chapter> ordered(QuestBook book) {
        List<ResourceLocation> order = book.meta().chapterOrder();
        List<Chapter> out = new ArrayList<>(book.chapters().values());
        out.sort(java.util.Comparator.comparingInt(chapter -> {
            int at = order.indexOf(chapter.id());
            return at < 0 ? Integer.MAX_VALUE : at;
        }));
        return out;
    }

    /** The place of a chapter on the map, from 1. */
    private static int number(QuestBook book, Chapter chapter) {
        int n = 0;
        for (Chapter each : ordered(book)) {
            if (each.isStory() && !each.offMap()) {
                n++;
                if (each.id().equals(chapter.id())) {
                    return n;
                }
            }
        }
        return 0;
    }

    private static void renderRow(Ui ui, String label, String count, Icons.Shape glyph, float x, float y, float w, boolean on, Runnable action) {
        boolean over = ui.over(x, y, w, CHAPTER_H);
        if (on || over) {
            ui.fill(x, y, w, CHAPTER_H, 7, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        float cy = y + CHAPTER_H / 2f;
        ui.icon(glyph, x + 16, cy - 6, 12, ui.c("text.dim"));
        ui.labelRight(count, x + w - 8, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        ui.label(label, x + 40, cy, 12.5f, Ui.REGULAR, ui.c(over ? "text.primary" : "text.secondary"));
        ui.hit(x, y, w, CHAPTER_H, action);
    }

    private static void renderAdd(Ui ui, String label, @Nullable String note, float x, float y, float w, float padLeft, Runnable action) {
        if (!ui.rowVisible(y, CHAPTER_H)) {
            return;
        }
        boolean over = ui.over(x, y, w, CHAPTER_H);
        float cy = y + CHAPTER_H / 2f;
        int fg = ui.c(over ? "text.primary" : note != null ? "text.dim" : "text.muted");
        ui.icon(Icons.Shape.PLUS, x + padLeft + 9, cy - 5.5f, 11, fg);
        ui.label(label, x + padLeft + 28, cy, 12.5f, Ui.REGULAR, fg);
        if (note != null) {
            ui.labelRight(note, x + w - 8, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        }
        ui.hit(x, y, w, CHAPTER_H, action);
    }

    /** The six first steps of a new book (picture 09), done ones struck through. */
    private void renderFirstSteps(Ui ui, QuestBook book, int x, float y, int w) {
        List<Quest> quests = book.quests().values().stream().filter(q -> q.chapter().map(id -> book.chapters().containsKey(id) && book.chapters().get(id).isStory()).orElse(false)).toList();
        boolean[] done = {
                book.meta().title().isPresent(),
                !quests.isEmpty(),
                quests.stream().anyMatch(q -> !q.tasks().isEmpty()),
                quests.stream().anyMatch(q -> !q.rewards().isEmpty()),
                lookedAsPlayer,
                book.meta().published()};
        int count = 0;
        for (boolean each : done) {
            count += each ? 1 : 0;
        }
        ui.rect(x, y, w - 1, 1, ui.c("panel.border"));
        ui.caps(EditorBook.tr("rysquests.ed.steps.title", count, done.length), x + 14, y + 1 + 6 + 11, ui.c("text.dim"));
        float row = y + 1 + 6 + 22;
        for (int i = 0; i < done.length; i++) {
            float cy = row + TODO_H / 2f;
            if (done[i]) {
                ui.circle(x + 14, cy - 8, 16, ui.c("state.ok"));
                ui.check(x + 14 + 3, cy - 5, 10, ui.c("sidebar.bg"));
            } else {
                ui.ring(x + 14, cy - 8, 16, 1.5f, ui.c("text.dim"));
            }
            String label = EditorBook.tr("rysquests.ed.steps." + (i + 1));
            ui.label(label, x + 14 + 16 + 9, cy, 12.5f, Ui.REGULAR, ui.c(done[i] ? "text.muted" : "text.secondary"));
            if (done[i]) {
                ui.rect(x + 14 + 16 + 9, cy, ui.width(label, 12.5f, Ui.REGULAR), 1, ui.c("text.dim"));
            }
            row += TODO_H;
        }
    }

    private void renderFoot(Ui ui, QuestBook book, int x, float y, int w) {
        ui.rect(x, y, w - 1, 1, ui.c("panel.border"));
        float cy = y + FOOT_H / 2f;
        ui.icon(Icons.Shape.MAP, x + 14, cy - 7, 14, ui.c("text.secondary"));
        MapForm form = book.meta().mapForm();
        String name = EditorBook.tr("rysquests.ed.form." + (form == MapForm.AUTO ? "auto" : form.getSerializedName())).toLowerCase(java.util.Locale.ROOT);
        String change = EditorBook.tr("rysquests.ed.side.change");
        float linkW = ui.width(change, 12, Ui.MEDIUM);
        ui.labelFitTip(EditorBook.tr("rysquests.ed.side.form", name), x + 14 + 14 + 8, cy, w - 14 - 22 - 14 - linkW - 12, 12, Ui.REGULAR, ui.c("text.secondary"));
        boolean over = ui.over(x + w - 13 - linkW, cy - 9, linkW, 18);
        ui.label(change, x + w - 13 - linkW, cy, 12, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
        ui.hit(x, y, w, FOOT_H, listener::mapForm);
    }

    /** The rail of the compact layout (picture 46e): unfold, the whole book, the epochs, "Вне карты" at the foot. */
    private void renderRail(Ui ui, Model model, int x, int y, int w, int h, boolean compact) {
        float pen = y + 8;
        if (!compact) {
            railButton(ui, Icons.Shape.SIDEBAR, null, x, pen, Component.translatable("rysquests.sidebar.unfold"), () -> Sidebar.folded = false);
            pen += 34;
        } else {
            railButton(ui, Icons.Shape.SIDEBAR, null, x, pen, Component.translatable("rysquests.sidebar.unfold"), () -> { });
            pen += 34;
        }
        railButton(ui, Icons.Shape.COMPASS, null, x, pen, Component.translatable("rysquests.sidebar.whole_book"), () -> listener.epoch(0));
        pen += 34;
        for (int number : model.epochs().keySet()) {
            railButton(ui, null, Ui.roman(number), x, pen, Component.literal(Ui.roman(number)), () -> listener.epoch(number));
            pen += 34;
        }
        railButton(ui, Icons.Shape.FLAG, null, x, y + h - 8 - 30, Component.translatable("rysquests.sidebar.off_map"), listener::archive);
    }

    private static void railButton(Ui ui, @Nullable Icons.Shape icon, @Nullable String text, float x, float y, Component tip, Runnable action) {
        float bx = x + 7;
        boolean over = ui.over(bx, y, 34, 30);
        if (over) {
            ui.fill(bx, y, 34, 30, 7, ui.c("button.bg"));
        }
        int fg = ui.c(over ? "text.primary" : "text.secondary");
        if (icon != null) {
            ui.icon(icon, bx + 10, y + 8, 14, fg);
        } else if (text != null) {
            ui.labelCentered(text, bx + 17, y + 15, 11.5f, Ui.SEMIBOLD, fg);
        }
        ui.tooltipIfOver(bx, y, 34, 30, tip);
        ui.hit(bx, y, 34, 30, action);
    }

    /** The form the book is shown in now, the automatic one worked out. */
    static MapForm shownForm(QuestBook book) {
        return MapForms.choose(book).form();
    }
}
