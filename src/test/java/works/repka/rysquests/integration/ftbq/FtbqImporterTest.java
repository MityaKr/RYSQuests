package works.repka.rysquests.integration.ftbq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.BookParser;
import works.repka.rysquests.data.BookReport;
import works.repka.rysquests.data.BookValidator;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Ids;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Result;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Source;

class FtbqImporterTest {
    private static final String CHAPTER = """
            {
            \tfilename: "b01_first"
            \tgroup: ""
            \tid: "4C3F7CDE181CAFB5"
            \torder_index: 0
            \tquest_links: [ ]
            \tquests: [
            \t\t{
            \t\t\tid: "73F8F7A274272D6D"
            \t\t\ticon: { id: "minecraft:stone" }
            \t\t\trewards: [{ id: "47AD93D250742569", type: "xp", xp: 10 }]
            \t\t\tsize: 2.0d
            \t\t\ttasks: [{ id: "7BA02683DFEFB03A", type: "checkmark" }]
            \t\t\tx: 0.0d
            \t\t}
            \t\t{
            \t\t\tdependencies: ["73F8F7A274272D6D"]
            \t\t\tdependency_requirement: "one_completed"
            \t\t\tid: "41239A05E60B07FC"
            \t\t\trewards: [
            \t\t\t\t{ count: 4, id: "511827D95771F51D", item: { count: 1, id: "minecraft:dirt" }, type: "item" }
            \t\t\t\t{ id: "50618E257FF4545E", table_id: 1433225472L, type: "random" }
            \t\t\t]
            \t\t\ttasks: [
            \t\t\t\t{ consume_items: true, count: 16L, id: "2F13349E22338404", item: { count: 1, id: "minecraft:stone" }, type: "item" }
            \t\t\t\t{ entity: "minecraft:zombie", id: "27ABC9480815614E", type: "kill", value: 5L }
            \t\t\t\t{ id: "4FF1863AA4E67346", structure: "#minecraft:village", type: "structure" }
            \t\t\t\t{ id: "75B09A194E73C8D1", stat: "minecraft:walk_one_cm", type: "stat", value: 100 }
            \t\t\t]
            \t\t}
            \t]
            }
            """;

    private static final String LANG_EN = """
            {
            \t"chapter.4C3F7CDE181CAFB5.title": "First"
            \t"quest.73F8F7A274272D6D.title": "&aHello World"
            \t"quest.73F8F7A274272D6D.quest_desc": ["Line one", "Line two"]
            \t"quest.41239A05E60B07FC.title": "Hello World"
            }
            """;

    private static final String LANG_RU = """
            {
            \t"quest.73F8F7A274272D6D.title": "Привет"
            }
            """;

    private static final String TABLE = """
            { id: "556D7C00", rewards: [ { item: { count: 1, id: "minecraft:gold_ingot" }, weight: 3 }, { item: { count: 1, id: "minecraft:coal" } } ] }
            """;

    /** FTB Quests' flexible progression becomes early completion (ADR-66): file, chapter and quest, default takes the parent's. */
    @Test
    void progressionModeBecomesEarlyCompletion() {
        String chapter = CHAPTER.replace("quest_links: [ ]", "progression_mode: \"linear\"\nquest_links: [ ]")
                .replace("dependency_requirement: \"one_completed\"", "dependency_requirement: \"one_completed\"\nprogression_mode: \"flexible\"");
        Source source = fixture();
        JsonObject data = new JsonObject();
        data.addProperty("progression_mode", "flexible");
        Result result = FtbqImporter.convert(new Source(data, source.groups(), List.of(parse(chapter)), source.tables(), source.lang()), "pack", Ids.empty());
        JsonObject book = result.files().get("data/pack/rysquests/book.json");
        assertTrue(book.get("pre_complete").getAsBoolean(), "the file's flexible is the book's default");
        JsonObject chapterFile = result.files().entrySet().stream().filter(e -> e.getKey().endsWith("_chapter.json")).findFirst().orElseThrow().getValue();
        assertEquals(false, chapterFile.get("pre_complete").getAsBoolean(), "the chapter's linear overrides it");
        List<JsonObject> quests = result.files().entrySet().stream().filter(e -> e.getKey().contains("/chapters/") && !e.getKey().endsWith("_chapter.json"))
                .map(Map.Entry::getValue).toList();
        assertEquals(1, quests.stream().filter(q -> q.has("pre_complete") && q.get("pre_complete").getAsBoolean()).count(), "the flexible quest");
        assertEquals(1, quests.stream().filter(q -> !q.has("pre_complete")).count(), "the default quest takes the chapter's");

        Result plain = FtbqImporter.convert(fixture(), "pack", Ids.empty());
        assertTrue(!plain.files().get("data/pack/rysquests/book.json").has("pre_complete"), "a linear or silent file leaves the book off");
    }

