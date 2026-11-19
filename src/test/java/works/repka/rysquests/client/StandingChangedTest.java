package works.repka.rysquests.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.task.StatTask;

/**
 * The version of statuses (logic review 19.11): a statistic that counts on every second leaves the caches that walk the whole book
 * alone; only what changes how a quest stands moves them.
 */
class StandingChangedTest {
    private static final Quest WALK = new Quest(ResourceLocation.parse("t:walk"), Optional.empty(), Optional.empty(), "t", Optional.empty(), Optional.empty(),
            Optional.empty(), List.of(), RequiresMode.ALL, Optional.empty(),
            List.of(new StatTask(ResourceLocation.parse("minecraft:custom"), ResourceLocation.parse("minecraft:walk_one_cm"), 100_000, Optional.empty())),
            List.of(), Optional.empty(), QuestOptions.DEFAULT);

    private static QuestProgress at(int value) {
        return new QuestProgress(List.of(value), 0, List.of());
    }

    @Test
    void countingOnIsNotAChangeOfStanding() {
        assertFalse(ClientProgress.standingChanged(WALK, at(10), at(500)));
        assertFalse(ClientProgress.standingChanged(WALK, at(500), at(99_999)));
    }

    @Test
    void theFirstStepTheTargetAndTheCompletionAre() {
        assertTrue(ClientProgress.standingChanged(WALK, at(0), at(1)), "available to started");
        assertTrue(ClientProgress.standingChanged(WALK, at(99_999), at(100_000)), "the task reaches its target");
        assertTrue(ClientProgress.standingChanged(WALK, at(100_000), new QuestProgress(List.of(100_000), 5, List.of())), "done");
        assertTrue(ClientProgress.standingChanged(WALK, new QuestProgress(List.of(100_000), 5, List.of()), new QuestProgress(List.of(100_000), 5, List.of(0))),
                "a reward taken");
        assertTrue(ClientProgress.standingChanged(null, at(1), at(2)), "a quest the book does not know");
    }
}
