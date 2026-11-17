package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Leaving a team with a copy of its progress never gives back the rewards the player already took in it. */
class TeamLeaveCopyTest {
    private static final ResourceLocation DONE = ResourceLocation.parse("t:done");
    private static final ResourceLocation OWN = ResourceLocation.parse("t:own");

    @Test
    void theRewardsTakenInTheTeamStayTakenInTheCopy() {
        UUID team = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        ProgressStore store = new ProgressStore();
        // The team's record holds only what one member could take (reward 1); the player's own record holds what they took (0 and 2).
        store.put(team, Map.of(DONE, new QuestProgress(List.of(5), 100, List.of(1))));
        store.put(player, Map.of(DONE, QuestProgress.EMPTY.withClaimed(List.of(0, 2))));

        store.copyTeam(team, player);

        QuestProgress copy = store.get(player, DONE);
        assertTrue(copy.completed());
        assertEquals(List.of(5), copy.tasks());
        assertEquals(List.of(0, 1, 2), copy.claimed());
    }

    @Test
    void claimsOfQuestsTheTeamNeverHadAreKept() {
        UUID team = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        ProgressStore store = new ProgressStore();
        store.put(team, Map.of(DONE, new QuestProgress(List.of(5), 100, List.of())));
        store.put(player, Map.of(OWN, new QuestProgress(List.of(1), 50, List.of(0))));

        store.copyTeam(team, player);

        assertEquals(List.of(0), store.get(player, OWN).claimed());
        assertTrue(store.get(player, DONE).completed());
        assertEquals(List.of(), store.get(player, DONE).claimed());
    }
}