    private static Source fixture() {
        Map<Long, JsonObject> tables = new HashMap<>();
        tables.put(1433225472L, parse(TABLE));
        Map<String, JsonObject> lang = new HashMap<>();
        lang.put("en_us", parse(LANG_EN));
        lang.put("ru_ru", parse(LANG_RU));
        return new Source(new JsonObject(), List.of(), List.of(parse(CHAPTER)), tables, lang);
    }

    private static JsonObject parse(String snbt) {
        return Snbt.parse(snbt).getAsJsonObject();
    }

    /** Runs our own loader over generated files, so the importer can only pass by producing a loadable book. */
    private static BookValidator.Result load(Result result) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        result.files().forEach((path, json) -> {
            String[] parts = path.split("/", 4);
            String relative = parts[3].substring(0, parts[3].length() - ".json".length());
            files.put(ResourceLocation.fromNamespaceAndPath(parts[1], relative), json);
        });
        return BookValidator.validate(BookParser.parse(files, JsonOps.INSTANCE), id -> true, JsonOps.INSTANCE);
    }

    @Test
    void snbtReadsRelaxedSyntax() {
        JsonObject parsed = parse("{ a: 1L b: 2.5d, c: \"x\\\"y\" d: [I; 1 2 3] e: true 3DC98D0DC910BA04: 0b f: [ { g: 1 } { g: 2 } ] }");

        assertEquals(1, parsed.get("a").getAsLong());
        assertEquals(2.5, parsed.get("b").getAsDouble());
        assertEquals("x\"y", parsed.get("c").getAsString());
        assertEquals(3, parsed.getAsJsonArray("d").size());
        assertTrue(parsed.get("e").getAsBoolean());
        assertTrue(parsed.has("3DC98D0DC910BA04"));
        assertEquals(2, parsed.getAsJsonArray("f").size());
    }

    @Test
    void snbtBytesZeroAndOneAreBooleans() {
        JsonObject parsed = parse("{ off: 0b on: 1b other: 5b }");

        assertTrue(parsed.get("off").getAsJsonPrimitive().isBoolean());
        assertTrue(parsed.get("on").getAsBoolean());
        assertEquals(5, parsed.get("other").getAsInt());
    }

    @Test
    void generatedBookLoadsCleanly() {
        Result result = FtbqImporter.convert(fixture(), "pack", Ids.empty());

        BookValidator.Result loaded = load(result);

        assertEquals(List.of(), loaded.report().issues());
        assertEquals(2, loaded.book().quests().size());
        assertEquals(1, loaded.book().chapters().size());
    }

    @Test
    void equalTitlesGetDistinctReadableIds() {
        Ids ids = FtbqImporter.convert(fixture(), "pack", Ids.empty()).ids();

        assertEquals("pack:b01_first/hello_world", ids.quests().get("73F8F7A274272D6D"));
        assertEquals("pack:b01_first/hello_world_2", ids.quests().get("41239A05E60B07FC"));
    }

    @Test
    void reimportKeepsIdsEvenIfTitleChanges() {
        Ids first = FtbqImporter.convert(fixture(), "pack", Ids.empty()).ids();
        Source renamed = fixture();
        renamed.lang().get("en_us").addProperty("quest.73F8F7A274272D6D.title", "Totally New Title");

        Ids second = FtbqImporter.convert(renamed, "pack", first).ids();

        assertEquals(first.quests(), second.quests());
    }

    @Test
    void mapsTasksRewardsAndTexts() {
        Result result = FtbqImporter.convert(fixture(), "pack", Ids.empty());

        JsonObject quest = result.files().get("data/pack/rysquests/chapters/b01_first/hello_world_2.json");
        assertEquals("any", quest.get("requires_mode").getAsString());
        JsonObject item = quest.getAsJsonArray("tasks").get(0).getAsJsonObject();
        assertEquals("consume", item.get("mode").getAsString());
        assertEquals(16, item.get("count").getAsInt());
        assertEquals("rysquests:kill", quest.getAsJsonArray("tasks").get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("#minecraft:village", quest.getAsJsonArray("tasks").get(2).getAsJsonObject().get("structure").getAsString());
        assertEquals("minecraft:custom", quest.getAsJsonArray("tasks").get(3).getAsJsonObject().get("stat").getAsString());
        JsonObject random = quest.getAsJsonArray("rewards").get(1).getAsJsonObject();
        assertEquals(3, random.getAsJsonArray("weights").get(0).getAsInt());

        assertEquals("Line one\nLine two", result.lang().get("en_us").get("quest.pack.b01_first.hello_world.text"));
        assertEquals("Привет", result.lang().get("ru_ru").get("quest.pack.b01_first.hello_world.title"));
    }

    /** A quest link repeats a quest of another chapter: it becomes the chapter's list of links, and the book still loads. */
    @Test
    void questLinksBecomeChapterLinks() {
        String linking = """
                {
                \tfilename: "b02_second"
                \tgroup: ""
                \tid: "5D4F8E0A29BDC611"
                \torder_index: 1
                \tquest_links: [
                \t\t{ id: "1A2B3C4D5E6F7081", linked_quest: "73F8F7A274272D6D", x: 1.0d, y: 0.0d }
                \t\t{ id: "1A2B3C4D5E6F7082", linked_quest: "73F8F7A274272D6D", x: 3.0d, y: 0.0d }
                \t\t{ id: "1A2B3C4D5E6F7083", linked_quest: "0000000000000000", x: 2.0d, y: 0.0d }
                \t]
                \tquests: [ { id: "6E5F7A8B9C0D1E2F", tasks: [{ id: "7BA02683DFEFB03B", type: "checkmark" }], x: 0.0d } ]
                }
                """;
        Source source = fixture();
        Source withLinks = new Source(source.data(), source.groups(), List.of(parse(CHAPTER), parse(linking)), source.tables(), source.lang());

        Result result = FtbqImporter.convert(withLinks, "pack", Ids.empty());

        JsonObject chapter = result.files().get("data/pack/rysquests/chapters/b02_second/_chapter.json");
        assertEquals(1, chapter.getAsJsonArray("links").size(), "the same quest linked twice counts once, a link to nothing is skipped");
        assertEquals("pack:b01_first/hello_world", chapter.getAsJsonArray("links").get(0).getAsString());
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("0000000000000000")));
        BookValidator.Result loaded = load(result);
        assertEquals(List.of(), loaded.report().issues());
        assertEquals(List.of(ResourceLocation.parse("pack:b01_first/hello_world")), loaded.book().chapters().get(ResourceLocation.parse("pack:b02_second")).links());
    }

    @Test
    void experienceCraftingAndUnconvertibleQuests() {
        String chapter = """
                {
                \tfilename: "b03_third"
                \tgroup: ""
                \tid: "7D4F8E0A29BDC611"
                \torder_index: 2
                \tquests: [
                \t\t{ id: "6E5F7A8B9C0D1E30", tasks: [{ id: "7BA02683DFEFB040", type: "xp", value: 30L, points: true }], x: 0.0d }
                \t\t{ id: "6E5F7A8B9C0D1E31", tasks: [{ id: "7BA02683DFEFB041", type: "item", only_from_crafting: true, item: { count: 1, id: "minecraft:torch" }, count: 8L }], x: 1.0d }
                \t\t{ id: "6E5F7A8B9C0D1E32", rewards: [{ id: "47AD93D250742570", type: "xp", xp: 10 }], tasks: [{ id: "7BA02683DFEFB042", type: "observation" }], x: 2.0d }
                \t]
                }
                """;
        Source source = fixture();
        Result result = FtbqImporter.convert(new Source(source.data(), source.groups(), List.of(parse(chapter)), source.tables(), source.lang()), "pack", Ids.empty());

        List<JsonObject> quests = result.files().entrySet().stream().filter(entry -> entry.getKey().contains("b03_third/") && !entry.getKey().endsWith("_chapter.json"))
                .sorted(java.util.Map.Entry.comparingByKey()).map(java.util.Map.Entry::getValue).toList();
        JsonObject xp = quests.stream().map(q -> q.getAsJsonArray("tasks").get(0).getAsJsonObject()).filter(t -> t.get("type").getAsString().equals("rysquests:xp")).findFirst().orElseThrow();
        assertEquals(30, xp.get("points").getAsInt());
        assertTrue(xp.get("consume").getAsBoolean());
        assertTrue(quests.stream().map(q -> q.getAsJsonArray("tasks").get(0).getAsJsonObject())
                .anyMatch(t -> t.has("mode") && t.get("mode").getAsString().equals("craft")));
        // A task the book cannot hold stands in as a checkbox, and the quest is a draft that players are not sent.
        JsonObject unconverted = quests.stream().filter(q -> q.getAsJsonArray("tasks").get(0).getAsJsonObject().get("type").getAsString().equals("rysquests:checkbox"))
                .findFirst().orElseThrow();
        assertTrue(unconverted.get("draft").getAsBoolean());
        assertEquals("rysquests.import.unconverted_task", unconverted.getAsJsonArray("tasks").get(0).getAsJsonObject().get("title").getAsString());
        assertEquals(List.of(), load(result).report().issues());
    }

    /** Every kind the book can hold comes across, field names as FTB Quests 2101.1.36 reads them. */
    @Test
    void theKindsTheBookCanHoldComeAcross() {
        String chapter = """
                {
                \tfilename: "b04_fourth"
                \tgroup: ""
                \tid: "8D4F8E0A29BDC611"
                \torder_index: 3
                \tquests: [
                \t\t{
                \t\t\tid: "6E5F7A8B9C0D1E40"
                \t\t\thide_until_deps_visible: true
                \t\t\trewards: [
                \t\t\t\t{ id: "47AD93D250742580", type: "command", command: "/give {p} minecraft:diamond", permission_level: 3 }
                \t\t\t\t{ id: "47AD93D250742585", type: "command", command: "/say {p}", permission_level: 0 }
                \t\t\t\t{ id: "47AD93D250742581", type: "loot", table_id: 1433225472L }
                \t\t\t\t{ id: "47AD93D250742582", type: "gamestage", stage: "iron_age" }
                \t\t\t\t{ id: "47AD93D250742583", type: "advancement", advancement: "minecraft:story/mine_stone" }
                \t\t\t\t{ id: "47AD93D250742584", type: "toast", description: "hi" }
                \t\t\t]
                \t\t\ttasks: [
                \t\t\t\t{ id: "7BA02683DFEFB050", type: "advancement", advancement: "minecraft:story/root", criterion: "" }
                \t\t\t\t{ id: "7BA02683DFEFB051", type: "fluid", fluid: { id: "minecraft:lava", amount: 2000L } }
                \t\t\t\t{ id: "7BA02683DFEFB052", type: "biome", biome: "#minecraft:is_forest" }
                \t\t\t\t{ id: "7BA02683DFEFB053", type: "location", dimension: "minecraft:the_nether", position: [I; 10, 64, 20], size: [I; 4, 4, 8] }
                \t\t\t]
                \t\t\tx: 0.0d
                \t\t}
                \t\t{ id: "6E5F7A8B9C0D1E41", tasks: [{ id: "7BA02683DFEFB054", type: "gamestage", stage: "steel_age", team_stage: true }], x: 1.0d }
                \t\t{ id: "6E5F7A8B9C0D1E42", tasks: [{ id: "7BA02683DFEFB055", type: "fluid", fluid: "minecraft:water" }], x: 2.0d }
                \t\t{ id: "6E5F7A8B9C0D1E43", rewards: [{ id: "47AD93D250742590", type: "item" }], tasks: [{ id: "7BA02683DFEFB056", type: "item", item: {} }, { id: "7BA02683DFEFB057", type: "item", item: "minecraft:stone", count: 4L }, { id: "7BA02683DFEFB058", type: "kill", entity: "minecraft:zombie", entityTypeTag: "minecraft:skeletons", value: 3L }], x: 3.0d }
                \t]
                }
                """;
        Source source = fixture();
        Result result = FtbqImporter.convert(new Source(source.data(), source.groups(), List.of(parse(chapter)), source.tables(), source.lang()), "pack", Ids.empty());

        List<JsonObject> quests = result.files().entrySet().stream().filter(entry -> entry.getKey().contains("b04_fourth/") && !entry.getKey().endsWith("_chapter.json"))
                .sorted(java.util.Map.Entry.comparingByKey()).map(java.util.Map.Entry::getValue).toList();
        JsonObject full = quests.stream().filter(q -> q.getAsJsonArray("tasks").size() == 4).findFirst().orElseThrow();
        JsonObject staged = quests.stream().filter(q -> q.has("stage_required")).findFirst().orElseThrow();
        JsonObject legacyFluid = quests.stream().filter(q -> q.getAsJsonArray("tasks").size() == 1
                && q.getAsJsonArray("tasks").get(0).getAsJsonObject().get("type").getAsString().equals("rysquests:fluid")).findFirst().orElseThrow();
        // FTB Quests shows such a quest once its dependencies are visible; hidden here means until they are done.
        assertTrue(!full.has("hidden"));
        assertEquals(5, full.getAsJsonArray("rewards").size(), "the toast is left out");
        JsonObject command = full.getAsJsonArray("rewards").get(0).getAsJsonObject();
        assertEquals("/give {p} minecraft:diamond", command.get("command").getAsString());
        assertTrue(command.get("as_server").getAsBoolean());
        assertEquals(3, command.get("level").getAsInt(), "the rights FTB Quests had, not more");
        assertTrue(!full.getAsJsonArray("rewards").get(1).getAsJsonObject().has("as_server"), "level 0 runs as the player");
        assertEquals("rysquests:random", full.getAsJsonArray("rewards").get(2).getAsJsonObject().get("type").getAsString());
        assertEquals("iron_age", full.getAsJsonArray("rewards").get(3).getAsJsonObject().get("stage").getAsString());
        assertEquals("minecraft:lava", full.getAsJsonArray("tasks").get(1).getAsJsonObject().get("fluid").getAsString());
        assertEquals(2000, full.getAsJsonArray("tasks").get(1).getAsJsonObject().get("amount").getAsInt());
        assertEquals(1000, legacyFluid.getAsJsonArray("tasks").get(0).getAsJsonObject().get("amount").getAsInt(), "an old book without an amount");
        JsonObject place = full.getAsJsonArray("tasks").get(3).getAsJsonObject();
        assertEquals(List.of(10, 64, 20), place.getAsJsonArray("pos").asList().stream().map(JsonElement::getAsInt).toList());
        assertEquals(List.of(4, 4, 8), place.getAsJsonArray("size").asList().stream().map(JsonElement::getAsInt).toList());
        assertEquals("steel_age", staged.get("stage_required").getAsString());
        assertEquals(0, staged.getAsJsonArray("tasks").size(), "a quest that only waited for a stage opens and completes with it");
        assertTrue(!staged.has("page"));
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("team stage")));
        // An empty stack, a 1.20 string item and a kill task for a tag: the import goes on and keeps what it can. What it cannot
        // keep stands in as a checkbox, and the quest is a draft until the author looks at it.
        JsonObject legacy = quests.stream().filter(q -> q.getAsJsonArray("tasks").size() == 3).findFirst().orElseThrow();
        assertEquals("rysquests:checkbox", legacy.getAsJsonArray("tasks").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("minecraft:stone", legacy.getAsJsonArray("tasks").get(1).getAsJsonObject().get("item").getAsString());
        assertEquals("rysquests:checkbox", legacy.getAsJsonArray("tasks").get(2).getAsJsonObject().get("type").getAsString());
        assertEquals(0, legacy.getAsJsonArray("rewards").size());
        assertTrue(legacy.get("draft").getAsBoolean());
        assertTrue(full.get("draft").getAsBoolean(), "the toast was left out");
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("minecraft:skeletons")));
        assertTrue(result.notes().getFirst().startsWith("Summary: "));
        assertTrue(result.notes().stream().anyMatch(note -> note.equals("Summary: reward toast: 0 carried over, 1 skipped")));
        assertEquals(List.of(), load(result).report().issues());
    }

    private static void stripComponents(JsonElement element) {
        if (element.isJsonObject()) {
            element.getAsJsonObject().remove("components");
            element.getAsJsonObject().remove("nbt");
            element.getAsJsonObject().asMap().values().forEach(FtbqImporterTest::stripComponents);
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(FtbqImporterTest::stripComponents);
        }
    }

    /** Runs on a real book only with -PftbqDir=path; item ids are not checked because the modded items are absent here. */
    @Test
    void importsRealBook() throws IOException {
        String dir = System.getProperty("rysquests.ftbqDir");
        assumeTrue(dir != null && !dir.isBlank());

        Result result = FtbqImporter.convert(FtbqFiles.read(Path.of(dir)), "repys", Ids.empty());
        // Component types of absent mods cannot be parsed here, in the real game they resolve.
        result.files().values().forEach(FtbqImporterTest::stripComponents);
        BookValidator.Result loaded = load(result);

        List<String> out = new ArrayList<>();
        out.add("chapters " + result.chapters() + " quests " + result.quests() + " notes " + result.notes().size());
        out.add("loaded chapters " + loaded.book().chapters().size() + " quests " + loaded.book().quests().size()
                + " errors " + loaded.report().errors() + " warnings " + loaded.report().warnings());
        loaded.report().issues().stream().limit(40).map(BookReport.Issue::toString).forEach(out::add);
        result.notes().stream().limit(20).forEach(out::add);
        Files.write(Path.of(System.getProperty("rysquests.reportFile")), out, StandardCharsets.UTF_8);
    }
}
