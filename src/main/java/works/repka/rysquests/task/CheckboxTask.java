package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Completed when the player ticks it. */
public record CheckboxTask(Optional<String> title) implements TaskDef {
    public static final MapCodec<CheckboxTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("title").forGetter(CheckboxTask::title)
    ).apply(instance, CheckboxTask::new));

    @Override
    public TaskType<?> type() {
        return TaskTypes.CHECKBOX.get();
    }
}
