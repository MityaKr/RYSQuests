package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.progress.BookCounts;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestStatus;

/**
 * The numbers the frame and the sidebar show: rewards waiting (the badge of "Now"), quests ready to hand in, and per chapter its
 * main quests done and the chapter's state. Chapters are worked out once per progress version, the rest twice a second.
 * Static, so that every page reads the same numbers without walking the book again.
 */
final class BookTallies {
    private static final long REFRESH_MS = 500;

    /** What the sidebar says about a chapter: finished (a check), open, or locked (a padlock, nothing in it can be done yet). */
    enum State {
        DONE, OPEN, LOCKED
    }

    /** Main quests of a chapter, and optional ones apart; optional ones never hold a chapter back. */
    record Tally(int done, int total, int optionalDone, int optionalTotal, State state) {
        static final Tally EMPTY = new Tally(0, 0, 0, 0, State.OPEN);

        float ratio() {
            return shownTotal() == 0 ? 0 : (float) shownDone() / shownTotal();
        }

        /** Done as the sidebar counts it: main quests, or with "Все" side quests too. */
        int shownDone() {
            return countAll() ? done + optionalDone : done;
        }

        int shownTotal() {
            return countAll() ? total + optionalTotal : total;
        }

        Tally plus(Tally other) {
            return new Tally(done + other.done, total + other.total, optionalDone + other.optionalDone, optionalTotal + other.optionalTotal, State.OPEN);
        }
    }

    private static final Map<ResourceLocation, Tally> CHAPTERS = new HashMap<>();
    private static final Map<ResourceLocation, List<Quest>> ORDER = new HashMap<>();
    private static int chaptersVersion = -1;
    private static boolean chaptersAll;
    @Nullable
    private static QuestBook chaptersBook;
    @Nullable
    private static QuestBook orderBook;

    private static long countedAt;
    private static int claimCount;
    private static List<ItemStack> claimStacks = List.of();
    private static boolean plainClaim;
    @Nullable
    private static Quest pickQuest;
    private static int submitCount;

    private BookTallies() {
    }

    /** Quests of a chapter in dependency order, hidden ones once they unlock. */
    static List<Quest> questsOf(ResourceLocation chapter) {
        if (orderBook != BookData.book()) {
            orderBook = BookData.book();
            ORDER.clear();
        }
        return ORDER.computeIfAbsent(chapter, id -> ChapterOrder.of(BookData.book(), id)).stream()
                .filter(quest -> !quest.options().hidden() || QuestLogic.isUnlocked(quest, ClientProgress::get))
                .toList();
    }

    static Tally chapter(ResourceLocation chapter) {
        if (chaptersVersion != ClientProgress.version() || chaptersBook != BookData.book() || chaptersAll != countAll()) {
            CHAPTERS.clear();
            chaptersAll = countAll();
            chaptersVersion = ClientProgress.version();
            chaptersBook = BookData.book();
        }
        return CHAPTERS.computeIfAbsent(chapter, BookTallies::count);
    }

    private static Tally count(ResourceLocation chapter) {
        List<Quest> quests = questsOf(chapter);
        BookCounts.Counts counts = BookCounts.of(quests, ClientProgress::get);
        boolean anyOpen = false;
        for (Quest quest : quests) {
            if (BookState.status(quest) != QuestStatus.LOCKED) {
                anyOpen = true;
                break;
            }
        }
        // with "Все" a chapter is finished when its side quests are done too
        int need = counts.main().total() + (countAll() ? counts.side().total() : 0);
        int got = counts.main().done() + (countAll() ? counts.side().done() : 0);
        State state = need > 0 && got >= need ? State.DONE
                : !anyOpen && !quests.isEmpty() ? State.LOCKED : State.OPEN;
        return new Tally(counts.main().done(), counts.main().total(), counts.side().done(), counts.side().total(), state);
    }

    /** "Все / Основные": whether side quests count too, kept in the client config; main ones only by default. */
    static boolean countAll() {
        try {
            return works.repka.rysquests.client.ClientConfig.COUNT_ALL_QUESTS.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    static void setCountAll(boolean all) {
        works.repka.rysquests.client.ClientConfig.COUNT_ALL_QUESTS.set(all);
        works.repka.rysquests.client.ClientConfig.COUNT_ALL_QUESTS.save();
    }

    /** Main quests done and in total, the way the old views read them. */
    static int[] mainCounts(ResourceLocation chapter) {
        Tally tally = chapter(chapter);
        return new int[] {tally.done(), tally.total()};
    }

    /** The whole book: every chapter the book shows. */
    static Tally book() {
        Tally sum = Tally.EMPTY;
        for (Chapter chapter : BookData.chapters()) {
            sum = sum.plus(chapter(chapter.id()));
        }
        return sum;
    }

    /** Quests that wait for their reward to be taken: the badge of "Now". */
    static int readyToClaim() {
        refresh();
        return claimCount;
    }

    static List<ItemStack> claimStacks() {
        refresh();
        return claimStacks;
    }

    static int readyToSubmit() {
        refresh();
        return submitCount;
    }

    /** Only choices are left: claiming everything would give nothing, so the button opens a quest to pick in. */
    static boolean onlyPicksLeft() {
        refresh();
        return !plainClaim && pickQuest != null;
    }

    @Nullable
    static Quest pickQuest() {
        refresh();
        return pickQuest;
    }

    /** Counts again on the next read: after a claim, for example. */
    static void invalidate() {
        countedAt = 0;
    }

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - countedAt <= REFRESH_MS) {
            return;
        }
        countedAt = now;
        int count = 0;
        List<ItemStack> stacks = new ArrayList<>();
        boolean plain = false;
        Quest pick = null;
        int submit = 0;
        for (Quest quest : BookData.book().quests().values()) {
            QuestStatus status = BookState.status(quest);
            if (BookData.inStory(quest) && status == QuestStatus.COMPLETED) {
                count++;
                if (stacks.size() < 2) {
                    stacks.addAll(QuestFacts.rewardStacks(quest, 2 - stacks.size()));
                }
                plain |= QuestFacts.hasUnclaimedPlain(quest);
                if (pick == null && QuestFacts.hasUnclaimedChoice(quest)) {
                    pick = quest;
                }
            }
            if (!BookData.shown(quest)) {
                continue;
            }
            if ((status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS) && QuestFacts.readyToSubmit(quest, ClientProgress.get(quest.id()))) {
                submit++;
            }
        }
        claimCount = count;
        claimStacks = stacks;
        plainClaim = plain;
        pickQuest = pick;
        submitCount = submit;
    }
}
