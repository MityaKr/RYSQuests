package works.repka.rysquests.integration.ftbq;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Converts an FTB Quests book to our datapack layout. Pure data work, the file system is in {@link FtbqFiles}. */
public final class FtbqImporter {
    private static final int MAX_SLUG = 48;
    private static final String ENGLISH = "en_us";

    /** Everything read from {@code config/ftbquests/quests}. */
    public record Source(JsonObject data, List<JsonObject> groups, List<JsonObject> chapters,
            Map<Long, JsonObject> tables, Map<String, JsonObject> lang) {
    }

    /** A task or reward of a quest, by position. */
    public record Ref(String quest, int index) {
        static final Codec<Ref> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("quest").forGetter(Ref::quest),
                Codec.INT.fieldOf("index").forGetter(Ref::index)
        ).apply(instance, Ref::new));
    }

    /** FTB hex ids to ours. Kept between imports so that re-importing never changes an id. */
    public record Ids(Map<String, String> quests, Map<String, Ref> tasks, Map<String, Ref> rewards) {
        public static final Codec<Ids> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("quests").forGetter(Ids::quests),
                Codec.unboundedMap(Codec.STRING, Ref.CODEC).fieldOf("tasks").forGetter(Ids::tasks),
                Codec.unboundedMap(Codec.STRING, Ref.CODEC).fieldOf("rewards").forGetter(Ids::rewards)
        ).apply(instance, Ids::new));

        public static Ids empty() {
            return new Ids(new HashMap<>(), new HashMap<>(), new HashMap<>());
        }
    }

    /** @param files JSON by path relative to the pack root; @param lang translations by locale */
    public record Result(Map<String, JsonObject> files, Map<String, Map<String, String>> lang, Ids ids,
            List<String> notes, int chapters, int quests) {
    }

    private final Source source;
    private final String namespace;
    private final Ids previous;
    private final Ids ids = Ids.empty();
    private final Map<String, JsonObject> files = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> lang = new HashMap<>();
    private final List<String> notes = new ArrayList<>();
    private final Set<String> usedIds = new HashSet<>();
    private int questCount;

    private FtbqImporter(Source source, String namespace, Ids previous) {
        this.source = source;
        this.namespace = namespace;
        this.previous = previous;
        source.lang().keySet().forEach(locale -> lang.put(locale, new LinkedHashMap<>()));
    }

    public static Result convert(Source source, String namespace, Ids previous) {
        return new FtbqImporter(source, namespace, previous).run();
    }

    private Result run() {
        List<JsonObject> chapters = orderedChapters();
        usedIds.addAll(previous.quests().values());
        chapters.forEach(this::assignQuestIds);

        JsonArray order = new JsonArray();
        for (JsonObject chapter : chapters) {
            String dir = chapterDir(chapter);
            order.add(namespace + ":" + dir);
            writeChapter(chapter, dir);
        }
        writeBook(order);
        Map<String, Map<String, String>> sortedLang = new HashMap<>(lang);
        return new Result(files, sortedLang, ids, notes, chapters.size(), questCount);
    }

    private List<JsonObject> orderedChapters() {
        Map<String, Integer> groupIndex = new HashMap<>();
        for (int i = 0; i < source.groups().size(); i++) {
            groupIndex.put(string(source.groups().get(i), "id").orElse(""), i + 1);
        }
        return source.chapters().stream()
                .sorted(Comparator.<JsonObject>comparingInt(chapter -> groupIndex.getOrDefault(string(chapter, "group").orElse(""), 0))
                        .thenComparingLong(chapter -> number(chapter, "order_index", 0))
                        .thenComparing(this::chapterDir))
                .toList();
    }

    private String chapterDir(JsonObject chapter) {
        return sanitize(string(chapter, "filename").orElse(string(chapter, "id").orElseThrow()));
    }

    private void assignQuestIds(JsonObject chapter) {
        String dir = chapterDir(chapter);
        for (JsonElement element : array(chapter, "quests")) {
            String hex = string(element.getAsJsonObject(), "id").orElseThrow();
            String known = previous.quests().get(hex);
            ids.quests().put(hex, known != null ? known : newQuestId(dir, hex));
        }
    }

    private String newQuestId(String dir, String hex) {
        String base = slug(translation(ENGLISH, "quest." + hex + ".title").orElse(""));
        if (base.isEmpty()) {
            base = "q_" + hex.toLowerCase(Locale.ROOT);
        }
        String id = namespace + ":" + dir + "/" + base;
        for (int n = 2; !usedIds.add(id); n++) {
            id = namespace + ":" + dir + "/" + base + "_" + n;
        }
        return id;
    }

    private void writeBook(JsonArray order) {
        JsonObject book = new JsonObject();
        book.addProperty("progress_mode", "solo");
        book.addProperty("default_view", "flow");
        book.addProperty("title", "book." + namespace + ".title");
        translate("book." + namespace + ".title", "file.1.title");
        itemId(source.data().get("icon")).ifPresent(icon -> book.addProperty("icon", icon));
        book.add("chapter_order", order);
        files.put("data/" + namespace + "/rysquests/book.json", book);
    }

    private void writeChapter(JsonObject chapter, String dir) {
        String hex = string(chapter, "id").orElseThrow();
        String chapterId = namespace + ":" + dir;
        JsonObject out = new JsonObject();
        out.addProperty("id", chapterId);
        String titleKey = "chapter." + namespace + "." + dir + ".title";
        out.addProperty("title", titleKey);
        translateRequired(titleKey, "chapter." + hex + ".title", dir);
        String subtitleKey = "chapter." + namespace + "." + dir + ".subtitle";
        if (translate(subtitleKey, "chapter." + hex + ".chapter_subtitle")) {
            out.addProperty("subtitle", subtitleKey);
        }
        itemId(chapter.get("icon")).ifPresent(icon -> out.addProperty("icon", icon));
        files.put("data/" + namespace + "/rysquests/chapters/" + dir + "/_chapter.json", out);

        int links = array(chapter, "quest_links").size();
        if (links > 0) {
            notes.add("Chapter " + dir + ": " + links + " quest links not imported (a link only repeats a quest on another page)");
        }
        for (JsonElement quest : array(chapter, "quests")) {
            writeQuest(quest.getAsJsonObject(), chapterId, dir);
        }
    }

    private void writeQuest(JsonObject quest, String chapterId, String dir) {
        String hex = string(quest, "id").orElseThrow();
        String id = ids.quests().get(hex);
        String path = id.substring(id.indexOf(':') + 1);
        String keyBase = "quest." + namespace + "." + path.replace('/', '.');
        JsonObject out = new JsonObject();
        out.addProperty("id", id);
        out.addProperty("chapter", chapterId);
        out.addProperty("title", keyBase + ".title");
        translateRequired(keyBase + ".title", "quest." + hex + ".title", hex);
        if (translate(keyBase + ".subtitle", "quest." + hex + ".quest_subtitle")) {
            out.addProperty("subtitle", keyBase + ".subtitle");
        }
        if (translate(keyBase + ".text", "quest." + hex + ".quest_desc")) {
            out.addProperty("text", keyBase + ".text");
        }
        Optional<String> icon = itemId(quest.get("icon")).or(() -> firstTaskItem(quest));
        icon.ifPresent(item -> out.addProperty("icon", item));

        JsonArray requires = new JsonArray();
        for (JsonElement dependency : array(quest, "dependencies")) {
            String target = ids.quests().get(dependency.getAsString());
            if (target != null) {
                requires.add(target);
            } else {
                notes.add("Quest " + id + ": dependency " + dependency.getAsString() + " is not a quest, skipped");
            }
        }
        out.add("requires", requires);
        out.addProperty("requires_mode", "one_completed".equals(string(quest, "dependency_requirement").orElse("")) ? "any" : "all");

        JsonArray tasks = new JsonArray();
        for (JsonElement task : array(quest, "tasks")) {
            convertTask(task.getAsJsonObject(), id).ifPresent(converted -> {
                ids.tasks().put(string(task.getAsJsonObject(), "id").orElseThrow(), new Ref(id, tasks.size()));
                tasks.add(converted);
            });
        }
        out.add("tasks", tasks);

        JsonArray rewards = new JsonArray();
        for (JsonElement reward : array(quest, "rewards")) {
            convertReward(reward.getAsJsonObject(), id).ifPresent(converted -> {
                ids.rewards().put(string(reward.getAsJsonObject(), "id").orElseThrow(), new Ref(id, rewards.size()));
                rewards.add(converted);
            });
        }
        out.add("rewards", rewards);

        out.addProperty("size", number(quest, "size", 1) >= 2 || decimal(quest, "size") >= 1.5 ? 2 : 1);
        if (bool(quest, "optional")) {
            out.addProperty("optional", true);
        }
        if (bool(quest, "hide_until_deps_complete")) {
            out.addProperty("hidden", true);
        }
        if (bool(quest, "can_repeat")) {
            JsonObject repeatable = new JsonObject();
            repeatable.addProperty("cooldown_minutes", 0);
            out.add("repeatable", repeatable);
        }
        if (tasks.isEmpty()) {
            out.addProperty("page", true);
        }
        String file = path.substring(path.lastIndexOf('/') + 1);
        files.put("data/" + namespace + "/rysquests/chapters/" + dir + "/" + file + ".json", out);
        questCount++;
    }

    private Optional<JsonObject> convertTask(JsonObject task, String questId) {
        String type = string(task, "type").orElse("");
        JsonObject out = new JsonObject();
        switch (type) {
            case "item" -> {
                out.addProperty("type", "rysquests:item");
                JsonObject item = task.getAsJsonObject("item");
                out.addProperty("item", string(item, "id").orElseThrow());
                out.addProperty("count", Math.max(1, number(task, "count", 1)));
                out.addProperty("mode", bool(task, "consume_items") ? "consume" : "observe");
                if (item.has("components")) {
                    JsonObject match = new JsonObject();
                    match.addProperty("strict", !"fuzzy".equals(string(task, "match_components").orElse("")));
                    match.add("components", item.get("components"));
                    out.add("nbt", match);
                }
            }
            case "checkmark" -> out.addProperty("type", "rysquests:checkbox");
            case "stat" -> {
                out.addProperty("type", "rysquests:stat");
                out.addProperty("stat", "minecraft:custom");
                out.addProperty("target", string(task, "stat").orElseThrow());
                out.addProperty("count", Math.max(1, number(task, "value", 1)));
            }
            case "kill" -> {
                out.addProperty("type", "rysquests:kill");
                out.addProperty("entity", string(task, "entity").orElseThrow());
                out.addProperty("count", Math.max(1, number(task, "value", 1)));
            }
            case "structure" -> {
                out.addProperty("type", "rysquests:visit");
                out.addProperty("structure", string(task, "structure").orElseThrow());
            }
            case "dimension" -> {
                out.addProperty("type", "rysquests:visit");
                out.addProperty("dimension", string(task, "dimension").orElseThrow());
            }
            default -> {
                notes.add("Quest " + questId + ": task type " + type + " has no equivalent, skipped");
                return Optional.empty();
            }
        }
        return Optional.of(out);
    }

    private Optional<JsonObject> convertReward(JsonObject reward, String questId) {
        String type = string(reward, "type").orElse("item");
        return switch (type) {
            case "item" -> Optional.of(itemReward(reward));
            case "xp" -> Optional.of(amount("points", number(reward, "xp", 1)));
            case "xp_levels" -> Optional.of(amount("levels", number(reward, "xp_levels", 1)));
            case "random", "choice" -> tableReward(reward, type, questId);
            default -> {
                notes.add("Quest " + questId + ": reward type " + type + " has no equivalent, skipped");
                yield Optional.empty();
            }
        };
    }

    private JsonObject itemReward(JsonObject reward) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "rysquests:item");
        JsonObject item = reward.getAsJsonObject("item");
        out.addProperty("item", string(item, "id").orElseThrow());
        out.addProperty("count", Math.max(1, number(reward, "count", 1) * Math.max(1, number(item, "count", 1))));
        if (item.has("components")) {
            out.add("components", item.get("components"));
        }
        return out;
    }

    private static JsonObject amount(String field, long value) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "rysquests:xp");
        out.addProperty(field, Math.max(1, value));
        return out;
    }

    private Optional<JsonObject> tableReward(JsonObject reward, String type, String questId) {
        JsonObject table = source.tables().get(number(reward, "table_id", 0));
        if (table == null) {
            notes.add("Quest " + questId + ": reward table " + number(reward, "table_id", 0) + " not found, skipped");
            return Optional.empty();
        }
        JsonArray of = new JsonArray();
        JsonArray weights = new JsonArray();
        for (JsonElement entry : array(table, "rewards")) {
            JsonObject item = entry.getAsJsonObject();
            convertReward(item, questId).ifPresent(converted -> {
                of.add(converted);
                weights.add(Math.max(1, number(item, "weight", 1)));
            });
        }
        JsonObject out = new JsonObject();
        out.addProperty("type", "rysquests:" + type);
        out.add("of", of);
        if (type.equals("random")) {
            out.add("weights", weights);
        }
        return Optional.of(out);
    }

    private Optional<String> firstTaskItem(JsonObject quest) {
        for (JsonElement task : array(quest, "tasks")) {
            if (task.getAsJsonObject().has("item")) {
                return itemId(task.getAsJsonObject().get("item"));
            }
        }
        return Optional.empty();
    }

    // --- translations ---

    /** Copies a text to every locale that has it. @return whether any locale had it */
    private boolean translate(String ourKey, String ftbKey) {
        boolean found = false;
        for (String locale : source.lang().keySet()) {
            Optional<String> text = translation(locale, ftbKey);
            if (text.isPresent()) {
                lang.get(locale).put(ourKey, text.get());
                found = true;
            }
        }
        return found;
    }

    private void translateRequired(String ourKey, String ftbKey, String fallback) {
        if (!translate(ourKey, ftbKey)) {
            lang.computeIfAbsent(ENGLISH, key -> new LinkedHashMap<>()).put(ourKey, fallback);
        }
    }

    private Optional<String> translation(String locale, String key) {
        JsonObject table = source.lang().get(locale);
        JsonElement value = table == null ? null : table.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value.isJsonArray()) {
            List<String> lines = new ArrayList<>();
            value.getAsJsonArray().forEach(line -> lines.add(line.getAsString()));
            return Optional.of(String.join("\n", lines));
        }
        return Optional.of(value.getAsString());
    }

    // --- JSON helpers ---

    private static Optional<String> string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value instanceof JsonPrimitive primitive ? Optional.of(primitive.getAsString()).filter(text -> !text.isEmpty()) : Optional.empty();
    }

    private static long number(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        return value instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsLong() : fallback;
    }

    private static double decimal(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsDouble() : 0;
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.get(key) instanceof JsonArray array ? array : new JsonArray();
    }

    private static Optional<String> itemId(JsonElement icon) {
        if (icon instanceof JsonObject object) {
            return string(object, "id");
        }
        return icon instanceof JsonPrimitive primitive ? Optional.of(primitive.getAsString()).filter(text -> !text.isEmpty()) : Optional.empty();
    }

    private static String sanitize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    /** A readable id from a title: formatting codes dropped, Latin letters and digits kept. */
    private static String slug(String title) {
        String plain = title.replaceAll("[&§][0-9a-fk-or]", "").toLowerCase(Locale.ROOT);
        String slug = plain.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return slug.length() > MAX_SLUG ? slug.substring(0, MAX_SLUG).replaceAll("_+$", "") : slug;
    }
}
