package works.repka.rysquests.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.data.BookParser;
import works.repka.rysquests.data.BookValidator;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.integration.ftbq.FtbqFiles;
import works.repka.rysquests.integration.ftbq.FtbqImporter;
import works.repka.rysquests.progress.QuestStatus;

/**
 * The two books of the canvas scenario, read on the client without a server: RepYS from its files with the titles of its lang
 * file, and ATM10 carried over from FTB Quests by the importer. Plus a made up progress for each, like the mockups have.
 */
final class CanvasBooks {
    /** The epoch names of the mockups; the RepYS book does not name its epochs yet. */
    private static final List<String> REPYS_EPOCHS = List.of("Механика", "Индустрия", "Машины", "Энергия и разум", "Магия и жизнь", "Высокие технологии", "Атом и квант",
            "Звёзды", "Творение");

    private CanvasBooks() {
    }

    static QuestBook repys(Path folder, Path lang) throws IOException {
        return repys(folder, lang, null);
    }

    /** @param form the map form the book names, or null for the automatic one */
    static QuestBook repys(Path folder, Path lang, String form) throws IOException {
        Map<String, String> names = new HashMap<>();
        if (Files.isRegularFile(lang)) {
            JsonParser.parseString(Files.readString(lang, StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
        }
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                String path = folder.relativize(file).toString().replace('\\', '/');
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                files.put(ResourceLocation.fromNamespaceAndPath("repys", path.substring(0, path.length() - ".json".length())), json);
            }
        }
        files.values().forEach(json -> prepare(json, names));
        files.forEach((id, json) -> {
            if (id.getPath().endsWith("/_chapter") && json.isJsonObject() && json.getAsJsonObject().has("epoch") && json.getAsJsonObject().get("epoch").getAsInt() == 0) {
                json.getAsJsonObject().addProperty("off_map", true);
            }
            if (id.getPath().equals("book") && json.isJsonObject()) {
                JsonArray epochs = new JsonArray();
                REPYS_EPOCHS.forEach(epochs::add);
                json.getAsJsonObject().add("epochs", epochs);
                if (form != null) {
                    json.getAsJsonObject().addProperty("map_form", form);
                }
            }
        });
        return BookValidator.validate(BookParser.parse(files, JsonOps.INSTANCE), id -> true, JsonOps.INSTANCE).book();
    }

    static QuestBook atm10(Path quests) throws IOException {
        FtbqImporter.Result result = FtbqImporter.convert(FtbqFiles.read(quests), "atm", FtbqImporter.Ids.empty());
        Map<String, String> names = new HashMap<>(result.lang().getOrDefault("en_us", Map.of()));
        names.putAll(result.lang().getOrDefault("ru_ru", Map.of()));
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        result.files().forEach((path, json) -> {
            String[] parts = path.split("/", 4);
            json.remove("draft");
            prepare(json, names);
            files.put(ResourceLocation.fromNamespaceAndPath(parts[1], parts[3].substring(0, parts[3].length() - ".json".length())), json);
        });
        return BookValidator.validate(BookParser.parse(files, JsonOps.INSTANCE), id -> true, JsonOps.INSTANCE).book();
    }

