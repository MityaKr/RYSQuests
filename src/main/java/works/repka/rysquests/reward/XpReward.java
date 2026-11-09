package works.repka.rysquests.reward;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;

/** Gives experience, either in levels or in points. */
public record XpReward(Optional<Integer> levels, Optional<Integer> points) implements RewardDef {
    public static final MapCodec<XpReward> CODEC = RecordCodecBuilder.<XpReward>mapCodec(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("levels").forGetter(XpReward::levels),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("points").forGetter(XpReward::points)
    ).apply(instance, XpReward::new)).validate(XpReward::checkAmount);

    private static DataResult<XpReward> checkAmount(XpReward reward) {
        return reward.levels.isPresent() != reward.points.isPresent()
                ? DataResult.success(reward)
                : DataResult.error(() -> "Xp reward needs exactly one of 'levels' and 'points'");
    }

    @Override
    public RewardType<?> type() {
        return RewardTypes.XP.get();
    }
}
