package works.repka.rysquests.integration.ftbq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Ids;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Result;

/** A broken file stops the import with its file and line, and the import can go on without it; the new data of an import. */
class FtbqBrokenFileTest {
    @TempDir
    Path quests;

    private static final String GOOD = """
            {
            \tfilename: "a_good"
            \tgroup: "1A"
            \tid: "4C3F7CDE181CAFB5"
            \torder_index: 0
            \tquests: [
            \t\t{ id: "73F8F7A274272D6D", size: 2.0d, tasks: [{ id: "7BA02683DFEFB03A", type: "checkmark" }], x: 1.5d, y: -2.0d }
            \t\t{ id: "41239A05E60B07FC", optional: true, dependencies: ["73F8F7A274272D6D"],
            \t\t\ttasks: [{ id: "7BA02683DFEFB03B", type: "observation" }, { id: "7BA02683DFEFB03C", type: "checkmark" }],
            \t\t\trewards: [{ id: "47AD93D250742569", type: "toast" }, { id: "47AD93D25074256A", type: "xp", xp: 5 }] }
            \t]
            }
            """;

    /** The object of the second quest never closes: reading stops at the end of the file, line 9. */
    private static final String BROKEN = """
            {
            \tfilename: "b_broken"
            \tgroup: ""
            \tid: "5C3F7CDE181CAFB5"
            \torder_index: 1
            \tquests: [
            \t\t{ id: "63F8F7A274272D6D", tasks: [{ id: "6BA02683DFEFB03A", type: "checkmark" }] }
            \t\t{ id: "63F8F7A274272D6E"
            """;

    private void write(String path, String text) throws Exception {
        Files.createDirectories(quests.resolve(path).getParent());
        Files.writeString(quests.resolve(path), text, StandardCharsets.UTF_8);
    }

    private void book() throws Exception {
        write("data.snbt", "{ id: \"0000000000000001\" }");
        write("chapter_groups.snbt", "{ chapter_groups: [ { id: \"1A\" } ] }");
        write("lang/en_us.snbt", "{ chapter_group.1A.title: \"Basics\", quest.73F8F7A274272D6D.title: \"Start\" }");
        write("chapters/a_good.snbt", GOOD);
        write("chapters/b_broken.snbt", BROKEN);
    }

    @Test
    void aBrokenChapterIsNamedWithItsLine() throws Exception {
        book();
        FtbqFiles.Read read = FtbqFiles.read(quests, Set.of());
        assertEquals(1, read.failures().size());
        FtbqFiles.Failure failure = read.failures().getFirst();
        assertEquals("chapters/b_broken.snbt", failure.file());
        assertEquals(9, failure.line(), failure.toString());
        assertTrue(failure.skippable());
        assertEquals(1, read.chaptersRead());
        assertEquals(2, read.chaptersTotal());
        assertThrows(java.io.IOException.class, () -> FtbqFiles.read(quests), "the plain read stops at it");
    }

    @Test
    void theImportGoesOnWithoutTheBrokenChapter() throws Exception {
        book();
        FtbqFiles.Read read = FtbqFiles.read(quests, Set.of("chapters/b_broken.snbt"));
        assertEquals(List.of(), read.failures());
        Result result = FtbqImporter.convert(read.source(), "pack", Ids.empty());
        assertEquals(1, result.chapters());
        assertEquals(2, result.quests());
    }

    @Test
    void theBookItselfCannotBeLeftOut() throws Exception {
        book();
        write("data.snbt", "{ id: ");
        FtbqFiles.Failure failure = FtbqFiles.read(quests, Set.of("chapters/b_broken.snbt")).failures().getFirst();
        assertEquals("data.snbt", failure.file());
        assertFalse(failure.skippable());
    }

    @Test
    void aChapterThatCannotBeConvertedIsNamedToo() throws Exception {
        book();
        write("chapters/b_broken.snbt", "{ filename: \"b_broken\", id: \"5C3F7CDE181CAFB5\", quests: [ { tasks: [] } ] }");
        FtbqFiles.Read read = FtbqFiles.read(quests, Set.of());
        FtbqImporter.ChapterError error = assertThrows(FtbqImporter.ChapterError.class, () -> FtbqImporter.convert(read.source(), "pack", Ids.empty()));
        assertEquals("chapters/b_broken.snbt", error.file());
    }

    @Test
    void placesMilestonesSectionsAndDraftsComeAcross() throws Exception {
        book();
        Result result = FtbqImporter.convert(FtbqFiles.read(quests, Set.of("chapters/b_broken.snbt")).source(), "pack", Ids.empty());
        JsonObject book = result.files().get("data/pack/rysquests/book.json");
        assertFalse(book.get("published").getAsBoolean(), "a carried over book is closed to players");
        JsonObject epoch = book.getAsJsonArray("epochs").get(0).getAsJsonObject();
        assertTrue(epoch.get("imported").getAsBoolean());
        assertEquals("Basics", result.lang().get("en_us").get(epoch.get("title").getAsString()));

        JsonObject start = result.files().get("data/pack/rysquests/chapters/a_good/start.json");
        assertTrue(start.get("milestone").getAsBoolean());
        assertFalse(start.has("size"));
        assertEquals(36, start.getAsJsonObject("layout").get("x").getAsInt(), "FTB's place is kept, one cell to the editor's grid step");
        assertEquals(-48, start.getAsJsonObject("layout").get("y").getAsInt());
        assertFalse(start.getAsJsonObject("layout").has("pin"), "kept, not pinned");
        assertFalse(start.has("draft"));

        JsonObject other = result.files().entrySet().stream().filter(entry -> entry.getKey().contains("a_good/q_")).map(java.util.Map.Entry::getValue).findFirst().orElseThrow();
        assertTrue(other.get("draft").getAsBoolean(), "a quest that lost a task or a reward waits for the author");
        assertEquals("rysquests:checkbox", other.getAsJsonArray("tasks").get(0).getAsJsonObject().get("type").getAsString(), "the task stands in as a checkbox");
        assertEquals(2, other.getAsJsonArray("tasks").size());
        assertEquals(1, other.getAsJsonArray("rewards").size(), "the toast is left out");

        ImportReport report = result.report();
        assertEquals(1, report.chapters());
        assertEquals(2, report.quests());
        assertEquals(1, report.optionalQuests());
        assertEquals(1, report.dependencies());
        assertEquals(1, report.sections());
        assertEquals(1, report.hiddenQuests());
        assertEquals(List.of("observation"), report.manual(ImportReport.UNCONVERTED_TASK).types());
        assertEquals(1, report.manual(ImportReport.UNCONVERTED_TASK).count());
        assertEquals(List.of("toast"), report.manual(ImportReport.DROPPED_REWARD).types());
        assertTrue(report.manual(ImportReport.DROPPED_REWARD).hidden());
        assertEquals(report, ImportReport.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, ImportReport.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, report).getOrThrow()).getOrThrow());
    }
}
