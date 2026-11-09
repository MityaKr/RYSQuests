package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Completed by being in a dimension or inside a structure, which may be a {@code #tag}. */
public record VisitTask(Optional<ResourceLocation> dimension, Optional<ItemRef> structure, Optional<String> title) implements TaskDef {
    public static final MapCodec<VisitTask> CODEC = RecordCodecBuilder.<VisitTask>mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(VisitTask::dimension),
            ItemRef.CODEC.optionalFieldOf("structure").forGetter(VisitTask::structure),
            Codec.STRING.optionalFieldOf("title").forGetter(VisitTask::title)
    ).apply(instance, VisitTask::new)).validate(VisitTask::checkTarget);

    private static DataResult<VisitTask> checkTarget(VisitTask task) {
        return task.dimension.isPresent() || task.structure.isPresent()
                ? DataResult.success(task)
                : DataResult.error(() -> "Visit task needs 'dimension' or 'structure'");
    }

    @Override
    public TaskType<?> type() {
        return TaskTypes.VISIT.get();
    }
}
