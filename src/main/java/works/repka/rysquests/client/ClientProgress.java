package works.repka.rysquests.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.hud.QuestToast;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.network.PinnedPayload;
import works.repka.rysquests.network.ProgressPayload;
import works.repka.rysquests.network.StageLocksPayload;
import works.repka.rysquests.progress.QuestProgress;

/** The client's copy of its own progress. Changed only by packets, on the main thread. */
public final class ClientProgress {
    private static final Map<ResourceLocation, QuestProgress> QUESTS = new HashMap<>();
    private static List<ResourceLocation> pinned = List.of();
    private static Set<ResourceLocation> stageLocked = Set.of();

    private ClientProgress() {
    }

    public static QuestProgress get(ResourceLocation quest) {
        return QUESTS.getOrDefault(quest, QuestProgress.EMPTY);
    }

    public static List<ResourceLocation> pinned() {
        return pinned;
    }

    static void receive(ProgressPayload payload) {
        if (payload.full()) {
            QUESTS.clear();
            QUESTS.putAll(payload.quests());
            return;
        }
        payload.quests().forEach((id, progress) -> {
            if (progress.completed() && !get(id).completed()) {
                Quest quest = ClientBook.current().quests().get(id);
                if (quest != null) {
                    Minecraft.getInstance().getToasts().addToast(new QuestToast(quest));
                }
            }
            QUESTS.put(id, progress);
        });
    }

    static void receivePinned(PinnedPayload payload) {
        pinned = payload.quests();
    }

    static void receiveStageLocks(StageLocksPayload payload) {
        stageLocked = Set.copyOf(payload.quests());
    }

    public static boolean isStageLocked(ResourceLocation quest) {
        return stageLocked.contains(quest);
    }

    static void clear() {
        QUESTS.clear();
        pinned = List.of();
        stageLocked = Set.of();
    }
}
