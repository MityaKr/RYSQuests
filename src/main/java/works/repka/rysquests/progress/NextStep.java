package works.repka.rysquests.progress;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.Quest;

/**
 * The next step on "Now", one rule for every book (plan, section 3, "Now"): the nearest unfinished main quest of the chapter where the
 * player last completed a quest; when there is none (the chapter is finished, or nothing was ever completed), the first unfinished
 * main quest of the first unfinished chapter. In a book with a story this is the next step of the story; in a book without one
 * (ATM10) it is the next step in the mod the player is busy with.
 *
 * <p>"Nearest" prefers a quest that can be done now; only when every unfinished main quest of the chapter is still locked is the
 * first of them in chapter order the next step.
 */
public final class NextStep {
    private NextStep() {
    }

    /**
     * The chosen quest.
     *
     * @param fromLast taken from the chapter of the last completed quest, the first half of the rule
     */
    public record Result(Quest quest, ResourceLocation chapter, boolean fromLast) {
    }

    /**
     * @param last      the chapter where this player last completed a quest, if known
     * @param questsOf  the quests of a chapter in chapter order (dependencies first)
     * @param order     the chapters the second half of the rule walks, in book order: chapters on the map
     * @param progress  the team's progress
     * @param open      whether a quest can be done now (neither locked nor done)
     * @return the next step, or empty when every main quest of the book is done
     */
    public static Optional<Result> choose(Optional<ResourceLocation> last, Function<ResourceLocation, List<Quest>> questsOf,
            List<ResourceLocation> order, Function<ResourceLocation, QuestProgress> progress, Predicate<Quest> open) {
        if (last.isPresent()) {
            Quest quest = nearest(questsOf.apply(last.get()), progress, open);
            if (quest != null) {
                return Optional.of(new Result(quest, last.get(), true));
            }
        }
        for (ResourceLocation chapter : order) {
            Quest quest = nearest(questsOf.apply(chapter), progress, open);
            if (quest != null) {
                return Optional.of(new Result(quest, chapter, false));
            }
        }
        return Optional.empty();
    }

    /** The nearest unfinished main quest among these: the first open one, else the first unfinished one; null when all are done. */
    @Nullable
    public static Quest nearest(List<Quest> quests, Function<ResourceLocation, QuestProgress> progress, Predicate<Quest> open) {
        Quest first = null;
        for (Quest quest : quests) {
            if (!isMain(quest) || progress.apply(quest.id()).everDone()) {
                continue;
            }
            if (open.test(quest)) {
                return quest;
            }
            if (first == null) {
                first = quest;
            }
        }
        return first;
    }

    /** A quest that counts: not optional, neither a draft nor in the archive. */
    public static boolean isMain(Quest quest) {
        return !quest.options().optional() && QuestLogic.isLive(quest);
    }
}
