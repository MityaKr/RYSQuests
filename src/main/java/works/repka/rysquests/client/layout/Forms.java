package works.repka.rysquests.client.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import works.repka.rysquests.data.MapForm;

/**
 * The four forms of the canvas, ported from the approved Python layouts:
 * <ul>
 *     <li>serpentine: the story as one band laid in rows that turn, cut at epoch boundaries ({@code quests-layouts-fable}, auth);</li>
 *     <li>spiral: the same band wound inwards, the last chapter in the middle ({@code quests-layouts-fable}, spir);</li>
 *     <li>generator: every chapter its own disc, the discs in book order on an oval spiral ({@code book2/final/src/atm_forms.py});</li>
 *     <li>galaxy: the main chapters as a spiral core and every section of the book an arm ({@code quests-hub-fable/hub.py}).</li>
 * </ul>
 * Each form only places whole wheels; the wheels themselves come from {@link Wheels} through {@link Parts}, which may keep them
 * from an earlier laying out of the same chapter.
 */
final class Forms {
    /** Reports how far a form is, from 0 to 1. */
    interface Progress {
        Progress NONE = fraction -> {
        };

        void at(double fraction);
    }

    /** A chapter packed on its own, relative to its start or centre, by wheel in the order of {@link Parts#wheels}. */
    record Local(float[] x, float[] y, double extent) {
    }

    /** Per chapter pieces the forms are built from. */
    interface Parts {
        Wheels.Style style();

        /** The chapter's wheels, fresh ones each call: forms move them. */
        List<Wheels.Wheel> wheels(MapBook.Chap chapter);

        /** The chapter packed along a straight band of width {@code band} from {@code s = 0}; extent is its length. */
        Local line(MapBook.Chap chapter, double band);

        /** The chapter packed on its own small spiral and centred; extent is its radius. */
        Local disc(MapBook.Chap chapter);
    }

    static final double SERPENTINE_ASPECT = 1.38;
    /** A chapter with no quests yet still gets a chapter's shape, wide enough for its label: a book from nothing starts so. */
    static final double EMPTY_LENGTH = 48;

    /** The least length of a chapter along its track: an empty one is not a sliver. */
    private static double least(MapBook book, int chapter, double least) {
        return book.chapters().get(chapter).quests().length == 0 ? Math.max(least, EMPTY_LENGTH) : least;
    }

    private Forms() {
    }

    static CanvasLayout lay(MapBook book, MapForm form, Parts parts, Progress progress) {
        if (book.chapters().isEmpty()) {
            return new Draft(book, parts).finish(form);
        }
        return switch (form) {
            case SPIRAL -> spiral(book, parts, progress);
            case GENERATOR -> generator(book, parts, progress);
            case GALAXY -> galaxy(book, parts, progress);
            default -> serpentine(book, parts, progress);
        };
    }

    // ================= serpentine =================

