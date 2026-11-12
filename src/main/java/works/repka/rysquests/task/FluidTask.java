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

    /** How much of this fluid the inventory's containers hold, by the same capability on the server and in the book. */
    public int amountIn(net.minecraft.world.entity.player.Inventory inventory) {
        net.minecraft.world.level.material.Fluid kind = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(fluid);
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var handler = inventory.getItem(i).getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM);
            if (handler != null) {
                total += handler.drain(new net.neoforged.neoforge.fluids.FluidStack(kind, Integer.MAX_VALUE),
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE).getAmount();
            }
        }
        return total;
    }

    @Override
    public int target() {
        return amount;
    }

    @Override
    public TaskType<?> type() {
        return TaskTypes.FLUID.get();
    }
}
