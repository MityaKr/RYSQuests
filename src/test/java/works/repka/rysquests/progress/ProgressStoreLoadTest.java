package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** One broken record in the save loses only itself: the rest of the progress loads, and is not written over with nothing. */
class ProgressStoreLoadTest {
    private static final ResourceLocation GOOD = ResourceLocation.parse("t:good");
    private static final ResourceLocation BAD = ResourceLocation.parse("t:bad");

    @Test
    void aBrokenRecordDoesNotTakeTheOthersWithIt() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        ProgressStore store = new ProgressStore();
        store.put(alice, Map.of(GOOD, new QuestProgress(List.of(3), 10, List.of(0))));
        store.put(bob, Map.of(BAD, new QuestProgress(List.of(1), 0, List.of())));
        CompoundTag saved = store.save(new CompoundTag(), null);
        saved.getCompound("teams").getCompound(bob.toString()).getCompound(BAD.toString()).put("tasks", StringTag.valueOf("broken"));

        ProgressStore loaded = ProgressStore.load(saved, null);

        assertEquals(List.of(3), loaded.get(alice, GOOD).tasks());
        assertTrue(loaded.get(alice, GOOD).completed());
    }
}
