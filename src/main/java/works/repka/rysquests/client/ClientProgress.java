package works.repka.rysquests.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.network.ProgressPayload;
import works.repka.rysquests.progress.QuestProgress;

/** The client's copy of its own progress. Changed only by packets, on the main thread. */
public final class ClientProgress {
    private static final Map<ResourceLocation, QuestProgress> QUESTS = new HashMap<>();

    private ClientProgress() {
    }

    public static QuestProgress get(ResourceLocation quest) {
        return QUESTS.getOrDefault(quest, QuestProgress.EMPTY);
    }

    static void receive(ProgressPayload payload) {
        if (payload.full()) {
            QUESTS.clear();
        }
        QUESTS.putAll(payload.quests());
    }

    static void clear() {
        QUESTS.clear();
    }
}
