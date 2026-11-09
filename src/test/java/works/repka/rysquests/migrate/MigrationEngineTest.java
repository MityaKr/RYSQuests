package works.repka.rysquests.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.migrate.MigrationEngine.Result;
import works.repka.rysquests.migrate.MigrationEngine.State;
import works.repka.rysquests.progress.QuestProgress;

class MigrationEngineTest {
    private static final UUID TEAM = new UUID(1, 2);
    private static final ResourceLocation A = ResourceLocation.parse("t:a");
    private static final ResourceLocation B = ResourceLocation.parse("t:b");
    private static final ResourceLocation C = ResourceLocation.parse("t:c");

    private static QuestProgress progress(int task, long completed, Integer... claimed) {
        return new QuestProgress(List.of(task), completed, List.of(claimed));
    }

    private static State state(Map<ResourceLocation, QuestProgress> quests) {
        Map<UUID, Map<ResourceLocation, QuestProgress>> teams = new HashMap<>();
        teams.put(TEAM, new HashMap<>(quests));
        return new State(teams, new HashMap<>(), Set.of());
    }

    @Test
    void renameMovesTheRecord() {
        Result result = MigrationEngine.run(List.of(new Migration.Rename("1", A, B)), state(Map.of(A, progress(3, 5))));

        Map<ResourceLocation, QuestProgress> quests = result.state().teams().get(TEAM);
        assertNull(quests.get(A));
        assertEquals(3, quests.get(B).task(0));
    }

    @Test
    void mergeTakesTheBestOfBoth() {
        Result result = MigrationEngine.run(List.of(new Migration.Merge("1", List.of(A, B), C)),
                state(Map.of(A, progress(3, 0, 0), B, progress(7, 9, 1))));

        QuestProgress merged = result.state().teams().get(TEAM).get(C);
        assertEquals(7, merged.task(0));
        assertEquals(9, merged.completedAt());
        assertEquals(List.of(0, 1), merged.claimed());
    }

    @Test
    void removeArchivesInsteadOfDeleting() {
        Result result = MigrationEngine.run(List.of(new Migration.Remove("1", A)), state(Map.of(A, progress(3, 5))));

        assertNull(result.state().teams().get(TEAM).get(A));
        assertEquals(3, result.state().archive().get(TEAM).get(A).task(0));
    }

    @Test
    void appliedMigrationsDoNotRunAgain() {
        List<Migration> migrations = List.of(new Migration.Rename("1", A, B));
        Result first = MigrationEngine.run(migrations, state(Map.of(A, progress(3, 5))));

        first.state().teams().get(TEAM).put(A, progress(9, 9));
        Result second = MigrationEngine.run(migrations, first.state());

        assertFalse(second.changed());
        assertEquals(9, second.state().teams().get(TEAM).get(A).task(0));
    }

    @Test
    void runLeavesItsInputUntouched() {
        State input = state(Map.of(A, progress(3, 5)));

        MigrationEngine.run(List.of(new Migration.Remove("1", A)), input);

        assertTrue(input.teams().get(TEAM).containsKey(A));
        assertTrue(input.applied().isEmpty());
    }

    @Test
    void fileFormatParses() {
        String json = """
                { "migrations": [
                  { "id": "a", "op": "rename", "from": "x:a", "to": "x:b" },
                  { "id": "b", "op": "merge", "from": ["x:a", "x:c"], "to": "x:d", "strategy": "max" },
                  { "id": "c", "op": "remove", "from": "x:a" },
                  { "id": "d", "op": "map", "from_ftbq": { "1A": "x:a" } }
                ] }
                """;

        List<Migration> migrations = Migration.FILE_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();

        assertEquals(List.of("rename", "merge", "remove", "map"), migrations.stream().map(Migration::op).toList());
    }
}
