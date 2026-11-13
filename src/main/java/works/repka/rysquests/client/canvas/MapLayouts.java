package works.repka.rysquests.client.canvas;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleSupplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientLayout;
import works.repka.rysquests.client.layout.CanvasLayout;
import works.repka.rysquests.client.layout.LayoutEngine;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.data.BookLayout;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.editor.EditorFiles;

/**
 * Where the client gets a book's canvas, by the plan's order: the layout that came with the book when it fits ({@code layout.json}),
 * else one laid out before and kept by the book's layout hash, in memory and on disk under {@code rysquests/layout_cache}, else it
 * is laid out now off the main thread and the map shows "Laying out the map" meanwhile. It also gives the layout engine to
 * {@link ClientLayout}, so that an operator's client can bake and save {@code layout.json}.
 */
public final class MapLayouts {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapLayouts.class);
    private static final int KEEP = 8;
    private static final LayoutEngine ENGINE = new LayoutEngine();
    private static final Map<String, CanvasLayout> MEMORY = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CanvasLayout> eldest) {
            return size() > KEEP;
        }
    };
    private static final Map<String, LayoutEngine.Job> RUNNING = new LinkedHashMap<>();
    @Nullable
    private static MapBook lastMap;

    private MapLayouts() {
    }

    /** The canvas of a book: ready, or being laid out with how far it is. */
    public record State(MapBook map, MapForm form, @Nullable CanvasLayout layout, DoubleSupplier progress) {
        public boolean ready() {
            return layout != null;
        }
    }

    /** Gives {@link ClientLayout} the engine: from now on operators can bake {@code layout.json}. */
    public static void register() {
        ClientLayout.setBaker((book, form) -> CompletableFuture.supplyAsync(() -> {
            MapBook map = MapBook.of(book);
            CanvasLayout layout = ENGINE.lay(map, form);
            remember(map, layout);
            return layout.toBaked(map);
        }, Util.backgroundExecutor()));
    }

    /** The canvas in the book's own form. Call from the main thread; a layout being worked out is picked up on a later call. */
    public static State get(QuestBook book) {
        return get(book, MapForms.choose(book).form());
    }

    public static State get(QuestBook book, MapForm form) {
        MapBook map = mapOf(book);
        if (pausedForTest) {
            return new State(map, form, null, () -> 0.62);
        }
        String key = key(map.hash(), form);
        CanvasLayout known = MEMORY.get(key);
        if (known != null) {
            return new State(map, form, known, () -> 1);
        }
        Optional<BookLayout> baked = book.freshLayout().filter(layout -> layout.form() == form);
        if (baked.isPresent()) {
            CanvasLayout read = CanvasLayout.fromBaked(map, baked.get());
            if (read != null) {
                MEMORY.put(key, read);
                return new State(map, form, read, () -> 1);
            }
        }
        LayoutEngine.Job job = RUNNING.get(key);
        if (job == null) {
            CanvasLayout cached = readCache(map, form);
            if (cached != null) {
                MEMORY.put(key, cached);
                return new State(map, form, cached, () -> 1);
            }
            job = ENGINE.start(map, form, Util.backgroundExecutor());
            RUNNING.put(key, job);
            LayoutEngine.Job started = job;
            job.result.thenAcceptAsync(layout -> {
                RUNNING.remove(key);
                MEMORY.put(key, layout);
                Util.ioPool().execute(() -> writeCache(map, layout));
            }, Minecraft.getInstance()).exceptionally(failure -> {
                RUNNING.remove(key);
                return null;
            });
            job = started;
        }
        if (job.result.isDone() && !job.result.isCompletedExceptionally()) {
            CanvasLayout done = job.result.join();
            MEMORY.put(key, done);
            RUNNING.remove(key);
            return new State(map, form, done, () -> 1);
        }
        LayoutEngine.Job shown = job;
        return new State(map, form, null, shown::progress);
    }

    /** All four forms for the form preview, the recommended one first (picture 07b). */
    public static Map<MapForm, LayoutEngine.Job> preview(QuestBook book) {
        MapBook map = mapOf(book);
        return ENGINE.layAll(map, MapForms.auto(book.meta(), java.util.List.copyOf(book.chapters().values()), java.util.List.copyOf(book.quests().values())).form(),
                Util.backgroundExecutor());
    }

    private static MapBook mapOf(QuestBook book) {
        MapBook map = lastMap;
        if (map == null || !map.hash().equals(book.layoutHash())) {
            map = MapBook.of(book);
            lastMap = map;
        }
        return map;
    }

    private static synchronized void remember(MapBook map, CanvasLayout layout) {
        Minecraft.getInstance().execute(() -> MEMORY.put(key(map.hash(), layout.form), layout));
    }

    private static String key(String hash, MapForm form) {
        return hash + "-" + form.getSerializedName();
    }

    // --- the cache on disk ---

    private static Path cacheFile(String hash, MapForm form) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("rysquests").resolve("layout_cache").resolve(key(hash.length() > 40 ? hash.substring(0, 40) : hash, form) + ".json");
    }

    @Nullable
    private static CanvasLayout readCache(MapBook map, MapForm form) {
        Path file = cacheFile(map.hash(), form);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            BookLayout layout = BookLayout.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
            if (layout == null || !layout.hash().equals(map.hash()) || layout.form() != form) {
                return null;
            }
            // kept by an older version that put other chapters on the map for the same hash (epoch 0, ADR-51): lay out again
            if (layout.decor().regions().size() != map.chapters().size()) {
                return null;
            }
            return CanvasLayout.fromBaked(map, layout);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Could not read the kept map {}", file, e);
            return null;
        }
    }

    private static void writeCache(MapBook map, CanvasLayout layout) {
        Path file = cacheFile(map.hash(), layout.form);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, EditorFiles.layoutText(layout.toBaked(map)), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Could not keep the map {}", file, e);
        }
    }

    private static volatile boolean pausedForTest;

    /** Holds every map on "Laying out the map", for the autotest's picture 49. */
    public static void pauseForTest(boolean paused) {
        pausedForTest = paused;
    }

    /** Forgets kept layouts in memory, for tests that lay out again. */
    public static void forget() {
        MEMORY.clear();
    }
}
