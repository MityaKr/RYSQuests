package works.repka.rysquests.client.screen;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.Numbers;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.layout.ChapterLayout;
import works.repka.rysquests.client.layout.LayeredLayout.Placed;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.editor.QuestEdits;
import works.repka.rysquests.network.QuestEditorSavePayload;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.StageReward;
import works.repka.rysquests.reward.TitleReward;
import works.repka.rysquests.reward.XpReward;

/** Edits a chapter on its map, from {@code design/Editor.dc.html}. */
final class QuestEditorView {
    private static final int BAR_H = 51;
    private static final int LEFT_W = 232;
    private static final int RIGHT_W = 360;
    private static final int NODE_W = 150;
    private static final int NODE_H = 40;
    private static final int ACTIVE_W = 200;
    private static final int ACTIVE_H = 52;
    private static final ChapterLayout.Metrics METRICS = new ChapterLayout.Metrics(NODE_W, NODE_H, 48, 48, 12, 24, 12, 10, 20, 0, 0);
    private static final int ROW_H = 33;
    private static final int ROW_STEP = 39;
    private static final int UNDO_LIMIT = 100;
    private static final long UNDO_PAUSE_MS = 400;
    private static final long EXTERNAL_MS = 60_000;
    private static final int MAX_RESULTS = 60;
    private static final int MIN_QUERY = 2;
    private static final int WHEEL = 48;
    private static final String NEW_QUEST_KEY = "rysquests.quest_editor.new_quest";

    private enum Tab {
        PROPS, TEXT, JSON
    }

    private enum Gesture {
        NONE, MOVE, PAN, LINK, GRIP
    }

    private enum Kind {
        ITEM, ENTITY, TEXT, NUMBER, MODE
    }

    private record Field(String name, Kind kind) {
    }

    private record State(Chapter chapter, Map<ResourceLocation, Quest> quests, Map<String, String> texts) {
    }

    private record Choice(String label, String note, ItemStack icon, Runnable action) {
    }

    private static final class Bound {
        final TextArea area;
        final Consumer<String> apply;
        String shown;

        Bound(TextArea area, String value, Consumer<String> apply) {
            this.area = area;
            this.apply = apply;
            shown = value;
            area.setText(value);
        }
    }

    private static final Field COUNT = new Field("count", Kind.NUMBER);
    private static final Field MODE = new Field("mode", Kind.MODE);
    /** Types with fields of their own. Anything else, modded types too, is edited on the JSON tab. */
    private static final Map<String, List<Field>> TASK_FIELDS = new LinkedHashMap<>();
    private static final Map<String, List<Field>> REWARD_FIELDS = new LinkedHashMap<>();

    static {
        TASK_FIELDS.put("rysquests:item", List.of(new Field("item", Kind.ITEM), COUNT, MODE));
        TASK_FIELDS.put("rysquests:kill", List.of(new Field("entity", Kind.ENTITY), COUNT));
        TASK_FIELDS.put("rysquests:stat", List.of(new Field("stat", Kind.TEXT), new Field("target", Kind.TEXT), COUNT));
        TASK_FIELDS.put("rysquests:advancement", List.of(new Field("advancement", Kind.TEXT)));
        TASK_FIELDS.put("rysquests:checkbox", List.of(new Field("title", Kind.TEXT)));
        TASK_FIELDS.put("rysquests:visit", List.of(new Field("dimension", Kind.TEXT), new Field("biome", Kind.TEXT), new Field("structure", Kind.TEXT), new Field("radius", Kind.NUMBER)));
        TASK_FIELDS.put("rysquests:fluid", List.of(new Field("fluid", Kind.TEXT), new Field("amount", Kind.NUMBER), MODE));
        TASK_FIELDS.put("rysquests:script", List.of(new Field("script", Kind.TEXT), COUNT));
        REWARD_FIELDS.put("rysquests:item", List.of(new Field("item", Kind.ITEM), COUNT));
        REWARD_FIELDS.put("rysquests:xp", List.of(new Field("levels", Kind.NUMBER), new Field("points", Kind.NUMBER)));
        REWARD_FIELDS.put("rysquests:currency", List.of(new Field("id", Kind.TEXT), new Field("amount", Kind.NUMBER)));
        REWARD_FIELDS.put("rysquests:title", List.of(new Field("id", Kind.TEXT)));
        REWARD_FIELDS.put("rysquests:stage", List.of(new Field("stage", Kind.TEXT)));
        REWARD_FIELDS.put("rysquests:command", List.of(new Field("command", Kind.TEXT)));
        REWARD_FIELDS.put("rysquests:loot_table", List.of(new Field("table", Kind.TEXT), new Field("rolls", Kind.NUMBER)));
        REWARD_FIELDS.put("rysquests:script", List.of(new Field("script", Kind.TEXT)));
    }

    private final BookScreen screen;
    private final Map<String, Bound> bound = new HashMap<>();
    private final TextArea filter = new TextArea(false, 12, 18, Ui.REGULAR, () -> { });
    private final Scroll treeScroll = new Scroll();
    private final Scroll panelScroll = new Scroll();
    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private final Set<ResourceLocation> selected = new LinkedHashSet<>();
    private final Map<ResourceLocation, Long> external = new HashMap<>();
    private final Map<ResourceLocation, float[]> rects = new HashMap<>();
    private State state;
    private State saved;
    private boolean newChapter;
    private QuestBook base;
    private Tab tab = Tab.PROPS;
    private int taskForm = -1;
    private int rewardForm = -1;
    private boolean linkMode;
    private boolean saving;
    private boolean exitArmed;
    private long lastEdit;
    @Nullable
    private String status;
    private boolean statusBad;
    private Gesture gesture = Gesture.NONE;
    @Nullable
    private ResourceLocation linkFrom;
    private boolean moved;
    private double startX;
    private double startY;
    private double pointerX;
    private double pointerY;
    private float panX = -36;
    private float panY = -60;
    private int gripIndex;
    private float gripTop;
    private int leftEdge;
    private int rightEdge;
    private volatile Map<String, Placed> layout = Map.of();
    @Nullable
    private String layoutKey;
    @Nullable
    private Supplier<List<Choice>> picker;
    private String pickerTitle = "";
    private List<Choice> found = List.of();
    private String lastQuery = "\0";

