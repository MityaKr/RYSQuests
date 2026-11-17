package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Quest;

/**
 * A repeatable quest in a team (ADR-52): the first member to take all their rewards starts the next round, and what the others
 * have not taken of the finished round stays theirs until they take it. Nobody loses a reward and nobody gets one twice.
 */
class TeamRoundOwedTest {
    private static final long DONE_AT = 1_000L;

    /** Reward 0 is for each member, reward 1 only one of the team takes. */
    private static Quest quest() {
        return Quest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "id": "t:daily", "title": "Daily", "tasks": [ { "type": "rysquests:checkbox" } ], "rewards": [
                  { "type": "rysquests:xp", "levels": 1 },
                  { "type": "rysquests:xp", "levels": 2, "team_mode": "once" } ],
                  "repeatable": { "cooldown_minutes": 0 } }
                """)).getOrThrow();
    }

    private static Quest onceOnly() {
        return Quest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "id": "t:shared", "title": "Shared", "rewards": [ { "type": "rysquests:xp", "levels": 2, "team_mode": "once" } ] }
                """)).getOrThrow();
    }

    private static QuestProgress finished(List<Integer> teamClaimed) {
        return new QuestProgress(List.of(1), DONE_AT, teamClaimed, 0, 3);
    }

    /** The rewards still waiting in what the book shows a member. */
    private static List<Integer> waiting(Quest quest, QuestProgress view) {
        List<Integer> left = new ArrayList<>();
        if (view.completed()) {
            for (int i = 0; i < quest.rewards().size(); i++) {
                if (!view.claimed().contains(i)) {
                    left.add(i);
                }
            }
        }
        return left;
    }

    @Test
    void aMemberWhoTookNothingBeforeTheNextRoundKeepsTheRound() {
        Quest quest = quest();
        // Alice took both rewards, the shared one too: the team's row lists it, and the next round starts.
        assertTrue(quest.options().repeatable().isPresent());
        QuestProgress owed = TeamRewards.owedAtNewRound(quest, finished(List.of(1)), List.of(), null);

        assertNotNull(owed);
        assertEquals(DONE_AT, owed.completedAt());
        assertEquals(List.of(1), owed.claimed());
        QuestProgress nextRound = new QuestProgress(List.of(), 0, List.of(), 0, 4);
        QuestProgress view = TeamRewards.owedView(owed, nextRound);
        assertTrue(view.completed());
        assertEquals(List.of(0), waiting(quest, view));
    }

    @Test
    void takingTheOwedRoundPaysOnceAndThenShowsTheTeamsRound() {
        Quest quest = quest();
        QuestProgress owed = TeamRewards.owedAtNewRound(quest, finished(List.of(1)), List.of(), null);

        QuestProgress after = TeamRewards.owedAfterClaim(quest, owed, List.of(1, 0));

        assertNull(after);
        // Back on the team's next round, still open: nothing to take until it is done again.
        QuestProgress nextRound = new QuestProgress(List.of(), 0, List.of(), 0, 4);
        assertEquals(List.of(), waiting(quest, nextRound));
    }

    @Test
    void aMemberWhoTookTheirRewardInTimeIsOwedNothing() {
        // Bob took his own reward of the round before Alice finished it: nothing more is his, so nothing is kept for him.
        assertNull(TeamRewards.owedAtNewRound(quest(), finished(List.of(1)), List.of(0), null));
    }

    @Test
    void aMemberWhoTookPartOfTheRoundKeepsOnlyTheRest() {
        Quest quest = quest();
        // Bob took the shared reward but his own did not fit; Alice then took hers and started the next round.
        QuestProgress owed = TeamRewards.owedAtNewRound(quest, finished(List.of(1)), List.of(1), null);

        assertNotNull(owed);
        assertEquals(List.of(0), waiting(quest, TeamRewards.owedView(owed, QuestProgress.EMPTY)));
    }

    @Test
    void roundsFinishedMeanwhileWaitBehindTheOldestOne() {
        Quest quest = quest();
        QuestProgress first = TeamRewards.owedAtNewRound(quest, finished(List.of(1)), List.of(), null);
        // The team finishes and starts another round while Bob still has the first one to take.
        QuestProgress two = TeamRewards.owedAtNewRound(quest, finished(List.of(1)), List.of(), first);

        assertEquals(1, two.times());
        assertEquals(List.of(1), two.claimed());
        // Bob takes the first round: the second one comes up, its shared reward already taken by whoever started the round after.
        QuestProgress second = TeamRewards.owedAfterClaim(quest, two, List.of(1, 0));
        assertNotNull(second);
        assertEquals(0, second.times());
        assertEquals(List.of(0), waiting(quest, TeamRewards.owedView(second, QuestProgress.EMPTY)));
        // And then nothing is left: two rounds, his own reward twice, never a third time.
        assertNull(TeamRewards.owedAfterClaim(quest, second, List.of(1, 0)));
    }

    @Test
    void aRoundWithOnlySharedRewardsLeavesNothingOwed() {
        Quest quest = onceOnly();
        assertNull(TeamRewards.owedAtNewRound(quest, new QuestProgress(List.of(), DONE_AT, List.of(0)), List.of(), null));
        QuestProgress owed = new QuestProgress(List.of(), DONE_AT, List.of(), 0, 0);
        assertSame(owed, TeamRewards.owedAtNewRound(quest, new QuestProgress(List.of(), DONE_AT, List.of(0)), List.of(), owed));
    }

    @Test
    void anUnfinishedRecordIsNeverOwed() {
        assertNull(TeamRewards.owedAtNewRound(quest(), new QuestProgress(List.of(0), 0, List.of()), List.of(), null));
    }

    @Test
    void partOfAnOwedRoundStaysUntilTheRestIsTaken() {
        Quest quest = quest();
        QuestProgress owed = new QuestProgress(List.of(1), DONE_AT, List.of(), 0, 0);

        QuestProgress after = TeamRewards.owedAfterClaim(quest, owed, List.of(0));

        assertNotNull(after);
        assertEquals(List.of(0), after.claimed());
        assertEquals(List.of(1), waiting(quest, TeamRewards.owedView(after, QuestProgress.EMPTY)));
    }

    @Test
    void roundMembersAreThePlayersOfTheTeam() {
        UUID team = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Map<UUID, UUID> teams = Map.of(team, team, alice, team, bob, team, other, other);

        // A team mod's id is not a player and is never owed anything.
        assertEquals(List.of(alice, bob), TeamRewards.roundMembers(List.of(team, alice, bob, other, alice), team, teams::get, false));
        // Our own teams use the owner's id: the owner is a player like the others.
        assertEquals(List.of(team, alice, bob), TeamRewards.roundMembers(List.of(team, alice, bob, other), team, teams::get, true));
    }

    @Test
    void owedRoundsAreSavedAndLoaded() {
        UUID bob = UUID.randomUUID();
        ResourceLocation daily = ResourceLocation.parse("t:daily");
        ProgressStore store = new ProgressStore();
        store.setOwed(bob, daily, new QuestProgress(List.of(1), DONE_AT, List.of(1), 0, 2));

        ProgressStore loaded = ProgressStore.load(store.save(new CompoundTag(), null), null);

        QuestProgress owed = loaded.owed(bob, daily);
        assertNotNull(owed);
        assertEquals(List.of(1), owed.claimed());
        assertEquals(2, owed.times());
        assertEquals(DONE_AT, owed.completedAt());
        loaded.setOwed(bob, daily, null);
        assertNull(loaded.owed(bob, daily));
        assertFalse(ProgressStore.load(loaded.save(new CompoundTag(), null), null).save(new CompoundTag(), null)
                .getCompound("owed_rounds").contains(bob.toString()));
    }

    @Test
    void savesFromBeforeLoadWithNothingOwed() {
        UUID bob = UUID.randomUUID();
        CompoundTag old = new ProgressStore().save(new CompoundTag(), null);
        old.remove("owed_rounds");

        assertNull(ProgressStore.load(old, null).owed(bob, ResourceLocation.parse("t:daily")));
    }
}
