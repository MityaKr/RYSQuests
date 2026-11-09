package works.repka.rysquests.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;

/** Grants a progression stage through every stage provider. */
public record StageReward(String stage, TeamMode teamMode) implements RewardDef {
    public static final MapCodec<StageReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("stage").forGetter(StageReward::stage),
            TeamMode.FIELD.forGetter(StageReward::teamMode)
    ).apply(instance, StageReward::new));

    @Override
    public RewardType<?> type() {
        return RewardTypes.STAGE.get();
    }
}
