package works.repka.rysquests.integration.patchouli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.StructureDef;

class PatchouliImporterTest {
    private static final String ENTRY = """
            {
             "name": "%s",
             "icon": "minecraft:stone",
             "category": "patchouli:machines",
             "sortnum": 20,
             "pages": [
              { "type": "patchouli:text", "text": "$(l)%s$()$(br)Size: 3x1x1.$(br2)Next." },
              { "type": "patchouli:multiblock", "name": "%s", "multiblock": { "mapping": { "C": "minecraft:stone", "0": "minecraft:iron_block" }, "pattern": [ [ "C0C" ], [ "_ _" ] ] } },
              { "type": "patchouli:crafting", "recipe": "minecraft:stone" }
             ]
            }
            """;

    private static void book(Path folder, String language, String name) throws IOException {
        Files.createDirectories(folder.resolve(language).resolve("categories"));
        Files.createDirectories(folder.resolve(language).resolve("entries"));
        Files.writeString(folder.resolve(language).resolve("categories/machines.json"), "{ \"name\": \"Machines " + language + "\", \"icon\": \"minecraft:clock\", \"sortnum\": 0 }");
        Files.writeString(folder.resolve(language).resolve("entries/hall.json"), ENTRY.formatted(name, name, name));
    }

    @Test
    void aBookBecomesAGuideWithStructures(@TempDir Path folder) throws IOException {
        book(folder, "en_us", "Hall");
        book(folder, "ru_ru", "Зал");

        PatchouliImporter.Result result = PatchouliImporter.convert(folder, "machines", "repys");

        assertEquals(1, result.guides());
        assertEquals(1, result.pages());
        assertEquals(1, result.structures());
        JsonObject guide = result.files().get("data/repys/rysquests/guides/machines/machines.json").getAsJsonObject();
        Guide read = Guide.CODEC.parse(JsonOps.INSTANCE, guide).getOrThrow();
        assertEquals("hall", read.pages().getFirst().id());
        assertEquals("Машины".length() > 0 ? "Зал" : "", result.lang().get("ru_ru").get("guide.repys.machines.machines.hall.title"));
        String text = result.lang().get("ru_ru").get("guide.repys.machines.machines.hall.text");
        assertTrue(text.contains("[[structure:repys:machines/hall|Зал]]"), text);
        assertTrue(text.startsWith("**Зал**\nSize: 3x1x1.\n\nNext."), text);
    }

    @Test
    void aStructureReadsAsCopied(@TempDir Path folder) throws IOException {
        book(folder, "en_us", "Hall");

        JsonObject file = PatchouliImporter.convert(folder, "machines", "repys").files().get("data/repys/rysquests/structures/machines/hall.json").getAsJsonObject().deepCopy();
        file.addProperty("id", "repys:machines/hall");
        StructureDef structure = StructureDef.CODEC.parse(JsonOps.INSTANCE, file).getOrThrow();

        assertTrue(structure.isRectangular());
        assertEquals(Optional.empty(), structure.unknownSymbol());
        assertTrue(structure.hasCenter());
        int[] blocks = new int[1];
        int[] highest = new int[1];
        structure.forEachBlock((x, y, z, block) -> {
            blocks[0]++;
            highest[0] = Math.max(highest[0], y);
        });
        assertEquals(3, blocks[0], "'_' and spaces hold nothing");
        assertEquals(1, highest[0], "The first layer is the top one");
    }

    @Test
    void whatCannotBeCarriedIsNoted(@TempDir Path folder) throws IOException {
        book(folder, "en_us", "Hall");

        assertEquals(List.of("Entry hall: page type crafting has no equivalent, skipped"), PatchouliImporter.convert(folder, "machines", "repys").notes());
    }

    @Test
    void formattingBecomesOurMarkup() {
        assertEquals("**Bold** plain\n- item", PatchouliImporter.markup("$(l)Bold$() $(#ff0000)plain$()$(li)item"));
    }

    @Test
    void aBrokenStructureIsToldApart() {
        StructureDef ragged = new StructureDef(ResourceLocation.parse("t:a"), Optional.empty(), Map.of("A", "minecraft:stone"), List.of(List.of("AA"), List.of("A")));
        StructureDef unknown = new StructureDef(ResourceLocation.parse("t:a"), Optional.empty(), Map.of("A", "minecraft:stone"), List.of(List.of("AB")));

        assertFalse(ragged.isRectangular());
        assertEquals(Optional.of("B"), unknown.unknownSymbol());
        assertFalse(unknown.hasCenter());
    }
}
