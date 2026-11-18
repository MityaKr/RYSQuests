package works.repka.rysquests.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.hud.StickerBoard;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Achievement;
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
    private static Set<String> openStages = Set.of();
    private static List<String> pinnedWays = List.of();
    private static final Set<String> GUIDE_PAGES = new HashSet<>();
    private static int teamSize = 1;
    private static Optional<ResourceLocation> lastChapter = Optional.empty();
    /** Grows with every packet that changes what the book shows: caches of counts and statuses compare it. */
    private static int version;

    public static int version() {
        return version;
    }

    private ClientProgress() {
    }

    public static QuestProgress get(ResourceLocation quest) {
        return QUESTS.getOrDefault(quest, QuestProgress.EMPTY);
    }

    public static List<ResourceLocation> pinned() {
        return pinned;
    }

    /**
     * The chapter of the quest this player last completed by their own doing, for the next step on "Now". Empty before the first
     * one: then the first unfinished chapter is meant.
     */
    public static Optional<ResourceLocation> lastChapter() {
        return lastChapter;
    }

    /** Replaces the whole progress as a full packet would: the autotest's way to a made up progress for a book it gave the client. */
    public static void receiveForTest(Map<ResourceLocation, QuestProgress> quests, Optional<ResourceLocation> last) {
        receive(new ProgressPayload(true, quests, last));
    }

    /** A copy of the whole progress, for the autotest to give it back after a made up one. */
    public static Map<ResourceLocation, QuestProgress> snapshotForTest() {
        return Map.copyOf(QUESTS);
    }

    static void receive(ProgressPayload payload) {
        version++;
        if (payload.full()) {
            QUESTS.clear();
            QUESTS.putAll(payload.quests());
            lastChapter = payload.lastChapter();
            return;
        }
        payload.lastChapter().ifPresent(chapter -> lastChapter = Optional.of(chapter));
        List<Quest> finished = new java.util.ArrayList<>();
        payload.quests().forEach((id, progress) -> {
            if (progress.completed() && !get(id).completed()) {
                Quest quest = ClientBook.current().quests().get(id);
                // A hidden quest that has opened is an ordinary quest by now and gets its message like any other.
                // The marks of a collection fill up in silence: its level tells that it is done (ADR-62).
                if (quest != null && !Achievement.isCollectionQuest(quest.id()) && (!quest.options().hidden() || Achievement.isTierQuest(quest.id())
                        || works.repka.rysquests.progress.QuestLogic.isUnlocked(quest, ClientProgress::get))) {
                    finished.add(quest);
                }
            }
            QUESTS.put(id, progress);
        });
        // After the whole update is in: a chapter the last of these quests closes counts as closed.
        StickerBoard.celebrate(finished, payload.done());
    }

    /** How many players share this player's progress. */
    public static int teamSize() {
        return teamSize;
    }

    static void receiveTeamInfo(TeamInfoPayload payload) {
        teamSize = payload.members();
    }

    static void receivePinned(PinnedPayload payload) {
        version++;
        pinned = payload.quests();
        pinnedWays = payload.ways();
    }

    static void receiveStageLocks(StageLocksPayload payload) {
        version++;
        stageLocked = Set.copyOf(payload.quests());
        guideLocked = Set.copyOf(payload.guides());
        openStages = Set.copyOf(payload.openStages());
    }

    /** Only knows the stages named by custom sections. */
    public static boolean hasStage(String stage) {
        return openStages.contains(stage);
    }

    public static List<String> pinnedWays() {
        return pinnedWays;
    }

    public static boolean isGuideLocked(ResourceLocation guide) {
        return guideLocked.contains(guide);
    }

    static void receiveGuidePages(GuidePagesPayload payload) {
        version++;
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

    // --- for the in-game autotest: states the server did not send (shop, achievements, guides, HUD pictures) ---

    public static void progressForTest(ResourceLocation quest, QuestProgress progress) {
        version++;
        QUESTS.put(quest, progress);
    }

    public static void pinnedForTest(List<ResourceLocation> quests) {
        version++;
        pinned = List.copyOf(quests);
    }

    public static void guidePagesForTest(java.util.Collection<String> readKeys, Set<ResourceLocation> locked) {
        version++;
        GUIDE_PAGES.addAll(readKeys);
        guideLocked = Set.copyOf(locked);
    }

    static void clear() {
        version++;
        QUESTS.clear();
        pinned = List.of();
        lastChapter = Optional.empty();
        stageLocked = Set.of();
        guideLocked = Set.of();
        openStages = Set.of();
        pinnedWays = List.of();
        GUIDE_PAGES.clear();
    }
}
