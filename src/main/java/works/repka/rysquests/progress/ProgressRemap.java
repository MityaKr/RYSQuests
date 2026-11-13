package works.repka.rysquests.progress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import works.repka.rysquests.data.Quest;

/**
 * Progress is kept by the position of a task or a reward. When the editor reorders, adds or removes them on a live server, the
 * saved values follow their own task and reward instead of sliding onto a neighbour, which could even pay a reward twice
 * (review C1). Pure, so that it can be tested.
 */
public final class ProgressRemap {
    private ProgressRemap() {
    }

    /**
     * Where each task and reward of the new quest comes from in the old one.
     *
     * @param tasks old task index for each new task, -1 for a new task
     * @param rewards old reward index for each new reward, -1 for a new reward
     */
    public record Mapping(int[] tasks, int[] rewards, int oldTasks, int oldRewards) {
        /** Nothing moved and nothing was added or removed. */
        public boolean identity() {
            return identity(tasks, oldTasks) && identity(rewards, oldRewards);
        }

        private static boolean identity(int[] from, int oldSize) {
            if (from.length != oldSize) {
                return false;
            }
            for (int i = 0; i < from.length; i++) {
                if (from[i] != i) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "Mapping" + Arrays.toString(tasks) + Arrays.toString(rewards);
        }
    }

    public static Mapping between(Quest before, Quest after) {
        return new Mapping(match(before.tasks(), after.tasks()), match(before.rewards(), after.rewards()), before.tasks().size(), before.rewards().size());
    }

    /**
     * Each new entry takes the first unused old entry equal to it. One that has no equal, because the author changed it, takes the
     * old entry at its own place when that one is unused and of the same kind: a task whose count went up keeps what was done.
     */
    static <T> int[] match(List<T> before, List<T> after) {
        int[] from = new int[after.size()];
        boolean[] used = new boolean[before.size()];
        Arrays.fill(from, -1);
        for (int i = 0; i < after.size(); i++) {
            for (int j = 0; j < before.size(); j++) {
                if (!used[j] && before.get(j).equals(after.get(i))) {
                    from[i] = j;
                    used[j] = true;
                    break;
                }
            }
        }
        for (int i = 0; i < after.size(); i++) {
            if (from[i] < 0 && i < before.size() && !used[i] && before.get(i).getClass() == after.get(i).getClass()) {
                from[i] = i;
                used[i] = true;
            }
        }
        return from;
    }

    /** The same progress, with values and claims moved to where their tasks and rewards are now. Completion stays as it was. */
    public static QuestProgress apply(QuestProgress progress, Mapping mapping) {
        if (progress.tasks().isEmpty() && progress.claimed().isEmpty()) {
            return progress;
        }
        List<Integer> tasks = new ArrayList<>();
        if (!progress.tasks().isEmpty()) {
            for (int from : mapping.tasks()) {
                tasks.add(from < 0 ? 0 : progress.task(from));
            }
        }
        List<Integer> claimed = new ArrayList<>();
        for (int i = 0; i < mapping.rewards().length; i++) {
            if (mapping.rewards()[i] >= 0 && progress.claimed().contains(mapping.rewards()[i])) {
                claimed.add(i);
            }
        }
        return new QuestProgress(List.copyOf(tasks), progress.completedAt(), List.copyOf(claimed), progress.nextAt(), progress.times());
    }
}
