package works.repka.rysquests.progress;

import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RequiresMode;

/** Status rules shared by the server and the client's display. */
public final class QuestLogic {
    private QuestLogic() {
    }

    public static boolean isUnlocked(Quest quest, Function<ResourceLocation, QuestProgress> progress) {
        if (quest.requires().isEmpty()) {
            return true;
        }
        // A repeatable quest between rounds has been done: what it opened stays open (no new round locks it again).
        long done = quest.requires().stream().filter(id -> progress.apply(id).everDone()).count();
        return switch (quest.requiresMode()) {
            case RequiresMode.AtLeast atLeast -> done >= Math.min(atLeast.n(), quest.requires().size());
            case RequiresMode.Named named -> named == RequiresMode.ANY ? done > 0 : done == quest.requires().size();
        };
    }

    public static boolean allClaimed(Quest quest, QuestProgress progress) {
        return progress.claimed().size() >= quest.rewards().size();
    }

    public static QuestStatus status(Quest quest, Function<ResourceLocation, QuestProgress> progress) {
        QuestProgress own = progress.apply(quest.id());
        if (own.completed()) {
            return allClaimed(quest, own) ? QuestStatus.CLAIMED : QuestStatus.COMPLETED;
        }
        if (own.coolingDown() || !isUnlocked(quest, progress)) {
            return QuestStatus.LOCKED;
        }
        return own.tasks().stream().anyMatch(value -> value > 0) ? QuestStatus.IN_PROGRESS : QuestStatus.AVAILABLE;
    }
}
