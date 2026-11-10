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
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Ids;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Ref;
import works.repka.rysquests.progress.QuestProgress;

/**
 * Reads FTB Quests team progress ({@code world/ftbquests/*.snbt}) and maps it to our quest ids. Tasks belong to the team;
 * a reward taken by one member of a party goes to that member's own record, as it does when claimed here.
 */
public final class FtbqProgress {
    private FtbqProgress() {
    }

    public static Map<UUID, Map<ResourceLocation, QuestProgress>> read(Path dir, Ids ids, QuestBook book) throws IOException {
        Map<UUID, Map<ResourceLocation, QuestProgress>> teams = new HashMap<>();
        Map<UUID, Map<ResourceLocation, List<Integer>>> members = new HashMap<>();
        if (!Files.isDirectory(dir)) {
            return teams;
        }
        List<Path> files;
        try (Stream<Path> paths = Files.list(dir)) {
            files = paths.filter(file -> file.toString().endsWith(".snbt")).sorted().toList();
        }
        for (Path file : files) {
            JsonObject team = Snbt.parse(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            teams.put(parseUuid(team.get("uuid").getAsString()), convert(team, ids, book, members));
        }
        members.forEach((player, quests) -> quests.forEach((quest, claimed) -> addClaims(teams.computeIfAbsent(player, id -> new HashMap<>()), quest, claimed)));
        return teams;
    }

    /** A player who also has a file of their own keeps it: nothing they took anywhere can be taken twice. */
    private static void addClaims(Map<ResourceLocation, QuestProgress> own, ResourceLocation quest, List<Integer> claimed) {
        QuestProgress before = own.getOrDefault(quest, QuestProgress.EMPTY);
        List<Integer> all = new ArrayList<>(before.claimed());
        claimed.stream().filter(reward -> !all.contains(reward)).forEach(all::add);
        own.put(quest, before.withClaimed(List.copyOf(all)));
    }

    static UUID parseUuid(String text) {
        String hex = text.replace("-", "");
        return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
    }

    private static boolean sharedByTeam(QuestBook book, ResourceLocation quest, int reward) {
        Quest definition = book.quests().get(quest);
        return definition != null && reward < definition.rewards().size() && definition.rewards().get(reward).teamMode() == TeamMode.ONCE;
    }

    static Map<ResourceLocation, QuestProgress> convert(JsonObject team, Ids ids, QuestBook book, Map<UUID, Map<ResourceLocation, List<Integer>>> members) {
        UUID owner = parseUuid(team.get("uuid").getAsString());
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
            int colon = key.lastIndexOf(':');
            Ref ref = ids.rewards().get(key.substring(colon + 1));
            if (ref == null) {
                return;
            }
            ResourceLocation quest = ResourceLocation.parse(ref.quest());
            UUID player = colon < 0 ? owner : parseUuid(key.substring(0, colon));
            if (player.equals(owner) || sharedByTeam(book, quest, ref.index())) {
                claimed.computeIfAbsent(quest, id -> new ArrayList<>()).add(ref.index());
            } else {
                members.computeIfAbsent(player, id -> new HashMap<>()).computeIfAbsent(quest, id -> new ArrayList<>()).add(ref.index());
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
