package works.repka.rysquests.api;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Fired on the game bus when all tasks of a quest are done. The player is whoever completed the last one. */
public final class QuestCompletedEvent extends Event {
    private final UUID team;
    private final ResourceLocation quest;
    private final ServerPlayer player;

    public QuestCompletedEvent(UUID team, ResourceLocation quest, ServerPlayer player) {
        this.team = team;
        this.quest = quest;
        this.player = player;
    }

    public UUID team() {
        return team;
    }

    public ResourceLocation quest() {
        return quest;
    }

    public ServerPlayer player() {
        return player;
    }
}
