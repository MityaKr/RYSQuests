package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.progress.EpochRewards.State;

/**
 * The reward of an epoch on the map (ADR-60): it opens when the epoch is passed, is taken once, and moving between teams in any
 * way never makes it claimable again. It is the epoch's quest underneath, so these are the quest's own records as the server keeps
 * them: the team's row, each member's own claims, and what leaving, joining and merging do to them.
 */
class EpochRewardsTest {
    private static final ResourceLocation KEY = ResourceLocation.parse("t:brass_mechanism");
    private static final ResourceLocation EPOCH = ResourceLocation.parse("t:start/ii_industry");
    private static final long DONE_AT = 1_000L;
    /** Every reward of the epoch, as the server lists them once all are taken. */
    private static final List<Integer> ALL = List.of(0, 1, 2, 3);

    /** The epoch quest of RepYS: waits for the key quest, one tick, experience, coins, a choice and a note; one shared reward more. */
    private static Quest epoch() {
        return Quest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "id": "t:start/ii_industry", "chapter": "t:start", "title": "II", "requires": [ "t:brass_mechanism" ],
                  "tasks": [ { "type": "rysquests:checkbox", "title": "task.t.era_open" } ],
                  "rewards": [
                    { "type": "rysquests:xp", "points": 300 },
                    { "type": "rysquests:currency", "id": "coin", "amount": 3 },
                    { "type": "rysquests:choice", "of": [ { "type": "rysquests:xp", "levels": 1 }, { "type": "rysquests:xp", "levels": 2 } ] },
                    { "type": "rysquests:xp", "levels": 5, "team_mode": "once" } ] }
                """)).getOrThrow();
    }

    private static Quest withKill() {
        return Quest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "id": "t:start/boss", "title": "Boss", "tasks": [ { "type": "rysquests:checkbox" }, { "type": "rysquests:kill", "entity": "minecraft:zombie" } ],
                  "rewards": [ { "type": "rysquests:xp", "levels": 1 } ] }
                """)).getOrThrow();
    }

    private final ProgressStore store = new ProgressStore();
    private final Quest quest = epoch();

    /** What the book shows a member, as the server's view and the client's copy of it have it. */
    private Function<ResourceLocation, QuestProgress> view(UUID member, UUID team) {
        boolean alone = member.equals(team);
        return id -> TeamRewards.memberView(id.equals(EPOCH) ? quest : null, store.get(team, id),
                alone ? List.of() : store.get(member, id).claimed(), store.owed(member, id), alone);
    }

    private State state(UUID member, UUID team) {
        return EpochRewards.state(quest, view(member, team));
    }

    /** The key quest of the epoch before is done by the team: the epoch is passed. */
    private void passEpoch(UUID team) {
        store.put(team, Map.of(KEY, new QuestProgress(List.of(1), DONE_AT, List.of())));
    }

    /** "Забрать награду" as the server makes it: the tick first, then the claim of every reward, the choice picked. */
    private void claim(UUID member, UUID team) {
        QuestProgress before = store.get(team, EPOCH);
        if (!before.completed()) {
            List<Integer> ticked = EpochRewards.ticked(quest, before.taskValues(quest.tasks().size())).orElseThrow();
            store.put(team, Map.of(EPOCH, QuestLogic.finish(quest, before, ticked, DONE_AT)));
        }
        List<Integer> waiting = EpochRewards.waiting(quest, view(member, team));
        if (waiting.isEmpty()) {
            return;
        }
        List<Integer> claimed = new java.util.ArrayList<>(view(member, team).apply(EPOCH).claimed());
        waiting.forEach(claimed::add);
        if (member.equals(team)) {
            store.put(team, Map.of(EPOCH, store.get(team, EPOCH).withClaimed(List.copyOf(claimed))));
        } else {
            store.put(member, Map.of(EPOCH, QuestProgress.EMPTY.withClaimed(List.copyOf(claimed))));
            QuestProgress row = store.get(team, EPOCH);
            store.put(team, Map.of(EPOCH, row.withClaimed(TeamRewards.sharedAfterClaim(quest, row.claimed(), claimed))));
        }
    }

    @Test
    void theRewardWaitsForTheEpochAndOpensWithoutATickByHand() {
        UUID player = UUID.randomUUID();
        assertEquals(State.LOCKED, state(player, player), "the key quest of the epoch before is not done");
        assertEquals(List.of(), EpochRewards.waiting(quest, view(player, player)));

        passEpoch(player);

        assertEquals(State.READY, state(player, player), "the epoch is passed: the tick is made when the reward is taken");
        assertEquals(Optional.of(List.of(1)), EpochRewards.ticked(quest, List.of(0)));
        assertEquals(ALL, EpochRewards.waiting(quest, view(player, player)));
    }

    @Test
    void aTaskThatIsNotATickKeepsTheEpochClosed() {
        Quest boss = withKill();
        assertEquals(Optional.empty(), EpochRewards.ticked(boss, List.of(0, 0)));
        assertEquals(Optional.of(List.of(1, 1)), EpochRewards.ticked(boss, List.of(0, 1)));
        assertEquals(State.LOCKED, EpochRewards.state(boss, id -> QuestProgress.EMPTY));
    }

    @Test
    void theRewardIsTakenOnce() {
        UUID player = UUID.randomUUID();
        passEpoch(player);

        claim(player, player);

        assertEquals(State.CLAIMED, state(player, player));
        assertTrue(store.get(player, EPOCH).completed());
        assertEquals(List.of(), EpochRewards.waiting(quest, view(player, player)), "nothing is left to take a second time");
        claim(player, player);
        assertEquals(ALL, store.get(player, EPOCH).claimed(), "a second press gives nothing");
    }

    @Test
    void aRewardTakenInTheOldChapterStaysTaken() {
        UUID player = UUID.randomUUID();
        // the record of 0.2.6: the epoch's quest was ticked and claimed in "Начало"; the same id now shows at the epoch
        passEpoch(player);
        store.put(player, Map.of(EPOCH, new QuestProgress(List.of(1), DONE_AT, ALL)));

        assertEquals(State.CLAIMED, state(player, player));
    }

    @Test
    void eachMemberTakesTheirOwnAndTheSharedOneOnce() {
        UUID team = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        passEpoch(team);

        claim(alice, team);

        assertEquals(State.CLAIMED, state(alice, team));
        assertEquals(State.READY, state(bob, team), "every member takes the epoch's reward for themselves");
        assertEquals(List.of(0, 1, 2), EpochRewards.waiting(quest, view(bob, team)), "the shared reward was taken by Alice for the team");
        claim(bob, team);
        assertEquals(State.CLAIMED, state(bob, team));
    }

    @Test
    void leavingWithACopyAndComingBackGivesNothingTwice() {
        UUID team = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        passEpoch(team);
        claim(alice, team);

        store.copyTeam(team, alice);
        assertEquals(State.CLAIMED, state(alice, alice), "alone, Alice keeps what she took in the team");

        claim(alice, alice);
        store.mergeInto(alice, team);
        assertEquals(State.CLAIMED, state(alice, team), "back in the team, nothing of the epoch waits again");
        assertEquals(ALL, store.get(alice, EPOCH).claimed());
    }

    @Test
    void joiningAnotherTeamThatHasNotTakenItGivesNothingTwice() {
        UUID first = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        passEpoch(first);
        claim(alice, first);
        store.copyTeam(first, alice);

        // Alice joins another team that has passed the epoch but taken nothing of it, carrying her progress
        passEpoch(other);
        store.mergeInto(alice, other);

        assertEquals(State.CLAIMED, state(alice, other), "her own claims follow her");
        assertEquals(State.READY, state(carol, other), "the new team's members still take theirs");
        assertEquals(List.of(0, 1, 2), EpochRewards.waiting(quest, view(carol, other)), "only the shared reward came along");
    }

    @Test
    void mergingTeamsGivesNothingTwice() {
        UUID from = UUID.randomUUID();
        UUID into = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID dave = UUID.randomUUID();
        passEpoch(from);
        passEpoch(into);
        claim(alice, from);
        claim(dave, into);

        store.mergeInto(from, into);

        assertEquals(State.CLAIMED, state(alice, into));
        assertEquals(State.CLAIMED, state(dave, into));
    }
}
