package works.repka.rysquests.client.layout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.MapForm;

/**
 * Lays books out on the canvas, off the main thread. Pieces of every chapter (its wheels, and the chapter packed on its own) are
 * kept by the chapter's version, so laying the book out again after one chapter changed packs that chapter alone; the forms that
 * pack all chapters on one track (spiral, galaxy) place the kept wheels again, which is the quick part.
 *
 * <p>The form preview lays out all four forms in turn, the recommended one first: {@link #layAll}.
 */
public final class LayoutEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(LayoutEngine.class);
    private static final int KEEP_PIECES = 20000;

    /** One form being laid out: how far it is and the result. {@code queued} until its turn comes. */
    public static final class Job {
        public final MapForm form;
        public final CompletableFuture<CanvasLayout> result = new CompletableFuture<>();
        private volatile double progress;
        private volatile boolean started;
        private volatile boolean cancelled;

        Job(MapForm form) {
            this.form = form;
        }

        /** From 0 to 1. */
        public double progress() {
            return result.isDone() ? 1 : progress;
        }

        public boolean queued() {
            return !started;
        }

        /**
         * Not wanted any more ("расчёт остальных остановится" once a form is chosen): a queued job never starts, a running one stops at
         * its next step. Its result ends cancelled; a finished one keeps its layout.
         */
        public void cancel() {
            cancelled = true;
            if (!started) {
                result.cancel(false);
            }
        }

        public boolean cancelled() {
            return cancelled;
        }
    }

    /** Thrown from the progress of a cancelled job to leave the layout where it is. */
    private static final class Cancelled extends RuntimeException {
        Cancelled() {
            super(null, null, false, false);
        }
    }

    /** Wheels of a chapter as they were made, with the book's quest indices of then: {@link Template#first} says how to move them. */
    private record Template(int first, List<Wheels.Wheel> wheels) {
    }

    private final Map<String, Template> wheels = new ConcurrentHashMap<>();
    private final Map<String, Forms.Local> lines = new ConcurrentHashMap<>();
    private final Map<String, Forms.Local> discs = new ConcurrentHashMap<>();
    /** How many chapters were packed on their own since the engine was made: tests see that kept chapters are not packed again. */
    final AtomicInteger packedChapters = new AtomicInteger();

    /** Lays the book out now, on this thread. */
    public CanvasLayout lay(MapBook book, MapForm form) {
        return lay(book, form, Forms.Progress.NONE);
    }

    CanvasLayout lay(MapBook book, MapForm form, Forms.Progress progress) {
        if (form == MapForm.AUTO) {
            throw new IllegalArgumentException("lay out in a form, not auto");
        }
        trim();
        long start = System.nanoTime();
        CanvasLayout layout = Forms.lay(book, form, new Parts(book, Wheels.style(book)), progress);
        LOGGER.debug("Laid out {} quests as {} in {} ms", book.quests().size(), form.getSerializedName(), (System.nanoTime() - start) / 1_000_000);
        return layout;
    }

    /** Lays the book out on the executor. */
    public Job start(MapBook book, MapForm form, Executor executor) {
        Job job = new Job(form);
        executor.execute(() -> run(job, book));
        return job;
    }

    /**
     * All four forms one after another on the executor, {@code first} first: the preview shows it as soon as it is ready and the
     * others as they come.
     */
    public Map<MapForm, Job> layAll(MapBook book, MapForm first, Executor executor) {
        List<MapForm> order = new ArrayList<>(List.of(first));
        for (MapForm form : MapForm.values()) {
            if (form != MapForm.AUTO && form != first) {
                order.add(form);
            }
        }
        Map<MapForm, Job> jobs = new EnumMap<>(MapForm.class);
        order.forEach(form -> jobs.put(form, new Job(form)));
        executor.execute(() -> {
            for (MapForm form : order) {
                run(jobs.get(form), book);
            }
        });
        return jobs;
    }

    private void run(Job job, MapBook book) {
        job.started = true;
        if (job.cancelled) {
            job.result.cancel(false);
            return;
        }
        try {
            job.result.complete(lay(book, job.form, fraction -> {
                if (job.cancelled) {
                    throw new Cancelled();
                }
                job.progress = fraction;
            }));
        } catch (Cancelled stopped) {
            LOGGER.debug("Stopped laying the map out as {}", job.form.getSerializedName());
            job.result.cancel(false);
        } catch (Throwable failure) {
            LOGGER.error("Could not lay the map out as {}", job.form.getSerializedName(), failure);
            job.result.completeExceptionally(failure);
        }
    }

    private void trim() {
        if (wheels.size() + lines.size() + discs.size() > KEEP_PIECES) {
            wheels.clear();
            lines.clear();
            discs.clear();
        }
    }

    /** The pieces of chapters, kept by chapter version. */
    private final class Parts implements Forms.Parts {
        private final MapBook book;
        private final Wheels.Style style;

        Parts(MapBook book, Wheels.Style style) {
            this.book = book;
            this.style = style;
        }

        @Override
        public Wheels.Style style() {
            return style;
        }

        private Template template(MapBook.Chap chapter) {
            // the style is book-wide, and which quests are main quests in a tree depends on dependents in other chapters too
            String key = style + "|" + chapter.version() + "|" + treeKey(chapter);
            return wheels.computeIfAbsent(key, k -> new Template(first(chapter), Wheels.of(book, chapter, style)));
        }

        private static int first(MapBook.Chap chapter) {
            return chapter.quests().length == 0 ? 0 : chapter.quests()[0];
        }

        /** In a tree, a quest of this chapter becomes a main quest when anything depends on it: part of the chapter's key. */
        private String treeKey(MapBook.Chap chapter) {
            if (style != Wheels.Style.TREE) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            for (int q : chapter.quests()) {
                out.append(book.dependents(q).length > 0 ? '1' : '0');
            }
            return Integer.toHexString(out.toString().hashCode());
        }

        @Override
        public List<Wheels.Wheel> wheels(MapBook.Chap chapter) {
            Template template = template(chapter);
            int shift = first(chapter) - template.first();
            List<Wheels.Wheel> out = new ArrayList<>();
            for (Wheels.Wheel wheel : template.wheels()) {
                out.add(wheel.copy(shift, chapter.index()));
            }
            return out;
        }

        @Override
        public Forms.Local line(MapBook.Chap chapter, double band) {
            String key = style + "|" + chapter.version() + "|" + treeKey(chapter) + "|" + band;
            return lines.computeIfAbsent(key, k -> {
                packedChapters.incrementAndGet();
                List<Wheels.Wheel> ws = wheels(chapter);
                double end = Packer.pack(book, ws, new Track.Line(0, 0), band, 0, new Packer.Grid(), Packer.Tuning.of(style));
                return local(ws, end, 0, 0);
            });
        }

        @Override
        public Forms.Local disc(MapBook.Chap chapter) {
            String key = style + "|" + chapter.version() + "|" + treeKey(chapter);
            return discs.computeIfAbsent(key, k -> {
                packedChapters.incrementAndGet();
                List<Wheels.Wheel> ws = wheels(chapter);
                if (ws.isEmpty()) {
                    return new Forms.Local(new float[0], new float[0], 16);
                }
                double maxR = 0;
                for (Wheels.Wheel wheel : ws) {
                    maxR = Math.max(maxR, wheel.r);
                }
                double band = Math.max(2 * maxR + 8, 60);
                Packer.pack(book, ws, new Track.Spiral(band / 2, band + 6, 0), band, 0, new Packer.Grid(), Packer.Tuning.of(style));
                double cx = 0;
                double cy = 0;
                for (Wheels.Wheel wheel : ws) {
                    cx += wheel.x();
                    cy += wheel.y();
                }
                cx /= ws.size();
                cy /= ws.size();
                double radius = 0;
                for (Wheels.Wheel wheel : ws) {
                    radius = Math.max(radius, Math.hypot(wheel.x() - cx, wheel.y() - cy) + wheel.r);
                }
                return local(ws, radius, cx, cy);
            });
        }

        private Forms.Local local(List<Wheels.Wheel> ws, double extent, double cx, double cy) {
            float[] x = new float[ws.size()];
            float[] y = new float[ws.size()];
            for (int i = 0; i < ws.size(); i++) {
                x[i] = (float) (ws.get(i).x() - cx);
                y[i] = (float) (ws.get(i).y() - cy);
            }
            return new Forms.Local(x, y, extent);
        }
    }
}
