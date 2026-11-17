package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * Where a quest stands. With {@code pin} the coordinates are the author's manual place on the chapter map; without it they are
 * only remembered, as the FTB Quests import keeps the quest's place from FTB, and the layout places the quest itself.
 */
public record Layout(boolean pin, Optional<Integer> x, Optional<Integer> y) {
    public static final Layout DEFAULT = new Layout(false, Optional.empty(), Optional.empty());
    /** One cell of an FTB Quests chapter in our units: the grid step of the editor. */
    public static final int FTB_CELL = 24;

    public static final Codec<Layout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("pin", false).forGetter(Layout::pin),
            Codec.INT.optionalFieldOf("x").forGetter(Layout::x),
            Codec.INT.optionalFieldOf("y").forGetter(Layout::y)
    ).apply(instance, Layout::new));

    /** The manual place, only when the author pinned the quest there. */
    public Optional<Integer> fixedX() {
        return pin ? x : Optional.empty();
    }

    public Optional<Integer> fixedY() {
        return pin ? y : Optional.empty();
    }
}
