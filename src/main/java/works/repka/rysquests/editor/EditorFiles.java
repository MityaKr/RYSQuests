package works.repka.rysquests.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.BookAssets;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.SectionDef;

/** Writes what the editor saves into a folder laid out like a pack. Knows nothing about the game, so it can be tested. */
public final class EditorFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}_[a-z0-9]{2,4}");
    private static final Pattern IMAGE_PATH = Pattern.compile("textures/guides/[a-z0-9_.-]+\\.png");
    private static final String PAGES = "pages";
    private static final List<String> GUIDE_FIELDS = List.of("id", "title", "icon", "category", "stage", "stage_title", PAGES);
    private static final List<String> SECTION_FIELDS = List.of("template", "title", "short_title", "subtitle", "icon", PAGES);
    private static final List<String> PAGE_FIELDS = List.of("id", "title", "text");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    /** A save that cannot be done. @param key the lang key of the reason */
    public static final class Rejected extends Exception {
        private final String key;

        public Rejected(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private EditorFiles() {
    }

    /** Writes a guide or a section of pages with its texts and pictures. Everything is checked before anything is written. */
    public static void save(Path root, Optional<Guide> guide, Optional<PagesSection> section, String language, Map<String, String> texts,
            Map<ResourceLocation, byte[]> images, boolean createPackFile) throws Rejected, IOException {
        if (guide.isPresent() == section.isPresent()) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
        ResourceLocation id = guide.map(Guide::id).orElseGet(() -> section.get().id());
        checkPages(guide.map(Guide::pages).orElseGet(() -> section.get().pages()));
        checkTexts(keysOf(guide, section), language, texts);
        for (Map.Entry<ResourceLocation, byte[]> image : images.entrySet()) {
            checkImage(id, image.getKey(), image.getValue());
        }
        if (createPackFile) {
            writePackFile(root);
        }
        Path file = guide.isPresent()
                ? root.resolve("data").resolve(id.getNamespace()).resolve("rysquests/guides").resolve(id.getPath() + ".json")
                : root.resolve("data").resolve(id.getNamespace()).resolve("rysquests/sections").resolve(id.getPath() + ".json");
        writeText(file, GSON.toJson(guide.isPresent() ? encode(guide.get()) : encode(section.get())) + "\n");
        if (!texts.isEmpty()) {
            mergeLang(root.resolve("assets").resolve(id.getNamespace()).resolve("lang").resolve(language + ".json"), texts);
        }
        for (Map.Entry<ResourceLocation, byte[]> image : images.entrySet()) {
            Path target = root.resolve("assets").resolve(image.getKey().getNamespace()).resolve(image.getKey().getPath());
            Files.createDirectories(target.getParent());
            Files.write(target, image.getValue());
        }
    }

    private static void checkPages(List<Guide.Page> pages) throws Rejected {
        Set<String> ids = new HashSet<>();
        if (pages.isEmpty()) {
            throw new Rejected("rysquests.editor.error.no_pages");
        }
        for (Guide.Page page : pages) {
            if (!ids.add(page.id())) {
                throw new Rejected("rysquests.editor.error.invalid");
            }
        }
    }

    private static Set<String> keysOf(Optional<Guide> guide, Optional<PagesSection> section) {
        Set<String> keys = new HashSet<>();
        guide.ifPresent(found -> {
            keys.add(found.title());
            found.category().ifPresent(keys::add);
            found.stageTitle().ifPresent(keys::add);
            found.pages().forEach(page -> {
                keys.add(page.title());
                keys.add(page.text());
            });
        });
        section.ifPresent(found -> {
            keys.add(found.title());
            found.shortTitle().ifPresent(keys::add);
            found.subtitle().ifPresent(keys::add);
            found.pages().forEach(page -> {
                keys.add(page.title());
                keys.add(page.text());
            });
        });
        return keys;
    }

    /** The editor only ever writes the strings of what it saves, so a request cannot touch the rest of a lang file. */
    private static void checkTexts(Set<String> keys, String language, Map<String, String> texts) throws Rejected {
        if (!LANGUAGE.matcher(language).matches() || !keys.containsAll(texts.keySet())) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
    }

    private static void checkImage(ResourceLocation owner, ResourceLocation id, byte[] bytes) throws Rejected {
        if (!id.getNamespace().equals(owner.getNamespace()) || !IMAGE_PATH.matcher(id.getPath()).matches() || !isPng(bytes)) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
        if (bytes.length > BookAssets.MAX_IMAGE_BYTES) {
            throw new Rejected("rysquests.editor.error.image_too_large");
        }
    }

    private static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG.length) {
            return false;
        }
        for (int i = 0; i < PNG.length; i++) {
            if (bytes[i] != PNG[i]) {
                return false;
            }
        }
        return true;
    }

    private static JsonElement encode(Guide guide) {
        return ordered(Guide.CODEC.encodeStart(JsonOps.INSTANCE, guide).getOrThrow().getAsJsonObject(), GUIDE_FIELDS);
    }

    /** A section's id comes from its file, so the file does not repeat it. */
    private static JsonElement encode(PagesSection section) {
        JsonObject json = SectionDef.CODEC.encodeStart(JsonOps.INSTANCE, section).getOrThrow().getAsJsonObject();
        json.remove("id");
        return ordered(json, SECTION_FIELDS);
    }

    /** Codecs do not promise an order of fields, and a file has to diff well, so it is set here. */
    private static JsonObject ordered(JsonObject json, List<String> fields) {
        JsonObject out = new JsonObject();
        for (String field : fields) {
            if (json.has(field)) {
                out.add(field, field.equals(PAGES) ? orderedPages(json.getAsJsonArray(PAGES)) : json.get(field));
            }
        }
        return out;
    }

    private static JsonArray orderedPages(JsonArray pages) {
        JsonArray out = new JsonArray();
        pages.forEach(page -> out.add(ordered(page.getAsJsonObject(), PAGE_FIELDS)));
        return out;
    }

    private static void writePackFile(Path root) throws IOException {
        Path file = root.resolve("pack.mcmeta");
        if (!Files.isRegularFile(file)) {
            writeText(file, "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"RYSQuests editor\"\n  }\n}\n");
        }
    }

    /** New strings are added to the end and changed ones stay where they were, so the file diffs well. */
    private static void mergeLang(Path file, Map<String, String> texts) throws IOException {
        JsonObject lang = new JsonObject();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                lang = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (RuntimeException e) {
                throw new IOException("Broken lang file " + file, e);
            }
        }
        JsonObject merged = lang;
        new java.util.TreeMap<>(texts).forEach(merged::addProperty);
        writeText(file, GSON.toJson(merged) + "\n");
    }

    /** Through a temporary file, so that a crash never leaves half a file. */
    private static void writeText(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
