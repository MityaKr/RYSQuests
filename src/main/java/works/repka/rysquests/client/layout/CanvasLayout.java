package works.repka.rysquests.client.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import works.repka.rysquests.data.BookLayout;
import works.repka.rysquests.data.MapForm;

/**
 * The whole canvas of a book in one form: where every quest stands, the shapes of chapters, the orbits of wheels, the marks and
 * labels of epochs and the trunk's turns. Canvas units: an orbit step is 9 to 11 of them. Node positions are whole units and
 * everything else keeps a tenth, so that a layout baked into {@code layout.json} and read back is the very same one.
 *
 * <p>Indices of nodes and chapters are those of the {@link MapBook} it was laid out from.
 */
public final class CanvasLayout {
    /** Node kind bits. */
    public static final byte HUB = 1;
    public static final byte MILESTONE = 2;
    /** The book's last quest, in the middle of the spiral. */
    public static final byte FINALE = 4;

    public enum RegionKind {
        RECT, BAND, DISC
    }

    /**
     * The shape of a chapter. {@code RECT}: {@code pts = [x, y, w, h]}. {@code BAND}: a band {@code width} wide along the line
     * {@code pts = [x0, y0, x1, y1, ...]}. {@code DISC}: {@code pts = [cx, cy, r]}.
     */
    public record Region(RegionKind kind, float[] pts, float width, float minX, float minY, float maxX, float maxY) {
        static Region rect(float x, float y, float w, float h) {
            return new Region(RegionKind.RECT, new float[] {x, y, w, h}, 0, x, y, x + w, y + h);
        }

        static Region disc(float cx, float cy, float r) {
            return new Region(RegionKind.DISC, new float[] {cx, cy, r}, 0, cx - r, cy - r, cx + r, cy + r);
        }

        static Region band(float[] pts, float width) {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (int i = 0; i + 1 < pts.length; i += 2) {
                minX = Math.min(minX, pts[i]);
                maxX = Math.max(maxX, pts[i]);
                minY = Math.min(minY, pts[i + 1]);
                maxY = Math.max(maxY, pts[i + 1]);
            }
            float h = width / 2;
            return new Region(RegionKind.BAND, pts, width, minX - h, minY - h, maxX + h, maxY + h);
        }

        public float centerX() {
            return kind == RegionKind.DISC ? pts[0] : (minX + maxX) / 2;
        }

        public float centerY() {
            return kind == RegionKind.DISC ? pts[1] : (minY + maxY) / 2;
        }

        /** Whether the point lies in the shape. */
        public boolean contains(float x, float y) {
            if (x < minX || x > maxX || y < minY || y > maxY) {
                return false;
            }
            return switch (kind) {
                case RECT -> true;
                case DISC -> {
                    float dx = x - pts[0];
                    float dy = y - pts[1];
                    yield dx * dx + dy * dy <= pts[2] * pts[2];
                }
                case BAND -> inBand(x, y);
            };
        }

        /**
         * Whether the point lies in the band as it is drawn: within half its width of the centre line and not past its ends, which
         * are cut square. The rounded ends a plain distance gives reach half a band's width into the next chapter along the same
         * track: on the spiral the pointer over one chapter lit its neighbour.
         */
        private boolean inBand(float x, float y) {
            int n = pts.length / 2;
            if (n < 2) {
                return n == 1 && Math.hypot(x - pts[0], y - pts[1]) <= width / 2;
            }
            float half = width / 2;
            for (int i = 0; i + 1 < n; i++) {
                float ax = pts[2 * i];
                float ay = pts[2 * i + 1];
                float dx = pts[2 * i + 2] - ax;
                float dy = pts[2 * i + 3] - ay;
                float l2 = dx * dx + dy * dy;
                float t = l2 == 0 ? 0 : ((x - ax) * dx + (y - ay) * dy) / l2;
                // past the first point of the line or the last one: outside the square end
                if (i == 0 && t < 0 || i == n - 2 && t > 1) {
                    continue;
                }
                t = Math.max(0, Math.min(1, t));
                if (Math.hypot(x - (ax + t * dx), y - (ay + t * dy)) <= half) {
                    return true;
                }
            }
            return false;
        }

