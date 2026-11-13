package works.repka.rysquests.client.layout;

/** Whether two simple polygons overlap: an edge of one crosses an edge of the other, or one holds a point of the other. */
public final class Polygons {
    private Polygons() {
    }

    public static boolean overlap(float[] a, float[] b) {
        int na = a.length / 2;
        int nb = b.length / 2;
        for (int i = 0; i < na; i++) {
            int i2 = (i + 1) % na;
            for (int j = 0; j < nb; j++) {
                int j2 = (j + 1) % nb;
                if (cross(a[2 * i], a[2 * i + 1], a[2 * i2], a[2 * i2 + 1], b[2 * j], b[2 * j + 1], b[2 * j2], b[2 * j2 + 1])) {
                    return true;
                }
            }
        }
        return inside(a, b[0], b[1]) || inside(b, a[0], a[1]);
    }

    private static boolean cross(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        double d1 = orient(ax, ay, bx, by, cx, cy);
        double d2 = orient(ax, ay, bx, by, dx, dy);
        double d3 = orient(cx, cy, dx, dy, ax, ay);
        double d4 = orient(cx, cy, dx, dy, bx, by);
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    private static double orient(float ax, float ay, float bx, float by, float cx, float cy) {
        return (double) (bx - ax) * (cy - ay) - (double) (by - ay) * (cx - ax);
    }

    public static boolean inside(float[] poly, float x, float y) {
        boolean in = false;
        int n = poly.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = poly[2 * i];
            float yi = poly[2 * i + 1];
            float xj = poly[2 * j];
            float yj = poly[2 * j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }
}
