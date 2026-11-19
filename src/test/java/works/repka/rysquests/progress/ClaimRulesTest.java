package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.reward.ClaimLimitReward;
import works.repka.rysquests.task.ClaimTask;

/** The land task and reward of the book (plan of the map, section 8): what their JSON says. */
class ClaimRulesTest {
    @Test
    void theTaskCountsChunks() {
        ClaimTask task = ClaimTask.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": 16}")).getOrThrow();
        assertEquals(16, task.target());
        assertEquals(1, ClaimTask.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow().target());
        assertTrue(ClaimTask.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"count\": 0}")).error().isPresent(), "nothing to claim is no task");
    }

    @Test
    void theRewardRaisesTheLimits() {
        ClaimLimitReward reward = ClaimLimitReward.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"claims\": 4, \"force_loaded\": 1, \"team_mode\": \"once\"}")).getOrThrow();
        assertEquals(new ClaimLimitReward(4, 1, TeamMode.ONCE), reward);
        assertEquals(TeamMode.EACH, ClaimLimitReward.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"claims\": 2}")).getOrThrow().teamMode(),
                "each member's own bonus by default");
        assertTrue(ClaimLimitReward.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).error().isPresent(), "a reward of nothing is refused");
    }
}
