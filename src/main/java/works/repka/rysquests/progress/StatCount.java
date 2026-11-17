package works.repka.rysquests.progress;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.task.StatTask;

/**
 * How progress that is shared counts a player statistic (ADR-61). Every player has their own statistic, so a team's counter is made
 * from all of its members': summed, as kills of a team add up, so that what any member does moves it. A streak, a statistic the game
 * starts again from zero (time since death or since rest), is the longest current one of the members instead, and the counter follows
 * it down when it starts again: the achievement asks for one life that long, as its text says.
 *
 * <p>Before 0.2.8 a poll took the larger of the stored counter and the polling player's own statistic, so a team's counter was the
 * best member's number alone and the others could not move it (RepYS: «Парашютист» stood at a teammate's 19.7 of 20 km while the
 * author fell; «Долгожитель» kept a teammate's long gone 49.2 hours although death starts the count again).
 */
public final class StatCount {
    private static final ResourceLocation CUSTOM = ResourceLocation.withDefaultNamespace("custom");
    private static final Set<ResourceLocation> STREAKS = Set.of(
            ResourceLocation.withDefaultNamespace("time_since_death"),
            ResourceLocation.withDefaultNamespace("time_since_rest"));

    private StatCount() {
    }

    /** Whether the game starts this statistic again from zero (a death, a night in bed). */
    public static boolean isStreak(StatTask task) {
        return task.stat().equals(CUSTOM) && STREAKS.contains(task.entry());
    }

    /**
     * The shared counter from each member's own statistic.
     *
     * @param values the statistic of every player who shares the progress, online or not
     * @return their sum, at most {@link Integer#MAX_VALUE}; for a streak the longest of them
     */
    public static int combine(boolean streak, int[] values) {
        long total = 0;
        int longest = 0;
        for (int value : values) {
            int own = Math.max(0, value);
            total += own;
            longest = Math.max(longest, own);
        }
        return streak ? longest : (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * The task's value after a poll. A counter only grows: what the members did stays done when one leaves the team. A streak follows
     * the count, down too, since it is the length of the current one.
     *
     * @param current the stored value
     * @param count   {@link #combine} of the members' statistics now
     * @param target  the task's target; the value never goes past it
     */
    public static int next(boolean streak, int current, int count, int target) {
        int capped = Math.min(target, count);
        return streak ? capped : Math.max(current, capped);
    }
}
