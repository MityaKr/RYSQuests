package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;

/** Optional quest fields, kept apart because a record codec takes at most 16 fields. */
public record QuestOptions(
        Layout layout,
        int size,
        boolean optional,
        boolean hidden,
        Optional<Repeatable> repeatable,
        boolean page,
        List<String> tags) {

    public record Repeatable(int cooldownMinutes) {
        static final Codec<Repeatable> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("cooldown_minutes").forGetter(Repeatable::cooldownMinutes)
        ).apply(instance, Repeatable::new));
    }

    public static final MapCodec<QuestOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Layout.CODEC.optionalFieldOf("layout", Layout.DEFAULT).forGetter(QuestOptions::layout),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("size", 1).forGetter(QuestOptions::size),
            Codec.BOOL.optionalFieldOf("optional", false).forGetter(QuestOptions::optional),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(QuestOptions::hidden),
            Repeatable.CODEC.optionalFieldOf("repeatable").forGetter(QuestOptions::repeatable),
            Codec.BOOL.optionalFieldOf("page", false).forGetter(QuestOptions::page),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(QuestOptions::tags)
    ).apply(instance, QuestOptions::new));
}
