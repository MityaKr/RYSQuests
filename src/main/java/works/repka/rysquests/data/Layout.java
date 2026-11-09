package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Manual placement of a quest on the map; empty coordinates leave it to the auto layout. */
public record Layout(boolean pin, Optional<Integer> x, Optional<Integer> y) {
    public static final Layout DEFAULT = new Layout(false, Optional.empty(), Optional.empty());

    public static final Codec<Layout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("pin", false).forGetter(Layout::pin),
            Codec.INT.optionalFieldOf("x").forGetter(Layout::x),
            Codec.INT.optionalFieldOf("y").forGetter(Layout::y)
    ).apply(instance, Layout::new));
}
