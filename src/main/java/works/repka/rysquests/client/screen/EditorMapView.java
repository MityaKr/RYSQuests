package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.client.canvas.MapCanvas;
import works.repka.rysquests.client.canvas.MapLayouts;
import works.repka.rysquests.client.layout.CanvasLayout;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.editor.QuestEdits;
import works.repka.rysquests.progress.QuestStatus;

/**
 * The editor's "Карта" (pictures 33, 33c, 34, 35, 35b, 09, 46e): the sidebar, and in the middle either the canvas of the draft or the
 * chapter as a table, with the quest's or the chapter's form on the right. On the canvas only links are drawn by hand: drag from a
 * quest to another, a hint says in words what will open after what, and a link that would close a circle is refused with the reason.
 * Quests are not moved by hand; the map lays itself out from the links. "Вид карты" (picture 36) opens from the sidebar's foot.
 */
final class EditorMapView extends EditorPage implements QuestForm.Host, QuestTable.Host {
    private static final int TOOLS_PAD = 12;
    private static final int MINI_W = 224;
    private static final int MINI_H = 240;
    private static final int MINI_LIMIT = 30;

    enum Mode {
        CANVAS, TABLE, FORM
    }

    private Mode mode = Mode.CANVAS;
    private final EditorSidebar sidebar = new EditorSidebar(new EditorSidebar.Listener() {
        @Override
        public void chapter(ResourceLocation id) {
            openChapter(id);
        }

        @Override
        public void epoch(int number) {
            chapter = null;
            selected = null;
            chapterForm = false;
            if (number == 0) {
                canvas.flyToBook();
            } else {
                canvas.flyToEpoch(number);
            }
        }

        @Override
        public void archive() {
            EditorPages.current = EditorPages.CHECK;
            ((CheckView) screen.editor.page(EditorPages.CHECK)).showArchive();
        }

        @Override
        public void mapForm() {
            mode = Mode.FORM;
        }
    });
    private final MapCanvas canvas = new MapCanvas();
    private final MapCanvas mini = new MapCanvas();
    private final QuestForm form = new QuestForm(this, this);
    private final ChapterForm chapterPanel;
    private final QuestTable table = new QuestTable(this, this);
    private final TaskPicker picker = new TaskPicker(this);
    private final MapFormPage formPage;
    @Nullable
    private ResourceLocation chapter;
    @Nullable
    private ResourceLocation selected;
    private boolean chapterForm;
    @Nullable
    private CanvasLayout shownLayout;
    @Nullable
    private CanvasLayout miniLayout;
    @Nullable
    private QuestBook shownBook;
    // the link being drawn
    @Nullable
    private ResourceLocation linkFrom;
    private boolean linkMoved;
    private double pointerX;
    private double pointerY;
    private boolean canvasPressed;
    private int[] canvasArea = new int[4];
    private int windowX;
    private int windowY;

    /** The book grew to three chapters or thirty quests: the editor offers "Вид карты" once (plan, section 4). */
    private static boolean formOffered;
    private static boolean actionRegistered;

    EditorMapView(BookScreen screen) {
        super(screen);
        if (!actionRegistered) {
            actionRegistered = true;
            BookMessageLine.action("rysquests.ed.offer_form", "rysquests.ed.offer_form.open", message -> {
                if (net.minecraft.client.Minecraft.getInstance().screen instanceof BookScreen open) {
                    EditorPages.current = EditorPages.MAP;
                    open.editor.map().mode = Mode.FORM;
                    works.repka.rysquests.client.ClientMessages.dismiss();
                }
            });
        }
        chapterPanel = new ChapterForm(this, () -> chapterForm = false);
        formPage = new MapFormPage(this, () -> mode = Mode.CANVAS);
        canvas.setStatus(id -> QuestStatus.AVAILABLE, () -> 0);
        mini.setStatus(id -> QuestStatus.AVAILABLE, () -> 0);
        mini.setMinimap(false);
        mini.setInsets(8, 8, 8);
        canvas.setInsets(56, 14, 12);
        canvas.setListener(new MapCanvas.Listener() {
            @Override
            public void clicked(MapCanvas.Target target, int button) {
                if (button != 0) {
                    return;
                }
                switch (target.kind()) {
                    case QUEST -> select(target.quest());
                    case CHAPTER -> {
                        selected = null;
                        chapter = target.chapter();
                        chapterForm = true;
                    }
                    default -> {
                        selected = null;
                        chapterForm = false;
                    }
                }
            }
        });
    }

