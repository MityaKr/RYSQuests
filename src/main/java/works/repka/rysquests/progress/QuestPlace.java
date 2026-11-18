package works.repka.rysquests.progress;

import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.task.VisitTask;

/**
 * Where a quest sends the player, for the map (RYSMap plan, step T3): the first visit task of the quest that names a position. A
 * dimension, a biome or a structure alone has no place on the map: it may be anywhere, and the client cannot look for it.
 *
 * <p>The place is the centre of the task's sphere, or the middle of its box when the task has a {@code size} (ADR-48); its radius
 * is the sphere's radius, or half the box's longest side. A task without a {@code dimension} counts in any dimension, so its place
 * is put in the overworld.
 *
 * @param task the index of the visit task in the quest
 */
public record QuestPlace(ResourceLocation dimension, double x, double y, double z, float radius, int task) {
    public static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");

    /** The place of a quest whatever its progress, or empty when no visit task of it names a position. */
    public static Optional<QuestPlace> of(Quest quest) {
        return find(quest, index -> true);
    }

    /**
     * The place still ahead: the first visit task with a position that is not done yet, by the task values of the quest's progress
     * (missing values count as zero). Empty when every such task is done, or there is none.
     */
    public static Optional<QuestPlace> ahead(Quest quest, List<Integer> values) {
        return find(quest, index -> (index < values.size() ? values.get(index) : 0) < quest.tasks().get(index).target());
    }

    private static Optional<QuestPlace> find(Quest quest, IntPredicate open) {
        List<TaskDef> tasks = quest.tasks();
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i) instanceof VisitTask visit && visit.pos().isPresent() && open.test(i)) {
                return Optional.of(of(visit, i));
            }
        }
        return Optional.empty();
    }

    /** The place of one visit task that names a position. @param index the task's index in its quest */
    public static QuestPlace of(VisitTask visit, int index) {
        List<Integer> pos = visit.pos().orElseThrow();
        ResourceLocation dimension = visit.dimension().orElse(OVERWORLD);
        if (visit.size().isPresent()) {
            List<Integer> size = visit.size().get();
            float half = Math.max(size.get(0), Math.max(size.get(1), size.get(2))) / 2f;
            return new QuestPlace(dimension, pos.get(0) + size.get(0) / 2.0, pos.get(1) + size.get(1) / 2.0, pos.get(2) + size.get(2) / 2.0, half, index);
        }
        // The server measures the sphere from the position itself (ProgressService.isVisiting), not from the block's middle.
        return new QuestPlace(dimension, pos.get(0), pos.get(1), pos.get(2), visit.radius(), index);
    }
}
