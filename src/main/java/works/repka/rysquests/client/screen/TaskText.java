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

    /** An item to show by its id: a variant from the creative search tab when the bare stack is not one the game itself offers. */
    public static ItemStack stackOf(ResourceLocation id) {
        return display(BuiltInRegistries.ITEM.get(id));
    }

    /**
     * Some items only make sense with a component: a bare EnderIO conduit has no conduit type and draws as {@code <MISSING>}.
     * When the search tab has the item but never bare, its first variant there is shown instead. Counting tasks still goes by
     * the id alone, this is only the picture and the name.
     */
    public static ItemStack display(net.minecraft.world.item.Item item) {
        ItemStack bare = new ItemStack(item);
        if (bare.isEmpty()) {
            return bare;
        }
        ItemStack variant = variants().get(item);
        return variant == null ? bare : variant.copy();
    }

    private static java.util.Collection<ItemStack> variantsFrom;
    private static java.util.Map<net.minecraft.world.item.Item, ItemStack> variants = java.util.Map.of();

    /** Items whose search tab entries are all variants, with the first of them; rebuilt when the game rebuilds the tabs. */
    private static synchronized java.util.Map<net.minecraft.world.item.Item, ItemStack> variants() {
        java.util.Collection<ItemStack> tab;
        try {
            tab = net.minecraft.world.item.CreativeModeTabs.searchTab().getDisplayItems();
        } catch (RuntimeException e) {
            return variants;
        }
        // The tabs are only filled once the game (or JEI) builds them; until then keep looking, each build is a new collection.
        if (tab == variantsFrom || tab.isEmpty()) {
            return variants;
        }
        java.util.Map<net.minecraft.world.item.Item, ItemStack> first = new java.util.HashMap<>();
        java.util.Set<net.minecraft.world.item.Item> bare = new java.util.HashSet<>();
        for (ItemStack stack : java.util.List.copyOf(tab)) {
            if (stack.getComponentsPatch().isEmpty()) {
                bare.add(stack.getItem());
            } else {
                first.putIfAbsent(stack.getItem(), stack.copyWithCount(1));
            }
        }
        first.keySet().removeAll(bare);
        variants = java.util.Map.copyOf(first);
        variantsFrom = tab;
        return variants;
    }

    public static ItemStack icon(TaskDef task) {
        if (task instanceof works.repka.rysquests.task.XpTask) {
            return new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
        }
        if (task instanceof works.repka.rysquests.task.CollectionTask collection) {
            return icon(collection.items().getFirst());
        }
        if (!(task instanceof ItemTask item) || item.itemRefs().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemRef ref = item.itemRefs().getFirst();
        if (!ref.tag()) {
            return stackOf(ref.id());
        }
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ref.id()))
                .filter(tag -> tag.size() > 0)
                .map(tag -> display(tag.get(0).value()))
                .orElse(ItemStack.EMPTY);
    }

    private static final java.util.Map<TaskDef, String> NAMES = new java.util.concurrent.ConcurrentHashMap<>();
    private static Object namesBook;
    private static String namesLanguage;

    /** {@link #describe} as a string, kept per book and language: the board and the card ask for it every frame. */
    public static String name(TaskDef task) {
        Object book = works.repka.rysquests.client.ClientBook.current();
        String language = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        if (book != namesBook || !language.equals(namesLanguage)) {
            NAMES.clear();
            namesBook = book;
            namesLanguage = language;
        }
        return NAMES.computeIfAbsent(task, key -> describe(key).getString());
    }

    public static Component describe(TaskDef task) {
        if (task.title().isPresent()) {
            return Component.translatable(task.title().get());
        }
        return switch (task) {
            case ItemTask item when item.mode() == ItemTask.Mode.CRAFT -> Component.translatable("rysquests.task.craft", itemName(item));
            case ItemTask item -> itemName(item);
            case works.repka.rysquests.task.XpTask xp -> Component.translatable(xp.points() ? "rysquests.task.xp_points" : "rysquests.task.xp_levels", xp.amount());
            case KillTask kill -> Component.translatable("rysquests.task.kill", BuiltInRegistries.ENTITY_TYPE.get(kill.entity()).getDescription());
            case StatTask stat -> Component.translatable("rysquests.task.stat", statName(stat));
            case VisitTask visit -> Component.translatable("rysquests.task.visit", visit.structure().map(ItemRef::toString)
                    .or(() -> visit.biome().map(ItemRef::toString))
                    .or(() -> visit.dimension().map(ResourceLocation::toString))
                    .orElseGet(() -> visit.pos().map(Object::toString).orElse("")));
            case FluidTask fluid -> Component.translatable("rysquests.task.fluid", BuiltInRegistries.FLUID.get(fluid.fluid()).getFluidType().getDescription());
            case works.repka.rysquests.task.ScriptTask script -> Component.literal(script.script().toString());
            case works.repka.rysquests.task.CollectionTask collection -> Component.translatable("rysquests.task.collection");
            case AdvancementTask advancement -> Component.translatable("rysquests.task.advancement", advancement.advancement().toString());
            case works.repka.rysquests.task.ClaimTask claims -> Component.translatable("rysquests.task.claims", claims.count());
            default -> Component.translatable("rysquests.task.checkbox");
        };
    }

    private static Component itemName(ItemTask item) {
        return icon(item).isEmpty() ? Component.literal(item.itemRefs().getFirst().toString()) : icon(item).getHoverName();
    }

    private static Component statName(StatTask stat) {
        return Component.translatable("stat." + stat.entry().toString().replace(':', '.'));
    }
}
