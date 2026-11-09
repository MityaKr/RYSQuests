package works.repka.rysquests.integration.ftbq;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Ids;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Result;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Source;

/** File side of the FTB Quests import: reads the FTB folder and writes the generated pack. */
public final class FtbqFiles {
    public static final String IDS_FILE = "ftbq-ids.json";
    private static final String REPORT_FILE = "import-report.md";
    private static final String PACK_META = "{ \"pack\": { \"description\": \"Imported from FTB Quests\", \"pack_format\": 48, \"supported_formats\": [34, 48] } }\n";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private FtbqFiles() {
    }

    public static Source read(Path quests) throws IOException {
        JsonObject data = readSnbt(quests.resolve("data.snbt"));
        List<JsonObject> groups = new ArrayList<>();
        Path groupsFile = quests.resolve("chapter_groups.snbt");
        if (Files.isRegularFile(groupsFile) && readSnbt(groupsFile).get("chapter_groups") != null) {
            readSnbt(groupsFile).getAsJsonArray("chapter_groups").forEach(group -> groups.add(group.getAsJsonObject()));
        }
        List<JsonObject> chapters = new ArrayList<>();
        for (Path file : snbtFiles(quests.resolve("chapters"))) {
            chapters.add(readSnbt(file));
        }
        Map<Long, JsonObject> tables = new HashMap<>();
        for (Path file : snbtFiles(quests.resolve("reward_tables"))) {
            JsonObject table = readSnbt(file);
            tables.put(Long.parseUnsignedLong(table.get("id").getAsString(), 16), table);
        }
        Map<String, JsonObject> lang = new HashMap<>();
        for (Path file : snbtFiles(quests.resolve("lang"))) {
            String name = file.getFileName().toString();
            lang.put(name.substring(0, name.length() - ".snbt".length()).toLowerCase(Locale.ROOT), readSnbt(file));
        }
        return new Source(data, groups, chapters, tables, lang);
    }

    private static JsonObject readSnbt(Path file) throws IOException {
        return Snbt.parse(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<Path> snbtFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.toString().endsWith(".snbt")).sorted().toList();
        }
    }

    public static Ids readIds(Path root) throws IOException {
        Path file = root.resolve(IDS_FILE);
        if (!Files.isRegularFile(file)) {
            return Ids.empty();
        }
        JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        return Ids.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    /** Replaces the previously generated pack; the id map and everything else in the folder stay. */
    public static void write(Path root, Result result) throws IOException {
        Files.createDirectories(root);
        deleteTree(root.resolve("data"));
        deleteTree(root.resolve("assets"));
        Files.writeString(root.resolve("pack.mcmeta"), PACK_META, StandardCharsets.UTF_8);
        for (Map.Entry<String, JsonObject> file : result.files().entrySet()) {
            writeJson(root.resolve(file.getKey()), file.getValue());
        }
        String namespace = result.files().keySet().iterator().next().split("/")[1];
        for (Map.Entry<String, Map<String, String>> locale : result.lang().entrySet()) {
            JsonObject lang = new JsonObject();
            locale.getValue().forEach(lang::addProperty);
            writeJson(root.resolve("assets/" + namespace + "/lang/" + locale.getKey() + ".json"), lang);
        }
        writeJson(root.resolve(IDS_FILE), Ids.CODEC.encodeStart(JsonOps.INSTANCE, result.ids()).getOrThrow());
        writeReport(root.resolve(REPORT_FILE), result);
    }

    private static void writeJson(Path file, JsonElement json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
    }

    private static void writeReport(Path file, Result result) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# FTB Quests import");
        lines.add("");
        lines.add("Chapters: " + result.chapters() + ", quests: " + result.quests() + ", notes: " + result.notes().size());
        lines.add("");
        result.notes().forEach(note -> lines.add("- " + note));
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
