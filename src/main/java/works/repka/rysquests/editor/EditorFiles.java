package works.repka.rysquests.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
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
import works.repka.rysquests.data.BookLayout;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.SectionDef;

/** Writes what the editor saves into a folder laid out like a pack. Knows nothing about the game, so it can be tested. */
public final class EditorFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}_[a-z0-9]{2,4}");
    private static final Pattern IMAGE_PATH = Pattern.compile("textures/guides/[a-z0-9_.-]+\\.png");
    /** Every field of guides and sections in the order a file shows them; others follow by name. */
    private static final List<String> FIELD_ORDER = List.of("template", "id", "type", "title", "name", "short_title", "subtitle", "text", "icon", "category", "item", "count",
            "advancement", "spoilers", "compact", "search", "row_title", "group", "groups", "art", "mark", "best", "hint", "stage", "stage_title", "done", "needs", "gives", "page", "quest", "lock_text",
            "links", "columns", "rows", "cells", "cards", "pages");
    private static final List<String> QUEST_ORDER = List.of("id", "chapter", "group", "title", "subtitle", "text", "icon", "requires", "requires_mode", "stage_required", "guide", "tasks", "rewards",
            "layout", "milestone", "optional", "hidden", "repeatable", "page", "tags", "draft", "archived");
    private static final List<String> CHAPTER_ORDER = List.of("id", "title", "subtitle", "icon", "epoch", "default_view", "section", "hidden", "off_map", "requires", "groups", "links");
    private static final List<String> BOOK_ORDER = List.of("progress_mode", "default_view", "default_theme", "title", "icon", "chapter_order", "hud", "sections", "epochs", "map_form", "published");

    /** Tasks and rewards start with their type, as the files written by hand do. */
    private static final List<String> RULE_ORDER = List.of("type", "id", "item", "any_of", "entity", "stat", "target", "advancement", "dimension", "biome", "structure", "pos", "size", "radius", "fluid", "script", "table", "stage", "command",
            "levels", "points", "count", "amount", "rolls", "mode", "nbt", "of", "weights", "title", "pin", "x", "y");
    private static final String CHAPTER_FILE = "_chapter.json";
    private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9_-]+");
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
    public static void save(Path root, Optional<Guide> guide, Optional<SectionDef> section, String language, Map<String, String> texts,
            Map<ResourceLocation, byte[]> images, boolean createPackFile) throws Rejected, IOException {
        if (guide.isPresent() == section.isPresent()) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
        ResourceLocation id = guide.map(Guide::id).orElseGet(() -> section.get().id());
        checkContents(guide, section);
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

    private static void checkContents(Optional<Guide> guide, Optional<SectionDef> section) throws Rejected {
        if (guide.isPresent()) {
            checkPages(guide.get().pages());
        } else if (section.get() instanceof PagesSection pages) {
            checkPages(pages.pages());
        } else if (section.get() instanceof MatrixSection matrix) {
            checkIds(matrix.columns().stream().map(MatrixSection.Column::id).toList());
            checkIds(matrix.rows().stream().map(MatrixSection.Row::id).toList());
        } else if (section.get() instanceof CardsSection cards) {
            checkIds(cards.cards().stream().map(CardsSection.Card::id).toList());
        }
    }

    private static void checkPages(List<Guide.Page> pages) throws Rejected {
        checkIds(pages.stream().map(Guide.Page::id).toList());
    }

    private static void checkIds(List<String> ids) throws Rejected {
        if (ids.isEmpty()) {
            throw new Rejected("rysquests.editor.error.no_pages");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
    }

    /**
     * Where the pack's chapters already live, so that a quest keeps its file.
     * @param quests quest id to file
     * @param chapters chapter id to its {@code _chapter.json}
     * @param shared quests that sit together in one list file, which this editor does not write
     */
    public record QuestFiles(Map<ResourceLocation, ResourceLocation> quests, Map<ResourceLocation, ResourceLocation> chapters, Set<ResourceLocation> shared) {
    }

    /** Writes a chapter's changed quests, one file each, with their texts. Everything is checked before anything is written. */
    public static void saveChapter(Path root, Chapter chapter, boolean chapterChanged, List<Quest> quests, QuestFiles known, String language, Map<String, String> texts,
            DynamicOps<JsonElement> ops, boolean createPackFile) throws Rejected, IOException {
        if (!LANGUAGE.matcher(language).matches()) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
        ResourceLocation chapterFile = known.chapters().getOrDefault(chapter.id(), ResourceLocation.fromNamespaceAndPath(chapter.id().getNamespace(), "rysquests/chapters/" + chapter.id().getPath() + "/" + CHAPTER_FILE));
        checkFolder(chapterFile.getPath());
        String folder = chapterFile.getPath().substring(0, chapterFile.getPath().lastIndexOf('/') + 1);
        Set<String> keys = new HashSet<>();
        keys.add(chapter.title());
        chapter.subtitle().ifPresent(keys::add);
        chapter.groups().forEach(group -> keys.add(group.title()));
        Map<Path, JsonElement> files = new java.util.LinkedHashMap<>();
        for (Quest quest : quests) {
            if (known.shared().contains(quest.id())) {
                throw new Rejected("rysquests.editor.error.shared_file");
            }
            if (quest.chapter().filter(chapter.id()::equals).isEmpty()) {
                throw new Rejected("rysquests.editor.error.invalid");
            }
            ResourceLocation file = known.quests().get(quest.id());
            if (file == null) {
                String name = quest.id().getPath().substring(quest.id().getPath().lastIndexOf('/') + 1);
                if (!FILE_NAME.matcher(name).matches() || name.startsWith("_")) {
                    throw new Rejected("rysquests.editor.error.invalid");
                }
                file = ResourceLocation.fromNamespaceAndPath(chapterFile.getNamespace(), folder + name + ".json");
            }
            checkFolder(file.getPath());
            files.put(dataFile(root, file), ordered(Quest.CODEC.encodeStart(ops, quest).getOrThrow(), QUEST_ORDER, RULE_ORDER));
            keys.add(quest.title());
            quest.subtitle().ifPresent(keys::add);
            quest.text().ifPresent(keys::add);
        }
        if (!keys.containsAll(texts.keySet())) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
        if (chapterChanged) {
            files.put(dataFile(root, chapterFile), ordered(Chapter.CODEC.encodeStart(ops, chapter).getOrThrow(), CHAPTER_ORDER, RULE_ORDER));
        }
        if (createPackFile) {
            writePackFile(root);
        }
        for (Map.Entry<Path, JsonElement> file : files.entrySet()) {
            writeText(file.getKey(), GSON.toJson(file.getValue()) + "\n");
        }
        if (!texts.isEmpty()) {
            mergeLang(root.resolve("assets").resolve(chapter.id().getNamespace()).resolve("lang").resolve(language + ".json"), texts);
        }
    }

    /**
     * Writes what the editor changed outside chapters, guides and tabs: the book's settings, shop offers and achievements, with their
     * strings. A removed offer's file moves to {@code _removed/} inside the folder, out of the data, rather than being deleted; an
     * offer that lives in another pack cannot be removed from here. Everything is checked before anything is written.
     *
     * @param achievements whole achievement files, each with its {@code id}
     */
    public static void saveBookEdits(Path root, String namespace, Optional<BookMeta> meta, List<works.repka.rysquests.data.ShopOffer> offers, List<ResourceLocation> removed,
            List<JsonObject> achievements, String language, Map<String, String> texts, DynamicOps<JsonElement> ops, boolean createPackFile) throws Rejected, IOException {
        if (!LANGUAGE.matcher(language).matches()) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
        Set<String> keys = new HashSet<>();
        meta.flatMap(BookMeta::title).ifPresent(keys::add);
        Map<Path, JsonElement> files = new java.util.LinkedHashMap<>();
        for (works.repka.rysquests.data.ShopOffer offer : offers) {
            files.put(fileOf(root, offer.id(), "rysquests/shop/"), ordered(works.repka.rysquests.data.ShopOffer.CODEC.encodeStart(ops, offer).getOrThrow(), FIELD_ORDER, RULE_ORDER));
            offer.title().ifPresent(keys::add);
            offer.category().ifPresent(keys::add);
        }
        for (JsonObject achievement : achievements) {
            ResourceLocation id = achievement.has("id") ? ResourceLocation.tryParse(achievement.get("id").getAsString()) : null;
            if (id == null || !achievement.has("task") || !achievement.has("tiers")) {
                throw new Rejected("rysquests.editor.error.invalid");
            }
            files.put(fileOf(root, id, "rysquests/achievements/"), ordered(achievement, FIELD_ORDER, RULE_ORDER));
            for (String field : List.of("title", "text", "hint", "category")) {
                if (achievement.has(field)) {
                    keys.add(achievement.get(field).getAsString());
                }
            }
        }
        Map<Path, Path> moves = new java.util.LinkedHashMap<>();
        for (ResourceLocation id : removed) {
            Path file = fileOf(root, id, "rysquests/shop/");
            if (!Files.isRegularFile(file)) {
                throw new Rejected("rysquests.editor.error.offer_elsewhere");
            }
            moves.put(file, root.resolve("_removed").resolve(root.relativize(file)));
        }
        if (!keys.containsAll(texts.keySet())) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
        if (createPackFile) {
            writePackFile(root);
        }
        if (meta.isPresent()) {
            writeText(bookFile(root, namespace), GSON.toJson(encodeMeta(meta.get())) + "\n");
        }
        for (Map.Entry<Path, JsonElement> file : files.entrySet()) {
            writeText(file.getKey(), GSON.toJson(file.getValue()) + "\n");
        }
        for (Map.Entry<Path, Path> move : moves.entrySet()) {
            Files.createDirectories(move.getValue().getParent());
            Files.move(move.getKey(), move.getValue(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (!texts.isEmpty()) {
            mergeLang(root.resolve("assets").resolve(namespace).resolve("lang").resolve(language + ".json"), texts);
        }
    }

    /** The file of an offer or an achievement by its id, checked so that it stays inside the folder. */
    private static Path fileOf(Path root, ResourceLocation id, String folder) throws Rejected {
        for (String segment : id.getPath().split("/")) {
            if (!FILE_NAME.matcher(segment).matches()) {
                throw new Rejected("rysquests.editor.error.invalid");
            }
        }
        return root.resolve("data").resolve(id.getNamespace()).resolve(folder + id.getPath() + ".json");
    }

    private static Path dataFile(Path root, ResourceLocation file) {
        return root.resolve("data").resolve(file.getNamespace()).resolve(file.getPath());
    }

    /** A resource path may hold {@code ..}, which must not lead out of the pack. */
    private static void checkFolder(String path) throws Rejected {
        if (!path.startsWith("rysquests/chapters/") || !path.endsWith(".json")) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
        for (String segment : path.substring(0, path.length() - ".json".length()).split("/")) {
            if (!FILE_NAME.matcher(segment).matches()) {
                throw new Rejected("rysquests.editor.error.invalid");
            }
        }
    }

    /** Writes what an import made: files by path inside the pack, and strings added to the lang files of each language. */
    public static void writeImported(Path root, String namespace, Map<String, JsonElement> files, Map<String, Map<String, String>> lang, boolean createPackFile) throws IOException {
        if (createPackFile) {
            writePackFile(root);
        }
        for (Map.Entry<String, JsonElement> file : files.entrySet()) {
            writeText(root.resolve(file.getKey()), GSON.toJson(file.getValue()) + "\n");
        }
        for (Map.Entry<String, Map<String, String>> language : lang.entrySet()) {
            mergeLang(root.resolve("assets").resolve(namespace).resolve("lang").resolve(language.getKey() + ".json"), language.getValue());
        }
    }

    /** Puts the order of the rail into the book's settings, leaving the rest of them as they are. */
    public static void saveBook(Path root, String namespace, BookMeta meta, List<String> rail, boolean createPackFile) throws Rejected, IOException {
        if (rail.isEmpty() || rail.stream().anyMatch(id -> ResourceLocation.tryParse(id) == null)) {
            throw new Rejected("rysquests.editor.error.invalid");
        }
        saveMeta(root, namespace, meta.withSections(rail), createPackFile);
    }

    /** Writes the book's settings as they are, in the order of the format document. */
    public static void saveMeta(Path root, String namespace, BookMeta meta, boolean createPackFile) throws IOException {
        if (createPackFile) {
            writePackFile(root);
        }
        writeText(bookFile(root, namespace), GSON.toJson(encodeMeta(meta)) + "\n");
    }

    public static JsonElement encodeMeta(BookMeta meta) {
        return ordered(BookMeta.CODEC.encodeStart(JsonOps.INSTANCE, meta).getOrThrow(), BOOK_ORDER, RULE_ORDER);
    }

    public static Path bookFile(Path root, String namespace) {
        return root.resolve("data").resolve(namespace).resolve("rysquests/book.json");
    }

    /** Writes the baked map beside the book's settings. */
    public static void saveLayout(Path root, String namespace, BookLayout layout, boolean createPackFile) throws IOException {
        if (createPackFile) {
            writePackFile(root);
        }
        writeText(root.resolve("data").resolve(namespace).resolve("rysquests/layout.json"), layoutText(layout));
    }

    /**
     * The layout file, one node or area to a line and sorted by id: a book of thousands of quests stays a file that diffs well,
     * which pretty printing every coordinate on a line of its own would not be.
     */
    public static String layoutText(BookLayout layout) {
        StringBuilder out = new StringBuilder("{\n");
        out.append("  \"form\": ").append(GSON.toJson(layout.form().getSerializedName())).append(",\n");
        out.append("  \"hash\": ").append(GSON.toJson(layout.hash())).append(",\n");
        out.append("  \"nodes\": {");
        appendEntries(out, new java.util.TreeMap<>(layout.nodes()), point -> "[" + point.x() + ", " + point.y() + "]");
        out.append("},\n  \"areas\": {");
        appendEntries(out, new java.util.TreeMap<>(layout.areas()), area -> "[" + area.x() + ", " + area.y() + ", " + area.w() + ", " + area.h() + "]");
        out.append("}");
        BookLayout.Decor decor = layout.decor();
        if (!decor.equals(BookLayout.Decor.EMPTY)) {
            out.append(",\n  \"decor\": {\n    \"regions\": {");
            appendEntries(out, new java.util.TreeMap<>(decor.regions()), EditorFiles::numbers, "      ", "\n    ");
            out.append("},\n    \"labels\": {");
            appendEntries(out, new java.util.TreeMap<>(decor.labels()), EditorFiles::numbers, "      ", "\n    ");
            out.append("},\n    \"wheels\": ");
            appendRows(out, decor.wheels());
            out.append(",\n    \"marks\": ");
            appendRows(out, decor.marks());
            out.append(",\n    \"arms\": ");
            appendRows(out, decor.arms());
            out.append(",\n    \"turns\": ");
            appendRows(out, decor.turns());
            out.append("\n  }");
        }
        out.append("\n}\n");
        return out.toString();
    }

    /** Numbers of the decor, whole ones without a fraction. */
    private static String numbers(List<Float> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            float v = values.get(i);
            out.append(v == Math.rint(v) ? Long.toString((long) v) : Float.toString(v));
        }
        return out.append(']').toString();
    }

    /** One row of numbers to a line. */
    private static void appendRows(StringBuilder out, List<List<Float>> rows) {
        if (rows.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        String separator = "\n";
        for (List<Float> row : rows) {
            out.append(separator).append("      ").append(numbers(row));
            separator = ",\n";
        }
        out.append("\n    ]");
    }

    private static <V> void appendEntries(StringBuilder out, Map<String, V> entries, java.util.function.Function<V, String> value) {
        appendEntries(out, entries, value, "    ", "\n  ");
    }

    private static <V> void appendEntries(StringBuilder out, Map<String, V> entries, java.util.function.Function<V, String> value, String indent, String close) {
        if (entries.isEmpty()) {
            return;
        }
        String separator = "\n";
        for (Map.Entry<String, V> entry : entries.entrySet()) {
            out.append(separator).append(indent).append(GSON.toJson(entry.getKey())).append(": ").append(value.apply(entry.getValue()));
            separator = ",\n";
        }
        out.append(close);
    }

    private static Set<String> keysOf(Optional<Guide> guide, Optional<SectionDef> section) {
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
        section.ifPresent(found -> keys.addAll(found.textKeys()));
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
        return ordered(Guide.CODEC.encodeStart(JsonOps.INSTANCE, guide).getOrThrow());
    }

    /** A section's id comes from its file, so the file does not repeat it. */
    private static JsonElement encode(SectionDef section) {
        JsonObject json = SectionDef.CODEC.encodeStart(JsonOps.INSTANCE, section).getOrThrow().getAsJsonObject();
        json.remove("id");
        return ordered(json);
    }

    /** Codecs do not promise an order of fields, and a file has to diff well, so it is set here. */
    private static JsonElement ordered(JsonElement element) {
        return ordered(element, FIELD_ORDER, FIELD_ORDER);
    }

    private static JsonElement ordered(JsonElement element, List<String> order, List<String> nested) {
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            element.getAsJsonArray().forEach(item -> out.add(ordered(item, nested, nested)));
            return out;
        }
        if (!element.isJsonObject()) {
            return element;
        }
        JsonObject source = element.getAsJsonObject();
        JsonObject out = new JsonObject();
        for (String field : order) {
            if (source.has(field)) {
                out.add(field, ordered(source.get(field), nested, nested));
            }
        }
        new java.util.TreeSet<>(source.keySet()).stream().filter(field -> !order.contains(field)).forEach(field -> out.add(field, ordered(source.get(field), nested, nested)));
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
