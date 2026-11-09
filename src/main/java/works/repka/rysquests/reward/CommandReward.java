package works.repka.rysquests.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;

/** Runs a command for the player claiming the reward. */
public record CommandReward(String command, boolean asServer) implements RewardDef {
    public static final MapCodec<CommandReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("command").forGetter(CommandReward::command),
            Codec.BOOL.optionalFieldOf("as_server", false).forGetter(CommandReward::asServer)
    ).apply(instance, CommandReward::new));

    @Override
    public RewardType<?> type() {
        return RewardTypes.COMMAND.get();
    }
}
