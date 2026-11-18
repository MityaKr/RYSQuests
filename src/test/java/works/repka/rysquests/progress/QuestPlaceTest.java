package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Layout;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.VisitTask;

/** Where a quest's goal stands on the map (RYSMap plan, step T3). */
class QuestPlaceTest {
    private static final ResourceLocation NETHER = ResourceLocation.parse("minecraft:the_nether");

    private static Quest quest(TaskDef... tasks) {
        return new Quest(ResourceLocation.parse("t:q"), Optional.empty(), Optional.empty(), "t", Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(), RequiresMode.ALL, Optional.empty(), List.of(tasks), List.of(), Optional.empty(),
                new QuestOptions(Layout.DEFAULT, 1, false, false, Optional.empty(), false, List.of()));
    }

    private static VisitTask at(Optional<ResourceLocation> dimension, int x, int y, int z, int radius, Optional<List<Integer>> size) {
        return new VisitTask(dimension, Optional.empty(), Optional.empty(), Optional.of(List.of(x, y, z)), radius, Optional.empty(), size);
    }

    @Test
    void questsWithoutPositionHaveNoPlace() {
        VisitTask structure = new VisitTask(Optional.empty(), Optional.empty(), Optional.of(new ItemRef(ResourceLocation.parse("t:fort"), true)),
                Optional.empty(), 8, Optional.empty(), Optional.empty());
        VisitTask dimension = new VisitTask(Optional.of(NETHER), Optional.empty(), Optional.empty(), Optional.empty(), 8, Optional.empty(), Optional.empty());

        assertTrue(QuestPlace.of(quest(structure, dimension, new CheckboxTask(Optional.empty()))).isEmpty());
        assertTrue(QuestPlace.of(quest()).isEmpty());
    }

    @Test
    void sphereStandsAtItsPosition() {
        QuestPlace place = QuestPlace.of(quest(new CheckboxTask(Optional.empty()), at(Optional.of(NETHER), 100, 64, -20, 12, Optional.empty()))).orElseThrow();

        assertEquals(new QuestPlace(NETHER, 100, 64, -20, 12, 1), place);
    }

    @Test
    void boxStandsAtItsMiddleAndWithoutDimensionInTheOverworld() {
        QuestPlace place = QuestPlace.of(quest(at(Optional.empty(), 0, 60, 10, 8, Optional.of(List.of(10, 4, 30))))).orElseThrow();

        assertEquals(QuestPlace.OVERWORLD, place.dimension());
        assertEquals(5, place.x());
        assertEquals(62, place.y());
        assertEquals(25, place.z());
        assertEquals(15, place.radius());
    }

    @Test
    void aheadSkipsPlacesAlreadyVisited() {
        Quest quest = quest(at(Optional.empty(), 1, 2, 3, 4, Optional.empty()), at(Optional.of(NETHER), 7, 8, 9, 4, Optional.empty()));

        assertEquals(0, QuestPlace.ahead(quest, List.of()).orElseThrow().task());
        assertEquals(1, QuestPlace.ahead(quest, List.of(1, 0)).orElseThrow().task());
        assertTrue(QuestPlace.ahead(quest, List.of(1, 1)).isEmpty());
        assertEquals(0, QuestPlace.of(quest).orElseThrow().task());
    }
}
