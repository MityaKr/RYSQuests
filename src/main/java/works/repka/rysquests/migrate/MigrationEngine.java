package works.repka.rysquests.migrate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.progress.QuestProgress;

/** Applies migrations to copies of the progress, so that a dry run is simply a run whose result is thrown away. */
public final class MigrationEngine {
    /** The state a run starts from, and, in {@link Result}, ends with. */
    public record State(Map<UUID, Map<ResourceLocation, QuestProgress>> teams, Map<UUID, Map<ResourceLocation, QuestProgress>> archive, Set<String> applied) {
    }

    public record Result(State state, List<String> report, boolean changed) {
    }

    private MigrationEngine() {
    }

    public static Result run(List<Migration> migrations, State input) {
        Map<UUID, Map<ResourceLocation, QuestProgress>> teams = deepCopy(input.teams());
        Map<UUID, Map<ResourceLocation, QuestProgress>> archive = deepCopy(input.archive());
        Set<String> applied = new LinkedHashSet<>(input.applied());
        List<String> report = new ArrayList<>();
        boolean changed = false;
        for (Migration migration : migrations) {
            if (applied.contains(migration.id())) {
                continue;
            }
            int touched = 0;
            for (Map.Entry<UUID, Map<ResourceLocation, QuestProgress>> team : teams.entrySet()) {
                touched += apply(migration, team.getValue(), archive.computeIfAbsent(team.getKey(), key -> new HashMap<>()));
            }
            applied.add(migration.id());
            changed = true;
            report.add(migration.id() + " (" + migration.op() + "): " + touched + " records");
        }
        return new Result(new State(teams, archive, applied), report, changed);
    }

    private static Map<UUID, Map<ResourceLocation, QuestProgress>> deepCopy(Map<UUID, Map<ResourceLocation, QuestProgress>> source) {
        Map<UUID, Map<ResourceLocation, QuestProgress>> copy = new HashMap<>();
        source.forEach((team, quests) -> copy.put(team, new HashMap<>(quests)));
        return copy;
    }

    private static int apply(Migration migration, Map<ResourceLocation, QuestProgress> quests, Map<ResourceLocation, QuestProgress> archive) {
        return switch (migration) {
            case Migration.Rename rename -> move(rename.from(), rename.to(), quests);
            case Migration.Merge merge -> {
                int moved = 0;
                for (ResourceLocation from : merge.from()) {
                    moved += move(from, merge.to(), quests);
                }
                yield moved;
            }
            case Migration.Remove remove -> {
                QuestProgress removed = quests.remove(remove.from());
                if (removed != null) {
                    archive.put(remove.from(), removed);
                }
                yield removed == null ? 0 : 1;
            }
            case Migration.FtbqMap ignored -> 0;
        };
    }

    private static int move(ResourceLocation from, ResourceLocation to, Map<ResourceLocation, QuestProgress> quests) {
        QuestProgress moved = quests.remove(from);
        if (moved == null) {
            return 0;
        }
        quests.merge(to, moved, MigrationEngine::best);
        return 1;
    }

    /** The better of two records: highest value per task, latest completion, and every claimed reward. */
    public static QuestProgress best(QuestProgress a, QuestProgress b) {
        List<Integer> tasks = new ArrayList<>();
        for (int i = 0; i < Math.max(a.tasks().size(), b.tasks().size()); i++) {
            tasks.add(Math.max(a.task(i), b.task(i)));
        }
        Set<Integer> claimed = new TreeSet<>(a.claimed());
        claimed.addAll(b.claimed());
        return new QuestProgress(tasks, Math.max(a.completedAt(), b.completedAt()), List.copyOf(claimed), Math.max(a.nextAt(), b.nextAt()));
    }
}
