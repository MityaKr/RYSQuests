package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class GuidePagesStoreTest {
    private static final UUID PLAYER = new UUID(1, 2);
    private static final UUID OTHER = new UUID(3, 4);

    @Test
    void aPageIsNewOnlyOnce() {
        ProgressStore store = new ProgressStore();

        assertTrue(store.markGuidePage(PLAYER, "a:b#c"));
        assertFalse(store.markGuidePage(PLAYER, "a:b#c"));
        assertTrue(store.isDirty());
    }

    @Test
    void readingIsPersonalAndSurvivesSaving() {
        ProgressStore store = new ProgressStore();
        store.markGuidePage(PLAYER, "a:b#c");

        ProgressStore loaded = ProgressStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(Set.of("a:b#c"), loaded.guidePages(PLAYER));
        assertEquals(Set.of(), loaded.guidePages(OTHER));
    }
}
