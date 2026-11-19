package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Reading a record makes no row (logic review 19.11): asking about any UUID used to leave an empty owner in the save. */
class StoreReadTest {
    @Test
    void readingLeavesNoEmptyRow() {
        ProgressStore store = new ProgressStore();
        assertEquals(QuestProgress.EMPTY, store.get(UUID.randomUUID(), ResourceLocation.parse("t:q")));
        assertTrue(store.allProgress().isEmpty());
    }
}
