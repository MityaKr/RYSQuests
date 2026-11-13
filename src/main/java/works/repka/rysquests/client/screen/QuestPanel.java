package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestPath;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.CommandReward;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.LootTableReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.rysquests.reward.ScriptReward;
import works.repka.rysquests.reward.StageReward;
import works.repka.rysquests.reward.TitleReward;
import works.repka.rysquests.reward.XpReward;
import works.repka.rysquests.task.AdvancementTask;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.FluidTask;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.KillTask;
import works.repka.rysquests.task.ScriptTask;
import works.repka.rysquests.task.StatTask;
import works.repka.rysquests.task.VisitTask;
import works.repka.rysquests.task.XpTask;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.Numbers;

/**
 * The quest panel (plan, section 3, "Quest"; pictures 17, 20, 21, 21b, 22): one panel for the map, the list and "Now". Head, the
 * state in words, the text, "Что сделать" with a row per task, the rewards in groups ("каждому", "один раз на команду"), the choice
 * shown whole, "Откроет", and at the bottom one main button that says what it does: "Как сделать: X" (with RYScope only), "Сдать",
 * "Отметить", "Забрать: ...", "Начать с: ..." for a closed quest; a state in words where nothing is to be pressed.
 *
 * <p>A closed quest shows its way in steps: three nearest steps, "ещё N шагов" and the goal ({@link QuestPath}). The map lights the
 * same way: it hears of it through {@link Events#route}.
 *
 * <pre>
 * private final QuestPanel panel = new QuestPanel(new QuestPanel.Events() {
 *     public void close() { ... back to the chapter card ... }
 *     public void route(QuestPath path) { ... light the way, or nothing when null ... }
 * });
 * panel.render(ui, x, y, DetailPanel.WIDTH, h, quest, QuestPanel.Look.MAP);
 * // scroll(): if (panel.contains(mx, my)) panel.scroll(amount);
 * </pre>
 */
final class QuestPanel {
    /** What the panel asks of the page around it. Everything has a default that works on any page. */
    interface Events {
        /** Show another quest in the panel: a step of the way, an option of "any one of". */
        default void select(Quest quest) {
            BookState.select(quest);
        }

        /** "Показать на карте": the map tab, on this quest. */
        default void showOnMap(Quest quest) {
            BookState.select(quest);
            MapView.showNext(quest.id());
            BookState.section = Chapter.STORY;
            BookState.view = works.repka.rysquests.data.ViewMode.MAP;
        }

        /**
         * The way the panel shows changed: a closed quest was selected (lead the player to it: light {@code path.steps()} on the map)
         * or none is shown any more ({@code null}). Called once per change, not every frame.
         */
        default void route(@Nullable QuestPath path) {
        }

        /** The cross: the map goes back to the chapter card. */
        default void close() {
        }
    }

    /**
     * Where the panel stands.
     *
     * @param closable the cross in the head (map and list); "Now" has none, its panel shows the next step
     * @param mapLink  "Показать на карте" at the bottom of the body (list)
     * @param onMap    the way is lit on the map, and its rubric says so
     * @param tasksFirst tasks and rewards before the text, as the compact layout lays them out
     */
    record Look(boolean closable, boolean mapLink, boolean onMap, boolean tasksFirst) {
        static final Look NOW = new Look(false, false, false, false);
        static final Look MAP = new Look(true, false, true, false);
        /** The map in the compact layout (46): the panel over the canvas, tasks and reward first, the text and "Откроет" under them. */
        static final Look MAP_OVER = new Look(true, false, true, true);
        static final Look LIST = new Look(true, true, false, false);
        static final Look OVER = new Look(true, true, false, true);
    }

    private static final float DESC_PX = 12.5f;
    private static final float DESC_LH = 18.75f;
    private static final int DESC_LINES = 6;
    private static final int NEAR_STEPS = 3;
    private static final int OPENS = 3;
    private static final int GAP = DetailPanel.GAP;

    private final DetailPanel panel = new DetailPanel();
    private final Events events;
    @Nullable
    private ResourceLocation shown;
    private boolean allSteps;
    private boolean moreText;
    @Nullable
    private QuestPath routed;

    QuestPanel(Events events) {
        this.events = events;
    }

    QuestPanel() {
        this(new Events() {
        });
    }

    boolean contains(double x, double y) {
        return panel.contains(x, y);
    }

    void scroll(double amount) {
        panel.scroll(amount);
    }

    /** The way the panel shows now, or null: the quest is open or done. */
    @Nullable
    QuestPath route() {
        return routed;
    }

    /** Nothing shown any more: tells the page the way is gone. */
    void clear() {
        if (shown != null) {
            shown = null;
            setRoute(null);
        }
    }

    // --- the way to a closed quest, shared with the map ---

    private static QuestBook pathBook;
    private static int pathVersion = -1;
    private static final java.util.Map<ResourceLocation, QuestPath> PATHS = new java.util.HashMap<>();

    /** The way to a quest, worked out once per book and progress: what "вести к цели" lights. */
    static QuestPath pathTo(Quest goal) {
        if (pathBook != BookData.book() || pathVersion != ClientProgress.version()) {
            PATHS.clear();
            pathBook = BookData.book();
            pathVersion = ClientProgress.version();
        }
        return PATHS.computeIfAbsent(goal.id(), id -> QuestPath.of(goal, BookData.book().quests(), ClientProgress::get));
    }

    private void setRoute(@Nullable QuestPath path) {
        if (!Objects.equals(path, routed)) {
            routed = path;
            events.route(path);
        }
    }

    // --- drawing ---

    void render(Ui ui, float x, float y, float w, float h, Quest quest, Look look) {
        if (!quest.id().equals(shown)) {
            shown = quest.id();
            panel.reset();
            allSteps = false;
            moreText = false;
        }
        QuestStatus status = BookState.status(quest);
        QuestProgress own = ClientProgress.get(quest.id());
        boolean cooling = own.coolingDown();
        QuestPath path = status == QuestStatus.LOCKED && !cooling && !ClientProgress.isStageLocked(quest.id()) ? pathTo(quest) : null;
        if (path != null && path.steps().isEmpty()) {
            path = null;
        }
        setRoute(path);
        Facts facts = new Facts(quest, own, status, path);
        DetailPanel.Action action = action(facts);
        float actionH = action == null ? 0 : 24 + (action.action() == null ? Ui.lineHeight(12.5f) + 10 : 42) + 1;
        float bodyBottom = y + h - actionH - GAP;
        panel.render(ui, x, y, w, h, (u, bx, by, bw) -> body(u, facts, look, bx, by, bw, bodyBottom - panelOffset(by, y)), action);
        menu(ui, quest);
    }

    // --- the quest's menu "⋯" in the head ---

    private static final float MENU_W = 196;
    private static final float MENU_ROW = 30;
    private boolean menuOpen;
    /** The button "⋯" on screen in this frame, in mockup pixels, or null when it was not drawn. */
    @Nullable
    private float[] menuButton;
    @Nullable
    private ResourceLocation menuQuest;

    private void moreButton(Ui ui, Quest quest, float x, float y) {
        boolean over = ui.over(x, y, 28, 28);
        if (over || menuOpen) {
            ui.fill(x, y, 28, 28, 7, ui.c("slot.hover"));
        }
        int color = ui.c(over || menuOpen ? "text.primary" : "text.muted");
        for (int i = -1; i <= 1; i++) {
            ui.circle(x + 14 + i * 6 - 1.7f, y + 14 - 1.7f, 3.4f, color);
        }
        ui.tooltipIfOver(x, y, 28, 28, Component.translatable("rysquests.qmenu.tip"));
        ui.hit(x, y, 28, 28, () -> menuOpen = !menuOpen);
        menuButton = new float[] {x, y, 28, 28};
        if (!quest.id().equals(menuQuest)) {
            menuQuest = quest.id();
            menuOpen = false;
        }
    }

    /** One line of the menu: words, and what it does, or null when it cannot be done now. */
    private record MenuItem(String label, @Nullable Runnable action) {
    }

