package works.repka.rysquests.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;

/** Gives a title, which a player can then show beside their name. @param id a lang key */
public record TitleReward(String id, TeamMode teamMode) implements RewardDef {
    public static final MapCodec<TitleReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(TitleReward::id),
            TeamMode.FIELD.forGetter(TitleReward::teamMode)
    ).apply(instance, TitleReward::new));

    @Override
    public RewardType<?> type() {
        return RewardTypes.TITLE.get();
    }
}
