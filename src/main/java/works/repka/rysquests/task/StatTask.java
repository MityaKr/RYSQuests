package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Completed when a player statistic reaches a value. {@code entry} is the JSON field {@code target}. */
public record StatTask(ResourceLocation stat, ResourceLocation entry, int count, Optional<String> title) implements TaskDef {
    public static final MapCodec<StatTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("stat").forGetter(StatTask::stat),
            ResourceLocation.CODEC.fieldOf("target").forGetter(StatTask::entry),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(StatTask::count),
            Codec.STRING.optionalFieldOf("title").forGetter(StatTask::title)
    ).apply(instance, StatTask::new));

    @Override
    public int target() {
        return count;
    }

    @Override
    public TaskType<?> type() {
        return TaskTypes.STAT.get();
    }
}
