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
 * {@link BookData}, and the editor of the book from {@link EditorPages}. Small screens get the family's compact base 480×270.
 */
public final class BookScreen extends ScaledScreen implements BookHeader.Host {
    private static final float DIM = 0.6f;
    private static final float WINDOW_W = 0.9375f;
    private static final float WINDOW_H = 0.91f;
    private static final int WINDOW_MAX_W = 1760;
    private static final int WINDOW_MAX_H = 1040;
    @Nullable
    private static ItemStack lastHovered;
    private static List<float[]> itemSpots = List.of();
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
    private final SearchPopover searchPopover = new SearchPopover(this);
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

    /** The page over the tab, or null: the first-run hooks of the autotest look at it. */
    @Nullable
    BookView page() {
        return page;
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
    public boolean hasAbout() {
        return AboutView.guide() != null;
    }

    @Override
    public boolean aboutOpen() {
        return page instanceof AboutView;
    }

    @Override
    public boolean aboutUnread() {
        return AboutView.unread();
    }

    /** The book's name in the header: the pages "О книге" over the tab, or back from them (ADR-60). */
    @Override
    public void toggleAbout() {
        if (page instanceof AboutView) {
            closePage();
        } else if (hasAbout()) {
            BookTabs.Tab tab = BookTabs.current(BookData.book());
            openPage(new AboutView(this, tab == null ? Component.translatable("rysquests.book.title").getString() : tab.label()));
        }
    }

    /** "Забрать" of an epoch's reward on "Сейчас": the map at the epoch with its card open (ADR-60). */
    void showEpochReward(int epoch) {
        closePage();
        BookState.section = Chapter.STORY;
        BookState.view = ViewMode.MAP;
        mapTab().map.showEpochReward(epoch);
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

    /** Puts a query in the search field, from a suggestion or a recent query of the results window. */
    void setSearch(String query) {
        BookState.search = query;
        if (searchBox != null) {
            searchBox.setValue(query);
        }
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
        if (ui.theme.beveled()) {
            // vanilla's window: a bevel of four mockup pixels, what is inside stands within it (picture 45)
            works.repka.ryscore.ui.Draw.bevel(g, x / 2f, y / 2f, w / 2f, h / 2f, ui.c("panel.bg"), ui.c("panel.bevel.light"), ui.c("panel.bevel.dark"), 2, false);
            drawBook(ui, x + 4, y + 4, w - 8, h - 8);
        } else {
            ui.box(x, y, w, h, 14, ui.c("panel.bg"), ui.c("panel.border"));
            drawBook(ui, x + 1, y + 1, w - 2, h - 2);
        }
        settleFields();
        lastHovered = ui.hoveredStack;
        itemSpots = List.copyOf(ui.itemSpots);
        lastHoveredText = ui.hoveredText == null ? null : ui.hoveredText.getString();
        if (dialog != null) {
            Kit.dialog(g, ui.theme, kitHits, dialog, mouseX, mouseY);
        } else if (ui.hoveredStack != null && ui.hoveredNote != null) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), ui.hoveredStack));
            lines.add(ui.hoveredNote);
            g.renderTooltip(font, lines, ui.hoveredStack.getTooltipImage(), ui.hoveredStack, mouseX, mouseY);
        } else if (ui.hoveredStack != null) {
            g.renderTooltip(font, ui.hoveredStack, mouseX, mouseY);
        } else if (ui.hoveredText != null) {
            // A tooltip of the interface in the family's look; an item keeps the game's own, as the kit says
            Kit.tooltip(g, ui.theme, List.of(ui.hoveredText.getString().split("\n")), mouseX, mouseY);
        }
    }

    private void drawBook(Ui ui, int x, int y, int w, int h) {
        BookView body = body();
        shown = body;
        boolean bare = body == loading || body == empty || body.bareHeader();
        if (header != null) {
            header.render(ui, x, y, w, bare ? BookHeader.Mode.BARE : BookHeader.Mode.FULL, compact(), bare ? body : null);
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
        if (!bare) {
            searchPopover.render(ui, x, y, w, h, searchBox != null && searchBox.isFocused(), compact());
        }
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
        int epoch = pick == null ? 0 : BookData.book().epochOf(pick.id());
        if (epoch > 0) {
            // an epoch's choice is made on its card
            showEpochReward(epoch);
        } else if (pick != null) {
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
            if (key != GLFW.GLFW_KEY_ESCAPE && searchPopover.key(key, true)) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    searchBox.setFocused(false);
                    setFocused(null);
                }
                return true;
            }
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
        if (searchPopover.key(key, false)) {
            return true;
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
    /** Items drawn last frame with their tooltip and recipe keys, in the book's coordinates: x, y, w, h, 1 for hover only. */
    public static List<float[]> itemSpotsForTest() {
        return itemSpots;
    }

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

    /** "Все / Основные" over the sidebar; returns the whole book's count as the sidebar shows it, "done/total". */
    public static String countAllForTest(boolean all) {
        BookTallies.setCountAll(all);
        BookTallies.Tally tally = BookTallies.book();
        return tally.shownDone() + "/" + tally.shownTotal();
    }

    /** The quest menu "⋯" of the list's panel: opened or closed for a picture. */
    public static void questMenuForTest(boolean open) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.mapTab().list.panelForTest().menuForTest(open);
        }
    }

    /** The lines of the quest menu for the selected quest, "(off)" after one that cannot be pressed. */
    public static List<String> questMenuItemsForTest() {
        Quest quest = BookState.quest == null ? null : BookData.book().quests().get(BookState.quest);
        return Minecraft.getInstance().screen instanceof BookScreen screen && quest != null ? screen.mapTab().list.panelForTest().menuItemsForTest(quest) : List.of();
    }

    /** Presses a line of the quest menu for the selected quest. */
    public static void questMenuPressForTest(int index) {
        Quest quest = BookState.quest == null ? null : BookData.book().quests().get(BookState.quest);
        if (Minecraft.getInstance().screen instanceof BookScreen screen && quest != null) {
            screen.mapTab().list.panelForTest().menuPressForTest(quest, index);
        }
    }

    /** Answers yes to the question the quest menu asked. */
    public static void questMenuYesForTest() {
        Runnable yes = QuestPanel.yesForTest;
        QuestPanel.yesForTest = null;
        if (yes != null) {
            yes.run();
        }
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

    /** The book's name in the header pressed: the pages "О книге" open or close. */
    public static void toggleAboutForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.toggleAbout();
        }
    }

    /** The page of "О книге" open over the tab, or -1 when they are not open. */
    public static int aboutPageForTest() {
        return Minecraft.getInstance().screen instanceof BookScreen screen && screen.page instanceof AboutView about ? about.pageForTest() : -1;
    }

    public static boolean aboutUnreadForTest() {
        return AboutView.unread();
    }

    /** The map at an epoch with its reward card open, as "Сейчас" opens it. */
    public static void showEpochRewardForTest(int epoch) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.showEpochReward(epoch);
        }
    }

    /** The epoch whose reward card is open on the map, 0 for none. */
    public static int epochCardForTest() {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen.mapTab().map.epochCardForTest() : 0;
    }

    /** Where the reward of an epoch stands for this player: LOCKED, READY or CLAIMED, or "" without one. */
    public static String epochRewardStateForTest(int epoch) {
        Quest quest = EpochCards.questOf(epoch);
        return quest == null ? "" : EpochCards.state(quest).name();
    }

    /** Picks an option of a choice in the open epoch card and presses "Забрать награду". */
    public static void claimEpochForTest(int epoch, int reward, int option) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            Quest quest = EpochCards.questOf(epoch);
            if (quest != null) {
                EpochCards cards = screen.mapTab().map.epochCardsForTest();
                if (reward >= 0) {
                    cards.pickForTest(reward, option);
                }
                cards.claim(quest);
            }
        }
    }

    /** The chapters the sidebar and the tallies show, by id. */
    public static List<ResourceLocation> shownChaptersForTest() {
        return BookData.chapters().stream().map(Chapter::id).toList();
    }

    /** The quests a chapter shows as cards, by id. */
    public static List<ResourceLocation> chapterQuestsForTest(ResourceLocation chapter) {
        return BookTallies.questsOf(chapter).stream().map(Quest::id).toList();
    }

    /** The author's tabs inside "Гайды" as this player sees them, by id. */
    public static List<String> authorSectionsForTest() {
        return BookTabs.authorSections(BookData.book()).stream().map(section -> section.id().toString()).toList();
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

    /** Why an offer of the client's book is closed, as its card says it; empty when it is open. */
    public static String shopLockForTest(ResourceLocation offer) {
        return ShopView.lockForTest(offer);
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

    /** The pin of an achievement's level as the window shows it: PINNED, FREE or FULL. */
    public static String achievementPinForTest(ResourceLocation tierQuest) {
        return AchievementsView.pin(tierQuest).name();
    }

    /** The pin button of the selected achievement in GUI pixels {x, y, w, h}, or null before it is drawn. */
    @Nullable
    public static float[] achievementPinButtonForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen && screen.achievementsView().pinButtonForTest() != null) {
            float[] r = screen.achievementsView().pinButtonForTest();
            return new float[] {r[0] / 2f, r[1] / 2f, r[2] / 2f, r[3] / 2f};
        }
        return null;
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
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.mapTab().map.zoomForTest(zoom);
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
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            return screen.mapTab().map.nodeForTest(quest);
        }
        return null;
    }

    /** The old layered layout is gone; kept so that older scenarios still compile. */
    public static void mapLegacyLayoutForTest(boolean legacy) {
    }

    /** Branches no longer fold on the canvas; kept so that older scenarios still compile. */
    public static void mapUnfoldForTest(boolean unfold) {
    }

    public static void mapFitForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.mapTab().map.flyToBook();
        }
    }

    public static String mapStatsForTest() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            return screen.mapTab().map.stateForTest();
        }
        return "no map";
    }

    /** The map's flights and toggles, for the map scenario. */
    public static void mapFlyForTest(@Nullable ResourceLocation chapter, int epoch, @Nullable ResourceLocation quest) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            BookState.view = ViewMode.MAP;
            MapView map = screen.mapTab().map;
            if (quest != null) {
                map.flyToQuest(quest);
            } else if (chapter != null) {
                map.flyToChapter(chapter);
            } else if (epoch > 0) {
                map.flyToEpoch(epoch);
            } else {
                map.flyToBook();
            }
        }
    }

    public static void mapSideForTest(boolean show) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.mapTab().map.canvas.setShowSide(show);
        }
    }

    public static void mapLegendForTest(boolean open) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.mapTab().map.legendForTest(open);
        }
    }

    /** A chapter's area on screen in mockup pixels, or null. */
    @Nullable
    public static float[] mapChapterForTest(ResourceLocation chapter) {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen.mapTab().map.chapterForTest(chapter) : null;
    }

    @Nullable
    public static ResourceLocation mapHereForTest() {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen.mapTab().map.hereForTest() : null;
    }

    public static boolean mapReadyForTest() {
        return Minecraft.getInstance().screen instanceof BookScreen screen && screen.mapTab().map.canvas.layout() != null && !screen.mapTab().map.canvas.flying();
    }

    /** The map's canvas itself, for the checks of hovering and clicking; null when the book is not open. */
    @Nullable
    public static works.repka.rysquests.client.canvas.MapCanvas mapCanvasForTest() {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen.mapTab().map.canvas : null;
    }

    /** A click on the map as the screen takes it: press and release at one point, in mockup pixels. */
    public static void mapClickForTest(double x, double y) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.mapTab().map.press(x, y, 0);
            screen.mapTab().map.release(x, y, 0);
        }
    }

    public static works.repka.rysquests.client.canvas.MapCanvas.Stats mapCanvasStatsForTest() {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen.mapTab().map.canvas.stats() : null;
    }
}
