package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.data.TestBooks;

/** The fixes the review asked for that live in shared rules: optional quests, craft mode, and progress that follows its task. */
class ReviewFixesTest {
    private static Quest quest(String json) {
        return Quest.CODEC.parse(JsonOps.INSTANCE, TestBooks.json(json)).getOrThrow();
    }

    private static TaskDef task(String json) {
        return TaskDef.CODEC.parse(JsonOps.INSTANCE, TestBooks.json(json)).getOrThrow();
    }

    @Test
    void optionalQuestsDoNotHoldAChapterBack() {
        Quest main = quest("{\"id\":\"t:main\",\"title\":\"m\"}");
        Quest side = quest("{\"id\":\"t:side\",\"title\":\"s\",\"optional\":true}");
        Quest draft = quest("{\"id\":\"t:draft\",\"title\":\"d\",\"draft\":true}");
        Map<ResourceLocation, QuestProgress> progress = Map.of(main.id(), new QuestProgress(List.of(), 5, List.of()));
        BookCounts.Counts counts = BookCounts.of(List.of(main, side, draft), id -> progress.getOrDefault(id, QuestProgress.EMPTY));
        assertEquals(new BookCounts.Count(1, 1), counts.main());
        assertTrue(counts.main().complete(), "the chapter is done although its optional quest is not");
        assertEquals(new BookCounts.Count(0, 1), counts.side());
    }

    @Test
    void itemsAtHandDoNotBringACraftTaskCloser() {
        TaskDef craft = task("{\"type\":\"rysquests:item\",\"item\":\"minecraft:torch\",\"count\":8,\"mode\":\"craft\"}");
        TaskDef observe = task("{\"type\":\"rysquests:item\",\"item\":\"minecraft:torch\",\"count\":8,\"mode\":\"observe\"}");
        assertEquals(6, QuestLogic.stillNeeded(craft, 2, 64));
        assertEquals(0, QuestLogic.stillNeeded(observe, 2, 64));
        assertEquals(3, QuestLogic.stillNeeded(observe, 2, 3));
    }

    private static final String STONE = "{\"type\":\"rysquests:item\",\"item\":\"minecraft:stone\",\"count\":16}";
    private static final String DIRT = "{\"type\":\"rysquests:item\",\"item\":\"minecraft:dirt\",\"count\":4}";
    private static final String CHECK = "{\"type\":\"rysquests:checkbox\"}";

    private static Quest withTasks(String... tasks) {
        return quest("{\"id\":\"t:q\",\"title\":\"q\",\"tasks\":[" + String.join(",", tasks) + "],"
                + "\"rewards\":[{\"type\":\"rysquests:xp\",\"levels\":1},{\"type\":\"rysquests:item\",\"item\":\"minecraft:apple\",\"count\":1}]}");
    }

    @Test
    void reorderedTasksKeepTheirOwnProgress() {
        Quest before = withTasks(STONE, DIRT, CHECK);
        Quest after = withTasks(CHECK, STONE, DIRT);
        QuestProgress progress = new QuestProgress(List.of(10, 3, 1), 0, List.of());
        QuestProgress moved = ProgressRemap.apply(progress, ProgressRemap.between(before, after));
        assertEquals(List.of(1, 10, 3), moved.tasks());
    }

    @Test
    void anAddedTaskStartsAtZeroAndARemovedOneTakesNothingWithIt() {
        QuestProgress progress = new QuestProgress(List.of(10, 3), 0, List.of());
        assertEquals(List.of(0, 10, 3), ProgressRemap.apply(progress, ProgressRemap.between(withTasks(STONE, DIRT), withTasks(CHECK, STONE, DIRT))).tasks());
        assertEquals(List.of(3), ProgressRemap.apply(progress, ProgressRemap.between(withTasks(STONE, DIRT), withTasks(DIRT))).tasks());
    }

    @Test
    void aTaskChangedInPlaceKeepsWhatWasDone() {
        QuestProgress progress = new QuestProgress(List.of(10, 3), 0, List.of());
        Quest after = withTasks(STONE.replace("16", "32"), DIRT);
        assertEquals(List.of(10, 3), ProgressRemap.apply(progress, ProgressRemap.between(withTasks(STONE, DIRT), after)).tasks());
    }

    @Test
    void claimsFollowTheirRewardsSoNothingIsPaidTwice() {
        Quest before = withTasks(STONE);
        Quest after = quest("{\"id\":\"t:q\",\"title\":\"q\",\"tasks\":[" + STONE + "],"
                + "\"rewards\":[{\"type\":\"rysquests:item\",\"item\":\"minecraft:apple\",\"count\":1},{\"type\":\"rysquests:xp\",\"levels\":1}]}");
        QuestProgress claimedXp = new QuestProgress(List.of(16), 5, List.of(0));
        QuestProgress moved = ProgressRemap.apply(claimedXp, ProgressRemap.between(before, after));
        assertEquals(List.of(1), moved.claimed(), "the experience moved to the second place and stays taken");
        assertEquals(5, moved.completedAt());
    }

    @Test
    void anUnchangedQuestIsLeftAlone() {
        assertTrue(ProgressRemap.between(withTasks(STONE, DIRT), withTasks(STONE, DIRT)).identity());
        assertTrue(!ProgressRemap.between(withTasks(STONE, DIRT), withTasks(DIRT, STONE)).identity());
    }
}
