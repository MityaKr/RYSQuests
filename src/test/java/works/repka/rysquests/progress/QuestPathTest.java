package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Layout;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;

/** The way to a closed quest and the next step on "Now" (plan, section 3). */
class QuestPathTest {
    private static final QuestProgress DONE = new QuestProgress(List.of(1), 5, List.of());

    private static Quest quest(String id, boolean optional, RequiresMode mode, String chapter, String... requires) {
        return new Quest(ResourceLocation.parse("t:" + id), Optional.of(ResourceLocation.parse("t:" + chapter)), Optional.empty(), id, Optional.empty(),
                Optional.empty(), Optional.empty(), java.util.Arrays.stream(requires).map(r -> ResourceLocation.parse("t:" + r)).toList(), mode,
                Optional.empty(), List.of(), List.of(), Optional.empty(),
                new QuestOptions(Layout.DEFAULT, 1, optional, false, Optional.empty(), false, List.of()));
    }

    private static Quest quest(String id, String... requires) {
        return quest(id, false, RequiresMode.ALL, "c", requires);
    }

    private static Map<ResourceLocation, Quest> book(Quest... quests) {
        Map<ResourceLocation, Quest> map = new LinkedHashMap<>();
        for (Quest quest : quests) {
            map.put(quest.id(), quest);
        }
        return map;
    }

    private static Function<ResourceLocation, QuestProgress> done(String... ids) {
        Map<ResourceLocation, QuestProgress> map = new HashMap<>();
        for (String id : ids) {
            map.put(ResourceLocation.parse("t:" + id), DONE);
        }
        return id -> map.getOrDefault(id, QuestProgress.EMPTY);
    }

    private static List<String> names(List<Quest> quests) {
        return quests.stream().map(Quest::title).toList();
    }

    @Test
    void wayListsEveryUnfinishedStepBeforeTheGoal() {
        // coil (done) -> mechanism -> dynamo -> capacitor -> block, and block also needs wire
        Map<ResourceLocation, Quest> book = book(quest("coil"), quest("mechanism", "coil"), quest("dynamo", "mechanism"),
                quest("capacitor", "dynamo"), quest("wire"), quest("block", "capacitor", "wire"));
        QuestPath path = QuestPath.of(book.get(ResourceLocation.parse("t:block")), book, done("coil"));

        assertEquals(List.of("mechanism", "dynamo", "capacitor", "wire", "block"), names(path.steps()));
        assertEquals(5, path.count());
        assertEquals("mechanism", path.first().title());
        assertEquals(1, path.done(), "the done coil is what the steps need directly");
        assertTrue(path.choices().isEmpty());
    }

    @Test
    void anyModeTakesTheNearestAndKeepsTheOthers() {
        // capacitor needs any one of kinetic (1 step after mechanism), alternator (two steps) and coil (two steps)
        Map<ResourceLocation, Quest> book = book(quest("mechanism"), quest("kinetic", "mechanism"), quest("alt_base"),
                quest("alternator", "alt_base", "mechanism"), quest("gen_base"), quest("gen_coil", "gen_base", "mechanism"),
                quest("capacitor", false, RequiresMode.ANY, "c", "alternator", "kinetic", "gen_coil"));
        Quest capacitor = book.get(ResourceLocation.parse("t:capacitor"));
        QuestPath path = QuestPath.of(capacitor, book, done());

        assertEquals(List.of("mechanism", "kinetic", "capacitor"), names(path.steps()));
        assertEquals(1, path.choices().size());
        assertEquals(List.of("kinetic"), names(path.choices().getFirst().taken()));
        assertEquals(List.of("alternator", "gen_coil"), names(path.choices().getFirst().others()));

        List<QuestPath.Option> options = QuestPath.options(capacitor, book, done());
        assertEquals("kinetic", options.getFirst().quest().title());
        assertEquals(2, options.getFirst().cost());
        assertEquals(3, options.get(1).cost());
        assertTrue(QuestPath.anyOf(capacitor));
        assertEquals(1, QuestPath.needs(capacitor, done()));
        assertEquals(0, QuestPath.needs(capacitor, done("alternator")));
    }

    @Test
    void atLeastTakesAsManyAsItNeeds() {
        Map<ResourceLocation, Quest> book = book(quest("a"), quest("b"), quest("c0"), quest("c", "c0"),
                quest("goal", false, new RequiresMode.AtLeast(2), "c", "a", "b", "c"));
        QuestPath path = QuestPath.of(book.get(ResourceLocation.parse("t:goal")), book, done("a"));

        assertEquals(List.of("b", "goal"), names(path.steps()));
        assertFalse(QuestPath.anyOf(quest("one", false, RequiresMode.ANY, "c", "a")));
    }

    @Test
    void nextStepFollowsTheLastChapterThenTheFirstUnfinished() {
        Quest a1 = quest("a1", false, RequiresMode.ALL, "a");
        Quest a2 = quest("a2", false, RequiresMode.ALL, "a", "a1");
        Quest aSide = quest("a_side", true, RequiresMode.ALL, "a");
        Quest b1 = quest("b1", false, RequiresMode.ALL, "b");
        Quest b2 = quest("b2", false, RequiresMode.ALL, "b", "b1");
        Map<ResourceLocation, List<Quest>> chapters = Map.of(ResourceLocation.parse("t:a"), List.of(aSide, a1, a2),
                ResourceLocation.parse("t:b"), List.of(b1, b2));
        List<ResourceLocation> order = List.of(ResourceLocation.parse("t:a"), ResourceLocation.parse("t:b"));
        Function<ResourceLocation, List<Quest>> of = id -> chapters.getOrDefault(id, List.of());

        // Nothing done, no mark: the first main quest of the first chapter, never the optional one before it.
        Function<ResourceLocation, QuestProgress> nothing = done();
        NextStep.Result first = NextStep.choose(Optional.empty(), of, order, nothing, q -> QuestLogic.isUnlocked(q, nothing)).orElseThrow();
        assertEquals("a1", first.quest().title());
        assertFalse(first.fromLast());

        // The player last completed a quest in chapter b: its next main quest, though chapter a is unfinished.
        Function<ResourceLocation, QuestProgress> someB = done("b1");
        NextStep.Result inB = NextStep.choose(Optional.of(ResourceLocation.parse("t:b")), of, order, someB, q -> QuestLogic.isUnlocked(q, someB)).orElseThrow();
        assertEquals("b2", inB.quest().title());
        assertTrue(inB.fromLast());

        // Chapter b finished: back to the first unfinished chapter.
        Function<ResourceLocation, QuestProgress> allB = done("b1", "b2");
        assertEquals("a1", NextStep.choose(Optional.of(ResourceLocation.parse("t:b")), of, order, allB, q -> QuestLogic.isUnlocked(q, allB))
                .orElseThrow().quest().title());

        // An open quest wins over an earlier locked one.
        Quest locked = quest("locked", false, RequiresMode.ALL, "a", "never");
        assertEquals("a1", NextStep.nearest(List.of(locked, a1), nothing, q -> QuestLogic.isUnlocked(q, nothing)).title());

        // Every main quest done: no next step.
        Function<ResourceLocation, QuestProgress> all = done("a1", "a2", "b1", "b2");
        assertTrue(NextStep.choose(Optional.empty(), of, order, all, q -> true).isEmpty());
    }
}
