package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Completed after killing a number of entities of one type. */
public record KillTask(ResourceLocation entity, int count, Optional<String> title) implements TaskDef {
    public static final MapCodec<KillTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(KillTask::entity),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(KillTask::count),
            Codec.STRING.optionalFieldOf("title").forGetter(KillTask::title)
    ).apply(instance, KillTask::new));

    @Override
    public TaskType<?> type() {
        return TaskTypes.KILL.get();
    }
}
