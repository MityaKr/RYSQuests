package works.repka.rysquests.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.data.BookHash;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.TestBooks;

/** The version the editor's save is checked by, so that two operators do not overwrite each other, and the drafts on disk. */
class EditorVersionTest {
    private static final ResourceLocation CHAPTER = ResourceLocation.parse("t:c");

    @TempDir
    Path folder;

    private static Map<ResourceLocation, JsonElement> files(String questTitle) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("t:chapters/c/_chapter"), TestBooks.json("{\"id\":\"t:c\",\"title\":\"c\"}"));
        files.put(ResourceLocation.parse("t:chapters/c/a"), TestBooks.json("{\"id\":\"t:c/a\",\"title\":\"" + questTitle + "\",\"tasks\":[{\"type\":\"rysquests:item\",\"item\":\"minecraft:stone\",\"count\":4}]}"));
        files.put(ResourceLocation.parse("t:chapters/d/_chapter"), TestBooks.json("{\"id\":\"t:d\",\"title\":\"d\"}"));
        files.put(ResourceLocation.parse("t:chapters/d/b"), TestBooks.json("{\"id\":\"t:d/b\",\"title\":\"b\"}"));
        return files;
    }

    @Test
    void theVersionChangesOnlyWithItsOwnChapter() {
        QuestBook book = TestBooks.book(files("a"));
        String version = BookHash.chapterVersion(book, CHAPTER, JsonOps.INSTANCE);
        assertEquals(version, BookHash.chapterVersion(TestBooks.book(files("a")), CHAPTER, JsonOps.INSTANCE), "the same chapter has the same version");
        assertNotEquals(version, BookHash.chapterVersion(TestBooks.book(files("changed by someone else")), CHAPTER, JsonOps.INSTANCE));
        Map<ResourceLocation, JsonElement> otherChapter = files("a");
        otherChapter.put(ResourceLocation.parse("t:chapters/d/b"), TestBooks.json("{\"id\":\"t:d/b\",\"title\":\"other\"}"));
        assertEquals(version, BookHash.chapterVersion(TestBooks.book(otherChapter), CHAPTER, JsonOps.INSTANCE), "a change elsewhere is no conflict");
        assertEquals("", BookHash.chapterVersion(book, ResourceLocation.parse("t:new"), JsonOps.INSTANCE), "a chapter not made yet");
    }

    @Test
    void theEditorArrivesAtTheVersionOfWhatItSaved() throws Exception {
        QuestBook book = TestBooks.book(files("a"));
        Chapter chapter = book.chapters().get(CHAPTER);
        List<Quest> quests = book.quests().values().stream().filter(quest -> quest.chapter().filter(CHAPTER::equals).isPresent()).toList();
        // What the editor holds after a save is what the server reads back from the files it wrote.
        EditorFiles.saveChapter(folder, chapter, true, quests, new EditorFiles.QuestFiles(Map.of(), Map.of(), java.util.Set.of()), "en_us", Map.of(), JsonOps.INSTANCE, true);
        QuestBook reread = TestBooks.book(TestBooks.folder(folder.resolve("data/t/rysquests"), "t"));
        assertEquals(BookHash.chapterVersion(chapter, quests, JsonOps.INSTANCE), BookHash.chapterVersion(reread, CHAPTER, JsonOps.INSTANCE));
    }

    @Test
    void aDraftOutlivesTheSession() throws Exception {
        QuestBook book = TestBooks.book(files("a"));
        Chapter chapter = book.chapters().get(CHAPTER);
        List<Quest> quests = List.copyOf(book.quests().values().stream().filter(quest -> quest.chapter().filter(CHAPTER::equals).isPresent()).toList());
        EditorDrafts.ChapterDraft draft = new EditorDrafts.ChapterDraft(chapter, quests, Map.of("quest.t.c.a.title", "Камень"), "ru_ru", "v1", false, 42L);
        EditorDrafts.save(folder, draft, JsonOps.INSTANCE);
        assertEquals(List.of(CHAPTER), EditorDrafts.chapters(folder));
        assertEquals(draft, EditorDrafts.load(folder, CHAPTER, JsonOps.INSTANCE).orElseThrow());
        EditorDrafts.discard(folder, CHAPTER);
        assertTrue(EditorDrafts.load(folder, CHAPTER, JsonOps.INSTANCE).isEmpty());
        assertTrue(EditorDrafts.chapters(folder).isEmpty());
    }
}
