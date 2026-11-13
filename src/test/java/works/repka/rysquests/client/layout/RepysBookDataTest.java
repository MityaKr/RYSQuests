package works.repka.rysquests.client.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import works.repka.rysquests.data.BookLayout;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.BookParser;
import works.repka.rysquests.data.BookReport;
import works.repka.rysquests.data.BookValidator;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.TestBooks;
import works.repka.rysquests.editor.EditorFiles;

/**
 * The RepYS book made ready for the new book (plan, step 13), read from a copy of its {@code rysquests} folder given by
 * {@code -PrepysData}: it reads without errors and without warnings the instance's book does not have, keeps every chapter and
 * quest (the content pass may add quests and send some to the archive, never drop one), names its nine epochs, marks every old
 * milestone with the field of their own (and may add more), sets its intro and chronicle off the map, takes the spiral its author
 * set and carries a baked {@code layout.json} that the server takes as fresh.
 *
 * <p>With {@code -PbakeLayout} it first lays the copy out with the engine the client uses and writes that {@code layout.json} beside
 * its {@code book.json}, as an operator's "Save layout" would: {@code gradlew test --tests '*RepysBookDataTest' -PrepysData=<folder>
 * -PbakeLayout}. The instance's own book, {@code -PrepysPack}, is only read, to compare with.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepysBookDataTest {
    private static final String NS = "repys";
    private static final int EPOCHS = 9;
    private static final Pattern EPOCH_IN_ID = Pattern.compile("^b\\d+_e(\\d)_");

    private static Optional<Path> folder(String property) {
        String value = System.getProperty(property);
        return value == null || value.isBlank() || !Files.isDirectory(Path.of(value)) ? Optional.empty() : Optional.of(Path.of(value));
    }

    private static Path copy() {
        Optional<Path> folder = folder("rysquests.repysData");
        assumeTrue(folder.isPresent(), "no copy of the RepYS book at " + System.getProperty("rysquests.repysData"));
        return folder.get();
    }

    private static Path instance() {
        Optional<Path> folder = folder("rysquests.repysPack");
        assumeTrue(folder.isPresent(), "no RepYS book at " + System.getProperty("rysquests.repysPack"));
        return folder.get();
    }

    /** Every JSON file of the folder. Item components name types of mods absent here; they never reach the map or its hash. */
    private static Map<ResourceLocation, JsonElement> files(Path folder) throws IOException {
        Map<ResourceLocation, JsonElement> files = TestBooks.folder(folder, NS);
        files.values().forEach(RepysBookDataTest::stripComponents);
        return files;
    }

    /** Linked as the server links the book, every item taken to exist, since the pack's mods are absent here. */
    private static BookValidator.Result read(Path folder) throws IOException {
        return BookValidator.validate(BookParser.parse(files(folder), JsonOps.INSTANCE), id -> true, JsonOps.INSTANCE);
    }

    @Test
    @Order(1)
    void bakeLayoutWhenAsked() throws IOException {
        assumeTrue(Boolean.getBoolean("rysquests.bakeLayout"), "baking is asked with -PbakeLayout");
        Path folder = copy();
        QuestBook book = read(folder).book();
        MapForm form = MapForms.choose(book).form();
        MapBook map = MapBook.of(book);
        long start = System.nanoTime();
        CanvasLayout layout = new LayoutEngine().lay(map, form);
        BookLayout baked = layout.toBaked(map);
        Files.writeString(folder.resolve("layout.json"), EditorFiles.layoutText(baked), StandardCharsets.UTF_8);
        System.out.printf("baked %s: %d quests in %d chapters, hash %s, %d ms%n", form, map.quests().size(), map.chapters().size(), baked.hash(),
                (System.nanoTime() - start) / 1_000_000);
    }

    @Test
    @Order(2)
    void readsWithoutErrorsOrNewWarnings() throws IOException {
        BookReport before = read(instance()).report();
        BookReport after = read(copy()).report();
        List<BookReport.Issue> errors = after.issues().stream().filter(issue -> issue.severity() == BookReport.Severity.ERROR).toList();
        assertTrue(errors.isEmpty(), "errors: " + errors);
        Set<String> old = before.issues().stream().map(BookReport.Issue::toString).collect(Collectors.toSet());
        List<String> added = after.issues().stream().map(BookReport.Issue::toString).filter(issue -> !old.contains(issue)).toList();
        assertTrue(added.isEmpty(), "new warnings: " + added);
        Set<String> now = after.issues().stream().map(BookReport.Issue::toString).collect(Collectors.toSet());
        System.out.printf("warnings: %d before, %d after %s; gone: %s%n", before.issues().size(), after.issues().size(),
                new java.util.TreeMap<>(after.issues().stream().collect(Collectors.groupingBy(BookReport.Issue::code, Collectors.counting()))),
                old.stream().filter(issue -> !now.contains(issue)).sorted().toList());
    }

    @Test
    @Order(3)
    void keepsEveryChapterAndQuestAndOnlyTheServiceChaptersLeaveTheMap() throws IOException {
        QuestBook before = read(instance()).book();
        QuestBook after = read(copy()).book();
        assertEquals(before.chapters().keySet(), after.chapters().keySet(), "the same chapters");
        // Players keep progress by id: no quest may go, a quest no longer needed goes to the archive instead.
        Set<ResourceLocation> gone = before.quests().keySet().stream().filter(id -> !after.quests().containsKey(id)).collect(Collectors.toSet());
        assertTrue(gone.isEmpty(), "quests that went: " + gone);
        List<ResourceLocation> added = after.quests().keySet().stream().filter(id -> !before.quests().containsKey(id)).sorted().toList();
        List<ResourceLocation> archived = after.quests().values().stream().filter(q -> q.options().archived() && !before.quests().get(q.id()).options().archived())
                .map(Quest::id).sorted().toList();
        assertEquals(before.quests().values().stream().filter(q -> q.options().live()).count() + added.size() - archived.size(),
                after.quests().values().stream().filter(q -> q.options().live()).count(), "live quests: the old ones, plus the new, less the archived");
        System.out.printf("quests added: %d %s; archived: %d %s%n", added.size(), added, archived.size(), archived);
        MapBook was = MapBook.of(before);
        MapBook now = MapBook.of(after);
        Set<ResourceLocation> onMapNow = now.quests().stream().map(MapBook.Q::id).collect(Collectors.toSet());
        List<ResourceLocation> leftTheMap = was.quests().stream().map(MapBook.Q::id).filter(id -> !onMapNow.contains(id) && !archived.contains(id)).toList();
        assertTrue(leftTheMap.isEmpty(), "quests that left the map without going to the archive: " + leftTheMap);
        Set<ResourceLocation> off = after.chapters().values().stream().filter(Chapter::offMap).map(Chapter::id).collect(Collectors.toSet());
        assertEquals(Set.of(id("a01_map"), id("a2_chronicle")), off, "the intro and the chronicle are off the map");
        assertTrue(off.stream().allMatch(c -> after.chapters().get(c).epoch().orElse(0) == 0), "only chapters without an epoch leave the map");
        long offQuests = after.quests().values().stream().filter(q -> q.options().live() && q.chapter().filter(off::contains).isPresent()).count();
        // The instance's book keeps them in epoch 0, which already reads as no epoch (ADR-51): the same map either way.
        assertEquals(was.chapters().size(), now.chapters().size(), "chapters on the map");
        assertEquals(was.quests().size() + added.size() - archived.size(), now.quests().size(), "quests on the map");
        assertTrue(now.chapters().stream().noneMatch(chapter -> off.contains(chapter.id())), "neither is on the map");
        System.out.printf("on the map: %d chapters, %d quests before; %d chapters, %d quests after (%d quests off the map); %d chapters, %d quests in all%n",
                was.chapters().size(), was.quests().size(), now.chapters().size(), now.quests().size(), offQuests, after.chapters().size(), after.quests().size());
    }

    @Test
    @Order(4)
    void namesNineEpochsThatMatchTheChapters() throws IOException {
        QuestBook book = read(copy()).book();
        List<BookMeta.Epoch> epochs = book.meta().epochs();
        assertEquals(EPOCHS, epochs.size());
        for (int i = 0; i < EPOCHS; i++) {
            assertEquals("book.repys.epoch." + (i + 1), epochs.get(i).title());
            assertFalse(epochs.get(i).imported());
        }
        for (MapBook.Chap chapter : MapBook.of(book).chapters()) {
            Matcher m = EPOCH_IN_ID.matcher(chapter.id().getPath());
            assertTrue(m.find(), chapter.id() + " is a chapter of an epoch");
            assertEquals(Integer.parseInt(m.group(1)), chapter.epoch(), chapter.id() + " stands in the epoch its id names");
        }
    }

    @Test
    @Order(5)
    void marksEveryOldMilestoneWithItsOwnField() throws IOException {
        Set<ResourceLocation> before = milestones(read(instance()).book());
        Set<ResourceLocation> after = milestones(read(copy()).book());
        assertTrue(after.containsAll(before), "every old size 2 is a milestone now; lost: " + before.stream().filter(id -> !after.contains(id)).toList());
        System.out.printf("milestones added: %s%n", after.stream().filter(id -> !before.contains(id)).sorted().toList());
        Set<String> sized = new HashSet<>();
        files(copy()).forEach((id, json) -> {
            if (json.isJsonObject() && json.getAsJsonObject().has("size") && json.getAsJsonObject().get("size").getAsInt() >= 2) {
                sized.add(id.toString());
            }
        });
        assertTrue(sized.isEmpty(), "still size 2: " + sized);
        System.out.printf("milestones: %d%n", after.size());
    }

    private static Set<ResourceLocation> milestones(QuestBook book) {
        return book.quests().values().stream().filter(q -> q.options().milestone()).map(Quest::id).collect(Collectors.toSet());
    }

    @Test
    @Order(6)
    void theSteelMilestoneLinksToItsOwnGuide() throws IOException {
        QuestBook book = read(copy()).book();
        for (String quest : List.of("b02_e1_steel/pressed_steel_plate", "b02_e1_steel/automation_target_64_pressed_steel_plate")) {
            assertEquals("repys:create#s1", book.quests().get(id(quest)).guide().orElseThrow().toString(), quest);
        }
    }

    @Test
    @Order(7)
    void takesTheSpiralItsAuthorSet() throws IOException {
        QuestBook book = read(copy()).book();
        // The author's choice: RepYS is a spiral. Left to the rule, its epochs would make it a serpentine.
        assertEquals(MapForm.SPIRAL, book.meta().mapForm(), "book.json sets the form");
        MapForms.Choice choice = MapForms.choose(book);
        assertEquals(MapForm.SPIRAL, choice.form());
        assertEquals(MapForms.Reason.SET_BY_AUTHOR, choice.reason());
        assertEquals(MapForm.SERPENTINE, MapForms.auto(book.meta(), List.copyOf(book.chapters().values()), List.copyOf(book.quests().values())).form());
        System.out.printf("form: %s by %s, %d of %d chapters with an epoch%n", choice.form(), choice.reason(), choice.chaptersWithEpoch(), choice.chapters());
    }

    @Test
    @Order(8)
    void carriesAFreshBakedLayout() throws IOException {
        BookValidator.Result result = read(copy());
        QuestBook book = result.book();
        assertTrue(book.layout().isPresent(), "layout.json beside book.json");
        BookLayout baked = book.layout().get();
        assertEquals(book.layoutHash(), baked.hash(), "the hash the server works out");
        assertTrue(book.freshLayout().isPresent());
        assertTrue(result.report().issues().stream().noneMatch(issue -> issue.code().equals("layout_stale")));
        assertEquals(MapForm.SPIRAL, baked.form());
        MapBook map = MapBook.of(book);
        CanvasLayout read = CanvasLayout.fromBaked(map, baked);
        assertNotNull(read, "the file holds the whole canvas");
        CanvasLayoutTest.check(map, read, "RepYS baked");
        assertTrue(new LayoutEngine().lay(map, MapForm.SPIRAL).sameAs(read), "the engine lays the book out as the file has it");
        assertEquals(map.quests().size(), baked.nodes().size(), "a node for every quest on the map");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NS, path);
    }

    private static void stripComponents(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            object.remove("components");
            object.remove("nbt");
            object.entrySet().forEach(entry -> stripComponents(entry.getValue()));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(RepysBookDataTest::stripComponents);
        }
    }
}
