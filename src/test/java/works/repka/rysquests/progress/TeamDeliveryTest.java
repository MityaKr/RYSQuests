package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What a team delivers towards a trade paid in parts (shop plan, step M4) stays with the team, and no way of moving between teams
 * delivers one item twice: leaving, delivering alone and coming back, a disband and a return from the archive, a merge and its undo,
 * an operator's reset. The same kind of hole as the reward duplication fixed in 0.2.4.
 *
 * <p>The steps are those of {@code ShopService} and {@code TeamBridge}: joining folds the player's own count into the team's, leaving
 * moves nothing, a merge moves one team's count into the other's, its undo gives back what is still there, taking the goods takes
 * one round off.
 */
class TeamDeliveryTest {
    private static final String TROPHY = "t:trophy";
    private static final String OTHER_TROPHY = "t:other";
    private static final String ALLOY = "t:alloy";
    private static final String CELL = "t:cell";
    private static final Map<String, Long> ROUND = Map.of(ALLOY, 100L, CELL, 10L);

    private final ProgressStore store = new ProgressStore();
    private final DeliveryLedger ledger = store.deliveries();
    private final UUID team = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();

    private long alloy(UUID owner) {
        return ledger.count(owner, TROPHY, ALLOY);
    }

    /** Everything delivered of an item, over every owner: what moves between teams never grows. */
    private long everywhere(String item, UUID... owners) {
        long sum = 0;
        for (UUID owner : owners) {
            sum += ledger.count(owner, TROPHY, item);
        }
        return sum;
    }

    @Test
    void whatWasDeliveredAloneJoinsTheTeamOnce() {
        ledger.add(player, TROPHY, ALLOY, 40);

        store.foldDeliveries(player, team);
        store.foldDeliveries(player, team);

        assertEquals(40, alloy(team));
        assertEquals(0, alloy(player));
    }

    @Test
    void leavingTakesNothingAlong() {
        ledger.add(team, TROPHY, ALLOY, 60);

        // Leaving with a copy of the team's progress: quests are copied, the count is not.
        store.copyTeam(team, player);

        assertEquals(60, alloy(team));
        assertEquals(0, alloy(player));
    }

    @Test
    void leftDeliveredAndCameBackCountsOnlyWhatWasReallyBrought() {
        ledger.add(team, TROPHY, ALLOY, 60);
        ledger.add(team, TROPHY, CELL, 10);
        // The player leaves: nothing moves. Alone they bring 40 more, then come back.
        ledger.add(player, TROPHY, ALLOY, 40);
        store.foldDeliveries(player, team);

        assertEquals(100, alloy(team));
        assertTrue(ledger.take(team, TROPHY, ROUND));
        // One round was brought, one round is taken: there is nothing left for a second.
        assertFalse(ledger.take(team, TROPHY, ROUND));
        assertEquals(0, everywhere(ALLOY, team, player));
    }

    @Test
    void leavingAfterTakingTheGoodsLeavesNothingToTakeAgain() {
        ledger.add(team, TROPHY, ALLOY, 100);
        ledger.add(team, TROPHY, CELL, 10);
        assertTrue(ledger.take(team, TROPHY, ROUND));

        store.copyTeam(team, player);
        store.foldDeliveries(player, team);

        assertFalse(ledger.take(team, TROPHY, ROUND));
        assertFalse(ledger.take(player, TROPHY, ROUND));
    }

    @Test
    void disbandingAndComingBackFromTheArchiveNeverDoubles() {
        ledger.add(team, TROPHY, ALLOY, 70);
        // Disbanding: every member leaves, the count stays on the team's record.
        store.copyTeam(team, player);
        store.copyTeam(team, friend);
        assertEquals(0, alloy(player));
        assertEquals(0, alloy(friend));
        ledger.add(friend, TROPHY, ALLOY, 30);

        // The team comes back from the archive: everyone joins again.
        store.foldDeliveries(player, team);
        store.foldDeliveries(friend, team);

        assertEquals(100, alloy(team));
        assertEquals(100, everywhere(ALLOY, team, player, friend));
    }

    @Test
    void joiningAnotherTeamBringsOnlyOwnDeliveries() {
        UUID other = UUID.randomUUID();
        ledger.add(team, TROPHY, ALLOY, 70);
        ledger.add(player, TROPHY, ALLOY, 5);

        store.foldDeliveries(player, other);

        assertEquals(5, alloy(other));
        assertEquals(70, alloy(team));
    }

