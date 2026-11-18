package works.repka.rysquests.api;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.stage.Stages;

/** What other mods and scripts can do with quests. Everything outside the {@code api} package may change. */
public final class QuestsApi {
    private QuestsApi() {
    }

    public static QuestBook book() {
        return BookHolder.current().book();
    }

    public static void registerStageProvider(StageProvider provider) {
        Stages.register(provider);
    }

    /** Marks a quest done or resets it for the player's team. @return false if there is no such quest */
    public static boolean setCompleted(ServerPlayer player, ResourceLocation quest, boolean completed) {
        Quest target = book().quests().get(quest);
        if (target == null) {
            return false;
        }
        ProgressService.setCompleted(player, target, completed);
        return true;
    }

    /** The owner of the progress the player counts towards: the team, the player alone or the server, by the book's mode. */
    public static UUID progressOwner(ServerPlayer player) {
        return ProgressService.owner(player);
    }

    /**
     * Marks an item of an achievement's collection as collected for an owner of progress, online or not (ADR-62). Meant for carrying
     * over marks a script kept before collections came to the book; marking twice changes nothing. KubeJS server scripts see this
     * class as {@code RYSQuests}.
     *
     * @param owner a team or player id as {@link #progressOwner} gives it, written as a string
     * @param achievement the achievement's id, such as {@code repys:collection_awakened_cores}
     * @param item an item id, or a tag written with {@code #}, as the collection names it
     * @return whether the item is collected now; false when there is no such collection, entry or owner
     */
    public static boolean collect(MinecraftServer server, String owner, String achievement, String item) {
        ResourceLocation id = ResourceLocation.tryParse(achievement);
        Achievement target = id == null ? null : book().achievements().get(id);
        UUID ownerId;
        try {
            ownerId = UUID.fromString(owner);
        } catch (IllegalArgumentException notAnId) {
            return false;
        }
        return target != null && ProgressService.collect(server, ownerId, target, item);
    }

    /** The same for the owner of the progress this player counts towards. */
    public static boolean collect(ServerPlayer player, String achievement, String item) {
        return collect(player.server, progressOwner(player).toString(), achievement, item);
    }
}