        /** Distance from the point to the band's centre line. */
        public float distanceToLine(float x, float y) {
            float best = Float.MAX_VALUE;
            for (int i = 0; i + 3 < pts.length; i += 2) {
                best = Math.min(best, segmentDistance(x, y, pts[i], pts[i + 1], pts[i + 2], pts[i + 3]));
            }
            if (pts.length == 2) {
                best = (float) Math.hypot(x - pts[0], y - pts[1]);
            }
            return best;
        }

        /** The outline as a closed polygon {@code [x0, y0, x1, y1, ...]}, for checks. */
        public float[] polygon() {
            return switch (kind) {
                case RECT -> new float[] {pts[0], pts[1], pts[0] + pts[2], pts[1], pts[0] + pts[2], pts[1] + pts[3], pts[0], pts[1] + pts[3]};
                case DISC -> {
                    float[] out = new float[96];
                    for (int i = 0; i < 48; i++) {
                        double a = 2 * Math.PI * i / 48;
                        out[2 * i] = (float) (pts[0] + pts[2] * Math.cos(a));
                        out[2 * i + 1] = (float) (pts[1] + pts[2] * Math.sin(a));
                    }
                    yield out;
                }
                case BAND -> {
                    int n = pts.length / 2;
                    float[] out = new float[n * 4];
                    for (int i = 0; i < n; i++) {
                        int a = Math.max(0, i - 1);
                        int b = Math.min(n - 1, i + 1);
                        float dx = pts[2 * b] - pts[2 * a];
                        float dy = pts[2 * b + 1] - pts[2 * a + 1];
                        float len = (float) Math.hypot(dx, dy);
                        float nx = len == 0 ? 0 : -dy / len * width / 2;
                        float ny = len == 0 ? 0 : dx / len * width / 2;
                        out[2 * i] = pts[2 * i] + nx;
                        out[2 * i + 1] = pts[2 * i + 1] + ny;
                        out[(2 * n - 1 - i) * 2] = pts[2 * i] - nx;
                        out[(2 * n - 1 - i) * 2 + 1] = pts[2 * i + 1] - ny;
                    }
                    yield out;
                }
            };
        }
    }

    /** Where a chapter's number stands, and how wide its label may grow there without leaving the chapter. */
    public record Label(float x, float y, float maxWidth) {
    }

    /** One wheel's orbits around {@code (x, y)}. */
    public record Ring(float x, float y, int chapter, float[] orbits) {
        public float outer() {
            return orbits.length == 0 ? 0 : orbits[orbits.length - 1];
        }
    }

    /**
     * The boundary where an epoch begins, from {@code (x1, y1)} on the outer or upper side to {@code (x2, y2)}. Its label stands at
     * {@code (x1, y1)} and reads to the {@code side}: 1 right, -1 left, 2 centred below, -2 centred above. {@code words} when the
     * epoch's name is spelt out, else its numeral alone (inner turns of the spiral).
     */
    public record Mark(int epoch, float x1, float y1, float x2, float y2, boolean words, int side) {
    }

    /** A galaxy's section label near the outer end of its arm; {@code (outX, outY)} points away from the core. */
    public record Arm(int epoch, float x, float y, float outX, float outY) {
    }

    public final MapForm form;
    public final String hash;
    /** Node centres by quest index. */
    public final float[] x;
    public final float[] y;
    /** {@link #HUB} and {@link #MILESTONE} bits by quest index. */
    public final byte[] kind;
    public final Region[] regions;
    public final Label[] labels;
    public final List<Ring> rings;
    public final List<Mark> marks;
    public final List<Arm> arms;
    public final List<float[]> turns;
    public final float minX;
    public final float minY;
    public final float maxX;
    public final float maxY;

    CanvasLayout(MapForm form, String hash, float[] x, float[] y, byte[] kind, Region[] regions, Label[] labels, List<Ring> rings, List<Mark> marks,
            List<Arm> arms, List<float[]> turns) {
        this.form = form;
        this.hash = hash;
        this.x = x;
        this.y = y;
        this.kind = kind;
        this.regions = regions;
        this.labels = labels;
        this.rings = List.copyOf(rings);
        this.marks = List.copyOf(marks);
        this.arms = List.copyOf(arms);
        this.turns = List.copyOf(turns);
        float x0 = Float.MAX_VALUE;
        float y0 = Float.MAX_VALUE;
        float x1 = -Float.MAX_VALUE;
        float y1 = -Float.MAX_VALUE;
        for (Region region : regions) {
            if (region == null) {
                continue;
            }
            x0 = Math.min(x0, region.minX);
            y0 = Math.min(y0, region.minY);
            x1 = Math.max(x1, region.maxX);
            y1 = Math.max(y1, region.maxY);
        }
        for (int i = 0; i < x.length; i++) {
            x0 = Math.min(x0, x[i] - 12);
            y0 = Math.min(y0, y[i] - 12);
            x1 = Math.max(x1, x[i] + 12);
            y1 = Math.max(y1, y[i] + 12);
        }
        if (x0 > x1) {
            x0 = y0 = 0;
            x1 = y1 = 1;
        }
        this.minX = x0;
        this.minY = y0;
        this.maxX = x1;
        this.maxY = y1;
    }

