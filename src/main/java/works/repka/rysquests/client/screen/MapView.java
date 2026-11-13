package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.canvas.MapCanvas;
import works.repka.rysquests.client.canvas.MapLayouts;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.progress.QuestPath;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;

/**
 * The map ("Полотно", pictures 13 to 18, 22, 41 to 47, 49): the {@link MapCanvas} with the tools over it and the panel on the right.
 * <ul>
 *     <li>top left "Карта | Список" and the crumbs from the epoch level; top right the legend's help, "−" and "+";</li>
 *     <li>bottom left "К моему квесту"; bottom right the legend line on the whole book, the minimap closer;</li>
 *     <li>hovering a chapter shows its popover (15); the chapter level has the chapter's card (16); a selected quest its panel (17),
 *     a locked one with its path lit on the canvas (22);</li>
 *     <li>in the compact layout (46) the panel lies over the canvas and closes with its cross, the minimap and legend line give way.</li>
 * </ul>
 * Tools stand on solid plates over the canvas, so nodes and labels go under them. The sidebar is the {@link MapTab}'s, which calls
 * {@link #flyToChapter}, {@link #flyToEpoch} and {@link #flyToBook}.
 */
final class MapView implements BookView {
    private static final int PAD = 12;
    private static final int TB = 30;
    private static final int TB_GAP = 6;
    private static final int PANEL_W = DetailPanel.WIDTH;
    private static final int POP_W = 244;
    private static final int LEGEND_W = 250;

    private final BookScreen screen;
    final MapCanvas canvas = new MapCanvas();
    private final DetailPanel panel = new DetailPanel();
    /** The quest panel shared with the list and "Now": its cross goes back to the chapter card, its way is lit on the canvas. */
    private final QuestPanel questView = new QuestPanel(new QuestPanel.Events() {
        @Override
        public void select(Quest quest) {
            if (map != null && map.quest(quest.id()) >= 0) {
                flyToQuest(quest.id());
            } else {
                BookState.select(quest);
            }
        }

        @Override
        public void showOnMap(Quest quest) {
            flyToQuest(quest.id());
        }

        @Override
        public void route(@Nullable QuestPath path) {
            canvas.setRoute(path == null ? List.of() : path.steps().stream().map(Quest::id).toList());
        }

        @Override
        public void close() {
            BookState.quest = null;
            canvas.select(null);
        }
    });
    /** The quest panel was drawn this frame (the wheel goes to it, not to the chapter card). */
    private boolean questShown;
    @Nullable
    private MapLayouts.State state;
    @Nullable
    private MapBook map;
    /** The chapter whose card shows on the chapter level. */
    @Nullable
    private ResourceLocation openChapter;
    private int focusEpoch;
    private boolean legendOpen;
    private boolean panelClosed;
    @Nullable
    private ResourceLocation panelFor;
    private final List<float[]> tools = new ArrayList<>();
    private boolean pressedOnCanvas;
    private double pointerX;
    private double pointerY;
    @Nullable
    private ResourceLocation here;
    private int hereVersion = Integer.MIN_VALUE;
    @Nullable
    private QuestBook hereBook;

    MapView(BookScreen screen) {
        this.screen = screen;
        canvas.setListener(new MapCanvas.Listener() {
            @Override
            public void clicked(MapCanvas.Target target, int button) {
                onCanvasClick(target, button);
            }
        });
        canvas.setStatus(MapView::status, ClientProgress::version);
        canvas.setInsets(56, 14, 54);
        canvas.setMinimapFrom(MapCanvas.Level.EPOCH);
    }

    // ================= the sidebar's side =================

    void flyToBook() {
        lastFlight = this::flyToBook;
        openChapter = null;
        focusEpoch = 0;
        canvas.select(null);
        BookState.quest = null;
        canvas.flyToBook();
    }

    void flyToEpoch(int number) {
        // the sidebar numbers a galaxy's chapters without a section -1; on the canvas they are epoch 0
        int epoch = Math.max(0, number);
        lastFlight = () -> flyToEpoch(epoch);
        openChapter = null;
        focusEpoch = epoch;
        canvas.flyToEpoch(epoch);
    }

    void flyToChapter(ResourceLocation chapter) {
        lastFlight = () -> flyToChapter(chapter);
        openChapter = chapter;
        focusEpoch = chapterEpoch(chapter);
        BookState.openChapter(chapter);
        BookState.quest = null;
        panelClosed = false;
        canvas.setOpenChapter(chapter);
        canvas.flyToChapter(chapter);
    }

    void flyToQuest(ResourceLocation quest) {
        Quest found = BookData.book().quests().get(quest);
        if (found == null) {
            return;
        }
        lastFlight = () -> flyToQuest(quest);
        BookState.select(found);
        openChapter = found.chapter().orElse(null);
        focusEpoch = openChapter == null ? 0 : chapterEpoch(openChapter);
        panelClosed = false;
        canvas.flyToQuest(quest);
    }

    /** A quest "Показать на карте" asked for from the list, "Now" or search: flown to when the map is next drawn and laid out. */
    @Nullable
    private static ResourceLocation focusNext;

    /** "Показать на карте" from another page: the map flies to the quest when it shows. */
    static void showNext(ResourceLocation quest) {
        focusNext = quest;
    }

    /** "Show on the map" from elsewhere: the selected quest in a close up. */
    void focusSelected() {
        if (BookState.quest != null) {
            flyToQuest(BookState.quest);
        }
    }

    /** The chapter the sidebar marks: the open one, or none for the whole book. */
    @Nullable
    ResourceLocation selectedChapter() {
        return canvas.level() == MapCanvas.Level.BOOK || canvas.level() == MapCanvas.Level.EPOCH && openChapter == null ? null : openChapter;
    }

