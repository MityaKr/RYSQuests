package works.repka.rysquests.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;

/** Gives a stack of an item. */
public record ItemReward(ResourceLocation item, int count, Optional<DataComponentPatch> components, TeamMode teamMode) implements RewardDef {
    public static final MapCodec<ItemReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(ItemReward::item),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ItemReward::count),
            DataComponentPatch.CODEC.optionalFieldOf("components").forGetter(ItemReward::components),
            TeamMode.FIELD.forGetter(ItemReward::teamMode)
    ).apply(instance, ItemReward::new));

    @Override
    public RewardType<?> type() {
        return RewardTypes.ITEM.get();
    }

    @Override
    public List<ResourceLocation> itemIds() {
        return List.of(item);
    }
}
