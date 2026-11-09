package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Quest;

class TeamRewardsTest {
    private static Quest quest() {
        return Quest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "id": "test:a", "title": "A", "rewards": [
                  { "type": "rysquests:xp", "levels": 1 },
                  { "type": "rysquests:xp", "levels": 2, "team_mode": "once" } ] }
                """)).getOrThrow();
    }

    @Test
    void rewardForEachMemberStaysPersonal() {
        assertEquals(List.of(), TeamRewards.claimedBy(quest(), List.of(0), List.of()));
    }

    @Test
    void rewardTakenOnceCountsForEveryMember() {
        assertEquals(List.of(1), TeamRewards.claimedBy(quest(), List.of(1), List.of()));
    }

    @Test
    void onlySharedRewardsReachTheTeamList() {
        assertEquals(List.of(1), TeamRewards.sharedAfterClaim(quest(), List.of(), List.of(0, 1)));
    }

    @Test
    void takenRewardIsNotCountedTwice() {
        assertEquals(List.of(1), TeamRewards.claimedBy(quest(), List.of(1), List.of(1)));
    }
}
