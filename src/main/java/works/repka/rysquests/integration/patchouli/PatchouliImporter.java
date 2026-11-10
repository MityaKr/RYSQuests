package works.repka.rysquests.integration.patchouli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Turns a Patchouli book into guides and structures. Only reads, and knows nothing about the game, so it can be tested. */
public final class PatchouliImporter {
    private static final String BASE_LANGUAGE = "en_us";

    /**
     * @param files JSON by path inside a pack
     * @param lang strings by language
     */
    public record Result(Map<String, JsonElement> files, Map<String, Map<String, String>> lang, int guides, int pages, int structures, List<String> notes) {
    }

    private record Entry(String id, String category, int order, Map<String, JsonObject> byLanguage) {
    }

    private final String book;
    private final String namespace;
    private final Map<String, JsonElement> files = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> lang = new LinkedHashMap<>();
    private final Set<String> notes = new LinkedHashSet<>();
    private int pages;
    private int structures;

    private PatchouliImporter(String book, String namespace) {
        this.book = book;
        this.namespace = namespace;
    }

    public static Result convert(Path folder, String book, String namespace) throws IOException {
        return new PatchouliImporter(book, namespace).run(folder);
    }

    private Result run(Path folder) throws IOException {
        List<String> languages = languages(folder);
        if (languages.isEmpty()) {
            throw new IOException("No book at " + folder);
        }
        String base = languages.contains(BASE_LANGUAGE) ? BASE_LANGUAGE : languages.getFirst();
        Map<String, Map<String, JsonObject>> categories = new LinkedHashMap<>();
        for (String language : languages) {
            read(folder.resolve(language).resolve("categories")).forEach((id, json) -> categories.computeIfAbsent(id, key -> new HashMap<>()).put(language, json));
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String language : languages) {
            read(folder.resolve(language).resolve("entries")).forEach((id, json) -> {
                String category = json.has("category") ? localId(json.get("category").getAsString()) : "";
                int order = json.has("sortnum") ? json.get("sortnum").getAsInt() : 0;
                entries.computeIfAbsent(id, key -> new Entry(id, category, order, new HashMap<>())).byLanguage().put(language, json);
            });
        }
        int guides = 0;
        List<String> order = new ArrayList<>(categories.keySet());
        order.sort(Comparator.comparingInt((String id) -> sortnum(categories.get(id))).thenComparing(id -> id));
        for (String category : order) {
            List<Entry> inside = entries.values().stream().filter(entry -> entry.category().equals(category))
                    .sorted(Comparator.comparingInt(Entry::order).thenComparing(Entry::id)).toList();
            if (!inside.isEmpty()) {
                writeGuide(category, categories.get(category), inside, base);
                guides++;
            }
        }
        entries.values().stream().filter(entry -> !categories.containsKey(entry.category())).forEach(entry -> notes.add("Entry " + entry.id() + ": unknown category " + entry.category() + ", skipped"));
        return new Result(files, lang, guides, pages, structures, List.copyOf(notes));
    }

    private static int sortnum(Map<String, JsonObject> category) {
        return category.values().stream().filter(json -> json.has("sortnum")).findFirst().map(json -> json.get("sortnum").getAsInt()).orElse(0);
    }

