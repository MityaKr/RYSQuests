package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** An item id or, written with a leading {@code #}, an item tag. */
public record ItemRef(ResourceLocation id, boolean tag) {
    public static final Codec<ItemRef> CODEC = Codec.STRING.comapFlatMap(
            text -> text.startsWith("#")
                    ? ResourceLocation.read(text.substring(1)).map(id -> new ItemRef(id, true))
                    : ResourceLocation.read(text).map(id -> new ItemRef(id, false)),
            ItemRef::toString);

    /** Tag keys made once: inventories are checked every second for every open task. */
    private static final Map<ResourceLocation, TagKey<Item>> TAGS = new ConcurrentHashMap<>();

    /** Whether the stack is this item or carries this tag. */
    public boolean test(ItemStack stack) {
        if (tag) {
            return stack.is(TAGS.computeIfAbsent(id, key -> TagKey.create(Registries.ITEM, key)));
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    @Override
    public String toString() {
        return tag ? "#" + id : id.toString();
    }
}
