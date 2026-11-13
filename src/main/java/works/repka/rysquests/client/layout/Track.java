package works.repka.rysquests.client.layout;

import java.util.Arrays;

/**
 * The centre line of a band that wheels are packed along: {@link #at} gives the point at length {@code s} along it, moved by
 * {@code o} across it. The serpentine packs a chapter along a straight line; the spiral and the galaxy along spirals.
 */
abstract class Track {
    /** Writes {@code x, y} into {@code out}. */
    abstract void at(double s, double o, double[] out);

    /** A straight line to the right from {@code (x0, y0)}; across is down. */
    static final class Line extends Track {
        private final double x0;
        private final double y0;

        Line(double x0, double y0) {
            this.x0 = x0;
            this.y0 = y0;
        }

        @Override
        void at(double s, double o, double[] out) {
            out[0] = x0 + s;
            out[1] = y0 + o;
        }
    }

    /**
     * An Archimedean spiral from the inside out, {@code r = r0 + pitch * t / 2π} at the angle {@code start + t}; across is away
     * from the centre: straight out, or square to the line when {@code square}, so that a band keeps its width however open the
     * spiral is. Sampled about every unit and a half of length, as far out as it is asked for.
     */
    static final class Spiral extends Track {
        private final double r0;
        private final double pitch;
        private final double start;
        private final boolean square;
        private double[] t = new double[1024];
        private double[] cum = new double[1024];
        private double[] px = new double[1024];
        private double[] py = new double[1024];
        private int n;

        Spiral(double r0, double pitch, double start) {
            this(r0, pitch, start, false);
        }

        Spiral(double r0, double pitch, double start, boolean square) {
            this.r0 = r0;
            this.pitch = pitch;
            this.start = start;
            this.square = square;
            add(0, 0);
        }

        private void add(double angle, double length) {
            if (n == t.length) {
                t = Arrays.copyOf(t, n * 2);
                cum = Arrays.copyOf(cum, n * 2);
                px = Arrays.copyOf(px, n * 2);
                py = Arrays.copyOf(py, n * 2);
            }
            double r = radius(angle);
            t[n] = angle;
            cum[n] = length;
            px[n] = r * StrictMath.cos(start + angle);
            py[n] = r * StrictMath.sin(start + angle);
            n++;
        }

        double radius(double angle) {
            return r0 + pitch * angle / (2 * Math.PI);
        }

        private void reach(double s) {
            while (cum[n - 1] < s) {
                double angle = t[n - 1];
                double r = Math.max(radius(angle), 1);
                double step = Math.min(0.02, 1.5 / r);
                double next = angle + step;
                double nr = radius(next);
                double x = nr * StrictMath.cos(start + next);
                double y = nr * StrictMath.sin(start + next);
                add(next, cum[n - 1] + Math.hypot(x - px[n - 1], y - py[n - 1]));
            }
        }

        /** The spiral's angle (from {@code start}) at length {@code s}. */
        double angleAt(double s) {
            if (s <= 0) {
                return 0;
            }
            reach(s);
            int i = index(s);
            return t[i];
        }

        private int index(double s) {
            int lo = 0;
            int hi = n - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (cum[mid] < s) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return lo;
        }

        @Override
        void at(double s, double o, double[] out) {
            if (s <= 0) {
                s = 0;
            }
            reach(s);
            int i = index(s);
            double x;
            double y;
            double angle;
            if (i > 0 && cum[i] > cum[i - 1]) {
                double k = (s - cum[i - 1]) / (cum[i] - cum[i - 1]);
                x = px[i - 1] + (px[i] - px[i - 1]) * k;
                y = py[i - 1] + (py[i] - py[i - 1]) * k;
                angle = t[i - 1] + (t[i] - t[i - 1]) * k;
            } else {
                x = px[i];
                y = py[i];
                angle = t[i];
            }
            double c = StrictMath.cos(start + angle);
            double sn = StrictMath.sin(start + angle);
            if (square) {
                double r = radius(angle);
                double b = pitch / (2 * Math.PI);
                double nx = r * c + b * sn;
                double ny = r * sn - b * c;
                double len = Math.hypot(nx, ny);
                c = nx / len;
                sn = ny / len;
            }
            out[0] = x + o * c;
            out[1] = y + o * sn;
        }
    }
}
