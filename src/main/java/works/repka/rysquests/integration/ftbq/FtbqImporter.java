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

    /** @param files JSON by path relative to the pack root; @param lang translations by locale; @param report the report by kind */
    public record Result(Map<String, JsonObject> files, Map<String, Map<String, String>> lang, Ids ids,
            List<String> notes, int chapters, int quests, ImportReport report) {
    }

    private final Source source;
    private final String namespace;
    private final Ids previous;
    private final Ids ids = Ids.empty();
    private final Map<String, JsonObject> files = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> lang = new HashMap<>();
    private final List<String> notes = new ArrayList<>();
    /** Per kind of task or reward ("task item", "reward command"): how many were carried over and how many skipped. */
    private final java.util.TreeMap<String, int[]> counts = new java.util.TreeMap<>();

    /** Inside a reward table: its entries are not counted as rewards of their own. */
    private int nested;
    private final java.util.Set<Long> notedTables = new java.util.HashSet<>();

    private void count(String kind, boolean carried) {
        counts.computeIfAbsent(kind, key -> new int[2])[carried ? 0 : 1]++;
    }

    /** What waits for the author's hand, by kind: the quests, and the FTB Quests types behind them. */
    private final Map<String, java.util.TreeSet<String>> manualQuests = new java.util.TreeMap<>();
    private final Map<String, java.util.TreeSet<String>> manualTypes = new java.util.TreeMap<>();
    private final Map<String, Integer> manualCounts = new java.util.TreeMap<>();
    private final Set<String> drafts = new java.util.TreeSet<>();
    private int optionalQuests;
    private int taskCount;
    private int rewardCount;
    private int dependencyCount;

    private void manual(String kind, String quest, String type) {
        manualQuests.computeIfAbsent(kind, key -> new java.util.TreeSet<>()).add(quest);
        manualTypes.computeIfAbsent(kind, key -> new java.util.TreeSet<>()).add(type);
        manualCounts.merge(kind, 1, Integer::sum);
    }

    private ImportReport report(int chapters) {
        List<ImportReport.Manual> manual = new ArrayList<>();
        manualQuests.forEach((kind, quests) -> manual.add(new ImportReport.Manual(kind, manualCounts.get(kind), List.copyOf(manualTypes.get(kind)),
                !kind.equals(ImportReport.LOOT_EMPTY_WEIGHT), List.copyOf(quests))));
        List<String> languages = source.lang().keySet().stream().sorted().toList();
        int sections = (int) source.groups().stream().filter(group -> string(group, "id").isPresent()).count();
        return new ImportReport(chapters, questCount, optionalQuests, taskCount, rewardCount, languages, dependencyCount, sections, chapters, manual, drafts.size(), 0, List.of(), "");
    }

    /** One line per kind at the head of the report, before the notes on single quests. */
    private List<String> summary() {
        List<String> lines = new ArrayList<>();
        counts.forEach((kind, n) -> lines.add("Summary: " + kind + ": " + n[0] + " carried over" + (n[1] > 0 ? ", " + n[1] + " skipped" : "")));
        return lines;
    }
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

    /** A chapter that could not be converted, named by its file, so that the import can go on without it. */
    public static final class ChapterError extends RuntimeException {
        private final String file;

        ChapterError(String file, RuntimeException cause) {
            super(file + ": " + cause.getMessage(), cause);
            this.file = file;
        }

        /** As {@link FtbqFiles.Failure#file()} names it. */
        public String file() {
            return file;
        }
    }

    private Result run() {
        List<JsonObject> chapters = orderedChapters();
        usedIds.addAll(previous.quests().values());
        chapters.forEach(chapter -> guarded(chapter, () -> assignQuestIds(chapter)));

        JsonArray order = new JsonArray();
        for (JsonObject chapter : chapters) {
            guarded(chapter, () -> {
                String dir = chapterDir(chapter);
                order.add(namespace + ":" + dir);
                writeChapter(chapter, dir);
            });
        }
        writeBook(order);
        notes.addAll(0, summary());
        Map<String, Map<String, String>> sortedLang = new HashMap<>(lang);
        return new Result(files, sortedLang, ids, notes, chapters.size(), questCount, report(chapters.size()));
    }

    private void guarded(JsonObject chapter, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            throw new ChapterError("chapters/" + string(chapter, "filename").orElse(string(chapter, "id").orElse("?")) + ".snbt", e);
        }
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
        // Chapter groups become the sections of the book. They are marked as the import's, so that the automatic map form
        // does not take them for the author's epochs (plan, section 5).
        JsonArray epochs = new JsonArray();
        for (int i = 0; i < source.groups().size(); i++) {
            String hex = string(source.groups().get(i), "id").orElse("");
            String key = "book." + namespace + ".epoch." + (i + 1);
            translateRequired(key, "chapter_group." + hex + ".title", hex);
            JsonObject epoch = new JsonObject();
            epoch.addProperty("title", key);
            epoch.addProperty("imported", true);
            epochs.add(epoch);
        }
        if (!epochs.isEmpty()) {
            book.add("epochs", epochs);
        }
        // A carried over book stays closed to players until an operator has looked at it and opened it.
        book.addProperty("published", false);
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
        int epoch = source.groups().stream().map(group -> string(group, "id").orElse("")).toList().indexOf(string(chapter, "group").orElse("")) + 1;
        if (epoch > 0) {
            out.addProperty("epoch", epoch);
        }
        // A quest link repeats a quest of another chapter on this chapter's page; its place is laid out anew.
        JsonArray links = new JsonArray();
        for (JsonElement link : array(chapter, "quest_links")) {
            String linked = string(link.getAsJsonObject(), "linked_quest").orElse("");
            String target = ids.quests().get(linked);
            if (target == null) {
                notes.add("Chapter " + dir + ": quest link to " + linked + " points at no quest, skipped");
            } else if (!links.contains(new JsonPrimitive(target))) {
                links.add(target);
            }
        }
        if (!links.isEmpty()) {
            out.add("links", links);
        }
        files.put("data/" + namespace + "/rysquests/chapters/" + dir + "/_chapter.json", out);
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
                dependencyCount++;
            } else {
                notes.add("Quest " + id + ": dependency " + dependency.getAsString() + " is not a quest, skipped");
            }
        }
        out.add("requires", requires);
        out.addProperty("requires_mode", "one_completed".equals(string(quest, "dependency_requirement").orElse("")) ? "any" : "all");

        JsonArray tasks = new JsonArray();
        int stageTasks = 0;
        for (JsonElement task : array(quest, "tasks")) {
            JsonObject source = task.getAsJsonObject();
            if ("gamestage".equals(string(source, "type").orElse(""))) {
                // A stage task waits for a stage: in this book that is the quest's own stage_required, and a quest whose
                // only task it was completes as soon as the stage opens it.
                String stage = string(source, "stage").orElse("");
                if (stage.isEmpty()) {
                    count("task gamestage", false);
                    notes.add("Quest " + id + ": stage task without a stage, skipped");
                    continue;
                }
                stageTasks++;
                if (bool(source, "team_stage")) {
                    notes.add("Quest " + id + ": stage " + stage + " was a team stage in FTB Quests, here each player's own stage is checked");
                }
                if (!out.has("stage_required")) {
                    out.addProperty("stage_required", stage);
                    count("task gamestage", true);
                } else {
                    count("task gamestage", false);
                    notes.add("Quest " + id + ": a second stage task " + stage + " was left out, the quest waits for one stage only");
                }
                continue;
            }
            // A task the book cannot hold stands in as a checkbox, and the quest becomes a draft that players do not see until
            // the author has looked at it (plan, section 5): it neither completes by itself nor pays out for a click meanwhile.
            JsonObject converted = convertTask(source, id).orElseGet(() -> {
                manual(ImportReport.UNCONVERTED_TASK, id, string(source, "type").orElse("?"));
                drafts.add(id);
                JsonObject standIn = new JsonObject();
                standIn.addProperty("type", "rysquests:checkbox");
                standIn.addProperty("title", "rysquests.import.unconverted_task");
                return standIn;
            });
            ids.tasks().put(string(source, "id").orElseThrow(), new Ref(id, tasks.size()));
            tasks.add(converted);
        }
        taskCount += tasks.size();
        out.add("tasks", tasks);

        JsonArray rewards = new JsonArray();
        for (JsonElement reward : array(quest, "rewards")) {
            Optional<JsonObject> carried = convertReward(reward.getAsJsonObject(), id);
            if (carried.isEmpty()) {
                // The quest comes without the reward, as a draft, so that players do not take it for the whole quest.
                manual(ImportReport.DROPPED_REWARD, id, string(reward.getAsJsonObject(), "type").orElse("item"));
                drafts.add(id);
            }
            carried.ifPresent(converted -> {
                ids.rewards().put(string(reward.getAsJsonObject(), "id").orElseThrow(), new Ref(id, rewards.size()));
                if (bool(reward.getAsJsonObject(), "team_reward")) {
                    converted.addProperty("team_mode", "once");
                }
                rewards.add(converted);
            });
        }
        rewardCount += rewards.size();
        out.add("rewards", rewards);

        // FTB Quests' own place of the quest is kept, not pinned: our map lays the quest out itself (plan, section 5).
        if (quest.has("x") || quest.has("y")) {
            JsonObject layout = new JsonObject();
            layout.addProperty("x", (int) Math.round(decimal(quest, "x") * works.repka.rysquests.data.Layout.FTB_CELL));
            layout.addProperty("y", (int) Math.round(decimal(quest, "y") * works.repka.rysquests.data.Layout.FTB_CELL));
            out.add("layout", layout);
        }
        if (number(quest, "size", 1) >= 2 || decimal(quest, "size") >= 1.5) {
            out.addProperty("milestone", true);
        }
        if (bool(quest, "optional")) {
            out.addProperty("optional", true);
            optionalQuests++;
        }
        if (bool(quest, "hide_until_deps_complete")) {
            out.addProperty("hidden", true);
        }
        if (bool(quest, "can_repeat")) {
            JsonObject repeatable = new JsonObject();
            repeatable.addProperty("cooldown_minutes", 0);
            out.add("repeatable", repeatable);
        }
        if (tasks.isEmpty() && stageTasks == 0) {
            out.addProperty("page", true);
        }
        if (drafts.contains(id)) {
            out.addProperty("draft", true);
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
                // An empty stack is written as {} or left out, and books from 1.20 may still hold a plain string.
                Optional<String> itemId = itemId(task.get("item"));
                if (itemId.isEmpty()) {
                    count("task item", false);
                    notes.add("Quest " + questId + ": item task without an item, skipped");
                    return Optional.empty();
                }
                out.addProperty("type", "rysquests:item");
                out.addProperty("item", itemId.get());
                out.addProperty("count", Math.max(1, number(task, "count", 1)));
                out.addProperty("mode", bool(task, "only_from_crafting") ? "craft" : bool(task, "consume_items") ? "consume" : "observe");
                JsonObject item = task.get("item") instanceof JsonObject object ? object : new JsonObject();
                if (item.has("components")) {
                    JsonObject match = new JsonObject();
                    match.addProperty("strict", !"fuzzy".equals(string(task, "match_components").orElse("")));
                    match.add("components", item.get("components"));
                    out.add("nbt", match);
                }
            }
            case "checkmark" -> out.addProperty("type", "rysquests:checkbox");
            case "xp" -> {
                // FTB Quests hands experience in with the submit button.
                out.addProperty("type", "rysquests:xp");
                out.addProperty(bool(task, "points") ? "points" : "levels", Math.max(1, number(task, "value", 1)));
                out.addProperty("consume", true);
            }
            case "stat" -> {
                out.addProperty("type", "rysquests:stat");
                out.addProperty("stat", "minecraft:custom");
                out.addProperty("target", string(task, "stat").orElseThrow());
                out.addProperty("count", Math.max(1, number(task, "value", 1)));
            }
            case "kill" -> {
                // FTB Quests checks the tag instead of the entity when there is one: our task knows single entities only.
                if (!string(task, "entityTypeTag").orElse("").isEmpty()) {
                    count("task kill", false);
                    notes.add("Quest " + questId + ": kill task for the tag " + string(task, "entityTypeTag").orElse("") + ", which the book cannot count, skipped");
                    return Optional.empty();
                }
                if (!string(task, "custom_name").orElse("").isEmpty() || task.has("nbt_filter")) {
                    notes.add("Quest " + questId + ": kill task filters by name or data, any " + string(task, "entity").orElse("") + " counts here");
                }
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
            case "advancement" -> {
                out.addProperty("type", "rysquests:advancement");
                out.addProperty("advancement", string(task, "advancement").orElseThrow());
                if (!string(task, "criterion").orElse("").isEmpty()) {
                    notes.add("Quest " + questId + ": advancement task asks for criterion " + string(task, "criterion").orElse("") + ", the whole advancement is checked instead");
                }
            }
            case "fluid" -> {
                // 2101 writes {id, amount}; older books a string and amount beside it. FTB Quests hands fluids in.
                JsonElement fluid = task.get("fluid");
                String id = fluid != null && fluid.isJsonObject() ? string(fluid.getAsJsonObject(), "id").orElse("") : string(task, "fluid").orElse("");
                long amount = fluid != null && fluid.isJsonObject() ? number(fluid.getAsJsonObject(), "amount", 1000) : number(task, "amount", 1000);
                if (id.isEmpty()) {
                    count("task fluid", false);
                    notes.add("Quest " + questId + ": fluid task without a fluid, skipped");
                    return Optional.empty();
                }
                out.addProperty("type", "rysquests:fluid");
                out.addProperty("fluid", id);
                out.addProperty("amount", amount > 0 ? amount : 1000);
                out.addProperty("mode", "consume");
            }
            case "biome" -> {
                out.addProperty("type", "rysquests:visit");
                out.addProperty("biome", string(task, "biome").orElseThrow());
            }
            case "location" -> {
                // The same box of blocks as FTB Quests checks: [position, position + size).
                JsonArray position = task.getAsJsonArray("position");
                JsonArray size = task.getAsJsonArray("size");
                if (position == null || position.size() < 3) {
                    count("task location", false);
                    notes.add("Quest " + questId + ": location task without a position, skipped");
                    return Optional.empty();
                }
                JsonArray corner = new JsonArray();
                JsonArray box = new JsonArray();
                for (int i = 0; i < 3; i++) {
                    corner.add(position.get(i).getAsInt());
                    box.add(size != null && size.size() > i ? Math.max(1, size.get(i).getAsInt()) : 1);
                }
                out.addProperty("type", "rysquests:visit");
                out.add("pos", corner);
                out.add("size", box);
                if (!bool(task, "ignore_dimension")) {
                    out.addProperty("dimension", string(task, "dimension").orElse("minecraft:overworld"));
                }
            }
            default -> {
                count("task " + type, false);
                notes.add("Quest " + questId + ": task type " + type + " has no equivalent, skipped");
                return Optional.empty();
            }
        }
        count("task " + type, true);
        return Optional.of(out);
    }

    private Optional<JsonObject> convertReward(JsonObject reward, String questId) {
        String type = string(reward, "type").orElse("item");
        Optional<JsonObject> converted = switch (type) {
            case "item" -> itemReward(reward, questId);
            case "xp" -> Optional.of(amount("points", number(reward, "xp", 1)));
            case "xp_levels" -> Optional.of(amount("levels", number(reward, "xp_levels", 1)));
            case "random", "choice" -> tableReward(reward, type, questId);
            // A loot reward rolls its table once, as a random reward does.
            case "loot" -> tableReward(reward, "random", questId);
            case "command" -> {
                // Kept as written: the book fills FTB Quests' placeholders when the reward is claimed (see CommandPlaceholders).
                // The rights stay as FTB Quests had them: permission_level, or 2 for the old elevate_perms.
                JsonObject out = new JsonObject();
                out.addProperty("type", "rysquests:command");
                out.addProperty("command", string(reward, "command").orElse(""));
                int level = bool(reward, "elevate_perms") ? 2 : (int) Math.max(0, Math.min(4, number(reward, "permission_level", 0)));
                if (level > 0) {
                    out.addProperty("as_server", true);
                    out.addProperty("level", level);
                }
                yield Optional.of(out);
            }
            case "gamestage" -> {
                if (bool(reward, "remove")) {
                    notes.add("Quest " + questId + ": reward takes stage " + string(reward, "stage").orElse("") + " away, which the book cannot do, skipped");
                    yield Optional.empty();
                }
                JsonObject out = new JsonObject();
                out.addProperty("type", "rysquests:stage");
                out.addProperty("stage", string(reward, "stage").orElse(""));
                yield Optional.of(out);
            }
            case "advancement" -> {
                JsonObject out = new JsonObject();
                out.addProperty("type", "rysquests:command");
                String criterion = string(reward, "criterion").orElse("");
                out.addProperty("command", "advancement grant @s only " + string(reward, "advancement").orElse("") + (criterion.isEmpty() ? "" : " " + criterion));
                out.addProperty("as_server", true);
                yield Optional.of(out);
            }
            default -> {
                notes.add("Quest " + questId + ": reward type " + type + " has no equivalent, skipped");
                yield Optional.empty();
            }
        };
        if (nested == 0) {
            count("reward " + type, converted.isPresent());
        }
        return converted;
    }

    private Optional<JsonObject> itemReward(JsonObject reward, String questId) {
        Optional<String> itemId = itemId(reward.get("item"));
        if (itemId.isEmpty()) {
            notes.add("Quest " + questId + ": item reward without an item, skipped");
            return Optional.empty();
        }
        JsonObject item = reward.get("item") instanceof JsonObject object ? object : new JsonObject();
        JsonObject out = new JsonObject();
        out.addProperty("type", "rysquests:item");
        out.addProperty("item", itemId.get());
        out.addProperty("count", Math.max(1, number(reward, "count", 1) * Math.max(1, number(item, "count", 1))));
        if (item.has("components")) {
            out.add("components", item.get("components"));
        }
        return Optional.of(out);
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
        if ("loot".equals(string(reward, "type").orElse("")) && decimal(table, "empty_weight") > 0) {
            // A loot roll in FTB Quests may give nothing; here one of the table's rewards always comes.
            manual(ImportReport.LOOT_EMPTY_WEIGHT, questId, "loot");
            if (notedTables.add(number(reward, "table_id", 0))) {
                notes.add("Reward table " + number(reward, "table_id", 0) + " has an empty weight, which is left out: a roll always gives something");
            }
        }
        nested++;
        for (JsonElement entry : array(table, "rewards")) {
            JsonObject item = entry.getAsJsonObject();
            convertReward(item, questId).ifPresent(converted -> {
                of.add(converted);
                weights.add(Math.max(1, number(item, "weight", 1)));
            });
        }
        nested--;
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
            return Optional.of(gameFormatting(String.join("\n", lines)));
        }
        return Optional.of(gameFormatting(value.getAsString()));
    }

    /** FTB writes colors as {@code &a}; translations need the game's own section sign. */
    private static String gameFormatting(String text) {
        return text.replaceAll("&([0-9a-fk-or])", "§$1");
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
        String plain = title.replaceAll("§[0-9a-fk-or]", "").toLowerCase(Locale.ROOT);
        String slug = plain.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return slug.length() > MAX_SLUG ? slug.substring(0, MAX_SLUG).replaceAll("_+$", "") : slug;
    }
}