    @Override
    public TaskPicker picker() {
        return picker;
    }

    @Override
    public void select(@Nullable ResourceLocation quest) {
        selected = quest;
        form.reset();
        if (quest != null) {
            chapterForm = false;
            Quest found = book().view().quests().get(quest);
            if (found != null && found.chapter().isPresent()) {
                chapter = found.chapter().get();
            }
            canvas.select(quest);
        }
    }

    @Override
    public void pickTask(ResourceLocation quest) {
        picker.openTask(task -> QuestForm.addTask(quest, task));
    }

    @Override
    public void selected(Set<ResourceLocation> quests) {
        if (!quests.isEmpty()) {
            mini.select(quests.iterator().next());
        }
    }

    void openChapter(ResourceLocation id) {
        chapter = id;
        selected = null;
        if (mode == Mode.TABLE) {
            table.show(id);
        } else {
            mode = Mode.CANVAS;
            chapterForm = true;
            canvas.flyToChapter(id);
        }
    }

    /** The table of a chapter, as a new book opens (picture 09). */
    void openTable(ResourceLocation id) {
        mode = Mode.TABLE;
        chapter = id;
        selected = null;
        table.show(id);
        fields.focus("table-entry");
    }

    Mode mode() {
        return mode;
    }

    // --- drawing ---

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        windowX = x;
        windowY = y;
        if (mode == Mode.FORM) {
            formPage.render(ui, x, y, w, h);
            choices.render(ui, y + h);
            return;
        }
        boolean compact = screen.compact();
        QuestBook book = book().view();
        if (chapter != null && !book.chapters().containsKey(chapter)) {
            chapter = null;
        }
        if (chapter == null) {
            chapter = EditorSidebar.ordered(book).stream().filter(Chapter::isStory).map(Chapter::id).findFirst().orElse(null);
        }
        if (!formOffered && book.meta().mapForm() == works.repka.rysquests.data.MapForm.AUTO
                && (book.chapters().values().stream().filter(Chapter::isStory).count() >= 3 || book.quests().size() >= 30)) {
            formOffered = true;
            works.repka.rysquests.client.ClientMessages.show("rysquests.ed.offer_form", List.of(), true);
        }
        int sw = sidebar.width(compact);
        sidebar.render(ui, x, y, h, compact, chapter);
        int mx = x + sw;
        int mw = w - sw;
        if (mode == Mode.TABLE) {
            renderTable(ui, mx, y, mw, h, compact);
        } else {
            boolean panel = selected != null || chapterForm && chapter != null;
            int pw = panel ? compact ? QuestForm.COMPACT_WIDTH : QuestForm.WIDTH : 0;
            int cw = compact ? mw : mw - pw;
            renderCanvas(ui, mx, y, cw, h, book, compact);
            // in the compact layout the form lies over the canvas: above the items of its nodes too
            Runnable drawPanel = () -> {
                if (selected != null) {
                    form.render(ui, x + w - pw, y, pw, h, selected, compact);
                } else if (chapterForm && chapter != null) {
                    chapterPanel.render(ui, x + w - pw, y, pw, h, chapter);
                }
            };
            if (compact) {
                ui.aboveItems(drawPanel);
            } else {
                drawPanel.run();
            }
        }
        picker.render(ui, windowX + Math.min(sw + 60, Math.max(0, w - TaskPicker.W - 20)), y + 40);
        choices.render(ui, y + h);
    }

    private void renderCanvas(Ui ui, int x, int y, int w, int h, QuestBook book, boolean compact) {
        canvasArea = new int[] {x, y, w, h};
        MapLayouts.State state = MapLayouts.get(book);
        if (state.ready() && state.layout() == shownLayout && book != shownBook) {
            // a new name or a new task: the same layout, the canvas only takes the book's new texts
            canvas.setLayout(book, state.map(), state.layout());
            canvas.select(selected);
            shownBook = book;
        }
        if (state.ready() && state.layout() != shownLayout) {
            shownBook = book;
            ResourceLocation keep = selected;
            canvas.setLayout(book, state.map(), state.layout());
            shownLayout = state.layout();
            canvas.select(keep);
            if (chapter != null && keep == null) {
                canvas.flyToChapter(chapter);
            }
        }
        canvas.setLoading(shownLayout == null ? state.progress() : null, null);
        canvas.render(ui.g, x, y, w, h, ui.mouseX, ui.mouseY);
        if (linkFrom != null && linkMoved) {
            renderLink(ui, x, y, w, h, book);
        }
        renderTools(ui, x, y, w, book, compact);
    }

    /** The tools over the canvas: "Карта | Таблица", the crumbs, "+ Квест"; on the right help, "−" and "+". */
    private void renderTools(Ui ui, int x, int y, int w, QuestBook book, boolean compact) {
        float tx = x + TOOLS_PAD;
        float ty = y + TOOLS_PAD;
        float pen = tx;
        pen += switchView(ui, pen, ty) + 6;
        pen += crumbs(ui, pen, ty, book, compact) + 6;
        EdUi.tool(ui, tr("rysquests.ed.map.add_quest"), Icons.Shape.PLUS, pen, ty + 2, false, null, this::addQuest);
        float right = x + w - TOOLS_PAD;
        right -= EdUi.tool(ui, "", Icons.Shape.PLUS, right - EdUi.TB_H, ty + 2, false, Component.translatable("rysquests.ed.map.zoom_in"), () -> canvas.zoomBy(1.5));
        right -= 6;
        right -= EdUi.tool(ui, "", Icons.Shape.MINUS, right - EdUi.TB_H, ty + 2, false, Component.translatable("rysquests.ed.map.zoom_out"), () -> canvas.zoomBy(1 / 1.5));
        right -= 6 + EdUi.TB_H;
        boolean over = ui.over(right, ty + 2, EdUi.TB_H, EdUi.TB_H);
        ui.box(right, ty + 2, EdUi.TB_H, EdUi.TB_H, 9, ui.c(over ? "slot.hover" : "button.bg"), ui.c("button.border"));
        ui.info(right + 8 + 3f, ty + 10 + 3f, 14, ui.c("text.secondary"));
        ui.tooltipIfOver(right, ty + 2, EdUi.TB_H, EdUi.TB_H, Component.translatable("rysquests.ed.map.help"));
    }

    /** "Карта | Таблица". @return its width */
    private float switchView(Ui ui, float x, float y) {
        List<Segments.Item> items = List.of(Segments.item(tr("rysquests.ed.map.canvas"), Icons.Shape.MAP), Segments.item(tr("rysquests.ed.map.table"), Icons.Shape.LIST));
        int on = mode == Mode.TABLE ? 1 : 0;
        return Segments.render(ui, x, y, items, on, index -> {
            fields.blur();
            if (index == 1 && chapter != null) {
                openTable(chapter);
                fields.blur();
            } else if (index == 0) {
                mode = Mode.CANVAS;
                if (chapter != null) {
                    canvas.flyToChapter(chapter);
                }
            }
        });
    }

    /** "Вся книга › II › Ток", each step clickable. @return its width */
    private float crumbs(Ui ui, float x, float y, QuestBook book, boolean compact) {
        List<String> parts = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        Chapter open = chapter == null ? null : book.chapters().get(chapter);
        if (open == null) {
            return -6;
        }
        parts.add(compact ? "" : tr("rysquests.sidebar.whole_book"));
        actions.add(() -> {
            mode = Mode.CANVAS;
            canvas.flyToBook();
        });
        int epoch = open.epoch().orElse(0);
        if (epoch >= 1 && book.meta().epoch(epoch).isPresent()) {
            String name = mode == Mode.TABLE ? Ui.roman(epoch) + " " + book().text(book.meta().epoch(epoch).map(BookMeta.Epoch::title).orElse("")) : Ui.roman(epoch);
            parts.add(name);
            actions.add(() -> {
                mode = Mode.CANVAS;
                canvas.flyToEpoch(epoch);
            });
        } else if (mode == Mode.TABLE && book.meta().title().isPresent()) {
            parts.set(0, book.meta().title().get());
        }
        parts.add(open.title());
        actions.add(() -> openChapter(open.id()));
        float w = 12;
        for (int i = 0; i < parts.size(); i++) {
            w += (parts.get(i).isEmpty() ? 14 : ui.width(parts.get(i), 12.5f, i == parts.size() - 1 ? Ui.SEMIBOLD : Ui.REGULAR)) + (i > 0 ? 18 : 0);
        }
        w += 12;
        ui.box(x, y, w, 34, 9, ui.c("sidebar.bg"), ui.c("panel.border"));
        float pen = x + 12;
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                ui.icon(Icons.Shape.CHEVRON_RIGHT, pen + 3, y + 12, 10, ui.c("text.dim"));
                pen += 18;
            }
            boolean last = i == parts.size() - 1;
            String part = parts.get(i);
            float pw = part.isEmpty() ? 14 : ui.width(part, 12.5f, last ? Ui.SEMIBOLD : Ui.REGULAR);
            boolean over = ui.over(pen, y, pw, 34);
            if (part.isEmpty()) {
                ui.icon(Icons.Shape.COMPASS, pen, y + 10, 14, ui.c(over ? "text.primary" : "text.secondary"));
            } else {
                ui.label(part, pen, y + 17, 12.5f, last ? Ui.SEMIBOLD : Ui.REGULAR, ui.c(last || over ? "text.primary" : "text.secondary"));
            }
            ui.hit(pen, y, pw, 34, actions.get(i));
            pen += pw;
        }
        return w;
    }

    /** The link being drawn: a dashed line to the pointer, the hint in words, red with the reason when it would close a circle. */
    private void renderLink(Ui ui, int x, int y, int w, int h, QuestBook book) {
        CanvasLayout layout = canvas.layout();
        var map = MapLayouts.get(book).map();
        int from = layout == null ? -1 : map.quest(linkFrom);
        if (from < 0) {
            return;
        }
        float[] a = canvas.screenOf(layout.x[from], layout.y[from]);
        MapCanvas.Target target = canvas.hit(pointerX, pointerY);
        ResourceLocation to = target.kind() == MapCanvas.Kind.QUEST && !linkFrom.equals(target.quest()) ? target.quest() : null;
        float[] b = {(float) pointerX, (float) pointerY};
        boolean cycle = false;
        if (to != null) {
            int t = map.quest(to);
            b = canvas.screenOf(layout.x[t], layout.y[t]);
            cycle = QuestEdits.wouldCycle(book().data().quests(), to, linkFrom);
        }
        int color = ui.c(cycle ? "state.bad" : "text.primary");
        float[] start = a;
        float[] end = b;
        ui.clip(x, y, w, h);
        Draw.dashed(ui.g, start[0] / 2f, start[1] / 2f, end[0] / 2f, end[1] / 2f, 1f, 3f, 2.5f, color);
        if (to != null) {
            for (int i = 0; i < 16; i++) {
                Draw.arc(ui.g, end[0] / 2f, end[1] / 2f, 13.5f, i * 22.5f, 12f, 0.75f, color);
            }
            Quest source = book.quests().get(linkFrom);
            Quest dest = book.quests().get(to);
            String title = cycle ? tr("rysquests.ed.link.cycle") : tr("rysquests.ed.link.will_open", dest.title(), source.title());
            String small = cycle ? tr("rysquests.ed.link.cycle_reason", dest.title(), source.title()) : tr("rysquests.ed.link.release");
            tip(ui, end[0] + 34, end[1] - (cycle ? 78 : 62), title, small, cycle, x, y, w, h);
        }
        ui.unclip();
    }

    /** The hint of the link ({@code .tipb}); it stays inside the canvas and wraps when the form takes the right side (plan, section 6). */
    private static void tip(Ui ui, float x, float y, String title, String small, boolean bad, int ax, int ay, int aw, int ah) {
        float maxW = Math.min(bad ? 284 : 330, aw - 16);
        List<String> heads = ui.wrap(title, maxW - 18, 11.5f, Ui.MEDIUM, 3);
        List<String> lines = ui.wrap(small, maxW - 18, 11, Ui.REGULAR, 3);
        float widest = 0;
        for (String line : heads) {
            widest = Math.max(widest, ui.width(line, 11.5f, Ui.MEDIUM));
        }
        for (String line : lines) {
            widest = Math.max(widest, ui.width(line, 11, Ui.REGULAR));
        }
        float w = Math.min(maxW, widest + 18);
        float h = 10 + heads.size() * 16 + lines.size() * 14;
        float px = Math.max(ax + 8, Math.min(x, ax + aw - w - 8));
        float py = Math.max(ay + 56, Math.min(y, ay + ah - h - 8));
        ui.aboveItems(() -> {
            ui.shadow(px, py, w, h, 8, 0.8f);
            ui.box(px, py, w, h, 8, ui.c("sidebar.bg"), ui.c(bad ? "state.bad" : "button.border"));
            for (int i = 0; i < heads.size(); i++) {
                ui.label(heads.get(i), px + 9, py + 5 + 8 + i * 16, 11.5f, Ui.MEDIUM, ui.c("text.primary"));
            }
            for (int i = 0; i < lines.size(); i++) {
                ui.label(lines.get(i), px + 9, py + 5 + heads.size() * 16 + 7 + i * 14, 11, Ui.REGULAR, ui.c("text.muted"));
            }
        });
    }

    private void renderTable(Ui ui, int x, int y, int w, int h, boolean compact) {
        table.show(chapter);
        table.setCompact(compact);
        QuestBook book = book().view();
        float px = x + 16;
        float pw = w - 32;
        float pen = y + 12;
        float pen2 = px;
        pen2 += switchView(ui, pen2, pen) + 8;
        pen2 += crumbs(ui, pen2, pen, book, compact) + 8;
        ui.info(pen2 + 3f, pen + 10 + 3f, 14, ui.c("text.muted"));
        ui.tooltipIfOver(pen2, pen + 6, 16, 22, Component.translatable("rysquests.ed.table.help"));
        List<QuestTable.Row> rows = table.rows(book);
        if (rows.size() >= 8 || table.filter() != 0) {
            List<String> chips = table.chipLabels(book);
            float cw = EdUi.chipsWidth(ui, chips);
            EdUi.chips(ui, px + pw - cw, pen + 3, chips, table.filter(), table::setFilter);
        }
        pen += 34 + 10;
        pen += table.renderBar(ui, px, pen, pw) + 10;
        int count = chapter == null ? 0 : EditorSidebar.quests(book, chapter);
        boolean miniShown = count < MINI_LIMIT && !compact;
        float tw = miniShown ? pw - MINI_W - 16 : pw;
        table.render(ui, px, pen, tw, y + h - 12 - pen);
        if (miniShown) {
            renderMini(ui, px + tw + 16, pen, book);
        }
    }

    /** "Карта строится сама": the chapter's map beside its table while it is small (picture 09). */
    private void renderMini(Ui ui, float x, float y, QuestBook book) {
        ui.caps(tr("rysquests.ed.table.mini"), x, y + 7, ui.c("text.dim"));
        String open = tr("rysquests.ed.table.mini_open");
        float ow = ui.width(open, 11.5f, Ui.REGULAR);
        boolean over = ui.over(x + MINI_W - ow, y, ow, 14);
        ui.label(open, x + MINI_W - ow, y + 7, 11.5f, Ui.REGULAR, ui.c(over ? "text.primary" : "link"));
        ui.hit(x + MINI_W - ow, y, ow, 14, () -> {
            mode = Mode.CANVAS;
            if (chapter != null) {
                canvas.flyToChapter(chapter);
            }
        });
        float my = y + 14 + 6;
        MapLayouts.State state = MapLayouts.get(book);
        if (state.ready() && state.layout() != miniLayout) {
            mini.setLayout(book, state.map(), state.layout());
            miniLayout = state.layout();
            if (chapter != null) {
                mini.flyToChapter(chapter);
            }
        }
        if (miniLayout != null && chapter != null && !mini.flying()) {
            mini.flyToChapter(chapter);
        }
        mini.setLoading(miniLayout == null ? state.progress() : null, null);
        ui.box(x, my, MINI_W, MINI_H, 12, ui.c("map.bg"), ui.c("panel.border"));
        mini.render(ui.g, Math.round(x + 1), Math.round(my + 1), MINI_W - 2, MINI_H - 2, ui.mouseX, ui.mouseY);
    }

    // --- changes ---

    private void addQuest() {
        if (chapter == null) {
            return;
        }
        List<ResourceLocation> after = selected == null ? List.of() : List.of(selected);
        ResourceLocation id = book().addQuest(chapter, tr("rysquests.ed.quest.new_next"), QuestEdits.Kind.MAIN, after, selected);
        select(id);
        fields.focus("quest-title:" + id);
    }

    // --- input ---

    @Override
    public void press(double x, double y, int button) {
        super.press(x, y, button);
        pointerX = x;
        pointerY = y;
        if (mode == Mode.FORM || picker.isOpen()) {
            return;
        }
        if (mode == Mode.TABLE) {
            if (!table.contains(x, y)) {
                table.leave();
            }
            return;
        }
        boolean inCanvas = x >= canvasArea[0] && x < canvasArea[0] + canvasArea[2] && y >= canvasArea[1] + 56 && y < canvasArea[1] + canvasArea[3];
        if (!inCanvas || form.contains(x, y)) {
            return;
        }
        MapCanvas.Target target = canvas.hit(x, y);
        if (button == 0 && target.kind() == MapCanvas.Kind.QUEST) {
            linkFrom = target.quest();
            linkMoved = false;
            return;
        }
        canvasPressed = canvas.mouseClicked(x, y, button);
    }

    @Override
    public boolean drag(double deltaX, double deltaY) {
        pointerX += deltaX;
        pointerY += deltaY;
        if (linkFrom != null) {
            if (Math.abs(deltaX) + Math.abs(deltaY) > 0) {
                linkMoved = true;
            }
            return true;
        }
        if (canvasPressed) {
            return canvas.mouseDragged(pointerX, pointerY, deltaX, deltaY);
        }
        fields.drag(pointerX, pointerY);
        return false;
    }

    @Override
    public boolean release(double x, double y, int button) {
        pointerX = x;
        pointerY = y;
        if (linkFrom != null) {
            ResourceLocation from = linkFrom;
            linkFrom = null;
            if (!linkMoved) {
                select(from);
                return true;
            }
            MapCanvas.Target target = canvas.hit(x, y);
            if (target.kind() == MapCanvas.Kind.QUEST && !from.equals(target.quest())) {
                QuestTable.link(target.quest(), from);
            }
            return true;
        }
        if (canvasPressed) {
            canvasPressed = false;
            return canvas.mouseReleased(x, y, button);
        }
        return false;
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (picker.isOpen()) {
            return;
        }
        if (mode == Mode.FORM) {
            formPage.scroll(mouseX, mouseY, amount);
            return;
        }
        if (sidebar.contains(mouseX, mouseY)) {
            sidebar.scroll(mouseX, mouseY, amount);
        } else if (mode == Mode.TABLE) {
            table.scroll(amount);
        } else if (selected != null && form.contains(mouseX, mouseY)) {
            form.scroll(amount);
        } else if (chapterForm && mouseX > canvasArea[0] + canvasArea[2]) {
            chapterPanel.scroll(amount);
        } else {
            canvas.mouseScrolled(mouseX, mouseY, amount);
        }
    }

    @Override
    public boolean key(int key) {
        if (picker.key(key)) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && linkFrom != null) {
            linkFrom = null;
            return true;
        }
        if (super.key(key)) {
            return true;
        }
        if (mode == Mode.TABLE && table.key(key)) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && mode == Mode.FORM) {
            mode = Mode.CANVAS;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && (selected != null || chapterForm)) {
            selected = null;
            chapterForm = false;
            return true;
        }
        return false;
    }

    @Override
    boolean fieldKey(int key) {
        if (mode == Mode.TABLE) {
            return table.fieldKey(key);
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
            fields.blur();
            book().endTyping();
            return true;
        }
        return false;
    }

    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        return canvasArea[2] > 0 ? canvasArea : null;
    }

    @Override
    public void closed() {
        picker.close();
    }

    // --- tests ---

    QuestTable tableForTest() {
        return table;
    }

    TaskPicker pickerForTest() {
        return picker;
    }

    @Nullable
    ResourceLocation selectedForTest() {
        return selected;
    }

    @Nullable
    ResourceLocation chapterForTest() {
        return chapter;
    }

    void modeForTest(Mode value) {
        mode = value;
    }

    void chapterFormForTest(ResourceLocation id) {
        mode = Mode.CANVAS;
        chapter = id;
        selected = null;
        chapterForm = true;
    }

    /** Holds a link drawn from one quest towards another, for the screenshot of pictures 33 and 33c. */
    boolean holdLinkForTest(ResourceLocation from, ResourceLocation to) {
        CanvasLayout layout = canvas.layout();
        if (layout == null) {
            return false;
        }
        var map = MapLayouts.get(book().view()).map();
        int t = map.quest(to);
        if (t < 0) {
            return false;
        }
        float[] b = canvas.screenOf(layout.x[t], layout.y[t]);
        linkFrom = from;
        linkMoved = true;
        pointerX = b[0];
        pointerY = b[1];
        return true;
    }

    void dropLinkForTest() {
        release(pointerX, pointerY, 0);
    }

    void flyToQuestForTest(ResourceLocation quest) {
        canvas.flyToQuest(quest);
    }

    boolean canvasReadyForTest() {
        return shownLayout != null && !canvas.flying();
    }
}
