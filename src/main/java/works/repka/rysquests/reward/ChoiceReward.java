package works.repka.rysquests.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;

/** Lets the player pick one of several rewards. */
public record ChoiceReward(List<RewardDef> of, TeamMode teamMode) implements RewardDef {
    public static final MapCodec<ChoiceReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.lazyInitialized(() -> RewardDef.CODEC).listOf().fieldOf("of").forGetter(ChoiceReward::of),
            TeamMode.FIELD.forGetter(ChoiceReward::teamMode)
    ).apply(instance, ChoiceReward::new));

    @Override
    public RewardType<?> type() {
        return RewardTypes.CHOICE.get();
    }

    @Override
    public List<ResourceLocation> itemIds() {
        return of.stream().flatMap(reward -> reward.itemIds().stream()).toList();
    }

    @Override
    public boolean isEmpty() {
        return of.isEmpty();
    }
}
