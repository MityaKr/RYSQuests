package works.repka.rysquests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The shop blocks ship default vanilla recipes; a modpack replaces them by id, so the ids and the files
 * must stay exactly where the docs say. Registries are not booted in unit tests, so the shape is checked here
 * and the game reads the rest.
 */
class ShopRecipesTest {
    private static JsonObject resource(String path) throws IOException {
        try (InputStream in = ShopRecipesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void ingredient(String symbol, JsonElement json) {
        JsonObject o = json.getAsJsonObject();
        if (o.has("type")) {
            assertEquals("neoforge:difference", o.get("type").getAsString(), symbol);
            ingredient(symbol, o.get("base"));
            ingredient(symbol, o.get("subtracted"));
            return;
        }
        assertTrue(o.has("item") ^ o.has("tag"), () -> symbol + " needs exactly one of item or tag");
        ResourceLocation.parse(o.has("item") ? o.get("item").getAsString() : o.get("tag").getAsString());
    }

    @ParameterizedTest
    @CsvSource({"shop_receiver, IEI|IHI|ICI, minecraft:hopper", "shop_dispenser, IEI|IDI|IRI, minecraft:dispenser"})
    void defaultRecipeIsShapedUnderItsDocumentedId(String block, String pattern, String unlockItem) throws IOException {
        JsonObject recipe = resource("data/rysquests/recipe/" + block + ".json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());

        JsonArray rows = recipe.getAsJsonArray("pattern");
        assertEquals(pattern, String.join("|", rows.asList().stream().map(JsonElement::getAsString).toList()));

        Set<String> used = new HashSet<>();
        rows.forEach(row -> row.getAsString().chars().filter(c -> c != ' ').forEach(c -> used.add(String.valueOf((char) c))));
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals(used, key.keySet(), "every pattern symbol has a key and every key is used");
        key.entrySet().forEach(e -> ingredient(e.getKey(), e.getValue()));

        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("rysquests:" + block, result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());

        JsonObject advancement = resource("data/rysquests/advancement/recipes/redstone/" + block + ".json");
        assertEquals("rysquests:" + block, advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        assertEquals("rysquests:" + block, criteria.getAsJsonObject("has_the_recipe").getAsJsonObject("conditions").get("recipe").getAsString());
        String item = criteria.entrySet().stream().filter(e -> !e.getKey().equals("has_the_recipe")).findFirst().orElseThrow()
                .getValue().getAsJsonObject().getAsJsonObject("conditions").getAsJsonArray("items").get(0).getAsJsonObject().get("items").getAsString();
        assertEquals(unlockItem, item);
    }
}
