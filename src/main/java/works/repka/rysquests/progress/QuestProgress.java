package works.repka.rysquests.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * Progress of one quest: value per task, completion time (0 while open), indexes of claimed rewards, for a
 * repeatable quest the time it becomes available again, and how many times it was done before this round.
 */
public record QuestProgress(List<Integer> tasks, long completedAt, List<Integer> claimed, long nextAt, int times) {
    public static final QuestProgress EMPTY = new QuestProgress(List.of(), 0, List.of());

    public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().optionalFieldOf("tasks", List.of()).forGetter(QuestProgress::tasks),
            Codec.LONG.optionalFieldOf("completed", 0L).forGetter(QuestProgress::completedAt),
            Codec.INT.listOf().optionalFieldOf("claimed", List.of()).forGetter(QuestProgress::claimed),
            Codec.LONG.optionalFieldOf("next_at", 0L).forGetter(QuestProgress::nextAt),
            Codec.INT.optionalFieldOf("times", 0).forGetter(QuestProgress::times)
    ).apply(instance, QuestProgress::new));

    public QuestProgress(List<Integer> tasks, long completedAt, List<Integer> claimed, long nextAt) {
        this(tasks, completedAt, claimed, nextAt, 0);
    }

    public QuestProgress(List<Integer> tasks, long completedAt, List<Integer> claimed) {
        this(tasks, completedAt, claimed, 0, 0);
    }

    public int task(int index) {
        return index < tasks.size() ? tasks.get(index) : 0;
    }

    public boolean completed() {
        return completedAt > 0;
    }

    /** Done now or in an earlier round: what opens the quests after a repeatable one, and what the counts show. */
    public boolean everDone() {
        // next_at is only ever set by a finished round, so saves from before times count too when they had a cooldown.
        return completedAt > 0 || times > 0 || nextAt > 0;
    }

    public boolean coolingDown() {
        return nextAt > System.currentTimeMillis();
    }

    public QuestProgress withClaimed(List<Integer> newClaimed) {
        return new QuestProgress(tasks, completedAt, newClaimed, nextAt, times);
    }

    /** Task values padded to the quest's task count. */
    public List<Integer> taskValues(int count) {
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(task(i));
        }
        return values;
    }
}
