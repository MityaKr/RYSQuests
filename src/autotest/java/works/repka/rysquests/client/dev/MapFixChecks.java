package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.canvas.MapCanvas;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.client.screen.BookScreen;

/**
 * The notes of the RepYS server on the map (fb1), on RepYS as a spiral: hovering finds the chapter under the pointer at both ends of
 * every chapter; a click near the edge of a tile takes its quest; the side links switch thins the web of dashes; a flight to a
 * chapter from the whole book arrives without a jump sideways when its card narrows the canvas; the finale in the middle.
 */
final class MapFixChecks {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapFixChecks.class);
    private static final ResourceLocation GARDEN = ResourceLocation.parse("repys:b12_e5_garden");
    private static final ResourceLocation BLOOD = ResourceLocation.parse("repys:b11_e5_blood");
    private static final ResourceLocation CREATION = ResourceLocation.parse("repys:b26_e9_creation");
    private static final ResourceLocation FINALE = ResourceLocation.parse("repys:b26_e9_creation/matrix_of_creation");

    /** Frames of a flight: the canvas width and the chapter's middle on screen. */
    private static final List<float[]> FLIGHT = new ArrayList<>();
    private static boolean recording;
    private static boolean listening;
    private static final List<String> MISSES = new ArrayList<>();

    private MapFixChecks() {
    }

    static void build(java.util.function.Supplier<MapBook> book) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
                MapCanvas canvas = BookScreen.mapCanvasForTest();
                if (recording && canvas != null) {
                    float[] area = canvas.chapterOnScreen(GARDEN);
                    if (area != null) {
                        FLIGHT.add(new float[] {canvas.viewWidth(), area[0] + area[2] / 2, canvas.flying() ? 1 : 0});
                    }
                }
            });
        }
        // 2: the pointer at either end of every chapter finds that chapter (a band's rounded end lit the next one)
        step("fb1 whole book", () -> {
            moveMouse(1, 1);
            BookScreen.mapFlyForTest(null, 0, null);
        }, 30);
        step("fb1 hover both ends of every chapter", () -> {
            MapCanvas canvas = BookScreen.mapCanvasForTest();
            MISSES.clear();
            for (MapBook.Chap chapter : book.get().chapters()) {
                for (float t : new float[] {0.02f, 0.5f, 0.98f}) {
                    float[] at = canvas.chapterPointForTest(chapter.id(), t);
                    MapCanvas.Target target = canvas.hit(at[0], at[1]);
                    // a node near the probe is taken first, as it should be: only the area of a wrong chapter is a miss
                    if (target.kind() == MapCanvas.Kind.CHAPTER && !chapter.id().equals(target.chapter())) {
                        MISSES.add(chapter.id().getPath() + "@" + t + " -> " + target.chapter());
                    }
                }
            }
            LOGGER.info("[autotest] fb1 hover misses: {}", MISSES);
        }, 2);
        expect("fb1 hover finds the chapter under the pointer", MISSES::isEmpty);
        step("fb1 point at the garden's end next to blood and souls", () -> {
            MapCanvas canvas = BookScreen.mapCanvasForTest();
            float[] at = canvas.chapterPointForTest(GARDEN, 0.97f);
            // near the end, where no node is under the pointer
            for (float t = 0.98f; t > 0.8f; t -= 0.01f) {
                float[] p = canvas.chapterPointForTest(GARDEN, t);
                if (canvas.hit(p[0], p[1]).kind() == MapCanvas.Kind.CHAPTER) {
                    at = p;
                    break;
                }
            }
            moveMouse(at[0] / 2, at[1] / 2);
        }, 15);
        screenshot("map_fb1_hover_garden_end");
        expect("fb1 the hover is the garden", () -> GARDEN.equals(BookScreen.mapCanvasForTest().hovered().chapter()));
        // 3: side links off thins the roads of the hovered chapter
        int[] roads = new int[2];
        step("fb1 count roads with side links", () -> roads[0] = BookScreen.mapCanvasForTest().stats().roads(), 2);
        step("fb1 side links off", () -> BookScreen.mapCanvasForTest().setShowSideLinks(false), 10);
        step("fb1 count roads without side links", () -> roads[1] = BookScreen.mapCanvasForTest().stats().roads(), 2);
        screenshot("map_fb1_side_links_off");
        expect("fb1 fewer roads without side links", () -> {
            LOGGER.info("[autotest] fb1 roads: {} with side links, {} without", roads[0], roads[1]);
            return roads[1] < roads[0];
        });
        step("fb1 legend with both switches", () -> BookScreen.mapLegendForTest(true), 10);
        screenshot("map_fb1_legend_switches");
        step("fb1 side links on, legend closed", () -> {
            BookScreen.mapLegendForTest(false);
            BookScreen.mapCanvasForTest().setShowSideLinks(true);
            moveMouse(1, 1);
        }, 5);
        // 5: a click on a chapter of the whole book flies there; the canvas narrows under the card without a jump
        step("fb1 click the garden on the whole book", () -> {
            MapCanvas canvas = BookScreen.mapCanvasForTest();
            float[] at = canvas.chapterPointForTest(GARDEN, 0.5f);
            // a point of the chapter's area with no node under it: a click on a node selects the quest instead
            for (float t = 0.2f; t <= 0.8f; t += 0.04f) {
                float[] p = canvas.chapterPointForTest(GARDEN, t);
                MapCanvas.Target target = canvas.hit(p[0], p[1]);
                if (target.kind() == MapCanvas.Kind.CHAPTER && GARDEN.equals(target.chapter())) {
                    at = p;
                    break;
                }
            }
            FLIGHT.clear();
            recording = true;
            BookScreen.mapClickForTest(at[0], at[1]);
        }, 50);
        step("fb1 stop recording", () -> recording = false, 1);
        screenshot("map_fb1_garden_arrived");
        expect("fb1 the flight narrowed the canvas without a jump", () -> {
            float worst = 0;
            boolean narrowed = false;
            for (int i = 1; i < FLIGHT.size(); i++) {
                if (FLIGHT.get(i)[0] != FLIGHT.get(i - 1)[0]) {
                    narrowed = true;
                    worst = Math.max(worst, Math.abs(FLIGHT.get(i)[1] - FLIGHT.get(i - 1)[1]));
                }
            }
            float last = FLIGHT.isEmpty() ? 0 : FLIGHT.getLast()[1];
            float settle = 0;
            for (int i = Math.max(1, FLIGHT.size() - 20); i < FLIGHT.size(); i++) {
                settle = Math.max(settle, Math.abs(FLIGHT.get(i)[1] - last));
            }
            LOGGER.info("[autotest] fb1 flight: {} frames, narrowed {}, step at narrowing {} px, last frames within {} px", FLIGHT.size(), narrowed, worst, settle);
            return narrowed && worst < 12;
        });
        // 8: a click near the edge of a tile takes its quest
        ResourceLocation[] quest = new ResourceLocation[1];
        step("fb1 close up on the finale", () -> BookScreen.mapFlyForTest(null, 0, FINALE), 40);
        screenshot("map_fb1_finale_close");
        boolean[] edge = new boolean[1];
        step("fb1 hit the edge of the finale's tile", () -> {
            MapCanvas canvas = BookScreen.mapCanvasForTest();
            float[] at = canvas.questOnScreen(FINALE);
            float half = canvas.nodeHalfForTest(FINALE);
            MapCanvas.Target target = canvas.hit(at[0] + half * 0.95f, at[1] + half * 0.95f);
            quest[0] = target.quest();
            edge[0] = FINALE.equals(target.quest());
            LOGGER.info("[autotest] fb1 tile edge: half {} px, hit {}", half, target.quest());
        }, 2);
        expect("fb1 the corner of a tile is its quest", () -> edge[0]);
        // the closest view: "+" goes on past the old limit, items stay sharp
        step("fb1 zoom in to the limit", () -> BookScreen.mapZoomForTest(20f), 40);
        screenshot("map_fb1_closest");
        expect("fb1 the closest view is past the old limit", () -> {
            double scale = BookScreen.mapCanvasForTest().scale();
            LOGGER.info("[autotest] fb1 closest scale {}", scale);
            return scale > 10;
        });
        // 6: the spiral's centre, the last epoch winding to the finale
        step("fb1 the last epoch", () -> BookScreen.mapFlyForTest(null, 9, null), 40);
        screenshot("map_fb1_creation_epoch");
        step("fb1 the whole spiral", () -> {
            BookScreen.selectForTest(null, null);
            BookScreen.mapFlyForTest(null, 0, null);
        }, 40);
        screenshot("map_fb1_spiral_book");
        step("fb1 the creation chapter", () -> BookScreen.mapFlyForTest(CREATION, 0, null), 40);
        screenshot("map_fb1_creation_chapter");
        step("fb1 back to the whole book", () -> {
            moveMouse(1, 1);
            BookScreen.mapFlyForTest(null, 0, null);
        }, 20);
        expect("fb1 blood and souls is on the map", () -> BookScreen.mapChapterForTest(BLOOD) != null);
    }
}