    QuestEditorView(BookScreen screen) {
        this.screen = screen;
        base = ClientBook.current();
        ResourceLocation start = BookState.chapter != null && base.chapters().containsKey(BookState.chapter) ? BookState.chapter : storyChapters().stream().map(Chapter::id).findFirst().orElse(null);
        if (start == null) {
            state = blankChapter(namespace());
            saved = new State(state.chapter, Map.of(), Map.of());
            newChapter = true;
        } else {
            load(start);
        }
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static List<Chapter> storyChapters() {
        return ClientBook.current().chapters().values().stream().filter(Chapter::isStory).toList();
    }

    private static String namespace() {
        return storyChapters().stream().findFirst().map(chapter -> chapter.id().getNamespace())
                .orElseGet(() -> ClientBook.current().chapters().keySet().stream().map(ResourceLocation::getNamespace).findFirst().orElse("pack"));
    }

    private State blankChapter(String namespace) {
        Set<ResourceLocation> taken = new LinkedHashSet<>(ClientBook.current().chapters().keySet());
        Chapter chapter = QuestEdits.newChapter(QuestEdits.freshChapter(namespace, taken));
        return new State(chapter, Map.of(), Map.of(chapter.title(), tr("rysquests.quest_editor.new_chapter_title")));
    }

    private void load(ResourceLocation chapter) {
        QuestBook book = ClientBook.current();
        Map<ResourceLocation, Quest> quests = new LinkedHashMap<>();
        book.quests().values().stream().filter(quest -> quest.chapter().filter(chapter::equals).isPresent()).forEach(quest -> quests.put(quest.id(), quest));
        state = new State(book.chapters().get(chapter), quests, Map.of());
        saved = state;
        newChapter = false;
        BookState.chapter = chapter;
        afterJump();
    }

    private void afterJump() {
        selected.clear();
        undo.clear();
        redo.clear();
        external.clear();
        closeForms();
        bound.clear();
        panX = -36;
        panY = -60;
        layoutKey = null;
    }

    private void closeForms() {
        taskForm = -1;
        rewardForm = -1;
        picker = null;
    }

    // --- texts ---

    private String shown(String key) {
        String own = state.texts.get(key);
        return own != null ? own : Language.getInstance().getOrDefault(key);
    }

    private static boolean isKey(String text) {
        return !text.isEmpty() && (Language.getInstance().has(text) || text.matches("[a-z0-9_.-]+"));
    }

    private void putText(String key, String value) {
        Map<String, String> texts = new HashMap<>(state.texts);
        texts.put(key, value);
        state = new State(state.chapter, state.quests, texts);
    }

    private void putQuest(Quest quest) {
        Map<ResourceLocation, Quest> quests = new LinkedHashMap<>(state.quests);
        quests.put(quest.id(), quest);
        state = new State(state.chapter, quests, state.texts);
    }

    private void setChapter(Chapter chapter) {
        state = new State(chapter, state.quests, state.texts);
    }

    private void setTitle(Quest quest, String value) {
        String key = isKey(quest.title()) ? quest.title() : QuestEdits.titleKey(quest.id());
        putText(key, value);
        Quest current = state.quests.get(quest.id());
        putQuest(QuestEdits.withTexts(current, key, current.subtitle(), current.text()));
    }

    private void setSubtitle(Quest quest, String value) {
        Quest current = state.quests.get(quest.id());
        String key = current.subtitle().filter(QuestEditorView::isKey).orElse(QuestEdits.subtitleKey(quest.id()));
        if (!value.isBlank()) {
            putText(key, value);
        }
        putQuest(QuestEdits.withTexts(current, current.title(), value.isBlank() ? Optional.empty() : Optional.of(key), current.text()));
    }

    private void setBody(Quest quest, String value) {
        Quest current = state.quests.get(quest.id());
        String key = current.text().filter(QuestEditorView::isKey).orElse(QuestEdits.textKey(quest.id()));
        if (!value.isBlank()) {
            putText(key, value);
        }
        putQuest(QuestEdits.withTexts(current, current.title(), current.subtitle(), value.isBlank() ? Optional.empty() : Optional.of(key)));
    }

    // --- undo ---

    private void remember() {
        undo.push(state);
        if (undo.size() > UNDO_LIMIT) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void beforeEdit() {
        syncFields();
        long now = Util.getMillis();
        if (now - lastEdit > UNDO_PAUSE_MS) {
            remember();
        }
        lastEdit = now;
        clearNote();
    }

    private void clearNote() {
        exitArmed = false;
        status = null;
    }

    private void edit(Runnable change) {
        syncFields();
        remember();
        change.run();
        clearNote();
        bound.clear();
    }

    private void stepUndo() {
        syncFields();
        if (!undo.isEmpty()) {
            redo.push(state);
            restore(undo.pop());
        }
    }

    private void stepRedo() {
        syncFields();
        if (!redo.isEmpty()) {
            undo.push(state);
            restore(redo.pop());
        }
    }

    private void restore(State target) {
        state = target;
        selected.retainAll(state.quests.keySet());
        closeForms();
        bound.clear();
    }

    private void show(String message, boolean bad) {
        status = message;
        statusBad = bad;
    }

    private boolean dirty() {
        return newChapter || !state.equals(saved);
    }

    // --- changes the toolbar makes ---

    @Nullable
    private Quest primary() {
        ResourceLocation last = null;
        for (ResourceLocation id : selected) {
            last = id;
        }
        return last == null ? null : state.quests.get(last);
    }

    private Set<ResourceLocation> takenIds() {
        Set<ResourceLocation> taken = new LinkedHashSet<>(ClientBook.current().quests().keySet());
        taken.addAll(state.quests.keySet());
        return taken;
    }

    /** New quests appear in the top left of what is on screen, below each other. */
    private int[] freeSpot() {
        int x = QuestEdits.snap(Math.round(panX) + 48);
        int y = QuestEdits.snap(Math.round(panY) + 72);
        Set<String> used = new LinkedHashSet<>();
        state.quests.values().forEach(quest -> {
            int[] at = position(quest);
            used.add(at[0] + ":" + at[1]);
        });
        while (used.contains(x + ":" + y)) {
            y += QuestEdits.GRID * 3;
        }
        return new int[] {x, y};
    }

    private void addQuest(boolean page) {
        edit(() -> {
            ResourceLocation id = QuestEdits.freshQuest(state.chapter.id(), takenIds());
            int[] at = freeSpot();
            Quest quest = QuestEdits.newQuest(id, state.chapter.id(), at[0], at[1], page);
            putQuest(quest);
            putText(quest.title(), tr(NEW_QUEST_KEY));
            select(id);
        });
    }

    private void select(ResourceLocation id) {
        selected.clear();
        selected.add(id);
        taskForm = -1;
        rewardForm = -1;
        bound.clear();
        panelScroll.reset();
    }

    private void toggleGroup() {
        Quest quest = primary();
        if (quest == null) {
            show(tr("rysquests.quest_editor.select_first"), true);
            return;
        }
        edit(() -> {
            boolean grouped = selected.stream().map(state.quests::get).allMatch(each -> each.group().isPresent());
            if (grouped) {
                selected.forEach(id -> putQuest(QuestEdits.withGroup(state.quests.get(id), Optional.empty())));
                return;
            }
            List<Chapter.Group> groups = new ArrayList<>(state.chapter.groups());
            Set<String> taken = new LinkedHashSet<>();
            groups.forEach(group -> taken.add(group.id()));
            int number = groups.size() + 1;
            while (taken.contains("group_" + number)) {
                number++;
            }
            String id = "group_" + number;
            String key = QuestEdits.groupKey(state.chapter.id(), id);
            groups.add(new Chapter.Group(id, key));
            Chapter c = state.chapter;
            setChapter(new Chapter(c.id(), c.title(), c.subtitle(), c.icon(), c.epoch(), c.defaultView(), groups, c.requires(), c.hidden(), c.section()));
            putText(key, text("rysquests.quest_editor.group_name", number));
            selected.forEach(each -> putQuest(QuestEdits.withGroup(state.quests.get(each), Optional.of(id))));
        });
    }

    private void autoLayout() {
        edit(() -> state.quests.values().forEach(quest -> putQuest(QuestEdits.auto(quest))));
        layoutKey = null;
        panX = -36;
        panY = -60;
    }

    private void duplicateSelected() {
        Quest quest = primary();
        if (quest == null) {
            return;
        }
        edit(() -> {
            ResourceLocation id = QuestEdits.freshQuest(state.chapter.id(), takenIds());
            int[] at = position(quest);
            Quest copy = QuestEdits.duplicate(quest, id, at[0] + QuestEdits.GRID * 2, at[1] + QuestEdits.GRID * 3);
            putQuest(copy);
            putText(copy.title(), shown(quest.title()));
            quest.subtitle().ifPresent(key -> putText(QuestEdits.subtitleKey(id), shown(key)));
            quest.text().ifPresent(key -> putText(QuestEdits.textKey(id), shown(key)));
            select(id);
        });
    }

    /** A quest in a saved file stays: files are never deleted from here. */
    private void removeSelected() {
        if (selected.isEmpty()) {
            return;
        }
        if (selected.stream().anyMatch(saved.quests::containsKey)) {
            show(tr("rysquests.quest_editor.cannot_remove"), true);
            return;
        }
        edit(() -> {
            Map<ResourceLocation, Quest> quests = new LinkedHashMap<>(state.quests);
            selected.forEach(quests::remove);
            quests.replaceAll((id, quest) -> QuestEdits.withRequires(quest, quest.requires().stream().filter(dependency -> !selected.contains(dependency)).toList(), quest.requiresMode()));
            state = new State(state.chapter, quests, state.texts);
            selected.clear();
        });
    }

    private void openChapter(ResourceLocation id) {
        syncFields();
        if (id.equals(state.chapter.id())) {
            return;
        }
        if (dirty() && !exitArmed) {
            exitArmed = true;
            show(tr("rysquests.editor.confirm_exit"), true);
            return;
        }
        clearNote();
        load(id);
    }

    private void createChapter() {
        syncFields();
        if (dirty() && !exitArmed) {
            exitArmed = true;
            show(tr("rysquests.editor.confirm_exit"), true);
            return;
        }
        clearNote();
        state = blankChapter(state.chapter.id().getNamespace());
        saved = new State(state.chapter, Map.of(), Map.of());
        newChapter = true;
        afterJump();
    }

    // --- saving and leaving ---

    private boolean textsChanged(String key) {
        return !Objects.equals(state.texts.get(key), saved.texts.get(key));
    }

    private boolean changed(Quest quest) {
        return !quest.equals(saved.quests.get(quest.id())) || textsChanged(quest.title())
                || quest.subtitle().filter(this::textsChanged).isPresent() || quest.text().filter(this::textsChanged).isPresent();
    }

    private boolean chapterChanged() {
        return newChapter || !state.chapter.equals(saved.chapter) || textsChanged(state.chapter.title())
                || state.chapter.groups().stream().anyMatch(group -> textsChanged(group.title()));
    }

    void save() {
        syncFields();
        if (saving) {
            return;
        }
        if (!dirty()) {
            show(tr("rysquests.editor.no_changes"), false);
            return;
        }
        for (Quest quest : state.quests.values()) {
            if (shown(quest.title()).isBlank()) {
                select(quest.id());
                show(tr("rysquests.quest_editor.error.no_title"), true);
                return;
            }
        }
        if (shown(state.chapter.title()).isBlank()) {
            selected.clear();
            show(tr("rysquests.quest_editor.error.no_chapter_title"), true);
            return;
        }
        List<Quest> quests = state.quests.values().stream().filter(this::changed).toList();
        Map<String, String> texts = new HashMap<>();
        state.texts.forEach((key, value) -> {
            if (textsChanged(key) && isKey(key)) {
                texts.put(key, value);
            }
        });
        saving = true;
        show(tr("rysquests.editor.saving"), false);
        PacketDistributor.sendToServer(new QuestEditorSavePayload(state.chapter, chapterChanged(), quests, Minecraft.getInstance().getLanguageManager().getSelected(), texts));
    }

    void onResult(boolean ok, String reason) {
        saving = false;
        if (ok) {
            saved = state;
            newChapter = false;
            BookState.chapter = state.chapter.id();
            show(tr("rysquests.editor.saved"), false);
        } else {
            show(text("rysquests.editor.failed", tr(reason)), true);
        }
    }

    private void requestExit() {
        syncFields();
        if (dirty() && !exitArmed) {
            exitArmed = true;
            show(tr("rysquests.editor.confirm_exit"), true);
            return;
        }
        screen.closeQuestEditor();
    }

    /** A book that changed without us: what the author has not touched follows it, and everything changed gets marked. */
    private void watchBook() {
        QuestBook book = ClientBook.current();
        if (book == base) {
            return;
        }
        QuestBook old = base;
        base = book;
        if (saving) {
            return;
        }
        ResourceLocation chapter = state.chapter.id();
        boolean any = false;
        for (Quest quest : book.quests().values()) {
            if (quest.chapter().filter(chapter::equals).isPresent() && !quest.equals(old.quests().get(quest.id()))) {
                external.put(quest.id(), Util.getMillis());
                boolean untouched = Objects.equals(state.quests.get(quest.id()), saved.quests.get(quest.id()));
                Map<ResourceLocation, Quest> baseQuests = new LinkedHashMap<>(saved.quests);
                baseQuests.put(quest.id(), quest);
                saved = new State(saved.chapter, baseQuests, saved.texts);
                if (untouched) {
                    putQuest(quest);
                }
                any = true;
            }
        }
        if (any) {
            bound.clear();
        }
    }

    // --- drawing ---

    void render(Ui ui, int x, int y, int w, int h) {
        watchBook();
        syncFields();
        renderBar(ui, x, y, w);
        int bodyY = y + BAR_H;
        int bodyH = h - BAR_H;
        leftEdge = x + LEFT_W;
        rightEdge = x + w - RIGHT_W;
        renderTree(ui, x, bodyY, LEFT_W, bodyH);
        renderMap(ui, leftEdge, bodyY, rightEdge - leftEdge, bodyH);
        renderPanel(ui, rightEdge, bodyY, RIGHT_W, bodyH);
    }

    private void renderBar(Ui ui, int x, int y, int w) {
        ui.fillCorners(x, y, w, BAR_H, 13, ui.c("sidebar.bg"), true, true, false, false);
        ui.rect(x, y + BAR_H - 1, w, 1, ui.c("panel.border"));
        float cy = y + (BAR_H - 1) / 2f;
        BookScreen.renderBrand(ui, x + 18, cy);
        float pen = x + 18 + 36 + ui.width("RYSQuests", 16, Ui.DISPLAY) + 10;
        pen += ui.pill(tr("rysquests.quest_editor.badge"), pen, cy, 11, Ui.SEMIBOLD, 8, 20, 10, ui.warmBorder(0.13f), ui.c("accent")) + 18;
        pen = EditorView.barButton(ui, tr("rysquests.quest_editor.add_quest"), pen, cy, () -> addQuest(false)) + 6;
        pen = EditorView.barButton(ui, tr("rysquests.editor.new_page"), pen, cy, () -> addQuest(true)) + 6;
        pen = EditorView.barButton(ui, tr("rysquests.quest_editor.group"), pen, cy, this::toggleGroup) + 6;
        pen = linkButton(ui, pen, cy) + 6;
        pen = EditorView.barButton(ui, tr("rysquests.quest_editor.auto_layout"), pen, cy, this::autoLayout) + 12;
        float right = x + w - 18;
        String exit = tr("rysquests.editor.exit");
        float exitW = ui.buttonWidth(exit, 11.5f, Ui.REGULAR, 10) + 2;
        right -= exitW;
        ui.button(exit, right, cy - 15, exitW, 30, 8, 11.5f, Ui.REGULAR, 10, Ui.Style.PLAIN, this::requestExit);
        String save = tr("rysquests.editor.save");
        float saveW = ui.buttonWidth(save, 11.5f, Ui.SEMIBOLD, 12);
        right -= 6 + saveW;
        ui.button(save, right, cy - 15, saveW, 30, 8, 11.5f, Ui.SEMIBOLD, 12, Ui.Style.ACCENT, this::save);
        String note = status != null ? status : dirty() ? Ui.counted("rysquests.plural.file_changed", changedFiles()) : "";
        right -= 12;
        float noteW = Math.min(ui.width(note, 11.5f, Ui.REGULAR), Math.max(1, right - pen - 78));
        ui.labelFitTip(note, right - noteW, cy, noteW, 11.5f, Ui.REGULAR, ui.c(statusBad && status != null ? "state.bad" : "text.muted"));
        float undoX = right - noteW - (note.isEmpty() ? 0 : 12) - 66;
        EditorView.iconButton(ui, Icons.Shape.UNDO, undoX, cy, !undo.isEmpty(), this::stepUndo);
        EditorView.iconButton(ui, Icons.Shape.REDO, undoX + 36, cy, !redo.isEmpty(), this::stepRedo);
    }

    private float linkButton(Ui ui, float x, float cy) {
        String label = tr("rysquests.quest_editor.dependency");
        float w = ui.buttonWidth(label, 11.5f, Ui.MEDIUM, 10) + 2;
        if (linkMode) {
            ui.box(x, cy - 15, w, 30, 8, ui.warmBorder(0.13f), ui.c("accent"));
            ui.labelCentered(label, x + w / 2f, cy, 11.5f, Ui.MEDIUM, ui.c("accent"));
            ui.hit(x, cy - 15, w, 30, () -> linkMode = false);
        } else {
            ui.button(label, x, cy - 15, w, 30, 8, 11.5f, Ui.REGULAR, 10, Ui.Style.PLAIN, () -> linkMode = true);
        }
        return x + w;
    }

    private int changedFiles() {
        int files = (int) state.quests.values().stream().filter(this::changed).count() + (chapterChanged() ? 1 : 0);
        boolean texts = state.texts.keySet().stream().anyMatch(this::textsChanged);
        return Math.max(1, files + (texts ? 1 : 0));
    }

    // --- left: the files ---

    private static String lastSegment(ResourceLocation id) {
        String path = id.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private void renderTree(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.columnBg(), false, false, false, true);
        ui.rect(x + w - 1, y, 1, h, ui.c("panel.border"));
        float left = x + 10;
        float inner = w - 21;
        ui.caps("data/" + state.chapter.id().getNamespace() + "/rysquests", left + 6, y + 19, ui.c("text.dim"));
        String note = tr("rysquests.quest_editor.note");
        List<String> noteLines = ui.wrap(note, inner - 20, 11, Ui.REGULAR, 5);
        float noteH = noteLines.size() * 16 + 20;
        float noteY = y + h - 12 - noteH;
        float addY = noteY - 8 - 30;
        float listTop = y + 34;
        float listH = addY - 8 - listTop;
        List<Chapter> chapters = new ArrayList<>(storyChapters());
        if (newChapter) {
            chapters.add(state.chapter);
        }
        float total = chapters.size() * 25 + 21 + state.quests.size() * 23f;
        treeScroll.content(total, Math.round(listH));
        ui.clip(x, Math.round(listTop), w, Math.round(listH));
        float cursor = listTop - treeScroll.offset();
        for (Chapter chapter : chapters) {
            boolean open = chapter.id().equals(state.chapter.id());
            renderFolder(ui, chapter, open, left, cursor, inner, listTop, listH);
            cursor += 25;
            if (open) {
                renderFile(ui, "_chapter.json", chapterChanged(), selected.isEmpty(), left, cursor, inner, listTop, listH, selected::clear);
                cursor += 23;
                for (Quest quest : state.quests.values()) {
                    renderFile(ui, lastSegment(quest.id()) + ".json", changed(quest), selected.contains(quest.id()), left, cursor, inner, listTop, listH, () -> select(quest.id()));
                    cursor += 23;
                }
            }
        }
        ui.unclip();
        EditorView.barButton(ui, tr("rysquests.quest_editor.add_chapter"), left, addY + 15, this::createChapter);
        ui.box(left, noteY, inner, noteH, 8, ui.c("slot.bg"), ui.mix("slot.bg", "panel.border", 0.6f));
        ui.lines(noteLines, left + 10, noteY + noteH / 2f, 16, 11, Ui.REGULAR, ui.c("text.muted"));
    }

    private void renderFolder(Ui ui, Chapter chapter, boolean open, float x, float y, float w, float top, float room) {
        ui.icon(open ? Icons.Shape.CHEVRON_DOWN : Icons.Shape.CHEVRON_RIGHT, x + 7, y + 7, 9, ui.c("text.muted"));
        ui.labelFitTip("chapters/" + chapter.id().getPath() + "/", x + 21, y + 11.5f, w - 25, 11.5f, Ui.REGULAR, ui.c(open ? "text.body" : "text.muted"));
        if (y + 23 > top && y < top + room) {
            ui.hit(x, Math.max(y, top), w, 23, () -> openChapter(chapter.id()));
        }
    }

    private void renderFile(Ui ui, String name, boolean changed, boolean current, float x, float y, float w, float top, float room, Runnable action) {
        if (current) {
            ui.fill(x, y, w, 21, 6, ui.c("button.bg"));
        }
        ui.labelFitTip(name, x + 22, y + 10.5f, w - 42, 11.5f, Ui.REGULAR, ui.c(current ? "text.primary" : "text.muted"));
        if (changed) {
            ui.circle(x + w - 15, y + 7, 7, ui.c("accent"));
        }
        if (y + 21 > top && y < top + room) {
            ui.hit(x, Math.max(y, top), w, 21, action);
        }
    }

    // --- the map ---

    /** The same layout the player's map uses (ADR-44), at the editor's own sizes. */
    private void requestLayout() {
        List<ChapterLayout.Node> nodes = new ArrayList<>();
        for (Quest quest : state.quests.values()) {
            var at = quest.options().layout();
            nodes.add(new ChapterLayout.Node(quest.id().toString(), quest.requires().stream().map(ResourceLocation::toString).toList(), quest.group(),
                    quest.options().optional(), at.x().map(OptionalInt::of).orElse(OptionalInt.empty()), at.y().map(OptionalInt::of).orElse(OptionalInt.empty())));
        }
        List<String> groupOrder = state.chapter.groups().stream().map(Chapter.Group::id).toList();
        String key = nodes.toString() + groupOrder;
        if (key.equals(layoutKey)) {
            return;
        }
        layoutKey = key;
        CompletableFuture.supplyAsync(() -> ChapterLayout.compute(nodes, groupOrder, METRICS), Util.backgroundExecutor())
                .thenAcceptAsync(result -> {
                    if (key.equals(layoutKey)) {
                        layout = result;
                    }
                }, Minecraft.getInstance());
    }

    private int[] position(Quest quest) {
        var fixed = quest.options().layout();
        if (fixed.x().isPresent() && fixed.y().isPresent()) {
            return new int[] {fixed.x().get(), fixed.y().get()};
        }
        Placed placed = layout.get(quest.id().toString());
        return placed == null ? new int[] {0, 0} : new int[] {placed.x(), placed.y()};
    }

    private int[] shownPosition(Quest quest) {
        int[] at = position(quest);
        if (gesture == Gesture.MOVE && moved && selected.contains(quest.id())) {
            return new int[] {QuestEdits.snap(at[0] + (int) Math.round(pointerX - startX)), QuestEdits.snap(at[1] + (int) Math.round(pointerY - startY))};
        }
        return at;
    }

    private void renderMap(Ui ui, int x, int y, int w, int h) {
        requestLayout();
        ui.rect(x, y, w, h, ui.mapBg());
        ui.clip(x, y, w, h);
        int line = Draw.alpha(ui.c("slot.border"), 0.5f);
        float step = QuestEdits.GRID;
        for (float px = x - positive(panX, step); px < x + w; px += step) {
            ui.rect(px, y, 1, h, line);
        }
        for (float py = y - positive(panY, step); py < y + h; py += step) {
            ui.rect(x, py, w, 1, line);
        }
        ui.hit(x, y, w, h, this::pressMap);
        float originX = x - panX;
        float originY = y - panY;
        Quest primary = primary();
        rects.clear();
        for (Quest quest : state.quests.values()) {
            int[] at = shownPosition(quest);
            boolean active = primary != null && quest.id().equals(primary.id());
            float nodeW = active ? ACTIVE_W : nodeWidth(ui, quest);
            rects.put(quest.id(), new float[] {originX + at[0], originY + at[1], nodeW, active ? ACTIVE_H : NODE_H});
        }
        renderGroups(ui);
        renderEdges(ui);
        for (Quest quest : state.quests.values()) {
            if (primary == null || !quest.id().equals(primary.id())) {
                renderNode(ui, quest, x, y, w, h);
            }
        }
        if (primary != null) {
            renderActive(ui, primary, x, y, w, h);
        }
        renderLink(ui);
        String epoch = state.chapter.epoch().map(number -> Ui.roman(number) + " · ").orElse("");
        String title = epoch + shown(state.chapter.title());
        ui.label(title, x + 16, y + 21, 15, Ui.DISPLAY, ui.c("text.primary"));
        ui.label(" · " + tr("rysquests.quest_editor.grid"), x + 16 + ui.width(title, 15, Ui.DISPLAY), y + 21, 12, Ui.REGULAR, ui.c("text.muted"));
        ui.labelFit(tr("rysquests.quest_editor.hint"), x + 16, y + h - 19, w - 32, 11, Ui.REGULAR, ui.c("text.dim"));
        ui.unclip();
    }

    private static float positive(float value, float step) {
        return ((value % step) + step) % step;
    }

    private float nodeWidth(Ui ui, Quest quest) {
        return Math.clamp(ui.width(shown(quest.title()), 12, Ui.REGULAR) + 50, 128, ACTIVE_W);
    }

    private void renderGroups(Ui ui) {
        for (Chapter.Group group : state.chapter.groups()) {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (Quest quest : state.quests.values()) {
                float[] r = rects.get(quest.id());
                if (r != null && quest.group().filter(group.id()::equals).isPresent()) {
                    minX = Math.min(minX, r[0]);
                    minY = Math.min(minY, r[1]);
                    maxX = Math.max(maxX, r[0] + r[2]);
                    maxY = Math.max(maxY, r[1] + r[3]);
                }
            }
            if (minX != Float.MAX_VALUE) {
                ui.dashed(minX - 12, minY - 24, maxX - minX + 24, maxY - minY + 36, ui.c("card.border"));
                String title = shown(group.title());
                ui.rect(minX - 4, minY - 34, ui.width(title, 11, Ui.REGULAR) + 12, 14, ui.mapBg());
                ui.label(title, minX + 2, minY - 27, 11, Ui.REGULAR, ui.c("text.dim"));
            }
        }
    }

    private void renderEdges(Ui ui) {
        for (Quest quest : state.quests.values()) {
            float[] to = rects.get(quest.id());
            for (ResourceLocation dependency : quest.requires()) {
                float[] from = rects.get(dependency);
                if (from != null) {
                    boolean done = ClientProgress.get(dependency).completed() && ClientProgress.get(quest.id()).completed();
                    edge(ui, from[0] + from[2], from[1] + from[3] / 2f, to[0], to[1] + to[3] / 2f, done ? ui.c("slot.have.border") : ui.idleLine());
                }
            }
        }
    }

    private static void edge(Ui ui, float x1, float y1, float x2, float y2, int color) {
        float mid = (x1 + x2) / 2f;
        ui.rect(Math.min(x1, mid), y1 - 1, Math.abs(mid - x1), 2, color);
        ui.rect(mid - 1, Math.min(y1, y2) - 1, 2, Math.abs(y2 - y1) + 2, color);
        ui.rect(Math.min(mid, x2), y2 - 1, Math.abs(x2 - mid), 2, color);
    }

    private void nodeHit(Ui ui, Quest quest, float[] r, int x, int y, int w, int h) {
        float left = Math.max(r[0], x);
        float top = Math.max(r[1], y);
        float right = Math.min(r[0] + r[2], x + w);
        float bottom = Math.min(r[1] + r[3], y + h);
        if (right > left && bottom > top) {
            ui.hit(left, top, right - left, bottom - top, () -> pressNode(quest));
        }
    }

    private void renderNode(Ui ui, Quest quest, int x, int y, int w, int h) {
        float[] r = rects.get(quest.id());
        boolean marked = selected.contains(quest.id());
        boolean target = gesture == Gesture.LINK && !quest.id().equals(linkFrom) && contains(r, pointerX, pointerY);
        long since = external.getOrDefault(quest.id(), 0L);
        boolean fresh = since > 0 && Util.getMillis() - since < EXTERNAL_MS;
        if (fresh) {
            ui.box(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 18 + 6, 12, ui.mix("panel.bg", "chip.mod.fg", 0.18f), 0);
        }
        float height = fresh ? r[3] + 12 : r[3];
        int border = fresh ? ui.c("chip.mod.fg") : marked ? ui.c("accent") : ClientProgress.get(quest.id()).completed() ? ui.doneBorder() : ui.c("panel.border");
        ui.box(r[0], r[1], r[2], height, 9, ui.c("panel.bg"), target ? ui.c("accent") : border);
        ui.item(icon(quest), r[0] + 10, r[1] + 9, 22);
        ui.labelFit(shown(quest.title()), r[0] + 40, r[1] + (fresh ? 15 : r[3] / 2f), r[2] - 50, 12, Ui.REGULAR, ui.nameFg());
        if (fresh) {
            long seconds = (Util.getMillis() - since) / 1000;
            ui.labelFit(text("rysquests.quest_editor.external", seconds), r[0] + 40, r[1] + 32, r[2] - 50, 10, Ui.REGULAR, ui.c("chip.mod.fg"));
        }
        nodeHit(ui, quest, r, x, y, w, h);
    }

    private void renderActive(Ui ui, Quest quest, int x, int y, int w, int h) {
        float[] r = rects.get(quest.id());
        ui.shadow(r[0], r[1], r[2], r[3], 10, 0.5f);
        ui.fill(r[0], r[1], r[2], r[3], 10, ui.c("card.bg"));
        ui.strokeBold(r[0], r[1], r[2], r[3], 10, ui.c("accent"));
        ui.item(icon(quest), r[0] + 12, r[1] + 12, 28);
        ui.labelFit(shown(quest.title()), r[0] + 50, r[1] + 18, r[2] - 62, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.labelFit(Ui.counted("rysquests.plural.task", quest.tasks().size()) + " · " + Ui.counted("rysquests.plural.reward", quest.rewards().size()), r[0] + 50, r[1] + 35, r[2] - 62, 10.5f, Ui.REGULAR, ui.c("text.muted"));
        nodeHit(ui, quest, r, x, y, w, h);
        int fill = ui.c("text.primary");
        int edge = ui.c("accent.text");
        for (float[] corner : new float[][] {{r[0] - 5, r[1] - 5}, {r[0] + r[2] - 3, r[1] - 5}, {r[0] - 5, r[1] + r[3] - 3}, {r[0] + r[2] - 3, r[1] + r[3] - 3}}) {
            ui.rect(corner[0], corner[1], 8, 8, edge);
            ui.rect(corner[0] + 1, corner[1] + 1, 6, 6, fill);
        }
        float dotX = r[0] + r[2] + 6;
        float dotY = r[1] + r[3] / 2f - 6;
        ui.circle(dotX - 4, dotY - 4, 20, ui.warmBorder(0.25f));
        ui.circle(dotX, dotY, 12, ui.c("accent"));
        if (dotX + 12 > x && dotX < x + w && dotY + 12 > y && dotY < y + h) {
            ui.hit(dotX - 4, dotY - 4, 20, 20, () -> startLink(quest.id()));
        }
    }

    private void renderLink(Ui ui) {
        if (gesture != Gesture.LINK || linkFrom == null) {
            return;
        }
        float[] from = rects.get(linkFrom);
        if (from == null) {
            return;
        }
        float x1 = from[0] + from[2];
        float y1 = from[1] + from[3] / 2f;
        float x2 = (float) pointerX;
        float y2 = (float) pointerY;
        float mid = (x1 + x2) / 2f;
        int color = ui.c("accent");
        dashH(ui, x1, mid, y1, color);
        dashV(ui, y1, y2, mid, color);
        dashH(ui, mid, x2, y2, color);
        ui.circle(x2 - 10, y2 - 10, 20, ui.warmBorder(0.25f));
        ui.circle(x2 - 6, y2 - 6, 12, color);
    }

    private static void dashH(Ui ui, float a, float b, float y, int color) {
        for (float at = Math.min(a, b); at < Math.max(a, b); at += 10) {
            ui.rect(at, y - 1, Math.min(6, Math.max(a, b) - at), 2, color);
        }
    }

    private static void dashV(Ui ui, float a, float b, float x, int color) {
        for (float at = Math.min(a, b); at < Math.max(a, b); at += 10) {
            ui.rect(x - 1, at, 2, Math.min(6, Math.max(a, b) - at), color);
        }
    }

    private static ItemStack icon(Quest quest) {
        return quest.icon().map(TaskText::stackOf).orElseGet(() -> quest.tasks().isEmpty() ? ItemStack.EMPTY : TaskText.icon(quest.tasks().getFirst()));
    }

    private static boolean contains(float[] r, double x, double y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    // --- gestures ---

    private void begin(Gesture next) {
        gesture = next;
        startX = pointerX;
        startY = pointerY;
        moved = false;
    }

    private void pressMap() {
        syncFields();
        focus(null);
        if (!Screen.hasShiftDown() && !selected.isEmpty()) {
            selected.clear();
            taskForm = -1;
            rewardForm = -1;
            bound.clear();
        }
        begin(Gesture.PAN);
    }

    private void pressNode(Quest quest) {
        syncFields();
        focus(null);
        if (linkMode) {
            startLink(quest.id());
            return;
        }
        if (Screen.hasShiftDown()) {
            if (!selected.remove(quest.id())) {
                selected.add(quest.id());
            }
            bound.clear();
        } else if (!selected.contains(quest.id())) {
            select(quest.id());
        } else {
            selected.remove(quest.id());
            selected.add(quest.id());
            bound.clear();
        }
        begin(Gesture.MOVE);
    }

    private void startLink(ResourceLocation from) {
        syncFields();
        linkFrom = from;
        begin(Gesture.LINK);
    }

    private void link(ResourceLocation from, ResourceLocation to) {
        if (from.equals(to)) {
            return;
        }
        Quest target = state.quests.get(to);
        if (!target.requires().contains(from) && QuestEdits.wouldCycle(state.quests, to, from)) {
            show(tr("rysquests.quest_editor.cycle"), true);
            return;
        }
        edit(() -> putQuest(QuestEdits.toggled(target, from)));
    }

    void pointer(double mouseX, double mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
    }

    void drag(double mouseX, double mouseY) {
        double lastX = pointerX;
        double lastY = pointerY;
        pointer(mouseX, mouseY);
        if (gesture == Gesture.NONE) {
            bound.values().forEach(field -> field.area.drag(mouseX, mouseY));
            filter.drag(mouseX, mouseY);
        } else if (gesture == Gesture.PAN) {
            panX -= (float) (mouseX - lastX);
            panY -= (float) (mouseY - lastY);
        } else if (gesture == Gesture.MOVE && Math.abs(mouseX - startX) + Math.abs(mouseY - startY) > 3) {
            moved = true;
        }
    }

    void release(double mouseX, double mouseY) {
        pointer(mouseX, mouseY);
        Gesture ended = gesture;
        gesture = Gesture.NONE;
        if (ended == Gesture.MOVE && moved) {
            int dx = (int) Math.round(mouseX - startX);
            int dy = (int) Math.round(mouseY - startY);
            edit(() -> selected.forEach(id -> {
                Quest quest = state.quests.get(id);
                int[] at = position(quest);
                putQuest(QuestEdits.moved(quest, at[0] + dx, at[1] + dy));
            }));
        } else if (ended == Gesture.LINK && linkFrom != null) {
            for (Map.Entry<ResourceLocation, float[]> entry : rects.entrySet()) {
                if (!entry.getKey().equals(linkFrom) && contains(entry.getValue(), mouseX, mouseY)) {
                    link(linkFrom, entry.getKey());
                    break;
                }
            }
            linkFrom = null;
        } else if (ended == Gesture.GRIP) {
            Quest quest = primary();
            if (quest != null && !quest.tasks().isEmpty()) {
                int target = Math.clamp(Math.round((float) (mouseY - gripTop) / ROW_STEP), 0, quest.tasks().size() - 1);
                if (target != gripIndex) {
                    List<TaskDef> tasks = new ArrayList<>(quest.tasks());
                    tasks.add(target, tasks.remove(gripIndex));
                    edit(() -> putQuest(QuestEdits.withTasks(quest, tasks)));
                }
            }
        }
    }

    void scroll(double mouseX, double mouseY, double amount) {
        if (mouseX < leftEdge) {
            treeScroll.wheel(amount, WHEEL);
        } else if (mouseX >= rightEdge) {
            panelScroll.wheel(amount, WHEEL);
        } else if (Screen.hasShiftDown()) {
            panX -= (float) (amount * WHEEL);
        } else {
            panY -= (float) (amount * WHEEL);
        }
    }

    // --- right: the panel ---

    private void syncFields() {
        for (Bound field : List.copyOf(bound.values())) {
            if (!field.area.text().equals(field.shown)) {
                field.shown = field.area.text();
                field.apply.accept(field.area.text());
            }
        }
    }

    private Bound field(String id, boolean multiline, float px, Supplier<String> value, Consumer<String> apply) {
        return bound.computeIfAbsent(id, key -> new Bound(multiline ? new TextArea(true, px, 17, Ui.REGULAR, this::beforeEdit) : new TextArea(false, px, 18, Ui.REGULAR, this::beforeEdit), value.get(), apply));
    }

    private void focus(@Nullable TextArea target) {
        bound.values().forEach(field -> field.area.focus(field.area == target));
        filter.focus(filter == target);
    }

    private boolean typing() {
        return filter.focused() || bound.values().stream().anyMatch(field -> field.area.focused());
    }

    private void click(TextArea area) {
        focus(area);
        area.click(pointerX, pointerY);
    }

    private float input(Ui ui, String id, String label, float x, float y, float w, float px, Supplier<String> value, Consumer<String> apply) {
        ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        Bound field = field(id, false, px, value, apply);
        ui.box(x, boxY, w, 32, 8, ui.c("slot.bg"), field.area.focused() ? ui.c("card.border") : ui.c("button.border"));
        field.area.render(ui, x + 10, boxY + 7, w - 20, 18, ui.c("text.primary"), "");
        ui.hit(x, boxY, w, 32, () -> click(field.area));
        return boxY + 32;
    }

    private float area(Ui ui, String id, String label, float x, float y, float w, float h, float px, Supplier<String> value, Consumer<String> apply) {
        ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        Bound field = field(id, true, px, value, apply);
        ui.box(x, boxY, w, h, 8, ui.c("slot.bg"), field.area.focused() ? ui.c("card.border") : ui.c("button.border"));
        field.area.render(ui, x + 10, boxY + 8, w - 20, h - 16, ui.c("text.primary"), "");
        ui.hit(x, boxY, w, h, () -> click(field.area));
        return boxY + h;
    }

    private Kit.Tab tabEntry(String key, Tab target) {
        return new Kit.Tab(tr(key), -1, tab == target, () -> {
            syncFields();
            tab = target;
            taskForm = -1;
            rewardForm = -1;
            panelScroll.reset();
            bound.clear();
        });
    }

    private void renderPanel(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, false, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float left = x + 15;
        float inner = w - 29;
        if (picker != null) {
            renderPicker(ui, left, y + 14, inner, h - 28);
            return;
        }
        List<Kit.Tab> tabs = List.of(tabEntry("rysquests.quest_editor.tab.props", Tab.PROPS), tabEntry("rysquests.quest_editor.tab.text", Tab.TEXT), tabEntry("rysquests.quest_editor.tab.json", Tab.JSON));
        ui.tabs(tabs, left, y + 8, 28, inner - 100);
        if (dirty()) {
            String unsaved = tr("rysquests.quest_editor.unsaved");
            float unsavedW = ui.width(unsaved, 11, Ui.REGULAR);
            ui.labelRight(unsaved, left + inner, y + 22, 11, Ui.REGULAR, ui.c("accent"));
            ui.circle(left + inner - unsavedW - 10, y + 18.5f, 7, ui.c("accent"));
        }
        ui.rect(x + 1, y + 40, w - 1, 1, ui.c("panel.border"));
        float top = y + 50;
        float room = h - 60;
        int offset = panelScroll.offset();
        ui.clip(x + 1, Math.round(top), w - 1, Math.round(room));
        float cursor = top - offset;
        Quest quest = primary();
        float end;
        if (quest == null) {
            end = renderChapterProps(ui, left, cursor, inner);
        } else if (taskForm >= 0 && taskForm < quest.tasks().size()) {
            end = renderForm(ui, quest, true, taskForm, left, cursor, inner);
        } else if (rewardForm >= 0 && rewardForm < quest.rewards().size()) {
            end = renderForm(ui, quest, false, rewardForm, left, cursor, inner);
        } else {
            end = switch (tab) {
                case PROPS -> renderProps(ui, quest, left, cursor, inner);
                case TEXT -> renderTextTab(ui, quest, left, cursor, inner, room);
                case JSON -> renderJson(ui, quest, left, cursor, inner, room);
            };
        }
        ui.unclip();
        panelScroll.content(end - cursor + 8, Math.round(room));
    }

    private float renderChapterProps(Ui ui, float x, float y, float w) {
        Chapter chapter = state.chapter;
        float cursor;
        if (newChapter) {
            cursor = input(ui, "chapter:id", tr("rysquests.quest_editor.field.chapter_id"), x, y, w, 11.5f, () -> chapter.id().toString(), this::renameChapter);
        } else {
            cursor = readOnly(ui, tr("rysquests.quest_editor.field.chapter_id"), chapter.id().toString(), x, y, w);
        }
        cursor = input(ui, "chapter:title", tr("rysquests.quest_editor.field.title"), x, cursor + 12, w, 12, () -> shown(chapter.title()), value -> putText(state.chapter.title(), value));
        cursor = input(ui, "chapter:epoch", tr("rysquests.quest_editor.field.epoch"), x, cursor + 12, w, 12, () -> chapter.epoch().map(String::valueOf).orElse(""), this::setEpoch);
        if (!chapter.groups().isEmpty()) {
            cursor += 12;
            ui.caps(tr("rysquests.quest_editor.groups"), x, cursor + 7, ui.c("text.dim"));
            cursor += 14;
            for (Chapter.Group group : chapter.groups()) {
                cursor = input(ui, "group:" + group.id(), group.id(), x, cursor + 6, w, 12, () -> shown(group.title()), value -> putText(group.title(), value));
            }
        }
        return filesBox(ui, "chapters/" + chapter.id().getPath() + "/_chapter.json", x, cursor + 12, w);
    }

    private void renameChapter(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !id.getPath().matches("[a-z0-9_]+") || ClientBook.current().chapters().containsKey(id)) {
            return;
        }
        Chapter c = state.chapter;
        Map<String, String> texts = new HashMap<>(state.texts);
        String title = texts.remove(c.title());
        String key = QuestEdits.chapterTitleKey(id);
        if (title != null) {
            texts.put(key, title);
        }
        Map<ResourceLocation, Quest> quests = new LinkedHashMap<>();
        state.quests.values().forEach(quest -> quests.put(quest.id(), quest.withChapter(id)));
        state = new State(new Chapter(id, key, c.subtitle(), c.icon(), c.epoch(), c.defaultView(), c.groups(), c.requires(), c.hidden(), c.section()), quests, texts);
    }

    private void setEpoch(String value) {
        Chapter c = state.chapter;
        Optional<Integer> epoch = value.matches("[0-9]{1,3}") ? Optional.of(Integer.parseInt(value)) : Optional.empty();
        setChapter(new Chapter(c.id(), c.title(), c.subtitle(), c.icon(), epoch, c.defaultView(), c.groups(), c.requires(), c.hidden(), c.section()));
    }

    private float readOnly(Ui ui, String label, String value, float x, float y, float w) {
        ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        ui.box(x, boxY, w, 32, 8, ui.c("slot.bg"), ui.c("button.border"));
        ui.labelFitTip(value, x + 10, boxY + 16, w - 20, 11.5f, Ui.REGULAR, ui.c("text.primary"));
        return boxY + 32;
    }

    private void renameQuest(Quest quest, String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || state.quests.containsKey(id) || ClientBook.current().quests().containsKey(id) || !state.quests.containsKey(quest.id())) {
            return;
        }
        Map<String, String> texts = new HashMap<>(state.texts);
        moveKey(texts, QuestEdits.titleKey(quest.id()), QuestEdits.titleKey(id));
        moveKey(texts, QuestEdits.subtitleKey(quest.id()), QuestEdits.subtitleKey(id));
        moveKey(texts, QuestEdits.textKey(quest.id()), QuestEdits.textKey(id));
        state = new State(state.chapter, QuestEdits.renamed(state.quests, quest.id(), id), texts);
        selected.remove(quest.id());
        selected.add(id);
    }

    private static void moveKey(Map<String, String> texts, String from, String to) {
        String value = texts.remove(from);
        if (value != null) {
            texts.put(to, value);
        }
    }

    private float renderProps(Ui ui, Quest quest, float x, float y, float w) {
        float cursor;
        if (saved.quests.containsKey(quest.id())) {
            cursor = readOnly(ui, tr("rysquests.quest_editor.field.id"), quest.id().toString(), x, y, w);
        } else {
            ResourceLocation original = quest.id();
            cursor = input(ui, "quest:id:" + original, tr("rysquests.quest_editor.field.id"), x, y, w, 11.5f, () -> original.toString(), value -> {
                Quest current = selected.stream().map(state.quests::get).filter(Objects::nonNull).reduce((a, b) -> b).orElse(null);
                if (current != null) {
                    renameQuest(current, value);
                }
            });
        }
        cursor += 12;
        float half = (w - 10) / 2f;
        ResourceLocation id = quest.id();
        input(ui, "quest:title:" + id, tr("rysquests.quest_editor.field.title_lang"), x, cursor, half, 12, () -> shown(quest.title()), value -> {
            Quest current = primary();
            if (current != null) {
                setTitle(current, value);
            }
        });
        renderIconField(ui, quest, x + half + 10, cursor, half);
        cursor += 50 + 12;
        cursor = renderTasks(ui, quest, x, cursor, w) + 12;
        cursor = renderRewards(ui, quest, x, cursor, w) + 12;
        renderDependencies(ui, quest, x, cursor, half);
        renderLayoutField(ui, quest, x + half + 10, cursor, half);
        cursor += 50 + 12;
        return filesBox(ui, "chapters/" + state.chapter.id().getPath() + "/" + lastSegment(quest.id()) + ".json", x, cursor, w);
    }

    private void renderIconField(Ui ui, Quest quest, float x, float y, float w) {
        ui.label(tr("rysquests.quest_editor.field.icon"), x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        ui.box(x, boxY, w, 32, 8, ui.over(x, boxY, w, 32) ? ui.c("slot.hover") : ui.c("slot.bg"), ui.c("button.border"));
        ui.item(icon(quest), x + 8, boxY + 7, 18);
        ui.labelFitTip(quest.icon().map(ResourceLocation::toString).orElse(tr("rysquests.quest_editor.choose")), x + 32, boxY + 16, w - 40, 11, Ui.REGULAR, ui.c("button.fg"));
        ResourceLocation id = quest.id();
        ui.hit(x, boxY, w, 32, () -> pickItem(tr("rysquests.quest_editor.field.icon"), item -> edit(() -> putQuest(QuestEdits.withIcon(state.quests.get(id), item)))));
    }

    private float sectionHead(Ui ui, String label, float x, float y, float w, Runnable add, String addLabel) {
        ui.caps(label, x, y + 12, ui.c("text.dim"));
        float addW = ui.buttonWidth(addLabel, 11, Ui.REGULAR, 8) + 2;
        ui.button(addLabel, x + w - addW, y, addW, 24, 6, 11, Ui.REGULAR, 8, Ui.Style.PLAIN, add);
        return y + 24 + 6;
    }

    private float renderTasks(Ui ui, Quest quest, float x, float y, float w) {
        float cursor = sectionHead(ui, text("rysquests.quest_editor.tasks", quest.tasks().size()), x, y, w, this::pickTask, tr("rysquests.quest_editor.add_task"));
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            float rowY = cursor + i * ROW_STEP;
            float top = cursor;
            int index = i;
            ui.box(x, rowY, w, ROW_H, 8, ui.over(x, rowY, w, ROW_H) ? ui.c("slot.hover") : ui.c("status.bg"), ui.c("panel.border"));
            float cy = rowY + ROW_H / 2f;
            ui.hit(x, rowY, w, ROW_H, () -> openForm(index, -1));
            ui.icon(Icons.Shape.GRIP, x + 9, cy - 5, 10, ui.c("text.dim"));
            ui.hit(x, rowY, 26, ROW_H, () -> {
                gripIndex = index;
                gripTop = top;
                begin(Gesture.GRIP);
            });
            String type = typeName(taskJson(task));
            float chipW = ui.pill(type, x + 27, cy, 10.5f, Ui.REGULAR, 6, 17, 5, ui.c("chip.mod.bg"), ui.c("chip.mod.fg"));
            String mode = modeName(task);
            float modeW = mode.isEmpty() ? 0 : ui.width(mode, 10.5f, Ui.REGULAR) + 8;
            ui.labelFitTip(TaskText.describe(task).getString(), x + 27 + chipW + 8, cy, w - 27 - chipW - 8 - 9 - modeW, 12, Ui.REGULAR, ui.nameFg());
            if (!mode.isEmpty()) {
                ui.labelRight(mode, x + w - 9, cy, 10.5f, Ui.REGULAR, ui.c("text.muted"));
            }
        }
        return cursor + quest.tasks().size() * ROW_STEP - 6;
    }

    private float renderRewards(Ui ui, Quest quest, float x, float y, float w) {
        float cursor = sectionHead(ui, text("rysquests.quest_editor.rewards", quest.rewards().size()), x, y, w, this::pickReward, tr("rysquests.quest_editor.add_reward"));
        for (int i = 0; i < quest.rewards().size(); i++) {
            RewardDef reward = quest.rewards().get(i);
            float rowY = cursor + i * ROW_STEP;
            int index = i;
            ui.box(x, rowY, w, ROW_H, 8, ui.over(x, rowY, w, ROW_H) ? ui.c("slot.hover") : ui.c("status.bg"), ui.c("panel.border"));
            float cy = rowY + ROW_H / 2f;
            String type = typeName(rewardJson(reward));
            float chipW = ui.pill(type, x + 9, cy, 10.5f, Ui.REGULAR, 6, 17, 5, ui.c("chip.fn.bg"), ui.c("chip.fn.fg"));
            ui.labelFitTip(rewardText(reward), x + 9 + chipW + 8, cy, w - 9 - chipW - 8 - 9, 12, Ui.REGULAR, ui.nameFg());
            ui.hit(x, rowY, w, ROW_H, () -> openForm(-1, index));
        }
        return cursor + quest.rewards().size() * ROW_STEP - 6;
    }

    private static String modeName(TaskDef task) {
        JsonObject json = taskJson(task);
        return json.has("mode") ? tr("rysquests.quest_editor.mode." + json.get("mode").getAsString()) : TASK_FIELDS.getOrDefault(json.get("type").getAsString(), List.of()).contains(MODE) ? tr("rysquests.quest_editor.mode.consume") : "";
    }

    private static String typeName(JsonObject json) {
        ResourceLocation type = ResourceLocation.tryParse(json.get("type").getAsString());
        return type == null ? json.get("type").getAsString() : type.getPath();
    }

    private static String rewardText(RewardDef reward) {
        return switch (reward) {
            case ItemReward item -> TaskText.stackOf(item.item()).getHoverName().getString() + " ×" + item.count();
            case XpReward xp -> xp.levels().map(levels -> Ui.counted("rysquests.plural.level", levels)).orElse(xp.points().map(String::valueOf).orElse(""));
            case CurrencyReward currency -> Numbers.compact(currency.amount()) + " " + currency.id();
            case TitleReward title -> tr(title.id());
            case StageReward stage -> stage.stage();
            default -> "";
        };
    }

    private void renderDependencies(Ui ui, Quest quest, float x, float y, float w) {
        ui.label(tr("rysquests.quest_editor.field.requires"), x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        ui.box(x, boxY, w, 32, 8, ui.over(x, boxY, w, 32) ? ui.c("slot.hover") : ui.c("slot.bg"), ui.c("button.border"));
        List<String> names = quest.requires().stream().map(id -> {
            Quest other = state.quests.get(id);
            if (other != null) {
                return shown(other.title());
            }
            Quest elsewhere = ClientBook.current().quests().get(id);
            return elsewhere != null ? tr(elsewhere.title()) : id.toString();
        }).toList();
        boolean all = RequiresMode.ALL.equals(quest.requiresMode());
        String label = names.isEmpty() ? tr("rysquests.quest_editor.none") : String.join(", ", names) + " · " + tr(all ? "rysquests.quest_editor.requires_all" : "rysquests.quest_editor.requires_any");
        ui.labelFitTip(label, x + 10, boxY + 16, w - 20, 12, Ui.REGULAR, ui.c("button.fg"));
        if (!names.isEmpty()) {
            ResourceLocation id = quest.id();
            ui.hit(x, boxY, w, 32, () -> edit(() -> {
                Quest current = state.quests.get(id);
                putQuest(QuestEdits.withRequires(current, current.requires(), RequiresMode.ALL.equals(current.requiresMode()) ? RequiresMode.ANY : RequiresMode.ALL));
            }));
        }
    }

    private void renderLayoutField(Ui ui, Quest quest, float x, float y, float w) {
        ui.label(tr("rysquests.quest_editor.field.layout"), x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        boolean auto = quest.options().layout().x().isEmpty();
        ui.box(x, boxY, w, 32, 8, ui.over(x, boxY, w, 32) ? ui.c("slot.hover") : ui.c("slot.bg"), ui.c("button.border"));
        ui.label(tr(auto ? "rysquests.quest_editor.layout_auto" : "rysquests.quest_editor.layout_manual"), x + 10, boxY + 16, 12, Ui.REGULAR, ui.c("button.fg"));
        ui.fill(x + w - 38, boxY + 8, 28, 16, 8, auto ? ui.c("accent") : ui.c("button.border"));
        ui.circle(auto ? x + w - 24 : x + w - 36, boxY + 10, 12, ui.c(auto ? "accent.text" : "text.muted"));
        ResourceLocation id = quest.id();
        ui.hit(x, boxY, w, 32, () -> edit(() -> {
            Quest current = state.quests.get(id);
            int[] at = position(current);
            putQuest(current.options().layout().x().isEmpty() ? QuestEdits.moved(current, at[0], at[1]) : QuestEdits.auto(current));
        }));
    }

    private float filesBox(Ui ui, String path, float x, float y, float w) {
        ui.box(x, y, w, 52, 8, ui.c("slot.bg"), ui.mix("slot.bg", "panel.border", 0.6f));
        ui.labelFitTip(path, x + 10, y + 18, w - 20, 10.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.labelFit("lang/" + Minecraft.getInstance().getLanguageManager().getSelected() + ".json", x + 10, y + 34, w - 20, 10.5f, Ui.REGULAR, ui.c("text.dim"));
        return y + 52;
    }

    private float renderTextTab(Ui ui, Quest quest, float x, float y, float w, float room) {
        ResourceLocation id = quest.id();
        float cursor = input(ui, "text:subtitle:" + id, tr("rysquests.quest_editor.field.subtitle"), x, y, w, 12, () -> quest.subtitle().map(this::shown).orElse(""), value -> setSubtitle(state.quests.get(id), value));
        return area(ui, "text:body:" + id, tr("rysquests.quest_editor.field.text"), x, cursor + 12, w, Math.max(160, room - 100), 12, () -> quest.text().map(this::shown).orElse(""), value -> setBody(state.quests.get(id), value));
    }

    private static DynamicOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
    }

    private float renderJson(Ui ui, Quest quest, float x, float y, float w, float room) {
        ResourceLocation id = quest.id();
        return area(ui, "json:" + id, tr("rysquests.quest_editor.field.json"), x, y, w, Math.max(200, room - 30), 11, () -> questJson(quest), value -> applyJson(id, value));
    }

    private static String questJson(Quest quest) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(Quest.CODEC.encodeStart(ops(), quest).getOrThrow());
    }

    /** The id and chapter stay what they are: this tab is for everything else. */
    private void applyJson(ResourceLocation id, String value) {
        try {
            var result = Quest.CODEC.parse(ops(), JsonParser.parseString(value));
            if (result.error().isPresent()) {
                show(tr("rysquests.quest_editor.error.json"), true);
                return;
            }
            Quest parsed = result.getOrThrow();
            if (!parsed.id().equals(id)) {
                show(tr("rysquests.quest_editor.error.json_id"), true);
                return;
            }
            putQuest(parsed.withChapter(state.chapter.id()));
            status = null;
        } catch (RuntimeException e) {
            show(tr("rysquests.quest_editor.error.json"), true);
        }
    }

    // --- tasks and rewards ---

    private static JsonObject taskJson(TaskDef task) {
        return TaskDef.CODEC.encodeStart(ops(), task).getOrThrow().getAsJsonObject();
    }

    private static JsonObject rewardJson(RewardDef reward) {
        return RewardDef.CODEC.encodeStart(ops(), reward).getOrThrow().getAsJsonObject();
    }

    private void openForm(int task, int reward) {
        syncFields();
        taskForm = task;
        rewardForm = reward;
        panelScroll.reset();
        bound.clear();
    }

    private void pickTask() {
        openPicker(tr("rysquests.quest_editor.add_task"), () -> typeChoices(TASK_FIELDS, this::addTask));
    }

    private void pickReward() {
        openPicker(tr("rysquests.quest_editor.add_reward"), () -> typeChoices(REWARD_FIELDS, this::addReward));
    }

    private List<Choice> typeChoices(Map<String, List<Field>> types, Consumer<String> chosen) {
        String query = filter.text().trim().toLowerCase(Locale.ROOT);
        List<Choice> out = new ArrayList<>();
        for (String type : types.keySet()) {
            String name = type.substring(type.indexOf(':') + 1);
            if (query.isEmpty() || name.contains(query)) {
                out.add(new Choice(name, "", ItemStack.EMPTY, () -> {
                    closeForms();
                    chosen.accept(type);
                }));
            }
        }
        return out;
    }

    private static JsonObject template(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        switch (type) {
            case "rysquests:item" -> json.addProperty("item", "minecraft:stone");
            case "rysquests:kill" -> json.addProperty("entity", "minecraft:zombie");
            case "rysquests:stat" -> {
                json.addProperty("stat", "minecraft:custom");
                json.addProperty("target", "minecraft:jump");
            }
            case "rysquests:advancement" -> json.addProperty("advancement", "minecraft:story/root");
            case "rysquests:visit" -> json.addProperty("dimension", "minecraft:the_nether");
            case "rysquests:fluid" -> {
                json.addProperty("fluid", "minecraft:water");
                json.addProperty("amount", 1000);
            }
            case "rysquests:xp" -> json.addProperty("levels", 1);
            case "rysquests:currency" -> {
                json.addProperty("id", "coin");
                json.addProperty("amount", 10);
            }
            case "rysquests:title" -> json.addProperty("id", "title.example");
            case "rysquests:stage" -> json.addProperty("stage", "minecraft:story/root");
            case "rysquests:command" -> json.addProperty("command", "say @s");
            case "rysquests:loot_table" -> json.addProperty("table", "minecraft:chests/simple_dungeon");
            case "rysquests:script" -> json.addProperty("script", "mypack:script");
            default -> {
            }
        }
        return json;
    }

    private void addTask(String type) {
        Quest quest = primary();
        var parsed = TaskDef.CODEC.parse(ops(), template(type));
        if (quest == null || parsed.error().isPresent()) {
            return;
        }
        List<TaskDef> tasks = new ArrayList<>(quest.tasks());
        tasks.add(parsed.getOrThrow());
        edit(() -> putQuest(QuestEdits.withTasks(quest, tasks)));
        openForm(tasks.size() - 1, -1);
    }

    private void addReward(String type) {
        Quest quest = primary();
        JsonObject json = template(type);
        if (type.equals("rysquests:item")) {
            json.addProperty("item", "minecraft:bread");
        }
        var parsed = RewardDef.CODEC.parse(ops(), json);
        if (quest == null || parsed.error().isPresent()) {
            return;
        }
        List<RewardDef> rewards = new ArrayList<>(quest.rewards());
        rewards.add(parsed.getOrThrow());
        edit(() -> putQuest(QuestEdits.withRewards(quest, rewards)));
        openForm(-1, rewards.size() - 1);
    }

    private void setFormValue(ResourceLocation questId, boolean task, int index, Field field, String value) {
        Quest quest = state.quests.get(questId);
        if (quest == null || index >= (task ? quest.tasks().size() : quest.rewards().size())) {
            return;
        }
        JsonObject json = task ? taskJson(quest.tasks().get(index)) : rewardJson(quest.rewards().get(index));
        if (value.isBlank()) {
            json.remove(field.name());
        } else if (field.kind() == Kind.NUMBER) {
            if (!value.matches("[0-9]{1,9}")) {
                show(tr("rysquests.quest_editor.error.value"), true);
                return;
            }
            json.addProperty(field.name(), Integer.parseInt(value));
        } else {
            json.addProperty(field.name(), value);
        }
        if (task) {
            var parsed = TaskDef.CODEC.parse(ops(), json);
            if (parsed.error().isPresent()) {
                show(tr("rysquests.quest_editor.error.value"), true);
                return;
            }
            List<TaskDef> tasks = new ArrayList<>(quest.tasks());
            tasks.set(index, parsed.getOrThrow());
            putQuest(QuestEdits.withTasks(quest, tasks));
        } else {
            var parsed = RewardDef.CODEC.parse(ops(), json);
            if (parsed.error().isPresent()) {
                show(tr("rysquests.quest_editor.error.value"), true);
                return;
            }
            List<RewardDef> rewards = new ArrayList<>(quest.rewards());
            rewards.set(index, parsed.getOrThrow());
            putQuest(QuestEdits.withRewards(quest, rewards));
        }
        status = null;
    }

    private float renderForm(Ui ui, Quest quest, boolean task, int index, float x, float y, float w) {
        JsonObject json = task ? taskJson(quest.tasks().get(index)) : rewardJson(quest.rewards().get(index));
        String type = json.get("type").getAsString();
        boolean over = ui.over(x, y, 28, 28);
        ui.box(x, y, 28, 28, 7, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        ui.icon(Icons.Shape.CHEVRON_LEFT, x + 9, y + 9, 10, ui.c("button.fg"));
        ui.hit(x, y, 28, 28, () -> openForm(-1, -1));
        ui.labelFit(typeName(json), x + 38, y + 14, w - 38, 15, Ui.DISPLAY, ui.c("text.primary"));
        float cursor = y + 28;
        ResourceLocation id = quest.id();
        List<Field> fields = (task ? TASK_FIELDS : REWARD_FIELDS).get(type);
        if (fields == null) {
            cursor += 12;
            ui.lines(ui.wrap(tr("rysquests.quest_editor.use_json"), w, 12, Ui.REGULAR, 3), x, cursor + 18, 18, 12, Ui.REGULAR, ui.c("text.muted"));
            cursor += 54;
        } else {
            for (Field field : fields) {
                cursor = renderFormField(ui, field, json, id, task, index, x, cursor + 12, w);
            }
        }
        String remove = tr("rysquests.quest_editor.remove");
        float removeW = ui.buttonWidth(remove, 11.5f, Ui.REGULAR, 10) + 2;
        ui.button(remove, x, cursor + 14, removeW, 30, 8, 11.5f, Ui.REGULAR, 10, Ui.Style.PLAIN, () -> removeRow(id, task, index));
        return cursor + 44;
    }

    private void removeRow(ResourceLocation id, boolean task, int index) {
        Quest quest = state.quests.get(id);
        edit(() -> {
            if (task) {
                List<TaskDef> tasks = new ArrayList<>(quest.tasks());
                tasks.remove(index);
                putQuest(QuestEdits.withTasks(quest, tasks));
            } else {
                List<RewardDef> rewards = new ArrayList<>(quest.rewards());
                rewards.remove(index);
                putQuest(QuestEdits.withRewards(quest, rewards));
            }
        });
        openForm(-1, -1);
    }

    private float renderFormField(Ui ui, Field field, JsonObject json, ResourceLocation id, boolean task, int index, float x, float y, float w) {
        String label = tr("rysquests.quest_editor.task_field." + field.name());
        String current = json.has(field.name()) ? json.get(field.name()).getAsString() : "";
        Consumer<String> set = value -> setFormValue(id, task, index, field, value);
        String fieldId = (task ? "task:" : "reward:") + id + ":" + index + ":" + field.name();
        return switch (field.kind()) {
            case ITEM, ENTITY -> {
                ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
                float boxY = y + 18;
                ui.box(x, boxY, w, 32, 8, ui.over(x, boxY, w, 32) ? ui.c("slot.hover") : ui.c("slot.bg"), ui.c("button.border"));
                float textX = x + 10;
                if (field.kind() == Kind.ITEM) {
                    ui.item(TaskText.stackOf(ResourceLocation.parse(current)), x + 8, boxY + 7, 18);
                    textX = x + 32;
                }
                ui.labelFitTip(current, textX, boxY + 16, x + w - 10 - textX, 11.5f, Ui.REGULAR, ui.c("button.fg"));
                Consumer<ResourceLocation> chosen = picked -> edit(() -> set.accept(picked.toString()));
                ui.hit(x, boxY, w, 32, () -> {
                    if (field.kind() == Kind.ITEM) {
                        pickItem(label, chosen);
                    } else {
                        pickEntity(label, chosen);
                    }
                });
                yield boxY + 32;
            }
            case MODE -> {
                ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
                List<Chips.Chip> chips = List.of("consume", "observe", "accumulate").stream().filter(mode -> task && (!type(json).endsWith("fluid") || !mode.equals("accumulate")))
                        .map(mode -> new Chips.Chip(mode, tr("rysquests.quest_editor.mode." + mode), -1)).toList();
                float[] end = Chips.render(ui, chips, current.isEmpty() ? "consume" : current, mode -> edit(() -> set.accept(mode)), x, y + 18, w);
                yield end[1] + Chips.HEIGHT;
            }
            default -> input(ui, fieldId, label, x, y, w, 12, () -> current, set);
        };
    }

    private static String type(JsonObject json) {
        return json.get("type").getAsString();
    }

    // --- pickers ---

    private void openPicker(String title, Supplier<List<Choice>> choices) {
        syncFields();
        pickerTitle = title;
        picker = choices;
        filter.setText("");
        lastQuery = "\0";
        panelScroll.reset();
        focus(filter);
    }

    private void closePicker() {
        picker = null;
        focus(null);
    }

    private void pickItem(String title, Consumer<ResourceLocation> chosen) {
        openPicker(title, () -> {
            String query = filter.text().trim().toLowerCase(Locale.ROOT);
            List<Choice> out = new ArrayList<>();
            if (query.length() < MIN_QUERY) {
                var inventory = Minecraft.getInstance().player.getInventory();
                List<Item> seen = new ArrayList<>();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (!stack.isEmpty() && !seen.contains(stack.getItem())) {
                        seen.add(stack.getItem());
                        out.add(itemChoice(stack, chosen));
                    }
                }
                return out;
            }
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (BuiltInRegistries.ITEM.getKey(item).toString().contains(query) || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    out.add(itemChoice(stack, chosen));
                    if (out.size() >= MAX_RESULTS) {
                        break;
                    }
                }
            }
            return out;
        });
    }

    private Choice itemChoice(ItemStack stack, Consumer<ResourceLocation> chosen) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new Choice(stack.getHoverName().getString(), "", stack, () -> {
            closePicker();
            chosen.accept(id);
        });
    }

    private void pickEntity(String title, Consumer<ResourceLocation> chosen) {
        openPicker(title, () -> {
            String query = filter.text().trim().toLowerCase(Locale.ROOT);
            List<Choice> out = new ArrayList<>();
            for (var type : BuiltInRegistries.ENTITY_TYPE) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                String name = type.getDescription().getString();
                if ((query.isEmpty() || id.toString().contains(query) || name.toLowerCase(Locale.ROOT).contains(query)) && out.size() < MAX_RESULTS) {
                    out.add(new Choice(name, "", ItemStack.EMPTY, () -> {
                        closePicker();
                        chosen.accept(id);
                    }));
                }
            }
            return out;
        });
    }

