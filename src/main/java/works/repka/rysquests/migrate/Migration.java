package works.repka.rysquests.migrate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** One step of {@code migrations.json}. Applied once per world, in file order. */
public sealed interface Migration {
    String id();

    String op();

    record Rename(String id, ResourceLocation from, ResourceLocation to) implements Migration {
        static final MapCodec<Rename> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Rename::id),
                ResourceLocation.CODEC.fieldOf("from").forGetter(Rename::from),
                ResourceLocation.CODEC.fieldOf("to").forGetter(Rename::to)
        ).apply(instance, Rename::new));

        @Override
        public String op() {
            return "rename";
        }
    }

    record Merge(String id, List<ResourceLocation> from, ResourceLocation to) implements Migration {
        static final MapCodec<Merge> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Merge::id),
                ResourceLocation.CODEC.listOf().fieldOf("from").forGetter(Merge::from),
                ResourceLocation.CODEC.fieldOf("to").forGetter(Merge::to)
        ).apply(instance, Merge::new));

        @Override
        public String op() {
            return "merge";
        }
    }

    record Remove(String id, ResourceLocation from) implements Migration {
        static final MapCodec<Remove> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Remove::id),
                ResourceLocation.CODEC.fieldOf("from").forGetter(Remove::from)
        ).apply(instance, Remove::new));

        @Override
        public String op() {
            return "remove";
        }
    }

    /** Marks FTB ids as handled. The import maps progress itself, so applying this changes nothing. */
    record FtbqMap(String id, Map<String, ResourceLocation> fromFtbq) implements Migration {
        static final MapCodec<FtbqMap> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(FtbqMap::id),
                Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).fieldOf("from_ftbq").forGetter(FtbqMap::fromFtbq)
        ).apply(instance, FtbqMap::new));

        @Override
        public String op() {
            return "map";
        }
    }

    Codec<Migration> CODEC = Codec.STRING.dispatch("op", Migration::op, Migration::codecFor);

    Codec<List<Migration>> FILE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CODEC.listOf().fieldOf("migrations").forGetter(list -> list)
    ).apply(instance, list -> list));

    private static MapCodec<? extends Migration> codecFor(String op) {
        return switch (op) {
            case "rename" -> Rename.CODEC;
            case "merge" -> Merge.CODEC;
            case "remove" -> Remove.CODEC;
            case "map" -> FtbqMap.CODEC;
            default -> MapCodec.unit(() -> null).flatXmap(
                    ignored -> DataResult.error(() -> "Unknown migration op: " + op),
                    ignored -> DataResult.error(() -> "Unknown migration op: " + op));
        };
    }
}
