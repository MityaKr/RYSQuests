package works.repka.rysquests.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.data.BookAssets;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.QuestBook;

class EditorFilesTest {
    private static final ResourceLocation ID = ResourceLocation.parse("t:forge");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10};
    private static final ResourceLocation IMAGE = ResourceLocation.parse("t:textures/guides/forge_1.png");

    @TempDir
    Path root;

    private static Guide guide() {
        return new Guide(ID, "guide.t.forge.title", Optional.of(ResourceLocation.parse("minecraft:furnace")), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(new Guide.Page("page_1", "guide.t.forge.page_1.title", "guide.t.forge.page_1.text")));
    }

    private static Map<String, String> texts() {
        return Map.of("guide.t.forge.title", "Кузня", "guide.t.forge.page_1.title", "Первая", "guide.t.forge.page_1.text", "Текст [[image:t:textures/guides/forge_1.png]]");
    }

    private void save(Guide guide, Map<String, String> texts, Map<ResourceLocation, byte[]> images) throws Exception {
        EditorFiles.save(root, Optional.of(guide), Optional.empty(), "ru_ru", texts, images, true);
    }

    private JsonObject readJson(String path) throws IOException {
        return JsonParser.parseString(Files.readString(root.resolve(path))).getAsJsonObject();
    }

    @Test
    void savedGuideReadsBackInTheFormatOfTheBook() throws Exception {
        save(guide(), texts(), Map.of(IMAGE, PNG));

        JsonObject json = readJson("data/t/rysquests/guides/forge.json");
        assertEquals(guide(), Guide.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(List.of("id", "title", "icon", "pages"), List.copyOf(json.keySet()));
        assertEquals("Кузня", readJson("assets/t/lang/ru_ru.json").get("guide.t.forge.title").getAsString());
        assertTrue(Files.isRegularFile(root.resolve("assets/t/textures/guides/forge_1.png")));
        assertTrue(Files.isRegularFile(root.resolve("pack.mcmeta")));
    }

    @Test
    void savingKeepsTheOtherStringsOfALangFile() throws Exception {
        Files.createDirectories(root.resolve("assets/t/lang"));
        Files.writeString(root.resolve("assets/t/lang/ru_ru.json"), "{\"other.key\":\"сохранить\",\"guide.t.forge.title\":\"старое\"}");

        save(guide(), texts(), Map.of());

        JsonObject lang = readJson("assets/t/lang/ru_ru.json");
        assertEquals("сохранить", lang.get("other.key").getAsString());
        assertEquals("Кузня", lang.get("guide.t.forge.title").getAsString());
        assertEquals("other.key", lang.keySet().iterator().next());
    }

    @Test
    void aSaveCannotWriteStringsOfSomethingElse() {
        EditorFiles.Rejected rejected = assertThrows(EditorFiles.Rejected.class, () -> save(guide(), Map.of("menu.quit", "x"), Map.of()));

        assertEquals("rysquests.editor.error.invalid", rejected.key());
        assertFalse(Files.exists(root.resolve("data")));
    }

    @Test
    void pictureMustBeAPngInTheGuidesFolderOfTheSameNamespace() {
        assertThrows(EditorFiles.Rejected.class, () -> save(guide(), texts(), Map.of(IMAGE, new byte[] {1, 2, 3, 4, 5})));
        assertThrows(EditorFiles.Rejected.class, () -> save(guide(), texts(), Map.of(ResourceLocation.parse("t:textures/block/stone.png"), PNG)));
        assertThrows(EditorFiles.Rejected.class, () -> save(guide(), texts(), Map.of(ResourceLocation.parse("minecraft:textures/guides/a.png"), PNG)));
        assertFalse(Files.exists(root.resolve("data")));
    }

    @Test
    void oversizedPictureIsRefused() {
        byte[] large = new byte[BookAssets.MAX_IMAGE_BYTES + 1];
        System.arraycopy(PNG, 0, large, 0, PNG.length);

        EditorFiles.Rejected rejected = assertThrows(EditorFiles.Rejected.class, () -> save(guide(), texts(), Map.of(IMAGE, large)));

        assertEquals("rysquests.editor.error.image_too_large", rejected.key());
    }

    @Test
    void aGuideNeedsPagesWithDifferentIds() {
        Guide empty = new Guide(ID, "T", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        Guide twice = new Guide(ID, "T", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(new Guide.Page("a", "T", "x"), new Guide.Page("a", "T", "y")));

        assertThrows(EditorFiles.Rejected.class, () -> save(empty, Map.of(), Map.of()));
        assertThrows(EditorFiles.Rejected.class, () -> save(twice, Map.of(), Map.of()));
    }

    @Test
    void sectionFileLeavesItsIdToThePath() throws Exception {
        PagesSection section = new PagesSection(ResourceLocation.parse("t:rules"), "Rules", Optional.empty(), Optional.empty(), Optional.empty(), List.of(new Guide.Page("a", "A", "text")));

        EditorFiles.save(root, Optional.empty(), Optional.of(section), "ru_ru", Map.of(), Map.of(), false);

        JsonObject json = readJson("data/t/rysquests/sections/rules.json");
        assertFalse(json.has("id"));
        assertEquals("pages", json.get("template").getAsString());
        assertFalse(Files.exists(root.resolve("pack.mcmeta")));
    }

    @Test
    void bookCarriesOnlyWhatItRefersTo() throws Exception {
        save(guide(), texts(), Map.of(IMAGE, PNG));
        Files.writeString(root.resolve("assets/t/lang/en_us.json"), "{\"guide.t.forge.title\":\"Forge\",\"unrelated.mod.key\":\"x\"}");
        Files.createDirectories(root.resolve("assets/t/textures/guides"));
        Files.write(root.resolve("assets/t/textures/guides/unused.png"), PNG);
        QuestBook book = QuestBook.create(BookMeta.DEFAULT, List.of(), List.of(), List.of(guide()), List.of(), "");

        BookAssets assets = BookAssets.read(root, book);

        assertEquals("Кузня", assets.texts().get("ru_ru").get("guide.t.forge.title"));
        assertEquals(Map.of("guide.t.forge.title", "Forge"), assets.texts().get("en_us"));
        assertEquals(Map.of(IMAGE, ByteBuffer.wrap(PNG)), assets.images());
    }
}
