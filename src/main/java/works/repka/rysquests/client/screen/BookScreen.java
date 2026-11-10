package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.task.ItemTask;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Field;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.ScaledScreen;

/**
 * The quest book window. Two frames, both from the mockups: the flow has a rail of chapters and a reading column
 * ({@code design/Comfort.dc.html}), every other view a top bar ({@code design/Main.dc.html}).
 */
public final class BookScreen extends ScaledScreen {
    private static final float DIM = 0.6f;
    private static final float WINDOW_W = 0.9375f;
    private static final float WINDOW_H = 0.91f;
    private static final int WINDOW_MAX_W = 1760;
    private static final int WINDOW_MAX_H = 1040;
    private static final int CARD_MIN_WINDOW = 1000;
    private static final int[] NO_COLORS = new int[0];
    private static final int NO_COUNT = -1;
    private static final int RAIL_MIN = 64;
    private static final int RAIL_MAX = 76;
    private static final int SEARCH_MIN = 150;
    private static final int FLOW_COLUMN_MAX = 600;
    private static final float RAIL_SHARE = 0.05326f;
    private static final float FLOW_COLUMN_SHARE = 0.41953f;
    private static final int RAIL_STEP = 60;
    private static final int CHAPTERS_W = 232;
    private static final int CARD_W = 392;
    private static final int CHAPTER_STEP = 51;
    private static final int WHEEL = 48;
    private static final long COUNT_REFRESH_MS = 500;
    private static final List<ViewMode> TABS = List.of(ViewMode.MAP, ViewMode.LIST, ViewMode.TIMELINE, ViewMode.HOME, ViewMode.FLOW);
    @Nullable
    private static ItemStack lastHovered;

    private final List<Ui.Hit> hits = new ArrayList<>();
    private final List<Ui.Hit> rightHits = new ArrayList<>();
    private final Kit.Hits kitHits = new Kit.Hits();
    private final Map<ResourceLocation, List<Quest>> orderCache = new HashMap<>();
    private final EnumMap<ViewMode, BookView> views = new EnumMap<>(ViewMode.class);
    private final ReadingColumn reading = new ReadingColumn(this);
    @Nullable
    private QuestBook cachedFor;
    @Nullable
    private Field searchBox;
    private long countsAt;
    private int claimCount;
    private List<ItemStack> claimStacks = List.of();
    private final Scroll chapterScroll = new Scroll();
    private final Scroll railScroll = new Scroll();
    private final Scroll cardScroll = new Scroll();
    private int chaptersLeft;
    private int chaptersRight;
    private int cardLeft;

    public BookScreen() {
        super(Component.translatable("rysquests.book.title"));
        views.put(ViewMode.FLOW, new FlowView(this));
        views.put(ViewMode.MAP, new MapView(this));
        views.put(ViewMode.HOME, new HomeView());
        views.put(ViewMode.LIST, new ListView(this));
        views.put(ViewMode.TIMELINE, new TimelineView());
        views.put(ViewMode.DEV, new DevView());
    }

