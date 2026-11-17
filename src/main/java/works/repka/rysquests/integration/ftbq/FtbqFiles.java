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

    /**
     * A file of the FTB Quests book that could not be read.
     *
     * @param file the path inside the {@code quests} folder, with forward slashes, such as {@code chapters/mekanism.snbt}
     * @param line the line where reading stopped, from 1, or 0 when it is not known
     * @param reason what was wrong there
     * @param skippable whether the import can go on without the file; {@code data.snbt} is the book itself and cannot be left out
     */
    public record Failure(String file, int line, String reason, boolean skippable) {
        public static final com.mojang.serialization.Codec<Failure> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                com.mojang.serialization.Codec.STRING.fieldOf("file").forGetter(Failure::file),
                com.mojang.serialization.Codec.INT.fieldOf("line").forGetter(Failure::line),
                com.mojang.serialization.Codec.STRING.fieldOf("reason").forGetter(Failure::reason),
                com.mojang.serialization.Codec.BOOL.fieldOf("skippable").forGetter(Failure::skippable)
        ).apply(instance, Failure::new));
    }

    /**
     * What reading found. The import goes on only without failures; a file is left out only when the author said so.
     *
     * @param chaptersRead chapter files that were read
     * @param chaptersTotal chapter files there are, left out ones included
     */
    public record Read(Source source, List<Failure> failures, int chaptersRead, int chaptersTotal) {
    }

    /** Reads the whole book; the first file that cannot be read stops it. */
    public static Source read(Path quests) throws IOException {
        Read read = read(quests, java.util.Set.of());
        if (!read.failures().isEmpty()) {
            Failure first = read.failures().getFirst();
            throw new IOException(first.file() + ":" + first.line() + ": " + first.reason());
        }
        return read.source();
    }

    /**
     * Reads every file it can, so that a broken one is named with its line instead of stopping everything at once.
     *
     * @param skip files to leave out, as {@link Failure#file()} names them
     */
    public static Read read(Path quests, java.util.Set<String> skip) throws IOException {
        List<Failure> failures = new ArrayList<>();
        JsonObject data = readSnbt(quests, quests.resolve("data.snbt"), false, failures);
        List<JsonObject> groups = new ArrayList<>();
        Path groupsFile = quests.resolve("chapter_groups.snbt");
        if (Files.isRegularFile(groupsFile) && !skip.contains(relative(quests, groupsFile))) {
            JsonObject read = readSnbt(quests, groupsFile, true, failures);
            if (read != null && read.get("chapter_groups") instanceof com.google.gson.JsonArray array) {
                array.forEach(group -> groups.add(group.getAsJsonObject()));
            }
        }
        List<JsonObject> chapters = new ArrayList<>();
        List<Path> chapterFiles = snbtFiles(quests.resolve("chapters"));
        for (Path file : chapterFiles) {
            if (!skip.contains(relative(quests, file))) {
                JsonObject chapter = readSnbt(quests, file, true, failures);
                if (chapter != null) {
                    chapters.add(chapter);
                }
            }
        }
        Map<Long, JsonObject> tables = new HashMap<>();
        for (Path file : snbtFiles(quests.resolve("reward_tables"))) {
            if (skip.contains(relative(quests, file))) {
                continue;
            }
            JsonObject table = readSnbt(quests, file, true, failures);
            if (table != null) {
                try {
                    tables.put(Long.parseUnsignedLong(table.get("id").getAsString(), 16), table);
                } catch (RuntimeException e) {
                    failures.add(new Failure(relative(quests, file), 0, "no id", true));
                }
            }
        }
        Map<String, JsonObject> lang = new HashMap<>();
        for (Path file : snbtFiles(quests.resolve("lang"))) {
            if (skip.contains(relative(quests, file))) {
                continue;
            }
            String name = file.getFileName().toString();
            JsonObject texts = readSnbt(quests, file, true, failures);
            if (texts != null) {
                lang.put(name.substring(0, name.length() - ".snbt".length()).toLowerCase(Locale.ROOT), texts);
            }
        }
        return new Read(new Source(data == null ? new JsonObject() : data, groups, chapters, tables, lang), List.copyOf(failures), chapters.size(), chapterFiles.size());
    }

    /** @return the file, or null when it could not be read, which is then added to the failures */
    private static JsonObject readSnbt(Path quests, Path file, boolean skippable, List<Failure> failures) {
        String name = relative(quests, file);
        try {
            JsonElement parsed = Snbt.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                failures.add(new Failure(name, 1, "not an object", skippable));
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (Snbt.SyntaxError e) {
            failures.add(new Failure(name, e.line(), e.reason(), skippable));
        } catch (IOException | RuntimeException e) {
            failures.add(new Failure(name, 0, e instanceof java.nio.file.NoSuchFileException ? "missing" : String.valueOf(e.getMessage()), skippable));
        }
        return null;
    }

    static String relative(Path quests, Path file) {
        return quests.relativize(file).toString().replace('\\', '/');
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
        for (ImportReport.Manual manual : result.report().manual()) {
            lines.add("## " + manual.kind() + ": " + manual.count() + " (" + String.join(", ", manual.types()) + ")" + (manual.hidden() ? ", hidden from players" : ""));
            manual.quests().forEach(quest -> lines.add("- " + quest));
            lines.add("");
        }
        result.notes().forEach(note -> lines.add("- " + note));
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    public static String reportFileName() {
        return REPORT_FILE;
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
