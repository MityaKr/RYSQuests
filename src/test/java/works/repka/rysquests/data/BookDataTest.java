package works.repka.rysquests.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.client.markup.GuideTopics;

class BookDataTest {
    private static final Path EXAMPLES = Path.of(System.getProperty("rysquests.examplesDir"));

    private static Map<ResourceLocation, JsonElement> load(Path pack) throws IOException {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        Path data = pack.resolve("data");
        try (Stream<Path> paths = Files.walk(data)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                Path relative = data.relativize(file);
                String namespace = relative.getName(0).toString();
                String path = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
                files.put(ResourceLocation.fromNamespaceAndPath(namespace, path.substring(0, path.length() - ".json".length())),
                        JsonParser.parseString(Files.readString(file)));
            }
        }
        return files;
    }

    private static Map<ResourceLocation, JsonElement> example() throws IOException {
        return load(EXAMPLES.resolve("example_pack"));
    }

    private static BookValidator.Result validate(Map<ResourceLocation, JsonElement> files) {
        return BookValidator.validate(BookParser.parse(files, JsonOps.INSTANCE), BuiltInRegistries.ITEM::containsKey, JsonOps.INSTANCE);
    }

    private static Set<String> codes(BookValidator.Result result) {
        return result.report().issues().stream().map(BookReport.Issue::code).collect(Collectors.toSet());
    }

    private static JsonElement quest(String json) {
        return JsonParser.parseString(json);
    }

    private static ResourceLocation questFile(String name) {
        return ResourceLocation.fromNamespaceAndPath("t", "chapters/c/" + name);
    }

    private static Map<ResourceLocation, JsonElement> singleChapter() {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(questFile("_chapter"), quest("{\"id\":\"t:c\",\"title\":\"C\"}"));
        return files;
    }

    private static <A> void assertRoundTrips(Codec<A> codec, A value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, codec.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void exampleBookIsClean() throws IOException {
        BookValidator.Result result = validate(example());

        assertEquals(List.of(), result.report().issues());
        assertEquals(14, result.book().quests().size());
        assertEquals(List.of(ResourceLocation.parse("example:01_start"), ResourceLocation.parse("example:02_power"), ResourceLocation.parse("example:_achievements"), ResourceLocation.parse("example:_cards/bosses")),
                List.copyOf(result.book().chapters().keySet()));
    }

    @Test
    void everyExampleEntryRoundTrips() throws IOException {
        RawBook raw = BookParser.parse(example(), JsonOps.INSTANCE);

        assertRoundTrips(BookMeta.CODEC, raw.meta());
        raw.chapters().forEach(chapter -> assertRoundTrips(Chapter.CODEC, chapter));
        raw.quests().forEach(quest -> assertRoundTrips(Quest.CODEC, quest));
    }

    @Test
    void indexesLinkQuests() throws IOException {
        QuestBook book = validate(example()).book();

        ResourceLocation tools = ResourceLocation.parse("example:start/first_tools");
        assertEquals(2, book.unlocks().get(tools).size());
        assertTrue(book.byItem().get(ResourceLocation.parse("minecraft:furnace")).contains(ResourceLocation.parse("example:start/first_furnace")));
    }

    @Test
    void brokenBookReportsEveryProblemAndLoadsNothingBroken() throws IOException {
        BookValidator.Result result = validate(load(EXAMPLES.resolve("broken")));

        assertEquals(3, result.report().errors());
        assertEquals(Set.of("dependency_cycle", "missing_dependency", "unknown_item"), codes(result));
        assertEquals(Map.of(), result.book().quests());
    }

    @Test
    void repeatableQuestsThatFinishByThemselvesAreReported() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(questFile("stat"), quest("{\"id\":\"t:stat\",\"title\":\"S\",\"repeatable\":{\"cooldown_minutes\":0},"
                + "\"tasks\":[{\"type\":\"rysquests:stat\",\"stat\":\"minecraft:custom\",\"target\":\"minecraft:jump\"}]}"));
        files.put(questFile("pay"), quest("{\"id\":\"t:pay\",\"title\":\"P\",\"repeatable\":{\"cooldown_minutes\":0},"
                + "\"tasks\":[{\"type\":\"rysquests:xp\",\"levels\":2,\"consume\":true}]}"));

        BookValidator.Result result = validate(files);

        assertEquals(1, result.report().issues().stream().filter(issue -> issue.code().equals("repeatable_passive")).count());
        assertEquals(2, result.book().quests().size());
    }

    @Test
    void aCraftedFluidAndALinkToNothingAreReported() {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(questFile("_chapter"), quest("{\"id\":\"t:c\",\"title\":\"C\",\"links\":[\"t:gone\"]}"));
        files.put(questFile("fluid"), quest("{\"id\":\"t:fluid\",\"title\":\"F\",\"tasks\":[{\"type\":\"rysquests:fluid\",\"fluid\":\"minecraft:water\",\"amount\":1000,\"mode\":\"craft\"}]}"));

        BookValidator.Result result = validate(files);

        assertTrue(codes(result).containsAll(Set.of("craft_fluid", "unknown_link")));
        assertTrue(result.book().quests().isEmpty());
    }

    @Test
    void accumulateIsReportedAndAChapterReachingOutsideItsRequiresToo() {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.fromNamespaceAndPath("t", "chapters/a/_chapter"), quest("{\"id\":\"t:a\",\"title\":\"A\"}"));
        files.put(ResourceLocation.fromNamespaceAndPath("t", "chapters/b/_chapter"), quest("{\"id\":\"t:b\",\"title\":\"B\",\"requires\":[\"t:a\"]}"));
        files.put(ResourceLocation.fromNamespaceAndPath("t", "chapters/c/_chapter"), quest("{\"id\":\"t:c\",\"title\":\"C\",\"requires\":[\"t:b\"]}"));
        files.put(ResourceLocation.fromNamespaceAndPath("t", "chapters/d/_chapter"), quest("{\"id\":\"t:d\",\"title\":\"D\"}"));
        files.put(ResourceLocation.fromNamespaceAndPath("t", "chapters/a/qa"), quest("{\"id\":\"t:qa\",\"title\":\"QA\"}"));
        files.put(ResourceLocation.fromNamespaceAndPath("t", "chapters/d/qd"), quest("{\"id\":\"t:qd\",\"title\":\"QD\"}"));
        // C reaches A through B: fine. C reaching D is outside its chain.
        files.put(ResourceLocation.fromNamespaceAndPath("t", "chapters/c/qc"), quest("{\"id\":\"t:qc\",\"title\":\"QC\",\"requires\":[\"t:qa\",\"t:qd\"],"
                + "\"tasks\":[{\"type\":\"rysquests:item\",\"item\":\"minecraft:stone\",\"mode\":\"accumulate\"}]}"));

        BookValidator.Result result = validate(files);

        assertEquals(1, result.report().issues().stream().filter(issue -> issue.code().equals("chapter_order")).count());
        assertTrue(codes(result).contains("accumulate_as_observe"));
        assertEquals(3, result.book().quests().size());
    }

    @Test
    void oneBadFileDoesNotStopTheOthers() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(questFile("good"), quest("{\"id\":\"t:good\",\"title\":\"G\"}"));
        files.put(questFile("bad"), quest("{\"id\":\"t:bad\"}"));

        BookValidator.Result result = validate(files);

        assertEquals(Set.of("parse_error"), codes(result));
        assertEquals(Set.of(ResourceLocation.parse("t:good")), result.book().quests().keySet());
    }

    @Test
    void questOnBrokenDependencyIsLeftOutWithWarning() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(questFile("a"), quest("{\"id\":\"t:a\",\"title\":\"A\",\"requires\":[\"t:gone\"]}"));
        files.put(questFile("b"), quest("{\"id\":\"t:b\",\"title\":\"B\",\"requires\":[\"t:a\"]}"));

        BookValidator.Result result = validate(files);

        assertEquals(Set.of("missing_dependency", "broken_dependency"), codes(result));
        assertEquals(1, result.report().warnings());
        assertTrue(result.book().quests().isEmpty());
    }

    @Test
    void longChainDoesNotOverflowTheStack() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        int length = 50_000;
        files.put(questFile("q0"), quest("{\"id\":\"t:q0\",\"title\":\"Q\"}"));
        for (int i = 1; i < length; i++) {
            files.put(questFile("q" + i), quest("{\"id\":\"t:q" + i + "\",\"title\":\"Q\",\"requires\":[\"t:q" + (i - 1) + "\"]}"));
        }

        assertEquals(length, validate(files).book().quests().size());
    }

    @Test
    void itemTaskNeedsExactlyOneTarget() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(questFile("both"), quest("{\"id\":\"t:both\",\"title\":\"B\",\"tasks\":[{\"type\":\"rysquests:item\",\"item\":\"minecraft:stone\",\"any_of\":[\"minecraft:dirt\"]}]}"));
        files.put(questFile("none"), quest("{\"id\":\"t:none\",\"title\":\"N\",\"tasks\":[{\"type\":\"rysquests:item\"}]}"));

        BookValidator.Result result = validate(files);

        assertEquals(2, result.report().errors());
        assertTrue(result.book().quests().isEmpty());
    }

    @Test
    void requiresModeAtLeastRoundTrips() {
        assertRoundTrips(RequiresMode.CODEC, new RequiresMode.AtLeast(2));
        assertRoundTrips(RequiresMode.CODEC, RequiresMode.ANY);
    }

    @Test
    void hashIgnoresFieldOrderButNotContent() throws IOException {
        Map<ResourceLocation, JsonElement> files = example();
        String original = validate(files).book().hash();

        Map<ResourceLocation, JsonElement> reordered = new HashMap<>();
        files.forEach((id, json) -> reordered.put(id, reverseKeys(json)));
        assertEquals(original, validate(reordered).book().hash());

        Map<ResourceLocation, JsonElement> changed = new HashMap<>(files);
        ResourceLocation wires = ResourceLocation.parse("example:chapters/02_power/wires");
        JsonObject edited = changed.get(wires).deepCopy().getAsJsonObject();
        edited.addProperty("title", "something else");
        changed.put(wires, edited);
        assertNotEquals(original, validate(changed).book().hash());
    }

    private static JsonElement reverseKeys(JsonElement element) {
        if (!element.isJsonObject()) {
            return element;
        }
        List<String> keys = new ArrayList<>(element.getAsJsonObject().keySet());
        Collections.reverse(keys);
        JsonObject reversed = new JsonObject();
        keys.forEach(key -> reversed.add(key, reverseKeys(element.getAsJsonObject().get(key))));
        return reversed;
    }

    @Test
    void wireFormatRoundTrips() throws IOException {
        QuestBook book = validate(example()).book();
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        byte[] data = BookSerializer.encode(book, registries);
        QuestBook decoded = BookSerializer.decode(data, book.hash(), registries);

        assertFalse(data.length == 0);
        assertEquals(book.quests(), decoded.quests());
        assertEquals(book.chapters(), decoded.chapters());
        assertEquals(book.meta(), decoded.meta());
        assertEquals(book.unlocks(), decoded.unlocks());
    }

    // --- guides ---

    private static ResourceLocation guideFile(String name) {
        return ResourceLocation.fromNamespaceAndPath("t", "guides/" + name);
    }

    private static JsonElement guide(String id, String pages) {
        return quest("{\"id\":\"" + id + "\",\"title\":\"G\",\"pages\":" + pages + "}");
    }

    private static final String ONE_PAGE = "[{\"id\":\"p\",\"title\":\"T\",\"text\":\"x\"}]";

    @Test
    void exampleGuidesLoadAndTravelToTheClient() throws IOException {
        QuestBook book = validate(example()).book();

        Guide furnace = book.guides().get(ResourceLocation.parse("example:furnace"));
        assertEquals(3, furnace.pages().size());
        assertEquals(1, furnace.pageIndex("build"));
        assertRoundTrips(Guide.CODEC, furnace);
        assertEquals(List.of("story", "guides", "example:ores", "example:bosses", "example:rules", "shop", "achievements"), book.meta().sections());
        QuestBook decoded = QuestBook.fromWire(book.wire(), book.hash());
        assertEquals(book.guides(), decoded.guides());
        assertEquals("example:furnace#fuel", book.quests().get(ResourceLocation.parse("example:start/first_furnace")).guide().orElseThrow().toString());
    }

    @Test
    void guideLinkKeepsItsPage() {
        GuideRef page = GuideRef.parse("a:b#c").orElseThrow();
        assertEquals(ResourceLocation.parse("a:b"), page.guide());
        assertEquals("c", page.page().orElseThrow());
        assertTrue(GuideRef.parse("a:b").orElseThrow().page().isEmpty());
        assertTrue(GuideRef.parse("a:b#").isEmpty());
        assertRoundTrips(GuideRef.CODEC, page);
    }

    @Test
    void questWithUnknownGuideStaysAndIsReported() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(questFile("a"), quest("{\"id\":\"t:a\",\"title\":\"A\",\"guide\":\"t:none\"}"));

        BookValidator.Result result = validate(files);

        assertTrue(codes(result).contains("unknown_guide"));
        assertTrue(result.book().quests().containsKey(ResourceLocation.parse("t:a")));
    }

    @Test
    void questWithUnknownGuidePageIsReported() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(guideFile("g"), guide("t:g", ONE_PAGE));
        files.put(questFile("a"), quest("{\"id\":\"t:a\",\"title\":\"A\",\"guide\":\"t:g#other\"}"));

        assertTrue(codes(validate(files)).contains("unknown_guide_page"));
    }

    @Test
    void guideWithoutPagesOrWithRepeatedPagesIsLeftOut() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(guideFile("empty"), guide("t:empty", "[]"));
        files.put(guideFile("twice"), guide("t:twice", "[{\"id\":\"p\",\"title\":\"T\",\"text\":\"x\"},{\"id\":\"p\",\"title\":\"T\",\"text\":\"y\"}]"));
        files.put(guideFile("fine"), guide("t:fine", ONE_PAGE));

        BookValidator.Result result = validate(files);

        assertTrue(codes(result).containsAll(Set.of("empty_guide", "duplicate_guide_page")));
        assertEquals(Set.of(ResourceLocation.parse("t:fine")), result.book().guides().keySet());
    }

    @Test
    void unknownSectionIsReported() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(ResourceLocation.fromNamespaceAndPath("t", "book"), quest("{\"sections\":[\"story\",\"wat\"]}"));

        assertTrue(codes(validate(files)).contains("unknown_section"));
    }

    @Test
    void topicsGatherFromThePageClosestFirst() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(guideFile("g"), guide("t:g", ONE_PAGE));
        files.put(questFile("whole"), quest("{\"id\":\"t:whole\",\"title\":\"A\",\"guide\":\"t:g\"}"));
        files.put(questFile("exact"), quest("{\"id\":\"t:exact\",\"title\":\"A\",\"guide\":\"t:g#p\"}"));
        files.put(questFile("named"), quest("{\"id\":\"t:named\",\"title\":\"A\"}"));
        files.put(questFile("item"), quest("{\"id\":\"t:item\",\"title\":\"A\",\"tasks\":[{\"type\":\"rysquests:item\",\"item\":\"minecraft:stone\",\"count\":1}]}"));
        files.put(questFile("other"), quest("{\"id\":\"t:other\",\"title\":\"A\"}"));
        QuestBook book = validate(files).book();
        Guide guide = book.guides().get(ResourceLocation.parse("t:g"));
        String text = "[[recipe:minecraft:stone]] and [[item:minecraft:dirt]], again [[item:minecraft:stone|stone]]. See [[quest:t:named]].";

        assertEquals(List.of(ResourceLocation.parse("minecraft:stone"), ResourceLocation.parse("minecraft:dirt")), GuideTopics.items(text));
        assertEquals(List.of("t:exact", "t:whole", "t:named", "t:item"),
                GuideTopics.quests(book, guide, guide.pages().getFirst(), text).stream().map(found -> found.id().toString()).toList());
    }

    // --- custom sections ---

    private static ResourceLocation sectionFile(String name) {
        return ResourceLocation.fromNamespaceAndPath("t", "sections/" + name);
    }

    @Test
    void exampleSectionsLoadWithIdsFromTheirFiles() throws IOException {
        BookValidator.Result result = validate(example());

        assertEquals(Set.of(), codes(result));
        assertEquals(Set.of(ResourceLocation.parse("example:ores"), ResourceLocation.parse("example:bosses"), ResourceLocation.parse("example:rules")), result.book().sections().keySet());
        result.book().sections().values().forEach(section -> assertRoundTrips(SectionDef.CODEC, section));
        QuestBook decoded = QuestBook.fromWire(result.book().wire(), result.book().hash());
        assertEquals(result.book().sections(), decoded.sections());
    }

    @Test
    void aWayIsFoundByItsPinKey() throws IOException {
        QuestBook book = validate(example()).book();
        MatrixSection ores = (MatrixSection) book.sections().get(ResourceLocation.parse("example:ores"));
        MatrixSection.Row iron = ores.rows().getFirst();
        MatrixSection.Column middle = ores.columns().get(1);
        String key = ores.pinKey(iron, middle, iron.ways(middle).getFirst());

        assertEquals("furnace", MatrixSection.findPinned(book, key).orElseThrow().way().id());
        assertTrue(MatrixSection.findPinned(book, "example:ores#iron#mid#gone").isEmpty());
        assertTrue(MatrixSection.findPinned(book, "nonsense").isEmpty());
    }

    @Test
    void cardConditionBecomesAHiddenQuestOutsideTheStory() throws IOException {
        QuestBook book = validate(example()).book();
        Quest condition = book.quests().get(ResourceLocation.parse("example:_cards/bosses/first_stone"));

        assertTrue(condition.options().hidden());
        assertFalse(book.chapters().get(condition.chapter().orElseThrow()).isStory());
        assertTrue(book.quests().keySet().stream().noneMatch(id -> id.getPath().endsWith("/wither")));
    }

    @Test
    void unknownTemplateIsAParseError() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(sectionFile("x"), quest("{\"template\":\"nope\",\"title\":\"T\"}"));

        BookValidator.Result result = validate(files);

        assertTrue(codes(result).contains("parse_error"));
        assertTrue(result.book().sections().isEmpty());
    }

    @Test
    void brokenSectionsAreLeftOutAndBrokenLinksOnlyReported() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(sectionFile("empty"), quest("{\"template\":\"cards\",\"title\":\"T\",\"cards\":[]}"));
        files.put(sectionFile("twice"), quest("{\"template\":\"pages\",\"title\":\"T\",\"pages\":[{\"id\":\"a\",\"title\":\"T\",\"text\":\"x\"},{\"id\":\"a\",\"title\":\"T\",\"text\":\"y\"}]}"));
        files.put(sectionFile("links"), quest("{\"template\":\"matrix\",\"title\":\"T\",\"columns\":[{\"id\":\"c\",\"title\":\"C\"}],\"rows\":[{\"id\":\"r\",\"cells\":{\"c\":[{\"id\":\"w\",\"links\":[{\"type\":\"quest\",\"id\":\"t:none\"}]}],\"other\":[]}}]}"));

        BookValidator.Result result = validate(files);

        assertTrue(codes(result).containsAll(Set.of("empty_section", "duplicate_section_id", "unknown_section_link", "unknown_section_column")));
        assertEquals(Set.of(ResourceLocation.parse("t:links")), result.book().sections().keySet());
    }

    @Test
    void cardMayLinkToASectionOfPages() {
        Map<ResourceLocation, JsonElement> files = singleChapter();
        files.put(sectionFile("book"), quest("{\"template\":\"pages\",\"title\":\"T\",\"pages\":[{\"id\":\"a\",\"title\":\"T\",\"text\":\"x\"}]}"));
        files.put(sectionFile("cards"), quest("{\"template\":\"cards\",\"title\":\"T\",\"cards\":[{\"id\":\"c\",\"page\":\"t:book#a\"},{\"id\":\"d\",\"page\":\"t:book#missing\",\"art\":\"red\"}]}"));

        BookValidator.Result result = validate(files);

        assertEquals(1, result.report().issues().stream().filter(issue -> issue.code().equals("unknown_section_link")).count());
        assertTrue(codes(result).contains("bad_section_color"));
    }
}
