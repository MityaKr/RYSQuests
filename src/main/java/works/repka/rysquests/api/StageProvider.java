package works.repka.rysquests.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** A source of progression stages. A stage counts as reached when any provider confirms it. */
public interface StageProvider {
    ResourceLocation id();

    boolean has(ServerPlayer player, String stage);

    void grant(ServerPlayer player, String stage);
}
