package works.repka.rysquests.progress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TeamMode;

/** Which rewards count as taken for a team member: their own, plus those only one member could take. */
final class TeamRewards {
    private TeamRewards() {
    }

    /** The players' own rows that a new round of a repeatable quest clears: everyone of the team, never the team row itself. */
    static java.util.List<java.util.UUID> rowsToReset(java.util.Collection<java.util.UUID> rows, java.util.UUID team,
            java.util.function.Function<java.util.UUID, java.util.UUID> teamOf) {
        return rows.stream().filter(row -> !row.equals(team) && team.equals(teamOf.apply(row))).toList();
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

    /**
     * Who of a team can be owed a round when a new one starts: every player whose progress is the team's, never the team's own id,
     * except under our own teams, where the team's id is its owner's.
     */
    static List<UUID> roundMembers(Collection<UUID> candidates, UUID team, Function<UUID, UUID> teamOf, boolean teamIsPlayer) {
        Set<UUID> found = new LinkedHashSet<>();
        for (UUID candidate : candidates) {
            if ((teamIsPlayer || !candidate.equals(team)) && team.equals(teamOf.apply(candidate))) {
                found.add(candidate);
            }
        }
        return List.copyOf(found);
    }

    /** The rewards only one of the team takes: when a round ends, someone has taken them all. */
    static List<Integer> onceRewards(Quest quest) {
        List<Integer> once = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (once(quest, i)) {
                once.add(i);
            }
        }
        return once;
    }

    /**
     * What a member is owed of a repeatable quest when a new round starts before they took all of this one.
     *
     * <p>The record is the finished round as the member saw it: its tasks, when it was done, and the rewards they took, the ones a
     * teammate took for the whole team counted. {@code times} counts further finished rounds owed after it, from which the member
     * took nothing yet, since the book shows them the oldest owed round first.
     *
     * @param round the team's record of the round that ends
     * @param own the rewards the member took of it for themselves
     * @param owed what the member was owed already, or null
     * @return the member's owed record, or null when they are owed nothing
     */
    @Nullable
    static QuestProgress owedAtNewRound(Quest quest, QuestProgress round, List<Integer> own, @Nullable QuestProgress owed) {
        if (owed != null) {
            // The member still takes an older round, so nothing of this one is theirs yet: it waits whole behind it.
            boolean leaves = onceRewards(quest).size() < quest.rewards().size();
            return leaves ? new QuestProgress(owed.tasks(), owed.completedAt(), owed.claimed(), 0, owed.times() + 1) : owed;
        }
        List<Integer> taken = claimedBy(quest, round.claimed(), own);
        if (taken.size() >= quest.rewards().size() || !round.completed()) {
            return null;
        }
        return new QuestProgress(round.tasks(), round.completedAt(), List.copyOf(taken), 0, 0);
    }

    /** What stays owed after the member took rewards of their owed round: the rest of it, the next owed round, or nothing. */
    @Nullable
    static QuestProgress owedAfterClaim(Quest quest, QuestProgress owed, List<Integer> claimed) {
        if (claimed.size() < quest.rewards().size()) {
            return owed.withClaimed(List.copyOf(claimed));
        }
        if (owed.times() > 0) {
            return new QuestProgress(owed.tasks(), owed.completedAt(), onceRewards(quest), 0, owed.times() - 1);
        }
        return null;
    }

    /** How the book shows an owed round: done, with the member's rewards of it, while the team's next round goes on underneath. */
    static QuestProgress owedView(QuestProgress owed, QuestProgress team) {
        return new QuestProgress(owed.tasks(), owed.completedAt(), owed.claimed(), 0, team.times());
    }
}
