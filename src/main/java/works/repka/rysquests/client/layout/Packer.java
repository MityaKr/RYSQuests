package works.repka.rysquests.client.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Packs wheels along a band, the one step the serpentine, the spiral and the galaxy share ({@code pack} of the Python layouts).
 * Each wheel looks for a place along the band and across it: it must not touch a wheel already placed, must not stand much earlier
 * than its placed neighbours by links, and of the places left takes the cheapest, the sum of distances to those neighbours plus a
 * fine for running ahead along the band. The first makes short roads, the second keeps the band dense.
 */
final class Packer {
    /** Wheels already placed, in cells of {@value #CELL} units, to test a new place against the nearby ones only. */
    static final class Grid {
        private static final double CELL = 60;
        private final Map<Long, List<double[]>> cells = new HashMap<>();
        private double maxR;

        void add(double x, double y, double r) {
            cells.computeIfAbsent(key((int) Math.floor(x / CELL), (int) Math.floor(y / CELL)), k -> new ArrayList<>()).add(new double[] {x, y, r});
            maxR = Math.max(maxR, r);
        }

        boolean free(double x, double y, double r, double pad) {
            double reach = r + maxR + pad;
            int x0 = (int) Math.floor((x - reach) / CELL);
            int x1 = (int) Math.floor((x + reach) / CELL);
            int y0 = (int) Math.floor((y - reach) / CELL);
            int y1 = (int) Math.floor((y + reach) / CELL);
            for (int i = x0; i <= x1; i++) {
                for (int j = y0; j <= y1; j++) {
                    List<double[]> list = cells.get(key(i, j));
                    if (list == null) {
                        continue;
                    }
                    for (double[] p : list) {
                        double dx = x - p[0];
                        double dy = y - p[1];
                        double min = r + p[2] + pad;
                        if (dx * dx + dy * dy < min * min) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private static long key(int i, int j) {
            return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        }
    }

    /** Steps of the search by wheel style, as each Python layout had them. */
    record Tuning(double pad, double step, double ahead, double widen, int lanes) {
        static Tuning of(Wheels.Style style) {
            return style == Wheels.Style.OPTIONAL ? new Tuning(6, 4, 40, 40, 60) : new Tuning(4, 5, 24, 30, 15);
        }
    }

    private Packer() {
    }

    /**
     * Places the wheels along the track inside a band of width {@code band}, from {@code s0}.
     *
     * @return the end of the stretch: the farthest {@code s + r}
     */
    static double pack(MapBook book, List<Wheels.Wheel> wheels, Track track, double band, double s0, Grid grid, Tuning tuning) {
        Map<Integer, double[]> pos = new HashMap<>();
        Map<Integer, Set<Integer>> adj = adjacency(book, wheels);
        double end = s0;
        double[] point = new double[2];
        for (Wheels.Wheel wheel : wheels) {
            double r = wheel.r;
            double maxO = Math.max(0, band / 2 - r - 2);
            int count = Math.min(tuning.lanes(), Math.max(1, (int) (2 * maxO / (r + tuning.pad())) + 1));
            double[] offs = new double[count];
            for (int k = 0; k < count; k++) {
                offs[k] = count > 1 ? -maxO + 2 * maxO * k / (count - 1) : 0;
            }
            List<double[]> ps = new ArrayList<>();
            if (wheel.hub >= 0) {
                for (int n : adj.getOrDefault(wheel.hub, Set.of())) {
                    double[] p = pos.get(n);
                    if (p != null) {
                        ps.add(p);
                    }
                }
            }
            double sLo = s0 + r;
            for (double[] p : ps) {
                sLo = Math.max(sLo, p[2] - r * 0.5);
            }
            double sHi = Math.max(end, sLo) + 2 * r + tuning.ahead();
            double bestCost = Double.MAX_VALUE;
            double bx = 0;
            double by = 0;
            double bs = 0;
            boolean found = false;
            double s = sLo;
            while (true) {
                for (double o : offs) {
                    track.at(s, o, point);
                    double x = point[0];
                    double y = point[1];
                    if (!grid.free(x, y, r, tuning.pad())) {
                        continue;
                    }
                    double cost;
                    if (ps.isEmpty()) {
                        cost = (s - s0) + Math.abs(o) * 0.3;
                    } else {
                        cost = 0;
                        for (double[] p : ps) {
                            cost += Math.hypot(x - p[0], y - p[1]);
                        }
                    }
                    cost += (s - sLo) * 0.15 + (s - s0);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bx = x;
                        by = y;
                        bs = s;
                        found = true;
                    }
                }
                s += tuning.step();
                if (s > sHi) {
                    if (found) {
                        break;
                    }
                    sHi += tuning.widen();
                }
            }
            grid.add(bx, by, r);
            wheel.at((float) bx, (float) by);
            if (wheel.hub >= 0) {
                pos.put(wheel.hub, new double[] {bx, by, bs});
            }
            end = Math.max(end, bs + r);
        }
        return end;
    }

    /** Main quests of the wheels linked to each other by a dependency either way. */
    private static Map<Integer, Set<Integer>> adjacency(MapBook book, List<Wheels.Wheel> wheels) {
        Set<Integer> hubs = new HashSet<>();
        for (Wheels.Wheel wheel : wheels) {
            if (wheel.hub >= 0) {
                hubs.add(wheel.hub);
            }
        }
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (int hub : hubs) {
            for (int r : book.quests().get(hub).requires()) {
                if (hubs.contains(r) && r != hub) {
                    adj.computeIfAbsent(hub, k -> new HashSet<>()).add(r);
                    adj.computeIfAbsent(r, k -> new HashSet<>()).add(hub);
                }
            }
        }
        return adj;
    }
}
