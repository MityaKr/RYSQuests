package works.repka.rysquests.integration.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** {@code RYSQuestsEvents.reward}: the player claimed a script reward. */
public final class ScriptRewardKubeEvent implements KubeEvent {
    private final ServerPlayer player;
    private final ResourceLocation script;

    ScriptRewardKubeEvent(ServerPlayer player, ResourceLocation script) {
        this.player = player;
        this.script = script;
    }

    public ResourceLocation getScript() {
        return script;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public MinecraftServer getServer() {
        return player.server;
    }
}
