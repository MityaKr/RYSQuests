package works.repka.rysquests.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.hud.StickerBoard;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.network.GuidePagesPayload;
import works.repka.rysquests.network.PinnedPayload;
import works.repka.rysquests.network.ProgressPayload;
import works.repka.rysquests.network.TeamInfoPayload;
import works.repka.rysquests.network.StageLocksPayload;
import works.repka.rysquests.progress.QuestProgress;

/** The client's copy of its own progress. Changed only by packets, on the main thread. */
public final class ClientProgress {
    private static final Map<ResourceLocation, QuestProgress> QUESTS = new HashMap<>();
    private static List<ResourceLocation> pinned = List.of();
    private static Set<ResourceLocation> stageLocked = Set.of();
    private static Set<ResourceLocation> guideLocked = Set.of();
    private static final Set<String> GUIDE_PAGES = new HashSet<>();
    private static int teamSize = 1;

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
                    StickerBoard.celebrate(quest);
                }
            }
            QUESTS.put(id, progress);
        });
    }

    /** How many players share this player's progress. */
    public static int teamSize() {
        return teamSize;
    }

    static void receiveTeamInfo(TeamInfoPayload payload) {
        teamSize = payload.members();
    }

    static void receivePinned(PinnedPayload payload) {
        pinned = payload.quests();
    }

    static void receiveStageLocks(StageLocksPayload payload) {
        stageLocked = Set.copyOf(payload.quests());
        guideLocked = Set.copyOf(payload.guides());
    }

    public static boolean isGuideLocked(ResourceLocation guide) {
        return guideLocked.contains(guide);
    }

    static void receiveGuidePages(GuidePagesPayload payload) {
        if (payload.full()) {
            GUIDE_PAGES.clear();
        }
        GUIDE_PAGES.addAll(payload.pages());
    }

    public static boolean isRead(Guide guide, Guide.Page page) {
        return GUIDE_PAGES.contains(guide.readKey(page));
    }

    public static int readPages(Guide guide) {
        return (int) guide.pages().stream().filter(page -> isRead(guide, page)).count();
    }

    public static boolean isStageLocked(ResourceLocation quest) {
        return stageLocked.contains(quest);
    }

    static void clear() {
        QUESTS.clear();
        pinned = List.of();
        stageLocked = Set.of();
        guideLocked = Set.of();
        GUIDE_PAGES.clear();
    }
}