    public boolean hub(int quest) {
        return (kind[quest] & HUB) != 0;
    }

    public boolean milestone(int quest) {
        return (kind[quest] & MILESTONE) != 0;
    }

    public boolean finale(int quest) {
        return (kind[quest] & FINALE) != 0;
    }

    /**
     * The book's finale: of the main quests of the last chapter on the map, one no main quest depends on, a milestone before others,
     * then the end of the longest chain; -1 when the last chapter has no main quest. The spiral puts it in its middle.
     */
    public static int finale(MapBook book) {
        if (book.chapters().isEmpty()) {
            return -1;
        }
        MapBook.Chap last = book.chapters().getLast();
        Map<Integer, Integer> depth = new HashMap<>();
        int best = -1;
        for (int q : last.quests()) {
            MapBook.Q quest = book.quests().get(q);
            if (quest.optional()) {
                continue;
            }
            boolean sink = true;
            for (int d : book.dependents(q)) {
                if (!book.quests().get(d).optional()) {
                    sink = false;
                    break;
                }
            }
            if (!sink) {
                continue;
            }
            if (best < 0 || better(book, q, best, depth)) {
                best = q;
            }
        }
        return best;
    }

    private static boolean better(MapBook book, int a, int b, Map<Integer, Integer> depth) {
        MapBook.Q qa = book.quests().get(a);
        MapBook.Q qb = book.quests().get(b);
        if (qa.milestone() != qb.milestone()) {
            return qa.milestone();
        }
        int da = depth(book, a, depth, new java.util.HashSet<>());
        int db = depth(book, b, depth, new java.util.HashSet<>());
        return da != db ? da > db : qa.id().compareTo(qb.id()) > 0;
    }

    /** The longest chain of requirements up from the quest, inside its chapter. */
    private static int depth(MapBook book, int q, Map<Integer, Integer> memo, java.util.Set<Integer> stack) {
        Integer known = memo.get(q);
        if (known != null) {
            return known;
        }
        stack.add(q);
        int best = 0;
        int chapter = book.quests().get(q).chapter();
        for (int r : book.quests().get(q).requires()) {
            if (book.quests().get(r).chapter() == chapter && !stack.contains(r)) {
                best = Math.max(best, depth(book, r, memo, stack) + 1);
            }
        }
        stack.remove(q);
        memo.put(q, best);
        return best;
    }

    /** The box of the chapters of an epoch: {@code [minX, minY, maxX, maxY]}, or null when no chapter has it. */
    public float[] epochBounds(MapBook book, int epoch) {
        float[] box = null;
        for (MapBook.Chap chapter : book.chapters()) {
            Region region = regions[chapter.index()];
            if (chapter.epoch() != epoch || region == null) {
                continue;
            }
            if (box == null) {
                box = new float[] {region.minX, region.minY, region.maxX, region.maxY};
            } else {
                box[0] = Math.min(box[0], region.minX);
                box[1] = Math.min(box[1], region.minY);
                box[2] = Math.max(box[2], region.maxX);
                box[3] = Math.max(box[3], region.maxY);
            }
        }
        return box;
    }

    // --- layout.json ---