    /** "Сбросить прогресс" for anyone in the team; "Завершить", "Скопировать id" and "Открыть в редакторе" for an operator. */
    private List<MenuItem> menuItems(Quest quest) {
        List<MenuItem> items = new ArrayList<>();
        QuestProgress own = ClientProgress.get(quest.id());
        boolean started = own.completed() || own.nextAt() > 0 || !own.claimed().isEmpty() || own.tasks().stream().anyMatch(value -> value > 0);
        String title = QuestFacts.plain(Component.translatable(quest.title()).getString());
        items.add(new MenuItem(t("rysquests.qmenu.reset"), started ? () -> ask("rysquests.qmenu.reset_title", t("rysquests.qmenu.reset_text", title),
                "rysquests.qmenu.reset_yes", () -> QuestFacts.send(QuestActionPayload.RESET, quest, 0, List.of())) : null));
        if (BookRole.operator()) {
            items.add(new MenuItem(t("rysquests.qmenu.complete"), own.completed() ? null : () -> ask("rysquests.qmenu.complete_title",
                    t("rysquests.qmenu.complete_text", title), "rysquests.qmenu.complete_yes", () -> QuestFacts.send(QuestActionPayload.COMPLETE, quest, 0, List.of()))));
            items.add(new MenuItem(t("rysquests.qmenu.copy_id"), () -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(quest.id().toString());
                works.repka.rysquests.client.ClientMessages.show("rysquests.qmenu.copied", List.of(quest.id().toString()), true);
            }));
            items.add(new MenuItem(t("rysquests.qmenu.editor"), () -> {
                if (Minecraft.getInstance().screen instanceof BookScreen screen) {
                    screen.toggleEditor();
                    EditorChecks.showOnMap(quest.id());
                }
            }));
        }
        return items;
    }

    private static void ask(String titleKey, String text, String yesKey, Runnable yes) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            Runnable answer = screen.answer(yes);
            yesForTest = answer;
            screen.ask(new Kit.Dialog(t(titleKey)).text(text)
                    .no(t("rysquests.qmenu.cancel"), screen.answer(() -> { }))
                    .yes(t(yesKey), answer));
        }
    }

    /** The main answer of the last question the menu asked, for the autotest to press. */
    @Nullable
    static Runnable yesForTest;

    /** The open menu under its button, over everything; a click elsewhere closes it. Drawn after the panel. */
    private void menu(Ui ui, Quest quest) {
        float[] button = menuButton;
        menuButton = null;
        if (!menuOpen || button == null) {
            return;
        }
        List<MenuItem> items = menuItems(quest);
        int pad = 4;
        float h = items.size() * MENU_ROW + 2 * pad * 2;
        int[] box = Kit.popover(ui.g, ui.theme, Math.round(button[0] / 2), Math.round(button[1] / 2), 14, 14, Math.round(MENU_W / 2), Math.round(h / 2),
                Kit.Side.BELOW, null, (g, bx, by, bw, bh) -> {
                });
        float px = box[0] * 2f;
        float py = box[1] * 2f + pad * 2;
        ui.kitHits.unclip();
        ui.kitHits.add(0, 0, ui.g.guiWidth(), ui.g.guiHeight(), b -> menuOpen = false);
        ui.g.pose().pushPose();
        ui.g.pose().translate(0, 0, 450);
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            float ry = py + i * MENU_ROW;
            float rx = px + pad * 2;
            float rw = MENU_W - pad * 4;
            boolean hot = item.action() != null && ui.over(rx, ry, rw, MENU_ROW);
            if (hot) {
                ui.fill(rx, ry, rw, MENU_ROW, 7, Draw.alpha(ui.c("row.selected.bg"), 0.6f));
            }
            int fg = item.action() == null ? Draw.alpha(ui.c("text.body"), 0.45f) : ui.c(hot ? "text.primary" : "text.body");
            ui.label(item.label(), rx + 10, ry + MENU_ROW / 2f, 12.5f, Ui.REGULAR, fg);
            if (item.action() != null) {
                Runnable action = item.action();
                ui.kitHits.add(Math.round(rx / 2), Math.round(ry / 2), Math.round(rw / 2), Math.round(MENU_ROW / 2), b -> {
                    if (b == 0) {
                        menuOpen = false;
                        action.run();
                    }
                });
            }
        }
        ui.g.pose().popPose();
    }

    // --- for tests ---

    void menuForTest(boolean open) {
        menuOpen = open;
    }

    /** The menu's lines and whether each can be pressed, for the autotest. */
    List<String> menuItemsForTest(Quest quest) {
        return menuItems(quest).stream().map(item -> item.label() + (item.action() == null ? " (off)" : "")).toList();
    }

    /** Presses a line of the menu as a click would, for the autotest. */
    void menuPressForTest(Quest quest, int index) {
        Runnable action = menuItems(quest).get(index).action();
        menuOpen = false;
        if (action != null) {
            action.run();
        }
    }

    /** How far the body is scrolled: its top minus the panel's own top padding. */
    private static float panelOffset(float bodyTop, float panelTop) {
        return bodyTop - (panelTop + DetailPanel.PAD_TOP);
    }

    /** The panel's facts about a quest outside of drawing: for the compact card of "Now", which carries the main button itself. */
    private Facts factsOf(Quest quest) {
        QuestStatus status = BookState.status(quest);
        QuestProgress own = ClientProgress.get(quest.id());
        QuestPath path = status == QuestStatus.LOCKED && !own.coolingDown() && !ClientProgress.isStageLocked(quest.id()) ? pathTo(quest) : null;
        return new Facts(quest, own, status, path != null && path.steps().isEmpty() ? null : path);
    }

    /** The panel's main button for a quest, the same one it draws at its bottom: "Как сделать: X", "Забрать: ...". */
    @Nullable
    DetailPanel.Action actionFor(Quest quest) {
        return action(factsOf(quest));
    }

    /** "Ток · основной · шаг 7 из 15" for a quest. */
    static String subtitleOf(Quest quest) {
        QuestStatus status = BookState.status(quest);
        return subtitle(new Facts(quest, ClientProgress.get(quest.id()), status, null));
    }

    /** The state line of the first unfinished task ("в инвентаре 0 из 1"), or null. */
    @Nullable
    String firstLine(Quest quest) {
        Facts facts = factsOf(quest);
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (!facts.finished(i)) {
                return taskRow(facts, i, quest.tasks().get(i), false, false).lineForTest();
            }
        }
        return null;
    }

    /** What the quest gives, in words: "25 опыта и 1 монета". */
    static String rewardsSummary(Quest quest) {
        return summary(quest, false);
    }

    /** What the panel knows about the quest this frame. */
    private static final class Facts {
        final Quest quest;
        final QuestProgress own;
        final QuestStatus status;
        @Nullable
        final QuestPath path;
        final boolean open;
        final boolean done;
        final boolean team;
        final List<Integer> givable = new ArrayList<>();
        final List<Integer> checks = new ArrayList<>();
        final List<Integer> short_ = new ArrayList<>();

        Facts(Quest quest, QuestProgress own, QuestStatus status, @Nullable QuestPath path) {
            this.quest = quest;
            this.own = own;
            this.status = status;
            this.path = path;
            open = status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
            done = status == QuestStatus.COMPLETED || status == QuestStatus.CLAIMED;
            team = ClientProgress.teamSize() > 1;
            for (int i = 0; i < quest.tasks().size(); i++) {
                TaskDef task = quest.tasks().get(i);
                if (finished(i)) {
                    continue;
                }
                if (open && QuestFacts.takes(task) && QuestFacts.canGive(task) > 0) {
                    givable.add(i);
                }
                if (open && task instanceof CheckboxTask) {
                    checks.add(i);
                }
                if (shortage(task, value(i)) > 0) {
                    short_.add(i);
                }
            }
        }

        int value(int index) {
            return Math.min(own.task(index), quest.tasks().get(index).target());
        }

        boolean finished(int index) {
            return own.completed() || own.task(index) >= quest.tasks().get(index).target();
        }

        /** How many items the inventory lacks for an item task; 0 for any other task. */
        static int shortage(TaskDef task, int value) {
            if (task instanceof ItemTask item) {
                int missing = task.target() - value;
                return item.mode() == ItemTask.Mode.CRAFT ? missing : Math.max(0, missing - BookData.held(item));
            }
            return 0;
        }
    }

    private float body(Ui ui, Facts f, Look look, float x, float y, float w, float bottom) {
        Quest quest = f.quest;
        float pen = y;
        ItemStack icon = QuestFacts.icon(quest);
        Runnable close = look.closable() ? events::close : null;
        float headH = DetailPanel.header(ui, x, pen, w, icon.isEmpty() ? null : icon, QuestFacts.plain(Component.translatable(quest.title()).getString()),
                subtitle(f), () -> BookData.togglePin(quest.id()), ClientProgress.pinned().contains(quest.id()), close, (bx, by) -> moreButton(ui, quest, bx, by));
        if (f.status == QuestStatus.LOCKED && !icon.isEmpty()) {
            ui.veil(x + 1, pen + 1, 44, 44, 6, ui.c("slot.bg"), 0.55f);
        }
        pen += headH + GAP;
        pen = state(ui, f, look, x, pen, w);
        if (!look.tasksFirst()) {
            pen = description(ui, quest, x, pen, w);
        }
        pen = tasks(ui, f, x, pen, w);
        pen = repeatNote(ui, f, x, pen, w);
        pen = rewards(ui, f, x, pen, w);
        if (look.tasksFirst()) {
            pen = description(ui, quest, x, pen, w);
        }
        pen = opens(ui, f, x, pen, w);
        if (look.mapLink()) {
            String link = t("rysquests.qpanel.show_on_map");
            float lh = Ui.lineHeight(12.5f);
            float ly = Math.max(pen, bottom - lh);
            float lw = ui.width(link, 12.5f, Ui.MEDIUM);
            boolean over = ui.over(x, ly, lw, lh);
            ui.label(link, x, ly + lh / 2f, 12.5f, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
            ui.hit(x, ly, lw, lh, () -> events.showOnMap(quest));
            pen = ly + lh + GAP;
        }
        return pen - GAP - y;
    }

    // --- head ---

    private static String subtitle(Facts f) {
        Quest quest = f.quest;
        List<String> parts = new ArrayList<>();
        Chapter chapter = QuestFacts.chapter(quest);
        if (chapter != null) {
            parts.add(QuestFacts.plain(Component.translatable(chapter.title()).getString()));
        }
        boolean offMap = chapter != null && Sidebar.isOffMap(chapter);
        String group = offMap ? groupTitle(chapter, quest) : null;
        if (group != null) {
            parts.add(group.toLowerCase(java.util.Locale.ROOT));
        } else {
            parts.add(t(quest.options().milestone() ? "rysquests.qpanel.sub.milestone"
                    : quest.options().optional() ? "rysquests.qpanel.sub.side" : "rysquests.qpanel.sub.main"));
        }
        if (quest.options().repeatable().isPresent() && f.status != QuestStatus.LOCKED) {
            parts.add(t("rysquests.qpanel.sub.repeat"));
            return String.join(" · ", parts);
        }
        if (!offMap && QuestFacts.isMain(quest) && !quest.options().milestone() && f.status != QuestStatus.LOCKED) {
            int[] step = QuestFacts.step(quest);
            if (step[0] > 0) {
                parts.add(t("rysquests.qpanel.sub.step", step[0], step[1]));
            }
        }
        if (f.done) {
            parts.add(t(f.team ? "rysquests.qpanel.sub.done_team" : "rysquests.qpanel.sub.done"));
        } else if (f.status == QuestStatus.LOCKED) {
            parts.add(t("rysquests.qpanel.sub.locked"));
        }
        return String.join(" · ", parts);
    }

    @Nullable
    static String groupTitle(Chapter chapter, Quest quest) {
        if (quest.group().isEmpty()) {
            return null;
        }
        return chapter.groups().stream().filter(group -> group.id().equals(quest.group().get())).findFirst()
                .map(group -> QuestFacts.plain(Component.translatable(group.title()).getString())).orElse(null);
    }

    // --- the state in words under the head ---

    private float state(Ui ui, Facts f, Look look, float x, float y, float w) {
        Quest quest = f.quest;
        if (f.status == QuestStatus.COMPLETED) {
            if (f.team) {
                return note(ui, Icons.Shape.USERS, "text.secondary", t("rysquests.qpanel.team_note"), x, y, w) + GAP;
            }
            String words = t(QuestFacts.hasUnclaimedChoice(quest) ? "rysquests.qpanel.done_pick" : "rysquests.qpanel.done_note");
            return note(ui, Icons.Shape.CHECK, "state.ok", words, x, y, w) + GAP;
        }
        if (f.status == QuestStatus.LOCKED) {
            if (f.own.coolingDown()) {
                String words = t("rysquests.qpanel.repeat_wait", times(f.own.times()), waitText(f.own.nextAt()));
                return note(ui, Icons.Shape.REPEAT, "text.secondary", words, x, y, w) + GAP;
            }
            if (f.path == null) {
                return lockbox(ui, BookData.waitingFor(quest).getString() + ".", 0, null, x, y, w) + GAP;
            }
            if (QuestPath.anyOf(quest)) {
                return anyOf(ui, f, x, y, w);
            }
            return way(ui, f, look, x, y, w);
        }
        if (f.open && QuestPath.anyOf(quest)) {
            return openedVia(ui, f, x, y, w);
        }
        return y;
    }

    /** A plain note: icon and words in a quiet box (mockup {@code .note}). @return the y below it */
    private static float note(Ui ui, Icons.Shape icon, String iconToken, String words, float x, float y, float w) {
        TextBlock.Look look = new TextBlock.Look(DESC_PX, 18.1f, ui.c("text.secondary"), ui.c("text.primary"));
        float textW = w - 24 - 15 - 10;
        float textH = measure(ui, words, textW, look);
        float h = Math.max(15, textH) + 20;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        float iconY = y + 10 + (Math.min(textH, 18.1f) - 15) / 2f;
        if (icon == Icons.Shape.CHECK) {
            ui.check(x + 12, iconY + 1, 13, ui.c(iconToken));
        } else {
            ui.icon(icon, x + 12, iconY, 15, ui.c(iconToken));
        }
        TextBlock.render(ui, words, x + 12 + 15 + 10, y + 10, textW, look, Recipes::showRecipes);
        return y + h;
    }

    /** The closed box: a padlock and words, and the options of "any one of" inside it. @return the y below it */
    private float lockbox(Ui ui, String words, float innerH, @Nullable InnerDrawer inner, float x, float y, float w) {
        TextBlock.Look look = new TextBlock.Look(DESC_PX, 18.1f, ui.c("text.secondary"), ui.c("text.primary"));
        float textW = w - 24 - 14 - 8;
        float textH = measure(ui, words, textW, look);
        float h = 10 + textH + (inner == null ? 0 : 8 + innerH) + 10;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        ui.lock(x + 12, y + 10 + (Math.min(textH, 18.1f) - 13) / 2f, 13, ui.c("text.secondary"));
        TextBlock.render(ui, words, x + 12 + 14 + 8, y + 10, textW, look, Recipes::showRecipes);
        if (inner != null) {
            inner.draw(x + 12, y + 10 + textH + 8, w - 24);
        }
        return y + h;
    }

    @FunctionalInterface
    private interface InnerDrawer {
        void draw(float x, float y, float w);
    }

    /** A closed quest: "Закрыт. Осталось 7 шагов", then the way in steps (pictures 21b, 22). */
    private float way(Ui ui, Facts f, Look look, float x, float y, float w) {
        QuestPath path = f.path;
        Quest first = path.first();
        boolean firstOpen = Epochs.isOpen(first);
        String count = Ui.counted("rysquests.qpanel.plural.step", path.count());
        String words = t(firstOpen ? "rysquests.qpanel.locked_way" : "rysquests.qpanel.locked_way_wait", "**" + count + "**");
        float pen = lockbox(ui, words, 0, null, x, y, w) + GAP;
        List<String> right = new ArrayList<>();
        if (path.done() > 0) {
            right.add(t("rysquests.qpanel.way_done", path.done()));
        }
        if (look.onMap()) {
            right.add(t("rysquests.qpanel.way_lit"));
        }
        pen += DetailPanel.rubric(ui, x, pen, w, t("rysquests.qpanel.way"), right.isEmpty() ? null : String.join(" · ", right)) + 2;
        List<Quest> steps = path.steps();
        int n = steps.size();
        List<Quest> near;
        if (allSteps || n <= NEAR_STEPS + 1) {
            near = steps.subList(0, n - 1);
        } else {
            near = steps.subList(0, NEAR_STEPS);
        }
        for (int i = 0; i < near.size(); i++) {
            Quest step = near.get(i);
            String state = i == 0 && firstOpen ? t("rysquests.qpanel.can_do") : null;
            pen += stepRow(ui, i + 1, step, state, i == 0, x, pen, w) + 2;
        }
        int rest = n - 1 - near.size();
        if (rest > 0) {
            float rh = 34;
            float cy = pen + rh / 2f;
            ui.chevron(true, x + 10, cy, 13, ui.c("text.muted"));
            ui.label(t("rysquests.qpanel.more_steps", Ui.counted("rysquests.qpanel.plural.step", rest)), x + 10 + 13 + 8, cy, 12, Ui.REGULAR, ui.c("text.muted"));
            String all = t("rysquests.qpanel.show_all");
            float aw = ui.width(all, 12, Ui.MEDIUM);
            boolean over = ui.over(x + w - 10 - aw, pen, aw + 10, rh);
            ui.label(all, x + w - 10 - aw, cy, 12, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
            ui.hit(x, pen, w, rh, () -> allSteps = true);
            pen += rh + 2;
        }
        pen += stepRow(ui, n, steps.get(n - 1), t("rysquests.qpanel.goal"), false, x, pen, w);
        pen += GAP;
        // The other way: a step that "any one of" chose, and what could stand in its place.
        for (QuestPath.Choice choice : path.choices()) {
            int index = steps.indexOf(choice.taken().getFirst());
            if (index >= 0 && index < n - 1) {
                String names = quoted(choice.others(), t("rysquests.qpanel.or"));
                pen = altWay(ui, t("rysquests.qpanel.other_way_step", index + 1, names), x, pen, w) + GAP;
                break;
            }
        }
        return pen;
    }

    /** A step of the way: its number in a circle, icon, title, the state on the right (mockup {@code .lrow.flat.tight}). */
    private float stepRow(Ui ui, int number, Quest step, @Nullable String state, boolean selected, float x, float y, float w) {
        boolean shownStep = BookData.shown(step);
        ListRow row = ListRow.of(shownStep ? QuestFacts.plain(Component.translatable(step.title()).getString()) : "???")
                .icon(QuestFacts.icon(step)).iconSize(28).look(ListRow.Look.TIGHT).selected(selected)
                .locked(!Epochs.isOpen(step) && !ClientProgress.get(step.id()).everDone() && !selected)
                .lead(22, (u, lx, cy) -> stepNumber(u, number, lx, cy))
                .onClick(shownStep ? () -> events.select(step) : null);
        if (state != null) {
            row.right(null, state, number == 1 ? "text.primary" : "text.muted");
        }
        return row.render(ui, x, y, w);
    }

    private static void stepNumber(Ui ui, int number, float x, float cy) {
        ui.box(x, cy - 11, 22, 22, 11, ui.c("button.bg"), ui.c("button.border"));
        ui.labelCentered(String.valueOf(number), x + 11, cy, 11.5f, Ui.SEMIBOLD, ui.c("text.secondary"));
    }

    /** A closed quest that needs any one of several: the options with their steps, the nearest marked (picture 21b). */
    private float anyOf(Ui ui, Facts f, float x, float y, float w) {
        Quest quest = f.quest;
        List<QuestPath.Option> options = QuestPath.options(quest, BookData.book().quests(), ClientProgress::get);
        int wanted = QuestPath.needs(quest, ClientProgress::get);
        String words = wanted <= 1 ? t("rysquests.qpanel.locked_any") : t("rysquests.qpanel.locked_some", wanted);
        float rowH = 36;
        float innerH = options.size() * (rowH + 6) - 6;
        QuestPath.Option nearest = options.stream().filter(option -> !option.done()).findFirst().orElse(null);
        float pen = lockbox(ui, words, innerH, (ix, iy, iw) -> {
            float ry = iy;
            for (QuestPath.Option option : options) {
                boolean near = option == nearest;
                String state = option.done() ? t("rysquests.qlist.state_done")
                        : near ? t("rysquests.qpanel.nearest", Ui.counted("rysquests.qpanel.plural.step", option.cost()))
                        : Ui.counted("rysquests.qpanel.plural.step", option.cost());
                optionRow(ui, option.quest(), state, near, ix, ry, iw, rowH);
                ry += rowH + 6;
            }
        }, x, y, w) + GAP;
        return altWay(ui, t("rysquests.qpanel.other_way_rest"), x, pen, w) + GAP;
    }

    /** One option of "any one of" (mockup {@code .lk}). */
    private void optionRow(Ui ui, Quest option, String state, boolean near, float x, float y, float w, float h) {
        boolean known = BookData.shown(option);
        boolean over = ui.over(x, y, w, h);
        ui.box(x, y, w, h, 9, over ? ui.c("tab.active.bg") : ui.c("card.bg"), ui.c(near ? "text.secondary" : "panel.border"));
        float cy = y + h / 2f;
        ui.box(x + 4, cy - 14, 28, 28, 6, ui.c("slot.bg"), ui.c("slot.border"));
        ui.item(QuestFacts.icon(option), x + 8, cy - 10, 20);
        float stateW = ui.width(state, 11.5f, Ui.REGULAR);
        ui.labelRight(state, x + w - 10, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.labelFit(known ? QuestFacts.plain(Component.translatable(option.title()).getString()) : "???", x + 4 + 28 + 9, cy, w - 4 - 28 - 9 - 10 - stateW - 8,
                12.5f, Ui.REGULAR, ui.c("text.primary"));
        if (known) {
            ui.hit(x, y, w, h, () -> events.select(option));
        }
    }

    /** An open quest that needed any one of several: which one opened it, and that the rest is not needed (picture 21b). */
    private float openedVia(Ui ui, Facts f, float x, float y, float w) {
        List<Quest> doneOnes = new ArrayList<>();
        List<Quest> others = new ArrayList<>();
        for (ResourceLocation id : f.quest.requires()) {
            Quest need = BookData.book().quests().get(id);
            if (need == null || !BookData.shown(need)) {
                continue;
            }
            (ClientProgress.get(id).everDone() ? doneOnes : others).add(need);
        }
        if (doneOnes.isEmpty() || others.isEmpty()) {
            return y;
        }
        String words = t("rysquests.qpanel.opened_via", "**" + quoted(doneOnes, ", ") + "**");
        String rest = t("rysquests.qpanel.other_way_left", quoted(others, ", "));
        TextBlock.Look look = new TextBlock.Look(DESC_PX, 18.1f, ui.c("text.secondary"), ui.c("text.primary"));
        TextBlock.Look small = new TextBlock.Look(12, 17.4f, ui.c("text.muted"));
        float textW = w - 24 - 14 - 8;
        float h1 = measure(ui, words, textW, look);
        float h2 = measure(ui, rest, w - 24, small);
        float h = 10 + h1 + 6 + h2 + 10;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        ui.check(x + 12, y + 10 + (Math.min(h1, 18.1f) - 13) / 2f + 1, 13, ui.c("state.ok"));
        TextBlock.render(ui, words, x + 12 + 14 + 8, y + 10, textW, look, Recipes::showRecipes);
        TextBlock.render(ui, rest, x + 12, y + 10 + h1 + 6, w - 24, small, Recipes::showRecipes);
        return y + h + GAP;
    }

    /** "Другой путь." in a dashed box with the route icon (mockup {@code .altway}). @return the y below it */
    private static float altWay(Ui ui, String words, float x, float y, float w) {
        String text = "**" + t("rysquests.qpanel.other_way_title") + "** " + words;
        TextBlock.Look look = new TextBlock.Look(12, 17.4f, ui.c("text.secondary"), ui.c("text.primary"));
        float textW = w - 22 - 14 - 9;
        float textH = measure(ui, text, textW, look);
        float h = 8 + textH + 8;
        ui.dashed(x, y, w, h, ui.c("button.border"));
        ui.icon(Icons.Shape.ROUTE, x + 11, y + 8 + 2, 14, ui.c("text.muted"));
        TextBlock.render(ui, text, x + 11 + 14 + 9, y + 8, textW, look, Recipes::showRecipes);
        return y + h;
    }

    private float repeatNote(Ui ui, Facts f, float x, float y, float w) {
        if (f.quest.options().repeatable().isEmpty() || f.own.times() <= 0 || f.status == QuestStatus.LOCKED) {
            return y;
        }
        return note(ui, Icons.Shape.REPEAT, "text.secondary", t("rysquests.qpanel.repeat_now", times(f.own.times())), x, y, w) + GAP;
    }

    // --- text ---

    private float description(Ui ui, Quest quest, float x, float y, float w) {
        if (quest.text().isEmpty()) {
            return y;
        }
        String key = quest.text().get();
        String text = Component.translatable(key).getString();
        // a text key nobody translated ("quest.repys.….text") is not shown to the player as it is: no text is better than a key
        if (text.isBlank() || text.equals(key) && key.indexOf(' ') < 0 && key.indexOf('.') > 0 && !BookRole.operator()) {
            return y;
        }
        TextBlock.Look look = new TextBlock.Look(DESC_PX, DESC_LH, ui.c("text.secondary"), ui.c("text.primary"));
        float full = measure(ui, text, w, look);
        float limit = DESC_LINES * DESC_LH;
        if (moreText || full <= limit + DESC_LH) {
            return TextBlock.render(ui, text, x, y, w, look, Recipes::showRecipes) + GAP;
        }
        float cut = (DESC_LINES - 1) * DESC_LH;
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(cut));
        TextBlock.render(ui, text, x, y, w, look, Recipes::showRecipes);
        ui.unclip();
        String more = t("rysquests.qpanel.more_text");
        float mw = ui.width(more, DESC_PX, Ui.MEDIUM);
        float my = y + cut;
        boolean over = ui.over(x, my, mw, DESC_LH);
        ui.label("… ", x, my + DESC_LH / 2f, DESC_PX, Ui.REGULAR, ui.c("text.secondary"));
        float dots = ui.width("… ", DESC_PX, Ui.REGULAR);
        ui.label(more, x + dots, my + DESC_LH / 2f, DESC_PX, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
        ui.hit(x, my, dots + mw, DESC_LH, () -> moreText = true);
        return my + DESC_LH + GAP;
    }

    // --- tasks ---

    private float tasks(Ui ui, Facts f, float x, float y, float w) {
        Quest quest = f.quest;
        if (quest.tasks().isEmpty()) {
            return y;
        }
        float pen = y;
        int done = QuestFacts.tasksDone(quest, f.own);
        pen += DetailPanel.rubric(ui, x, pen, w, t("rysquests.qpanel.tasks"), t("rysquests.count.of", done, quest.tasks().size())) + 6;
        // Done tasks fold into one line each when more than three are done, or when a choice of rewards waits below them.
        boolean fold = done > 3 || f.status == QuestStatus.COMPLETED && QuestFacts.hasUnclaimedChoice(quest);
        boolean rowButtons = f.givable.size() + f.checks.size() > 1;
        boolean needLines = f.short_.size() > 1;
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            if (fold && f.finished(i)) {
                pen += folded(ui, taskTitle(quest, task), x, pen, w) + 6;
                continue;
            }
            pen += taskRow(f, i, task, rowButtons, needLines).render(ui, x, pen, w) + 6;
        }
        return pen - 6 + GAP;
    }

    /** A done task folded to one line: a green check and its name (mockup {@code .tdone}). */
    private static float folded(Ui ui, String title, float x, float y, float w) {
        float h = Ui.lineHeight(12) + 2;
        float cy = y + h / 2f;
        ui.check(x + 4, cy - 6, 12, ui.c("state.ok"));
        ui.labelFit(title, x + 4 + 14 + 8, cy, w - 30, 12, Ui.REGULAR, ui.c("text.muted"));
        return h;
    }

    private TaskRow taskRow(Facts f, int index, TaskDef task, boolean rowButtons, boolean needLines) {
        Quest quest = f.quest;
        int value = f.value(index);
        boolean done = f.finished(index);
        int target = task.target();
        boolean teamCounts = f.team;
        TaskRow row = TaskRow.of(taskTitle(quest, task));
        String line;
        TaskRow.State state = done ? TaskRow.State.DONE : TaskRow.State.PLAIN;
        switch (task) {
            case ItemTask item -> {
                int held = BookData.held(item);
                ItemStack stack = TaskText.icon(item);
                String kinds = kinds(item);
                if (item.mode() == ItemTask.Mode.CONSUME) {
                    int remaining = target - value;
                    if (done) {
                        line = t("rysquests.qpanel.line.ready");
                    } else if (held > 0) {
                        line = value > 0 ? t("rysquests.qpanel.line.given_can", n(value), n(target), n(held)) : t("rysquests.qpanel.line.can_give", n(held), n(target));
                        state = TaskRow.State.READY;
                    } else {
                        line = value > 0 ? t("rysquests.qpanel.line.given", n(value), n(target), n(held)) : t("rysquests.qpanel.line.have_plain", n(held), n(target));
                    }
                    row.item(stack, target, done || held >= remaining ? TaskRow.Slot.HAVE : TaskRow.Slot.MISSING);
                    if (!done && value > 0 && target > 1) {
                        row.progress((float) value / target);
                    }
                    if (!done && f.givable.contains(index) && rowButtons) {
                        row.button(t("rysquests.qpanel.give_row"), () -> QuestFacts.send(QuestActionPayload.SUBMIT, quest, index, List.of()));
                    }
                } else if (item.mode() == ItemTask.Mode.CRAFT) {
                    line = done ? t("rysquests.qpanel.line.ready") : t("rysquests.qpanel.line.crafted", n(value), n(target));
                    row.item(stack, target, done ? TaskRow.Slot.HAVE : TaskRow.Slot.PLAIN);
                    if (!done && target > 1 && value > 0) {
                        row.progress((float) value / target);
                    }
                } else {
                    line = done ? t("rysquests.qpanel.line.kept") : t("rysquests.qpanel.line.have", n(Math.min(held, target)), n(target));
                    if (!done && kinds != null) {
                        line = t("rysquests.qpanel.line.have_plain", n(Math.min(held, target)), n(target)) + kinds;
                    }
                    row.item(stack, target, done || held >= target ? TaskRow.Slot.HAVE : TaskRow.Slot.MISSING);
                }
                int shortage = Facts.shortage(task, value);
                if (!done && needLines && shortage > 0) {
                    boolean viewer = Recipes.available() && !stack.isEmpty();
                    row.need(t("rysquests.qpanel.need", n(shortage)), viewer ? t("rysquests.qpanel.how") : null, viewer ? () -> Recipes.showRecipes(stack) : null);
                }
            }
            case FluidTask fluid -> {
                int held = fluid.amountIn(Minecraft.getInstance().player.getInventory());
                ItemStack bucket = new ItemStack(BuiltInRegistries.FLUID.get(fluid.fluid()).getBucket());
                boolean give = fluid.mode() == ItemTask.Mode.CONSUME;
                if (done) {
                    line = t("rysquests.qpanel.line.ready");
                } else {
                    String amounts = t(give ? "rysquests.qpanel.line.fluid_give" : "rysquests.qpanel.line.fluid_have", buckets(give ? value + held : held), bucketsOf(target));
                    line = amounts;
                    if (give && held > 0) {
                        state = TaskRow.State.READY;
                    }
                }
                row.item(bucket, 1, done || held >= target - (give ? value : 0) ? TaskRow.Slot.HAVE : TaskRow.Slot.PLAIN);
                if (!done && give && value > 0) {
                    row.progress((float) value / target);
                }
                if (!done && f.givable.contains(index) && rowButtons) {
                    row.button(t("rysquests.qpanel.give_row"), () -> QuestFacts.send(QuestActionPayload.SUBMIT, quest, index, List.of()));
                }
            }
            case KillTask kill -> {
                row.glyph(Icons.Shape.SKULL);
                line = done ? t("rysquests.qpanel.line.ready") : t("rysquests.qpanel.line.kills", n(value), n(target)) + (teamCounts ? t("rysquests.qpanel.line.team_counts") : "");
                if (!done && target > 1 && value > 0) {
                    row.progress((float) value / target);
                }
            }
            case StatTask stat -> {
                row.glyph(Icons.Shape.CHART);
                line = done ? t("rysquests.qpanel.line.ready") : t("rysquests.qpanel.line.stat", n(value), n(target));
                if (!done && value > 0) {
                    row.progress((float) value / target);
                }
            }
            case VisitTask visit -> {
                row.glyph(Icons.Shape.COMPASS);
                line = done ? t("rysquests.qpanel.line.ready") : visit.structure().isPresent() ? t("rysquests.qpanel.line.find")
                        : visit.biome().isPresent() || visit.dimension().isPresent() ? t("rysquests.qpanel.line.visit") : t("rysquests.qpanel.line.place");
            }
            case XpTask xp -> {
                int held = xp.have(Minecraft.getInstance().player);
                row.item(new ItemStack(Items.EXPERIENCE_BOTTLE), 1, TaskRow.Slot.PLAIN);
                String unit = xp.points() ? Ui.counted("rysquests.qpanel.plural.point_gen", target) : Ui.counted("rysquests.qpanel.plural.level_gen", target);
                line = done ? t("rysquests.qpanel.line.ready") : t("rysquests.qpanel.line.xp", n(xp.consume() ? value + held : held), unit);
                if (!done && target > 0) {
                    row.progress(Math.min(1f, (float) (xp.consume() ? value + held : held) / target));
                }
                if (!done && xp.consume() && held > 0) {
                    state = TaskRow.State.READY;
                }
                if (!done && f.givable.contains(index) && rowButtons) {
                    row.button(t("rysquests.qpanel.give_row"), () -> QuestFacts.send(QuestActionPayload.SUBMIT, quest, index, List.of()));
                }
            }
            case CheckboxTask check -> {
                row.checkbox(done);
                line = done ? t("rysquests.qpanel.line.ready") : t(quest.options().page() ? "rysquests.qpanel.line.check_page" : "rysquests.qpanel.line.check");
                if (!done && f.open) {
                    state = TaskRow.State.READY;
                    row.onClick(() -> QuestFacts.send(QuestActionPayload.CHECK, quest, index, List.of()));
                    if (rowButtons) {
                        row.button(t("rysquests.qpanel.check_row"), () -> QuestFacts.send(QuestActionPayload.CHECK, quest, index, List.of()));
                    }
                }
            }
            case AdvancementTask advancement -> {
                row.glyph(Icons.Shape.STAR);
                line = done ? t("rysquests.qpanel.line.ready") : t("rysquests.qpanel.line.advancement");
            }
            case ScriptTask script -> {
                row.glyph(Icons.Shape.GEAR);
                line = done ? t("rysquests.qpanel.line.ready") : target > 1 ? t("rysquests.qpanel.line.script_count", n(value), n(target)) : t("rysquests.qpanel.line.script");
                if (!done && target > 1 && value > 0) {
                    row.progress((float) value / target);
                }
            }
            default -> {
                row.glyph(Icons.Shape.STAR);
                line = done ? t("rysquests.qpanel.line.ready") : Ui.ratio(value, target);
            }
        }
        return row.line(line).state(state);
    }

    /** The task's name as an action: "Получить: Электромеханизм ×32", "Победить: Иссушитель". The author's own title wins. */
    static String taskTitle(Quest quest, TaskDef task) {
        if (task.title().isPresent()) {
            return Component.translatable(task.title().get()).getString();
        }
        int target = task.target();
        String times = target > 1 ? " ×" + Numbers.compact(target) : "";
        return switch (task) {
            case ItemTask item -> t(switch (item.mode()) {
                case CONSUME -> "rysquests.qpanel.task.give";
                case CRAFT -> "rysquests.qpanel.task.craft";
                default -> "rysquests.qpanel.task.get";
            }, itemName(item) + times);
            case FluidTask fluid -> t(fluid.mode() == ItemTask.Mode.CONSUME ? "rysquests.qpanel.task.give_fluid" : "rysquests.qpanel.task.have_fluid",
                    BuiltInRegistries.FLUID.get(fluid.fluid()).getFluidType().getDescription().getString(), bucketsOf(fluid.amount()));
            case KillTask kill -> t("rysquests.qpanel.task.kill", BuiltInRegistries.ENTITY_TYPE.get(kill.entity()).getDescription().getString() + times);
            case VisitTask visit -> visit.structure().isPresent() ? t("rysquests.qpanel.task.find", placeName("structure", visit.structure().get().id()))
                    : visit.biome().isPresent() ? t("rysquests.qpanel.task.visit", placeName("biome", visit.biome().get().id()))
                    : visit.dimension().isPresent() ? t("rysquests.qpanel.task.visit", placeName("dimension", visit.dimension().get()))
                    : t("rysquests.qpanel.task.visit", t("rysquests.qpanel.task.place"));
            case XpTask xp -> t(xp.consume() ? "rysquests.qpanel.task.give_xp" : "rysquests.qpanel.task.have_xp",
                    Ui.counted(xp.points() ? "rysquests.qpanel.plural.point" : "rysquests.qpanel.plural.level", xp.amount()));
            case CheckboxTask check -> t("rysquests.qpanel.task.check", t(quest.options().page() ? "rysquests.qpanel.task.read" : "rysquests.qpanel.task.done"));
            case AdvancementTask advancement -> t("rysquests.qpanel.task.advancement", advancementName(advancement.advancement()));
            case ScriptTask script -> t("rysquests.qpanel.task.script");
            case StatTask stat -> TaskText.name(stat);
            default -> TaskText.name(task);
        };
    }

    /** "любые доски" for a tag, the item's own name otherwise. */
    private static String itemName(ItemTask item) {
        List<ItemRef> refs = item.itemRefs();
        if (refs.isEmpty()) {
            return "?";
        }
        ItemRef ref = refs.getFirst();
        if (ref.tag()) {
            String key = "tag.item." + ref.id().getNamespace() + "." + ref.id().getPath().replace('/', '.');
            if (Language.getInstance().has(key)) {
                return t("rysquests.qpanel.task.any", Component.translatable(key).getString().toLowerCase(java.util.Locale.ROOT));
            }
        }
        ItemStack stack = TaskText.icon(item);
        return stack.isEmpty() ? ref.toString() : stack.getHoverName().getString();
    }

    /** " · подходит 11 видов" for a task any of several items fit, or null. */
    @Nullable
    private static String kinds(ItemTask item) {
        int count = 0;
        for (ItemRef ref : item.itemRefs()) {
            if (ref.tag()) {
                count += BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ref.id())).map(tag -> tag.size()).orElse(0);
            } else {
                count++;
            }
        }
        return count > 1 ? t("rysquests.qpanel.line.kinds", Ui.counted("rysquests.qpanel.plural.kind", count)) : null;
    }

    /** A biome, structure or dimension by name: the game's own word when it has one, ours for vanilla, else the id made readable. */
    private static String placeName(String kind, ResourceLocation id) {
        // Ours first: a loader's English name of a vanilla dimension would win over the language the player chose.
        for (String key : List.of("rysquests.qpanel.place." + id.getNamespace() + "." + id.getPath().replace('/', '.'),
                kind + "." + id.getNamespace() + "." + id.getPath().replace('/', '.'))) {
            if (Language.getInstance().has(key)) {
                return Component.translatable(key).getString();
            }
        }
        String path = id.getPath();
        path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return path.isEmpty() ? id.toString() : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static String advancementName(ResourceLocation id) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            var holder = connection.getAdvancements().get(id);
            if (holder != null && holder.value().display().isPresent()) {
                return holder.value().display().get().getTitle().getString();
            }
        }
        return placeName("advancements", id);
    }

    /** A fluid amount in buckets: "4 ведра", or millibuckets when it is not whole. */
    private static String bucketsOf(int amount) {
        return amount % 1000 == 0 ? Ui.counted("rysquests.qpanel.plural.bucket", amount / 1000) : t("rysquests.qpanel.millibuckets", n(amount));
    }

    /** A fluid amount as a bare number of buckets for "2 из 4". */
    private static String buckets(int amount) {
        if (amount % 1000 == 0) {
            return n(amount / 1000);
        }
        return amount >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f", amount / 1000f) : t("rysquests.qpanel.millibuckets", n(amount));
    }

    // --- rewards ---

    private float rewards(Ui ui, Facts f, float x, float y, float w) {
        Quest quest = f.quest;
        if (quest.rewards().isEmpty()) {
            return y;
        }
        List<Integer> claimed = f.own.claimed();
        List<RewardChip> each = new ArrayList<>();
        List<RewardChip> once = new ArrayList<>();
        boolean onceTaken = false;
        boolean eachTaken = false;
        List<Integer> choices = new ArrayList<>();
        List<Integer> randoms = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            RewardDef reward = quest.rewards().get(i);
            if (reward instanceof ChoiceReward) {
                choices.add(i);
                continue;
            }
            if (reward instanceof RandomReward) {
                randoms.add(i);
                continue;
            }
            boolean shared = f.team && reward.teamMode() == TeamMode.ONCE;
            RewardChip chip = chip(reward, shared);
            if (chip == null) {
                continue;
            }
            if (shared) {
                once.add(chip);
                onceTaken |= claimed.contains(i);
            } else {
                each.add(chip);
                eachTaken |= claimed.contains(i);
            }
        }
        float pen = y;
        if (!each.isEmpty()) {
            pen += DetailPanel.rubric(ui, x, pen, w, t("rysquests.qpanel.reward"), f.team ? t("rysquests.qpanel.each") : null) + 6;
            pen += RewardChip.row(ui, x, pen, w, each);
            if (f.team && f.done) {
                pen += 4;
                pen = small(ui, t(eachTaken ? "rysquests.qpanel.each_taken" : "rysquests.qpanel.each_note"), "text.muted", x, pen, w);
            }
            pen += GAP;
        }
        if (!once.isEmpty()) {
            pen += DetailPanel.rubric(ui, x, pen, w, t("rysquests.qpanel.reward_shared"), t("rysquests.qpanel.once")) + 6;
            float top = pen;
            pen += RewardChip.row(ui, x, pen, w, once);
            if (onceTaken) {
                ui.veil(x, top, w, pen - top, 9, ui.c("panel.bg"), 0.45f);
                pen += 4;
                ui.check(x, pen + 3, 12, ui.c("state.ok"));
                pen = small(ui, t("rysquests.qpanel.once_taken"), "state.ok", x + 18, pen, w - 18);
            }
            pen += GAP;
        }
        for (int index : choices) {
            pen = choice(ui, f, index, (ChoiceReward) quest.rewards().get(index), x, pen, w) + GAP;
        }
        for (int index : randoms) {
            pen = random(ui, (RandomReward) quest.rewards().get(index), f.team, x, pen, w) + GAP;
        }
        return pen;
    }

    private static float small(Ui ui, String words, String token, float x, float y, float w) {
        List<String> lines = ui.wrap(words, w, 12, Ui.REGULAR, 3);
        float lh = 17.4f;
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x, y + lh * (i + 0.5f), 12, Ui.REGULAR, ui.c(token));
        }
        return y + lh * lines.size();
    }

    /** A plain reward as a chip; null for one that shows nothing (a choice or a random is drawn on its own). */
    @Nullable
    private static RewardChip chip(RewardDef reward, boolean shared) {
        return switch (reward) {
            case ItemReward item -> {
                ItemStack stack = QuestFacts.stack(item);
                yield RewardChip.item(stack, item.count(), stack.getHoverName().getString());
            }
            case XpReward xp -> xp.levels().isPresent()
                    ? RewardChip.xp(xp.levels().get(), Ui.counted("rysquests.qpanel.plural.xp_level", xp.levels().get()))
                    : RewardChip.xp(xp.points().orElse(0), t("rysquests.qpanel.xp_word"));
            case CurrencyReward currency -> RewardChip.coins(currency.amount(),
                    Ui.counted(shared ? "rysquests.qpanel.plural.coin_team" : "rysquests.qpanel.plural.coin", currency.amount()));
            case TitleReward title -> RewardChip.glyph(Icons.Shape.FLAG, t("rysquests.qpanel.title", Component.translatable(title.id()).getString()));
            case StageReward stage -> RewardChip.glyph(Icons.Shape.KEY, t("rysquests.qpanel.stage", stage.stage()));
            case LootTableReward loot -> RewardChip.item(new ItemStack(Items.CHEST), 1, t("rysquests.qpanel.loot"));
            case CommandReward command -> RewardChip.glyph(Icons.Shape.SPARK, t("rysquests.qpanel.special"));
            case ScriptReward script -> RewardChip.glyph(Icons.Shape.SPARK, t("rysquests.qpanel.special"));
            default -> RewardChip.glyph(Icons.Shape.SPARK, t("rysquests.qpanel.special"));
        };
    }

    /** A choice, every option shown in two columns, nothing cut away (review U1; picture 21). */
    private float choice(Ui ui, Facts f, int index, ChoiceReward choice, float x, float y, float w) {
        float pen = y;
        boolean taken = f.own.claimed().contains(index);
        String right = t("rysquests.qpanel.pick_one", choice.of().size());
        pen += DetailPanel.rubric(ui, x, pen, w, t("rysquests.qpanel.reward_choice"), right) + 6;
        float gap = 6;
        float cardW = (w - gap) / 2f;
        float cardH = 38;
        int picked = taken ? -1 : BookState.PICKS.getOrDefault(index, -1);
        for (int option = 0; option < choice.of().size(); option++) {
            RewardDef candidate = choice.of().get(option);
            float cx = x + (option % 2) * (cardW + gap);
            float cy = pen + (option / 2) * (cardH + gap);
            int which = option;
            Kit.radioRow(ui.g, ui.theme, ui.kitHits, Math.round(cx / 2f), Math.round(cy / 2f), Math.round(cardW / 2f), Math.round(cardH / 2f),
                    picked == option, ui.mouseX / 2, ui.mouseY / 2, () -> {
                        if (!taken) {
                            BookState.PICKS.put(index, which);
                        }
                    });
            float mid = cy + cardH / 2f;
            String name;
            if (candidate instanceof ItemReward item) {
                ItemStack stack = QuestFacts.stack(item);
                ui.box(cx + 5, mid - 14, 28, 28, 6, ui.c("slot.bg"), ui.c("slot.border"));
                ui.item(stack, cx + 9, mid - 10, 20);
                ui.count(item.count(), cx + 5 + 27, mid + 16);
                name = stack.getHoverName().getString();
            } else {
                name = phrase(candidate, false);
            }
            float textX = cx + (candidate instanceof ItemReward ? 5 + 28 + 8 : 10);
            ui.labelFit(name, textX, mid, cx + cardW - 32 - textX, 12, Ui.REGULAR, ui.c("text.body"));
        }
        int rows = (choice.of().size() + 1) / 2;
        pen += rows * cardH + Math.max(0, rows - 1) * gap;
        if (taken) {
            ui.veil(x, y + Ui.lineHeight(11.5f) + 6, w, pen - y - Ui.lineHeight(11.5f) - 6, 9, ui.c("panel.bg"), 0.45f);
        }
        return pen;
    }

    /** A random reward: its items in a row, the chance of each on hover, the rest as "+N" (pictures 12c, 19b). */
    private static float random(Ui ui, RandomReward random, boolean team, float x, float y, float w) {
        float pen = y;
        String left = t("rysquests.qpanel.reward_random");
        String right = t("rysquests.qpanel.random_of", random.of().size()) + (team && random.teamMode() == TeamMode.EACH ? " · " + t("rysquests.qpanel.each") : "");
        if (ui.capsWidth(left) + 12 + ui.width(right, 11.5f, Ui.REGULAR) > w) {
            // A narrow panel keeps the count; the chances still show on hover.
            right = t("rysquests.qpanel.random_count", random.of().size());
        }
        pen += DetailPanel.rubric(ui, x, pen, w, left, right) + 6;
        int total = 0;
        for (int i = 0; i < random.of().size(); i++) {
            total += i < random.weights().size() ? random.weights().get(i) : 1;
        }
        float size = 26;
        float step = size + 4;
        String plusText = "+" + random.of().size();
        int fit = Math.max(1, (int) ((w - ui.width(plusText, 12, Ui.REGULAR) - 8 + 4) / step));
        int shownCount = random.of().size() <= (int) ((w + 4) / step) ? random.of().size() : fit;
        float pen2 = x;
        for (int i = 0; i < shownCount; i++) {
            RewardDef option = random.of().get(i);
            int weight = i < random.weights().size() ? random.weights().get(i) : 1;
            String chance = total == 0 ? "" : Math.round(100f * weight / total) + " %";
            ui.box(pen2, pen, size, size, 6, ui.c("slot.bg"), ui.c("slot.border"));
            String name;
            if (option instanceof ItemReward item) {
                ItemStack stack = QuestFacts.stack(item);
                ui.item(stack, pen2 + 4, pen + 4, 18);
                ui.count(item.count(), pen2 + size - 1, pen + size + 2);
                name = stack.getHoverName().getString() + (item.count() > 1 ? " ×" + item.count() : "");
            } else if (option instanceof CurrencyReward) {
                ui.circle(pen2 + 7.5f, pen + 7.5f, 11, ui.c("coin"));
                name = phrase(option, false);
            } else {
                ui.icon(Icons.Shape.SPARK, pen2 + 6, pen + 6, 14, ui.c("text.secondary"));
                name = phrase(option, false);
            }
            ui.tooltipIfOver(pen2, pen, size, size, Component.literal(t("rysquests.qpanel.chance", name, chance)));
            pen2 += step;
        }
        if (shownCount < random.of().size()) {
            ui.label("+" + (random.of().size() - shownCount), pen2 + 4, pen + size / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        return pen + size;
    }

    /** A reward in words, for the claim button and options that are not items: "25 опыта", "1 монету", "Стальной слиток ×32". */
    private static String phrase(RewardDef reward, boolean accusative) {
        return switch (reward) {
            case ItemReward item -> QuestFacts.stack(item).getHoverName().getString() + (item.count() > 1 ? " ×" + Numbers.compact(item.count()) : "");
            case XpReward xp -> xp.levels().isPresent() ? Ui.counted("rysquests.qpanel.plural.level", xp.levels().get()) + " " + t("rysquests.qpanel.xp_word")
                    : n(xp.points().orElse(0)) + " " + t("rysquests.qpanel.xp_word");
            case CurrencyReward currency -> Ui.counted(accusative ? "rysquests.qpanel.plural.coin_acc" : "rysquests.qpanel.plural.coin_n", currency.amount());
            case TitleReward title -> t("rysquests.qpanel.title", Component.translatable(title.id()).getString());
            case StageReward stage -> t("rysquests.qpanel.stage", stage.stage());
            case LootTableReward loot -> t("rysquests.qpanel.loot");
            case RandomReward random -> t(accusative ? "rysquests.qpanel.random_acc" : "rysquests.qpanel.random_short", random.of().size());
            default -> t(accusative ? "rysquests.qpanel.special_acc" : "rysquests.qpanel.special");
        };
    }

    /** What the claim button takes: "25 опыта и 1 монету", "Стальной слиток ×32 и ещё 3". */
    static String claimSummary(Quest quest) {
        return summary(quest, true);
    }

    private static String summary(Quest quest, boolean accusative) {
        List<Integer> claimed = ClientProgress.get(quest.id()).claimed();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (claimed.contains(i)) {
                continue;
            }
            RewardDef reward = quest.rewards().get(i);
            if (reward instanceof ChoiceReward choice) {
                int pick = BookState.PICKS.getOrDefault(i, -1);
                if (pick >= 0 && pick < choice.of().size()) {
                    parts.addFirst(phrase(choice.of().get(pick), accusative));
                }
                continue;
            }
            parts.add(phrase(reward, accusative));
        }
        if (parts.isEmpty()) {
            return t("rysquests.qpanel.reward").toLowerCase(java.util.Locale.ROOT);
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        if (parts.size() == 2) {
            return t("rysquests.qpanel.act.and", parts.get(0), parts.get(1));
        }
        return t("rysquests.qpanel.act.more", parts.getFirst(), parts.size() - 1);
    }

    // --- what the quest opens ---

    private static float opens(Ui ui, Facts f, float x, float y, float w) {
        Quest quest = f.quest;
        QuestBook book = BookData.book();
        List<String> names = new ArrayList<>();
        List<String> sides = new ArrayList<>();
        for (ResourceLocation id : book.unlocks().getOrDefault(quest.id(), List.of())) {
            Quest next = book.quests().get(id);
            if (next == null || !works.repka.rysquests.progress.QuestLogic.isLive(next)) {
                continue;
            }
            String name = BookData.shown(next) ? "**«" + QuestFacts.plain(Component.translatable(next.title()).getString()) + "»**" : "???";
            (next.options().optional() ? sides : names).add(name);
        }
        for (Chapter chapter : book.chapters().values()) {
            if (chapter.requires().contains(quest.id()) && !chapter.hidden()) {
                names.addFirst("**" + t("rysquests.qpanel.opens_chapter", QuestFacts.plain(Component.translatable(chapter.title()).getString())) + "**");
            }
        }
        names.addAll(sides);
        if (names.isEmpty()) {
            return y;
        }
        List<String> head = names.subList(0, Math.min(OPENS, names.size()));
        String text = t(f.done ? "rysquests.qpanel.opened" : "rysquests.qpanel.opens") + String.join(", ", head)
                + (names.size() > OPENS ? t("rysquests.qpanel.opens_more", names.size() - OPENS) : "");
        TextBlock.Look look = new TextBlock.Look(12, 17.4f, ui.c("text.muted"), ui.c("text.secondary"));
        ui.icon(Icons.Shape.KEY, x, y + 2, 14, ui.c("text.muted"));
        return TextBlock.render(ui, text, x + 14 + 8, y, w - 22, look, Recipes::showRecipes) + GAP;
    }

    // --- the main button ---

    @Nullable
    private DetailPanel.Action action(Facts f) {
        Quest quest = f.quest;
        switch (f.status) {
            case COMPLETED -> {
                if (QuestFacts.awaitsPick(quest)) {
                    return DetailPanel.Action.state(t("rysquests.qpanel.act.pick"));
                }
                return DetailPanel.Action.button(t("rysquests.qpanel.act.claim", claimSummary(quest)), () -> QuestFacts.claim(quest));
            }
            case CLAIMED -> {
                return DetailPanel.Action.state(t("rysquests.qpanel.act.claimed"));
            }
            case LOCKED -> {
                if (f.own.coolingDown()) {
                    return DetailPanel.Action.state(t("rysquests.qpanel.act.wait", waitText(f.own.nextAt())));
                }
                if (f.path != null && Epochs.isOpen(f.path.first()) && BookData.shown(f.path.first())) {
                    Quest first = f.path.first();
                    return DetailPanel.Action.button(t("rysquests.qpanel.act.start", QuestFacts.plain(Component.translatable(first.title()).getString())), () -> events.select(first));
                }
                return DetailPanel.Action.state(t("rysquests.qpanel.act.locked"));
            }
            default -> {
            }
        }
        if (f.givable.size() > 1) {
            return DetailPanel.Action.button(t("rysquests.qpanel.act.give_all"),
                    () -> QuestFacts.send(QuestActionPayload.SUBMIT, quest, QuestActionPayload.ALL_TASKS, List.of()));
        }
        if (f.givable.size() == 1) {
            int index = f.givable.getFirst();
            TaskDef task = quest.tasks().get(index);
            int amount = Math.min(QuestFacts.canGive(task), task.target() - f.value(index));
            return DetailPanel.Action.button(t("rysquests.qpanel.act.give", giveWhat(task, amount)),
                    () -> QuestFacts.send(QuestActionPayload.SUBMIT, quest, index, List.of()));
        }
        if (!f.checks.isEmpty()) {
            int index = f.checks.getFirst();
            String what = t(quest.options().page() ? "rysquests.qpanel.task.read" : "rysquests.qpanel.task.done");
            TaskDef task = quest.tasks().get(index);
            if (task.title().isPresent()) {
                what = Component.translatable(task.title().get()).getString();
            }
            return DetailPanel.Action.button(t("rysquests.qpanel.act.check", what), () -> QuestFacts.send(QuestActionPayload.CHECK, quest, index, List.of()));
        }
        if (!f.short_.isEmpty() && Recipes.available()) {
            ItemStack stack = TaskText.icon(quest.tasks().get(f.short_.getFirst()));
            if (!stack.isEmpty()) {
                return DetailPanel.Action.button(t("rysquests.qpanel.act.how", stack.getHoverName().getString()), () -> Recipes.showRecipes(stack));
            }
        }
        return DetailPanel.Action.state(autoWords(f));
    }

    /** "Электромеханизм ×8", "Лава, 2 ведра", "12 уровней опыта". */
    private static String giveWhat(TaskDef task, int amount) {
        return switch (task) {
            case ItemTask item -> itemName(item) + (amount > 1 ? " ×" + Numbers.compact(amount) : "");
            case FluidTask fluid -> BuiltInRegistries.FLUID.get(fluid.fluid()).getFluidType().getDescription().getString() + ", " + bucketsOf(amount);
            case XpTask xp -> Ui.counted(xp.points() ? "rysquests.qpanel.plural.point" : "rysquests.qpanel.plural.level", amount) + " " + t("rysquests.qpanel.xp_word");
            default -> TaskText.name(task);
        };
    }

    /** The state in words when nothing is to be pressed: "Засчитается само после победы", "Идёт само: 5 задач". */
    private static String autoWords(Facts f) {
        List<TaskDef> left = new ArrayList<>();
        for (int i = 0; i < f.quest.tasks().size(); i++) {
            if (!f.finished(i)) {
                left.add(f.quest.tasks().get(i));
            }
        }
        if (left.isEmpty()) {
            return t("rysquests.qpanel.act.soon");
        }
        if (left.stream().allMatch(task -> task instanceof KillTask)) {
            return t("rysquests.qpanel.act.kill");
        }
        if (left.stream().allMatch(task -> task instanceof VisitTask)) {
            return t("rysquests.qpanel.act.visit");
        }
        if (left.stream().allMatch(task -> task instanceof ItemTask item && item.mode() != ItemTask.Mode.CONSUME && item.mode() != ItemTask.Mode.CRAFT)) {
            return t("rysquests.qpanel.act.item");
        }
        boolean xpShort = left.stream().anyMatch(task -> task instanceof XpTask xp && xp.consume());
        return t("rysquests.qpanel.act.auto", Ui.counted("rysquests.plural.task", left.size())) + (xpShort ? t("rysquests.qpanel.act.auto_xp") : "");
    }

    // --- words ---

    static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String n(int value) {
        return Numbers.compact(value);
    }

    private static String times(int count) {
        return Ui.counted("rysquests.qpanel.plural.time", count);
    }

    /** "2 ч 40 мин", "5 мин" until a moment. */
    static String waitText(long at) {
        long minutes = Math.max(1, (at - System.currentTimeMillis() + 59_999) / 60_000);
        return minutes >= 60 ? t("rysquests.qpanel.hours", minutes / 60, minutes % 60) : t("rysquests.qpanel.minutes", minutes);
    }

    /** «A», «B» или «C». */
    private static String quoted(List<Quest> quests, String last) {
        List<String> names = quests.stream().map(quest -> "«" + QuestFacts.plain(Component.translatable(quest.title()).getString()) + "»").toList();
        if (names.size() <= 1) {
            return String.join("", names);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + last + names.getLast();
    }

    private static float measure(Ui ui, String text, float w, TextBlock.Look look) {
        return ui.measure(() -> Math.round(TextBlock.render(ui, text, 0, 0, w, look, stack -> {
        })));
    }

    @SuppressWarnings("unused")
    private static int faded(int color) {
        return Draw.alpha(color, 0.55f);
    }
}
