package works.repka.rysquests.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.integration.ftbq.FtbqFiles;
import works.repka.rysquests.integration.ftbq.FtbqImporter;

/** The rule of the automatic map form (plan, section 5), on made up books and on the real books of RepYS and ATM10. */
class MapFormsTest {
    private static Chapter chapter(String id, Optional<Integer> epoch) {
        return new Chapter(ResourceLocation.parse(id), id, Optional.empty(), Optional.empty(), epoch, Optional.empty(), List.of(), List.of(), false, Chapter.STORY);
    }

    private static Quest quest(String id, String chapter, String... requires) {
        return new Quest(ResourceLocation.parse(id), Optional.of(ResourceLocation.parse(chapter)), Optional.empty(), id, Optional.empty(), Optional.empty(), Optional.empty(),
                java.util.Arrays.stream(requires).map(ResourceLocation::parse).toList(), RequiresMode.ALL, Optional.empty(), List.of(), List.of(), Optional.empty(), QuestOptions.DEFAULT);
    }

    /** {@code count} chapters of two quests each, the second needing the first; {@code linked} of them also need the chapter before. */
    private static MapForms.Choice book(BookMeta meta, int count, int withEpoch, int linked) {
        List<Chapter> chapters = new ArrayList<>();
        List<Quest> quests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = "t:c" + i;
            chapters.add(chapter(id, i < withEpoch ? Optional.of(1 + i / 3) : Optional.empty()));
            quests.add(i > 0 && i <= linked ? quest(id + "/a", id, "t:c" + (i - 1) + "/b") : quest(id + "/a", id));
            quests.add(quest(id + "/b", id, id + "/a"));
        }
        return MapForms.auto(meta, chapters, quests);
    }

    @Test
    void mostChaptersWithAnEpochMakeASerpentine() {
        MapForms.Choice choice = book(BookMeta.DEFAULT, 10, 6, 9);
        assertEquals(MapForm.SERPENTINE, choice.form());
        assertEquals(MapForms.Reason.EPOCHS, choice.reason());
        assertEquals(MapForm.GENERATOR, book(BookMeta.DEFAULT, 10, 5, 9).form(), "half is not most, and the chapters are well linked");
    }

    @Test
    void fewLinksAndManyChaptersMakeAGalaxy() {
        assertEquals(MapForm.GALAXY, book(BookMeta.DEFAULT, 8, 0, 0).form());
        assertEquals(MapForm.GENERATOR, book(BookMeta.DEFAULT, 7, 0, 0).form(), "too few chapters for a galaxy");
        assertEquals(MapForm.GENERATOR, book(BookMeta.DEFAULT, 10, 0, 9).form(), "9 links across of 19 is not few");
    }

    @Test
    void epochsMadeByTheImportDoNotCount() {
        List<BookMeta.Epoch> imported = List.of(new BookMeta.Epoch("a", true), new BookMeta.Epoch("b", true), new BookMeta.Epoch("c", true), new BookMeta.Epoch("d", true));
        MapForms.Choice choice = book(BookMeta.DEFAULT.withEpochs(imported), 10, 10, 0);
        assertEquals(MapForm.GALAXY, choice.form());
        assertEquals(0, choice.chaptersWithEpoch());
    }

    @Test
    void theAuthorsFormWins() {
        List<Chapter> chapters = List.of(chapter("t:c", Optional.of(1)));
        MapForms.Choice choice = MapForms.choose(BookMeta.DEFAULT.withMapForm(MapForm.SPIRAL), chapters, List.of(quest("t:c/a", "t:c")));
        assertEquals(MapForm.SPIRAL, choice.form());
        assertEquals(MapForms.Reason.SET_BY_AUTHOR, choice.reason());
    }

    @Test
    void chaptersOffTheMapAreNotCounted() {
        List<Chapter> chapters = List.of(chapter("t:a", Optional.empty()).withOffMap(true), chapter("t:b", Optional.empty()).withOffMap(true), chapter("t:c", Optional.of(1)));
        List<Quest> quests = List.of(quest("t:a/q", "t:a"), quest("t:b/q", "t:b"), quest("t:c/q", "t:c"));
        MapForms.Choice choice = MapForms.auto(BookMeta.DEFAULT, chapters, quests);
        assertEquals(1, choice.chapters());
        assertEquals(MapForm.SERPENTINE, choice.form());
    }

    // --- real books ---

    private static Optional<Path> folder(String property) {
        String value = System.getProperty(property);
        return value == null || value.isBlank() || !Files.isDirectory(Path.of(value)) ? Optional.empty() : Optional.of(Path.of(value));
    }

    private static BookValidator.Result repys(String property) throws IOException {
        Optional<Path> folder = folder(property);
        assumeTrue(folder.isPresent(), "no RepYS book at " + System.getProperty(property));
        Map<ResourceLocation, JsonElement> files = TestBooks.folder(folder.get(), "repys");
        files.values().forEach(MapFormsTest::stripComponents);
        return TestBooks.validate(files);
    }

    @Test
    void repysIsASerpentine() throws IOException {
        QuestBook book = repys("rysquests.repysBook").book();
        MapForms.Choice choice = MapForms.choose(book);
        assertEquals(MapForm.SERPENTINE, choice.form(), choice.toString());
        assertTrue(choice.chaptersWithEpoch() * 2 > choice.chapters(), choice.toString());
    }

    @Test
    void theRepysBookReadsWithoutErrors() throws IOException {
        for (String property : List.of("rysquests.repysBook", "rysquests.repysPack")) {
            if (folder(property).isEmpty()) {
                continue;
            }
            BookValidator.Result result = repys(property);
            List<BookReport.Issue> errors = result.report().issues().stream().filter(issue -> issue.severity() == BookReport.Severity.ERROR).toList();
            assertEquals(List.of(), errors, property);
            assertTrue(result.book().meta().published(), "RepYS stays open to its players");
            assertTrue(result.book().quests().values().stream().anyMatch(quest -> quest.options().milestone()), "its size 2 quests are milestones");
        }
    }

    @Test
    void atm10CarriedOverIsAGalaxy() throws IOException {
        Optional<Path> folder = folder("rysquests.atm10Quests");
        assumeTrue(folder.isPresent(), "no ATM10 FTB Quests book at " + System.getProperty("rysquests.atm10Quests"));
        FtbqImporter.Result result = FtbqImporter.convert(FtbqFiles.read(folder.get()), "atm", FtbqImporter.Ids.empty());
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        result.files().forEach((path, json) -> {
            stripComponents(json);
            String[] parts = path.split("/", 4);
            files.put(ResourceLocation.fromNamespaceAndPath(parts[1], parts[3].substring(0, parts[3].length() - ".json".length())), json);
        });
        QuestBook book = TestBooks.book(files);
        assertTrue(book.meta().epochs().stream().allMatch(BookMeta.Epoch::imported), "chapter groups became the import's sections");
        MapForms.Choice choice = MapForms.choose(book);
        assertEquals(MapForm.GALAXY, choice.form(), choice.toString());
        assertEquals(0, choice.chaptersWithEpoch());
        assertTrue(choice.crossLinks() * 20 < choice.links(), "few links between chapters: " + choice);
    }

    /** Component types of absent mods cannot be parsed here; in the real game they resolve. */
    private static void stripComponents(JsonElement element) {
        if (element.isJsonObject()) {
            element.getAsJsonObject().remove("components");
            element.getAsJsonObject().remove("nbt");
            element.getAsJsonObject().entrySet().forEach(entry -> stripComponents(entry.getValue()));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(MapFormsTest::stripComponents);
        }
    }
}
