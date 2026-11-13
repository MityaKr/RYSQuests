package works.repka.rysquests.client.screen;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientDrafts;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.editor.QuestEdits;

/**
 * Hooks of the in-game autotest into the editor of the book ({@link EditorPages}): what a test needs to drive it without hunting for
 * pixels. Nothing here is used by the mod itself.
 */
public final class EditorTesting {
    private EditorTesting() {
    }

    @Nullable
    private static BookScreen screen() {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen : null;
    }

    /** Opens the book in the editor on a tab: map, guides, shop, achievements, tabs or check. */
    public static void open(String tab) {
        if (screen() == null) {
            Minecraft.getInstance().setScreen(new BookScreen());
        }
        EditorBook.active = true;
        EditorPages.current = tab;
        BookScreen screen = screen();
        if (screen != null) {
            screen.closePage();
        }
    }

    public static void leave() {
        EditorBook.active = false;
    }

    public static boolean on() {
        BookScreen screen = screen();
        return screen != null && screen.editor.on();
    }

    /** The entry of the first-run screens after the question of picture 09a. */
    public static void startNewBook(String title, MapForm form, boolean epochs) {
        BookScreen screen = screen();
        if (screen != null) {
            screen.editor.startNewBook(title, form, epochs);
        }
    }

    private static EditorMapView map() {
        return screen().editor.map();
    }

    @Nullable
    public static ResourceLocation chapter() {
        return map().chapterForTest();
    }

    public static void table(@Nullable ResourceLocation chapter) {
        if (chapter != null) {
            map().openTable(chapter);
        }
    }

    public static void canvas() {
        map().modeForTest(EditorMapView.Mode.CANVAS);
    }

    public static void mapForm() {
        map().modeForTest(EditorMapView.Mode.FORM);
    }

    public static void chapterForm(ResourceLocation chapter) {
        map().chapterFormForTest(chapter);
    }

    /** Types a title into the table's quick entry and presses Enter. */
    public static void typeInTable(String title) {
        map().tableForTest().typeEntryForTest(title);
    }

    public static void tick(ResourceLocation quest) {
        map().tableForTest().tickForTest(quest);
    }

    /** The quests of a chapter in the draft, in the table's order. */
    public static List<ResourceLocation> quests(ResourceLocation chapter) {
        EditorBook editor = EditorBook.get();
        QuestTable table = map().tableForTest();
        table.show(chapter);
        return table.rows(editor.view()).stream().map(row -> row.quest().id()).toList();
    }

    @Nullable
    public static Quest quest(ResourceLocation id) {
        return EditorBook.get().quest(id);
    }

    public static String title(ResourceLocation quest) {
        Quest found = quest(quest);
        return found == null ? "" : EditorBook.get().text(found.title());
    }

    public static void select(@Nullable ResourceLocation quest) {
        map().select(quest);
    }

    /** "after" a quest: the link the canvas makes, with the refusal of a circle. */
    public static void link(ResourceLocation quest, ResourceLocation after) {
        QuestTable.link(quest, after);
    }

    public static boolean holdLink(ResourceLocation from, ResourceLocation to) {
        return map().holdLinkForTest(from, to);
    }

    public static void dropLink() {
        map().dropLinkForTest();
    }

    public static boolean canvasReady() {
        return map().canvasReadyForTest();
    }

    public static void flyTo(ResourceLocation quest) {
        map().flyToQuestForTest(quest);
    }

    public static void setKind(ResourceLocation quest, QuestEdits.Kind kind) {
        EditorBook.get().editQuest(quest, "kind", q -> QuestEdits.withKind(q, kind));
    }

    public static void addTask(ResourceLocation quest, TaskDef task) {
        QuestForm.addTask(quest, task);
    }

    public static void addReward(ResourceLocation quest, RewardDef reward) {
        QuestForm.addReward(quest, reward);
    }

