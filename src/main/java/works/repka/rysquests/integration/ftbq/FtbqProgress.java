package works.repka.rysquests.integration.ftbq;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Ids;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Ref;
import works.repka.rysquests.progress.QuestProgress;

/** Reads FTB Quests team progress ({@code world/ftbquests/*.snbt}) and maps it to our quest ids. */
public final class FtbqProgress {
    private FtbqProgress() {
    }

    public static Map<UUID, Map<ResourceLocation, QuestProgress>> read(Path dir, Ids ids, QuestBook book) throws IOException {
        Map<UUID, Map<ResourceLocation, QuestProgress>> teams = new HashMap<>();
        if (!Files.isDirectory(dir)) {
            return teams;
        }
        List<Path> files;
        try (Stream<Path> paths = Files.list(dir)) {
            files = paths.filter(file -> file.toString().endsWith(".snbt")).sorted().toList();
        }
        for (Path file : files) {
            JsonObject team = Snbt.parse(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            teams.put(parseUuid(team.get("uuid").getAsString()), convert(team, ids, book));
        }
        return teams;
    }

    static UUID parseUuid(String text) {
        String hex = text.replace("-", "");
        return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
    }

    static Map<ResourceLocation, QuestProgress> convert(JsonObject team, Ids ids, QuestBook book) {
        Map<ResourceLocation, int[]> tasks = new HashMap<>();
        Map<ResourceLocation, Long> completed = new HashMap<>();
        Map<ResourceLocation, List<Integer>> claimed = new HashMap<>();

        forEachEntry(team, "task_progress", (hex, value) -> {
            Ref ref = ids.tasks().get(hex);
            Quest quest = ref == null ? null : book.quests().get(ResourceLocation.parse(ref.quest()));
            if (quest != null && ref.index() < quest.tasks().size()) {
                tasks.computeIfAbsent(quest.id(), key -> new int[quest.tasks().size()])[ref.index()] = (int) Math.min(Integer.MAX_VALUE, value.getAsLong());
            }
        });
        forEachEntry(team, "completed", (hex, value) -> {
            String id = ids.quests().get(hex);
            if (id != null) {
                completed.put(ResourceLocation.parse(id), Math.max(1, value.getAsLong()));
            }
        });
        // The key is "<player>:<reward>" for per-player rewards and "<reward>" for team rewards.
        forEachEntry(team, "claimed_rewards", (key, value) -> {
            Ref ref = ids.rewards().get(key.substring(key.lastIndexOf(':') + 1));
            if (ref != null) {
                claimed.computeIfAbsent(ResourceLocation.parse(ref.quest()), id -> new ArrayList<>()).add(ref.index());
            }
        });

        Map<ResourceLocation, QuestProgress> result = new HashMap<>();
        for (Quest quest : book.quests().values()) {
            boolean started = tasks.containsKey(quest.id()) || completed.containsKey(quest.id());
            if (started) {
                result.put(quest.id(), new QuestProgress(values(quest, tasks.get(quest.id()), completed.containsKey(quest.id())),
                        completed.getOrDefault(quest.id(), 0L), List.copyOf(claimed.getOrDefault(quest.id(), List.of()))));
            }
        }
        return result;
    }

    /** A completed quest shows full tasks even if FTB did not record each of them. */
    private static List<Integer> values(Quest quest, int[] raw, boolean completed) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            int value = raw == null ? 0 : raw[i];
            values.add(Math.min(task.target(), completed ? task.target() : value));
        }
        return values;
    }

    private interface EntryConsumer {
        void accept(String key, JsonElement value);
    }

    private static void forEachEntry(JsonObject team, String section, EntryConsumer consumer) {
        if (team.get(section) instanceof JsonObject entries) {
            entries.asMap().forEach(consumer::accept);
        }
    }
}