    private int chapterEpoch(ResourceLocation chapter) {
        Chapter found = BookData.book().chapters().get(chapter);
        return found == null ? 0 : found.epoch().orElse(0);
    }

    // ================= drawing =================

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        QuestBook book = BookData.book();
        state = MapLayouts.get(book);
        map = state.map();
        tools.clear();
        if (state.ready()) {
            if (canvas.layout() != state.layout()) {
                canvas.setLayout(book, state.map(), state.layout());
                canvas.setLoading(null, null);
                if (openChapter != null) {
                    canvas.setOpenChapter(openChapter);
                }
            }
        } else if (canvas.layout() != null || !loadingShown) {
            canvas.setLayout(book, state.map(), null);
            MapLayouts.State shown = state;
            canvas.setLoading(() -> shown.progress().getAsDouble(), () -> BookState.view = ViewMode.LIST);
            loadingShown = true;
        }
        if (state.ready()) {
            loadingShown = false;
        }
        refreshHere(book);
        if (focusNext != null && state.ready()) {
            ResourceLocation target = focusNext;
            focusNext = null;
            flyToQuest(target);
        }
        Quest selected = selectedQuest(book);
        canvas.select(selected == null ? null : selected.id());
        boolean compact = screen.compact();
        MapCanvas.Level level = canvas.level();
        if (level == MapCanvas.Level.BOOK && !canvas.flying()) {
            openChapter = null;
        }
        canvas.setOpenChapter(openChapter);
        canvas.setFocusEpoch(focusEpoch);
        canvas.setMinimap(!compact);
        boolean showCard = selected == null && openChapter != null && level.ordinal() >= MapCanvas.Level.CHAPTER.ordinal() && state.ready();
        boolean showPanel = (selected != null || showCard) && !panelClosed;
        int canvasW = showPanel && !compact ? w - PANEL_W : w;
        canvas.setViewport(x, y, canvasW, h);
        if (canvasW != lastCanvasW) {
            if (lastCanvasW >= 0 && lastFlight != null && canvas.flying()) {
                lastFlight.run();
            }
            lastCanvasW = canvasW;
        }
        canvas.render(ui.g, x, y, canvasW, h, ui.mouseX, ui.mouseY);
        int toolsRight = showPanel && compact ? x + w - PANEL_W : x + canvasW;
        middle = new int[] {x, y, toolsRight - x, h};
        drawTopLeft(ui, x, y, toolsRight - x, compact);
        if (state.ready()) {
            drawTopRight(ui, toolsRight, y);
            drawBottom(ui, x, y, toolsRight - x, h, compact, level);
            drawChapterPopover(ui, x, y, canvasW, h);
            if (legendOpen) {
                drawLegend(ui, toolsRight, y);
            }
        }
        questShown = showPanel && selected != null;
        if (showPanel) {
            int px = x + w - PANEL_W;
            tools.add(new float[] {px, y, PANEL_W, h});
            if (selected != null) {
                questPanel(ui, selected, px, y, PANEL_W, h);
            } else {
                chapterCard(ui, openChapter, px, y, PANEL_W, h);
            }
        }
        if (selected == null) {
            questView.clear();
        }
    }

    private boolean loadingShown;
    /** The canvas left of the panel, where the book's message line lies; null before the first frame. */
    @Nullable
    private int[] middle;
    /** The last flight asked for: flown again when the canvas changes its width under it, as the panel opens or closes. */
    @Nullable
    private Runnable lastFlight;
    private int lastCanvasW = -1;

    @Nullable
    private Quest selectedQuest(QuestBook book) {
        Quest quest = BookState.quest == null ? null : book.quests().get(BookState.quest);
        if (quest == null || map == null || map.quest(quest.id()) < 0) {
            return null;
        }
        return quest;
    }

    // --- tools ---

    private void drawTopLeft(Ui ui, int x, int y, int w, boolean compact) {
        Segments.Item mapItem = Segments.item(Component.translatable("rysquests.tab.map").getString(), Icons.Shape.MAP);
        List<Segments.Item> items = List.of(mapItem, Segments.item(Component.translatable("rysquests.view.list").getString(), Icons.Shape.LIST));
        float sx = x + PAD;
        float sy = y + PAD;
        float segW = Segments.width(items, 0);
        ui.fill(sx - 2, sy - 2, segW + 4, Segments.HEIGHT + 4, 11, ui.c("map.bg"));
        Segments.render(ui, sx, sy, items, 0, index -> {
            if (index == 1) {
                BookState.view = ViewMode.LIST;
            }
        });
        tools.add(new float[] {sx, sy, segW, Segments.HEIGHT});
        if (state == null || !state.ready()) {
            return;
        }
        drawCrumbs(ui, sx + segW + TB_GAP, sy + (Segments.HEIGHT - TB) / 2f, x + w - (TB * 3 + TB_GAP * 2 + PAD * 2) - (sx + segW + TB_GAP), compact);
    }

    /** "Вся книга › II Индустрия › Ток": from the epoch level, each part pressable; squeezed in order when room is short. */
    private void drawCrumbs(Ui ui, float x, float y, float room, boolean compact) {
        MapCanvas.Level level = canvas.level();
        if (level == MapCanvas.Level.BOOK) {
            return;
        }
        int epoch = focusEpoch;
        ResourceLocation chapter = level.ordinal() >= MapCanvas.Level.CHAPTER.ordinal() ? openChapter : null;
        if (chapter != null) {
            epoch = chapterEpoch(chapter);
        }
        if (epoch == 0 && chapter == null) {
            return;
        }
        boolean galaxy = galaxy();
        String whole = Component.translatable("rysquests.sidebar.whole_book").getString();
        String epochFull = epoch == 0 ? "" : epochName(epoch, galaxy, false);
        String epochShort = epoch == 0 ? "" : epochName(epoch, galaxy, true);
        String chapterTitle = chapter == null ? "" : chapterTitle(chapter);
        float px = 12;
        float sep = 6 + 11 + 6;
        boolean iconWhole = compact;
        boolean shortEpoch = compact;
        float needed = needed(ui, whole, iconWhole, shortEpoch ? epochShort : epochFull, chapterTitle, sep, px);
        if (needed > room && !shortEpoch) {
            shortEpoch = true;
            needed = needed(ui, whole, iconWhole, epochShort, chapterTitle, sep, px);
        }
        if (needed > room && !iconWhole) {
            iconWhole = true;
            needed = needed(ui, whole, true, shortEpoch ? epochShort : epochFull, chapterTitle, sep, px);
        }
        String epochText = shortEpoch ? epochShort : epochFull;
        float chapterRoom = Float.MAX_VALUE;
        if (needed > room && !chapterTitle.isEmpty()) {
            chapterRoom = Math.max(30, ui.width(chapterTitle, 12, Ui.SEMIBOLD) - (needed - room));
        }
        float w = Math.min(needed, room);
        ui.box(x, y, w, TB, 9, ui.c("button.bg"), ui.c("button.border"));
        tools.add(new float[] {x, y, w, TB});
        float pen = x + 11;
        float cy = y + TB / 2f;
        // the whole book
        float ww = iconWhole ? 13 : ui.width(whole, 12, Ui.MEDIUM);
        boolean overWhole = ui.over(pen - 4, y, ww + 8, TB);
        if (iconWhole) {
            ui.icon(Icons.Shape.COMPASS, pen, cy - 6.5f, 13, ui.c(overWhole ? "text.primary" : "text.secondary"));
            ui.tooltipIfOver(pen - 4, y, ww + 8, TB, Component.literal(whole));
        } else {
            ui.label(whole, pen, cy, 12, Ui.MEDIUM, ui.c(overWhole ? "text.primary" : "text.secondary"));
        }
        ui.hit(pen - 4, y, ww + 8, TB, this::flyToBook);
        pen += ww + 6;
        if (!epochText.isEmpty()) {
            ui.icon(Icons.Shape.CHEVRON_RIGHT, pen, cy - 5.5f, 11, ui.c("text.muted"));
            pen += 11 + 6;
            float ew = ui.width(epochText, 12, chapter == null ? Ui.SEMIBOLD : Ui.MEDIUM);
            int epochNumber = epoch;
            if (chapter == null) {
                ui.label(epochText, pen, cy, 12, Ui.SEMIBOLD, ui.c("text.primary"));
            } else {
                boolean over = ui.over(pen - 3, y, ew + 6, TB);
                ui.label(epochText, pen, cy, 12, Ui.MEDIUM, ui.c(over ? "text.primary" : "text.secondary"));
                ui.hit(pen - 3, y, ew + 6, TB, () -> flyToEpoch(epochNumber));
                if (shortEpoch) {
                    ui.tooltipIfOver(pen - 3, y, ew + 6, TB, Component.literal(epochFull));
                }
            }
            pen += ew + 6;
        }
        if (!chapterTitle.isEmpty()) {
            ui.icon(Icons.Shape.CHEVRON_RIGHT, pen, cy - 5.5f, 11, ui.c("text.muted"));
            pen += 11 + 6;
            if (chapterRoom == Float.MAX_VALUE) {
                ui.label(chapterTitle, pen, cy, 12, Ui.SEMIBOLD, ui.c("text.primary"));
            } else {
                ui.labelFitTip(chapterTitle, pen, cy, chapterRoom, 12, Ui.SEMIBOLD, ui.c("text.primary"));
            }
        }
    }

    private static float needed(Ui ui, String whole, boolean iconWhole, String epoch, String chapter, float sep, float pad) {
        float w = pad * 2 - 1 + (iconWhole ? 13 : ui.width(whole, 12, Ui.MEDIUM));
        if (!epoch.isEmpty()) {
            w += sep + ui.width(epoch, 12, chapter.isEmpty() ? Ui.SEMIBOLD : Ui.MEDIUM);
        }
        if (!chapter.isEmpty()) {
            w += sep + ui.width(chapter, 12, Ui.SEMIBOLD);
        }
        return w;
    }

    private void drawTopRight(Ui ui, int right, int y) {
        float ty = y + PAD;
        float ix = right - PAD - TB * 3 - TB_GAP * 2;
        // the group stands on a plate of the canvas colour: nodes and labels go under it
        ui.fill(ix - 2, ty - 2, TB * 3 + TB_GAP * 2 + 4, TB + 4, 11, ui.c("map.bg"));
        toolButton(ui, right - PAD - TB, ty, Icons.Shape.PLUS, false, Component.translatable("rysquests.mapview.zoom_in"), () -> canvas.zoomBy(1.6));
        toolButton(ui, right - PAD - TB * 2 - TB_GAP, ty, Icons.Shape.MINUS, false, Component.translatable("rysquests.mapview.zoom_out"), () -> canvas.zoomBy(1 / 1.6));
        boolean over = ui.over(ix, ty, TB, TB);
        ui.box(ix, ty, TB, TB, 9, over ? ui.mix("button.bg", "text.primary", 0.08f) : ui.c("button.bg"), ui.c(legendOpen ? "text.secondary" : "button.border"));
        works.repka.ryscore.ui.Draw.info(ui.g, Math.round((ix + 5) / 2), Math.round((ty + 5) / 2), ui.c(legendOpen || over ? "text.primary" : "text.secondary"));
        ui.tooltipIfOver(ix, ty, TB, TB, Component.translatable("rysquests.mapview.legend_tip"));
        ui.hit(ix, ty, TB, TB, () -> legendOpen = !legendOpen);
        tools.add(new float[] {ix, ty, TB, TB});
    }

    private void toolButton(Ui ui, float x, float y, Icons.Shape icon, boolean on, Component tip, Runnable action) {
        boolean over = ui.over(x, y, TB, TB);
        ui.box(x, y, TB, TB, 9, over ? ui.mix("button.bg", "text.primary", 0.08f) : ui.c("button.bg"), ui.c(on ? "text.secondary" : "button.border"));
        ui.icon(icon, x + 8, y + 8, 14, ui.c(over || on ? "text.primary" : "text.secondary"));
        ui.tooltipIfOver(x, y, TB, TB, tip);
        ui.hit(x, y, TB, TB, action);
        tools.add(new float[] {x, y, TB, TB});
    }

    private void drawBottom(Ui ui, int x, int y, int w, int h, boolean compact, MapCanvas.Level level) {
        // "К моему квесту"
        String mine = Component.translatable("rysquests.mapview.mine").getString();
        float mw = 10 + 14 + 6 + ui.width(mine, 12, Ui.MEDIUM) + 10;
        float my = y + h - PAD - TB;
        float mx = x + PAD;
        boolean over = ui.over(mx, my, mw, TB);
        ui.fill(mx - 2, my - 2, mw + 4, TB + 4, 11, ui.c("map.bg"));
        ui.box(mx, my, mw, TB, 9, over ? ui.mix("button.bg", "text.primary", 0.08f) : ui.c("button.bg"), ui.c("button.border"));
        ui.icon(Icons.Shape.TARGET, mx + 10, my + 8, 14, ui.c(over ? "text.primary" : "text.secondary"));
        ui.label(mine, mx + 30, my + TB / 2f, 12, Ui.MEDIUM, ui.c(over ? "text.primary" : "text.secondary"));
        ui.hit(mx, my, mw, TB, () -> {
            if (here != null) {
                Quest quest = BookData.book().quests().get(here);
                openChapter = quest == null ? null : quest.chapter().orElse(null);
                focusEpoch = openChapter == null ? 0 : chapterEpoch(openChapter);
                canvas.flyToQuest(here);
            }
        });
        tools.add(new float[] {mx, my, mw, TB});
        // the legend line on the whole book; closer in its corner is the minimap's
        // the compact layout keeps the legend and the side quests' switch in the help only (picture 46)
        if (compact) {
            return;
        }
        if (level == MapCanvas.Level.BOOK) {
            drawLegendLine(ui, x + w - PAD, y + h - PAD - TB);
        } else {
            tools.add(new float[] {x + w - 12 - 172, y + h - 12 - 104, 172, 104});
        }
    }

    /** One line: done, can do, locked, milestone; and while side quests are hidden, "побочные скрыты · показать". */
    private void drawLegendLine(Ui ui, float right, float y) {
        String[] words = {tr("rysquests.mapview.legend.done"), tr("rysquests.mapview.legend.open"), tr("rysquests.mapview.legend.locked"), tr("rysquests.mapview.legend.milestone")};
        float w = 12;
        for (String word : words) {
            w += 12 + 5 + ui.width(word, 11.5f, Ui.REGULAR) + 12;
        }
        boolean hidden = !canvas.showSide();
        String hiddenText = tr("rysquests.mapview.legend.side_hidden");
        String show = tr("rysquests.mapview.legend.show");
        float hiddenW = hidden ? 12 + ui.width(hiddenText, 11.5f, Ui.REGULAR) + 8 + ui.width(show, 11.5f, Ui.MEDIUM) + 12 : 0;
        w += hiddenW;
        float x = right - w;
        ui.box(x, y, w, TB, 9, ui.c("sidebar.bg"), ui.c("panel.border"));
        tools.add(new float[] {x, y, w, TB});
        float pen = x + 12;
        float cy = y + TB / 2f;
        for (int i = 0; i < words.length; i++) {
            legendSample(ui, i, pen + 6, cy, 1f);
            pen += 12 + 5;
            ui.label(words[i], pen, cy, 11.5f, Ui.REGULAR, ui.c("text.secondary"));
            pen += ui.width(words[i], 11.5f, Ui.REGULAR) + 12;
        }
        if (hidden) {
            ui.rect(pen, y + 1, 1, TB - 2, ui.c("panel.border"));
            pen += 12;
            ui.label(hiddenText, pen, cy, 11.5f, Ui.REGULAR, ui.c("text.primary"));
            pen += ui.width(hiddenText, 11.5f, Ui.REGULAR) + 8;
            float sw = ui.width(show, 11.5f, Ui.MEDIUM);
            ui.label(show, pen, cy, 11.5f, Ui.MEDIUM, ui.c("link"));
            ui.hit(pen - 2, y, sw + 4, TB, () -> canvas.setShowSide(true));
        }
    }

    /** The node samples of the legend: 0 done, 1 can do, 2 locked, 3 milestone, 4 side quest, 5 you are here. */
    private static void legendSample(Ui ui, int kind, float cx, float cy, float k) {
        switch (kind) {
            case 0 -> ui.circle(cx - 4 * k, cy - 4 * k, 8 * k, ui.c("map.node.done"));
            case 1 -> {
                ui.circle(cx - 3.6f * k, cy - 3.6f * k, 7.2f * k, ui.c("map.node.open"));
                ui.circle(cx - 2.2f * k, cy - 2.2f * k, 4.4f * k, ui.c("slot.bg"));
            }
            case 2 -> {
                ui.circle(cx - 3.6f * k, cy - 3.6f * k, 7.2f * k, ui.c("map.node.locked"));
                ui.circle(cx - 2.6f * k, cy - 2.6f * k, 5.2f * k, ui.c("slot.border"));
            }
            case 3 -> {
                ui.ring(cx - 6 * k, cy - 6 * k, 12 * k, 1, ui.c("map.node.locked"));
                ui.circle(cx - 3 * k, cy - 3 * k, 6 * k, ui.c("map.node.locked"));
                ui.circle(cx - 2 * k, cy - 2 * k, 4 * k, ui.c("slot.border"));
            }
            case 4 -> ui.circle(cx - 2.5f * k, cy - 2.5f * k, 5 * k, ui.c("scroll.thumb"));
            default -> {
                ui.ring(cx - 7.5f * k, cy - 7.5f * k, 15 * k, 2, ui.c("map.node.open"));
                ui.circle(cx - 4 * k, cy - 4 * k, 8 * k, ui.c("map.node.open"));
                ui.circle(cx - 3 * k, cy - 3 * k, 6 * k, ui.c("slot.bg"));
            }
        }
    }

    /** "Как читать карту" (picture 18): what each mark means, and the switch of side quests with the words of what it hides. */
    private void drawLegend(Ui ui, int right, int y) {
        int w = LEGEND_W;
        String[] rows = {tr("rysquests.mapview.legend.done"), tr("rysquests.mapview.legend.open_long"), tr("rysquests.mapview.legend.locked_long"),
                tr("rysquests.mapview.legend.milestone_long"), tr("rysquests.mapview.legend.side_long"), tr("rysquests.canvas.here")};
        String note = tr("rysquests.mapview.legend.note");
        List<String> noteLines = ui.wrap(note, w - 28, 11.5f, Ui.REGULAR, 4);
        int h = 12 + 20 + 8 + rows.length * 26 + 8 + 30 + 4 + noteLines.size() * 16 + 12;
        int x = right - PAD - w;
        int top = y + 50;
        int[] box = Kit.popover(ui.g, ui.theme, x / 2, top / 2, 1, 1, w / 2, h / 2, Kit.Side.BELOW, null, (g, bx, by, bw, bh) -> {
        });
        float px = box[0] * 2f;
        float py = box[1] * 2f;
        overPopover(ui, () -> {
            float pen = py + 12;
            ui.label(tr("rysquests.mapview.legend.title"), px + 14, pen + 10, 14, Ui.DISPLAY, ui.c("text.primary"));
            pen += 20 + 8;
            for (int i = 0; i < rows.length; i++) {
                legendSample(ui, i, px + 14 + 9, pen + 13, 1.25f);
                ui.label(rows[i], px + 14 + 28, pen + 13, 12, Ui.REGULAR, ui.c("text.secondary"));
                pen += 26;
            }
            pen += 8;
            ui.rect(px + 14, pen, w - 28, 1, ui.c("panel.border"));
            String toggle = tr("rysquests.mapview.legend.side_toggle");
            ui.label(toggle, px + 14, pen + 19, 12.5f, Ui.REGULAR, ui.c("text.body"));
            float tx = px + w - 14 - 36;
            boolean hot = ui.over(tx, pen + 9, 36, 20);
            Kit.toggle(ui.g, ui.theme, Math.round(tx / 2), Math.round((pen + 9) / 2), canvas.showSide(), hot);
            ui.hit(tx - 4, pen + 5, 44, 28, () -> canvas.setShowSide(!canvas.showSide()));
            pen += 30 + 4;
            for (String line : noteLines) {
                ui.label(line, px + 14, pen + 8, 11.5f, Ui.REGULAR, ui.c("text.muted"));
                pen += 16;
            }
        });
        tools.add(new float[] {px, py, w, h});
    }

    /** The popover of a hovered chapter (picture 15): name, epoch, main and side counts, and why it is locked. */
    private void drawChapterPopover(Ui ui, int x, int y, int w, int h) {
        MapCanvas.Target hover = canvas.hovered();
        // on the whole book and the epoch a node is too small to mean itself: the pointer is over its chapter
        if (hover.kind() != MapCanvas.Kind.CHAPTER && hover.kind() != MapCanvas.Kind.QUEST || hover.chapter() == null || canvas.level().ordinal() > MapCanvas.Level.EPOCH.ordinal()) {
            return;
        }
        for (float[] tool : tools) {
            if (ui.mouseX >= tool[0] && ui.mouseX < tool[0] + tool[2] && ui.mouseY >= tool[1] && ui.mouseY < tool[1] + tool[3]) {
                return;
            }
        }
        Chapter chapter = BookData.book().chapters().get(hover.chapter());
        float[] area = canvas.chapterOnScreen(hover.chapter());
        if (chapter == null || area == null) {
            return;
        }
        BookTallies.Tally tally = BookTallies.chapter(chapter.id());
        String title = Component.translatable(chapter.title()).getString();
        int epoch = chapter.epoch().orElse(0);
        String sub = epoch == 0 ? "" : epochName(epoch, galaxy(), false);
        List<String> need = tally.state() == BookTallies.State.LOCKED ? neededChapters(chapter.id(), 2) : List.of();
        // a chapter's name in running text is always in quotes (plan, section 1)
        String needText = need.isEmpty() ? "" : Component.translatable("rysquests.mapview.closed_need",
                String.join(", ", need.stream().map(name -> "«" + name.replace(" ✓", "") + "»").toList())).getString();
        List<String> needLines = needText.isEmpty() ? List.of() : ui.wrap(needText, POP_W - 24, 11.5f, Ui.REGULAR, 3);
        int ph = 10 + 20 + (sub.isEmpty() ? 0 : 16) + 6 + 18 + 6 + (tally.optionalTotal() > 0 ? 18 + 6 : 0) + needLines.size() * 16 + 10;
        // beside the chapter's area, inside the canvas
        int ax = Math.round(Math.max(x, area[0]));
        int ay = Math.round(Math.max(y, area[1]));
        int aw = Math.round(Math.min(x + w, area[0] + area[2]) - ax);
        int ah = Math.round(Math.min(y + h, area[1] + area[3]) - ay);
        int[] bounds = {x / 2, y / 2, w / 2, h / 2};
        int[] box = Kit.popover(ui.g, ui.theme, ax / 2, Math.max(ay, ui.mouseY - 60) / 2, Math.max(1, aw) / 2, 1, POP_W / 2, ph / 2, Kit.Side.RIGHT, bounds, (g, bx, by, bw, bh) -> {
        });
        float px = box[0] * 2f;
        float py = box[1] * 2f;
        overPopover(ui, () -> {
            float pen = py + 10;
            ui.labelFit(title, px + 12, pen + 10, POP_W - 24, 14.5f, Ui.DISPLAY, ui.c("text.primary"));
            pen += 20;
            if (!sub.isEmpty()) {
                ui.label(sub, px + 12, pen + 6, 11.5f, Ui.REGULAR, ui.c("text.muted"));
                pen += 16;
            }
            pen += 6;
            pen = popRow(ui, px, pen, tr("rysquests.mapview.main"), tally.done(), tally.total(), true) + 6;
            if (tally.optionalTotal() > 0) {
                pen = popRow(ui, px, pen, tr("rysquests.mapview.side"), tally.optionalDone(), tally.optionalTotal(), false) + 6;
            }
            for (String line : needLines) {
                ui.label(line, px + 12, pen + 8, 11.5f, Ui.REGULAR, ui.c("text.secondary"));
                pen += 16;
            }
        });
    }

    /** Draws above the popover frame the core puts high over the window. */
    private static void overPopover(Ui ui, Runnable drawing) {
        ui.g.pose().pushPose();
        ui.g.pose().translate(0, 0, 450);
        drawing.run();
        ui.g.pose().popPose();
    }

    private float popRow(Ui ui, float px, float y, String label, int done, int total, boolean main) {
        float cy = y + 9;
        ui.label(label, px + 12, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        float bx = px + 12 + 64 + 8;
        float bw = POP_W - 24 - 64 - 8 - 60 - 8;
        ui.bar(bx, cy - 2, bw, 4, 2, total == 0 ? 0 : done / (float) total, main ? ui.c("state.ok") : ui.c("text.muted"));
        ui.labelRight(Component.translatable("rysquests.count.of", Sidebar.full(done), Sidebar.full(total)).getString(), px + POP_W - 12, cy, 11.5f, Ui.MEDIUM, ui.c("text.secondary"));
        return y + 18;
    }

    // --- the panel on the right ---

    /**
     * The one place the quest panel plugs in: the {@link QuestPanel} of the list and "Now" in its map look. Its cross closes back to
     * the chapter's card; the way to a closed quest it works out is the one the canvas lights.
     */
    private void questPanel(Ui ui, Quest quest, int x, int y, int w, int h) {
        panelFor = quest.id();
        questView.render(ui, x, y, w, h, quest, QuestPanel.Look.MAP);
    }

    private void closeCross(Ui ui, float x, float y, Runnable action) {
        boolean over = ui.over(x, y, 28, 28);
        ui.fill(x, y, 28, 28, 7, over ? ui.c("slot.hover") : ui.c("panel.bg"));
        ui.icon(Icons.Shape.CROSS, x + 7, y + 7, 14, ui.c(over ? "text.primary" : "text.muted"));
        ui.hit(x, y, 28, 28, action);
    }

    /** The chapter's card on the chapter level (picture 16): counts, milestones, links, the list link and "Продолжить: …". */
    private void chapterCard(Ui ui, ResourceLocation chapterId, int x, int y, int w, int h) {
        QuestBook book = BookData.book();
        Chapter chapter = book.chapters().get(chapterId);
        if (chapter == null) {
            return;
        }
        if (!chapterId.equals(panelFor)) {
            panel.reset();
            panelFor = chapterId;
        }
        BookTallies.Tally tally = BookTallies.chapter(chapterId);
        Quest next = nextQuest(book, chapterId);
        DetailPanel.Action action = next == null ? null : DetailPanel.Action.button(Component.translatable("rysquests.mapview.card.continue", Component.translatable(next.title())).getString(), () -> flyToQuest(next.id()));
        panel.render(ui, x, y, w, h, (u, bx, by, bw) -> {
            float pen = by;
            int epoch = chapter.epoch().orElse(0);
            String rubric = Component.translatable("rysquests.mapview.card.rubric", String.format("%02d", map == null ? 0 : map.chapter(chapterId) + 1), epoch == 0 ? "" : epochName(epoch, galaxy(), false)).getString();
            pen += DetailPanel.rubric(u, bx, pen, bw - 30, epoch == 0 ? rubric.replace(" · ", "") : rubric, null) + 10;
            ItemStack icon = chapter.icon().map(TaskText::stackOf).orElse(null);
            pen += DetailPanel.header(u, bx, pen, bw, icon, Component.translatable(chapter.title()).getString(), chapter.subtitle().map(s -> Component.translatable(s).getString()).orElse(null), null, false, null);
            pen += 16;
            pen = countRow(u, bx, pen, bw, tr("rysquests.mapview.main"), tally.done(), tally.total(), u.c("state.ok"));
            if (tally.optionalTotal() > 0) {
                pen = countRow(u, bx, pen + 4, bw, tr("rysquests.mapview.side"), tally.optionalDone(), tally.optionalTotal(), u.c("text.muted"));
            }
            List<Quest> milestones = milestones(book, chapterId);
            if (!milestones.isEmpty()) {
                pen += DetailPanel.GAP + 4;
                long done = milestones.stream().filter(q -> status(q.id()) == QuestStatus.COMPLETED).count();
                pen += DetailPanel.rubric(u, bx, pen, bw, tr("rysquests.mapview.card.milestones"), Component.translatable("rysquests.count.of", done, milestones.size()).getString()) + 4;
                for (Quest quest : milestones) {
                    QuestStatus st = status(quest.id());
                    ListRow row = ListRow.of(Component.translatable(quest.title()).getString()).icon(questIcon(quest)).look(ListRow.Look.FLAT).locked(st == QuestStatus.LOCKED)
                            .onClick(() -> flyToQuest(quest.id()));
                    row = st == QuestStatus.COMPLETED ? row.right(Icons.Shape.CHECK, "", "state.ok") : st == QuestStatus.LOCKED ? row.right(tr("rysquests.mapview.card.locked")) : row.right(tr("rysquests.mapview.card.can_do"));
                    pen += row.render(u, bx - 8, pen, bw + 16);
                }
            }
            List<String> needs = neededChapters(chapterId, 2);
            List<String> opens = openedChapters(chapterId, 3);
            if (!needs.isEmpty() || !opens.isEmpty()) {
                pen += DetailPanel.GAP + 4;
                pen += DetailPanel.rubric(u, bx, pen, bw, tr("rysquests.mapview.card.links"), null) + 6;
                if (!needs.isEmpty()) {
                    pen = linkRow(u, bx, pen, bw, tr("rysquests.mapview.card.needs"), String.join(", ", needs));
                }
                if (!opens.isEmpty()) {
                    pen = linkRow(u, bx, pen + 2, bw, tr("rysquests.mapview.card.opens"), String.join(", ", opens));
                }
            }
            pen += DetailPanel.GAP + 6;
            String asList = tr("rysquests.mapview.card.as_list");
            float lw = u.width(asList, 12.5f, Ui.MEDIUM);
            boolean over = u.over(bx, pen, lw, 18);
            u.label(asList, bx, pen + 9, 12.5f, Ui.MEDIUM, over ? u.mix("link", "text.primary", 0.3f) : u.c("link"));
            u.hit(bx, pen, lw, 18, () -> {
                BookState.openChapter(chapterId);
                BookState.view = ViewMode.LIST;
            });
            pen += 18;
            return pen - by;
        }, action);
        closeCross(ui, x + w - 18 - 22, y + 10, () -> panelClosed = true);
    }

    private static float countRow(Ui ui, float x, float y, float w, String label, int done, int total, int color) {
        float cy = y + 9;
        ui.label(label, x, cy, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
        ui.labelRight(Component.translatable("rysquests.count.of", Sidebar.full(done), Sidebar.full(total)).getString(), x + w, cy, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.bar(x, y + 22, w, 6, 3, total == 0 ? 0 : done / (float) total, color);
        return y + 30;
    }

    private static float linkRow(Ui ui, float x, float y, float w, String label, String value) {
        List<String> lines = ui.wrap(value, w - 74, 12.5f, Ui.MEDIUM, 3);
        ui.label(label, x, y + 9, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        float pen = y;
        for (String line : lines) {
            ui.label(line, x + 74, pen + 9, 12.5f, Ui.MEDIUM, ui.c("text.primary"));
            pen += 18;
        }
        return pen;
    }

    // ================= input =================

    @Override
    public void press(double x, double y, int button) {
        for (float[] tool : tools) {
            if (x >= tool[0] && x < tool[0] + tool[2] && y >= tool[1] && y < tool[1] + tool[3]) {
                pressedOnCanvas = false;
                return;
            }
        }
        if (legendOpen) {
            legendOpen = false;
        }
        if (canvas.clickLoading(x, y)) {
            pressedOnCanvas = false;
            return;
        }
        pressedOnCanvas = canvas.mouseClicked(x, y, button);
        pointerX = x;
        pointerY = y;
    }

    @Override
    public boolean drag(double deltaX, double deltaY) {
        if (!pressedOnCanvas) {
            return false;
        }
        pointerX += deltaX;
        pointerY += deltaY;
        return canvas.mouseDragged(pointerX, pointerY, deltaX, deltaY);
    }

    @Override
    public boolean release(double x, double y, int button) {
        if (!pressedOnCanvas) {
            return false;
        }
        pressedOnCanvas = false;
        return canvas.mouseReleased(x, y, button);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (questShown && questView.contains(mouseX, mouseY) && inTools(mouseX, mouseY)) {
            questView.scroll(amount);
        } else if (!questShown && panel.contains(mouseX, mouseY) && inTools(mouseX, mouseY)) {
            panel.scroll(amount);
        } else {
            canvas.mouseScrolled(mouseX, mouseY, amount);
        }
    }

    private boolean inTools(double x, double y) {
        for (float[] tool : tools) {
            if (x >= tool[0] && x < tool[0] + tool[2] && y >= tool[1] && y < tool[1] + tool[3]) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean key(int key) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD) {
            canvas.zoomBy(1.6);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT) {
            canvas.zoomBy(1 / 1.6);
            return true;
        }
        return false;
    }

    private void onCanvasClick(MapCanvas.Target target, int button) {
        if (button != 0) {
            if (button == 1 && target.kind() == MapCanvas.Kind.QUEST && target.quest() != null) {
                BookData.togglePin(target.quest());
            }
            return;
        }
        switch (target.kind()) {
            case QUEST -> {
                Quest quest = BookData.book().quests().get(target.quest());
                if (quest != null) {
                    BookState.select(quest);
                    openChapter = target.chapter();
                    focusEpoch = target.epoch();
                    panelClosed = false;
                }
            }
            case CHAPTER -> {
                if (canvas.level().ordinal() < MapCanvas.Level.CHAPTER.ordinal() || !target.chapter().equals(openChapter)) {
                    flyToChapter(target.chapter());
                } else {
                    BookState.quest = null;
                    panelClosed = false;
                }
            }
            case EPOCH -> {
                openChapter = null;
                focusEpoch = target.epoch();
            }
            default -> BookState.quest = null;
        }
    }

    @Nullable
    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        // the middle column: left of the panel, over the canvas
        return middle;
    }

    // ================= data =================

    static QuestStatus status(ResourceLocation id) {
        Quest quest = BookData.book().quests().get(id);
        return quest == null ? QuestStatus.LOCKED : BookState.status(quest);
    }

    /**
     * "You are here", the next step by the plan's one rule: the nearest unfinished main quest of the chapter where the player last
     * completed a quest; else the first unfinished main quest of the first unfinished chapter.
     */
    private void refreshHere(QuestBook book) {
        int version = ClientProgress.version();
        if (version == hereVersion && hereBook == book) {
            return;
        }
        hereVersion = version;
        hereBook = book;
        here = null;
        if (map == null) {
            return;
        }
        Optional<ResourceLocation> last = ClientProgress.lastChapter().filter(id -> map.chapter(id) >= 0);
        if (last.isPresent()) {
            here = nextMain(book, last.get());
        }
        if (here == null) {
            for (MapBook.Chap chapter : map.chapters()) {
                here = nextMain(book, chapter.id());
                if (here != null) {
                    break;
                }
            }
        }
        canvas.setHere(here);
    }

    /** The first main quest of the chapter not done yet, one that can be done before one that cannot. */
    @Nullable
    private static ResourceLocation nextMain(QuestBook book, ResourceLocation chapter) {
        ResourceLocation fallback = null;
        for (Quest quest : ChapterOrder.of(book, chapter)) {
            if (quest.options().optional() || !quest.options().live()) {
                continue;
            }
            QuestStatus st = status(quest.id());
            if (st == QuestStatus.COMPLETED) {
                continue;
            }
            if (st != QuestStatus.LOCKED) {
                return quest.id();
            }
            if (fallback == null) {
                fallback = quest.id();
            }
        }
        return fallback;
    }

    @Nullable
    private static Quest nextQuest(QuestBook book, ResourceLocation chapter) {
        ResourceLocation id = nextMain(book, chapter);
        return id == null ? null : book.quests().get(id);
    }

    private static List<Quest> milestones(QuestBook book, ResourceLocation chapter) {
        return ChapterOrder.of(book, chapter).stream().filter(q -> q.options().milestone() && !q.options().optional() && q.options().live()).toList();
    }

    /** Chapters this one needs, most links first, finished ones with the check left to the caller's words. */
    private List<String> neededChapters(ResourceLocation chapter, int limit) {
        return crossChapters(chapter, true, limit);
    }

    private List<String> openedChapters(ResourceLocation chapter, int limit) {
        return crossChapters(chapter, false, limit);
    }

    private List<String> crossChapters(ResourceLocation chapter, boolean needs, int limit) {
        QuestBook book = BookData.book();
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        for (Quest quest : book.quests().values()) {
            if (!quest.options().live() || quest.chapter().isEmpty()) {
                continue;
            }
            for (ResourceLocation r : quest.requires()) {
                Quest other = book.quests().get(r);
                if (other == null || other.chapter().isEmpty()) {
                    continue;
                }
                ResourceLocation from = other.chapter().get();
                ResourceLocation to = quest.chapter().get();
                if (from.equals(to)) {
                    continue;
                }
                if (needs && to.equals(chapter)) {
                    counts.merge(from, 1, Integer::sum);
                } else if (!needs && from.equals(chapter)) {
                    counts.merge(to, 1, Integer::sum);
                }
            }
        }
        Map<ResourceLocation, Integer> order = new HashMap<>();
        List<ResourceLocation> all = new ArrayList<>(book.chapters().keySet());
        for (int i = 0; i < all.size(); i++) {
            order.put(all.get(i), i);
        }
        return counts.entrySet().stream()
                .filter(e -> map == null || map.chapter(e.getKey()) >= 0)
                .filter(e -> needs || order.getOrDefault(e.getKey(), 0) > order.getOrDefault(chapter, 0))
                .sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed().thenComparing(e -> order.getOrDefault(e.getKey(), 0)))
                .limit(limit)
                .map(e -> chapterTitle(e.getKey()) + (BookTallies.chapter(e.getKey()).state() == BookTallies.State.DONE ? " ✓" : ""))
                .toList();
    }

    private boolean galaxy() {
        return canvas.layout() != null && canvas.layout().form == MapForm.GALAXY;
    }

    private static String chapterTitle(ResourceLocation id) {
        Chapter chapter = BookData.book().chapters().get(id);
        return chapter == null ? id.getPath() : Component.translatable(chapter.title()).getString();
    }

    /** "II Индустрия", or the numeral alone when short; a galaxy's sections have names only. */
    private static String epochName(int epoch, boolean galaxy, boolean shortForm) {
        String title = BookData.book().meta().epoch(epoch).map(BookMeta.Epoch::title).map(t -> Component.translatable(t).getString()).orElse("");
        if (galaxy) {
            return title.isEmpty() ? Ui.roman(epoch) : title;
        }
        return shortForm || title.isEmpty() ? Ui.roman(epoch) : Ui.roman(epoch) + " " + title;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    // ================= for tests =================

    String stateForTest() {
        return canvas.level() + " " + canvas.stats() + " chapter " + openChapter + " epoch " + focusEpoch + " quest " + BookState.quest + " here " + here;
    }

    void zoomForTest(float scale) {
        canvas.zoomBy(scale / canvas.scale());
    }

    @Nullable
    double[] nodeForTest(ResourceLocation quest) {
        float[] at = canvas.questOnScreen(quest);
        return at == null ? null : new double[] {at[0] / 2 - 6, at[1] / 2 - 6, 12, 12};
    }

    void legendForTest(boolean open) {
        legendOpen = open;
    }

    @Nullable
    float[] chapterForTest(ResourceLocation chapter) {
        return canvas.chapterOnScreen(chapter);
    }

    @Nullable
    ResourceLocation hereForTest() {
        return here;
    }

    private static ItemStack questIcon(Quest quest) {
        return quest.icon().map(TaskText::stackOf).orElseGet(() -> quest.tasks().isEmpty() ? ItemStack.EMPTY : TaskText.icon(quest.tasks().getFirst()));
    }
}
