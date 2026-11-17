package works.repka.rysquests.progress;

import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Quest;

/**
 * The one count of the book: main quests. Optional quests are counted apart and never hold a chapter back, and drafts and
 * archived quests are not counted at all (review U4 + C6, plan section 3, "Map").
 */
public final class BookCounts {
    private BookCounts() {
    }

    public record Count(int done, int total) {
        public static final Count NONE = new Count(0, 0);

        public boolean complete() {
            return done >= total;
        }

        Count plus(boolean isDone) {
            return new Count(done + (isDone ? 1 : 0), total + 1);
        }
    }

    /** @param main the quests that count; @param side the optional ones */
    public record Counts(Count main, Count side) {
        public static final Counts NONE = new Counts(Count.NONE, Count.NONE);
    }

    /** Done now or in an earlier round counts as done, as the book shows it. */
    public static Counts of(Iterable<Quest> quests, Function<ResourceLocation, QuestProgress> progress) {
        Count main = Count.NONE;
        Count side = Count.NONE;
        for (Quest quest : quests) {
            if (!QuestLogic.isLive(quest)) {
                continue;
            }
            boolean done = progress.apply(quest.id()).everDone();
            if (quest.options().optional()) {
                side = side.plus(done);
            } else {
                main = main.plus(done);
            }
        }
        return new Counts(main, side);
    }
}
