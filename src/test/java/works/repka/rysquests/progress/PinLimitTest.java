package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Quest;

/** The limit of pinned cards, one rule for the server that refuses and the achievements window that greys the pin out beforehand. */
class PinLimitTest {
    private static final int LIMIT = 3;
    private final Map<ResourceLocation, Quest> quests = new HashMap<>();
    private final Map<ResourceLocation, QuestProgress> progress = new HashMap<>();

    private ResourceLocation quest(String path, boolean repeatable) {
        Quest quest = Quest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\": \"t:" + path + "\", \"title\": \"Q\""
                + (repeatable ? ", \"repeatable\": {\"cooldown_minutes\": 5}" : "") + ", \"tasks\": [{\"type\": \"rysquests:checkbox\"}]}")).getOrThrow();
        quests.put(quest.id(), quest);
        return quest.id();
    }

    private boolean canPin(ResourceLocation quest, List<ResourceLocation> pinned, int ways) {
        return PinLimit.canPin(quest, pinned, ways, LIMIT, quests::get, id -> progress.getOrDefault(id, QuestProgress.EMPTY));
    }

    @Test
    void aFourthCannotBePinnedWhileThreeAre() {
        List<ResourceLocation> pinned = List.of(quest("a", false), quest("b", false), quest("c", false));
        ResourceLocation fourth = quest("d", false);

        assertEquals(3, PinLimit.taken(pinned, quests::get, id -> QuestProgress.EMPTY));
        assertFalse(canPin(fourth, pinned, 0), "the window greys the pin out instead of a press that does nothing");
        assertTrue(canPin(pinned.getFirst(), pinned, 0), "a pinned one can always be unpinned");
        assertTrue(canPin(fourth, pinned.subList(0, 2), 0));
    }

    @Test
    void waysShareTheLimit() {
        List<ResourceLocation> pinned = List.of(quest("a", false), quest("b", false));
        assertFalse(canPin(quest("d", false), pinned, 1));
    }

    @Test
    void aFinishedQuestFreesItsPlaceButARepeatableOneKeepsIt() {
        ResourceLocation done = quest("a", false);
        ResourceLocation again = quest("b", true);
        List<ResourceLocation> pinned = List.of(done, again, quest("c", false));
        progress.put(done, new QuestProgress(List.of(1), 100, List.of()));
        progress.put(again, new QuestProgress(List.of(1), 100, List.of()));

        assertTrue(canPin(quest("d", false), pinned, 0));
        assertEquals(2, PinLimit.taken(pinned, quests::get, id -> progress.getOrDefault(id, QuestProgress.EMPTY)));
    }

    @Test
    void aQuestTakenOutOfTheBookTakesNoPlace() {
        List<ResourceLocation> pinned = List.of(quest("a", false), quest("b", false), ResourceLocation.parse("t:gone"));
        assertTrue(canPin(quest("d", false), pinned, 0));
    }
}