    /** Opens "Что нужно сделать?" on a quest and fills it, without pressing "Добавить задачу". */
    public static void pickTask(ResourceLocation quest, String kind, @Nullable ResourceLocation item, int count, String text) {
        map().pickTask(quest);
        map().pickerForTest().chooseForTest(kind, item, count, text);
    }

    public static void pickerQuery(String query) {
        map().pickerForTest().queryForTest(query);
    }

    public static void confirmPicker() {
        map().pickerForTest().confirmForTest();
    }

    public static boolean pickerOpen() {
        return map().pickerForTest().isOpen();
    }

    // --- the draft and the save ---

    public static int unsaved() {
        return EditorBook.get().unsaved();
    }

    public static List<String> lines() {
        return EditorBook.get().lines();
    }

    public static void save() {
        EditorBook.get().save(screen(), null);
    }

    public static boolean saving() {
        return EditorBook.get().saving();
    }

    public static void undo() {
        EditorBook.get().undo();
    }

    /** Forgets the draft in memory, as closing the game does; the next look reads it from the disk. */
    public static void forgetDraft() {
        EditorBook.forgetForTest();
    }

    public static boolean restoredFromDisk() {
        return EditorBook.get().restoredFromDisk();
    }

    public static Path draftFile() {
        return ClientDrafts.folder().resolve("book_draft.json");
    }

    /** Asks the editor's question before closing the book (picture 33b). @return whether it was asked */
    public static boolean askClose() {
        BookScreen screen = screen();
        return screen != null && screen.editor.askBeforeClose(() -> { });
    }

    public static void askRestore() {
        BookScreen screen = screen();
        if (screen != null) {
            screen.editor.askRestore();
        }
    }

    /** Answers the open question as if a button was pressed, by closing it with nothing done. */
    public static void dismissQuestion() {
        BookScreen screen = screen();
        if (screen != null) {
            screen.answer(() -> { }).run();
        }
    }

    public static void keepRestored() {
        dismissQuestion();
        EditorBook.get().keepRestored();
    }

    public static boolean conflict() {
        return EditorBook.get().conflictForTest();
    }

    public static void saveOver() {
        dismissQuestion();
        EditorBook.get().saveOver();
    }

    // --- the other tabs ---

    public static void newOffer() {
        ((ShopEditorView) screen().editor.page(EditorPages.SHOP)).newOfferForTest();
    }

    public static void newAchievement() {
        ((AchievementEditorView) screen().editor.page(EditorPages.ACHIEVEMENTS)).newForTest();
    }

    public static void newGuide(String title, String text) {
        GuideEditorView guides = (GuideEditorView) screen().editor.page(EditorPages.GUIDES);
        guides.newGuide();
        guides.writeForTest(title, text);
    }

    @Nullable
    public static ResourceLocation guide() {
        return ((GuideEditorView) screen().editor.page(EditorPages.GUIDES)).docForTest();
    }

    public static void tab(ResourceLocation section) {
        ((TabsEditorView) screen().editor.page(EditorPages.TABS)).select(section);
    }

    /** "Новая вкладка": cards, pages or matrix. */
    public static void newTab(String template) {
        ((TabsEditorView) screen().editor.page(EditorPages.TABS)).newSection(template);
    }

    public static void cell(int row, int column) {
        ((TabsEditorView) screen().editor.page(EditorPages.TABS)).cellForTest(row, column);
    }

    public static void checkStuck() {
        ((CheckView) screen().editor.page(EditorPages.CHECK)).showStuckForTest();
    }

    public static void checkIssues() {
        ((CheckView) screen().editor.page(EditorPages.CHECK)).showIssuesForTest();
    }

    public static int issues() {
        return ((CheckView) screen().editor.page(EditorPages.CHECK)).issuesForTest();
    }

    public static void setMapForm(MapForm form) {
        EditorBook.get().setMapForm(form);
    }

    public static Optional<MapForm> draftForm() {
        return EditorBook.get().draft().meta().map(meta -> meta.mapForm());
    }
}
