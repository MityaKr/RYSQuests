package works.repka.rysquests.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/** Progress of one quest: value per task, completion time (0 while open) and indexes of claimed rewards. */
public record QuestProgress(List<Integer> tasks, long completedAt, List<Integer> claimed) {
    public static final QuestProgress EMPTY = new QuestProgress(List.of(), 0, List.of());

    public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().optionalFieldOf("tasks", List.of()).forGetter(QuestProgress::tasks),
            Codec.LONG.optionalFieldOf("completed", 0L).forGetter(QuestProgress::completedAt),
            Codec.INT.listOf().optionalFieldOf("claimed", List.of()).forGetter(QuestProgress::claimed)
    ).apply(instance, QuestProgress::new));

    public int task(int index) {
        return index < tasks.size() ? tasks.get(index) : 0;
    }

    public boolean completed() {
        return completedAt > 0;
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
