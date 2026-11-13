package works.repka.rysquests.client.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.MapForm;

/**
 * Notes of the RepYS server on the spiral: the last epoch winds on to the book's finale in the middle; the marks of the epochs stand
 * in the middle of the gap between chapters; the pointer over a chapter finds that chapter, not the neighbour whose band would reach
 * over it with a rounded end.
 */
class SpiralCentreTest {
    @Test
    void theFinaleStandsInTheMiddleOfTheSpiral() {
        MapBook book = CanvasLayoutTest.madeUp(9, 6, 4);
        CanvasLayout layout = new LayoutEngine().lay(book, MapForm.SPIRAL);
        int finale = CanvasLayout.finale(book);
        assertEquals("t:c8/m5", book.quests().get(finale).id().toString(), "the end of the last chapter's chain");
        assertEquals(0, layout.x[finale]);
        assertEquals(0, layout.y[finale]);
        assertTrue(layout.finale(finale));
        assertTrue(layout.regions[book.chapters().size() - 1].contains(0, 0), "the last chapter curls round the middle");
        for (int q = 0; q < layout.x.length; q++) {
            assertEquals(q == finale, layout.finale(q));
        }
        // the last epoch winds tighter than the rest
        assertTrue(layout.regions[8].width() < layout.regions[0].width());
        assertEquals(layout.regions[6].width(), layout.regions[8].width());
        CanvasLayoutTest.check(book, layout, "spiral");
        // other forms keep no finale
        CanvasLayout serpentine = new LayoutEngine().lay(book, MapForm.SERPENTINE);
        assertFalse(serpentine.finale(finale));
    }

    @Test
    void anEpochsMarkStandsInTheMiddleOfTheGap() {
        MapBook book = CanvasLayoutTest.madeUp(9, 6, 4);
        for (MapForm form : new MapForm[] {MapForm.SPIRAL, MapForm.SERPENTINE}) {
            CanvasLayout layout = new LayoutEngine().lay(book, form);
            for (CanvasLayout.Mark mark : layout.marks) {
                int first = -1;
                for (MapBook.Chap chapter : book.chapters()) {
                    if (chapter.epoch() == mark.epoch()) {
                        first = chapter.index();
                        break;
                    }
                }
                if (first <= 0) {
                    continue;
                }
                float mx = (mark.x1() + mark.x2()) / 2;
                float my = (mark.y1() + mark.y2()) / 2;
                double before = gapTo(layout.regions[first - 1], mx, my);
                double after = gapTo(layout.regions[first], mx, my);
                if (before > 60) {
                    // the serpentine starts the epoch on a new row: the chapter before ends the row above
                    continue;
                }
                assertTrue(Math.abs(before - after) <= 1.5, form + ": the mark of epoch " + mark.epoch() + " is " + before + " from the chapter before and " + after
                        + " from its own");
            }
        }
    }

    /** How far the point is from the region's outline. */
    private static double gapTo(CanvasLayout.Region region, float x, float y) {
        float[] poly = region.polygon();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < poly.length; i += 2) {
            int j = (i + 2) % poly.length;
            best = Math.min(best, CanvasLayout.segmentDistance(x, y, poly[i], poly[i + 1], poly[j], poly[j + 1]));
        }
        return best;
    }

    @Test
    void aBandEndsSquare() {
        // two chapters along one line, a gap of 14 between them, 100 wide
        CanvasLayout.Region a = CanvasLayout.Region.band(new float[] {0, 0, 100, 0}, 100);
        CanvasLayout.Region b = CanvasLayout.Region.band(new float[] {114, 0, 214, 0}, 100);
        assertTrue(a.contains(99, 40));
        assertFalse(a.contains(110, 0), "past the end of the band");
        assertTrue(b.contains(116, 30));
        assertFalse(a.contains(120, 30), "the neighbour's area is its own");
        assertFalse(a.contains(-5, 0), "before the start");
        assertTrue(a.distanceToLine(110, 0) <= 50, "the plain distance still reaches round the end");
    }
}
