package works.repka.rysquests.client.screen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.task.AdvancementTask;
import works.repka.rysquests.task.FluidTask;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.KillTask;
import works.repka.rysquests.task.StatTask;
import works.repka.rysquests.task.VisitTask;

/** How tasks read and look on the screen and on the board. */
public final class TaskText {
    private TaskText() {
    }

    public static ItemStack stackOf(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    public static ItemStack icon(TaskDef task) {
        if (!(task instanceof ItemTask item) || item.itemRefs().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemRef ref = item.itemRefs().getFirst();
        if (!ref.tag()) {
            return stackOf(ref.id());
        }
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ref.id()))
                .filter(tag -> tag.size() > 0)
                .map(tag -> new ItemStack(tag.get(0)))
                .orElse(ItemStack.EMPTY);
    }

    public static Component describe(TaskDef task) {
        if (task.title().isPresent()) {
            return Component.translatable(task.title().get());
        }
        return switch (task) {
            case ItemTask item -> icon(item).isEmpty() ? Component.literal(item.itemRefs().getFirst().toString()) : icon(item).getHoverName();
            case KillTask kill -> Component.translatable("rysquests.task.kill", BuiltInRegistries.ENTITY_TYPE.get(kill.entity()).getDescription());
            case StatTask stat -> Component.translatable("rysquests.task.stat", statName(stat));
            case VisitTask visit -> Component.translatable("rysquests.task.visit", visit.structure().map(ItemRef::toString)
                    .or(() -> visit.biome().map(ItemRef::toString))
                    .or(() -> visit.dimension().map(ResourceLocation::toString))
                    .orElseGet(() -> visit.pos().map(Object::toString).orElse("")));
            case FluidTask fluid -> Component.translatable("rysquests.task.fluid", BuiltInRegistries.FLUID.get(fluid.fluid()).getFluidType().getDescription());
            case AdvancementTask advancement -> Component.translatable("rysquests.task.advancement", advancement.advancement().toString());
            default -> Component.translatable("rysquests.task.checkbox");
        };
    }

    private static Component statName(StatTask stat) {
        return Component.translatable("stat." + stat.entry().toString().replace(':', '.'));
    }
}
