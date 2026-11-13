package works.repka.rysquests.client.canvas;

/**
 * Where the canvas looks: the canvas point in the middle of the view and the scale in mockup pixels per canvas unit. Flights ease
 * from where it is to a target in a fixed time, the scale on a logarithmic curve, so zooming far in or out feels even.
 */
final class Camera {
    static final long FLIGHT_MS = 380;

    double cx;
    double cy;
    double scale = 1;
    double minScale = 0.05;
    double maxScale = 8;

    private double fromX;
    private double fromY;
    private double fromScale;
    private double toX;
    private double toY;
    private double toScale;
    private long startedAt;
    private boolean flying;

    void jump(double x, double y, double s) {
        flying = false;
        cx = x;
        cy = y;
        scale = clamp(s);
    }

    void fly(double x, double y, double s) {
        fromX = cx;
        fromY = cy;
        fromScale = scale;
        toX = x;
        toY = y;
        toScale = clamp(s);
        startedAt = System.currentTimeMillis();
        flying = true;
    }

    boolean flying() {
        return flying;
    }

    /** Moves along the flight; called once a frame. */
    void update() {
        if (!flying) {
            return;
        }
        double t = Math.min(1, (System.currentTimeMillis() - startedAt) / (double) FLIGHT_MS);
        double e = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
        double logFrom = Math.log(fromScale);
        double logTo = Math.log(toScale);
        scale = Math.exp(logFrom + (logTo - logFrom) * e);
        // the point moves so that the view centre slides in screen terms, not canvas terms
        double k = Math.abs(logTo - logFrom) < 1e-6 ? e : (1 / scale - 1 / fromScale) / (1 / toScale - 1 / fromScale);
        if (!Double.isFinite(k)) {
            k = e;
        }
        cx = fromX + (toX - fromX) * k;
        cy = fromY + (toY - fromY) * k;
        if (t >= 1) {
            flying = false;
            cx = toX;
            cy = toY;
            scale = toScale;
        }
    }

    void stop() {
        flying = false;
    }

    /** Pans by mockup pixels. */
    void pan(double dx, double dy) {
        flying = false;
        cx -= dx / scale;
        cy -= dy / scale;
    }

    /** Zooms by {@code factor} keeping the canvas point under {@code (px, py)}, given relative to the view centre, in place. */
    void zoomAt(double factor, double px, double py) {
        flying = false;
        double s = clamp(scale * factor);
        double wx = cx + px / scale;
        double wy = cy + py / scale;
        scale = s;
        cx = wx - px / s;
        cy = wy - py / s;
    }

    double clamp(double s) {
        return Math.max(minScale, Math.min(maxScale, s));
    }

    double targetScale() {
        return flying ? toScale : scale;
    }
}
