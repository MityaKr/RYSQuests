package works.repka.rysquests.client.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
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
import works.repka.rysquests.data.BookLayout;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.data.TestBooks;
import works.repka.rysquests.integration.ftbq.FtbqFiles;
import works.repka.rysquests.integration.ftbq.FtbqImporter;

/**
 * The canvas layout on made up books and on the real books of RepYS and ATM10: chapters never overlap, labels stay in their
 * chapters, every quest on the map is placed inside its chapter, one book always gives one layout, and ATM10 lays out in time.
 */
class CanvasLayoutTest {
    private static final List<MapForm> FORMS = List.of(MapForm.SERPENTINE, MapForm.SPIRAL, MapForm.GENERATOR, MapForm.GALAXY);

    // --- made up books ---

    private static Chapter chapter(String id, int epoch) {
        return new Chapter(ResourceLocation.parse(id), id, Optional.empty(), Optional.empty(), epoch > 0 ? Optional.of(epoch) : Optional.empty(), Optional.empty(), List.of(), List.of(),
                false, Chapter.STORY);
    }

    private static Quest quest(String id, String chapter, boolean optional, String... requires) {
        QuestOptions options = new QuestOptions(QuestOptions.DEFAULT.layout(), false, optional, false, Optional.empty(), false, List.of(), false, false);
        return new Quest(ResourceLocation.parse(id), Optional.of(ResourceLocation.parse(chapter)), Optional.empty(), id, Optional.empty(), Optional.empty(), Optional.empty(),
                java.util.Arrays.stream(requires).map(ResourceLocation::parse).toList(), RequiresMode.ALL, Optional.empty(), List.of(), List.of(), Optional.empty(), options);
    }

    /** {@code count} chapters of a chain of main quests, each with a few side quests, three chapters to an epoch. */
    static MapBook madeUp(int count, int mains, int sides) {
        List<Chapter> chapters = new ArrayList<>();
        List<Quest> quests = new ArrayList<>();
        for (int c = 0; c < count; c++) {
            String ch = "t:c" + c;
            chapters.add(chapter(ch, 1 + c / 3));
            for (int m = 0; m < mains; m++) {
                String id = ch + "/m" + m;
                quests.add(m == 0 ? (c == 0 ? quest(id, ch, false) : quest(id, ch, false, "t:c" + (c - 1) + "/m" + (mains - 1))) : quest(id, ch, false, ch + "/m" + (m - 1)));
                for (int s = 0; s < sides; s++) {
                    quests.add(quest(ch + "/m" + m + "s" + s, ch, true, s == 0 ? id : ch + "/m" + m + "s" + (s - 1)));
                }
            }
        }
        return MapBook.of(BookMeta.DEFAULT, chapters, quests, "made-up-" + count);
    }

    @Test
    void everyFormHoldsTheRulesOnAMadeUpBook() {
        MapBook book = madeUp(9, 6, 4);
        for (MapForm form : FORMS) {
            check(book, new LayoutEngine().lay(book, form), form + " made up");
        }
    }

    @Test
    void chaptersOffTheMapAreNotOnTheCanvas() {
        List<Chapter> chapters = List.of(chapter("t:a", 1).withOffMap(true), chapter("t:b", 1));
        List<Quest> quests = List.of(quest("t:a/q", "t:a", false), quest("t:b/q", "t:b", false), quest("t:b/r", "t:b", false, "t:b/q", "t:a/q"));
        MapBook book = MapBook.of(BookMeta.DEFAULT, chapters, quests, "off");
        assertEquals(1, book.chapters().size());
        assertEquals(2, book.quests().size());
        assertEquals(-1, book.quest(ResourceLocation.parse("t:a/q")));
        assertEquals(1, book.edges().size(), "the road from a chapter off the map is left out");
        check(book, new LayoutEngine().lay(book, MapForm.SERPENTINE), "off the map");
    }

    @Test
    void oneBookGivesOneLayoutAndTheBakedFileReadsBackTheSame() {
        MapBook book = madeUp(7, 5, 3);
        for (MapForm form : FORMS) {
            CanvasLayout a = new LayoutEngine().lay(book, form);
            CanvasLayout b = new LayoutEngine().lay(book, form);
            assertTrue(a.sameAs(b), form + ": two engines, one layout");
            BookLayout baked = a.toBaked(book);
            JsonElement json = BookLayout.CODEC.encodeStart(JsonOps.INSTANCE, baked).getOrThrow();
            BookLayout read = BookLayout.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            CanvasLayout back = CanvasLayout.fromBaked(book, read);
            assertNotNull(back, form + ": the file holds the whole canvas");
            assertTrue(a.sameAs(back), form + ": the baked file reads back as the same canvas");
        }
    }

