package works.repka.rysquests.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;

/** Gives currency. With {@code team_mode: once} it goes to the treasury of the team. */
public record CurrencyReward(String id, int amount, TeamMode teamMode) implements RewardDef {
    public static final MapCodec<CurrencyReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(CurrencyReward::id),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(CurrencyReward::amount),
            TeamMode.FIELD.forGetter(CurrencyReward::teamMode)
    ).apply(instance, CurrencyReward::new));

    @Override
    public RewardType<?> type() {
        return RewardTypes.CURRENCY.get();
    }
}
