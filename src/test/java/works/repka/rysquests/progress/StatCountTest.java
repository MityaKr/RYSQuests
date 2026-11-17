package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.task.StatTask;

/**
 * A team's counter of a statistic comes from all its members (ADR-61). The numbers are the RepYS server's of 18.11: the author and his
 * teammate share progress, and three achievements stood at the teammate's numbers while the author played.
 */
class StatCountTest {
    private static StatTask custom(String entry, int count) {
        return new StatTask(ResourceLocation.withDefaultNamespace("custom"), ResourceLocation.withDefaultNamespace(entry), count, Optional.empty());
    }

    @Test
    void theAuthorsFallsAddToHisTeammatesAndReachTheGoldLevel() {
        // «Парашютист», level 3: 20 km. The teammate fell 19.66 km, the author 3.46 km; before, the counter kept the teammate's alone.
        StatTask skydiver = custom("fall_one_cm", 2_000_000);
        int count = StatCount.combine(false, new int[] {345_975, 1_966_197});
        assertEquals(2_312_172, count);
        assertEquals(2_000_000, StatCount.next(false, 1_966_197, count, skydiver.target()));
    }

    @Test
    void deathsOfTheTeamAddUp() {
        // «Бессмертный… почти», level 3: 50 deaths; 37 and 46.
        assertEquals(50, StatCount.next(false, 46, StatCount.combine(false, new int[] {37, 46}), 50));
    }

    @Test
    void aCounterNeverGoesDownWhenAMemberLeaves() {
        assertEquals(900, StatCount.next(false, 900, StatCount.combine(false, new int[] {400}), 1000));
    }

    @Test
    void theSumStopsAtTheLargestInt() {
        assertEquals(Integer.MAX_VALUE, StatCount.combine(false, new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, 5}));
        assertEquals(10, StatCount.combine(false, new int[] {-3, 10}));
    }

    @Test
    void timeSinceDeathAndRestAreStreaks() {
        assertTrue(StatCount.isStreak(custom("time_since_death", 1)));
        assertTrue(StatCount.isStreak(custom("time_since_rest", 1)));
        assertFalse(StatCount.isStreak(custom("play_time", 1)));
        assertFalse(StatCount.isStreak(custom("deaths", 1)));
        // the same path under another statistic type is not the game's streak
        assertFalse(StatCount.isStreak(new StatTask(ResourceLocation.withDefaultNamespace("mined"),
                ResourceLocation.withDefaultNamespace("time_since_death"), 1, Optional.empty())));
    }

    @Test
    void aStreakIsTheLongestCurrentLifeAndStartsAgainAfterDeath() {
        // «Долгожитель», level 3: 50 hours. The counter held the teammate's 49.2 hours of a life long over; now 21.6 and 0.5 hours.
        int count = StatCount.combine(true, new int[] {38_563, 1_553_133});
        assertEquals(1_553_133, count);
        assertEquals(1_553_133, StatCount.next(true, 3_545_641, count, 3_600_000));
        // one life of 50 hours reaches it
        assertEquals(3_600_000, StatCount.next(true, 1_553_133, StatCount.combine(true, new int[] {3_600_020, 0}), 3_600_000));
    }

    @Test
    void theMembersOfATeamMadeWithOurCommandsShareTheCounter() {
        UUID owner = UUID.randomUUID();
        UUID mate = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        ProgressStore store = new ProgressStore();
        store.setTeam(owner, owner);
        store.setTeam(mate, owner);
        store.setTeam(stranger, stranger);
        assertEquals(Set.of(owner, mate), Set.copyOf(store.teamMembers(owner, ProgressMode.TEAM)));
        store.leaveTeam(mate);
        assertEquals(Set.of(owner), Set.copyOf(store.teamMembers(owner, ProgressMode.TEAM)));
    }
}