    @Test
    void oneChangedChapterIsPackedAlone() {
        MapBook before = madeUp(9, 6, 4);
        LayoutEngine engine = new LayoutEngine();
        engine.lay(before, MapForm.GENERATOR);
        int full = engine.packedChapters.get();
        assertEquals(9, full, "every chapter packed once");
        // one more side quest in chapter 4
        MapBook grown = madeUpWithExtra(4);
        CanvasLayout again = engine.lay(grown, MapForm.GENERATOR);
        assertEquals(full + 1, engine.packedChapters.get(), "only the changed chapter is packed again");
        check(grown, again, "after one chapter changed");
        CanvasLayout fresh = new LayoutEngine().lay(grown, MapForm.GENERATOR);
        assertTrue(fresh.sameAs(again), "kept chapters give the same canvas as laying out from scratch");
    }

    private static MapBook madeUpWithExtra(int chapter) {
        MapBook base = madeUp(9, 6, 4);
        List<Chapter> chapters = new ArrayList<>();
        List<Quest> quests = new ArrayList<>();
        for (int c = 0; c < 9; c++) {
            chapters.add(chapter("t:c" + c, 1 + c / 3));
        }
        for (MapBook.Q q : base.quests()) {
            String ch = "t:c" + q.chapter();
            String[] req = java.util.Arrays.stream(q.requires()).mapToObj(r -> base.quests().get(r).id().toString()).toArray(String[]::new);
            quests.add(quest(q.id().toString(), ch, q.optional(), req));
        }
        quests.add(quest("t:c" + chapter + "/extra", "t:c" + chapter, true, "t:c" + chapter + "/m2"));
        return MapBook.of(BookMeta.DEFAULT, chapters, quests, "made-up-extra");
    }

    // --- real books ---

    private static Optional<Path> folder(String property) {
        String value = System.getProperty(property);
        return value == null || value.isBlank() || !Files.isDirectory(Path.of(value)) ? Optional.empty() : Optional.of(Path.of(value));
    }

    /** The RepYS book with its intro and chronicle off the map, as the book's data will have them. */
    static QuestBook repys() throws IOException {
        Optional<Path> folder = folder("rysquests.repysBook");
        assumeTrue(folder.isPresent(), "no RepYS book at " + System.getProperty("rysquests.repysBook"));
        Map<ResourceLocation, JsonElement> files = TestBooks.folder(folder.get(), "repys");
        files.values().forEach(CanvasLayoutTest::stripComponents);
        files.forEach((id, json) -> {
            if (id.getPath().endsWith("/_chapter") && json.isJsonObject() && json.getAsJsonObject().has("epoch") && json.getAsJsonObject().get("epoch").getAsInt() == 0) {
                json.getAsJsonObject().addProperty("off_map", true);
            }
        });
        return TestBooks.book(files);
    }

    static QuestBook atm10() throws IOException {
        Optional<Path> folder = folder("rysquests.atm10Quests");
        assumeTrue(folder.isPresent(), "no ATM10 FTB Quests book at " + System.getProperty("rysquests.atm10Quests"));
        FtbqImporter.Result result = FtbqImporter.convert(FtbqFiles.read(folder.get()), "atm", FtbqImporter.Ids.empty());
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        result.files().forEach((path, json) -> {
            stripComponents(json);
            String[] parts = path.split("/", 4);
            files.put(ResourceLocation.fromNamespaceAndPath(parts[1], parts[3].substring(0, parts[3].length() - ".json".length())), json);
        });
        // drafts would not be on the map; the importer's drafts are counted here as the author would have published them
        files.values().forEach(json -> {
            if (json.isJsonObject() && json.getAsJsonObject().has("draft")) {
                json.getAsJsonObject().remove("draft");
            }
        });
        return TestBooks.book(files);
    }

