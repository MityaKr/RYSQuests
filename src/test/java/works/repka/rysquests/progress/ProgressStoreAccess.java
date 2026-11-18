package works.repka.rysquests.progress;

import net.minecraft.nbt.CompoundTag;

/** Reads a saved store in tests of other packages, as the world does on load. */
public final class ProgressStoreAccess {
    private ProgressStoreAccess() {
    }

    public static ProgressStore load(CompoundTag data) {
        return ProgressStore.load(data, null);
    }
}
