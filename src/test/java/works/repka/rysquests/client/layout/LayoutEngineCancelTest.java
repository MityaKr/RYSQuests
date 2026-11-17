package works.repka.rysquests.client.layout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.MapForm;

/** The form preview stops the forms nobody waits for once one is chosen ("расчёт остальных остановится"). */
class LayoutEngineCancelTest {
    @Test
    void cancelledFormsOfThePreviewAreNotLaidOut() {
        MapBook book = CanvasLayoutTest.madeUp(6, 4, 2);
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Executor later = queue::add;
        Map<MapForm, LayoutEngine.Job> jobs = new LayoutEngine().layAll(book, MapForm.SERPENTINE, later);
        jobs.forEach((form, job) -> {
            if (form != MapForm.SERPENTINE) {
                job.cancel();
            }
        });
        queue.forEach(Runnable::run);
        LayoutEngine.Job kept = jobs.get(MapForm.SERPENTINE);
        assertTrue(kept.result.isDone() && !kept.result.isCompletedExceptionally(), "the chosen form is laid out");
        jobs.forEach((form, job) -> {
            if (form != MapForm.SERPENTINE) {
                assertTrue(job.result.isCancelled(), form + " is cancelled");
                assertTrue(job.cancelled());
            }
        });
    }

    @Test
    void aFinishedFormKeepsItsLayout() {
        MapBook book = CanvasLayoutTest.madeUp(3, 3, 1);
        LayoutEngine.Job job = new LayoutEngine().start(book, MapForm.SPIRAL, Runnable::run);
        job.cancel();
        assertFalse(job.result.isCancelled());
        assertTrue(job.result.isDone());
    }
}