    /** Puts lang texts in place of title keys and drops item components of mods the dev client does not have. */
    private static void prepare(JsonElement element, Map<String, String> names) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            object.remove("components");
            object.remove("nbt");
            for (Map.Entry<String, JsonElement> entry : new ArrayList<>(object.entrySet())) {
                JsonElement value = entry.getValue();
                if ((entry.getKey().equals("title") || entry.getKey().equals("subtitle")) && value.isJsonPrimitive() && names.containsKey(value.getAsString())) {
                    // FTB Quests colours its titles with section signs; the book would show them as they are
                    String text = names.get(value.getAsString()).replaceAll("§.", "");
                    if (text.length() > 4 && text.indexOf(". ") > 0 && text.indexOf(". ") < 4) {
                        text = text.substring(text.indexOf(". ") + 2);
                    }
                    object.add(entry.getKey(), new JsonPrimitive(text));
                } else {
                    prepare(value, names);
                }
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement value = array.get(i);
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && names.containsKey(value.getAsString())) {
                    array.set(i, new JsonPrimitive(names.get(value.getAsString())));
                } else {
                    prepare(value, names);
                }
            }
        }
    }

    // --- made up progress ---

    /** Done quests and the status they give: done, open when everything before is done, else locked. */
    record Progress(Set<ResourceLocation> done, Function<ResourceLocation, QuestStatus> status) {
    }

    static Progress progress(QuestBook book, Set<ResourceLocation> done) {
        return new Progress(done, id -> {
            if (done.contains(id)) {
                return QuestStatus.COMPLETED;
            }
            Quest quest = book.quests().get(id);
            if (quest == null) {
                return QuestStatus.LOCKED;
            }
            for (ResourceLocation r : quest.requires()) {
                if (book.quests().containsKey(r) && !done.contains(r)) {
                    return QuestStatus.LOCKED;
                }
            }
            return QuestStatus.AVAILABLE;
        });
    }

    private static void close(QuestBook book, ResourceLocation quest, Set<ResourceLocation> done) {
        List<ResourceLocation> stack = new ArrayList<>(List.of(quest));
        while (!stack.isEmpty()) {
            Quest q = book.quests().get(stack.removeLast());
            if (q == null) {
                continue;
            }
            for (ResourceLocation r : q.requires()) {
                if (book.quests().containsKey(r) && done.add(r)) {
                    stack.add(r);
                }
            }
        }
    }

    /** As the mockups: epoch I done, the player in "Current" at the electromechanism, three steps into "Oil and plastic". */
    static Set<ResourceLocation> repysProgress(QuestBook book, MapBook map, ResourceLocation here) {
        Set<ResourceLocation> done = new HashSet<>();
        for (MapBook.Q q : map.quests()) {
            if (map.chapters().get(q.chapter()).epoch() == 1 && !q.optional()) {
                done.add(q.id());
                close(book, q.id(), done);
            }
        }
        close(book, here, done);
        map.chapters().stream().filter(c -> c.id().getPath().contains("oil")).findFirst().ifPresent(oil -> {
            List<MapBook.Q> mains = java.util.Arrays.stream(oil.quests()).mapToObj(i -> map.quests().get(i)).filter(q -> !q.optional())
                    .sorted(Comparator.comparingInt(q -> depth(map, q.index(), new HashMap<>()))).limit(3).toList();
            mains.forEach(q -> {
                done.add(q.id());
                close(book, q.id(), done);
            });
        });
        Random random = new Random(3);
        for (int round = 0; round < 4; round++) {
            for (MapBook.Q q : map.quests()) {
                int e = map.chapters().get(q.chapter()).epoch();
                if (q.optional() && !done.contains(q.id()) && e <= 2 && java.util.Arrays.stream(q.requires()).allMatch(r -> done.contains(map.quests().get(r).id()))) {
                    if (random.nextDouble() < (e == 1 ? 0.8 : 0.45)) {
                        done.add(q.id());
                    }
                }
            }
        }
        return done;
    }

    /** As picture 47: the main chapters half done, about a third of the others begun. */
    static Set<ResourceLocation> atmProgress(QuestBook book, MapBook map) {
        Set<ResourceLocation> done = new HashSet<>();
        Random random = new Random(5);
        int firstEpoch = map.chapters().stream().mapToInt(MapBook.Chap::epoch).filter(e -> e > 0).min().orElse(0);
        for (MapBook.Chap chapter : map.chapters()) {
            double share = chapter.epoch() == firstEpoch ? 0.5 : random.nextDouble() < 0.3 ? new double[] {0.15, 0.3, 0.5}[random.nextInt(3)] : 0;
            if (share == 0) {
                continue;
            }
            List<MapBook.Q> mains = java.util.Arrays.stream(chapter.quests()).mapToObj(i -> map.quests().get(i)).filter(q -> !q.optional())
                    .sorted(Comparator.comparingInt(q -> depth(map, q.index(), new HashMap<>()))).toList();
            for (MapBook.Q q : mains.subList(0, Math.max(1, (int) (mains.size() * share)))) {
                done.add(q.id());
                close(book, q.id(), done);
            }
        }
        return done;
    }

    private static int depth(MapBook map, int q, Map<Integer, Integer> memo) {
        Integer known = memo.get(q);
        if (known != null) {
            return known;
        }
        memo.put(q, 0);
        int best = 0;
        for (int r : map.quests().get(q).requires()) {
            best = Math.max(best, depth(map, r, memo) + 1);
        }
        memo.put(q, best);
        return best;
    }

    /** The path to a goal: the goal's undone ancestors in an order that can be walked, the goal last. */
    static List<ResourceLocation> route(QuestBook book, Set<ResourceLocation> done, ResourceLocation goal) {
        List<ResourceLocation> out = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        walk(book, done, goal, seen, out);
        return out;
    }

    private static void walk(QuestBook book, Set<ResourceLocation> done, ResourceLocation id, Set<ResourceLocation> seen, List<ResourceLocation> out) {
        if (!seen.add(id) || done.contains(id)) {
            return;
        }
        Quest quest = book.quests().get(id);
        if (quest == null) {
            return;
        }
        for (ResourceLocation r : quest.requires()) {
            walk(book, done, r, seen, out);
        }
        out.add(id);
    }
}