    private static List<String> languages(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(folder)) {
            return paths.filter(path -> Files.isDirectory(path.resolve("entries"))).map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static Map<String, JsonObject> read(Path folder) throws IOException {
        Map<String, JsonObject> found = new LinkedHashMap<>();
        if (!Files.isDirectory(folder)) {
            return found;
        }
        List<Path> files;
        try (Stream<Path> paths = Files.walk(folder)) {
            files = paths.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
        for (Path file : files) {
            String relative = folder.relativize(file).toString().replace('\\', '/');
            found.put(slug(relative.substring(0, relative.length() - ".json".length())), JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        }
        return found;
    }

    private static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static String localId(String id) {
        return slug(id.substring(id.indexOf(':') + 1));
    }

    private void put(String language, String key, String value) {
        lang.computeIfAbsent(language, id -> new LinkedHashMap<>()).put(key, value);
    }

    private void writeGuide(String category, Map<String, JsonObject> names, List<Entry> entries, String base) {
        String path = book + "/" + category;
        String prefix = "guide." + namespace + "." + book + "." + category;
        names.forEach((language, json) -> {
            if (json.has("name")) {
                put(language, prefix + ".title", json.get("name").getAsString());
            }
        });
        JsonObject guide = new JsonObject();
        guide.addProperty("id", namespace + ":" + path);
        guide.addProperty("title", prefix + ".title");
        JsonObject first = names.getOrDefault(base, names.values().iterator().next());
        if (first.has("icon") && isPlainId(first.get("icon").getAsString())) {
            guide.addProperty("icon", first.get("icon").getAsString());
        }
        JsonArray list = new JsonArray();
        for (Entry entry : entries) {
            JsonObject page = new JsonObject();
            String key = prefix + "." + entry.id();
            page.addProperty("id", entry.id());
            page.addProperty("title", key + ".title");
            page.addProperty("text", key + ".text");
            list.add(page);
            pages++;
            for (Map.Entry<String, JsonObject> translated : entry.byLanguage().entrySet()) {
                JsonObject json = translated.getValue();
                if (json.has("name")) {
                    put(translated.getKey(), key + ".title", json.get("name").getAsString());
                }
                put(translated.getKey(), key + ".text", text(entry, json, translated.getKey().equals(base) || !entry.byLanguage().containsKey(base)));
            }
        }
        guide.add("pages", list);
        files.put("data/" + namespace + "/rysquests/guides/" + path + ".json", guide);
    }

    private static boolean isPlainId(String icon) {
        return icon.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    /** Pages of an entry become one page of text, with a structure wherever a multiblock stood. */
    private String text(Entry entry, JsonObject json, boolean isBase) {
        List<String> parts = new ArrayList<>();
        int multiblock = 0;
        for (JsonElement element : json.getAsJsonArray("pages")) {
            JsonObject page = element.getAsJsonObject();
            String type = page.has("type") ? localId(page.get("type").getAsString()) : "";
            String title = page.has("title") ? "**" + page.get("title").getAsString() + "**\n" : "";
            String body = page.has("text") ? markup(page.get("text").getAsString()) : "";
            switch (type) {
                case "text" -> parts.add(title + body);
                case "spotlight" -> parts.add(title + "[[item:" + page.get("item").getAsString() + "]]\n" + body);
                case "multiblock" -> {
                    if (page.has("multiblock")) {
                        multiblock++;
                        String id = book + "/" + entry.id() + (multiblock > 1 ? "_" + multiblock : "");
                        String caption = page.has("name") ? "|" + page.get("name").getAsString() : "";
                        parts.add(title + "[[structure:" + namespace + ":" + id + caption + "]]" + (body.isEmpty() ? "" : "\n" + body));
                        if (isBase) {
                            JsonObject structure = new JsonObject();
                            JsonObject source = page.getAsJsonObject("multiblock");
                            structure.add("mapping", source.get("mapping"));
                            structure.add("pattern", source.get("pattern"));
                            files.put("data/" + namespace + "/rysquests/structures/" + id + ".json", structure);
                            structures++;
                        }
                    } else if (isBase) {
                        notes.add("Entry " + entry.id() + ": a multiblock registered by id is not in the book's files, skipped");
                    }
                }
                default -> {
                    if (isBase) {
                        notes.add("Entry " + entry.id() + ": page type " + type + " has no equivalent, skipped");
                    }
                }
            }
        }
        return String.join("\n\n", parts).strip();
    }

    /** Patchouli's formatting in our markup. What has no equivalent, such as colors, is dropped and its text stays. */
    public static String markup(String text) {
        return text.replace("$(br2)", "\n\n").replace("$(br)", "\n").replace("$(li)", "\n- ")
                .replaceAll("\\$\\(l\\)(.*?)\\$\\(\\)", "**$1**")
                .replaceAll("\\$\\([^)]*\\)", "")
                .strip();
    }
}
