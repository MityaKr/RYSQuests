package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.ItemTask;

/**
 * RepYS, «Тертиевая эссенция»: the server's record of the quest held its one task done ({@code tasks [1]}) but no completion, a
 * record taken over from FTB Quests, which counts tasks of a quest whose dependencies are not done yet. The book said "done · the
 * item stays with you" and "Засчитается сейчас", while the server's poll only wrote a quest whose task grew, which a full task never
 * does: the quest stayed open for good. Now the poll completes such a quest as soon as it is open, so the book and the server agree;
 * while it is still closed, the book shows it closed, with what it waits for, and the server does not count it.
 */
class FullTasksOpenQuestTest {
    private static final ResourceLocation CRYSTAL = ResourceLocation.parse("repys:b13_e5_bio/resonant_crystal");
    private static final ResourceLocation PRUDENTIUM = ResourceLocation.parse("repys:b12_e5_garden/prudentium_essence");
    private static final ResourceLocation TERTIUM = ResourceLocation.parse("repys:b13_e5_bio/tertium_essence");
    private static final QuestProgress DONE = new QuestProgress(List.of(1), 1794013539341L, List.of(0, 1));
    /** The server's record of «Тертиевая эссенция», as the RepYS world kept it. */
    private static final QuestProgress IMPORTED = new QuestProgress(List.of(1), 0, List.of());
    private static final long NOW = 1_794_600_000_000L;

    private static Quest tertium() {
        ItemTask observe = new ItemTask(Optional.of(new ItemRef(ResourceLocation.parse("mysticalagriculture:tertium_essence"), false)), List.of(), 1,
                ItemTask.Mode.OBSERVE, Optional.empty(), Optional.empty());
        return new Quest(TERTIUM, Optional.of(ResourceLocation.parse("repys:b13_e5_bio")), Optional.empty(), "t", Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(CRYSTAL, PRUDENTIUM), RequiresMode.ALL, Optional.empty(), List.of(observe),
                List.of(new works.repka.rysquests.reward.XpReward(Optional.empty(), Optional.of(25), works.repka.rysquests.data.TeamMode.EACH)), Optional.empty(),
                new QuestOptions(Layout.DEFAULT, 1, false, false, Optional.empty(), false, List.of()));
    }

    private static Function<ResourceLocation, QuestProgress> world(Map<ResourceLocation, QuestProgress> rows) {
        return id -> rows.getOrDefault(id, QuestProgress.EMPTY);
    }

    @Test
    void anOpenQuestWithEveryTaskDoneCompletesOnTheNextPoll() {
        Quest quest = tertium();
        Function<ResourceLocation, QuestProgress> progress = world(Map.of(CRYSTAL, DONE, PRUDENTIUM, DONE, TERTIUM, IMPORTED));
        // the book and the server read the same rules: the quest is open and its task is done
        assertTrue(QuestLogic.isUnlocked(quest, progress));
        assertEquals(QuestStatus.IN_PROGRESS, QuestLogic.status(quest, progress));
        assertTrue(QuestLogic.allTasksDone(quest, IMPORTED.tasks()));

        // the poll finds nothing new to count, and still completes the quest
        Optional<QuestProgress> written = QuestLogic.afterPoll(quest, IMPORTED, IMPORTED.taskValues(1), false, NOW);
        assertTrue(written.isPresent(), "the poll writes the quest that waited with its task done");
        assertTrue(written.get().completed());
        assertEquals(NOW, written.get().completedAt());
        assertEquals(QuestStatus.COMPLETED, QuestLogic.status(quest, world(Map.of(CRYSTAL, DONE, PRUDENTIUM, DONE, TERTIUM, written.get()))));
    }

    @Test
    void whileADependencyIsOpenTheQuestIsClosedOnBothSides() {
        Quest quest = tertium();
        // the garden's essence not done yet: the server does not poll the quest, and the book does not promise it
        Function<ResourceLocation, QuestProgress> progress = world(Map.of(CRYSTAL, DONE, TERTIUM, IMPORTED));
        assertFalse(QuestLogic.isUnlocked(quest, progress));
        assertEquals(QuestStatus.LOCKED, QuestLogic.status(quest, progress));
    }

    @Test
    void aPollWithNothingNewWritesNothing() {
        Quest quest = tertium();
        QuestProgress open = new QuestProgress(List.of(0), 0, List.of());
        assertTrue(QuestLogic.afterPoll(quest, open, open.taskValues(1), false, NOW).isEmpty());
        Optional<QuestProgress> grown = QuestLogic.afterPoll(quest, open, List.of(1), true, NOW);
        assertTrue(grown.isPresent() && grown.get().completed(), "the item came: the task grows and the quest completes as before");
    }
    /** The gate of Oritech: a checkbox the book shows ticked, whose quest the server never completed. */
    @Test
    void theStuckGateOfOritechIsFoundAndMendedAtLogin() {
        ResourceLocation circuit = ResourceLocation.parse("repys:b13_e5_bio/elite_control_circuit");
        ResourceLocation gateId = ResourceLocation.parse("repys:b14_e6_oritech/oritech");
        Quest gate = new Quest(gateId, Optional.of(ResourceLocation.parse("repys:b14_e6_oritech")), Optional.empty(), "t", Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(circuit), RequiresMode.ALL, Optional.empty(), List.of(new works.repka.rysquests.task.CheckboxTask(Optional.empty())),
                List.of(new works.repka.rysquests.reward.XpReward(Optional.empty(), Optional.of(25), works.repka.rysquests.data.TeamMode.EACH)), Optional.empty(),
                new QuestOptions(Layout.DEFAULT, 1, false, false, Optional.empty(), false, List.of()));
        // as the RepYS world kept it: tasks [1], no completion, the circuit done since
        Function<ResourceLocation, QuestProgress> before = world(Map.of(gateId, IMPORTED));
        assertFalse(QuestLogic.stuck(gate, before), "closed while the circuit is not done: nothing to mend, the book shows it closed");
        Function<ResourceLocation, QuestProgress> now = world(Map.of(circuit, DONE, gateId, IMPORTED));
        assertTrue(QuestLogic.stuck(gate, now));
        QuestProgress mended = QuestLogic.finish(gate, IMPORTED, IMPORTED.taskValues(1), NOW);
        assertEquals(QuestStatus.COMPLETED, QuestLogic.status(gate, world(Map.of(circuit, DONE, gateId, mended))));
        assertFalse(QuestLogic.stuck(gate, world(Map.of(circuit, DONE, gateId, mended))), "mended once");
        // a quest with an open task or already done is not stuck
        assertFalse(QuestLogic.stuck(gate, world(Map.of(circuit, DONE, gateId, new QuestProgress(List.of(0), 0, List.of())))));
        assertFalse(QuestLogic.stuck(gate, world(Map.of(circuit, DONE, gateId, DONE))));
    }
}
