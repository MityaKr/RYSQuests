package works.repka.rysquests.reward;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.api.QuestRegistries;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;

/** The reward type registry and the built-in reward types. */
public final class RewardTypes {
    public static final DeferredRegister<RewardType<?>> REGISTER = DeferredRegister.create(QuestRegistries.REWARD_TYPES, RYSQuests.MODID);
    public static final Registry<RewardType<?>> REGISTRY = REGISTER.makeRegistry(builder -> {
    });

    public static final DeferredHolder<RewardType<?>, RewardType<ItemReward>> ITEM = register("item", ItemReward.CODEC);
    public static final DeferredHolder<RewardType<?>, RewardType<XpReward>> XP = register("xp", XpReward.CODEC);
    public static final DeferredHolder<RewardType<?>, RewardType<CommandReward>> COMMAND = register("command", CommandReward.CODEC);
    public static final DeferredHolder<RewardType<?>, RewardType<ChoiceReward>> CHOICE = register("choice", ChoiceReward.CODEC);

    public static final DeferredHolder<RewardType<?>, RewardType<RandomReward>> RANDOM = register("random", RandomReward.CODEC);

    public static final DeferredHolder<RewardType<?>, RewardType<StageReward>> STAGE = register("stage", StageReward.CODEC);
    public static final DeferredHolder<RewardType<?>, RewardType<LootTableReward>> LOOT_TABLE = register("loot_table", LootTableReward.CODEC);

    private RewardTypes() {
    }

    private static <R extends RewardDef> DeferredHolder<RewardType<?>, RewardType<R>> register(String name, MapCodec<R> codec) {
        RewardType<R> type = () -> codec;
        return REGISTER.register(name, () -> type);
    }
}