    /** The layout as {@code layout.json} keeps it. */
    public BookLayout toBaked(MapBook book) {
        Map<String, BookLayout.Point> nodes = new LinkedHashMap<>();
        for (MapBook.Q quest : book.quests()) {
            nodes.put(quest.id().toString(), new BookLayout.Point(Math.round(x[quest.index()]), Math.round(y[quest.index()])));
        }
        Map<String, BookLayout.Area> areas = new LinkedHashMap<>();
        Map<String, List<Float>> regionMap = new LinkedHashMap<>();
        Map<String, List<Float>> labelMap = new LinkedHashMap<>();
        for (MapBook.Chap chapter : book.chapters()) {
            Region region = regions[chapter.index()];
            String id = chapter.id().toString();
            areas.put(id, area(region.minX, region.minY, region.maxX, region.maxY));
            List<Float> shape = new ArrayList<>();
            shape.add((float) region.kind.ordinal());
            if (region.kind == RegionKind.BAND) {
                shape.add(tenth(region.width));
            }
            for (float v : region.pts) {
                shape.add(tenth(v));
            }
            regionMap.put(id, shape);
            Label label = labels[chapter.index()];
            labelMap.put(id, List.of(tenth(label.x), tenth(label.y), tenth(label.maxWidth)));
        }
        for (int epoch : book.epochNumbers()) {
            float[] box = epochBounds(book, epoch);
            if (box != null) {
                areas.put(BookLayout.epochArea(epoch), area(box[0], box[1], box[2], box[3]));
            }
        }
        List<List<Float>> wheelRows = new ArrayList<>();
        for (Ring ring : rings) {
            List<Float> row = new ArrayList<>(List.of(tenth(ring.x), tenth(ring.y), (float) ring.chapter));
            for (float o : ring.orbits) {
                row.add(tenth(o));
            }
            wheelRows.add(row);
        }
        List<List<Float>> markRows = new ArrayList<>();
        for (Mark mark : marks) {
            markRows.add(List.of((float) mark.epoch, tenth(mark.x1), tenth(mark.y1), tenth(mark.x2), tenth(mark.y2), mark.words ? 1f : 0f, (float) mark.side));
        }
        List<List<Float>> armRows = new ArrayList<>();
        for (Arm arm : arms) {
            armRows.add(List.of((float) arm.epoch, tenth(arm.x), tenth(arm.y), tenth(arm.outX), tenth(arm.outY)));
        }
        List<List<Float>> turnRows = new ArrayList<>();
        for (float[] turn : turns) {
            List<Float> row = new ArrayList<>();
            for (float v : turn) {
                row.add(tenth(v));
            }
            turnRows.add(row);
        }
        return new BookLayout(form, hash, nodes, areas, new BookLayout.Decor(regionMap, labelMap, wheelRows, markRows, armRows, turnRows));
    }

    /**
     * The canvas from {@code layout.json}, or null when the file does not hold all of it: a node or a chapter shape missing, or a
     * file from before the decor. Which quests are main quests is not kept in the file; it is told from the book again, cheaply.
     */
    public static CanvasLayout fromBaked(MapBook book, BookLayout baked) {
        if (baked.decor().isEmpty() && !book.quests().isEmpty()) {
            return null;
        }
        int n = book.quests().size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (MapBook.Q quest : book.quests()) {
            BookLayout.Point point = baked.nodes().get(quest.id().toString());
            if (point == null) {
                return null;
            }
            xs[quest.index()] = point.x();
            ys[quest.index()] = point.y();
        }
        Region[] regions = new Region[book.chapters().size()];
        Label[] labels = new Label[book.chapters().size()];
        for (MapBook.Chap chapter : book.chapters()) {
            List<Float> shape = baked.decor().regions().get(chapter.id().toString());
            List<Float> label = baked.decor().labels().get(chapter.id().toString());
            if (shape == null || shape.isEmpty() || label == null || label.size() < 3) {
                return null;
            }
            regions[chapter.index()] = region(shape);
            if (regions[chapter.index()] == null) {
                return null;
            }
            labels[chapter.index()] = new Label(label.get(0), label.get(1), label.get(2));
        }
        List<Ring> rings = new ArrayList<>();
        for (List<Float> row : baked.decor().wheels()) {
            if (row.size() < 3) {
                continue;
            }
            float[] orbits = new float[row.size() - 3];
            for (int i = 0; i < orbits.length; i++) {
                orbits[i] = row.get(3 + i);
            }
            rings.add(new Ring(row.get(0), row.get(1), Math.round(row.get(2)), orbits));
        }
        List<Mark> marks = new ArrayList<>();
        for (List<Float> row : baked.decor().marks()) {
            if (row.size() >= 7) {
                marks.add(new Mark(Math.round(row.get(0)), row.get(1), row.get(2), row.get(3), row.get(4), row.get(5) > 0.5f, Math.round(row.get(6))));
            }
        }
        List<Arm> arms = new ArrayList<>();
        for (List<Float> row : baked.decor().arms()) {
            if (row.size() >= 5) {
                arms.add(new Arm(Math.round(row.get(0)), row.get(1), row.get(2), row.get(3), row.get(4)));
            }
        }
        List<float[]> turns = new ArrayList<>();
        for (List<Float> row : baked.decor().turns()) {
            float[] pts = new float[row.size()];
            for (int i = 0; i < pts.length; i++) {
                pts[i] = row.get(i);
            }
            turns.add(pts);
        }
        return new CanvasLayout(baked.form(), baked.hash(), xs, ys, kinds(book, baked.form()), regions, labels, rings, marks, arms, turns);
    }

