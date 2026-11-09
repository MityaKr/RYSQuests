package works.repka.rysquests.integration.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import works.repka.rysquests.api.QuestCompletedEvent;

/** {@code RYSQuestsEvents.completed}: all tasks of a quest are done. */
public final class QuestCompletedKubeEvent implements KubeEvent {
    private final QuestCompletedEvent event;

    QuestCompletedKubeEvent(QuestCompletedEvent event) {
        this.event = event;
    }

    public ResourceLocation getQuest() {
        return event.quest();
    }

    public UUID getTeam() {
        return event.team();
    }

    public ServerPlayer getPlayer() {
        return event.player();
    }

    public MinecraftServer getServer() {
        return event.player().server;
    }
}
