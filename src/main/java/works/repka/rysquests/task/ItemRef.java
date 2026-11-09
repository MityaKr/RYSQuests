package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

/** An item id or, written with a leading {@code #}, an item tag. */
public record ItemRef(ResourceLocation id, boolean tag) {
    public static final Codec<ItemRef> CODEC = Codec.STRING.comapFlatMap(
            text -> text.startsWith("#")
                    ? ResourceLocation.read(text.substring(1)).map(id -> new ItemRef(id, true))
                    : ResourceLocation.read(text).map(id -> new ItemRef(id, false)),
            ItemRef::toString);

    @Override
    public String toString() {
        return tag ? "#" + id : id.toString();
    }
}