    /** What is under the cursor, for the recipe viewer's keys. Empty when the book is closed. */
    public static ItemStack hoveredStack() {
        return lastHovered == null || !(Minecraft.getInstance().screen instanceof BookScreen) ? ItemStack.EMPTY : lastHovered;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new Field(80, 10);
        searchBox.setHint(Component.translatable("rysquests.book.search"));
        searchBox.setValue(BookState.search);
        searchBox.setResponder(value -> BookState.search = value);
        addWidget(searchBox);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static QuestBook book() {
        return ClientBook.current();
    }

    private static Function<ResourceLocation, QuestProgress> progress() {
        return ClientProgress::get;
    }

    /** Quests of the open chapter in dependency order; hidden ones appear once they unlock. */
    List<Quest> visibleQuests() {
        return BookState.chapter == null ? List.of() : questsOf(BookState.chapter);
    }

    private List<Quest> questsOf(ResourceLocation chapter) {
        if (cachedFor != book()) {
            cachedFor = book();
            orderCache.clear();
        }
        return orderCache.computeIfAbsent(chapter, id -> ChapterOrder.of(book(), id)).stream()
                .filter(quest -> !quest.options().hidden() || QuestLogic.isUnlocked(quest, progress()))
                .toList();
    }

    static List<Chapter> chapters() {
        return book().chapters().values().stream().filter(chapter -> !chapter.hidden() && chapter.isStory()).toList();
    }

    /** Whether a quest belongs to what the book shows: chapters of other sections stay out of it for now. */
    static boolean inStory(Quest quest) {
        return quest.chapter().map(id -> book().chapters().get(id)).map(Chapter::isStory).orElse(true);
    }

    /** The chapter's number in the book, which the rail and the headings show as a roman numeral. */
    static int chapterNumber(@Nullable ResourceLocation id) {
        int index = 0;
        for (Chapter chapter : chapters()) {
            index++;
            if (chapter.id().equals(id)) {
                return index;
            }
        }
        return 0;
    }

    int readyToSubmit() {
        var inventory = Minecraft.getInstance().player.getInventory();
        return (int) visibleQuests().stream().filter(quest -> {
            QuestStatus status = BookState.status(quest);
            if (status != QuestStatus.AVAILABLE && status != QuestStatus.IN_PROGRESS) {
                return false;
            }
            QuestProgress own = ClientProgress.get(quest.id());
            boolean any = false;
            for (int i = 0; i < quest.tasks().size(); i++) {
                if (quest.tasks().get(i) instanceof ItemTask task && task.mode() == ItemTask.Mode.CONSUME && own.task(i) < task.count()) {
                    if (countInInventory(task, inventory) < task.count() - own.task(i)) {
                        return false;
                    }
                    any = true;
                }
            }
            return any;
        }).count();
    }

    /** How many fitting items the player holds. */
    static int held(ItemTask task) {
        return countInInventory(task, Minecraft.getInstance().player.getInventory());
    }

    private static int countInInventory(ItemTask task, net.minecraft.world.entity.player.Inventory inventory) {
        int have = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && matches(task, stack)) {
                have += stack.getCount();
            }
        }
        return have;
    }

    private static boolean matches(ItemTask task, ItemStack stack) {
        return task.itemRefs().stream().anyMatch(ref -> ref.tag()
                ? stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ref.id()))
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ref.id()));
    }

    int readyToClaim() {
        refreshCounts();
        return claimCount;
    }

    private void refreshCounts() {
        long now = System.currentTimeMillis();
        if (now - countsAt <= COUNT_REFRESH_MS) {
            return;
        }
        countsAt = now;
        int count = 0;
        List<ItemStack> stacks = new ArrayList<>();
        for (Quest quest : book().quests().values()) {
            if (inStory(quest) && BookState.status(quest) == QuestStatus.COMPLETED) {
                count++;
                if (stacks.size() < 2) {
                    stacks.addAll(QuestCard.rewardStacks(quest, 2 - stacks.size()));
                }
            }
        }
        claimCount = count;
        claimStacks = stacks;
    }

    /** What a locked quest still needs, in words. */
    static Component waitingFor(Quest quest) {
        List<Quest> missing = quest.requires().stream()
                .filter(id -> !ClientProgress.get(id).completed())
                .map(id -> book().quests().get(id))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (missing.isEmpty()) {
            if (ClientProgress.isStageLocked(quest.id()) && quest.stageRequired().isPresent()) {
                return Component.translatable("rysquests.flow.needs_stage", quest.stageRequired().get());
            }
            return Component.translatable("rysquests.flow.opens_after");
        }
        return Component.translatable("rysquests.flow.waiting", Component.translatable(missing.getFirst().title()));
    }

    private BookView activeView() {
        BookView view = views.get(BookState.currentView());
        return view != null ? view : views.get(ViewMode.FLOW);
    }

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
        int x = (screenW - w) / 2;
        int y = (screenH - h) / 2;
        Chapter opened = BookState.chapter == null ? null : book().chapters().get(BookState.chapter);
        if (opened == null || !opened.isStory()) {
            BookState.chapter = chapters().stream().findFirst().map(Chapter::id).orElse(null);
        }
        refreshCounts();
        if (searchBox != null) {
            boolean searchable = BookState.currentView() == ViewMode.MAP || BookState.currentView() == ViewMode.LIST;
            searchBox.visible = searchable;
            if (!searchable && searchBox.isFocused()) {
                searchBox.setFocused(false);
                setFocused(null);
            }
        }
        if (BookState.currentView() == ViewMode.FLOW) {
            renderFlowFrame(ui, x, y, w, h);
        } else {
            renderMainFrame(ui, x, y, w, h);
        }
        lastHovered = ui.hoveredStack;
        if (ui.hoveredStack != null) {
            g.renderTooltip(font, ui.hoveredStack, mouseX, mouseY);
        } else if (ui.hoveredText != null) {
            g.renderTooltip(font, ui.hoveredText, mouseX, mouseY);
        }
    }

    // --- the flow's frame ---

    private void renderFlowFrame(Ui ui, int x, int y, int w, int h) {
        ui.shadow(x, y, w, h, 16, 1f);
        ui.box(x, y, w, h, 16, ui.flowBg(), ui.flowBorder());
        int inner = w - 2;
        int railW = Mth.clamp(Math.round(inner * RAIL_SHARE), RAIL_MIN, RAIL_MAX);
        int columnW = Math.min(FLOW_COLUMN_MAX, Math.round(inner * FLOW_COLUMN_SHARE));
        int top = y + 1;
        int height = h - 2;
        int railX = x + 1;
        chaptersLeft = railX;
        chaptersRight = railX + railW;
        int columnX = railX + railW;
        renderRail(ui, railX, top, railW, height);
        activeView().render(ui, columnX, top, columnW, height);
        cardLeft = columnX + columnW;
        reading.render(ui, cardLeft, top, x + w - 1 - cardLeft, height);
    }

    private void renderRail(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 15, ui.railBg(), true, false, false, true);
        ui.rect(x + w - 1, y, 1, h, ui.flowBorder());
        float center = x + (w - 1) / 2f;
        ui.logo(center - 15, y + 14, 30);
        int listTop = y + 60;
        int gearY = y + h - 14 - 50;
        int listH = gearY - 10 - listTop;
        List<Chapter> chapters = chapters();
        railScroll.content(chapters.size() * RAIL_STEP, listH);
        int railOffset = railScroll.offset();
        ui.clip(x, listTop, w, listH);
        for (int i = 0; i < chapters.size(); i++) {
            int rowY = listTop + i * RAIL_STEP - railOffset;
            if (rowY + 50 > listTop && rowY < listTop + listH) {
                renderRailItem(ui, chapters.get(i), i + 1, center - 25, rowY);
            }
        }
        ui.unclip();
        boolean over = ui.over(center - 25, gearY, 50, 50);
        ui.box(center - 25, gearY, 50, 50, 12, over ? ui.c("slot.hover") : 0, ui.flowBorder());
        ui.icon(Icons.Shape.GEAR, center - 7, gearY + 18, 14, ui.c("text.muted"));
        ui.hit(center - 25, gearY, 50, 50, this::openSettings);
    }

    private void renderRailItem(Ui ui, Chapter chapter, int number, float x, int y) {
        boolean open = chapter.id().equals(BookState.chapter);
        int[] counts = chapterCounts(chapter.id());
        if (open) {
            ui.box(x, y, 50, 50, 12, ui.c("slot.hover"), ui.mix("card.border", "text.dim", 0.14f));
        } else if (ui.over(x, y, 50, 50)) {
            ui.fill(x, y, 50, 50, 12, ui.c("slot.hover"));
        }
        boolean started = counts[0] > 0;
        String fg = open ? "text.primary" : started ? "text.muted" : "text.dim";
        String numeral = Ui.roman(number);
        ui.labelCentered(ui.width(numeral, 15, Ui.DISPLAY) > 46 ? String.valueOf(number) : numeral, x + 25, y + 22, 15, Ui.DISPLAY, ui.c(fg));
        float ratio = counts[1] == 0 ? 0 : (float) counts[0] / counts[1];
        ui.fill(x + 12, y + 34.5f, 26, 3, 2, ui.c("slot.bg"));
        if (ratio > 0) {
            ui.fill(x + 12, y + 34.5f, 26 * ratio, 3, 2, ui.c(open && ratio < 1 ? "accent" : "state.ok"));
        }
        ui.tooltipIfOver(x, y, 50, 50, Component.translatable(chapter.title()));
        ui.hit(x, y, 50, 50, () -> BookState.openChapter(chapter.id()));
    }

    private void openSettings() {
        ModList.get().getModContainerById(RYSQuests.MODID).ifPresent((ModContainer container) ->
                Minecraft.getInstance().setScreen(new ConfigurationScreen(container, this)));
    }

    // --- the frame of every other view ---

    private void renderMainFrame(Ui ui, int x, int y, int w, int h) {
        ui.shadow(x, y, w, h, 14, 1f);
        ui.box(x, y, w, h, 14, ui.c("panel.bg"), ui.c("panel.border"));
        ViewMode mode = BookState.currentView();
        int innerX = x + 1;
        int innerW = w - 2;
        int top = y + 1;
        int barH = mode == ViewMode.DEV ? 51 : mode == ViewMode.MAP || mode == ViewMode.LIST ? 61 : 59;
        if (mode == ViewMode.DEV) {
            ((DevView) views.get(ViewMode.DEV)).renderBar(ui, innerX, top, innerW, barH);
        } else {
            renderBar(ui, mode, innerX, top, innerW, barH);
        }
        int bodyY = top + barH;
        int bodyH = h - 2 - barH;
        cardLeft = x + w;
        chaptersLeft = innerX;
        chaptersRight = innerX;
        if (mode == ViewMode.MAP || mode == ViewMode.LIST) {
            renderChapters(ui, innerX, bodyY, CHAPTERS_W, bodyH);
            chaptersRight = innerX + CHAPTERS_W;
            int viewX = innerX + CHAPTERS_W;
            int viewW = innerW - CHAPTERS_W;
            if (mode == ViewMode.MAP) {
                boolean card = w >= CARD_MIN_WINDOW;
                if (card) {
                    viewW -= CARD_W;
                    cardLeft = viewX + viewW;
                    renderCard(ui, cardLeft, bodyY, CARD_W, bodyH);
                }
            }
            activeView().render(ui, viewX, bodyY, viewW, bodyH);
        } else {
            activeView().render(ui, innerX, bodyY, innerW, bodyH);
        }
    }

    private void renderBar(Ui ui, ViewMode mode, int x, int y, int w, int barH) {
        ui.fillCorners(x, y, w, barH, 13, ui.c("sidebar.bg"), true, true, false, false);
        ui.rect(x, y + barH - 1, w, 1, ui.c("panel.border"));
        float cy = y + (barH - 1) / 2f;
        renderBrand(ui, x + 18, cy);
        float tabsX = x + 161;
        List<ViewMode> modes = new ArrayList<>(TABS);
        if (Minecraft.getInstance().player.hasPermissions(2)) {
            modes.add(ViewMode.DEV);
        }
        List<Kit.Tab> tabs = new ArrayList<>();
        for (ViewMode tab : modes) {
            tabs.add(new Kit.Tab(Component.translatable("rysquests.view." + tab.getSerializedName()).getString(), NO_COUNT, tab == mode, () -> BookState.view = tab));
        }
        float cursor = tabsX + Math.min(ui.tabsWidth(tabs), x + w - 18 - 34 - 14 - tabsX);
        float right = x + w - 18;
        renderClose(ui, right - 34, cy - 17);
        right -= 34 + 14;
        int members = ClientProgress.teamSize();
        if (members > 1) {
            String team = Component.translatable("rysquests.book.team", members).getString();
            float chipW = ui.width(team, 12, Ui.REGULAR) + 38;
            if (right - chipW - 14 >= cursor) {
                ui.box(right - chipW, cy - 14.5f, chipW, 29, 9, ui.c("button.bg"), ui.c("button.border"));
                ui.circle(right - chipW + 11, cy - 4, 8, ui.c("state.ok"));
                ui.label(team, right - chipW + 27, cy, 12, Ui.REGULAR, ui.c("button.fg"));
                right -= chipW + 14;
            }
        }
        ui.tabs(tabs, tabsX, cy - 14, 28, cursor - tabsX);
        float room = right - 14 - cursor;
        if (mode == ViewMode.MAP || mode == ViewMode.LIST) {
            if (room >= SEARCH_MIN) {
                renderSearch(ui, right, cy, Math.round(Math.min(mode == ViewMode.LIST ? 286 : 266, room)));
            } else if (searchBox != null) {
                searchBox.visible = false;
            }
        } else if (mode == ViewMode.TIMELINE) {
            ((TimelineView) views.get(ViewMode.TIMELINE)).renderSummary(ui, right, cy, room);
        }
    }

    static void renderBrand(Ui ui, float x, float cy) {
        ui.logo(x, cy - 13, 26);
        ui.label("RYSQuests", x + 36, cy, 16, Ui.DISPLAY, ui.c("text.primary"));
    }

    private void renderClose(Ui ui, float x, float y) {
        boolean over = ui.over(x, y, 34, 34);
        ui.box(x, y, 34, 34, 9, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        ui.icon(Icons.Shape.CROSS, x + 11, y + 11, 12, ui.c("button.fg"));
        ui.hit(x, y, 34, 34, this::onClose);
    }

    private void renderSearch(Ui ui, float right, float cy, int w) {
        float x = right - w;
        boolean focused = searchBox != null && searchBox.isFocused();
        ui.box(x, cy - 18, w, 36, 9, ui.c("input.bg"), focused ? ui.c("card.border") : ui.c("input.border"));
        ui.icon(Icons.Shape.MAGNIFIER, x + 13, cy - 5, 10, ui.c("text.muted"));
        if (searchBox != null) {
            int fieldX = Math.round((x + 29) / 2f);
            searchBox.setX(fieldX);
            searchBox.setY(Math.round(cy / 2f) - 5);
            searchBox.setWidth(Math.round((w - 29 - 13) / 2f));
            searchBox.setSpans(SearchIndex.spans(searchBox.getValue()), NO_COLORS);
            searchBox.render(ui.g, ui.mouseX / 2, ui.mouseY / 2, 0);
        }
    }

    private void renderChapters(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.columnBg(), false, false, false, true);
        ui.rect(x + w - 1, y, 1, h, ui.c("panel.border"));
        ui.caps(Component.translatable("rysquests.book.chapters").getString(), x + 18, y + 21, ui.c("text.dim"));

        int claim = claimCount;
        int listTop = y + 40;
        int blockH = claim > 0 ? 72 : 0;
        int listH = y + h - 14 - blockH - (claim > 0 ? 8 : 0) - listTop;
        List<Chapter> chapters = chapters();
        chapterScroll.content(chapters.size() * CHAPTER_STEP, listH);
        int chapterOffset = chapterScroll.offset();
        ui.clip(x, listTop, w, listH);
        int first = Math.max(0, chapterOffset / CHAPTER_STEP);
        int last = Math.min(chapters.size(), first + listH / CHAPTER_STEP + 2);
        for (int i = first; i < last; i++) {
            renderChapterRow(ui, chapters.get(i), i + 1, x + 12, listTop + i * CHAPTER_STEP - chapterOffset, 207);
        }
        ui.unclip();
        if (claim > 0) {
            renderClaimBlock(ui, x + 12, y + h - 14 - 72, 207);
        }
    }

    private void renderChapterRow(Ui ui, Chapter chapter, int number, int x, int y, int w) {
        boolean open = chapter.id().equals(BookState.chapter);
        if (open) {
            ui.box(x, y, w, 45, 9, ui.c("button.bg"), ui.c("button.border"));
        } else if (ui.over(x, y, w, 45)) {
            ui.fill(x, y, w, 45, 9, ui.c("button.bg"));
        }
        int[] counts = chapterCounts(chapter.id());
        String count = counts[0] + " / " + counts[1];
        float countW = ui.width(count, 11.5f, Ui.REGULAR);
        String title = Ui.roman(number) + " · " + Component.translatable(chapter.title()).getString();
        ui.labelFit(title, x + 11, y + 17.5f, w - 22 - countW - 6, 12.5f, Ui.MEDIUM, ui.nameFg());
        ui.labelRight(count, x + w - 11, y + 17.5f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        float ratio = counts[1] == 0 ? 0 : (float) counts[0] / counts[1];
        ui.bar(x + 11, y + 31, w - 22, 4, 2, ratio, ui.c(open && ratio < 1 ? "accent" : "state.ok"));
        ui.hit(x, y, w, 45, () -> BookState.openChapter(chapter.id()));
    }

    private void renderClaimBlock(Ui ui, int x, int y, int w) {
        ui.box(x, y, w, 72, 9, ui.c("slot.bg"), ui.c("slot.border"));
        ui.label(Component.translatable("rysquests.book.rewards_waiting").getString(), x + 11, y + 18, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        for (int i = 0; i < claimStacks.size(); i++) {
            ui.cell(claimStacks.get(i), x + 11 + i * 38, y + 31, 30, 30, 6, 16, ui.c("panel.bg"), ui.c("card.border"));
        }
        String label = Component.translatable("rysquests.book.claim_short").getString();
        float bw = ui.buttonWidth(label, 11.5f, Ui.SEMIBOLD, 10);
        ui.button(label, x + w - 11 - bw, y + 32, bw, 28, 7, 11.5f, Ui.SEMIBOLD, 10, Ui.Style.ACCENT, () -> QuestKeys.sendAll(QuestActionPayload.CLAIM_ALL));
    }

    private void renderCard(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, false, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        Quest quest = BookState.quest == null ? null : book().quests().get(BookState.quest);
        if (quest == null || !quest.chapter().equals(java.util.Optional.ofNullable(BookState.chapter))) {
            return;
        }
        ui.clip(x, y, w, h);
        int content = QuestCard.render(ui, quest, x + 1, y - cardScroll.offset(), w - 1);
        ui.unclip();
        cardScroll.content(content, h);
    }

    int[] chapterCounts(ResourceLocation chapter) {
        int[] counts = new int[2];
        for (Quest quest : questsOf(chapter)) {
            counts[1]++;
            if (ClientProgress.get(quest.id()).completed()) {
                counts[0]++;
            }
        }
        return counts;
    }

    // --- input ---

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (button == 0 && kitHits.click(mouseX, mouseY, button)) {
            return true;
        }
        int x = (int) Math.round(mouseX * 2);
        int y = (int) Math.round(mouseY * 2);
        List<Ui.Hit> list = button == 0 ? hits : button == 1 ? rightHits : List.of();
        for (int i = list.size() - 1; i >= 0; i--) {
            Ui.Hit hit = list.get(i);
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                hit.action().run();
                if (searchBox != null) {
                    searchBox.setFocused(false);
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

    @Override
    protected boolean onDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return activeView().drag(dragX * 2, dragY * 2) || super.onDrag(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX * 2;
        if (x >= chaptersLeft && x < chaptersRight) {
            if (BookState.currentView() == ViewMode.FLOW) {
                railScroll.wheel(scrollY, RAIL_STEP);
            } else {
                chapterScroll.wheel(scrollY, CHAPTER_STEP);
            }
        } else if (x >= cardLeft) {
            if (BookState.currentView() == ViewMode.FLOW) {
                reading.scroll(scrollY, WHEEL);
            } else {
                cardScroll.wheel(scrollY, WHEEL);
            }
        } else {
            activeView().scroll(x, mouseY * 2, scrollY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean typing = searchBox != null && searchBox.isFocused();
        if (typing && key == GLFW.GLFW_KEY_ENTER && !BookState.search.isBlank()) {
            SearchIndex.find(BookState.search).stream().findFirst().ifPresent(BookState::select);
            searchBox.setFocused(false);
            setFocused(null);
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && hasControlDown() && searchBox != null && searchBox.visible) {
            searchBox.setFocused(true);
            setFocused(searchBox);
            return true;
        }
        if (!typing) {
            if (activeView().key(key)) {
                return true;
            }
            if (QuestKeys.OPEN_BOOK.matches(key, scan)) {
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            if (QuestKeys.SUBMIT_READY.matches(key, scan)) {
                QuestKeys.sendAll(QuestActionPayload.SUBMIT_ALL);
                return true;
            }
            if (QuestKeys.PIN.matches(key, scan) && BookState.quest != null) {
                togglePin(BookState.quest);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    static void togglePin(ResourceLocation quest) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of()));
    }

    /** The other view of the map's card: shows the path to the selected quest. */
    void focusOnMap() {
        BookState.view = ViewMode.MAP;
        ((MapView) views.get(ViewMode.MAP)).focusSelected();
    }

    /** Opens the book on a quest, from a link in chat. */
    public static void openAt(ResourceLocation id) {
        Quest quest = book().quests().get(id);
        if (quest != null) {
            BookState.select(quest);
            BookState.view = ViewMode.FLOW;
            Minecraft.getInstance().setScreen(new BookScreen());
        }
    }

    public static void searchForTest(String query) {
        BookState.search = query;
    }

    public static ViewMode viewForTest() {
        return BookState.currentView();
    }

    public static void showForTest(ViewMode view) {
        BookState.view = view;
    }

    public static void selectForTest(ResourceLocation chapter, ResourceLocation quest) {
        BookState.chapter = chapter;
        BookState.quest = quest;
    }
}
