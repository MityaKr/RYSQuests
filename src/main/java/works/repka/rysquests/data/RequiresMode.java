package works.repka.rysquests.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

/** How many dependencies must be complete: all, any, or at least {@code n}. */
public sealed interface RequiresMode {
    RequiresMode ALL = new Named("all");
    RequiresMode ANY = new Named("any");

    record Named(String name) implements RequiresMode {
    }

    record AtLeast(int n) implements RequiresMode {
        static final Codec<AtLeast> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("n").forGetter(AtLeast::n)
        ).apply(instance, AtLeast::new));
    }

    Codec<RequiresMode> CODEC = Codec.either(Codec.STRING.comapFlatMap(RequiresMode::named, Named::name), AtLeast.CODEC)
            .xmap(either -> either.map(named -> named, atLeast -> atLeast), RequiresMode::split);

    private static DataResult<Named> named(String text) {
        return switch (text) {
            case "all" -> DataResult.success((Named) ALL);
            case "any" -> DataResult.success((Named) ANY);
            default -> DataResult.error(() -> "Unknown requires_mode: " + text);
        };
    }

    private static Either<Named, AtLeast> split(RequiresMode mode) {
        return switch (mode) {
            case Named named -> Either.left(named);
            case AtLeast atLeast -> Either.right(atLeast);
        };
    }
}
