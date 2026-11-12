package works.repka.rysquests.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;

/** Runs a command for the player claiming the reward. */
public record CommandReward(String command, boolean asServer, TeamMode teamMode, int level) implements RewardDef {
    /** {@code as_server} runs at {@code level}, 4 unless the book says otherwise; FTB Quests books carry their own level. */
    public static final MapCodec<CommandReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("command").forGetter(CommandReward::command),
            Codec.BOOL.optionalFieldOf("as_server", false).forGetter(CommandReward::asServer),
            TeamMode.FIELD.forGetter(CommandReward::teamMode),
            Codec.intRange(0, 4).optionalFieldOf("level", 4).forGetter(CommandReward::level)
    ).apply(instance, CommandReward::new));

    @Override
    public RewardType<?> type() {
        return RewardTypes.COMMAND.get();
    }
}