    @Test
    void mergingTeamsAddsUpWhatEachDelivered() {
        UUID other = UUID.randomUUID();
        ledger.add(team, TROPHY, ALLOY, 30);
        ledger.add(other, TROPHY, ALLOY, 50);
        ledger.add(other, OTHER_TROPHY, CELL, 4);

        ledger.move(other, team);

        assertEquals(80, alloy(team));
        assertEquals(4, ledger.count(team, OTHER_TROPHY, CELL));
        assertEquals(0, alloy(other));
    }

    @Test
    void undoingAMergeGivesBothSidesBackTheirOwn() {
        UUID other = UUID.randomUUID();
        ledger.add(team, TROPHY, ALLOY, 60);
        ledger.add(other, TROPHY, ALLOY, 100);
        Map<String, Map<String, Long>> before = ledger.copy(other);
        ledger.move(other, team);
        // Meanwhile the merged team brings 40 more.
        ledger.add(team, TROPHY, ALLOY, 40);

        ledger.giveBack(team, other, before);

        assertEquals(100, alloy(other));
        assertEquals(100, alloy(team));
    }

    @Test
    void undoingAMergeAfterTheGoodsWereTakenGivesBackOnlyWhatIsLeft() {
        UUID other = UUID.randomUUID();
        ledger.add(team, TROPHY, ALLOY, 60);
        ledger.add(team, TROPHY, CELL, 10);
        ledger.add(other, TROPHY, ALLOY, 100);
        Map<String, Map<String, Long>> before = ledger.copy(other);
        ledger.move(other, team);
        assertTrue(ledger.take(team, TROPHY, ROUND));

        ledger.giveBack(team, other, before);

        // 160 were brought and 100 used: 60 are left in all, not 160.
        assertEquals(60, everywhere(ALLOY, team, other));
        assertEquals(60, alloy(other));
        assertEquals(0, alloy(team));
    }

    @Test
    void anOperatorResetForgetsTheTeamsAndTheOwnCount() {
        UUID other = UUID.randomUUID();
        ledger.add(team, TROPHY, ALLOY, 60);
        ledger.add(team, OTHER_TROPHY, CELL, 3);
        ledger.add(player, TROPHY, ALLOY, 5);
        ledger.add(other, TROPHY, ALLOY, 9);

        // What ShopService.resetDeliveries does for one trade.
        ledger.clear(team, TROPHY);
        ledger.clear(player, TROPHY);

        assertEquals(0, alloy(team));
        assertEquals(0, alloy(player));
        assertEquals(3, ledger.count(team, OTHER_TROPHY, CELL));
        assertEquals(9, alloy(other));

        // A reset of everything, then the player joins: there is nothing of theirs to bring.
        ledger.clear(team, null);
        ledger.clear(player, null);
        store.foldDeliveries(player, team);
        assertTrue(ledger.all(team).isEmpty());
    }

    @Test
    void takingTheGoodsKeepsWhatWasBroughtBeyondTheRound() {
        ledger.add(team, TROPHY, ALLOY, 130);
        ledger.add(team, TROPHY, CELL, 10);

        assertTrue(ledger.take(team, TROPHY, ROUND));

        assertEquals(30, alloy(team));
        assertEquals(0, ledger.count(team, TROPHY, CELL));
    }

    @Test
    void aShortRoundTakesNothing() {
        ledger.add(team, TROPHY, ALLOY, 100);
        ledger.add(team, TROPHY, CELL, 9);

        assertFalse(ledger.take(team, TROPHY, ROUND));

        assertEquals(100, alloy(team));
        assertEquals(9, ledger.count(team, TROPHY, CELL));
    }

    @Test
    void aLeaderOfOwnCommandsTeamKeepsOneCount() {
        // Teams made with the mod's own commands are named after their leader: the team and the player share one record.
        ledger.add(player, TROPHY, ALLOY, 25);

        store.foldDeliveries(player, player);

        assertEquals(25, alloy(player));
    }

    @Test
    void theCountSurvivesASave() {
        ledger.add(team, TROPHY, ALLOY, 77);
        ledger.add(player, OTHER_TROPHY, CELL, 2);

        ProgressStore loaded = ProgressStore.load(store.save(new net.minecraft.nbt.CompoundTag(), null), null);

        assertEquals(77, loaded.deliveries().count(team, TROPHY, ALLOY));
        assertEquals(2, loaded.deliveries().count(player, OTHER_TROPHY, CELL));
    }

    @Test
    void aChangeMarksTheSaveDirty() {
        store.setDirty(false);
        ledger.add(team, TROPHY, ALLOY, 1);
        assertTrue(store.isDirty());
    }
}
