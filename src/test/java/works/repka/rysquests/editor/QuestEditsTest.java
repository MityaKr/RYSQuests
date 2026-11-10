package works.repka.rysquests.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;

class QuestEditsTest {
    private static final ResourceLocation CHAPTER = ResourceLocation.parse("t:power");
    private static final EditorFiles.QuestFiles NOTHING = new EditorFiles.QuestFiles(Map.of(), Map.of(), Set.of());

    private static Quest quest(String path, int x) {
        return QuestEdits.newQuest(CHAPTER.withSuffix("/" + path), CHAPTER, x, 0, false);
    }

    private static Map<ResourceLocation, Quest> chain() {
        Quest a = quest("a", 0);
        Quest b = QuestEdits.toggled(quest("b", 24), a.id());
        Quest c = QuestEdits.toggled(quest("c", 48), b.id());
        Map<ResourceLocation, Quest> quests = new LinkedHashMap<>();
        List.of(a, b, c).forEach(q -> quests.put(q.id(), q));
        return quests;
    }

    @Test
    void newIdsAvoidWhatIsTaken() {
        ResourceLocation taken = CHAPTER.withSuffix("/quest_1");

        assertEquals(CHAPTER.withSuffix("/quest_2"), QuestEdits.freshQuest(CHAPTER, Set.of(taken)));
    }

    @Test
    void positionsSnapToTheGrid() {
        assertEquals(java.util.Optional.of(24), QuestEdits.moved(quest("a", 0), 30, 0).options().layout().x());
        assertEquals(java.util.Optional.empty(), QuestEdits.auto(quest("a", 24)).options().layout().x());
    }

    @Test
    void aDependencyCannotCloseACircle() {
        Map<ResourceLocation, Quest> quests = chain();
        ResourceLocation a = CHAPTER.withSuffix("/a");
        ResourceLocation c = CHAPTER.withSuffix("/c");

        assertTrue(QuestEdits.wouldCycle(quests, a, c));
        assertTrue(QuestEdits.wouldCycle(quests, a, a));
        assertFalse(QuestEdits.wouldCycle(quests, c, a));
    }

    @Test
    void droppingOnADependencyTakesItAway() {
        Quest b = chain().get(CHAPTER.withSuffix("/b"));

        assertEquals(List.of(), QuestEdits.toggled(b, CHAPTER.withSuffix("/a")).requires());
    }

    @Test
    void renamingTakesDependenciesAndKeysAlong() {
        ResourceLocation from = CHAPTER.withSuffix("/a");
        ResourceLocation to = CHAPTER.withSuffix("/first");

        Map<ResourceLocation, Quest> renamed = QuestEdits.renamed(chain(), from, to);

        assertEquals(List.of(to), renamed.get(CHAPTER.withSuffix("/b")).requires());
        assertEquals("quest.t.power.first.title", renamed.get(to).title());
    }

    @Test
    void aSavedQuestReadsBackAndKeepsItsFieldsInOrder(@TempDir Path root) throws Exception {
        Quest quest = QuestEdits.withIcon(chain().get(CHAPTER.withSuffix("/b")), ResourceLocation.parse("minecraft:stone"));
        Chapter chapter = QuestEdits.newChapter(CHAPTER);

        EditorFiles.saveChapter(root, chapter, true, List.of(quest), NOTHING, "en_us", Map.of(quest.title(), "B", chapter.title(), "Power"), JsonOps.INSTANCE, true);

        Path file = root.resolve("data/t/rysquests/chapters/power/b.json");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.indexOf("\"id\"") < text.indexOf("\"chapter\"") && text.indexOf("\"title\"") < text.indexOf("\"icon\"") && text.indexOf("\"icon\"") < text.indexOf("\"requires\""));
        assertEquals(quest, Quest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text)).getOrThrow());
        assertTrue(Files.isRegularFile(root.resolve("data/t/rysquests/chapters/power/_chapter.json")));
        assertTrue(Files.readString(root.resolve("assets/t/lang/en_us.json")).contains("Power"));
    }

    @Test
    void anExistingQuestKeepsItsFile(@TempDir Path root) throws Exception {
        Quest quest = quest("a", 0);
        ResourceLocation file = ResourceLocation.parse("t:rysquests/chapters/01_old/renamed.json");
        EditorFiles.QuestFiles known = new EditorFiles.QuestFiles(Map.of(quest.id(), file), Map.of(), Set.of());

        EditorFiles.saveChapter(root, QuestEdits.newChapter(CHAPTER), false, List.of(quest), known, "en_us", Map.of(), JsonOps.INSTANCE, true);

        assertTrue(Files.isRegularFile(root.resolve("data/t/rysquests/chapters/01_old/renamed.json")));
    }

    @Test
    void aFileNameCannotClimbOutOfTheFolder(@TempDir Path root) {
        Quest quest = QuestEdits.newQuest(ResourceLocation.parse("t:power/.."), CHAPTER, 0, 0, false);

        assertThrows(EditorFiles.Rejected.class, () -> EditorFiles.saveChapter(root, QuestEdits.newChapter(CHAPTER), false, List.of(quest), NOTHING, "en_us", Map.of(), JsonOps.INSTANCE, true));
    }

    @Test
    void aTextOutsideTheSavedQuestsIsRefused(@TempDir Path root) {
        assertThrows(EditorFiles.Rejected.class, () -> EditorFiles.saveChapter(root, QuestEdits.newChapter(CHAPTER), false, List.of(quest("a", 0)), NOTHING, "en_us", Map.of("gui.done", "x"), JsonOps.INSTANCE, true));
    }

    @Test
    void aQuestInASharedFileIsRefused(@TempDir Path root) {
        Quest quest = quest("a", 0);
        EditorFiles.QuestFiles known = new EditorFiles.QuestFiles(Map.of(), Map.of(), Set.of(quest.id()));

        EditorFiles.Rejected rejected = assertThrows(EditorFiles.Rejected.class, () -> EditorFiles.saveChapter(root, QuestEdits.newChapter(CHAPTER), false, List.of(quest), known, "en_us", Map.of(), JsonOps.INSTANCE, true));

        assertEquals("rysquests.editor.error.shared_file", rejected.key());
    }

    @Test
    void writingTwiceChangesNothing(@TempDir Path root) throws IOException, EditorFiles.Rejected {
        Quest quest = quest("a", 0);
        EditorFiles.saveChapter(root, QuestEdits.newChapter(CHAPTER), false, List.of(quest), NOTHING, "en_us", Map.of(), JsonOps.INSTANCE, true);
        Path file = root.resolve("data/t/rysquests/chapters/power/a.json");
        String first = Files.readString(file);

        EditorFiles.saveChapter(root, QuestEdits.newChapter(CHAPTER), false, List.of(quest), NOTHING, "en_us", Map.of(), JsonOps.INSTANCE, true);

        assertEquals(first, Files.readString(file));
    }
}
