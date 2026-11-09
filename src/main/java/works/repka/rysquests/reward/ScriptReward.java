package works.repka.rysquests.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;

/** What the player gets is up to a script. */
public record ScriptReward(ResourceLocation script, TeamMode teamMode) implements RewardDef {
    public static final MapCodec<ScriptReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("script").forGetter(ScriptReward::script),
            TeamMode.FIELD.forGetter(ScriptReward::teamMode)
    ).apply(instance, ScriptReward::new));

    @Override
    public RewardType<?> type() {
        return RewardTypes.SCRIPT.get();
    }
}
