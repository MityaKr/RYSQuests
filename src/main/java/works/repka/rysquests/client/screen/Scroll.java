package works.repka.rysquests.client.screen;

import works.repka.ryscore.ui.Scroller;

/**
 * A list's scroll position in mockup pixels, on top of the family's smooth {@link Scroller}. The length of a list is
 * known only after drawing it, so each frame draws with the limit found on the frame before.
 */
final class Scroll {
    private static final float PER_GUI = 2f;

    private final Scroller scroller = new Scroller();
    private float limit;

    /** @param notches positive when the wheel turns up, as the game reports it */
    void wheel(double notches, float step) {
        scroller.by((float) (-notches * step) / PER_GUI);
    }

    /** Call once per frame. Near an edge the result can go slightly past it. */
    int offset() {
        return Math.round(scroller.update(limit / PER_GUI) * PER_GUI);
    }

    void content(float content, float view) {
        limit = Math.max(0, content - view);
        scroller.limit(limit / PER_GUI);
    }

    void reset() {
        scroller.reset();
    }
}
