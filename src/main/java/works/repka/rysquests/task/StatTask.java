package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Completed when a player statistic reaches a value. */
public record StatTask(ResourceLocation stat, ResourceLocation target, int count, Optional<String> title) implements TaskDef {
    public static final MapCodec<StatTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("stat").forGetter(StatTask::stat),
            ResourceLocation.CODEC.fieldOf("target").forGetter(StatTask::target),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(StatTask::count),
            Codec.STRING.optionalFieldOf("title").forGetter(StatTask::title)
    ).apply(instance, StatTask::new));

    @Override
    public TaskType<?> type() {
        return TaskTypes.STAT.get();
    }
}
