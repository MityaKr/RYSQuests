package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** Requires an item, a tag, or any of several of them. */
public record ItemTask(
        Optional<ItemRef> item,
        List<ItemRef> anyOf,
        int count,
        Mode mode,
        Optional<ComponentMatch> nbt,
        Optional<String> title) implements TaskDef {

    public enum Mode implements StringRepresentable {
        CONSUME("consume"),
        OBSERVE("observe"),
        ACCUMULATE("accumulate"),
        /** Counts what the player crafts or smelts from the moment the quest opens, as FTB Quests' "only from crafting". */
        CRAFT("craft");

        public static final Codec<Mode> CODEC = StringRepresentable.fromValues(Mode::values);

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record ComponentMatch(boolean strict, Optional<DataComponentPatch> components) {
        static final Codec<ComponentMatch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("strict", false).forGetter(ComponentMatch::strict),
                DataComponentPatch.CODEC.optionalFieldOf("components").forGetter(ComponentMatch::components)
        ).apply(instance, ComponentMatch::new));
    }

    public static final MapCodec<ItemTask> CODEC = RecordCodecBuilder.<ItemTask>mapCodec(instance -> instance.group(
            ItemRef.CODEC.optionalFieldOf("item").forGetter(ItemTask::item),
            ItemRef.CODEC.listOf().optionalFieldOf("any_of", List.of()).forGetter(ItemTask::anyOf),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ItemTask::count),
            Mode.CODEC.optionalFieldOf("mode", Mode.CONSUME).forGetter(ItemTask::mode),
            ComponentMatch.CODEC.optionalFieldOf("nbt").forGetter(ItemTask::nbt),
            Codec.STRING.optionalFieldOf("title").forGetter(ItemTask::title)
    ).apply(instance, ItemTask::new)).validate(ItemTask::checkTarget);

    private static DataResult<ItemTask> checkTarget(ItemTask task) {
        return task.item.isPresent() == task.anyOf.isEmpty()
                ? DataResult.success(task)
                : DataResult.error(() -> "Item task needs exactly one of 'item' and 'any_of'");
    }

    @Override
    public TaskType<?> type() {
        return TaskTypes.ITEM.get();
    }

    @Override
    public int target() {
        return count;
    }

    /** The one test of a stack against this task, shared by the server and the book, components included. */
    public boolean matches(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean found = item.isPresent() && item.get().test(stack);
        for (int i = 0; !found && i < anyOf.size(); i++) {
            found = anyOf.get(i).test(stack);
        }
        return found && nbt.flatMap(ComponentMatch::components).map(patch -> hasComponents(stack, patch)).orElse(true);
    }

    private static boolean hasComponents(net.minecraft.world.item.ItemStack stack, DataComponentPatch patch) {
        for (var entry : patch.entrySet()) {
            if (entry.getValue().isPresent() && !entry.getValue().get().equals(stack.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<ItemRef> itemRefs() {
        List<ItemRef> refs = new ArrayList<>(anyOf);
        item.ifPresent(refs::add);
        return refs;
    }
}
