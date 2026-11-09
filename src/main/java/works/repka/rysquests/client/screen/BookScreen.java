package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientDev;
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
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;

/** The quest book window: chapter list, a view of the open chapter, and the reading column. */
public final class BookScreen extends Screen {
    private static final int PAD = 6;
    private static final int ROW = 20;
    private static final int HEADER = 18;
    private static final int READING_MIN_PANEL = 430;
    private static final List<ViewMode> TABS = List.of(ViewMode.HOME, ViewMode.FLOW, ViewMode.MAP, ViewMode.LIST, ViewMode.TIMELINE);
    @Nullable
    private static ItemStack lastHovered;

    private final List<Ui.Hit> hits = new ArrayList<>();
    private final Map<ResourceLocation, List<Quest>> orderCache = new HashMap<>();
    private final Map<ResourceLocation, int[]> chapterProgress = new HashMap<>();
    private final EnumMap<ViewMode, BookView> views = new EnumMap<>(ViewMode.class);
    @Nullable
    private QuestBook cachedFor;
    @Nullable
    private EditBox searchBox;
    private static final long COUNT_REFRESH_MS = 500;
    private long countsAt;
    private int claimCount;
    private int chapterScroll;
    private int readingScroll;
    private int chaptersRight;
    private int readingLeft;

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
        searchBox = new EditBox(font, 0, 0, 80, 12, Component.translatable("rysquests.book.search"));
        searchBox.setBordered(false);
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
                    int have = 0;
                    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                        ItemStack stack = inventory.getItem(slot);
                        if (!stack.isEmpty() && matches(task, stack)) {
                            have += stack.getCount();
                        }
                    }
                    if (have < task.count() - own.task(i)) {
                        return false;
                    }
                    any = true;
                }
            }
            return any;
        }).count();
    }

    /** Whether the player holds what is still missing for a task: the slot frame shows it. */
    static boolean hasEnough(works.repka.rysquests.data.TaskDef task, int value) {
        if (!(task instanceof ItemTask item)) {
            return false;
        }
        var inventory = Minecraft.getInstance().player.getInventory();
        int have = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && matches(item, stack)) {
                have += stack.getCount();
            }
        }
        return have >= item.count() - value;
    }

    private static boolean matches(ItemTask task, ItemStack stack) {
        return task.itemRefs().stream().anyMatch(ref -> ref.tag()
                ? stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ref.id()))
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ref.id()));
    }

    int readyToClaim() {
        return (int) book().quests().values().stream().filter(quest -> BookState.status(quest) == QuestStatus.COMPLETED).count();
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        hits.clear();
        Ui ui = new Ui(g, mouseX, mouseY, hits);
        Theme theme = ThemeManager.get();
        int panelW = Math.round(width * 0.94f);
        int panelH = Math.round(height * 0.92f);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        theme.panel(g, panelX, panelY, panelW, panelH);

        if (BookState.chapter == null || !book().chapters().containsKey(BookState.chapter)) {
            BookState.chapter = book().chapters().keySet().stream().findFirst().orElse(null);
        }
        if (BookState.chapter == null) {
            ui.text(Component.translatable("rysquests.book.empty"), panelX + PAD * 2, panelY + PAD * 2, "text.muted");
            return;
        }
        renderHeader(ui, panelX + PAD, panelY + PAD, panelW - PAD * 2);
        int top = panelY + PAD + HEADER + 3;
        int bottom = panelY + panelH - PAD;
        int chaptersW = Math.min(112, panelW / 4);
        int readingW = panelW >= READING_MIN_PANEL ? Math.min(200, panelW * 2 / 5) : 0;
        int chaptersX = panelX + PAD;
        int viewX = chaptersX + chaptersW + PAD;
        readingLeft = readingW > 0 ? panelX + panelW - PAD - readingW : panelX + panelW - PAD;
        chaptersRight = viewX;
        int viewW = readingLeft - PAD - viewX;

        int sidebarX = theme.flushSidebar ? panelX : chaptersX;
        int sidebarBottom = theme.flushSidebar ? panelY + panelH : bottom;
        theme.sidebar(g, sidebarX, top, chaptersX + chaptersW - sidebarX, sidebarBottom - top);
        renderChapters(ui, chaptersX, top, chaptersW, bottom - top);
        activeView().render(ui, viewX, top, viewW, bottom - top);
        if (readingW > 0) {
            renderReading(ui, readingLeft, top, readingW, bottom - top);
        }
        lastHovered = ui.hoveredStack;
        if (ui.hoveredStack != null) {
            g.renderTooltip(font, ui.hoveredStack, mouseX, mouseY);
        } else if (ui.hoveredText != null) {
            g.renderTooltip(font, ui.hoveredText, mouseX, mouseY);
        }
    }

    private void renderChapters(Ui ui, int x, int y, int w, int h) {
        List<Chapter> chapters = List.copyOf(book().chapters().values());
        chapterScroll = clampScroll(chapterScroll, chapters.size() * ROW, h);
        ui.clip(x, y, w, h);
        int first = chapterScroll / ROW;
        int last = Math.min(chapters.size(), first + h / ROW + 2);
        for (int i = first; i < last; i++) {
            Chapter chapter = chapters.get(i);
            int rowY = y + i * ROW - chapterScroll;
            boolean open = chapter.id().equals(BookState.chapter);
            ui.theme.tab(ui.g, x, rowY, w, ROW - 2, open, ui.over(x, rowY, w, ROW - 2));
            chapter.icon().map(TaskText::stackOf).ifPresent(stack -> ui.plated(stack, x + 1, rowY + 1));
            ui.text(Draw.ellipsize(Component.translatable(chapter.title()).getString(), w - 23), x + 20, rowY + 3, open ? "tab.active.fg" : "tab.inactive.fg");
            int[] counts = chapterCounts(chapter.id());
            ui.bar(x + 20, rowY + 13, w - 26, 2, counts[1] == 0 ? 0 : (float) counts[0] / counts[1], "state.ok");
            ui.hit(x, rowY, w, ROW - 2, () -> BookState.openChapter(chapter.id()));
        }
        ui.unclip();
    }

    private int[] chapterCounts(ResourceLocation chapter) {
        int[] counts = new int[2];
        for (Quest quest : questsOf(chapter)) {
            counts[1]++;
            if (ClientProgress.get(quest.id()).completed()) {
                counts[0]++;
            }
        }
        return counts;
    }

    private void renderHeader(Ui ui, int x, int y, int w) {
        int cursor = x;
        ViewMode current = BookState.currentView();
        List<ViewMode> tabs = new ArrayList<>(TABS);
        if (Minecraft.getInstance().player.hasPermissions(2)) {
            tabs.add(ViewMode.DEV);
        }
        for (ViewMode mode : tabs) {
            Component label = Component.translatable("rysquests.view." + mode.getSerializedName());
            int count = tabCount(mode);
            String badge = count > 0 ? String.valueOf(count) : "";
            int badgeW = badge.isEmpty() ? 0 : font.width(badge) + 9;
            int tabW = font.width(label) + 12 + badgeW;
            boolean over = ui.over(cursor, y, tabW, HEADER - 2);
            ui.theme.tabLine(ui.g, cursor, y, tabW, HEADER - 2, current == mode, over);
            Draw.text(ui.g, label, cursor + 6, y + 4, ui.theme.tabLineFg(current == mode, over));
            if (!badge.isEmpty()) {
                int badgeX = cursor + 6 + font.width(label) + 4;
                ui.theme.chip(ui.g, badgeX, y + 3, badgeW - 4 + 2, 10, "mod");
                ui.text(badge, badgeX + 3, y + 4, "chip.mod.fg");
            }
            ui.hit(cursor, y, tabW, HEADER - 2, () -> BookState.view = mode);
            cursor += tabW + 3;
        }
        if (searchBox != null) {
            int boxW = Math.min(90, x + w - cursor - 4);
            int boxX = x + w - boxW;
            ui.theme.input(ui.g, boxX, y, boxW, HEADER - 2, searchBox.isFocused());
            searchBox.setX(boxX + 4);
            searchBox.setY(y + 4);
            searchBox.setWidth(boxW - 8);
            searchBox.render(ui.g, ui.mouseX, ui.mouseY, 0);
            if (BookState.search.isEmpty() && !searchBox.isFocused()) {
                Draw.magnifier(ui.g, boxX + 4, y + 4, ui.color("text.dim"));
                ui.small(Component.translatable("rysquests.book.search").getString(), boxX + 16, y + 5, "text.dim");
            }
        }
    }

    /** Counters beside section names: rewards waiting, and problems in the book for operators. */
    private int tabCount(ViewMode mode) {
        long now = System.currentTimeMillis();
        if (now - countsAt > COUNT_REFRESH_MS) {
            countsAt = now;
            claimCount = readyToClaim();
        }
        if (mode == ViewMode.HOME) {
            return claimCount;
        }
        if (mode == ViewMode.DEV && ClientDev.report() != null) {
            return ClientDev.report().sections().getFirst().entries().size();
        }
        return 0;
    }

    private void renderReading(Ui ui, int x, int y, int w, int h) {
        ui.theme.card(ui.g, x, y, w, h);
        Quest quest = BookState.quest == null ? null : book().quests().get(BookState.quest);
        if (quest == null) {
            return;
        }
        int inner = w - PAD * 2;
        int left = x + PAD;
        ui.clip(x, y + 1, w, h - 2);
        int cursor = y + PAD - readingScroll;
        if (BookState.currentView() != ViewMode.FLOW) {
            cursor += QuestCard.render(ui, quest, x, cursor - PAD, w) - PAD + 2;
        }
        if (quest.text().isPresent()) {
            ui.small(Component.translatable("rysquests.reading.how").getString(), left, cursor, "text.dim");
            cursor += Ui.LINE;
            cursor = TextBlock.render(ui, Component.translatable(quest.text().get()).getString(), left, cursor, inner, Recipes::showRecipes) + 6;
        }
        cursor = renderBlocked(ui, quest, left, cursor, inner);
        ui.unclip();
        readingScroll = clampScroll(readingScroll, cursor + readingScroll - y + PAD, h);
    }

    private int renderBlocked(Ui ui, Quest quest, int x, int y, int w) {
        List<Quest> blocked = new ArrayList<>();
        for (ResourceLocation id : book().unlocks().getOrDefault(quest.id(), List.of())) {
            Quest next = book().quests().get(id);
            if (next != null && BookState.status(next) == QuestStatus.LOCKED) {
                blocked.add(next);
            }
        }
        if (BookState.status(quest) == QuestStatus.LOCKED) {
            ui.small(Component.translatable("rysquests.reading.locked").getString(), x, y, "text.dim");
            y += Ui.LINE;
            y = ui.wrapped(waitingFor(quest), x, y, w, "text.body") + 6;
        }
        if (blocked.isEmpty()) {
            return y;
        }
        ui.small(Component.translatable("rysquests.reading.next_locked").getString(), x, y, "text.dim");
        y += Ui.LINE;
        for (Quest next : blocked) {
            y = ui.wrapped(Component.translatable(next.title()), x, y, w, "text.muted");
            y = ui.wrapped(waitingFor(next), x, y, w, "text.body") + 3;
        }
        return y;
    }

    private static int clampScroll(int scroll, int content, int view) {
        return Mth.clamp(scroll, 0, Math.max(0, content - view));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Ui.Hit hit = hits.get(i);
                if (mouseX >= hit.x() && mouseX < hit.x() + hit.w() && mouseY >= hit.y() && mouseY < hit.y() + hit.h()) {
                    hit.action().run();
                    if (searchBox != null) {
                        searchBox.setFocused(false);
                        setFocused(null);
                    }
                    return true;
                }
            }
        }
        if (button == 1 && lastHovered != null) {
            Recipes.showUsages(lastHovered);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return activeView().drag(dragX, dragY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= readingLeft) {
            readingScroll -= (int) (scrollY * ROW);
        } else if (mouseX < chaptersRight) {
            chapterScroll -= (int) (scrollY * ROW);
        } else {
            activeView().scroll(mouseX, mouseY, scrollY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean typing = searchBox != null && searchBox.isFocused();
        if (key == GLFW.GLFW_KEY_F && hasControlDown() && searchBox != null) {
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
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, BookState.quest, 0, List.of()));
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    public static void showForTest(ViewMode view) {
        BookState.view = view;
    }

    public static void selectForTest(ResourceLocation chapter, ResourceLocation quest) {
        BookState.chapter = chapter;
        BookState.quest = quest;
    }
}
