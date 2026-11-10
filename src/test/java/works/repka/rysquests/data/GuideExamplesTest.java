package works.repka.rysquests.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.migrate.Migration;

/** docs/GUIDE.md promises that its examples work, so each marked block is read with the codec the game uses. */
class GuideExamplesTest {
    private static final Path GUIDE = Path.of(System.getProperty("rysquests.examplesDir")).resolveSibling("docs").resolve("GUIDE.md");
    private static final Pattern BLOCK = Pattern.compile("```json (\\w+)\\R(.*?)\\R```", Pattern.DOTALL);

    private static <A> void read(Codec<A> codec, JsonElement json, String block) {
        assertTrue(codec.parse(JsonOps.INSTANCE, json).isSuccess(), () -> "Does not read: " + block + "\n" + codec.parse(JsonOps.INSTANCE, json).error().map(Object::toString).orElse(""));
    }

    @Test
    void everyMarkedExampleReads() throws IOException {
        Matcher blocks = BLOCK.matcher(Files.readString(GUIDE, StandardCharsets.UTF_8));
        Map<String, Integer> seen = new HashMap<>();
        while (blocks.find()) {
            String kind = blocks.group(1);
            String text = blocks.group(2);
            JsonElement json = JsonParser.parseString(text);
            seen.merge(kind, 1, Integer::sum);
            switch (kind) {
                case "book" -> read(BookMeta.CODEC, json, text);
                case "chapter" -> read(Chapter.CODEC, json, text);
                case "quest" -> read(Quest.CODEC, json, text);
                case "task" -> read(TaskDef.CODEC, json, text);
                case "reward" -> read(RewardDef.CODEC, json, text);
                case "guide" -> read(Guide.CODEC, json, text);
                case "offer" -> read(ShopOffer.CODEC, json, text);
                case "migrations" -> read(Migration.FILE_CODEC, json, text);
                case "section" -> {
                    JsonObject withId = json.getAsJsonObject().deepCopy();
                    withId.addProperty("id", "t:section");
                    read(SectionDef.CODEC, withId, text);
                }
                case "achievement" -> {
                    read(Achievement.CODEC, json, text);
                    read(TaskDef.CODEC, json.getAsJsonObject().get("task"), text);
                }
                default -> throw new AssertionError("Unknown kind of example: " + kind);
            }
        }
        for (String kind : List.of("book", "chapter", "quest", "task", "reward", "guide", "offer", "migrations", "achievement")) {
            assertTrue(seen.containsKey(kind), () -> "The guide lost its example of " + kind);
        }
        assertEquals(4, seen.get("section"), "Mining atlas, build atlas, cards and pages are expected");
    }
}