    @Test
    void repysInEveryForm() throws IOException {
        QuestBook book = repys();
        MapBook map = MapBook.of(book);
        assertTrue(map.chapters().size() >= 20, "story chapters on the map: " + map.chapters().size());
        assertEquals(Wheels.Style.OPTIONAL, Wheels.style(map), "RepYS marks its side quests");
        for (MapForm form : FORMS) {
            long start = System.nanoTime();
            CanvasLayout layout = new LayoutEngine().lay(map, form);
            long ms = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("RepYS %s: %d quests, %d chapters, %d ms, box %.0f x %.0f%n", form, map.quests().size(), map.chapters().size(), ms,
                    layout.maxX - layout.minX, layout.maxY - layout.minY);
            check(map, layout, "RepYS " + form);
        }
    }

    @Test
    void atm10InEveryFormAndInTime() throws IOException {
        QuestBook book = atm10();
        MapBook map = MapBook.of(book);
        assertTrue(map.quests().size() > 4000, "ATM10 quests on the map: " + map.quests().size());
        assertEquals(Wheels.Style.TREE, Wheels.style(map), "ATM10's optional flag does not divide the book");
        assertEquals(MapForm.GALAXY, MapForms.choose(book).form());
        for (MapForm form : FORMS) {
            long start = System.nanoTime();
            CanvasLayout layout = new LayoutEngine().lay(map, form);
            long ms = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("ATM10 %s: %d quests, %d chapters, %d ms, box %.0f x %.0f%n", form, map.quests().size(), map.chapters().size(), ms,
                    layout.maxX - layout.minX, layout.maxY - layout.minY);
            check(map, layout, "ATM10 " + form);
            if (form == MapForm.GALAXY) {
                assertTrue(ms < 15_000, "the galaxy of ATM10 lays out in " + ms + " ms");
            }
        }
    }

    // --- the rules ---

    static void check(MapBook book, CanvasLayout layout, String what) {
        assertEquals(book.quests().size(), layout.x.length, what + ": a place for every quest");
        assertEquals(book.chapters().size(), layout.regions.length, what);
        // every quest is placed, and inside its own chapter
        for (MapBook.Q quest : book.quests()) {
            float x = layout.x[quest.index()];
            float y = layout.y[quest.index()];
            assertTrue(Float.isFinite(x) && Float.isFinite(y), what + ": " + quest.id());
            CanvasLayout.Region region = layout.regions[quest.chapter()];
            assertTrue(inside(region, x, y, 3), what + ": " + quest.id() + " at " + x + ", " + y + " is outside its chapter " + region.kind());
        }
        // chapters do not overlap
        int n = layout.regions.length;
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                CanvasLayout.Region ra = layout.regions[a];
                CanvasLayout.Region rb = layout.regions[b];
                if (ra.maxX() < rb.minX() || rb.maxX() < ra.minX() || ra.maxY() < rb.minY() || rb.maxY() < ra.minY()) {
                    continue;
                }
                assertTrue(!Polygons.overlap(ra.polygon(), rb.polygon()), what + ": chapters " + book.chapters().get(a).id() + " and " + book.chapters().get(b).id() + " overlap");
            }
        }
        // a chapter's label stands in its chapter and is not wider than it
        for (MapBook.Chap chapter : book.chapters()) {
            CanvasLayout.Label label = layout.labels[chapter.index()];
            CanvasLayout.Region region = layout.regions[chapter.index()];
            assertTrue(inside(region, label.x(), label.y(), 1), what + ": the label of " + chapter.id() + " stands outside its chapter");
            assertTrue(label.maxWidth() > 0 && label.maxWidth() <= region.maxX() - region.minX() + 0.01f, what + ": the label of " + chapter.id() + " is wider than its chapter");
        }
    }

    private static boolean inside(CanvasLayout.Region region, float x, float y, float slack) {
        if (region.contains(x, y)) {
            return true;
        }
        return switch (region.kind()) {
            case RECT -> x >= region.minX() - slack && x <= region.maxX() + slack && y >= region.minY() - slack && y <= region.maxY() + slack;
            case DISC -> Math.hypot(x - region.pts()[0], y - region.pts()[1]) <= region.pts()[2] + slack;
            case BAND -> region.distanceToLine(x, y) <= region.width() / 2 + slack;
        };
    }

    /** Component types of absent mods cannot be parsed here; in the real game they resolve. */
    private static void stripComponents(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            object.remove("components");
            object.remove("nbt");
            object.entrySet().forEach(entry -> stripComponents(entry.getValue()));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(CanvasLayoutTest::stripComponents);
        }
    }
}
