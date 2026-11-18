package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.BookReport;
import works.repka.rysquests.data.BookValidator;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.data.TestBooks;
import works.repka.rysquests.task.CollectionTask;
import works.repka.rysquests.task.ItemTask;

/**
 * A collection in the achievements (ADR-62): its marks are a hidden quest of observe tasks, its levels count them, and a team keeps,
 * shares and passes them on as any quest progress.
 */
class CollectionTest {
    private static final ResourceLocation ID = ResourceLocation.parse("t:cores");
    private static final ResourceLocation MARKS = ResourceLocation.parse("t:_achievements/cores/_collection");
    private static final ResourceLocation BRONZE = ResourceLocation.parse("t:_achievements/cores/bronze");
    private static final ResourceLocation GOLD = ResourceLocation.parse("t:_achievements/cores/gold");
    private static final String TASK = """
            { "type": "rysquests:collection", "items": [ "t:ignis_core", "t:void_core", { "item": "t:envelope", "count": 64 }, "#c:gems" ] }""";

    private static Map<ResourceLocation, JsonElement> files(String task, String tiers) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("t:book"), TestBooks.json("{\"sections\": [\"story\", \"achievements\"]}"));
        files.put(ResourceLocation.parse("t:achievements/cores"), TestBooks.json("{\"id\": \"t:cores\", \"task\": " + task + ", \"tiers\": " + tiers + "}"));
        return files;
    }

    private static final String TIERS = "[{\"tier\": \"bronze\", \"amount\": 2}, {\"tier\": \"silver\", \"amount\": 3}, {\"tier\": \"gold\", \"amount\": 4}]";

    private static QuestBook book() {
        return TestBooks.book(files(TASK, TIERS));
    }

    private static Set<String> codes(BookValidator.Result result) {
        return result.report().issues().stream().map(BookReport.Issue::code).collect(Collectors.toSet());
    }

    @Test
    void aCollectionMakesItsMarksAndItsLevels() {
        BookValidator.Result result = TestBooks.validate(files(TASK, TIERS));
        assertEquals(List.of(), result.report().issues());
        QuestBook book = result.book();
        Quest marks = book.quests().get(MARKS);
        assertEquals(4, marks.tasks().size(), "one task per item");
        assertTrue(marks.tasks().stream().allMatch(task -> task instanceof ItemTask item && item.mode() == ItemTask.Mode.OBSERVE), "held, never taken");
        assertEquals(64, marks.tasks().get(2).target(), "an item counted by the stack held at once");
        assertTrue(marks.options().hidden());
        assertEquals(2, book.quests().get(BRONZE).tasks().getFirst().target());
        assertEquals(4, book.quests().get(GOLD).tasks().getFirst().target());
        assertTrue(book.quests().get(GOLD).tasks().getFirst() instanceof CollectionTask);
        assertEquals(MARKS, book.achievements().get(ID).collectionQuestId());
    }

    @Test
    void theMarksAreNotALevel() {
        assertTrue(Achievement.isCollectionQuest(MARKS));
        assertFalse(Achievement.isTierQuest(MARKS), "no message, no reward of its own");
        assertTrue(Achievement.isTierQuest(GOLD));
        assertFalse(Achievement.isCollectionQuest(GOLD));
        assertEquals(MARKS, Achievement.collectionQuestOf(GOLD));
        assertEquals(ResourceLocation.parse("t:_achievements/a/b/_collection"), Achievement.collectionQuestOf(ResourceLocation.parse("t:_achievements/a/b/gold")));
    }

    @Test
    void theValidatorGuardsTheLevels() {
        assertTrue(codes(TestBooks.validate(files(TASK, "[{\"tier\": \"gold\", \"amount\": 5}]"))).contains("achievement_collection_amount"));
        assertTrue(codes(TestBooks.validate(files(TASK, "[{\"tier\": \"_x\", \"amount\": 2}]"))).contains("achievement_tier_name"));
        Map<ResourceLocation, JsonElement> withQuest = files(TASK, TIERS);
        withQuest.put(ResourceLocation.parse("t:chapters/a/_chapter"), TestBooks.json("{\"title\": \"A\"}"));
        withQuest.put(ResourceLocation.parse("t:chapters/a/q"), TestBooks.json("{\"id\": \"t:a/q\", \"title\": \"Q\", \"tasks\": [" + TASK + "]}"));
        assertTrue(codes(TestBooks.validate(withQuest)).contains("collection_outside_achievement"));
        assertTrue(TaskDef.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\": \"rysquests:collection\", \"items\": []}")).isError());
    }

    @Test
    void aTeamCollectsTogether() {
        Quest marks = book().quests().get(MARKS);
        UUID team = UUID.randomUUID();
        ProgressStore store = new ProgressStore();
        // one member held the void core, another the gems: each poll raises the team's own record
        store.put(team, Map.of(MARKS, CollectionMarks.mark(marks, store.get(team, MARKS), 1, 10).orElseThrow()));
        store.put(team, Map.of(MARKS, CollectionMarks.mark(marks, store.get(team, MARKS), 3, 20).orElseThrow()));

        assertEquals(2, CollectionMarks.count(marks, store.get(team, MARKS)));
        assertTrue(CollectionMarks.collected(marks, store.get(team, MARKS), 3));
        assertFalse(CollectionMarks.collected(marks, store.get(team, MARKS), 0));
        assertEquals(Optional.empty(), CollectionMarks.mark(marks, store.get(team, MARKS), 1, 30), "marking twice changes nothing");
        assertFalse(store.get(team, MARKS).completed());
    }

    @Test
    void theStackHasToBeHeldAtOnce() {
        Quest marks = book().quests().get(MARKS);
        QuestProgress tenEnvelopes = new QuestProgress(List.of(0, 0, 10, 0), 0, List.of());
        assertFalse(CollectionMarks.collected(marks, tenEnvelopes, 2));
        assertEquals(0, CollectionMarks.count(marks, tenEnvelopes));
    }

    @Test
    void theLastItemCompletesTheMarks() {
        Quest marks = book().quests().get(MARKS);
        QuestProgress three = new QuestProgress(List.of(1, 1, 64, 0), 0, List.of());
        QuestProgress all = CollectionMarks.mark(marks, three, 3, 99).orElseThrow();
        assertTrue(all.completed());
        assertEquals(4, CollectionMarks.count(marks, all));
    }

    @Test
    void levelsCountTheMarksAndNeverGoDown() {
        assertEquals(2, CollectionMarks.levelValue(0, 2, 4));
        assertEquals(2, CollectionMarks.levelValue(0, 3, 2), "a level never counts past its amount");
        assertEquals(3, CollectionMarks.levelValue(3, 1, 4), "an item taken out of the list does not take back what was counted");
    }

    @Test
    void scriptsFindTheEntryByItemOrTag() {
        Quest marks = book().quests().get(MARKS);
        assertEquals(1, CollectionMarks.indexOf(marks, "t:void_core"));
        assertEquals(3, CollectionMarks.indexOf(marks, "#c:gems"));
        assertEquals(-1, CollectionMarks.indexOf(marks, "t:stick"));
        assertEquals(Optional.empty(), CollectionMarks.mark(marks, QuestProgress.EMPTY, -1, 1));
    }

    @Test
    void aPlayerWhoLeavesWithACopyKeepsTheMarksAndSoDoesTheTeam() {
        Quest marks = book().quests().get(MARKS);
        UUID team = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        ProgressStore store = new ProgressStore();
        store.put(team, Map.of(MARKS, new QuestProgress(List.of(1, 0, 64, 0), 0, List.of())));

        store.copyTeam(team, player);

        assertEquals(2, CollectionMarks.count(marks, store.get(player, MARKS)));
        assertEquals(2, CollectionMarks.count(marks, store.get(team, MARKS)), "the team keeps what its members collected");
    }

    @Test
    void joiningATeamAddsTheMarksOfBoth() {
        Quest marks = book().quests().get(MARKS);
        UUID team = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        ProgressStore store = new ProgressStore();
        store.put(team, Map.of(MARKS, new QuestProgress(List.of(1, 0, 0, 0), 0, List.of())));
        store.put(player, Map.of(MARKS, new QuestProgress(List.of(0, 1, 30, 0), 0, List.of())));

        store.mergeInto(player, team);

        QuestProgress merged = store.get(team, MARKS);
        assertEquals(2, CollectionMarks.count(marks, merged));
        assertEquals(30, merged.task(2), "the most envelopes held so far stays");
    }

    @Test
    void marksSurviveSaving() {
        UUID team = UUID.randomUUID();
        ProgressStore store = new ProgressStore();
        store.put(team, Map.of(MARKS, new QuestProgress(List.of(1, 0, 64, 1), 0, List.of())));

        ProgressStore loaded = ProgressStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(List.of(1, 0, 64, 1), loaded.get(team, MARKS).tasks());
    }

    @Test
    void theEditorWritesTheTaskBackAsItWas() {
        TaskDef level = book().quests().get(BRONZE).tasks().getFirst();
        JsonObject written = TaskDef.CODEC.encodeStart(JsonOps.INSTANCE, level).getOrThrow().getAsJsonObject();
        written.remove("count");
        assertEquals(JsonParser.parseString(TASK), written, "bare ids stay bare, the stack keeps its count, no mode is added");
        TaskDef again = TaskDef.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow();
        assertEquals(((CollectionTask) level).items(), ((CollectionTask) again).items());
        assertEquals(4, again.target(), "without a level's amount it counts every item");
    }
}
