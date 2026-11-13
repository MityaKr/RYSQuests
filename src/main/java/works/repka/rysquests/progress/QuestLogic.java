package works.repka.rysquests.progress;

import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.task.ItemTask;

/** Status rules shared by the server and the client's display. */
public final class QuestLogic {
    private QuestLogic() {
    }

    public static boolean isUnlocked(Quest quest, Function<ResourceLocation, QuestProgress> progress) {
        if (quest.requires().isEmpty()) {
            return true;
        }
        // A repeatable quest between rounds has been done: what it opened stays open (no new round locks it again).
        long done = quest.requires().stream().filter(id -> progress.apply(id).everDone()).count();
        return switch (quest.requiresMode()) {
            case RequiresMode.AtLeast atLeast -> done >= Math.min(atLeast.n(), quest.requires().size());
            case RequiresMode.Named named -> named == RequiresMode.ANY ? done > 0 : done == quest.requires().size();
        };
    }

    /** A quest players see and the server counts: neither a draft nor in the archive. */
    public static boolean isLive(Quest quest) {
        return quest.options().live();
    }

    /**
     * How much of a task is still missing when the player holds {@code held} of what it asks for. A task in craft mode counts
     * only what is crafted after the quest opened, so what lies in the inventory does not bring it closer.
     */
    public static int stillNeeded(TaskDef task, int value, int held) {
        int missing = Math.max(0, task.target() - value);
        if (task instanceof ItemTask item && item.mode() == ItemTask.Mode.CRAFT) {
            return missing;
        }
        return Math.max(0, missing - Math.max(0, held));
    }

    /** Whether every task has reached its target in these values. */
    public static boolean allTasksDone(Quest quest, java.util.List<Integer> values) {
        for (int i = 0; i < quest.tasks().size(); i++) {
            if ((i < values.size() ? values.get(i) : 0) < quest.tasks().get(i).target()) {
                return false;
            }
        }
        return true;
    }

    /** The quest's progress with these task values: completed now when every task is done, open otherwise. */
    public static QuestProgress finish(Quest quest, QuestProgress before, java.util.List<Integer> values, long now) {
        long completedAt = allTasksDone(quest, values) ? now : before.completedAt();
        return new QuestProgress(java.util.List.copyOf(values), completedAt, before.claimed(), before.nextAt(), before.times());
    }

    /**
     * What a poll of an open quest writes: the new values when a task grew; and also the completion of a quest whose tasks were all
     * done already but which never completed. Such a record comes from outside the poll: FTB Quests' progress taken over by the
     * import (FTB counts the tasks of a quest whose dependencies are not done yet), a migration that merges two records, or an
     * edit that lowered a task's target. Without this the quest would wait for a task to grow past its target, which never
     * happens, while the book rightly says the task is done (RepYS, «Тертиевая эссенция»).
     *
     * @param before  the stored progress of the quest, open and not cooling down
     * @param values  the task values after this poll
     * @param changed whether this poll raised any value
     */
    public static java.util.Optional<QuestProgress> afterPoll(Quest quest, QuestProgress before, java.util.List<Integer> values, boolean changed, long now) {
        boolean stuck = !before.completed() && allTasksDone(quest, values);
        return changed || stuck || quest.tasks().isEmpty() ? java.util.Optional.of(finish(quest, before, values, now)) : java.util.Optional.empty();
    }

    /**
     * A quest that is open, has every task done and yet is not completed: the state {@link #afterPoll} completes. Found at login
     * too, so that records stuck before this rule are mended without a reset (RepYS: «Тертиевая эссенция», the gate of Oritech).
     * Stages are the server's to add.
     */
    public static boolean stuck(Quest quest, Function<ResourceLocation, QuestProgress> progress) {
        QuestProgress own = progress.apply(quest.id());
        return isLive(quest) && !quest.tasks().isEmpty() && !own.completed() && !own.coolingDown() && isUnlocked(quest, progress)
                && allTasksDone(quest, own.tasks());
    }

    public static boolean allClaimed(Quest quest, QuestProgress progress) {
        return progress.claimed().size() >= quest.rewards().size();
    }

    public static QuestStatus status(Quest quest, Function<ResourceLocation, QuestProgress> progress) {
        QuestProgress own = progress.apply(quest.id());
        if (own.completed()) {
            return allClaimed(quest, own) ? QuestStatus.CLAIMED : QuestStatus.COMPLETED;
        }
        if (own.coolingDown() || !isUnlocked(quest, progress)) {
            return QuestStatus.LOCKED;
        }
        return own.tasks().stream().anyMatch(value -> value > 0) ? QuestStatus.IN_PROGRESS : QuestStatus.AVAILABLE;
    }
}
