package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.reward.RewardTypes;

/** One reward of a quest as written in the book. */
public interface RewardDef {
    Codec<RewardDef> CODEC = RewardTypes.REGISTRY.byNameCodec().dispatch("type", RewardDef::type, RewardType::codec);

    RewardType<?> type();

    /** Whether every member of a team gets this reward, or only the first to claim it. */
    default TeamMode teamMode() {
        return TeamMode.EACH;
    }

    /** Items this reward can give. */
    default List<ResourceLocation> itemIds() {
        return List.of();
    }

    /** Whether the reward can give nothing at all, which is an authoring error. */
    default boolean isEmpty() {
        return false;
    }
}
