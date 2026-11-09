package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Progress comes from a script, asked on the same schedule as other polled tasks. */
public record ScriptTask(ResourceLocation script, int count, Optional<String> title) implements TaskDef {
    public static final MapCodec<ScriptTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("script").forGetter(ScriptTask::script),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ScriptTask::count),
            Codec.STRING.optionalFieldOf("title").forGetter(ScriptTask::title)
    ).apply(instance, ScriptTask::new));

    @Override
    public TaskType<?> type() {
        return TaskTypes.SCRIPT.get();
    }

    @Override
    public int target() {
        return count;
    }
}
