package works.repka.rysquests.progress;

import java.util.List;
import java.util.Optional;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.ItemTask;

/**
 * The rules of an achievement's collection (ADR-62), shared by the server, the window and the tests. What is collected lives in the
 * quest of marks ({@link works.repka.rysquests.data.Achievement#collectionQuestId}): one item task per entry, done once the entry has
 * been held. A level counts the marks.
 */
public final class CollectionMarks {
    private CollectionMarks() {
    }

    /** Whether the entry at this place is collected. */
    public static boolean collected(Quest marks, QuestProgress progress, int index) {
        return index >= 0 && index < marks.tasks().size() && progress.task(index) >= marks.tasks().get(index).target();
    }

    /** How many entries are collected. */
    public static int count(Quest marks, QuestProgress progress) {
        int found = 0;
        for (int i = 0; i < marks.tasks().size(); i++) {
            found += collected(marks, progress, i) ? 1 : 0;
        }
        return found;
    }

    /**
     * A level's value after a poll: the number of marks, never past the level's amount and never lower than it was, so that a level
     * keeps what it counted when the author takes an entry out of the list.
     */
    public static int levelValue(int current, int count, int target) {
        return Math.max(current, Math.min(target, count));
    }

    /**
     * The entry an item belongs to, for a script that carries marks over from elsewhere.
     *
     * @param item an item id, or a tag written with {@code #}
     * @return its place, or -1 when no entry names it
     */
    public static int indexOf(Quest marks, String item) {
        List<TaskDef> tasks = marks.tasks();
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i) instanceof ItemTask entry) {
                for (ItemRef ref : entry.itemRefs()) {
                    if (ref.toString().equals(item)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * The marks with one more entry collected.
     *
     * @return the new progress, empty when the entry was collected already or there is no such place
     */
    public static Optional<QuestProgress> mark(Quest marks, QuestProgress before, int index, long now) {
        if (index < 0 || index >= marks.tasks().size() || collected(marks, before, index)) {
            return Optional.empty();
        }
        List<Integer> values = before.taskValues(marks.tasks().size());
        values.set(index, marks.tasks().get(index).target());
        return Optional.of(QuestLogic.finish(marks, before, values, now));
    }
}
