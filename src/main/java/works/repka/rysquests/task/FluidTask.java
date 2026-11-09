package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Requires an amount of a fluid in containers the player carries, in millibuckets. */
public record FluidTask(ResourceLocation fluid, int amount, ItemTask.Mode mode, Optional<String> title) implements TaskDef {
    public static final MapCodec<FluidTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("fluid").forGetter(FluidTask::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidTask::amount),
            ItemTask.Mode.CODEC.optionalFieldOf("mode", ItemTask.Mode.CONSUME).forGetter(FluidTask::mode),
            Codec.STRING.optionalFieldOf("title").forGetter(FluidTask::title)
    ).apply(instance, FluidTask::new));

    @Override
    public int target() {
        return amount;
    }

    @Override
    public TaskType<?> type() {
        return TaskTypes.FLUID.get();
    }
}
