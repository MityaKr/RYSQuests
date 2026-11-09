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
