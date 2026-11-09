package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.TaskTypes;

/** One task of a quest as written in the book. */
public interface TaskDef {
    Codec<TaskDef> CODEC = TaskTypes.REGISTRY.byNameCodec().dispatch("type", TaskDef::type, TaskType::codec);

    TaskType<?> type();

    Optional<String> title();

    /** Progress value at which the task is done. */
    default int target() {
        return 1;
    }

    /** Items and tags this task refers to. */
    default List<ItemRef> itemRefs() {
        return List.of();
    }
}
