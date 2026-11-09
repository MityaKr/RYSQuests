package works.repka.rysquests.progress;

import java.util.ArrayList;
import java.util.List;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TeamMode;

/** Which rewards count as taken for a team member: their own, plus those only one member could take. */
final class TeamRewards {
    private TeamRewards() {
    }

    static boolean once(Quest quest, int reward) {
        return reward < quest.rewards().size() && quest.rewards().get(reward).teamMode() == TeamMode.ONCE;
    }

    static List<Integer> claimedBy(Quest quest, List<Integer> team, List<Integer> own) {
        List<Integer> claimed = new ArrayList<>(own);
        for (int reward : team) {
            if (once(quest, reward) && !claimed.contains(reward)) {
                claimed.add(reward);
            }
        }
        return claimed;
    }

    /** The team's list with the shared rewards a member has just taken added to it. */
    static List<Integer> sharedAfterClaim(Quest quest, List<Integer> team, List<Integer> own) {
        List<Integer> shared = new ArrayList<>(team);
        for (int reward : own) {
            if (once(quest, reward) && !shared.contains(reward)) {
                shared.add(reward);
            }
        }
        return shared;
    }
}
