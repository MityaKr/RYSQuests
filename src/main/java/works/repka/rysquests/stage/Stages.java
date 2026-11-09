package works.repka.rysquests.stage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.api.StageProvider;

/** All stage providers. The built-in one treats a stage as an advancement id. */
public final class Stages {
    private static final List<StageProvider> PROVIDERS = new CopyOnWriteArrayList<>(List.of(new AdvancementStages()));

    private Stages() {
    }

    public static void register(StageProvider provider) {
        PROVIDERS.add(provider);
    }

    public static boolean has(ServerPlayer player, String stage) {
        return PROVIDERS.stream().anyMatch(provider -> provider.has(player, stage));
    }

    public static void grant(ServerPlayer player, String stage) {
        PROVIDERS.forEach(provider -> provider.grant(player, stage));
    }

    private static final class AdvancementStages implements StageProvider {
        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "advancements");
        }

        private static AdvancementHolder find(ServerPlayer player, String stage) {
            ResourceLocation id = ResourceLocation.tryParse(stage);
            return id == null ? null : player.server.getAdvancements().get(id);
        }

        @Override
        public boolean has(ServerPlayer player, String stage) {
            AdvancementHolder advancement = find(player, stage);
            return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
        }

        @Override
        public void grant(ServerPlayer player, String stage) {
            AdvancementHolder advancement = find(player, stage);
            if (advancement != null) {
                player.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria()
                        .forEach(criterion -> player.getAdvancements().award(advancement, criterion));
            }
        }
    }
}
