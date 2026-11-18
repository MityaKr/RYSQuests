package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Layout;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.ItemTask;

/**
 * Early completion (ADR-66, FTB Quests' flexible progression): the tasks of a locked quest count, the quest stays locked, and it
 * completes as soon as it opens. Who says so: the quest, else its chapter, else the book.
 */
class PreCompleteTest {
    private static final ResourceLocation CHAPTER = ResourceLocation.parse("t:c");
    private static final ResourceLocation FIRST = ResourceLocation.parse("t:c/first");
    private static final ResourceLocation SECOND = ResourceLocation.parse("t:c/second");
    private static final long NOW = 1_800_000_000_000L;
    private static final QuestProgress DONE = new QuestProgress(List.of(1), NOW - 1000, List.of());

    private static ItemTask observe() {
        return new ItemTask(Optional.of(new ItemRef(ResourceLocation.parse("minecraft:diamond"), false)), List.of(), 1, ItemTask.Mode.OBSERVE, Optional.empty(), Optional.empty());
    }

    private static Quest quest(ResourceLocation id, List<ResourceLocation> requires, Optional<Boolean> preComplete, TaskDef... tasks) {
        return new Quest(id, Optional.of(CHAPTER), Optional.empty(), "t", Optional.empty(), Optional.empty(), Optional.empty(), requires, RequiresMode.ALL,
                Optional.empty(), List.of(tasks), List.of(new works.repka.rysquests.reward.XpReward(Optional.of(1), Optional.empty(), works.repka.rysquests.data.TeamMode.EACH)),
                Optional.empty(), QuestOptions.DEFAULT.withPreComplete(preComplete));
    }

