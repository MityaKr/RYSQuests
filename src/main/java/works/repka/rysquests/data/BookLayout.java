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
 * @param decor what the canvas draws besides nodes: the shapes of chapters, orbits, epoch marks and the trunk's turns
 */
public record BookLayout(MapForm form, String hash, Map<String, Point> nodes, Map<String, Area> areas, Decor decor) {
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

    /**
     * The canvas besides its nodes, in canvas units with a tenth of a unit kept. Every list is flat numbers:
     *
     * @param regions chapter id to its shape: {@code [0, x, y, w, h]} a rectangle, {@code [1, width, x0, y0, x1, y1, ...]} a band
     *                of that width along a line, {@code [2, cx, cy, r]} a disc
     * @param labels chapter id to where its number stands and how wide its label may be: {@code [x, y, maxWidth]}
     * @param wheels the orbits around main quests: {@code [cx, cy, chapterIndex, r1, r2, ...]}
     * @param marks epoch boundaries: {@code [epoch, x1, y1, x2, y2, words, side]}, words 1 when the label is spelt out, side the
     *              way it reads (1 right, -1 left, 2 below, -2 above)
     * @param arms labels of the galaxy's sections: {@code [epoch, x, y, outX, outY]}, epoch 0 for chapters without one
     * @param turns the trunk between rows of the serpentine: {@code [x0, y0, x1, y1, ...]}
     */
    public record Decor(Map<String, List<Float>> regions, Map<String, List<Float>> labels, List<List<Float>> wheels, List<List<Float>> marks,
            List<List<Float>> arms, List<List<Float>> turns) {
        public static final Decor EMPTY = new Decor(Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of());
        private static final Codec<List<Float>> NUMBERS = Codec.FLOAT.listOf();
        public static final Codec<Decor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, NUMBERS).optionalFieldOf("regions", Map.of()).forGetter(Decor::regions),
                Codec.unboundedMap(Codec.STRING, NUMBERS).optionalFieldOf("labels", Map.of()).forGetter(Decor::labels),
                NUMBERS.listOf().optionalFieldOf("wheels", List.of()).forGetter(Decor::wheels),
                NUMBERS.listOf().optionalFieldOf("marks", List.of()).forGetter(Decor::marks),
                NUMBERS.listOf().optionalFieldOf("arms", List.of()).forGetter(Decor::arms),
                NUMBERS.listOf().optionalFieldOf("turns", List.of()).forGetter(Decor::turns)
        ).apply(instance, Decor::new));

        public Decor {
            regions = Map.copyOf(regions);
            labels = Map.copyOf(labels);
            wheels = List.copyOf(wheels);
            marks = List.copyOf(marks);
            arms = List.copyOf(arms);
            turns = List.copyOf(turns);
        }

        public boolean isEmpty() {
            return regions.isEmpty() && wheels.isEmpty();
        }
    }

    public static final Codec<BookLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MapForm.CODEC.fieldOf("form").forGetter(BookLayout::form),
            Codec.STRING.fieldOf("hash").forGetter(BookLayout::hash),
            Codec.unboundedMap(Codec.STRING, Point.CODEC).optionalFieldOf("nodes", Map.of()).forGetter(BookLayout::nodes),
            Codec.unboundedMap(Codec.STRING, Area.CODEC).optionalFieldOf("areas", Map.of()).forGetter(BookLayout::areas),
            Decor.CODEC.optionalFieldOf("decor", Decor.EMPTY).forGetter(BookLayout::decor)
    ).apply(instance, BookLayout::new));

    public BookLayout {
        nodes = Map.copyOf(nodes);
        areas = Map.copyOf(areas);
    }

    /** A layout of nodes and areas only, as layouts were before the decor. */
    public BookLayout(MapForm form, String hash, Map<String, Point> nodes, Map<String, Area> areas) {
        this(form, hash, nodes, areas, Decor.EMPTY);
    }

    /** Whether it was laid out for this very book. */
    public boolean fits(QuestBook book) {
        return hash.equals(book.layoutHash());
    }

    public static String epochArea(int epoch) {
        return EPOCH_AREA + epoch;
    }
}