    private void renderPicker(Ui ui, float x, float y, float w, float h) {
        boolean over = ui.over(x, y, 28, 28);
        ui.box(x, y, 28, 28, 7, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        ui.icon(Icons.Shape.CHEVRON_LEFT, x + 9, y + 9, 10, ui.c("button.fg"));
        ui.hit(x, y, 28, 28, this::closePicker);
        ui.labelFit(pickerTitle, x + 38, y + 14, w - 38, 15, Ui.DISPLAY, ui.c("text.primary"));
        float boxY = y + 38;
        ui.box(x, boxY, w, 32, 8, ui.c("slot.bg"), filter.focused() ? ui.c("card.border") : ui.c("button.border"));
        filter.render(ui, x + 10, boxY + 7, w - 20, 18, ui.c("text.primary"), tr("rysquests.editor.filter"));
        ui.hit(x, boxY, w, 32, () -> click(filter));
        if (!filter.text().equals(lastQuery)) {
            lastQuery = filter.text();
            found = picker.get();
            panelScroll.reset();
        }
        float listTop = boxY + 42;
        float listH = y + h - listTop;
        if (found.isEmpty()) {
            ui.label(tr("rysquests.editor.pick.empty"), x, listTop + 12, 12, Ui.REGULAR, ui.c("text.dim"));
            return;
        }
        panelScroll.content(found.size() * 37 - 5, Math.round(listH));
        int offset = panelScroll.offset();
        ui.clip(Math.round(x), Math.round(listTop), Math.round(w), Math.round(listH));
        List<Choice> list = found;
        for (int i = 0; i < list.size(); i++) {
            float rowY = listTop + i * 37 - offset;
            if (rowY + 32 > listTop && rowY < listTop + listH) {
                Choice choice = list.get(i);
                ui.box(x, rowY, w, 32, 8, ui.over(x, rowY, w, 32) ? ui.c("slot.hover") : ui.c("status.bg"), ui.c("status.border"));
                ui.item(choice.icon(), x + 9, rowY + 8, 16);
                float textX = choice.icon().isEmpty() ? x + 11 : x + 32;
                ui.labelFitTip(choice.label(), textX, rowY + 16, x + w - 9 - textX, 12, Ui.REGULAR, ui.nameFg());
                ui.hit(x, rowY, w, 32, choice.action());
            }
        }
        ui.unclip();
    }

    // --- keys ---

    boolean typed(char c) {
        for (Bound field : bound.values()) {
            if (field.area.typed(c)) {
                return true;
            }
        }
        return filter.typed(c);
    }

    boolean key(int key) {
        boolean ctrl = Screen.hasControlDown();
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            stepUndo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            stepRedo();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (gesture != Gesture.NONE) {
                gesture = Gesture.NONE;
                linkFrom = null;
            } else if (picker != null) {
                closePicker();
            } else if (taskForm >= 0 || rewardForm >= 0) {
                openForm(-1, -1);
            } else if (linkMode) {
                linkMode = false;
            } else {
                requestExit();
            }
            return true;
        }
        if (!typing()) {
            if (ctrl && key == GLFW.GLFW_KEY_D) {
                duplicateSelected();
            } else if (key == GLFW.GLFW_KEY_DELETE) {
                removeSelected();
            }
            return true;
        }
        for (Bound field : bound.values()) {
            if (field.area.key(key)) {
                return true;
            }
        }
        filter.key(key);
        return true;
    }

