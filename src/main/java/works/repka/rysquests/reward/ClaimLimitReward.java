package works.repka.rysquests.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;

/**
 * More land: chunks to claim and chunks to keep loaded on top of the limit (RYSClaims, RYSCore {@code claims.ClaimsApi}; plan of the
 * map, section 8, RYSQuests). {@code each} gives every member a bonus of their own, which goes with them to another team
 * ({@code addPlayerBonus}); {@code once} gives the team one bonus that stays with it ({@code addBonus}). Without RYSClaims there is
 * no limit to raise, and the reward gives nothing.
 */
public record ClaimLimitReward(int claims, int forceLoaded, TeamMode teamMode) implements RewardDef {
    public static final MapCodec<ClaimLimitReward> CODEC = RecordCodecBuilder.<ClaimLimitReward>mapCodec(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("claims", 0).forGetter(ClaimLimitReward::claims),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("force_loaded", 0).forGetter(ClaimLimitReward::forceLoaded),
            TeamMode.FIELD.forGetter(ClaimLimitReward::teamMode)
    ).apply(instance, ClaimLimitReward::new)).validate(reward -> reward.claims > 0 || reward.forceLoaded > 0
            ? com.mojang.serialization.DataResult.success(reward)
            : com.mojang.serialization.DataResult.error(() -> "Claim limit reward needs claims or force_loaded above 0"));

    @Override
    public RewardType<?> type() {
        return RewardTypes.CLAIM_LIMIT.get();
    }
}