    private static Chapter chapter(Optional<Boolean> preComplete) {
        return new Chapter(CHAPTER, "c", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), false, Chapter.STORY)
                .withPreComplete(preComplete);
    }

    private static QuestBook book(boolean bookSays, Optional<Boolean> chapterSays, Quest... quests) {
        return QuestBook.create(BookMeta.DEFAULT.withPreComplete(bookSays), List.of(chapter(chapterSays)), List.of(quests), List.of(), List.of(), "");
    }

    private static Function<ResourceLocation, QuestProgress> world(Map<ResourceLocation, QuestProgress> rows) {
        return id -> rows.getOrDefault(id, QuestProgress.EMPTY);
    }

    @Test
    void theNearestWordWins() {
        assertFalse(QuestLogic.preComplete(Optional.empty(), Optional.empty(), false), "off by default");
        assertTrue(QuestLogic.preComplete(Optional.empty(), Optional.empty(), true), "the book's");
        assertFalse(QuestLogic.preComplete(Optional.empty(), Optional.of(false), true), "the chapter over the book");
        assertTrue(QuestLogic.preComplete(Optional.empty(), Optional.of(true), false));
        assertFalse(QuestLogic.preComplete(Optional.of(false), Optional.of(true), true), "the quest over the chapter");
        assertTrue(QuestLogic.preComplete(Optional.of(true), Optional.of(false), false));

        Quest inherits = quest(SECOND, List.of(FIRST), Optional.empty(), observe());
        assertTrue(QuestLogic.preComplete(book(true, Optional.empty(), inherits), inherits));
        assertFalse(QuestLogic.preComplete(book(true, Optional.of(false), inherits), inherits));
        Quest own = quest(SECOND, List.of(FIRST), Optional.of(true), observe());
        assertTrue(QuestLogic.preComplete(book(false, Optional.of(false), own), own));
    }

    @Test
    void aLockedQuestKeepsItsTasksAndCompletesWhenItOpens() {
        Quest first = quest(FIRST, List.of(), Optional.empty(), observe());
        Quest second = quest(SECOND, List.of(FIRST), Optional.empty(), observe());
        QuestBook book = book(true, Optional.empty(), first, second);
        Map<ResourceLocation, QuestProgress> rows = new HashMap<>();

        assertTrue(QuestLogic.countsEarly(book, second, world(rows)), "locked, and its book counts early");
        assertFalse(QuestLogic.countsEarly(book, first, world(rows)), "an open quest is polled as before");

        // the poll of the locked quest raises its task and does not complete it
        QuestProgress early = QuestLogic.earlyPoll(QuestProgress.EMPTY, List.of(1), true).orElseThrow();
        assertFalse(early.completed());
        assertEquals(1, early.task(0));
        assertTrue(QuestLogic.earlyPoll(early, List.of(1), false).isEmpty(), "nothing new, nothing written");
        rows.put(SECOND, early);
        assertEquals(QuestStatus.LOCKED, QuestLogic.status(second, world(rows)), "it still looks locked");
        assertTrue(QuestLogic.doneEarly(book, second, world(rows)), "and the book says its condition is met");

        // the dependency completes: the quest opens, and the first poll completes it with nothing new to count
        rows.put(FIRST, DONE);
        assertFalse(QuestLogic.countsEarly(book, second, world(rows)));
        assertFalse(QuestLogic.doneEarly(book, second, world(rows)));
        assertTrue(QuestLogic.stuck(second, world(rows)), "the login's mend finds it too");
        QuestProgress opened = QuestLogic.afterPoll(second, early, early.taskValues(1), false, NOW).orElseThrow();
        assertTrue(opened.completed());
        rows.put(SECOND, opened);
        assertEquals(QuestStatus.COMPLETED, QuestLogic.status(second, world(rows)), "its reward waits by the usual rules");
    }

    @Test
    void offCountsNothingEarly() {
        Quest first = quest(FIRST, List.of(), Optional.empty(), observe());
        Quest second = quest(SECOND, List.of(FIRST), Optional.empty(), observe());
        assertFalse(QuestLogic.countsEarly(book(false, Optional.empty(), first, second), second, world(Map.of())));
        Quest refuses = quest(SECOND, List.of(FIRST), Optional.of(false), observe());
        assertFalse(QuestLogic.countsEarly(book(true, Optional.empty(), first, refuses), refuses, world(Map.of())));
    }

    @Test
    void doneAndCoolingQuestsAreNotCountedEarly() {
        Quest first = quest(FIRST, List.of(), Optional.empty(), observe());
        Quest second = quest(SECOND, List.of(FIRST), Optional.empty(), observe());
        QuestBook book = book(true, Optional.empty(), first, second);
        assertFalse(QuestLogic.countsEarly(book, second, world(Map.of(SECOND, new QuestProgress(List.of(1), NOW, List.of())))), "already done");
        QuestProgress cooling = new QuestProgress(List.of(0), 0, List.of(), System.currentTimeMillis() + 600_000, 1);
        assertFalse(QuestLogic.countsEarly(book, second, world(Map.of(SECOND, cooling))), "between rounds of a repeatable quest");
        Quest noTasks = quest(SECOND, List.of(FIRST), Optional.empty());
        assertFalse(QuestLogic.doneEarly(book(true, Optional.empty(), first, noTasks), noTasks, world(Map.of())), "a quest without tasks is no condition met");
    }

    @Test
    void theFieldIsWrittenOnlyWhenSet() {
        JsonObject absent = QuestOptions.CODEC.codec().encodeStart(JsonOps.INSTANCE, QuestOptions.DEFAULT).getOrThrow().getAsJsonObject();
        assertFalse(absent.has("pre_complete"), "old books stay as they were");
        JsonObject off = QuestOptions.CODEC.codec().encodeStart(JsonOps.INSTANCE, QuestOptions.DEFAULT.withPreComplete(Optional.of(false))).getOrThrow().getAsJsonObject();
        assertEquals(false, off.get("pre_complete").getAsBoolean(), "an explicit off overrides the chapter");
        QuestOptions read = QuestOptions.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"pre_complete\": true}")).getOrThrow();
        assertEquals(Optional.of(true), read.preComplete());
        assertEquals(Optional.empty(), QuestOptions.CODEC.codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow().preComplete());

        Chapter chapter = Chapter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\": \"t:c\", \"title\": \"c\", \"pre_complete\": false}")).getOrThrow();
        assertEquals(Optional.of(false), chapter.preComplete());
        assertFalse(Chapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter(Optional.empty())).getOrThrow().getAsJsonObject().has("pre_complete"));
        assertEquals(Optional.of(false), chapter.withTitle("x").withOffMap(true).preComplete(), "kept through the editor's changes");

        BookMeta meta = BookMeta.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"pre_complete\": true}")).getOrThrow();
        assertTrue(meta.preComplete());
        assertTrue(meta.withPublished(false).withSections(List.of()).preComplete(), "kept through the editor's changes");
        assertFalse(BookMeta.CODEC.encodeStart(JsonOps.INSTANCE, BookMeta.DEFAULT).getOrThrow().getAsJsonObject().has("pre_complete"));
        assertFalse(BookMeta.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow().preComplete(), "a book without the field is off");
    }

    @Test
    void layoutAndOtherOptionsKeepTheWord() {
        QuestOptions options = new QuestOptions(Layout.DEFAULT, false, false, false, Optional.empty(), false, List.of(), false, false, Optional.of(true));
        assertEquals(Optional.of(true), options.withMilestone(true).withOptional(true).withDraft(true).withArchived(false).withLayout(Layout.DEFAULT).preComplete());
    }
}
