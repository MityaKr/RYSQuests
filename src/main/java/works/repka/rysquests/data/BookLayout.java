package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;

/**
 * The baked map of the book, {@code rysquests/layout.json} beside {@code book.json}. The client lays the map out; an operator's
 * client sends the result to the server, which writes this file and sends it to everyone with the book, so that players never
 * lay the map out themselves. It only fits the book whose {@link QuestBook#layoutHash()} it names.
 *
 * @param form the form it was laid out in, never {@link MapForm#AUTO}
 * @param hash the {@link QuestBook#layoutHash()} of the book it was laid out for
 * @param nodes quest id to the centre of its node on the canvas
 * @param areas the rectangles of chapters by chapter id, and of epochs as {@code epoch:<number>}
 */
public record BookLayout(MapForm form, String hash, Map<String, Point> nodes, Map<String, Area> areas) {
    public static final String EPOCH_AREA = "epoch:";

    public record Point(int x, int y) {
        public static final Codec<Point> CODEC = Codec.INT.listOf().comapFlatMap(
                list -> list.size() == 2 ? DataResult.success(new Point(list.get(0), list.get(1))) : DataResult.error(() -> "A point is [x, y]"),
                point -> List.of(point.x, point.y));
    }

    public record Area(int x, int y, int w, int h) {
        public static final Codec<Area> CODEC = Codec.INT.listOf().comapFlatMap(
                list -> list.size() == 4 ? DataResult.success(new Area(list.get(0), list.get(1), list.get(2), list.get(3))) : DataResult.error(() -> "An area is [x, y, w, h]"),
                area -> List.of(area.x, area.y, area.w, area.h));
    }

    public static final Codec<BookLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MapForm.CODEC.fieldOf("form").forGetter(BookLayout::form),
            Codec.STRING.fieldOf("hash").forGetter(BookLayout::hash),
            Codec.unboundedMap(Codec.STRING, Point.CODEC).optionalFieldOf("nodes", Map.of()).forGetter(BookLayout::nodes),
            Codec.unboundedMap(Codec.STRING, Area.CODEC).optionalFieldOf("areas", Map.of()).forGetter(BookLayout::areas)
    ).apply(instance, BookLayout::new));

    public BookLayout {
        nodes = Map.copyOf(nodes);
        areas = Map.copyOf(areas);
    }

    /** Whether it was laid out for this very book. */
    public boolean fits(QuestBook book) {
        return hash.equals(book.layoutHash());
    }

    public static String epochArea(int epoch) {
        return EPOCH_AREA + epoch;
    }
}
