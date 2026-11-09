package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Completed when the player earns an advancement. */
public record AdvancementTask(ResourceLocation advancement, Optional<String> title) implements TaskDef {
    public static final MapCodec<AdvancementTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("advancement").forGetter(AdvancementTask::advancement),
            Codec.STRING.optionalFieldOf("title").forGetter(AdvancementTask::title)
    ).apply(instance, AdvancementTask::new));

    @Override
    public TaskType<?> type() {
        return TaskTypes.ADVANCEMENT.get();
    }
}