    private static CanvasLayout serpentine(MapBook book, Parts parts, Progress progress) {
        final double rowGap = 30;
        final double chGap = 16;
        final double epGap = 34;
        final double margin = 12;
        Draft draft = new Draft(book, parts);
        List<int[]> units = units(book);
        double[] gaps = gaps(book, units, epGap);
        int nCh = book.chapters().size();
        double bandMin = Math.max(160, 2 * draft.maxR() + 2 * margin + 8);
        double bandMax = Math.max(bandMin, 400);
        double bandStep = book.quests().size() > 1500 ? 40 : 10;
        List<Double> bands = new ArrayList<>();
        for (double band = bandMin; band <= bandMax + 0.01; band += bandStep) {
            bands.add(band);
        }
        double bestScore = Double.MAX_VALUE;
        double bestBand = bandMin;
        int[] bestCuts = null;
        double bestW = 0;
        int step = 0;
        for (double band : bands) {
            double[] len = new double[nCh];
            for (MapBook.Chap chapter : book.chapters()) {
                len[chapter.index()] = Math.max(parts.line(chapter, band - 2 * margin).extent(), least(book, chapter.index(), 0));
            }
            double[] unitLen = new double[units.size()];
            for (int u = 0; u < units.size(); u++) {
                for (int c : units.get(u)) {
                    unitLen[u] += len[c] + chGap;
                }
            }
            for (int rows = 1; rows <= Math.min(8, units.size()); rows++) {
                int[] cuts = partition(unitLen, rows, gaps);
                double w = maxRow(unitLen, cuts, gaps);
                double h = rows * band + (rows - 1) * rowGap;
                double score = Math.abs(w / h - SERPENTINE_ASPECT);
                if (score < bestScore - 1e-9) {
                    bestScore = score;
                    bestBand = band;
                    bestCuts = cuts;
                    bestW = w;
                }
            }
            progress.at(0.9 * ++step / bands.size());
        }
        double band = bestBand;
        double w = bestW;
        int[] cuts = bestCuts;
        double[] len = new double[nCh];
        Local[] local = new Local[nCh];
        for (MapBook.Chap chapter : book.chapters()) {
            local[chapter.index()] = parts.line(chapter, band - 2 * margin);
            len[chapter.index()] = Math.max(local[chapter.index()].extent(), least(book, chapter.index(), 0));
        }
        double[] unitLen = new double[units.size()];
        for (int u = 0; u < units.size(); u++) {
            for (int c : units.get(u)) {
                unitLen[u] += len[c] + chGap;
            }
        }
        int[] rowOf = new int[nCh];
        for (int row = 0, u = 0; row < cuts.length - 1; row++) {
            double yc = band / 2 + row * (band + rowGap);
            double total = rowLen(unitLen, cuts[row], cuts[row + 1], gaps) - chGap;
            double s = (w - total) / 2;
            int prevEpoch = Integer.MIN_VALUE;
            boolean mirror = row % 2 == 1;
            for (u = cuts[row]; u < cuts[row + 1]; u++) {
                for (int c : units.get(u)) {
                    MapBook.Chap chapter = book.chapters().get(c);
                    int e = chapter.epoch();
                    if (u > cuts[row] && c == units.get(u)[0]) {
                        s += gaps[u];
                    }
                    if (e != prevEpoch && e > 0) {
                        // in the middle of the gap: the chapter before ended a chapter's gap and the epoch's gap ago
                        double gapBefore = c == units.get(u)[0] && u > cuts[row] ? chGap + gaps[u] : chGap;
                        double mx = s - (prevEpoch != Integer.MIN_VALUE ? gapBefore / 2 : 10);
                        double x1 = mirror ? w - mx : mx;
                        draft.marks.add(new CanvasLayout.Mark(e, (float) x1, (float) (yc - band / 2 - 2), (float) x1, (float) (yc + band / 2 + 2), true, mirror ? -1 : 1));
                    }
                    prevEpoch = e;
                    List<Wheels.Wheel> wheels = draft.wheels.get(c);
                    Local own = local[c];
                    for (int k = 0; k < wheels.size(); k++) {
                        double wx = s + own.x()[k];
                        wheels.get(k).at((float) (mirror ? w - wx : wx), (float) (yc + own.y()[k]));
                    }
                    double end = s + len[c];
                    double x0 = s - 5;
                    double x1 = end + 5;
                    if (mirror) {
                        double t = x0;
                        x0 = w - x1;
                        x1 = w - t;
                    }
                    draft.regions[c] = CanvasLayout.Region.rect((float) x0, (float) (yc - band / 2), (float) (x1 - x0), (float) band);
                    draft.labels[c] = new CanvasLayout.Label((float) ((x0 + x1) / 2), (float) (yc - band / 2), (float) (x1 - x0 - 8));
                    rowOf[c] = row;
                    s = end + chGap;
                }
            }
        }
        // the trunk turns between rows: from the end of a row round to the start of the next
        for (int c = 0; c + 1 < nCh; c++) {
            if (rowOf[c] == rowOf[c + 1]) {
                continue;
            }
            CanvasLayout.Region a = draft.regions[c];
            CanvasLayout.Region b = draft.regions[c + 1];
            boolean right = rowOf[c] % 2 == 0;
            double xa = right ? a.maxX() : a.minX();
            double xb = right ? b.maxX() : b.minX();
            double far = right ? Math.max(xa, xb) + 30 : Math.min(xa, xb) - 30;
            double ya = (a.minY() + a.maxY()) / 2;
            double yb = (b.minY() + b.maxY()) / 2;
            float[] pts = new float[2 * 21];
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                double u1 = 1 - t;
                double px = u1 * u1 * u1 * xa + 3 * u1 * u1 * t * far + 3 * u1 * t * t * far + t * t * t * xb;
                double py = u1 * u1 * u1 * ya + 3 * u1 * u1 * t * ya + 3 * u1 * t * t * yb + t * t * t * yb;
                pts[2 * i] = (float) px;
                pts[2 * i + 1] = (float) py;
            }
            draft.turns.add(pts);
        }
        progress.at(1);
        return draft.finish(MapForm.SERPENTINE);
    }

    /** Runs of chapters with one epoch, in book order; chapters without an epoch stand alone. */
    static List<int[]> units(MapBook book) {
        List<int[]> out = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        int epoch = Integer.MIN_VALUE;
        for (MapBook.Chap chapter : book.chapters()) {
            if (!current.isEmpty() && (chapter.epoch() != epoch || chapter.epoch() == 0)) {
                out.add(current.stream().mapToInt(Integer::intValue).toArray());
                current.clear();
            }
            current.add(chapter.index());
            epoch = chapter.epoch();
        }
        if (!current.isEmpty()) {
            out.add(current.stream().mapToInt(Integer::intValue).toArray());
        }
        return out;
    }

    /** The gap before each run: between two epochs wider than between chapters. */
    private static double[] gaps(MapBook book, List<int[]> units, double epGap) {
        double[] out = new double[units.size()];
        for (int u = 1; u < units.size(); u++) {
            int before = book.chapters().get(units.get(u - 1)[0]).epoch();
            int now = book.chapters().get(units.get(u)[0]).epoch();
            out[u] = before > 0 && now > 0 && before != now ? epGap : 0;
        }
        return out;
    }

    private static double rowLen(double[] unitLen, int from, int to, double[] gap) {
        double sum = 0;
        for (int u = from; u < to; u++) {
            sum += unitLen[u];
            if (u > from) {
                sum += gap[u];
            }
        }
        return sum;
    }

    private static double maxRow(double[] unitLen, int[] cuts, double[] gap) {
        double max = 0;
        for (int r = 0; r + 1 < cuts.length; r++) {
            max = Math.max(max, rowLen(unitLen, cuts[r], cuts[r + 1], gap));
        }
        return max;
    }

    /** Cuts the units into {@code rows} runs with the longest run as short as it can be: {@code [0, c1, ..., n]}. */
    static int[] partition(double[] unitLen, int rows, double[] gap) {
        int n = unitLen.length;
        double[][] best = new double[rows + 1][n + 1];
        int[][] from = new int[rows + 1][n + 1];
        for (double[] line : best) {
            java.util.Arrays.fill(line, Double.MAX_VALUE);
        }
        best[0][0] = 0;
        for (int r = 1; r <= rows; r++) {
            for (int j = r; j <= n; j++) {
                for (int i = r - 1; i < j; i++) {
                    if (best[r - 1][i] == Double.MAX_VALUE) {
                        continue;
                    }
                    double cost = Math.max(best[r - 1][i], rowLen(unitLen, i, j, gap));
                    if (cost < best[r][j] - 1e-9) {
                        best[r][j] = cost;
                        from[r][j] = i;
                    }
                }
            }
        }
        int[] cuts = new int[rows + 1];
        cuts[rows] = n;
        for (int r = rows; r > 0; r--) {
            cuts[r - 1] = from[r][cuts[r]];
        }
        return cuts;
    }

    // ================= spiral =================

    /** Between two turns of the spiral. */
    private static final double SPIRAL_GAP = 22;
    /** Between two chapters along the spiral. */
    private static final double SPIRAL_STEP = 14;

    /**
     * The story wound inwards. The chapters of the last epoch wind on in a tighter band, as one with the rest (plan, picture 41),
     * down to the book's finale in the very middle; the pitch grows over the turn before the wider band, so the turns run smooth and
     * never touch. A book whose last chapter has no epoch winds that chapter alone to the middle.
     */
    private static CanvasLayout spiral(MapBook book, Parts parts, Progress progress) {
        Draft draft = new Draft(book, parts);
        int nCh = book.chapters().size();
        int last = nCh - 1;
        int first = innerFirst(book);
        double band = Math.max(150, 2 * draft.maxR() + 16);
        int finale = CanvasLayout.finale(book);
        Wheels.Wheel centre = null;
        for (Wheels.Wheel wheel : draft.wheels.get(last)) {
            if (finale >= 0 && wheel.hub == finale) {
                centre = wheel;
            }
        }
        double innerR = 10;
        for (int c = first; c <= last; c++) {
            for (Wheels.Wheel wheel : draft.wheels.get(c)) {
                innerR = Math.max(innerR, wheel.r);
            }
        }
        double bandIn = Math.min(band, Math.max(84, 2 * innerR + 16));
        double pitchIn = bandIn + SPIRAL_GAP;
        double pitchOut = band + SPIRAL_GAP;
        // the track starts in the very middle: the last chapter's band curls round the finale there and unwinds into the spiral
        double r0 = 0;
        Packer.Tuning tuning = Packer.Tuning.of(parts.style());
        double[] widths = new double[nCh];
        double[][] seg = new double[nCh][];
        // the inner epoch, packed again while the ramp to the wider band follows where the epoch ends
        Track.Spiral track = new Track.Spiral(r0, pitchIn, 0);
        Packer.Grid grid = new Packer.Grid();
        double rampEnd = -1;
        double trackRamp = -1;
        double s = 0;
        for (int pass = 0; pass < 6; pass++) {
            trackRamp = rampEnd;
            track = rampEnd < 0 ? new Track.Spiral(r0, pitchIn, 0)
                    : new Track.Spiral(r0, pitchIn, 0, false, Math.max(0, rampEnd - 2 * Math.PI), rampEnd, pitchOut);
            grid = new Packer.Grid();
            if (centre != null) {
                centre.at(0, 0);
                grid.add(0, 0, centre.r);
            }
            s = 0;
            for (int c = last; c >= first; c--) {
                double s0 = c == last ? 0 : s + SPIRAL_STEP;
                List<Wheels.Wheel> wheels = new ArrayList<>(draft.wheels.get(c));
                wheels.remove(centre);
                Collections.reverse(wheels);
                double end = Packer.pack(book, wheels, track, bandIn, s0, grid, tuning);
                double to = 0;
                if (c == last && last > 0) {
                    // the curl round the middle reaches half a band out: the next chapter starts a whole turn out, clear of it
                    double block = track.lengthAt(2 * Math.PI);
                    if (end < block - 8 && !wheels.isEmpty()) {
                        // a short last chapter is spread over its whole block, wheels and trunk evenly, not left in its first half
                        end = spread(book, wheels, track, bandIn, s0, block, centre, tuning);
                        grid = new Packer.Grid();
                        if (centre != null) {
                            grid.add(0, 0, centre.r);
                        }
                        for (Wheels.Wheel wheel : wheels) {
                            grid.add(wheel.x(), wheel.y(), wheel.r);
                        }
                    }
                    to = block;
                }
                to = Math.max(to, Math.max(end, s0 + least(book, c, 8)));
                seg[c] = new double[] {s0, to};
                widths[c] = bandIn;
                s = seg[c][1];
            }
            if (first == 0 || bandIn >= band) {
                break;
            }
            double next = track.angleAt(s + SPIRAL_STEP);
            if (rampEnd >= 0 && Math.abs(next - rampEnd) < 0.01) {
                break;
            }
            rampEnd = next;
        }
        progress.at(0.3);
        // the outer chapters on the same track, from where the ramp has brought it to the wider band
        double outerFrom = s + SPIRAL_STEP;
        if (trackRamp >= 0) {
            outerFrom = Math.max(outerFrom, track.lengthAt(trackRamp));
        }
        // a short inner part leaves the wide band near the middle: it starts where its inner edge clears the curl round the middle
        if (first > 0) {
            double clear = r0 + bandIn / 2 + SPIRAL_GAP + band / 2;
            while (track.radius(track.angleAt(outerFrom)) < clear) {
                outerFrom += 4;
            }
        }
        for (int c = first - 1; c >= 0; c--) {
            double s0 = c == first - 1 ? outerFrom : s + SPIRAL_STEP;
            List<Wheels.Wheel> wheels = new ArrayList<>(draft.wheels.get(c));
            Collections.reverse(wheels);
            double end = Packer.pack(book, wheels, track, band, s0, grid, tuning);
            seg[c] = new double[] {s0, Math.max(end, s0 + least(book, c, 8))};
            widths[c] = band;
            s = seg[c][1];
            progress.at(0.3 + 0.7 * (first - c) / Math.max(1, first));
        }
        // the start of the book on the left
        double[] p = new double[2];
        track.at(seg[0][1], 0, p);
        Rotation rotation = new Rotation(Math.PI - StrictMath.atan2(p[1], p[0]));
        for (int c = 0; c <= last; c++) {
            for (Wheels.Wheel wheel : draft.wheels.get(c)) {
                rotation.apply(wheel);
            }
        }
        double startAngle = track.angleAt(seg[0][1]);
        int prevEpoch = Integer.MIN_VALUE;
        for (int c = 0; c <= last; c++) {
            double a = seg[c][0];
            double b = seg[c][1];
            double width = widths[c];
            int n = Math.max(8, (int) ((b - a) / 6));
            float[] pts = new float[2 * (n + 1)];
            for (int k = 0; k <= n; k++) {
                track.at(a + (b - a) * k / n, 0, p);
                rotation.apply(p);
                pts[2 * k] = (float) p[0];
                pts[2 * k + 1] = (float) p[1];
            }
            draft.regions[c] = CanvasLayout.Region.band(pts, (float) width);
            track.at((a + b) / 2, width / 4, p);
            rotation.apply(p);
            draft.labels[c] = new CanvasLayout.Label((float) p[0], (float) p[1], (float) (Math.min(b - a, width) * 0.85));
            int e = book.chapters().get(c).epoch();
            if (e != prevEpoch && e > 0) {
                // in the middle of the gap between this chapter's outer end and the inner end of the one before it
                double at = c > 0 ? (b + seg[c - 1][0]) / 2 : b + SPIRAL_STEP / 2;
                double half = Math.max(width, c > 0 ? widths[c - 1] : 0) / 2 + 4;
                double[] outer = new double[2];
                double[] inner = new double[2];
                track.at(at, half, outer);
                track.at(at, -half, inner);
                rotation.apply(outer);
                rotation.apply(inner);
                double sweep = Math.toDegrees(startAngle - track.angleAt(at));
                double dist = Math.max(1, Math.hypot(outer[0], outer[1]));
                int side = Math.abs(outer[1]) / dist > 0.8 ? (outer[1] > 0 ? 2 : -2) : (outer[0] > 0 ? 1 : -1);
                draft.marks.add(new CanvasLayout.Mark(e, (float) outer[0], (float) outer[1], (float) inner[0], (float) inner[1], sweep < 350, side));
            }
            prevEpoch = e;
        }
        progress.at(1);
        return draft.finish(MapForm.SPIRAL);
    }

    /**
     * Packs the wheels along a stretch of the track from {@code s0} to {@code to}, spread over all of it: on a track whose lengths
     * are drawn out until the packed wheels reach its end. @return the end of the packed stretch on the real track
     */
    private static double spread(MapBook book, List<Wheels.Wheel> wheels, Track.Spiral track, double band, double s0, double to,
            @org.jetbrains.annotations.Nullable Wheels.Wheel centre, Packer.Tuning tuning) {
        double k = 1;
        double end = s0;
        for (int round = 0; round < 6; round++) {
            Packer.Grid grid = new Packer.Grid();
            if (centre != null) {
                grid.add(0, 0, centre.r);
            }
            double stretch = k;
            Track drawn = new Track() {
                @Override
                void at(double s, double o, double[] out) {
                    track.at(s0 + (s - s0) * stretch, o, out);
                }
            };
            double packed = Packer.pack(book, wheels, drawn, band, s0, grid, tuning);
            end = s0 + (packed - s0) * stretch;
            if (Math.abs(end - to) < 12 || end > to) {
                break;
            }
            k = Math.min(8, k * (to - s0) / Math.max(1, end - s0));
        }
        return end;
    }

    /**
     * The first chapter that winds in the tighter band: the first of the last epoch when that epoch has more than one chapter and
     * is not the whole book, else the last chapter alone.
     */
    static int innerFirst(MapBook book) {
        int last = book.chapters().size() - 1;
        int epoch = book.chapters().get(last).epoch();
        if (epoch <= 0) {
            return last;
        }
        int first = last;
        while (first > 0 && book.chapters().get(first - 1).epoch() == epoch) {
            first--;
        }
        return first == 0 ? last : first;
    }

    // ================= generator =================

    private static CanvasLayout generator(MapBook book, Parts parts, Progress progress) {
        Draft draft = new Draft(book, parts);
        int nCh = book.chapters().size();
        List<double[]> placed = new ArrayList<>();
        for (MapBook.Chap chapter : book.chapters()) {
            Local own = parts.disc(chapter);
            double r = Math.max(own.extent(), chapter.quests().length == 0 ? 36 : 16);
            double th = 0;
            double x;
            double y;
            while (true) {
                double rr = 26 * th / (2 * Math.PI);
                x = 1.45 * rr * StrictMath.cos(th);
                y = rr * StrictMath.sin(th);
                boolean clear = true;
                for (double[] other : placed) {
                    double dx = x - other[0];
                    double dy = y - other[1];
                    double min = r + other[2] + 16;
                    if (dx * dx + dy * dy < min * min) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    break;
                }
                th += 0.05;
            }
            placed.add(new double[] {x, y, r});
            List<Wheels.Wheel> wheels = draft.wheels.get(chapter.index());
            for (int k = 0; k < wheels.size(); k++) {
                wheels.get(k).at((float) (x + own.x()[k]), (float) (y + own.y()[k]));
            }
            draft.regions[chapter.index()] = CanvasLayout.Region.disc((float) x, (float) y, (float) r);
            draft.labels[chapter.index()] = discLabel(x, y, r);
            progress.at((double) (chapter.index() + 1) / nCh);
        }
        return draft.finish(MapForm.GENERATOR);
    }

    private static CanvasLayout.Label discLabel(double x, double y, double r) {
        double up = r * 0.72;
        return new CanvasLayout.Label((float) x, (float) (y - up), (float) (2 * Math.sqrt(r * r - up * up) * 0.9));
    }

    // ================= galaxy =================

    private static CanvasLayout galaxy(MapBook book, Parts parts, Progress progress) {
        // arms near the core lean far from the radius; when chapters of neighbouring arms touch, the arms open up and try again
        double curl = 1.6;
        CanvasLayout layout = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            layout = galaxy(book, parts, progress, curl);
            if (!anyOverlap(layout)) {
                break;
            }
            curl *= 1.35;
        }
        return layout;
    }

    /** Whether any two chapters overlap. */
    static boolean anyOverlap(CanvasLayout layout) {
        CanvasLayout.Region[] regions = layout.regions;
        float[][] polygons = new float[regions.length][];
        for (int a = 0; a < regions.length; a++) {
            for (int b = a + 1; b < regions.length; b++) {
                CanvasLayout.Region ra = regions[a];
                CanvasLayout.Region rb = regions[b];
                if (ra.maxX() < rb.minX() || rb.maxX() < ra.minX() || ra.maxY() < rb.minY() || rb.maxY() < ra.minY()) {
                    continue;
                }
                if (polygons[a] == null) {
                    polygons[a] = ra.polygon();
                }
                if (polygons[b] == null) {
                    polygons[b] = rb.polygon();
                }
                if (Polygons.overlap(polygons[a], polygons[b])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CanvasLayout galaxy(MapBook book, Parts parts, Progress progress, double curl) {
        Draft draft = new Draft(book, parts);
        int nCh = book.chapters().size();
        List<Integer> core = core(book);
        List<List<Integer>> arms = arms(book, core);
        Packer.Grid grid = new Packer.Grid();
        Packer.Tuning tuning = Packer.Tuning.of(parts.style());
        int done = 0;
        double[] p = new double[2];
        // the core: the main chapters as one band wound inwards, the last one in the middle
        double coreBand = Math.max(96, 2 * maxR(draft, core) + 8);
        Track.Spiral coreTrack = new Track.Spiral(coreBand * 0.75 + 10, coreBand + 14, 0, true);
        double s = 0;
        for (int i = core.size() - 1; i >= 0; i--) {
            int c = core.get(i);
            double s0 = s + 20;
            List<Wheels.Wheel> wheels = new ArrayList<>(draft.wheels.get(c));
            Collections.reverse(wheels);
            s = Packer.pack(book, wheels, coreTrack, coreBand, s0, grid, tuning);
            s = Math.max(s, s0 + least(book, c, 8));
            draft.regions[c] = segment(coreTrack, s0 - 4, s + 4, coreBand);
            coreTrack.at((s0 + s) / 2, coreBand / 4, p);
            draft.labels[c] = new CanvasLayout.Label((float) p[0], (float) p[1], (float) Math.min(s - s0 + 8, coreBand));
            progress.at((double) ++done / nCh);
        }
        double coreR = 0;
        for (int c : core) {
            for (Wheels.Wheel wheel : draft.wheels.get(c)) {
                coreR = Math.max(coreR, Math.hypot(wheel.x(), wheel.y()) + wheel.r);
            }
            for (float[] corner : corners(draft.regions[c])) {
                coreR = Math.max(coreR, Math.hypot(corner[0], corner[1]));
            }
        }
        // the arms: a section each, a curved band out from the core, as wide as its quests need
        double[] widths = new double[arms.size()];
        final double gap = 26;
        double pitch = 0;
        for (int k = 0; k < arms.size(); k++) {
            int quests = 0;
            for (int c : arms.get(k)) {
                quests += book.chapters().get(c).quests().length;
            }
            widths[k] = Math.max(26 * Math.sqrt(quests / 10.0), 2 * maxR(draft, arms.get(k)) + 12);
            pitch += widths[k] + gap;
        }
        // every arm starts on one circle round the core, which must be long enough to hold them side by side
        double rb = Math.max(coreR + 40, pitch / (2 * Math.PI));
        double th = -Math.PI * 0.62;
        for (int k = 0; k < arms.size(); k++) {
            double w = widths[k];
            // from the middle of the arm before to the middle of this one: half of each and the gap
            th += 2 * Math.PI * ((k == 0 ? widths[arms.size() - 1] : widths[k - 1]) / 2 + w / 2 + gap) / pitch;
            Track.Spiral track = new Track.Spiral(rb + w / 2, pitch * curl, th, true);
            s = 0;
            for (int c : arms.get(k)) {
                double s0 = s + 16;
                s = Packer.pack(book, draft.wheels.get(c), track, w, s0, grid, tuning);
                s = Math.max(s, s0 + least(book, c, 8));
                draft.regions[c] = segment(track, s0 - 5, s + 5, w);
                track.at((s0 + s) / 2, w / 4, p);
                draft.labels[c] = new CanvasLayout.Label((float) p[0], (float) p[1], (float) Math.min(s - s0 + 10, w));
                progress.at((double) ++done / nCh);
            }
            int epoch = book.chapters().get(arms.get(k).getFirst()).epoch();
            track.at(Math.max(s - 70, s * 0.7), w / 2 + 22, p);
            double[] out = new double[2];
            track.at(Math.max(s - 70, s * 0.7), w / 2 + 60, out);
            draft.arms.add(new CanvasLayout.Arm(epoch, (float) p[0], (float) p[1], (float) (out[0] - p[0]), (float) (out[1] - p[1])));
        }
        return draft.finish(MapForm.GALAXY);
    }

    private static float[][] corners(CanvasLayout.Region region) {
        return new float[][] {{region.minX(), region.minY()}, {region.maxX(), region.minY()}, {region.minX(), region.maxY()}, {region.maxX(), region.maxY()}};
    }

    private static double maxR(Draft draft, List<Integer> chapters) {
        double max = 10;
        for (int c : chapters) {
            for (Wheels.Wheel wheel : draft.wheels.get(c)) {
                max = Math.max(max, wheel.r);
            }
        }
        return max;
    }

    private static CanvasLayout.Region segment(Track track, double a, double b, double band) {
        int n = Math.max(8, (int) ((b - a) / 6));
        float[] pts = new float[2 * (n + 1)];
        double[] p = new double[2];
        for (int k = 0; k <= n; k++) {
            track.at(a + (b - a) * k / n, 0, p);
            pts[2 * k] = (float) p[0];
            pts[2 * k + 1] = (float) p[1];
        }
        return CanvasLayout.Region.band(pts, (float) band);
    }

    /**
     * The core of a galaxy, by the first rule that finds one: the first section when it has at most six chapters and the rest at
     * least twice as many; the chapters other chapters depend on; the first chapter.
     */
    static List<Integer> core(MapBook book) {
        Map<Integer, List<Integer>> sections = sections(book);
        int firstEpoch = sections.keySet().stream().filter(e -> e > 0).min(Integer::compare).orElse(0);
        if (firstEpoch > 0) {
            List<Integer> first = sections.get(firstEpoch);
            int rest = book.chapters().size() - first.size();
            if (first.size() <= 6 && rest >= 2 * first.size()) {
                return first;
            }
        }
        Set<Integer> needed = new HashSet<>();
        for (MapBook.Edge edge : book.edges()) {
            if (edge.cross()) {
                needed.add(book.quests().get(edge.from()).chapter());
            }
        }
        if (!needed.isEmpty() && needed.size() <= Math.max(1, book.chapters().size() / 3)) {
            return needed.stream().sorted().toList();
        }
        return List.of(0);
    }

    /** Chapters by epoch number in order of first appearance; chapters without one under 0. */
    private static Map<Integer, List<Integer>> sections(MapBook book) {
        Map<Integer, List<Integer>> out = new LinkedHashMap<>();
        for (MapBook.Chap chapter : book.chapters()) {
            out.computeIfAbsent(chapter.epoch(), key -> new ArrayList<>()).add(chapter.index());
        }
        return out;
    }

    /**
     * The arms: one per section, chapters without a section in one arm last. A book without sections is cut into six to eight
     * arms in book order.
     */
    static List<List<Integer>> arms(MapBook book, List<Integer> core) {
        Set<Integer> inCore = new HashSet<>(core);
        Map<Integer, List<Integer>> sections = sections(book);
        List<List<Integer>> out = new ArrayList<>();
        boolean any = sections.keySet().stream().anyMatch(e -> e > 0);
        if (any) {
            for (Map.Entry<Integer, List<Integer>> entry : sections.entrySet()) {
                if (entry.getKey() == 0) {
                    continue;
                }
                List<Integer> rest = entry.getValue().stream().filter(c -> !inCore.contains(c)).toList();
                if (!rest.isEmpty()) {
                    out.add(rest);
                }
            }
            List<Integer> loose = sections.getOrDefault(0, List.of()).stream().filter(c -> !inCore.contains(c)).toList();
            if (!loose.isEmpty()) {
                out.add(loose);
            }
            return out;
        }
        List<Integer> rest = book.chapters().stream().map(MapBook.Chap::index).filter(c -> !inCore.contains(c)).toList();
        if (rest.isEmpty()) {
            return out;
        }
        int count = Math.min(rest.size(), Math.max(6, Math.min(8, (int) Math.ceil(rest.size() / 8.0))));
        for (int k = 0; k < count; k++) {
            List<Integer> arm = rest.subList(rest.size() * k / count, rest.size() * (k + 1) / count);
            if (!arm.isEmpty()) {
                out.add(arm);
            }
        }
        return out;
    }

    // ================= shared =================

    private record Rotation(double cos, double sin) {
        Rotation(double angle) {
            this(StrictMath.cos(angle), StrictMath.sin(angle));
        }

        void apply(double[] p) {
            double x = p[0] * cos - p[1] * sin;
            double y = p[0] * sin + p[1] * cos;
            p[0] = x;
            p[1] = y;
        }

        void apply(Wheels.Wheel wheel) {
            double x = wheel.x() * cos - wheel.y() * sin;
            double y = wheel.x() * sin + wheel.y() * cos;
            wheel.at((float) x, (float) y);
        }
    }

    /** A canvas being laid out: the wheels of every chapter and what the form adds around them. */
    static final class Draft {
        final MapBook book;
        final List<List<Wheels.Wheel>> wheels = new ArrayList<>();
        final CanvasLayout.Region[] regions;
        final CanvasLayout.Label[] labels;
        final List<CanvasLayout.Mark> marks = new ArrayList<>();
        final List<CanvasLayout.Arm> arms = new ArrayList<>();
        final List<float[]> turns = new ArrayList<>();

        Draft(MapBook book, Parts parts) {
            this.book = book;
            for (MapBook.Chap chapter : book.chapters()) {
                wheels.add(parts.wheels(chapter));
            }
            regions = new CanvasLayout.Region[book.chapters().size()];
            labels = new CanvasLayout.Label[book.chapters().size()];
        }

        double maxR() {
            double max = 10;
            for (List<Wheels.Wheel> list : wheels) {
                for (Wheels.Wheel wheel : list) {
                    max = Math.max(max, wheel.r);
                }
            }
            return max;
        }

        /** Nodes from the wheels, whole units; everything else to a tenth. */
        CanvasLayout finish(MapForm form) {
            int n = book.quests().size();
            float[] x = new float[n];
            float[] y = new float[n];
            byte[] kind = new byte[n];
            List<CanvasLayout.Ring> rings = new ArrayList<>();
            for (List<Wheels.Wheel> list : wheels) {
                for (Wheels.Wheel wheel : list) {
                    if (wheel.hub >= 0) {
                        x[wheel.hub] = Math.round(wheel.x());
                        y[wheel.hub] = Math.round(wheel.y());
                        kind[wheel.hub] |= CanvasLayout.HUB;
                    }
                    for (int k = 0; k < wheel.members.length; k++) {
                        x[wheel.members[k]] = Math.round(wheel.x() + wheel.relX[k]);
                        y[wheel.members[k]] = Math.round(wheel.y() + wheel.relY[k]);
                    }
                    if (wheel.orbits.length > 0) {
                        float[] orbits = new float[wheel.orbits.length];
                        for (int k = 0; k < orbits.length; k++) {
                            orbits[k] = CanvasLayout.tenth(wheel.orbits[k]);
                        }
                        rings.add(new CanvasLayout.Ring(CanvasLayout.tenth(wheel.x()), CanvasLayout.tenth(wheel.y()), wheel.chapter, orbits));
                    }
                }
            }
            for (MapBook.Q quest : book.quests()) {
                if (quest.milestone()) {
                    kind[quest.index()] |= CanvasLayout.MILESTONE;
                }
            }
            int finale = form == MapForm.SPIRAL ? CanvasLayout.finale(book) : -1;
            if (finale >= 0) {
                kind[finale] |= CanvasLayout.FINALE;
            }
            CanvasLayout.Region[] outRegions = new CanvasLayout.Region[regions.length];
            CanvasLayout.Label[] outLabels = new CanvasLayout.Label[labels.length];
            for (int i = 0; i < regions.length; i++) {
                CanvasLayout.Region region = regions[i] != null ? regions[i] : CanvasLayout.Region.rect(0, 0, 30, 30);
                float[] pts = region.pts().clone();
                for (int k = 0; k < pts.length; k++) {
                    pts[k] = CanvasLayout.tenth(pts[k]);
                }
                float width = CanvasLayout.tenth(region.width());
                outRegions[i] = switch (region.kind()) {
                    case RECT -> CanvasLayout.Region.rect(pts[0], pts[1], pts[2], pts[3]);
                    case DISC -> CanvasLayout.Region.disc(pts[0], pts[1], pts[2]);
                    case BAND -> CanvasLayout.Region.band(pts, width);
                };
                CanvasLayout.Label label = labels[i] != null ? labels[i] : new CanvasLayout.Label(region.centerX(), region.minY(), 30);
                outLabels[i] = new CanvasLayout.Label(CanvasLayout.tenth(label.x()), CanvasLayout.tenth(label.y()), CanvasLayout.tenth(label.maxWidth()));
            }
            List<CanvasLayout.Mark> outMarks = marks.stream().map(m -> new CanvasLayout.Mark(m.epoch(), CanvasLayout.tenth(m.x1()), CanvasLayout.tenth(m.y1()),
                    CanvasLayout.tenth(m.x2()), CanvasLayout.tenth(m.y2()), m.words(), m.side())).toList();
            List<CanvasLayout.Arm> outArms = arms.stream().map(a -> new CanvasLayout.Arm(a.epoch(), CanvasLayout.tenth(a.x()), CanvasLayout.tenth(a.y()),
                    CanvasLayout.tenth(a.outX()), CanvasLayout.tenth(a.outY()))).toList();
            List<float[]> outTurns = new ArrayList<>();
            for (float[] turn : turns) {
                float[] t = turn.clone();
                for (int k = 0; k < t.length; k++) {
                    t[k] = CanvasLayout.tenth(t[k]);
                }
                outTurns.add(t);
            }
            return new CanvasLayout(form, book.hash(), x, y, kind, outRegions, outLabels, rings, outMarks, outArms, outTurns);
        }
    }
}