    // --- for the autotest ---

    void newChapterForTest(String title) {
        createChapter();
        putText(state.chapter.title(), title);
    }

    ResourceLocation addQuestForTest(String title) {
        addQuest(false);
        Quest quest = primary();
        setTitle(quest, title);
        bound.clear();
        return quest.id();
    }

    void addItemTaskForTest(ResourceLocation item, int count) {
        addTask("rysquests:item");
        setFormValue(primary().id(), true, 0, new Field("item", Kind.ITEM), item.toString());
        setFormValue(primary().id(), true, 0, COUNT, String.valueOf(count));
        openForm(-1, -1);
    }

    void addCoinRewardForTest(int amount) {
        addReward("rysquests:currency");
        setFormValue(primary().id(), false, primary().rewards().size() - 1, new Field("amount", Kind.NUMBER), String.valueOf(amount));
        openForm(-1, -1);
    }

    void moveForTest(ResourceLocation id, int x, int y) {
        putQuest(QuestEdits.moved(state.quests.get(id), x, y));
    }

    void linkForTest(ResourceLocation from, ResourceLocation to) {
        link(from, to);
    }

    void chapterEpochForTest(int epoch) {
        setEpoch(String.valueOf(epoch));
    }

    void autoLayoutForTest() {
        autoLayout();
    }

    void selectForTest(@Nullable ResourceLocation id) {
        if (id == null) {
            selected.clear();
            bound.clear();
        } else {
            select(id);
        }
    }

    void linkModeForTest(boolean on) {
        linkMode = on;
    }

    void tabForTest(String name) {
        tab = Tab.valueOf(name);
        bound.clear();
    }

    boolean dirtyForTest() {
        return dirty();
    }

    @Nullable
    String statusForTest() {
        return status;
    }

    ResourceLocation chapterForTest() {
        return state.chapter.id();
    }

    @Nullable
    float[] rectForTest(ResourceLocation id) {
        return rects.get(id);
    }

    @Nullable
    Quest questForTest(ResourceLocation id) {
        return state.quests.get(id);
    }

    boolean layoutReadyForTest() {
        return layout.size() == state.quests.size() || state.quests.values().stream().allMatch(quest -> quest.options().layout().x().isPresent());
    }
}
