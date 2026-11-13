package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Field;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.ScaledScreen;

/**
 * The quest book window (plan, sections 2 and 7): one frame for everything. The {@link BookHeader} on top, under it the body: the
 * open tab's view ({@link BookTabs}), a page opened over it ({@link #openPage}), or one of the book's own states, loading
 * ({@link LoadingView}) and empty ({@link EmptyBookView}). Over the body lie the closed-book line for operators
 * ({@link ClosedBookLine}), the message line ({@link BookMessageLine}) and a question ({@link #ask}).
 *
 * <p>The screen keeps no page logic of its own: views come from {@link BookTabs}, numbers from {@link BookTallies}, lookups from
 * {@link BookData}, and the old full-window editors from {@link BookEditors}. Small screens get the family's compact base 480×270.
 */
public final class BookScreen extends ScaledScreen implements BookHeader.Host {
    private static final float DIM = 0.6f;
    private static final float WINDOW_W = 0.9375f;
    private static final float WINDOW_H = 0.91f;
    private static final int WINDOW_MAX_W = 1760;
    private static final int WINDOW_MAX_H = 1040;
    @Nullable
    private static ItemStack lastHovered;
    @Nullable
    private static String lastHoveredText;
    @Nullable
    private static String lastEditorResult;
    @Nullable
    private static String lastShopResult;
    /** The first opening of a game session is on "Now", whatever the setting says (picture 32). */
    private static boolean openedBefore;

    private final List<Ui.Hit> hits = new ArrayList<>();
    private final List<Ui.Hit> rightHits = new ArrayList<>();
    private final Kit.Hits kitHits = new Kit.Hits();
    private final Map<String, BookView> views = new HashMap<>();
    private final LoadingView loading = new LoadingView();
    private final EmptyBookView empty = new EmptyBookView(this);
    final BookEditors editors = new BookEditors(this);
    final EditorPages editor = new EditorPages(this);
    @Nullable
    private BookView page;
    @Nullable
    private Kit.Dialog dialog;
    @Nullable
    private Field searchBox;
    @Nullable
    private Field sectionSearch;
    @Nullable
    private BookHeader header;
    private int windowW;
    @Nullable
    private BookView shown;

    public BookScreen() {
        super(Component.translatable("rysquests.book.title"));
        useCompactBase();
        if (!openedBefore || !ClientConfig.OPEN_WHERE_CLOSED.get()) {
            BookTabs.byId(BookTabs.NOW).open().run();
        }
        openedBefore = true;
    }

    /** Opens the book: the K key, the button of the family's panel, the FTB sidebar button. */
    public static void open() {
        Minecraft.getInstance().setScreen(new BookScreen());
    }

