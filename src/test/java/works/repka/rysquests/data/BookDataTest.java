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
        assertEquals(6, result.book().quests().size());
        assertEquals(List.of(ResourceLocation.parse("example:01_start"), ResourceLocation.parse("example:02_power")),
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
}
