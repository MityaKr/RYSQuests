package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.progress.QuestStatus;

/**
 * The book as eras for the timeline: chapters that share an epoch, and each chapter without one on its own.
 * Walks the whole book, so the result is kept for half a second.
 */
final class Epochs {
    private static final long REFRESH_MS = 500;
    private static final int MILESTONES = 3;
    private static final String MILESTONE_TAG = "milestone";
    private static final String KEY_ITEM_TAG = "key_item";

    /** One era. {@code milestones} are the quests the timeline names, {@code keyQuest} the one whose item it shows. */
    record Epoch(int number, String title, List<Chapter> chapters, int done, int total, List<Quest> milestones, Quest keyQuest) {
        boolean finished() {
            return total > 0 && done >= total;
        }

        float ratio() {
            return total == 0 ? 0 : (float) done / total;
        }
    }

    private static final Map<ResourceLocation, List<Quest>> ORDERED = new HashMap<>();
    private static QuestBook orderedFor;
    private static List<Epoch> cached = List.of();
    private static long refreshedAt;

    private Epochs() {
    }

    static List<Epoch> list() {
        long now = System.currentTimeMillis();
        if (now - refreshedAt >= REFRESH_MS) {
            refreshedAt = now;
            cached = build();
        }
        return cached;
    }

    /** The first era that is not finished, the one the player is in. */
    static int currentIndex(List<Epoch> epochs) {
        for (int i = 0; i < epochs.size(); i++) {
            if (!epochs.get(i).finished()) {
                return i;
            }
        }
        return Math.max(0, epochs.size() - 1);
    }

    private static List<Epoch> build() {
        Map<Object, List<Chapter>> groups = new LinkedHashMap<>();
        for (Chapter chapter : BookScreen.chapters()) {
            groups.computeIfAbsent(chapter.epoch().<Object>map(number -> number).orElse(chapter.id()), key -> new ArrayList<>()).add(chapter);
        }
        if (orderedFor != ClientBook.current()) {
            orderedFor = ClientBook.current();
            ORDERED.clear();
        }
        List<Epoch> epochs = new ArrayList<>();
        int number = 0;
        for (List<Chapter> chapters : groups.values()) {
            number++;
            List<Quest> quests = new ArrayList<>();
            chapters.forEach(chapter -> quests.addAll(ORDERED.computeIfAbsent(chapter.id(), id -> ChapterOrder.of(orderedFor, id))));
            int done = (int) quests.stream().filter(quest -> ClientProgress.get(quest.id()).everDone()).count();
            List<Quest> milestones = milestones(quests);
            Quest key = quests.stream().filter(quest -> quest.options().tags().contains(KEY_ITEM_TAG)).findFirst()
                    .orElse(milestones.isEmpty() ? null : milestones.getLast());
            epochs.add(new Epoch(number, Component.translatable(chapters.getFirst().title()).getString(), chapters, done, quests.size(), milestones, key));
        }
        return epochs;
    }

    /** Quests tagged as milestones, otherwise those that open the most others. */
    private static List<Quest> milestones(List<Quest> quests) {
        List<Quest> tagged = quests.stream().filter(quest -> quest.options().tags().contains(MILESTONE_TAG)).limit(MILESTONES).toList();
        if (!tagged.isEmpty()) {
            return tagged;
        }
        List<Quest> opening = quests.stream()
                .sorted(Comparator.comparingInt(QuestCard::opens).reversed())
                .limit(MILESTONES)
                .toList();
        return quests.stream().filter(opening::contains).toList();
    }

    static boolean isOpen(Quest quest) {
        QuestStatus status = BookState.status(quest);
        return status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
    }
}