    /** What is under the cursor, for the recipe viewer's keys. Empty when the book is closed. */
    public static ItemStack hoveredStack() {
        return lastHovered == null || !(Minecraft.getInstance().screen instanceof BookScreen) ? ItemStack.EMPTY : lastHovered;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new Field(80, 10);
        searchBox.setHint(Component.translatable("rysquests.header.search_hint"));
        searchBox.setValue(BookState.search);
        searchBox.setResponder(value -> BookState.search = value);
        searchBox.visible = false;
        addWidget(searchBox);
        sectionSearch = new Field(80, 10);
        sectionSearch.setHint(Component.translatable("rysquests.guides.search"));
        sectionSearch.setValue(BookState.sectionSearch);
        sectionSearch.setResponder(value -> BookState.sectionSearch = value);
        sectionSearch.visible = false;
        addWidget(sectionSearch);
        header = new BookHeader(this, searchBox);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- pages, questions, views ---

    /** Opens a page over the open tab: the settings, a first-run step. "Back" and Esc close it. */
    void openPage(BookView view) {
        if (page != null) {
            page.closed();
        }
        page = view;
    }

    void closePage() {
        if (page != null) {
            page.closed();
            page = null;
        }
    }

    /** Asks a question over the book (picture 10). Wrap each answer in {@link #answer} so that it also closes the question. */
    void ask(Kit.Dialog question) {
        dialog = question;
    }

    Runnable answer(Runnable action) {
        return () -> {
            dialog = null;
            action.run();
        };
    }

    /** The view of a tab, made once per open book. */
    BookView view(String tab) {
        return views.computeIfAbsent(tab, id -> BookTabs.byId(id).view().apply(this));
    }

    GuidesTab guidesTab() {
        return (GuidesTab) view(BookTabs.GUIDES);
    }

    MapTab mapTab() {
        return (MapTab) view(BookTabs.MAP);
    }

    ShopView shopView() {
        return (ShopView) view(BookTabs.SHOP);
    }

    AchievementsView achievementsView() {
        return (AchievementsView) view(BookTabs.ACHIEVEMENTS);
    }

    /** The window's width in mockup pixels, for views that change their layout with it. */
    int windowWidth() {
        return windowW;
    }

    /** What the body shows this frame. */
    private BookView body() {
        if (!ClientBook.received()) {
            return loading;
        }
        if (page != null) {
            return page;
        }
        if (editor.on()) {
            return editor.view();
        }
        BookTabs.Tab tab = BookTabs.current(BookData.book());
        return tab == null ? empty : view(tab.id());
    }

    // --- the header's questions ---

    @Override
    public List<BookTabs.Tab> tabs() {
        return BookTabs.visible(BookData.book());
    }

    @Override
    public List<BookTabs.Tab> editorTabs() {
        return editor.tabs();
    }

    @Nullable
    @Override
    public BookTabs.Tab activeTab() {
        return page != null ? null : editor.on() ? editor.activeTab() : BookTabs.current(BookData.book());
    }

    @Override
    public void openTab(BookTabs.Tab tab) {
        closePage();
        tab.open().run();
    }

    @Override
    public boolean settingsOpen() {
        return page instanceof SettingsView;
    }

    @Override
    public void toggleSettings() {
        if (page instanceof SettingsView) {
            closePage();
        } else {
            BookTabs.Tab tab = BookTabs.current(BookData.book());
            openPage(new SettingsView(this, tab == null ? Component.translatable("rysquests.book.title").getString() : tab.label()));
        }
    }

    @Override
    public void toggleEditor() {
        BookTabs.Tab from = page == null && !editor.on() ? BookTabs.current(BookData.book()) : null;
        closePage();
        editor.toggle(from);
    }

    @Nullable
    @Override
    public EditorSession session() {
        BookView current = shown;
        return current instanceof EditorSession session ? session : null;
    }

    @Override
    public void focusSearch() {
        if (searchBox != null) {
            searchBox.visible = true;
            searchBox.setFocused(true);
            setFocused(searchBox);
        }
    }

    @Override
    public void close() {
        onClose();
    }

    // --- drawing ---

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The mockups dim the world and keep it sharp, without the game's blur.
        Draw.rect(g, 0, 0, width, height, Draw.alpha(works.repka.ryscore.theme.ThemeManager.get().c("overlay"), DIM));
    }

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        hits.clear();
        rightHits.clear();
        kitHits.clear();
        Ui ui = new Ui(g, mouseX, mouseY, hits, rightHits, kitHits);
        int screenW = width * 2;
        int screenH = height * 2;
        int w = Math.min(WINDOW_MAX_W, Math.round(screenW * WINDOW_W));
        int h = Math.min(WINDOW_MAX_H, Math.round(screenH * WINDOW_H));
        windowW = w;
        int x = (screenW - w) / 2;
        int y = (screenH - h) / 2;
        BookState.ensureChapter();
        if (sectionSearch != null) {
            sectionSearch.visible = false;
        }
        ui.shadow(x, y, w, h, 14, 1f);
        ui.box(x, y, w, h, 14, ui.c("panel.bg"), ui.c("panel.border"));
        drawBook(ui, x + 1, y + 1, w - 2, h - 2);
        settleFields();
        lastHovered = ui.hoveredStack;
        lastHoveredText = ui.hoveredText == null ? null : ui.hoveredText.getString();
        if (dialog != null) {
            Kit.dialog(g, ui.theme, kitHits, dialog, mouseX, mouseY);
        } else if (ui.hoveredStack != null) {
            g.renderTooltip(font, ui.hoveredStack, mouseX, mouseY);
        } else if (ui.hoveredText != null) {
            g.renderTooltip(font, ui.hoveredText, mouseX, mouseY);
        }
    }

    private void drawBook(Ui ui, int x, int y, int w, int h) {
        BookView body = body();
        shown = body;
        boolean bare = body == loading || body == empty || body.bareHeader();
        if (header != null) {
            header.render(ui, x, y, w, bare ? BookHeader.Mode.BARE : BookHeader.Mode.FULL, compact());
        }
        int top = y + BookHeader.HEIGHT;
        if (!bare && ClosedBookLine.shown()) {
            ClosedBookLine.render(ui, this, x, top, w);
            top += ClosedBookLine.HEIGHT;
        }
        int bodyH = y + h - top;
        body.render(ui, x, top, w, bodyH);
        int[] area = body.messageArea(x, top, w, bodyH);
        int[] line = area != null ? area : new int[] {x, top, w, bodyH};
        ui.aboveItems(() -> BookMessageLine.render(ui, line));
        if (body != loading && body != empty && body != page && !editor.on() && BookState.previewSection == null) {
            BookState.track();
        }
    }

    /** A text field that was not drawn this frame cannot keep the keyboard. */
    private void settleFields() {
        for (Field field : new Field[] {searchBox, sectionSearch}) {
            if (field != null && !field.visible && field.isFocused()) {
                field.setFocused(false);
                if (getFocused() == field) {
                    setFocused(null);
                }
            }
        }
    }

    /** The search field in the header of a section of the old guides views. @param hint the section's own hint, or null for the usual */
    void renderSectionSearch(Ui ui, float right, float cy, int w, @Nullable String hint) {
        float x = right - w;
        boolean focused = sectionSearch != null && sectionSearch.isFocused();
        ui.box(x, cy - 19, w, 38, 9, ui.c("input.bg"), focused ? ui.c("card.border") : ui.c("input.border"));
        ui.icon(works.repka.ryscore.ui.Icons.Shape.MAGNIFIER, x + 13, cy - 5, 10, ui.c("text.muted"));
        if (sectionSearch != null) {
            sectionSearch.visible = true;
            sectionSearch.setHint(hint != null ? Component.literal(hint) : Component.translatable("rysquests.guides.search"));
            if (!sectionSearch.getValue().equals(BookState.sectionSearch)) {
                sectionSearch.setValue(BookState.sectionSearch);
            }
            sectionSearch.setX(Math.round((x + 29) / 2f));
            sectionSearch.setY(Math.round(cy / 2f) - 5);
            sectionSearch.setWidth(Math.round((w - 29 - 13) / 2f));
            sectionSearch.render(ui.g, ui.mouseX / 2, ui.mouseY / 2, 0);
        }
    }

    // --- what the old views ask of the screen ---

    /** Quests of the open chapter in dependency order; hidden ones appear once they unlock. */
    List<Quest> visibleQuests() {
        return BookState.chapter == null ? List.of() : BookTallies.questsOf(BookState.chapter);
    }

    int[] chapterCounts(ResourceLocation chapter) {
        return BookTallies.mainCounts(chapter);
    }

    static int[] countsOf(ResourceLocation chapter) {
        return BookTallies.mainCounts(chapter);
    }

    int readyToSubmit() {
        return BookTallies.readyToSubmit();
    }

    int readyToClaim() {
        return BookTallies.readyToClaim();
    }

    boolean onlyPicksLeft() {
        return BookTallies.onlyPicksLeft();
    }

    /** The bulk claim of every button: everything that needs no decision, or else the first quest that waits for one. */
    void claimAll() {
        Quest pick = BookTallies.onlyPicksLeft() ? BookTallies.pickQuest() : null;
        if (pick != null) {
            BookState.select(pick);
            BookState.view = ViewMode.MAP;
            BookState.pickNudge = System.currentTimeMillis();
        } else {
            QuestKeys.sendAll(QuestActionPayload.CLAIM_ALL);
            BookTallies.invalidate();
        }
    }

    /** The label of a bulk claim button: its own words, or "choose a reward" when only choices are left. */
    String claimAllLabel(String own) {
        return onlyPicksLeft() ? Component.translatable("rysquests.book.pick_reward").getString() : own;
    }

    /** The other view of the map's card: shows the path to the selected quest. */
    void focusOnMap() {
        BookState.section = Chapter.STORY;
        BookState.view = ViewMode.MAP;
        mapTab().map.focusSelected();
    }

    /** The library's "+": a new guide in the editor's "Гайды". */
    void newGuide() {
        EditorBook.active = true;
        closePage();
        EditorPages.current = EditorPages.GUIDES;
        ((GuideEditorView) editor.page(EditorPages.GUIDES)).newGuide();
    }

    /** The server's answer to a trade. */
    public static void onShopResult(boolean ok, String reason) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen && screen.views.get(BookTabs.SHOP) instanceof ShopView shop) {
            shop.onResult(ok, reason);
        }
        lastShopResult = ok ? "" : reason;
    }

    /** The server's answer to a save. */
    public static void onEditorResult(boolean ok, String reason, List<String> args) {
        lastEditorResult = ok ? "" : reason;
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            EditorBook.get().onResult(screen, ok, reason, args);
        } else if (EditorBook.get().saving()) {
            EditorBook.get().onResult(null, ok, reason, args);
        }
    }

    /** Opens the book on a quest, from a link in chat. */
    public static void openAt(ResourceLocation id) {
        Quest quest = BookData.book().quests().get(id);
        if (quest != null) {
            BookState.select(quest);
            BookState.view = ViewMode.MAP;
            Minecraft.getInstance().setScreen(new BookScreen());
        }
    }

    // --- input ---

    private boolean typing() {
        return searchBox != null && searchBox.isFocused() || sectionSearch != null && sectionSearch.isFocused();
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (dialog != null) {
            kitHits.click(mouseX, mouseY, button);
            return true;
        }
        if (button == 0 && kitHits.click(mouseX, mouseY, button)) {
            return true;
        }
        int x = (int) Math.round(mouseX * 2);
        int y = (int) Math.round(mouseY * 2);
        if (button == GLFW.GLFW_MOUSE_BUTTON_4 && goBack()) {
            return true;
        }
        if (shown != null) {
            shown.press(mouseX * 2, mouseY * 2, button);
        }
        List<Ui.Hit> list = button == 0 ? hits : button == 1 ? rightHits : List.of();
        for (int i = list.size() - 1; i >= 0; i--) {
            Ui.Hit hit = list.get(i);
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                boolean searching = searchBox != null && searchBox.isFocused();
                hit.action().run();
                if (sectionSearch != null) {
                    sectionSearch.setFocused(false);
                }
                if (searchBox != null && searching == searchBox.isFocused()) {
                    searchBox.setFocused(false);
                }
                if (!typing()) {
                    setFocused(null);
                }
                return true;
            }
        }
        if (button == 1 && lastHovered != null) {
            Recipes.showUsages(lastHovered);
            return true;
        }
        return super.onClick(mouseX, mouseY, button);
    }

    /** Backspace and the mouse's back button: a page closes, otherwise the book goes back in its history. */
    private boolean goBack() {
        if (page != null) {
            closePage();
            return true;
        }
        return BookState.back();
    }

    /** The book closes: with edits in the editor's draft it asks first (picture 33b). */
    @Override
    public void onClose() {
        if (dialog == null && editor.askBeforeClose(super::onClose)) {
            return;
        }
        super.onClose();
    }

    @Override
    public void removed() {
        views.values().forEach(BookView::closed);
        editor.closed();
        if (page != null) {
            page.closed();
        }
        super.removed();
    }

    @Override
    protected boolean onRelease(double mouseX, double mouseY, int button) {
        if (shown != null && shown.release(mouseX * 2, mouseY * 2, button)) {
            return true;
        }
        return super.onRelease(mouseX, mouseY, button);
    }

    @Override
    protected boolean onDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return shown != null && shown.drag(dragX * 2, dragY * 2) || super.onDrag(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dialog != null) {
            return true;
        }
        if (shown != null) {
            shown.scroll(mouseX * 2, mouseY * 2, scrollY);
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (dialog != null) {
            return true;
        }
        if (typing()) {
            return super.charTyped(c, modifiers);
        }
        return shown != null && shown.typed(c) || super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (dialog != null) {
            return dialog.keyPressed(key);
        }
        if (searchBox != null && searchBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER && !BookState.search.isBlank()) {
                SearchIndex.find(BookState.search).stream().findFirst().ifPresent(quest -> {
                    closePage();
                    BookState.select(quest);
                    BookState.view = BookState.mapMode;
                });
                searchBox.setFocused(false);
                setFocused(null);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                setFocused(null);
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        if (typing()) {
            return super.keyPressed(key, scan, modifiers);
        }
        if (shown != null && shown.key(key)) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_SLASH || key == GLFW.GLFW_KEY_F && hasControlDown()) {
            focusSearch();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && goBack()) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && page != null) {
            closePage();
            return true;
        }
        if (QuestKeys.OPEN_BOOK.matches(key, scan)) {
            onClose();
            return true;
        }
        if (QuestKeys.PIN.matches(key, scan) && BookState.quest != null) {
            BookData.togglePin(BookState.quest);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // --- hooks for the in-game autotest ---

    /** Which body the book shows: "loading", "empty", "page" or the open tab's id. */
    public static String bodyForTest() {
        if (!(Minecraft.getInstance().screen instanceof BookScreen screen)) {
            return "closed";
        }
        BookView body = screen.body();
        if (body == screen.loading) {
            return "loading";
        }
        if (body == screen.empty) {
            return "empty";
        }
        if (body == screen.page) {
            return "page";
        }
        BookTabs.Tab tab = BookTabs.current(BookData.book());
        return tab == null ? "none" : tab.id();
    }

    /** The ids of the tabs in the header. */
    public static List<String> tabsForTest() {
        return BookTabs.visible(BookData.book()).stream().map(BookTabs.Tab::id).toList();
    }

    public static void openTabForTest(String id) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.openTab(BookTabs.byId(id));
        }
    }

    public static void toggleSettingsForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.toggleSettings();
        }
    }

    public static boolean compactForTest() {
        return Minecraft.getInstance().screen instanceof BookScreen screen && screen.compact();
    }

    public static void foldSidebarForTest(boolean fold) {
        Sidebar.folded = fold;
    }

    public static void openEmptyPageForTest(boolean importWizard) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.openPage(importWizard ? new ImportWizardView(screen) : new NewBookView(screen));
        }
    }

    public static void askToOpenForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            ClosedBookLine.askToOpen(screen);
        }
    }

    public static void ftbForTest(@Nullable Boolean present) {
        EmptyBookView.ftbForTest = present;
    }

    public static boolean dialogOpenForTest() {
        return Minecraft.getInstance().screen instanceof BookScreen screen && screen.dialog != null;
    }

    public static boolean onlyPicksLeftForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            BookTallies.invalidate();
            return screen.onlyPicksLeft();
        }
        return false;
    }

    public static void claimAllForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            BookTallies.invalidate();
            screen.claimAll();
        }
    }

    @Nullable
    public static ResourceLocation selectedQuestForTest() {
        return BookState.quest;
    }

    public static boolean inGuidesForTest() {
        return BookState.inGuides();
    }

    public static List<String> guideIssuesForTest() {
        return GuideCheck.section().entries().stream().map(entry -> entry.subject() + " " + entry.detail()).toList();
    }

    public static void showSectionForTest(String section) {
        BookState.openSection(section);
    }

    public static String currentSectionForTest() {
        return BookState.currentSection();
    }

    public static int sectionPageForTest(ResourceLocation section) {
        return BookState.SECTION_PAGES.getOrDefault(section, 0);
    }

    public static void shopTradeForTest(ResourceLocation offer, boolean treasury) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.shopView().tradeForTest(offer, treasury);
        }
    }

    public static void shopSelectForTest(ResourceLocation offer) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.shopView().selectForTest(offer);
        }
    }

    public static void shopHistoryForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.shopView().showHistoryForTest();
        }
    }

    @Nullable
    public static String lastShopResultForTest() {
        return lastShopResult;
    }

    public static void clearShopResultForTest() {
        lastShopResult = null;
    }

    public static void achievementSelectForTest(ResourceLocation id) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.achievementsView().selectForTest(id);
        }
    }

    public static boolean structureReadyForTest(ResourceLocation id) {
        return Minecraft.getInstance().screen instanceof BookScreen screen && screen.guidesTab().guidePage.structureForTest(id) != null && screen.guidesTab().guidePage.structureForTest(id).readyForTest();
    }

    public static void structureLayerForTest(ResourceLocation id) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.guidesTab().guidePage.structureForTest(id).stepLayerForTest();
        }
    }

    @Nullable
    public static String lastEditorResultForTest() {
        return lastEditorResult;
    }

    public static void showGuidesForTest() {
        BookState.openLibrary();
    }

    public static void showStoryForTest() {
        BookState.openSection(Chapter.STORY);
    }

    public static void openGuideForTest(ResourceLocation id, int page) {
        Guide guide = BookData.book().guides().get(id);
        if (guide != null) {
            BookState.openGuide(guide, page);
        }
    }

    public static void selectGuideForTest(ResourceLocation id) {
        BookState.guide = id;
        BookState.openLibrary();
    }

    public static int guidePageForTest() {
        return BookState.guidePage;
    }

    public static void openRefForTest(works.repka.rysquests.data.GuideRef ref) {
        BookState.openRef(ref);
    }

    public static void searchForTest(String query) {
        BookState.search = query;
    }

    public static ViewMode viewForTest() {
        return BookState.currentView();
    }

    public static void showForTest(ViewMode view) {
        BookState.section = Chapter.STORY;
        BookState.view = view;
    }

    public static void selectForTest(ResourceLocation chapter, ResourceLocation quest) {
        BookState.chapter = chapter;
        BookState.quest = quest;
    }

    public static void mapZoomForTest(float zoom) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen && screen.mapTab().map instanceof MapView map) {
            map.zoomForTest(zoom);
        }
    }

    public static int chapterScrollForTest() {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen.mapTab().sidebarScrollForTest() : -1;
    }

    @Nullable
    public static ResourceLocation chapterForTest() {
        return BookState.chapter;
    }

    /** The text tooltip of the last frame, or null. */
    @Nullable
    public static String hoveredTextForTest() {
        return lastHoveredText;
    }

    /** A quest's node on the map in interface units (x, y, width, height), or null when it is not laid out. */
    @Nullable
    public static double[] mapNodeForTest(ResourceLocation quest) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen && screen.mapTab().map instanceof MapView map) {
            return map.nodeForTest(quest);
        }
        return null;
    }

    public static void mapLegacyLayoutForTest(boolean legacy) {
        MapView.legacyLayoutForTest = legacy;
    }

    public static void mapUnfoldForTest(boolean unfold) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen && screen.mapTab().map instanceof MapView map) {
            map.unfoldForTest(unfold);
        }
    }

    public static void mapFitForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen && screen.mapTab().map instanceof MapView map) {
            map.fitForTest();
        }
    }

    public static String mapStatsForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen && screen.mapTab().map instanceof MapView map) {
            return map.statsForTest();
        }
        return "no map";
    }
}
