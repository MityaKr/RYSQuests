package works.repka.rysquests.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;

/** Gives one of several rewards at random; {@code weights} is parallel to {@code of}, empty means equal odds. */
public record RandomReward(List<RewardDef> of, List<Integer> weights, TeamMode teamMode) implements RewardDef {
    public static final MapCodec<RandomReward> CODEC = RecordCodecBuilder.<RandomReward>mapCodec(instance -> instance.group(
            Codec.lazyInitialized(() -> RewardDef.CODEC).listOf().fieldOf("of").forGetter(RandomReward::of),
            Codec.intRange(0, Integer.MAX_VALUE).listOf().optionalFieldOf("weights", List.of()).forGetter(RandomReward::weights),
            TeamMode.FIELD.forGetter(RandomReward::teamMode)
    ).apply(instance, RandomReward::new)).validate(RandomReward::checkWeights);

    private static DataResult<RandomReward> checkWeights(RandomReward reward) {
        return reward.weights.isEmpty() || reward.weights.size() == reward.of.size()
                ? DataResult.success(reward)
                : DataResult.error(() -> "'weights' must match 'of' in size");
    }

    @Override
    public RewardType<?> type() {
        return RewardTypes.RANDOM.get();
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
