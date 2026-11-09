package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Completed by being somewhere: a dimension, a biome or structure (each may be a {@code #tag}), or near a position. */
public record VisitTask(Optional<ResourceLocation> dimension, Optional<ItemRef> biome, Optional<ItemRef> structure,
        Optional<List<Integer>> pos, int radius, Optional<String> title) implements TaskDef {
    public static final MapCodec<VisitTask> CODEC = RecordCodecBuilder.<VisitTask>mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(VisitTask::dimension),
            ItemRef.CODEC.optionalFieldOf("biome").forGetter(VisitTask::biome),
            ItemRef.CODEC.optionalFieldOf("structure").forGetter(VisitTask::structure),
            Codec.INT.listOf(3, 3).optionalFieldOf("pos").forGetter(VisitTask::pos),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("radius", 8).forGetter(VisitTask::radius),
            Codec.STRING.optionalFieldOf("title").forGetter(VisitTask::title)
    ).apply(instance, VisitTask::new)).validate(VisitTask::checkTarget);

    private static DataResult<VisitTask> checkTarget(VisitTask task) {
        return task.dimension.isPresent() || task.biome.isPresent() || task.structure.isPresent() || task.pos.isPresent()
                ? DataResult.success(task)
                : DataResult.error(() -> "Visit task needs a dimension, biome, structure or pos");
    }

    @Override
    public TaskType<?> type() {
        return TaskTypes.VISIT.get();
    }
}
