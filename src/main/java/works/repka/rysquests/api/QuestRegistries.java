package works.repka.rysquests.api;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** Keys of the registries other mods add task and reward types to. */
public final class QuestRegistries {
    public static final ResourceKey<Registry<TaskType<?>>> TASK_TYPES = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "task_type"));
    public static final ResourceKey<Registry<RewardType<?>>> REWARD_TYPES = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "reward_type"));

    private QuestRegistries() {
    }
}