    private static Region region(List<Float> shape) {
        int kind = Math.round(shape.get(0));
        float[] rest;
        switch (kind) {
            case 0 -> {
                if (shape.size() < 5) {
                    return null;
                }
                return Region.rect(shape.get(1), shape.get(2), shape.get(3), shape.get(4));
            }
            case 1 -> {
                if (shape.size() < 4) {
                    return null;
                }
                rest = new float[shape.size() - 2];
                for (int i = 0; i < rest.length; i++) {
                    rest[i] = shape.get(2 + i);
                }
                return Region.band(rest, shape.get(1));
            }
            case 2 -> {
                if (shape.size() < 4) {
                    return null;
                }
                return Region.disc(shape.get(1), shape.get(2), shape.get(3));
            }
            default -> {
                return null;
            }
        }
    }

    /** Main quests and milestones, told from the book as the wheels tell them. */
    static byte[] kinds(MapBook book, MapForm form) {
        byte[] out = new byte[book.quests().size()];
        Wheels.Style style = Wheels.style(book);
        for (MapBook.Chap chapter : book.chapters()) {
            for (Wheels.Wheel wheel : Wheels.of(book, chapter, style)) {
                if (wheel.hub >= 0) {
                    out[wheel.hub] |= HUB;
                }
            }
        }
        for (MapBook.Q quest : book.quests()) {
            if (quest.milestone()) {
                out[quest.index()] |= MILESTONE;
            }
        }
        int finale = form == MapForm.SPIRAL ? finale(book) : -1;
        if (finale >= 0) {
            out[finale] |= FINALE;
        }
        return out;
    }

    static float tenth(float v) {
        return Math.round(v * 10f) / 10f;
    }

    private static BookLayout.Area area(float x0, float y0, float x1, float y1) {
        int ix = (int) Math.floor(x0);
        int iy = (int) Math.floor(y0);
        return new BookLayout.Area(ix, iy, (int) Math.ceil(x1) - ix, (int) Math.ceil(y1) - iy);
    }

    static float segmentDistance(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float l2 = dx * dx + dy * dy;
        float t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2));
        return (float) Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    /** Whether two layouts are the same canvas. */
    public boolean sameAs(CanvasLayout other) {
        if (other == null || form != other.form || !hash.equals(other.hash) || !java.util.Arrays.equals(x, other.x) || !java.util.Arrays.equals(y, other.y)
                || !java.util.Arrays.equals(kind, other.kind) || regions.length != other.regions.length || rings.size() != other.rings.size()
                || marks.size() != other.marks.size() || arms.size() != other.arms.size() || turns.size() != other.turns.size()) {
            return false;
        }
        for (int i = 0; i < regions.length; i++) {
            if (regions[i].kind != other.regions[i].kind || !java.util.Arrays.equals(regions[i].pts, other.regions[i].pts) || regions[i].width != other.regions[i].width
                    || !labels[i].equals(other.labels[i])) {
                return false;
            }
        }
        for (int i = 0; i < rings.size(); i++) {
            Ring a = rings.get(i);
            Ring b = other.rings.get(i);
            if (a.x != b.x || a.y != b.y || a.chapter != b.chapter || !java.util.Arrays.equals(a.orbits, b.orbits)) {
                return false;
            }
        }
        return marks.equals(other.marks) && arms.equals(other.arms) && turns.stream().map(java.util.Arrays::toString).toList()
                .equals(other.turns.stream().map(java.util.Arrays::toString).toList());
    }

    /** Debug and test counters. */
    public Map<String, Integer> counts() {
        Map<String, Integer> out = new HashMap<>();
        out.put("nodes", x.length);
        out.put("chapters", regions.length);
        out.put("wheels", rings.size());
        out.put("marks", marks.size());
        return out;
    }
}
