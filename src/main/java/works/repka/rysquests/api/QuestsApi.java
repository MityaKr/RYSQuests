package works.repka.rysquests.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
}
