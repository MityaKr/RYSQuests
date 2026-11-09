package works.repka.rysquests.integration.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** {@code RYSQuestsEvents.task}: asked about once a second while the quest is open. */
public final class ScriptTaskKubeEvent implements KubeEvent {
    private final ServerPlayer player;
    private final ResourceLocation script;
    private int progress;

    ScriptTaskKubeEvent(ServerPlayer player, ResourceLocation script, int progress) {
        this.player = player;
        this.script = script;
        this.progress = progress;
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

    public int getProgress() {
        return progress;
    }

    public void setProgress(int value) {
        progress = value;
    }
}
