package works.repka.rysquests.task;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/**
 * A collection of an achievement (ADR-62): a list of items, each counted once it has been in the inventory of anyone who shares the
 * progress. The achievement's levels count how many of them are collected.
 *
 * <p>The parser turns it into two kinds of hidden quest: one quest of marks with an {@code observe} item task per entry, which keeps
 * what is collected as ordinary task progress (shared by a team, copied to a player who leaves with a copy, merged when teams merge),
 * and a quest per level with this task, whose value is the number of marks and whose target is the level's amount.
 *
 * @param items the entries in their order; an entry is written as an item id or a {@code #tag}, or as an item task's fields
 *              ({@code item} or {@code any_of}, {@code count} held at once, {@code nbt}, {@code title}); its {@code mode} is always
 *              {@code observe}
 * @param count how many entries make the level; the parser sets it from the level's amount, every entry when left out
 */
public record CollectionTask(List<ItemTask> items, Optional<Integer> count, Optional<String> title) implements TaskDef {

    /** An entry, as a bare id when it is only that, so that a file read and written by the editor keeps its look. */
    public static final Codec<ItemTask> ENTRY_CODEC = Codec.either(ItemRef.CODEC, ItemTask.CODEC.codec()).xmap(
            either -> either.map(ref -> new ItemTask(Optional.of(ref), List.of(), 1, ItemTask.Mode.OBSERVE, Optional.empty(), Optional.empty()),
                    task -> new ItemTask(task.item(), task.anyOf(), task.count(), ItemTask.Mode.OBSERVE, task.nbt(), task.title())),
            CollectionTask::written);

    public static final MapCodec<CollectionTask> CODEC = RecordCodecBuilder.<CollectionTask>mapCodec(instance -> instance.group(
            ENTRY_CODEC.listOf().fieldOf("items").forGetter(CollectionTask::items),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(CollectionTask::count),
            Codec.STRING.optionalFieldOf("title").forGetter(CollectionTask::title)
    ).apply(instance, CollectionTask::new)).validate(task -> task.items.isEmpty()
            ? DataResult.error(() -> "A collection needs at least one item")
            : DataResult.success(task));

    /** The entry as it is written: a bare id, or the item task's fields without the mode, which is always observe. */
    private static Either<ItemRef, ItemTask> written(ItemTask entry) {
        boolean bare = entry.item().isPresent() && entry.anyOf().isEmpty() && entry.count() == 1 && entry.nbt().isEmpty() && entry.title().isEmpty();
        return bare ? Either.left(entry.item().get())
                // CONSUME is the item task's default, so the field is left out of the file
                : Either.right(new ItemTask(entry.item(), entry.anyOf(), entry.count(), ItemTask.Mode.CONSUME, entry.nbt(), entry.title()));
    }

    /** The same collection counting to another amount: what a level's quest is made with. */
    public CollectionTask withCount(int amount) {
        return new CollectionTask(items, Optional.of(amount), title);
    }

    @Override
    public TaskType<?> type() {
        return TaskTypes.COLLECTION.get();
    }

    @Override
    public int target() {
        return count.orElse(items.size());
    }

    @Override
    public List<ItemRef> itemRefs() {
        List<ItemRef> refs = new ArrayList<>();
        items.forEach(entry -> refs.addAll(entry.itemRefs()));
        return refs;
    }
}
