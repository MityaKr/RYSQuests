package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.ShopOffer.Limit;
import works.repka.rysquests.data.ShopOffer.Per;
import works.repka.rysquests.data.ShopOffer.Reset;
import works.repka.rysquests.shop.ShopLimits;

/**
 * A trade "once per team" stays bought however players move between teams: leaving, buying alone and coming back never buys it
 * twice for one team (the same kind of hole as the reward duplication fixed in 0.2.4).
 */
class TeamShopLimitsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private static final String SCHEMATIC = "t:schem";
    private static final String KIT = "t:kit";
    private static final Limit ONCE = new Limit(Per.TEAM, 1, Reset.NEVER);
    private static final Limit THREE = new Limit(Per.TEAM, 3, Reset.NEVER);
    private static final Map<String, Limit> LIMITS = Map.of(SCHEMATIC, ONCE, KIT, THREE);

    private final ProgressStore store = new ProgressStore();
    private final UUID team = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();

    /** A purchase as ShopService makes it: counted for whoever holds the limit, the team or a player alone. */
    private void buy(UUID owner, String offer, int times) {
        store.setShopUsage(owner, offer, ShopLimits.after(LIMITS.get(offer), store.shopUsage(owner).get(offer), times, TODAY));
    }

    private int used(UUID owner, String offer) {
        return ShopLimits.used(LIMITS.get(offer), store.shopUsage(owner).get(offer), TODAY);
    }

    @Test
    void leavingAfterTheTeamBoughtKeepsItBoughtAlone() {
        buy(team, SCHEMATIC, 1);

        store.carryTeamLimits(team, player, LIMITS);

        assertEquals(1, used(player, SCHEMATIC));
        assertEquals(1, used(team, SCHEMATIC));
    }

    @Test
    void leftBoughtAndCameBackCountsForTheTeam() {
        store.carryTeamLimits(team, player, LIMITS);
        buy(player, SCHEMATIC, 1);

        store.foldTeamLimits(player, team, LIMITS, TODAY);

        assertEquals(1, used(team, SCHEMATIC));
    }

    @Test
    void comingBackAfterTheTeamBoughtAddsNothing() {
        buy(team, SCHEMATIC, 1);
        store.carryTeamLimits(team, player, LIMITS);

        store.foldTeamLimits(player, team, LIMITS, TODAY);

        assertEquals(1, used(team, SCHEMATIC));
    }

    @Test
    void whatWasBoughtAloneBeforeJoiningCountsOnce() {
        buy(player, SCHEMATIC, 1);

        store.foldTeamLimits(player, team, LIMITS, TODAY);
        store.foldTeamLimits(player, team, LIMITS, TODAY);

        assertEquals(1, used(team, SCHEMATIC));
    }

    @Test
    void onlyWhatWasBoughtAloneIsAddedOnReturn() {
        buy(team, KIT, 1);
        store.carryTeamLimits(team, player, LIMITS);
        buy(player, KIT, 1);
        buy(team, KIT, 1);

        store.foldTeamLimits(player, team, LIMITS, TODAY);

        // One bought by the team before, one by the team meanwhile, one by the player alone.
        assertEquals(3, used(team, KIT));
    }

    @Test
    void disbandingAndRestoringNeverBuysTwice() {
        buy(team, SCHEMATIC, 1);
        buy(team, KIT, 1);
        // Disbanding: every member leaves with a copy.
        store.carryTeamLimits(team, player, LIMITS);
        store.carryTeamLimits(team, friend, LIMITS);
        assertEquals(1, used(player, SCHEMATIC));
        assertEquals(1, used(friend, SCHEMATIC));
        buy(player, KIT, 1);
        buy(friend, KIT, 1);

        // The team comes back from the archive: everyone joins again.
        store.foldTeamLimits(player, team, LIMITS, TODAY);
        store.foldTeamLimits(friend, team, LIMITS, TODAY);

        assertEquals(1, used(team, SCHEMATIC));
        assertEquals(3, used(team, KIT));
    }

    @Test
    void joiningAnotherTeamBringsOnlyOwnPurchases() {
        UUID other = UUID.randomUUID();
        buy(team, SCHEMATIC, 1);
        store.carryTeamLimits(team, player, LIMITS);

        store.foldTeamLimits(player, other, LIMITS, TODAY);

        // The schematic is the old team's; the new one has not bought it.
        assertEquals(0, used(other, SCHEMATIC));
    }

    @Test
    void mergingTeamsAddsUpWhatEachBought() {
        UUID other = UUID.randomUUID();
        buy(team, KIT, 1);
        buy(other, KIT, 2);

        store.addTeamLimits(other, team, LIMITS, TODAY);

        assertEquals(3, used(team, KIT));
    }

    @Test
    void aLeaderOfOwnCommandsTeamIsNotCountedTwice() {
        // Teams made with the mod's own commands are named after their leader: the team and the player share one record.
        buy(player, SCHEMATIC, 1);

        store.foldTeamLimits(player, player, LIMITS, TODAY);
        store.carryTeamLimits(player, player, LIMITS);
        store.foldTeamLimits(player, player, LIMITS, TODAY);

        assertEquals(1, used(player, SCHEMATIC));
    }

    @Test
    void aDailyTeamLimitCarriesOnlyToday() {
        Limit daily = new Limit(Per.TEAM, 2, Reset.DAILY);
        Map<String, Limit> limits = Map.of(KIT, daily);
        store.setShopUsage(player, KIT, ShopLimits.after(daily, null, 1, TODAY.minusDays(1)));

        store.foldTeamLimits(player, team, limits, TODAY);

        assertEquals(0, ShopLimits.used(daily, store.shopUsage(team).get(KIT), TODAY));
    }

    @Test
    void aPurchaseAfterAnOperatorResetStillCounts() {
        UUID other = UUID.randomUUID();
        buy(team, SCHEMATIC, 1);
        store.carryTeamLimits(team, player, LIMITS);
        // What ShopService.resetLimits does for the player.
        store.clearShopUsage(player, null);
        store.clearCountedUsage(player, null);
        buy(player, SCHEMATIC, 1);

        store.foldTeamLimits(player, other, LIMITS, TODAY);

        assertEquals(1, used(other, SCHEMATIC));
    }

    @Test
    void theCountedPartSurvivesASave() {
        buy(team, SCHEMATIC, 1);
        store.carryTeamLimits(team, player, LIMITS);

        ProgressStore loaded = ProgressStore.load(store.save(new net.minecraft.nbt.CompoundTag(), null), null);
        loaded.foldTeamLimits(player, team, LIMITS, TODAY);

        assertEquals(1, ShopLimits.used(ONCE, loaded.shopUsage(team).get(SCHEMATIC), TODAY));
        assertEquals(1, ShopLimits.used(ONCE, loaded.countedUsage(player).get(SCHEMATIC), TODAY));
    }
}
