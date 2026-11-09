package works.repka.rysquests.api;

import com.mojang.serialization.MapCodec;
import works.repka.rysquests.data.TaskDef;

/** A kind of quest task, selected by the {@code type} field of its JSON. */
public interface TaskType<T extends TaskDef> {
    MapCodec<T> codec();
}
