package works.repka.rysquests.client.canvas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.layout.CanvasLayout;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.client.screen.TaskText;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Type;
import works.repka.ryscore.ui.UiScale;

/**
 * The canvas of the map ("Полотно"): a book laid out in one form, drawn in a rectangle with a camera over it. It is a component of
 * its own, given a book, its layout, the progress and a rectangle; the map screen around it adds the sidebar, crumbs and panels.
 *
 * <p>Everything outside is in mockup pixels, as the book's {@code Ui} has it: at GUI scale 2 one mockup pixel is one screen
 * pixel. The camera zooms continuously; what is drawn follows the scale (plan, section 7):
 * <ul>
 *     <li>the whole book: nodes are dots in one batch, roads inside chapters and the trunk, chapter numbers and epoch names;</li>
 *     <li>an epoch and a chapter: nodes grow into tiles with item icons once they are 14 pixels, at most 150 icons a frame,
 *     chapter names, the minimap from the chapter level;</li>
 *     <li>a close up: tiles with names, no chapter frames or epoch marks.</li>
 * </ul>
 * Roads between chapters show only for the hovered chapter, the open chapter and between the chapters of the focused epoch.
 * Nothing outside the rectangle is drawn, and roads shorter than 4 pixels are skipped on the whole book.
 */
public final class MapCanvas {
    /** The four levels of the map, from the scale. */
    public enum Level {
        BOOK, EPOCH, CHAPTER, CLOSE
    }

    public enum Kind {
        NONE, QUEST, CHAPTER, EPOCH
    }

    /** What the pointer is over or clicked: a quest, a chapter's area or an epoch label. */
    public record Target(Kind kind, @Nullable ResourceLocation quest, @Nullable ResourceLocation chapter, int epoch) {
        public static final Target NONE = new Target(Kind.NONE, null, null, 0);
    }

    /** Clicks and hovers, for the screen around the canvas. */
    public interface Listener {
        default void clicked(Target target, int button) {
        }

        default void hovered(Target target) {
        }
    }

    /** What the last frame drew and what it cost on the CPU. */
    public record Stats(int nodes, int roads, int icons, int rings, int labels, long nanos) {
    }

    // scale limits of the levels, in mockup pixels per canvas unit
    static final double EPOCH_FROM = 1.2;
    static final double CHAPTER_FROM = 2.7;
    static final double CLOSE_FROM = 4.2;
    /** How close the wheel and "+" go: tiles grow to their largest and the nodes keep drawing apart. */
    private static final double MAX_SCALE = 12;
    /** How close a flight frames on its own: a small chapter is not blown up past this. */
    private static final double FRAME_MAX = 7;
    /** Room for an epoch's plate over the top row of chapters, in mockup pixels. */
    private static final float EPOCH_LABEL_ROOM = 28;
    private static final int MAX_ICONS = 150;
    private static final float MIN_TILE = 14;
    private static final float MIN_ROAD = 4;
    private static final float PER_GUI = 2f;

    private static final byte LOCKED = 0;
    private static final byte OPEN = 1;
    private static final byte DONE = 2;
    /** Chapter states: not reached, being done, all main quests done. */
    private static final byte CH_LOCKED = 0;
    private static final byte CH_CURRENT = 1;
    private static final byte CH_DONE = 2;

    private final Camera camera = new Camera();
    @Nullable
    private QuestBook book;
    @Nullable
    private MapBook map;
    @Nullable
    private CanvasLayout layout;
    private Function<ResourceLocation, QuestStatus> status = id -> QuestStatus.LOCKED;
    private IntSupplier progressVersion = () -> 0;
    private int seenProgress = Integer.MIN_VALUE;
    private Listener listener = new Listener() {
    };
    @Nullable
    private DoubleSupplier loading;
    @Nullable
    private Runnable openList;

    private boolean showSide = true;
    private boolean showSideLinks = true;
    private int here = -1;
    private int selected = -1;
    private int[] route = new int[0];
    private final Set<Long> routeEdges = new HashSet<>();
    private final Set<Integer> routeNodes = new HashSet<>();
    private int openChapter = -1;
    private int focusEpoch = 0;
    private boolean minimap = true;
    private Level minimapFrom = Level.CHAPTER;
    private float insetTop = 44;
    private float insetBottom = 12;
    private float insetSide = 14;
    private boolean fitted;

    // per progress
    private byte[] state = new byte[0];
    private byte[] chapterState = new byte[0];
    private int[] mainCount = new int[0];
    /** "Все / Основные": the counts and the finished chapters take side quests in too. */
    private boolean countAll;
    private int[] mainDone = new int[0];
    private int[] sideCount = new int[0];
    private int[] sideDone = new int[0];
    private final Map<Integer, ItemStack> icons = new HashMap<>();
    /** Item against tile in the last frame, for the drift scenario ({@link #iconOffsetsForTest}). */
    private float[] iconOffsets = new float[0];
    private static boolean oldIconSnap;
    @Nullable
    private NodeGrid grid;

    // the frame
    private float vx;
    private float vy;
    private float vw = 1;
    private float vh = 1;
    private int mouseX;
    private int mouseY;
    private int hoverQuest = -1;
    private int hoverChapter = -1;
    private int hoverEpoch = 0;
    private Target lastHover = Target.NONE;
    private boolean pressed;
    private double pressX;
    private double pressY;
    private boolean dragged;
    private long lastClickAt;
    private int lastClickChapter = -1;
    private Stats stats = new Stats(0, 0, 0, 0, 0, 0);
    private final List<float[]> labelBoxes = new ArrayList<>();
    private final Map<Integer, float[]> armPlaces = new HashMap<>();
    private double armPlacesScale = -1;

    // reused buffers
    private float[] bufLocked = new float[256];
    private float[] bufLockedTrunk = new float[256];
    private float[] bufDone = new float[256];
    private float[] bufDoneTrunk = new float[256];
    private float[] bufOpen = new float[256];
    private float[] bufRoute = new float[64];

    // ================= the screen's side =================

    /** Gives the canvas a book and its layout; a new layout keeps the view where the old one showed the same chapter. */
    public void setLayout(QuestBook newBook, MapBook newMap, CanvasLayout newLayout) {
        boolean same = layout != null && newLayout != null && layout.hash.equals(newLayout.hash) && layout.form == newLayout.form;
        book = newBook;
        map = newMap;
        layout = newLayout;
        icons.clear();
        seenProgress = Integer.MIN_VALUE;
        armPlacesScale = -1;
        grid = newLayout == null ? null : new NodeGrid(newLayout);
        if (!same) {
            // indices of another book or form mean nothing here
            fitted = false;
            openChapter = -1;
            focusEpoch = 0;
            selected = -1;
            here = -1;
            setRoute(List.of());
        }
    }

    public void setStatus(Function<ResourceLocation, QuestStatus> newStatus, IntSupplier version) {
        status = newStatus;
        progressVersion = version;
        seenProgress = Integer.MIN_VALUE;
    }

    public void setListener(Listener newListener) {
        listener = newListener;
    }

    /**
     * Shows "Laying out the map" (picture 49) while {@code progress} is set and there is no layout yet; {@code openList} is the
     * button that opens the list meanwhile, or null for none.
     */
    public void setLoading(@Nullable DoubleSupplier progress, @Nullable Runnable openListAction) {
        loading = progress;
        openList = openListAction;
    }

    /** Side quests shown or hidden ("Побочные квесты" in the legend). */
    public void setShowSide(boolean show) {
        showSide = show;
    }

    public boolean showSide() {
        return showSide;
    }

    /**
     * Side links shown or hidden ("Побочные связи" in the legend): the dashed roads between chapters and the roads to and between
     * side quests. A way lit to a goal shows whatever it takes.
     */
    public void setShowSideLinks(boolean show) {
        showSideLinks = show;
    }

    public boolean showSideLinks() {
        return showSideLinks;
    }

    /** The quest the player is at: "you are here". */
    public void setHere(@Nullable ResourceLocation quest) {
        here = index(quest);
    }

    public void select(@Nullable ResourceLocation quest) {
        selected = index(quest);
    }

    /** The path to a goal, first step first; lit on the map. */
    public void setRoute(List<ResourceLocation> quests) {
        List<Integer> list = new ArrayList<>();
        for (ResourceLocation id : quests) {
            int q = index(id);
            if (q >= 0) {
                list.add(q);
            }
        }
        route = list.stream().mapToInt(Integer::intValue).toArray();
        routeEdges.clear();
        routeNodes.clear();
        for (int i = 0; i < route.length; i++) {
            routeNodes.add(route[i]);
            if (i + 1 < route.length) {
                routeEdges.add(edgeKey(route[i], route[i + 1]));
                routeEdges.add(edgeKey(route[i + 1], route[i]));
            }
        }
    }

    /** The chapter whose card is open: its roads to other chapters show. */
    public void setOpenChapter(@Nullable ResourceLocation chapter) {
        openChapter = map == null || chapter == null ? -1 : map.chapter(chapter);
    }

    /** The epoch in focus: roads between its chapters show. 0 for none. */
    public void setFocusEpoch(int epoch) {
        focusEpoch = epoch;
    }

    /** The level from which the minimap shows: the chapter by default, the epoch as picture 15b has it. */
    public void setMinimapFrom(Level level) {
        minimapFrom = level;
    }

    /** What the pointer was over in the last frame. */
    public Target hovered() {
        return lastHover;
    }

    /** A chapter's box on screen in mockup pixels {@code {x, y, w, h}}, or null when it is not on the canvas. */
    @Nullable
    public float[] chapterOnScreen(ResourceLocation chapter) {
        if (layout == null || map == null || map.chapter(chapter) < 0) {
            return null;
        }
        CanvasLayout.Region region = layout.regions[map.chapter(chapter)];
        float[] a = screenOf(region.minX(), region.minY());
        float[] b = screenOf(region.maxX(), region.maxY());
        return new float[] {a[0], a[1], b[0] - a[0], b[1] - a[1]};
    }

    /** A quest's centre on screen in mockup pixels, or null when it is not on the canvas. */
    @Nullable
    public float[] questOnScreen(ResourceLocation quest) {
        int q = index(quest);
        return q < 0 ? null : screenOf(layout.x[q], layout.y[q]);
    }

    /** The width of the rectangle of the last frame, in mockup pixels. */
    public float viewWidth() {
        return vw;
    }

    /**
     * For the autotest: where on screen a point of a chapter's area lies, {@code t} of the way along its band's centre line (or across
     * its box), in mockup pixels; null when the chapter is not on the canvas.
     */
    @Nullable
    public float[] chapterPointForTest(ResourceLocation chapter, float t) {
        if (layout == null || map == null || map.chapter(chapter) < 0) {
            return null;
        }
        CanvasLayout.Region region = layout.regions[map.chapter(chapter)];
        float[] pts = region.pts();
        if (region.kind() != CanvasLayout.RegionKind.BAND) {
            return screenOf(region.minX() + (region.maxX() - region.minX()) * t, region.centerY());
        }
        double total = 0;
        for (int i = 0; i + 3 < pts.length; i += 2) {
            total += Math.hypot(pts[i + 2] - pts[i], pts[i + 3] - pts[i + 1]);
        }
        double want = total * t;
        for (int i = 0; i + 3 < pts.length; i += 2) {
            double len = Math.hypot(pts[i + 2] - pts[i], pts[i + 3] - pts[i + 1]);
            if (want <= len || i + 4 >= pts.length) {
                double k = len == 0 ? 0 : Math.min(1, want / len);
                return screenOf(pts[i] + (pts[i + 2] - pts[i]) * k, pts[i + 1] + (pts[i + 3] - pts[i + 1]) * k);
            }
            want -= len;
        }
        return screenOf(pts[0], pts[1]);
    }

    /** For the autotest: half the side of the node as drawn now (a tile's, or a dot's radius), in mockup pixels; -1 when absent. */
    public float nodeHalfForTest(ResourceLocation quest) {
        int q = index(quest);
        if (q < 0) {
            return -1;
        }
        boolean hub = layout.hub(q);
        boolean big = layout.milestone(q);
        if (levelOf(camera.scale) != Level.BOOK && tilePx(hub || big, false) >= MIN_TILE) {
            return Math.max(MIN_TILE, tilePx(hub || big, big)) / 2;
        }
        return nodeRadiusPx(hub, big);
    }

    public void setMinimap(boolean show) {
        minimap = show;
    }

    /** Room the screen's tools take over the canvas, in mockup pixels: flights keep what they frame clear of it. */
    public void setInsets(float top, float side, float bottom) {
        insetTop = top;
        insetSide = side;
        insetBottom = bottom;
    }

    public void flyToBook() {
        if (layout == null) {
            return;
        }
        focusEpoch = 0;
        openChapter = -1;
        frame(layout.minX, layout.minY, layout.maxX, layout.maxY, false, 0, EPOCH_LABEL_ROOM);
    }

    public void flyToEpoch(int epoch) {
        if (layout == null || map == null) {
            return;
        }
        float[] box = layout.epochBounds(map, epoch);
        if (box != null) {
            focusEpoch = epoch;
            openChapter = -1;
            frame(box[0], box[1], box[2], box[3], false, 0, EPOCH_LABEL_ROOM);
        }
    }

    public void flyToChapter(ResourceLocation chapter) {
        flyToChapter(chapter, 0);
    }

    /**
     * Flies to the chapter as it will be framed in a rectangle {@code width} wide from the same left edge: the chapter's card opens
     * on arrival and narrows the canvas, and a flight aimed at the narrower canvas from the start arrives without a jerk. 0 for the
     * rectangle as it is.
     */
    public void flyToChapter(ResourceLocation chapter, float width) {
        if (layout == null || map == null) {
            return;
        }
        int c = map.chapter(chapter);
        if (c < 0) {
            return;
        }
        CanvasLayout.Region region = layout.regions[c];
        openChapter = c;
        focusEpoch = map.chapters().get(c).epoch();
        planWidth = width;
        frame(region.minX(), region.minY(), region.maxX(), region.maxY(), false, CHAPTER_FROM);
        planWidth = 0;
    }

    /** The width {@link #frame} frames for, when not the rectangle's own. */
    private float planWidth;

    /** A close up of the quest, framed with the quest it hangs on so that the road between them shows. */
    public void flyToQuest(ResourceLocation quest) {
        if (layout == null || map == null) {
            return;
        }
        int q = map.quest(quest);
        if (q < 0) {
            return;
        }
        double x = layout.x[q];
        double y = layout.y[q];
        int[] requires = map.quests().get(q).requires();
        if (requires.length > 0 && map.quests().get(requires[0]).chapter() == map.quests().get(q).chapter()) {
            x = (x * 2 + layout.x[requires[0]]) / 3;
            y = (y * 2 + layout.y[requires[0]]) / 3;
        }
        openChapter = map.quests().get(q).chapter();
        double s = Math.max(CLOSE_FROM + 0.3, camera.targetScale());
        camera.fly(x, y + (insetTop - insetBottom) / 2 / s * -1, s);
    }

    /** "−" and "+": zoom about the middle of the view. */
    public void zoomBy(double factor) {
        camera.fly(camera.cx, camera.cy, camera.targetScale() * factor);
    }

    public Level level() {
        return levelOf(camera.scale);
    }

    static Level levelOf(double s) {
        return s >= CLOSE_FROM ? Level.CLOSE : s >= CHAPTER_FROM ? Level.CHAPTER : s >= EPOCH_FROM ? Level.EPOCH : Level.BOOK;
    }

    public double scale() {
        return camera.scale;
    }

    /**
     * Tells the canvas the rectangle it is about to be drawn in, so that a flight planned before the next frame frames for it: the
     * map's panel opening narrows the canvas.
     */
    public void setViewport(int x, int y, int w, int h) {
        viewport(x, y, w, h);
    }

    /**
     * The rectangle of this frame. When only its width changes, as the map's panel opens or closes, the picture stays where it is:
     * the view centre moves with the middle of the rectangle, through a flight too, so nothing jumps sideways.
     */
    private void viewport(int x, int y, int w, int h) {
        float nw = Math.max(1, w);
        float nh = Math.max(1, h);
        if (layout != null && fitted && x == vx && y == vy && nh == vh && nw != vw) {
            camera.shiftScreen((nw - vw) / 2, 0);
        }
        vx = x;
        vy = y;
        vw = nw;
        vh = nh;
    }

    /** Where a canvas point is in the rectangle now, in mockup pixels. */
    public float[] screenOf(double wx, double wy) {
        return new float[] {(float) (vx + vw / 2 + (wx - camera.cx) * camera.scale), (float) (vy + vh / 2 + (wy - camera.cy) * camera.scale)};
    }

    public boolean flying() {
        return camera.flying();
    }

    public Stats stats() {
        return stats;
    }

    /** Pans the view by mockup pixels, as a drag does; for the drift scenario. */
    public void panForTest(double dx, double dy) {
        camera.pan(dx, dy);
    }

    /**
     * Where each tile's item stood against its tile in the last frame: the item's centre minus the tile's centre in screen pixels,
     * x and y per quest index, NaN for a quest without an item on screen. For the drift scenario: the numbers must not change
     * while the map moves.
     */
    public float[] iconOffsetsForTest() {
        return iconOffsets.clone();
    }

    /** Draws items as 0.2.2 did (rounded to whole GUI units against a tile on fractional pixels): the "before" of the drift scenario. */
    public static void oldIconSnapForTest(boolean old) {
        oldIconSnap = old;
    }

    @Nullable
    public CanvasLayout layout() {
        return layout;
    }

    // ================= input =================

    public boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my) || layout == null) {
            return false;
        }
        pressed = true;
        dragged = false;
        pressX = mx;
        pressY = my;
        return true;
    }

    public boolean mouseDragged(double mx, double my, double dx, double dy) {
        if (!pressed) {
            return false;
        }
        if (!dragged && Math.hypot(mx - pressX, my - pressY) > 3) {
            dragged = true;
        }
        if (dragged) {
            camera.pan(dx, dy);
        }
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (!pressed) {
            return false;
        }
        pressed = false;
        if (!dragged) {
            Target target = hit(mx, my);
            long now = System.currentTimeMillis();
            if (target.kind() == Kind.CHAPTER && button == 0) {
                int c = map.chapter(target.chapter());
                if (c == lastClickChapter && now - lastClickAt < 350) {
                    flyToChapter(target.chapter());
                }
                lastClickChapter = c;
                lastClickAt = now;
            }
            if (target.kind() == Kind.EPOCH && button == 0) {
                flyToEpoch(target.epoch());
            }
            listener.clicked(target, button);
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!inside(mx, my) || layout == null) {
            return false;
        }
        camera.zoomAt(Math.pow(1.2, amount), mx - (vx + vw / 2), my - (vy + vh / 2));
        return true;
    }

    private boolean inside(double mx, double my) {
        return mx >= vx && mx < vx + vw && my >= vy && my < vy + vh;
    }

    /** What lies under a point of the rectangle: a quest first, then a chapter's area. */
    public Target hit(double mx, double my) {
        if (layout == null || map == null || !inside(mx, my)) {
            return Target.NONE;
        }
        double s = camera.scale;
        double wx = camera.cx + (mx - (vx + vw / 2)) / s;
        double wy = camera.cy + (my - (vy + vh / 2)) / s;
        for (float[] box : epochBoxes) {
            if (mx >= box[0] && mx < box[2] && my >= box[1] && my < box[3]) {
                return new Target(Kind.EPOCH, null, null, (int) box[4]);
            }
        }
        if (grid != null) {
            int q = grid.under(wx, wy, MAX_HIT / s, showSide ? null : this::side, this::reach);
            if (q >= 0) {
                MapBook.Q quest = map.quests().get(q);
                return new Target(Kind.QUEST, quest.id(), map.chapters().get(quest.chapter()).id(), map.chapters().get(quest.chapter()).epoch());
            }
        }
        for (int c = 0; c < layout.regions.length; c++) {
            if (layout.regions[c].contains((float) wx, (float) wy)) {
                MapBook.Chap chapter = map.chapters().get(c);
                return new Target(Kind.CHAPTER, null, chapter.id(), chapter.epoch());
            }
        }
        return Target.NONE;
    }

    // ================= drawing =================

    private final List<float[]> epochBoxes = new ArrayList<>();

    /**
     * Draws the canvas into the rectangle; all in mockup pixels.
     */
    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        long start = System.nanoTime();
        Theme t = canvasTheme();
        viewport(x, y, w, h);
        mouseX = mx;
        mouseY = my;
        Draw.scissor(g, Math.floorDiv(x, 2), Math.floorDiv(y, 2), Math.floorDiv(x + w + 1, 2), Math.floorDiv(y + h + 1, 2));
        fill(g, x, y, w, h, 0, ThemeManager.get().c("map.bg"));
        if (layout == null || map == null || book == null) {
            if (loading != null) {
                drawLoading(g, t);
            }
            g.disableScissor();
            stats = new Stats(0, 0, 0, 0, 0, System.nanoTime() - start);
            return;
        }
        camera.minScale = fitScale(layout.minX, layout.minY, layout.maxX, layout.maxY) * 0.7;
        if (!fitted) {
            fitted = true;
            camera.maxScale = MAX_SCALE;
            frame(layout.minX, layout.minY, layout.maxX, layout.maxY, true, 0, EPOCH_LABEL_ROOM);
        }
        camera.update();
        refreshProgress();
        updateHover();
        labelBoxes.clear();
        epochBoxes.clear();
        double s = camera.scale;
        Level level = levelOf(s);
        Frame f = new Frame(s);
        int[] counts = new int[4];
        if (level != Level.CLOSE) {
            drawRegions(g, t, f);
        }
        if (level == Level.BOOK) {
            drawTurns(g, t, f);
        }
        if (level != Level.CLOSE) {
            drawMarkLines(g, t, f);
        }
        if (s > 0.3 && showSide) {
            counts[3] = drawOrbits(g, t, f);
        }
        counts[1] = drawRoads(g, t, f, level);
        int[] drawn = drawNodes(g, t, f, level);
        counts[0] = drawn[0];
        counts[2] = drawn[1];
        int labels = drawLabels(g, t, f, level);
        if (minimap && level.ordinal() >= minimapFrom.ordinal()) {
            drawMinimap(g, t);
        }
        g.disableScissor();
        stats = new Stats(counts[0], counts[1], counts[2], counts[3], labels, System.nanoTime() - start);
    }

    /**
     * The theme the canvas draws its nodes, roads and labels with. The canvas is dark in every theme (plan, decision 18): a light
     * theme with a bevel (vanilla) keeps its own tools around it, but what lies on the canvas takes slate's tokens, as its
     * mockup does (picture 45b).
     */
    static Theme canvasTheme() {
        Theme current = ThemeManager.get();
        if (!current.beveled()) {
            return current;
        }
        for (Theme theme : ThemeManager.all()) {
            if ("ryscore:slate".equals(theme.id)) {
                return theme;
            }
        }
        return current;
    }

    /**
     * World to mockup pixels for one frame. The offset lies on whole screen pixels, so a drag moves everything on the canvas by whole
     * pixels together and the fraction of every point stays the same from frame to frame: what is rounded (items) does not flicker.
     */
    private final class Frame {
        final double s;
        final double ox;
        final double oy;
        final double minX;
        final double minY;
        final double maxX;
        final double maxY;
        /** Screen pixels in one mockup pixel. */
        final double k;

        Frame(double s) {
            this.s = s;
            this.k = screenPerPx();
            double rawX = vx + vw / 2 - camera.cx * s;
            double rawY = vy + vh / 2 - camera.cy * s;
            this.ox = oldIconSnap ? rawX : Math.round(rawX * k) / k;
            this.oy = oldIconSnap ? rawY : Math.round(rawY * k) / k;
            double m = 40 / s;
            this.minX = camera.cx - vw / 2 / s - m;
            this.maxX = camera.cx + vw / 2 / s + m;
            this.minY = camera.cy - vh / 2 / s - m;
            this.maxY = camera.cy + vh / 2 / s + m;
        }

        float px(double wx) {
            return (float) (wx * s + ox);
        }

        /** Whole GUI units, as items were placed in 0.2.2: the drift scenario's "before". */
        float oldSnap(double v) {
            return Math.round(v / PER_GUI) * PER_GUI;
        }

        float py(double wy) {
            return (float) (wy * s + oy);
        }

        /** A coordinate in mockup pixels put on the nearest whole screen pixel. */
        float snap(double v) {
            return (float) (Math.round(v * k) / k);
        }

        boolean visible(double x, double y, double r) {
            return x + r >= minX && x - r <= maxX && y + r >= minY && y - r <= maxY;
        }

        boolean visibleBox(double x0, double y0, double x1, double y1) {
            return x1 >= minX && x0 <= maxX && y1 >= minY && y0 <= maxY;
        }
    }

    private void frame(double x0, double y0, double x1, double y1, boolean jump, double minScale) {
        frame(x0, y0, x1, y1, jump, minScale, 0);
    }

    /**
     * @param labelRoom mockup pixels kept free above what is framed: the plates of the epochs stand over their chapters, and on
     *                  the whole book the first of them would slip under the tools ("I Механика" under "Карта | Список")
     */
    private void frame(double x0, double y0, double x1, double y1, boolean jump, double minScale, float labelRoom) {
        float top = insetTop + labelRoom;
        double w = Math.max(1, x1 - x0);
        double h = Math.max(1, y1 - y0);
        float planW = planWidth > 0 ? planWidth : vw;
        double s = Math.max(minScale, Math.min((planW - 2 * insetSide) / w, (vh - top - insetBottom) / h));
        s = Math.min(s, FRAME_MAX);
        // framed in the middle of the planned rectangle, which shares the left edge of this one
        double cx = (x0 + x1) / 2 + (vw - planW) / 2 / s;
        // the tools take the top of the view: what is framed sits below them
        double cy = (y0 + y1) / 2 - (top - insetBottom) / 2 / s;
        if (jump) {
            camera.jump(cx, cy, s);
        } else {
            camera.fly(cx, cy, s);
        }
    }

    private double fitScale(double x0, double y0, double x1, double y1) {
        double w = Math.max(1, x1 - x0);
        double h = Math.max(1, y1 - y0);
        return Math.min((vw - 2 * insetSide) / w, (vh - insetTop - insetBottom) / h);
    }

    // --- progress ---

    private void refreshProgress() {
        int version = progressVersion.getAsInt();
        if (version == seenProgress && state.length == map.quests().size()) {
            return;
        }
        seenProgress = version;
        int n = map.quests().size();
        int nc = map.chapters().size();
        state = new byte[n];
        mainCount = new int[nc];
        mainDone = new int[nc];
        sideCount = new int[nc];
        sideDone = new int[nc];
        boolean[] started = new boolean[nc];
        boolean[] anyOpen = new boolean[nc];
        for (MapBook.Q quest : map.quests()) {
            QuestStatus st = status.apply(quest.id());
            byte code = st == QuestStatus.COMPLETED || st == QuestStatus.CLAIMED ? DONE : st == QuestStatus.LOCKED ? LOCKED : OPEN;
            state[quest.index()] = code;
            int c = quest.chapter();
            if (quest.optional()) {
                sideCount[c]++;
                if (code == DONE) {
                    sideDone[c]++;
                }
            } else {
                mainCount[c]++;
                if (code == DONE) {
                    mainDone[c]++;
                }
            }
            if (code == DONE) {
                started[c] = true;
            }
            if (code == OPEN) {
                anyOpen[c] = true;
            }
        }
        chapterState = new byte[nc];
        for (int c = 0; c < nc; c++) {
            chapterState[c] = counted(c) > 0 && countedDone(c) == counted(c) ? CH_DONE : started[c] || anyOpen[c] ? CH_CURRENT : CH_LOCKED;
        }
        // a galaxy: nearly everything is open in such a book, so "can do" lights only in chapters already begun
        if (layout.form == MapForm.GALAXY) {
            for (MapBook.Q quest : map.quests()) {
                if (state[quest.index()] == OPEN && !started[quest.chapter()]) {
                    state[quest.index()] = LOCKED;
                }
            }
        }
    }

    /** Counts of side quests too, or main ones only, as the sidebar's "Все / Основные" says. */
    public void setCountAll(boolean all) {
        if (all != countAll) {
            countAll = all;
            seenProgress = Integer.MIN_VALUE;
        }
    }

    private int counted(int chapter) {
        return mainCount[chapter] + (countAll ? sideCount[chapter] : 0);
    }

    private int countedDone(int chapter) {
        return mainDone[chapter] + (countAll ? sideDone[chapter] : 0);
    }

    private boolean side(int quest) {
        return map.quests().get(quest).optional();
    }

    private void updateHover() {
        hoverQuest = -1;
        hoverChapter = -1;
        hoverEpoch = 0;
        Target target = pressed && dragged ? Target.NONE : hit(mouseX, mouseY);
        if (target.kind() == Kind.QUEST) {
            hoverQuest = map.quest(target.quest());
            hoverChapter = map.chapter(target.chapter());
        } else if (target.kind() == Kind.CHAPTER) {
            hoverChapter = map.chapter(target.chapter());
        } else if (target.kind() == Kind.EPOCH) {
            hoverEpoch = target.epoch();
        }
        if (!target.equals(lastHover)) {
            lastHover = target;
            listener.hovered(target);
        }
    }

    // --- regions, turns, marks, orbits ---

    private void drawRegions(GuiGraphics g, Theme t, Frame f) {
        int open = t.c("map.node.open");
        int done = t.c("map.node.done");
        int bg = t.c("map.bg");
        for (int c = 0; c < layout.regions.length; c++) {
            CanvasLayout.Region region = layout.regions[c];
            if (!f.visibleBox(region.minX(), region.minY(), region.maxX(), region.maxY())) {
                continue;
            }
            boolean hover = c == hoverChapter;
            boolean on = c == openChapter;
            byte st = chapterState[c];
            switch (region.kind()) {
                case RECT -> {
                    float x = f.px(region.pts()[0]);
                    float y = f.py(region.pts()[1]);
                    float w = (float) (region.pts()[2] * f.s);
                    float h = (float) (region.pts()[3] * f.s);
                    float r = (float) Math.min(16 * f.s, 22);
                    int fillColor = hover ? Draw.alpha(open, 0.055f) : on ? Draw.alpha(open, 0.04f) : st == CH_DONE ? Draw.alpha(done, 0.035f) : st == CH_CURRENT ? Draw.alpha(open, 0.03f) : Draw.alpha(open, 0.016f);
                    int border = hover ? t.c("text.muted") : on ? t.c("text.dim") : st == CH_DONE ? Draw.alpha(done, 0.10f) : st == CH_CURRENT ? Draw.alpha(open, 0.09f) : Draw.alpha(open, 0.035f);
                    fill(g, x, y, w, h, r, fillColor);
                    border(g, x, y, w, h, r, border, hover || on ? 1.2f : 1f);
                }
                case DISC -> {
                    float cx = f.px(region.pts()[0]);
                    float cy = f.py(region.pts()[1]);
                    float r = (float) (region.pts()[2] * f.s);
                    int fillColor = hover ? Draw.alpha(open, 0.055f) : on ? Draw.alpha(open, 0.04f) : st == CH_DONE ? Draw.alpha(done, 0.035f) : st == CH_CURRENT ? Draw.alpha(open, 0.03f) : Draw.alpha(open, 0.016f);
                    int border = hover ? t.c("text.muted") : on ? t.c("text.dim") : st == CH_DONE ? Draw.alpha(done, 0.10f) : st == CH_CURRENT ? Draw.alpha(open, 0.09f) : Draw.alpha(open, 0.035f);
                    Draw.disc(g, cx / PER_GUI, cy / PER_GUI, r / PER_GUI, fillColor);
                    Draw.ring(g, cx / PER_GUI, cy / PER_GUI, r / PER_GUI, (hover || on ? 1.2f : 1f) / PER_GUI, border);
                }
                case BAND -> {
                    float[] pts = region.pts();
                    int n = pts.length / 2;
                    float[] xy = new float[pts.length];
                    for (int i = 0; i < n; i++) {
                        xy[2 * i] = f.px(pts[2 * i]) / PER_GUI;
                        xy[2 * i + 1] = f.py(pts[2 * i + 1]) / PER_GUI;
                    }
                    float width = (float) (region.width() * f.s) / PER_GUI;
                    if (hover || on) {
                        int edge = hover ? t.c("text.muted") : t.c("text.dim");
                        Draw.polyline(g, xy, n, false, width + 2.4f / PER_GUI, edge);
                        roundJoins(g, xy, n, (width + 2.4f / PER_GUI) / 2, edge);
                    }
                    int color = hover || on ? t.c("card.bg") : st == CH_DONE ? Draw.mix(bg, done, 0.05f) : st == CH_CURRENT ? Draw.mix(bg, open, 0.045f) : Draw.mix(bg, open, 0.02f);
                    Draw.polyline(g, xy, n, false, width, color);
                    roundJoins(g, xy, n, width / 2, color);
                }
            }
        }
    }

    /**
     * Fills the wedges a wide band leaves open where its line turns sharply: in the curl round the middle of the spiral the band is
     * far wider than the turn and its pieces fan out. A plain spiral turns a degree a piece and needs none.
     */
    private static void roundJoins(GuiGraphics g, float[] xy, int n, float radius, int color) {
        for (int i = 1; i + 1 < n; i++) {
            float ax = xy[2 * i] - xy[2 * i - 2];
            float ay = xy[2 * i + 1] - xy[2 * i - 1];
            float bx = xy[2 * i + 2] - xy[2 * i];
            float by = xy[2 * i + 3] - xy[2 * i + 1];
            float la = (float) Math.hypot(ax, ay);
            float lb = (float) Math.hypot(bx, by);
            if (la == 0 || lb == 0) {
                continue;
            }
            float cos = (ax * bx + ay * by) / (la * lb);
            // a turn of more than about 6 degrees opens a wedge wider than a pixel on a band this wide
            if (cos < 0.995f && radius * (float) Math.sqrt(Math.max(0, 2 - 2 * cos)) > 0.5f) {
                Draw.disc(g, xy[2 * i], xy[2 * i + 1], radius, color);
            }
        }
    }

    private void drawTurns(GuiGraphics g, Theme t, Frame f) {
        int color = Draw.mix(t.c("scroll.thumb"), t.c("map.node.locked"), 0.35f);
        for (float[] turn : layout.turns) {
            int n = turn.length / 2;
            float[] xy = new float[turn.length];
            for (int i = 0; i < n; i++) {
                xy[2 * i] = f.px(turn[2 * i]) / PER_GUI;
                xy[2 * i + 1] = f.py(turn[2 * i + 1]) / PER_GUI;
            }
            Draw.polyline(g, xy, n, false, 2f / PER_GUI, color);
            // a chevron where the trunk comes into the next row
            float ex = xy[2 * n - 2];
            float ey = xy[2 * n - 1];
            float dx = ex - xy[2 * n - 4];
            float dy = ey - xy[2 * n - 3];
            float len = (float) Math.hypot(dx, dy);
            if (len > 0) {
                dx /= len;
                dy /= len;
                float a = 5f / PER_GUI;
                Draw.line(g, ex - dx * a - dy * a, ey - dy * a + dx * a, ex, ey, 2f / PER_GUI, color);
                Draw.line(g, ex - dx * a + dy * a, ey - dy * a - dx * a, ex, ey, 2f / PER_GUI, color);
            }
        }
    }

    private void drawMarkLines(GuiGraphics g, Theme t, Frame f) {
        int color = t.c("text.dim");
        for (CanvasLayout.Mark mark : layout.marks) {
            if (!f.visibleBox(Math.min(mark.x1(), mark.x2()), Math.min(mark.y1(), mark.y2()), Math.max(mark.x1(), mark.x2()), Math.max(mark.y1(), mark.y2()))) {
                continue;
            }
            Draw.line(g, f.px(mark.x1()) / PER_GUI, f.py(mark.y1()) / PER_GUI, f.px(mark.x2()) / PER_GUI, f.py(mark.y2()) / PER_GUI, 1.2f / PER_GUI, color);
        }
    }

    private int drawOrbits(GuiGraphics g, Theme t, Frame f) {
        int color = Draw.alpha(t.c("map.node.open"), 0.07f);
        int count = 0;
        float thickness = 1f / PER_GUI;
        for (CanvasLayout.Ring ring : layout.rings) {
            if (!f.visible(ring.x(), ring.y(), ring.outer())) {
                continue;
            }
            float cx = f.px(ring.x()) / PER_GUI;
            float cy = f.py(ring.y()) / PER_GUI;
            for (float o : ring.orbits()) {
                float r = (float) (o * f.s) / PER_GUI;
                if (r < 2) {
                    continue;
                }
                Draw.ring(g, cx, cy, r + thickness / 2, thickness, color);
                count++;
            }
        }
        return count;
    }

    // --- roads ---

    private int drawRoads(GuiGraphics g, Theme t, Frame f, Level level) {
        int nLocked = 0;
        int nLockedTrunk = 0;
        int nDone = 0;
        int nDoneTrunk = 0;
        int nOpen = 0;
        int nRoute = 0;
        int drawn = 0;
        float minLen = level == Level.BOOK ? MIN_ROAD : 0;
        float[] x = layout.x;
        float[] y = layout.y;
        List<MapBook.Edge> cross = new ArrayList<>();
        for (MapBook.Edge edge : map.edges()) {
            int a = edge.from();
            int b = edge.to();
            if (!showSide && (side(a) || side(b))) {
                continue;
            }
            boolean onRoute = routeEdges.contains(edgeKey(a, b));
            if (!showSideLinks && !onRoute && (edge.cross() || side(a) || side(b))) {
                continue;
            }
            if (edge.cross() && !onRoute) {
                cross.add(edge);
                continue;
            }
            if (!f.visibleBox(Math.min(x[a], x[b]), Math.min(y[a], y[b]), Math.max(x[a], x[b]), Math.max(y[a], y[b]))) {
                continue;
            }
            float x1 = f.px(x[a]) / PER_GUI;
            float y1 = f.py(y[a]) / PER_GUI;
            float x2 = f.px(x[b]) / PER_GUI;
            float y2 = f.py(y[b]) / PER_GUI;
            if (minLen > 0 && (Math.abs(x2 - x1) + Math.abs(y2 - y1)) * PER_GUI < minLen) {
                continue;
            }
            drawn++;
            if (onRoute) {
                bufRoute = put(bufRoute, nRoute++, x1, y1, x2, y2);
                continue;
            }
            boolean trunk = layout.hub(a) && layout.hub(b);
            byte sa = state[a];
            byte sb = state[b];
            if (sa == DONE && sb == DONE) {
                bufDone = put(bufDone, nDone++, x1, y1, x2, y2);
                if (trunk) {
                    bufDoneTrunk = put(bufDoneTrunk, nDoneTrunk++, x1, y1, x2, y2);
                }
            } else if (sa == DONE && sb == OPEN) {
                bufOpen = put(bufOpen, nOpen++, x1, y1, x2, y2);
            } else if (trunk) {
                bufLockedTrunk = put(bufLockedTrunk, nLockedTrunk++, x1, y1, x2, y2);
            } else {
                bufLocked = put(bufLocked, nLocked++, x1, y1, x2, y2);
            }
        }
        float sw = (float) Math.min(2.0, Math.max(0.7, 0.9 * f.s)) / PER_GUI;
        int done = t.c("map.node.done");
        int lockedColor = t.c("scroll.thumb");
        if (nLocked > 0) {
            Draw.lines(g, bufLocked, nLocked, sw, lockedColor);
        }
        if (nLockedTrunk > 0) {
            Draw.lines(g, bufLockedTrunk, nLockedTrunk, sw * 1.7f, Draw.mix(lockedColor, t.c("map.node.locked"), 0.35f));
        }
        drawn += drawCross(g, t, f, cross, sw);
        if (nDone > 0) {
            Draw.lines(g, bufDone, nDone, sw * 3.2f, Draw.alpha(done, 0.13f));
            Draw.lines(g, bufDone, nDone, sw, Draw.alpha(done, 0.85f));
        }
        if (nDoneTrunk > 0) {
            Draw.lines(g, bufDoneTrunk, nDoneTrunk, sw * 1.7f, done);
        }
        if (nOpen > 0) {
            Draw.lines(g, bufOpen, nOpen, sw, Draw.alpha(t.c("text.secondary"), 0.8f));
        }
        if (nRoute > 0) {
            int hi = t.c("text.primary");
            Draw.lines(g, bufRoute, nRoute, 7f / PER_GUI, Draw.alpha(hi, 0.25f));
            Draw.lines(g, bufRoute, nRoute, 2.6f / PER_GUI, hi);
        }
        return drawn;
    }

    /**
     * Roads between chapters: only those of the hovered chapter and the open one, and on the epoch level those between the
     * chapters of the epoch in focus. Dashed, with a slight bow so that they read as links, not as roads inside a chapter.
     */
    private int drawCross(GuiGraphics g, Theme t, Frame f, List<MapBook.Edge> cross, float sw) {
        int drawn = 0;
        Level level = levelOf(f.s);
        for (MapBook.Edge edge : cross) {
            int a = edge.from();
            int b = edge.to();
            int ca = map.quests().get(a).chapter();
            int cb = map.quests().get(b).chapter();
            boolean hovered = hoverChapter >= 0 && (ca == hoverChapter || cb == hoverChapter);
            boolean open = openChapter >= 0 && (ca == openChapter || cb == openChapter);
            boolean epoch = level == Level.EPOCH && focusEpoch > 0 && map.chapters().get(ca).epoch() == focusEpoch && map.chapters().get(cb).epoch() == focusEpoch;
            if (!hovered && !open && !epoch) {
                continue;
            }
            float x1 = f.px(layout.x[a]);
            float y1 = f.py(layout.y[a]);
            float x2 = f.px(layout.x[b]);
            float y2 = f.py(layout.y[b]);
            if (!f.visibleBox(Math.min(layout.x[a], layout.x[b]), Math.min(layout.y[a], layout.y[b]), Math.max(layout.x[a], layout.x[b]), Math.max(layout.y[a], layout.y[b]))) {
                continue;
            }
            int color = hovered ? Draw.alpha(t.c("text.secondary"), 0.55f) : state[a] == DONE && state[b] != LOCKED ? Draw.alpha(t.c("ok.border"), 0.7f)
                    : Draw.alpha(t.c("scroll.thumb"), 0.6f);
            // a bow of 12 % of the length to one side, drawn as two dashed halves
            float mx = (x1 + x2) / 2 - (y2 - y1) * 0.12f;
            float my = (y1 + y2) / 2 + (x2 - x1) * 0.12f;
            float dash = 4f / PER_GUI;
            float gap = 3f / PER_GUI;
            float w = Math.max(sw, 1f / PER_GUI);
            Draw.dashed(g, x1 / PER_GUI, y1 / PER_GUI, mx / PER_GUI, my / PER_GUI, w, dash, gap, color);
            float first = (float) Math.hypot(mx - x1, my - y1) / PER_GUI;
            Draw.dashed(g, mx / PER_GUI, my / PER_GUI, x2 / PER_GUI, y2 / PER_GUI, w, dash, gap, first % (dash + gap), color);
            drawn++;
        }
        return drawn;
    }

    private static float[] put(float[] buf, int index, float x1, float y1, float x2, float y2) {
        if (buf.length < (index + 1) * 4) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, (index + 1) * 4));
        }
        buf[index * 4] = x1;
        buf[index * 4 + 1] = y1;
        buf[index * 4 + 2] = x2;
        buf[index * 4 + 3] = y2;
        return buf;
    }

    private static long edgeKey(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    // --- nodes ---

    /** Spare room round a node that a click still takes, in mockup pixels. */
    private static final float HIT_SPARE = 3;
    /** The least reach of a node: a dot of the whole book is a pixel or two, a pointer is not that exact. */
    private static final float MIN_HIT = 7;
    /** The farthest any node reaches: half the largest tile, its milestone frame and the spare room. */
    private static final float TILE_MAX = 76;
    private static final float MAX_HIT = TILE_MAX / 2 + 10 + HIT_SPARE + 1;

    /**
     * How far a point lies from a node in the node's own terms, under 1 on it: a tile takes its whole square and a little round
     * it, a dot its circle and a little more, never less than {@value #MIN_HIT} pixels. As {@link #drawNodes} draws them.
     */
    private double reach(int q, double dx, double dy) {
        double s = camera.scale;
        Level level = levelOf(s);
        boolean hub = layout.hub(q);
        boolean big = layout.milestone(q);
        boolean tile = level != Level.BOOK && tilePx(hub || big, false) >= MIN_TILE;
        if (tile) {
            float half = Math.max(MIN_TILE, tilePx(hub || big, big)) / 2 + (big ? 4.5f : 0) + HIT_SPARE;
            return Math.max(Math.abs(dx), Math.abs(dy)) * s / Math.max(MIN_HIT, half);
        }
        float radius = nodeRadiusPx(hub, big) + (big ? 3 : 0) + HIT_SPARE + 1;
        return Math.hypot(dx, dy) * s / Math.max(MIN_HIT, radius);
    }

    /** The radius of a dot in mockup pixels. */
    private float nodeRadiusPx(boolean hub, boolean milestone) {
        double s = camera.scale;
        boolean tree = layout != null && layout.form == MapForm.GALAXY || (map != null && map.optionalCount() * 4 < map.quests().size());
        double world;
        double floor;
        double cap;
        if (tree) {
            world = milestone ? 14.5 : hub ? 9.5 : 5.75;
            floor = milestone ? 2.9 : hub ? 1.9 : 1.15;
            cap = milestone ? 9.9 : hub ? 6.5 : 3.9;
        } else {
            world = milestone ? 8.3 : hub ? 5.5 : 3.0;
            floor = milestone ? 3.6 : hub ? 2.4 : 1.3;
            cap = milestone ? 7.5 : hub ? 5.2 : 3.2;
        }
        return (float) Math.max(floor, Math.min(cap, world * s));
    }

    /** The side of a tile in mockup pixels; under {@value #MIN_TILE} the node is a dot. */
    private float tilePx(boolean hub, boolean milestone) {
        double s = camera.scale;
        double world = milestone ? 12 : hub ? 9.5 : 6.8;
        return (float) Math.min(milestone ? TILE_MAX : hub ? 66 : 48, world * s);
    }

    private int[] drawNodes(GuiGraphics g, Theme t, Frame f, Level level) {
        int n = map.quests().size();
        int drawn = 0;
        int iconCount = 0;
        int done = t.c("map.node.done");
        int open = t.c("map.node.open");
        int locked = t.c("map.node.locked");
        int bg = t.c("map.bg");
        int slot = t.c("slot.bg");
        int slotBorder = t.c("slot.border");
        int thumb = t.c("scroll.thumb");
        int hi = t.c("text.primary");
        int secondary = t.c("text.secondary");
        // the plate under an item: brighter for the main way, a shade darker for side quests (from the canvas tokens of the theme)
        int plate = t.c("map.label.bg") | 0xFF000000;
        int backMain = Draw.mix(plate, open, 0.15f);
        int backSide = Draw.mix(plate, open, 0.075f);
        int gold = t.c("medal.gold");
        float rMain = nodeRadiusPx(true, false);
        float rSide = nodeRadiusPx(false, false);
        float rBig = nodeRadiusPx(true, true);
        boolean tiles = level != Level.BOOK && tilePx(false, false) >= MIN_TILE;
        boolean mainTiles = level != Level.BOOK && tilePx(true, false) >= MIN_TILE;
        List<Integer> tileList = new ArrayList<>();
        if (iconOffsets.length != 2 * n) {
            iconOffsets = new float[2 * n];
        }
        java.util.Arrays.fill(iconOffsets, Float.NaN);
        // dots first, in one run: rings, glows and fills
        for (int q = 0; q < n; q++) {
            boolean isSide = side(q);
            if (!showSide && isSide) {
                continue;
            }
            float wx = layout.x[q];
            float wy = layout.y[q];
            if (!f.visible(wx, wy, 0)) {
                continue;
            }
            drawn++;
            boolean hub = layout.hub(q);
            boolean big = layout.milestone(q);
            if ((hub || big) ? mainTiles : tiles) {
                tileList.add(q);
                continue;
            }
            float px = f.px(wx) / PER_GUI;
            float py = f.py(wy) / PER_GUI;
            float r = (big ? rBig : hub ? rMain : rSide) / PER_GUI;
            byte st = state[q];
            if (layout.finale(q)) {
                // the book's finale in the middle of the spiral: a gold halo
                dot(g, px, py, r + 9f / PER_GUI, Draw.alpha(gold, 0.16f));
                dot(g, px, py, r + 6f / PER_GUI, Draw.alpha(gold, 0.9f));
                dot(g, px, py, r + 4.6f / PER_GUI, bg);
            }
            if (big) {
                int ring = st == DONE ? done : st == OPEN ? open : locked;
                dot(g, px, py, r + 3f / PER_GUI, ring);
                dot(g, px, py, r + 1.8f / PER_GUI, bg);
            }
            if (st == DONE && hub) {
                dot(g, px, py, r + 2.6f / PER_GUI, Draw.alpha(done, 0.16f));
            }
            if (routeNodes.contains(q)) {
                dot(g, px, py, r + 3.7f / PER_GUI, hi);
                dot(g, px, py, r + 2.3f / PER_GUI, bg);
            }
            if (q == selected || q == here) {
                dot(g, px, py, r + 5f / PER_GUI, hi);
                dot(g, px, py, r + 3f / PER_GUI, bg);
            }
            switch (st) {
                case DONE -> dot(g, px, py, r, hub || big ? done : Draw.mix(done, bg, 0.3f));
                case OPEN -> {
                    dot(g, px, py, r, hub || big ? open : secondary);
                    dot(g, px, py, r - (hub || big ? 1.4f : 1.1f) / PER_GUI, slot);
                }
                default -> {
                    if (hub || big) {
                        dot(g, px, py, r, locked);
                        dot(g, px, py, r - 1f / PER_GUI, slotBorder);
                    } else {
                        dot(g, px, py, r, thumb);
                    }
                }
            }
        }
        // tiles: main quests and the selected first get icons, at most MAX_ICONS
        tileList.sort((a, b) -> Integer.compare(priority(b), priority(a)));
        for (int q : tileList) {
            boolean hub = layout.hub(q);
            boolean big = layout.milestone(q);
            float size = tilePx(hub || big, big);
            if (size < MIN_TILE) {
                size = MIN_TILE;
            }
            // the tile's centre on a whole screen pixel: the tile, its item and its badge are all placed from this one point
            float px = oldIconSnap ? f.px(layout.x[q]) : f.snap(f.px(layout.x[q]));
            float py = oldIconSnap ? f.py(layout.y[q]) : f.snap(f.py(layout.y[q]));
            byte st = state[q];
            float x0 = px - size / 2;
            float y0 = py - size / 2;
            float radius = size * 0.24f;
            int back = hub || big ? backMain : backSide;
            if (layout.finale(q)) {
                float halo = 10;
                fill(g, x0 - halo, y0 - halo, size + 2 * halo, size + 2 * halo, radius + halo, Draw.alpha(gold, 0.12f));
                border(g, x0 - halo + 2, y0 - halo + 2, size + 2 * halo - 4, size + 2 * halo - 4, radius + halo - 2, Draw.alpha(gold, 0.9f), 2f);
            }
            if (big) {
                int ring = st == DONE ? t.c("ok.border") : t.c("button.border");
                border(g, x0 - 4.5f, y0 - 4.5f, size + 9, size + 9, radius + 4.5f, ring, 1.5f);
            }
            int edge = st == DONE ? t.c("ok.border") : st == OPEN ? (hub || big ? open : t.c("text.muted")) : t.c("panel.border");
            float edgeWidth = 1.5f;
            if (q == selected || routeNodes.contains(q)) {
                edge = hi;
                edgeWidth = 2f;
            }
            fill(g, x0, y0, size, size, radius, back);
            border(g, x0, y0, size, size, radius, edge, edgeWidth);
            if (iconCount < MAX_ICONS) {
                ItemStack icon = icon(q);
                if (!icon.isEmpty()) {
                    float iconSize = crisp(size * 0.68f);
                    // on whole screen pixels from the tile's own centre (not whole GUI units: those are two or more screen
                    // pixels, and the item jumped against its tile while the map moved)
                    float ix = oldIconSnap ? f.oldSnap(px - iconSize / 2) : f.snap(px - iconSize / 2);
                    float iy = oldIconSnap ? f.oldSnap(py - iconSize / 2) : f.snap(py - iconSize / 2);
                    item(g, icon, ix, iy, iconSize);
                    iconOffsets[2 * q] = (float) ((ix + iconSize / 2 - px) * f.k);
                    iconOffsets[2 * q + 1] = (float) ((iy + iconSize / 2 - py) * f.k);
                    iconCount++;
                    if (st == LOCKED) {
                        fill(g, x0 + 1.5f, y0 + 1.5f, size - 3, size - 3, radius, Draw.alpha(back, 0.62f));
                    }
                }
            }
            if (st == DONE && (hub || big || level == Level.CLOSE)) {
                float b = 13;
                fill(g, x0 + size - b + 5, y0 - 5, b, b, b / 2, t.c("state.ok"));
                icon(g, Icons.Shape.CHECK, x0 + size - b + 7, y0 - 3, b - 4, slot);
            } else if (st == LOCKED && (hub || big) && level == Level.CLOSE) {
                float b = 13;
                fill(g, x0 + size - b + 5, y0 - 5, b, b, b / 2, slotBorder);
                lock(g, x0 + size - b + 8.5f, y0 - 1.5f, t.c("text.muted"));
            }
        }
        return new int[] {drawn, iconCount};
    }

    private int priority(int q) {
        if (q == selected) {
            return 4;
        }
        if (layout.milestone(q)) {
            return 3;
        }
        return layout.hub(q) ? 2 : 1;
    }

    private ItemStack icon(int q) {
        return icons.computeIfAbsent(q, key -> {
            Quest quest = book.quests().get(map.quests().get(key).id());
            if (quest == null) {
                return ItemStack.EMPTY;
            }
            return quest.icon().map(TaskText::stackOf).orElseGet(() -> quest.tasks().isEmpty() ? ItemStack.EMPTY : TaskText.icon(quest.tasks().getFirst()));
        });
    }

    // --- labels ---

    private int drawLabels(GuiGraphics g, Theme t, Frame f, Level level) {
        int count = 0;
        boolean galaxy = layout.form == MapForm.GALAXY;
        // epoch labels first: chapter numbers keep clear of them
        if (level == Level.BOOK && !galaxy) {
            count += drawEpochLabels(g, t, f);
        } else if (level != Level.CLOSE && !galaxy) {
            for (CanvasLayout.Mark mark : layout.marks) {
                count += numeral(g, t, f, mark) ? 1 : 0;
            }
        }
        if (galaxy && level == Level.BOOK) {
            count += drawArmLabels(g, t, f);
        }
        for (int c = 0; c < layout.regions.length; c++) {
            CanvasLayout.Region region = layout.regions[c];
            if (!f.visibleBox(region.minX(), region.minY(), region.maxX(), region.maxY())) {
                continue;
            }
            if (level == Level.CLOSE) {
                continue;
            }
            if (level == Level.BOOK && c != hoverChapter) {
                if (!galaxy) {
                    count += chapterNumber(g, t, f, c) ? 1 : 0;
                }
                continue;
            }
            count += chapterName(g, t, f, c) ? 1 : 0;
        }
        count += drawNodeNames(g, t, f, level);
        count += drawHere(g, t, f, level) ? 1 : 0;
        return count;
    }

    private int drawEpochLabels(GuiGraphics g, Theme t, Frame f) {
        int count = 0;
        for (CanvasLayout.Mark mark : layout.marks) {
            float x1 = f.px(mark.x1());
            float y1 = f.py(mark.y1());
            if (x1 < vx - 20 || x1 > vx + vw + 20 || y1 < vy - 10 || y1 > vy + vh) {
                continue;
            }
            if (!mark.words()) {
                count += numeral(g, t, f, mark) ? 1 : 0;
                continue;
            }
            int e = mark.epoch();
            byte est = epochState(e);
            String numeral = roman(e);
            String title = epochTitle(e);
            float numW = Draw.labelWidth(numeral, 12.5f, Type.Weight.DISPLAY) * PER_GUI;
            float titleW = title.isEmpty() ? 0 : Draw.labelWidth(title, 12.5f, Type.Weight.MEDIUM) * PER_GUI;
            float markW = est == CH_LOCKED || est == CH_DONE ? 16 : 0;
            float w = 18 + numW + (titleW > 0 ? 7 + titleW : 0) + markW;
            float h = 22;
            float ex;
            float ey;
            int side = mark.side();
            if (Math.abs(side) == 2) {
                ex = Math.min(Math.max(x1 - w / 2, vx + 6), vx + vw - w - 6);
                ey = Math.min(Math.max(y1 + Math.signum(side) * 17, vy + 14), vy + vh - 14);
            } else {
                ey = Math.max(y1, vy + 24) - 13;
                ex = side > 0 ? Math.min(x1, vx + vw - w - 6) : Math.max(x1, vx + w + 6) - w;
                float step = ey > vy + vh / 2 ? 26 : -26;
                for (int i = 0; i < 5 && collides(ex, ey - h / 2, w, h); i++) {
                    ey += step;
                }
            }
            int fg = est == CH_LOCKED ? t.c("text.muted") : t.c("text.primary");
            fill(g, ex, ey - h / 2, w, h, 7, t.c("button.bg"));
            border(g, ex, ey - h / 2, w, h, 7, t.c("button.border"), 1);
            float pen = ex + 9;
            label(g, numeral, pen, ey, 12.5f, Type.Weight.DISPLAY, fg);
            pen += numW + 7;
            if (titleW > 0) {
                label(g, title, pen, ey, 12.5f, Type.Weight.MEDIUM, est == CH_LOCKED ? t.c("text.muted") : t.c("text.secondary"));
                pen += titleW + 5;
            }
            if (est == CH_DONE) {
                icon(g, Icons.Shape.CHECK, pen, ey - 6, 12, t.c("state.ok"));
            } else if (est == CH_LOCKED) {
                lock(g, pen, ey - 5, t.c("text.muted"));
            }
            labelBoxes.add(new float[] {ex, ey - h / 2, ex + w, ey + h / 2});
            epochBoxes.add(new float[] {ex, ey - h / 2, ex + w, ey + h / 2, e});
            if (hoverEpoch == e) {
                border(g, ex, ey - h / 2, w, h, 7, t.c("text.secondary"), 1);
            }
            count++;
        }
        return count;
    }

    /** A small numeral plate on an epoch boundary: inner turns of the spiral, and the epoch level. */
    private boolean numeral(GuiGraphics g, Theme t, Frame f, CanvasLayout.Mark mark) {
        float x1 = f.px(mark.x1());
        float y1 = Math.max(f.py(mark.y1()), vy + 10);
        if (x1 < vx - 20 || x1 > vx + vw + 20 || y1 < vy - 10 || y1 > vy + vh) {
            return false;
        }
        String numeral = roman(mark.epoch());
        float w = Math.max(20, Draw.labelWidth(numeral, 11, Type.Weight.DISPLAY) * PER_GUI + 10);
        float h = 16;
        fill(g, x1 - w / 2, y1 - h / 2, w, h, 5, t.c("button.bg"));
        border(g, x1 - w / 2, y1 - h / 2, w, h, 5, t.c("button.border"), 1);
        label(g, numeral, x1 - Draw.labelWidth(numeral, 11, Type.Weight.DISPLAY), y1, 11, Type.Weight.DISPLAY, t.c("text.secondary"));
        labelBoxes.add(new float[] {x1 - w / 2, y1 - h / 2, x1 + w / 2, y1 + h / 2});
        epochBoxes.add(new float[] {x1 - w / 2, y1 - h / 2, x1 + w / 2, y1 + h / 2, mark.epoch()});
        return true;
    }

    /** The chapter's number on its plate, in the corner of its area; not drawn when the area is narrower than the plate. */
    private boolean chapterNumber(GuiGraphics g, Theme t, Frame f, int c) {
        CanvasLayout.Region region = layout.regions[c];
        CanvasLayout.Label anchor = layout.labels[c];
        String text = String.format("%02d", map.chapters().get(c).number());
        float w = Math.max(22, Draw.labelWidth(text, 11, Type.Weight.DISPLAY) * PER_GUI + 10);
        float h = 17;
        if (w > anchor.maxWidth() * f.s) {
            return false;
        }
        float px;
        float py;
        if (region.kind() == CanvasLayout.RegionKind.RECT) {
            px = f.px(region.pts()[0]) + 19;
            py = f.py(region.pts()[1]) + 14;
        } else {
            px = f.px(anchor.x());
            py = f.py(anchor.y());
            for (int i = 0; i < 6 && collides(px - w / 2, py - h / 2, w, h); i++) {
                py += 21;
            }
        }
        py = Math.max(py, vy + 12);
        byte st = chapterState[c];
        int fg = st == CH_DONE ? t.c("state.ok") : st == CH_CURRENT ? t.c("text.primary") : t.c("text.dim");
        fill(g, px - w / 2, py - h / 2, w, h, 5, t.c("map.label.bg"));
        if (st == CH_CURRENT) {
            border(g, px - w / 2, py - h / 2, w, h, 5, t.c("button.border"), 1);
        }
        label(g, text, px - Draw.labelWidth(text, 11, Type.Weight.DISPLAY), py, 11, Type.Weight.DISPLAY, fg);
        labelBoxes.add(new float[] {px - w / 2, py - h / 2, px + w / 2, py + h / 2});
        return true;
    }

    /**
     * The chapter's name plate from the epoch level, and on hover: number, title, then "N of M" main quests or a check. It never
     * grows wider than its chapter: the count goes first, then the title is cut with an ellipsis (the hover names it in full).
     */
    private boolean chapterName(GuiGraphics g, Theme t, Frame f, int c) {
        CanvasLayout.Label anchor = layout.labels[c];
        boolean hover = c == hoverChapter;
        float maxW = hover ? Float.MAX_VALUE : (float) (anchor.maxWidth() * f.s);
        MapBook.Chap chapter = map.chapters().get(c);
        Chapter data = book.chapters().get(chapter.id());
        String title = data == null ? chapter.id().getPath() : text(data.title());
        String number = layout.form == MapForm.GALAXY ? "" : String.format("%02d", chapter.number());
        byte st = chapterState[c];
        String count = st == CH_DONE ? "" : Component.translatable("rysquests.canvas.count", countedDone(c), counted(c)).getString();
        float numW = number.isEmpty() ? 0 : Draw.labelWidth(number, 11, Type.Weight.REGULAR) * PER_GUI + 5;
        float countW = count.isEmpty() ? (st == CH_DONE ? 16 : 0) : Draw.labelWidth(count, 11, Type.Weight.REGULAR) * PER_GUI + 5;
        float titleW = Draw.labelWidth(title, 11, Type.Weight.MEDIUM) * PER_GUI;
        float pad = 14;
        if (pad + numW + titleW + countW > maxW) {
            countW = 0;
            count = "";
        }
        if (pad + numW + titleW > maxW) {
            float room = maxW - pad - numW;
            if (room < 24) {
                return false;
            }
            title = Draw.ellipsize(title, room / PER_GUI, 11, Type.Weight.MEDIUM);
            titleW = Draw.labelWidth(title, 11, Type.Weight.MEDIUM) * PER_GUI;
        }
        float w = pad + numW + titleW + countW;
        float h = 19;
        float px = f.px(anchor.x());
        float py = f.py(anchor.y());
        if (levelOf(f.s) != Level.BOOK) {
            py = Math.max(py, vy + insetTop + 12);
        }
        px = Math.min(Math.max(px, vx + w / 2 + 4), vx + vw - w / 2 - 4);
        for (int i = 0; i < 6 && collides(px - w / 2, py - h / 2, w, h); i++) {
            py += 21;
        }
        boolean open = c == openChapter;
        int bgColor = hover || open ? t.c("button.bg") : st == CH_LOCKED ? Draw.alpha(t.c("map.label.bg"), 0.6f) : t.c("map.label.bg");
        int borderColor = hover || open ? t.c("text.secondary") : st == CH_LOCKED ? 0 : st == CH_CURRENT ? t.c("button.border") : t.c("panel.border");
        int fg = hover || open || st == CH_CURRENT ? t.c("text.primary") : st == CH_LOCKED ? t.c("text.dim") : t.c("text.secondary");
        fill(g, px - w / 2, py - h / 2, w, h, 6, bgColor);
        if (borderColor != 0) {
            border(g, px - w / 2, py - h / 2, w, h, 6, borderColor, 1);
        }
        float pen = px - w / 2 + 7;
        if (!number.isEmpty()) {
            label(g, number, pen, py, 11, Type.Weight.REGULAR, st == CH_LOCKED ? t.c("text.dim") : t.c("text.muted"));
            pen += numW;
        }
        label(g, title, pen, py, 11, Type.Weight.MEDIUM, fg);
        pen += titleW + 5;
        if (!count.isEmpty()) {
            label(g, count, pen, py, 11, Type.Weight.REGULAR, t.c("text.muted"));
        } else if (st == CH_DONE && countW > 0) {
            icon(g, Icons.Shape.CHECK, pen, py - 5.5f, 11, t.c("state.ok"));
        }
        labelBoxes.add(new float[] {px - w / 2, py - h / 2, px + w / 2, py + h / 2});
        return true;
    }

    /** A galaxy's section names at the end of each arm, where fewest nodes lie under them. Places are kept per scale. */
    private int drawArmLabels(GuiGraphics g, Theme t, Frame f) {
        if (Math.abs(armPlacesScale - f.s) > 1e-4 || armPlaces.isEmpty()) {
            armPlaces.clear();
            armPlacesScale = f.s;
        }
        int count = 0;
        for (CanvasLayout.Arm arm : layout.arms) {
            String title = arm.epoch() > 0 ? map.epochTitle(arm.epoch()) : "";
            title = title.isEmpty() ? Component.translatable("rysquests.canvas.no_section").getString() : text(title);
            int done = 0;
            int total = 0;
            for (MapBook.Chap chapter : map.chapters()) {
                if (chapter.epoch() == arm.epoch()) {
                    done += countedDone(chapter.index());
                    total += counted(chapter.index());
                }
            }
            String upper = title.toUpperCase(java.util.Locale.ROOT);
            String countText = Component.translatable("rysquests.canvas.count", done, total).getString();
            float tw = Draw.labelWidth(upper, 11, Type.Weight.SEMIBOLD, 0.88f / PER_GUI) * PER_GUI;
            float cw = Draw.labelWidth(countText, 11, Type.Weight.REGULAR) * PER_GUI;
            float w = tw + 6 + cw + 16;
            float h = 20;
            float[] place = armPlaces.get(arm.epoch());
            if (place == null) {
                place = placeArm(f, arm, w, h);
                armPlaces.put(arm.epoch(), place);
            }
            // places are kept in canvas terms relative to the view: recompute the pixel point from the stored canvas point
            float px = f.px(place[0]);
            float py = f.py(place[1]);
            px = Math.min(Math.max(px, vx + w / 2 + 8), vx + vw - w / 2 - 8);
            py = Math.min(Math.max(py, vy + 20), vy + vh - 20);
            fill(g, px - w / 2, py - h / 2, w, h, 6, t.c("map.label.bg"));
            border(g, px - w / 2, py - h / 2, w, h, 6, t.c("panel.border"), 1);
            Draw.label(g, upper, (px - w / 2 + 8) / PER_GUI, py / PER_GUI, 11, Type.Weight.SEMIBOLD, t.c("text.secondary"), 0.88f / PER_GUI);
            label(g, countText, px - w / 2 + 8 + tw + 6, py, 11, Type.Weight.REGULAR, t.c("text.muted"));
            labelBoxes.add(new float[] {px - w / 2, py - h / 2, px + w / 2, py + h / 2});
            count++;
        }
        return count;
    }

    private float[] placeArm(Frame f, CanvasLayout.Arm arm, float w, float h) {
        double len = Math.max(1e-3, Math.hypot(arm.outX(), arm.outY()));
        double ux = arm.outX() / len;
        double uy = arm.outY() / len;
        double best = Double.MAX_VALUE;
        float[] place = {arm.x(), arm.y()};
        for (int out : new int[] {0, 16, 32, 48, 64, 80, 100, 120}) {
            for (int tg : new int[] {0, -24, 24, -48, 48, -72, 72}) {
                double cx = arm.x() + (ux * out - uy * tg) / f.s;
                double cy = arm.y() + (uy * out + ux * tg) / f.s;
                float px = f.px(cx);
                float py = f.py(cy);
                if (collides(px - w / 2, py - h / 2, w, h)) {
                    continue;
                }
                int under = grid == null ? 0 : grid.count(cx, cy, (w / 2 + 3) / f.s, (h / 2 + 3) / f.s);
                double cost = under * 40 + out + Math.abs(tg);
                if (cost < best) {
                    best = cost;
                    place = new float[] {(float) cx, (float) cy};
                }
            }
        }
        return place;
    }

    private int drawNodeNames(GuiGraphics g, Theme t, Frame f, Level level) {
        if (level == Level.BOOK) {
            return 0;
        }
        int count = 0;
        for (int q = 0; q < map.quests().size(); q++) {
            boolean big = layout.milestone(q);
            boolean hub = layout.hub(q);
            boolean show = q == selected || q == hoverQuest || (level == Level.CLOSE ? hub || big : big);
            if (!show || (!showSide && side(q)) || !f.visible(layout.x[q], layout.y[q], 0)) {
                continue;
            }
            Quest quest = book.quests().get(map.quests().get(q).id());
            if (quest == null) {
                continue;
            }
            float size = Math.max(MIN_TILE, tilePx(hub || big, big));
            float px = f.px(layout.x[q]);
            float py = f.py(layout.y[q]) + size / 2 + 4;
            String title = text(quest.title());
            List<String> lines = Draw.wrapLabel(title, 104 / PER_GUI, 11, Type.Weight.REGULAR, 2);
            float lw = 0;
            for (String line : lines) {
                lw = Math.max(lw, Draw.labelWidth(line, 11, Type.Weight.REGULAR) * PER_GUI);
            }
            float w = lw + 10;
            float h = lines.size() * 13 + 3;
            boolean isSelected = q == selected;
            fill(g, px - w / 2, py, w, h, 5, t.c("map.label.bg"));
            int fg = isSelected ? t.c("text.primary") : state[q] == LOCKED ? t.c("text.dim") : t.c("text.secondary");
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                float lineW = Draw.labelWidth(line, 11, isSelected ? Type.Weight.SEMIBOLD : Type.Weight.REGULAR) * PER_GUI;
                label(g, line, px - lineW / 2, py + 8 + i * 13, 11, isSelected ? Type.Weight.SEMIBOLD : Type.Weight.REGULAR, fg);
            }
            count++;
        }
        return count;
    }

    /** "You are here" on the side of the node where no chapter number or epoch label stands. */
    private boolean drawHere(GuiGraphics g, Theme t, Frame f, Level level) {
        if (here < 0 || here == selected || (!showSide && side(here)) || !f.visible(layout.x[here], layout.y[here], 0)) {
            return false;
        }
        String text = Component.translatable("rysquests.canvas.here").getString();
        float w = Draw.labelWidth(text, 11, Type.Weight.REGULAR) * PER_GUI + 27;
        float h = 20;
        float hx = f.px(layout.x[here]);
        float hy = f.py(layout.y[here]);
        float reach = level == Level.BOOK ? 20 : Math.max(MIN_TILE, tilePx(layout.hub(here), layout.milestone(here))) / 2 + 14;
        float[][] tries = {{0, -reach}, {0, reach}, {w / 2 + 14, 0}, {-w / 2 - 14, 0}, {w / 2, -reach}, {-w / 2, -reach}, {w / 2, reach}, {-w / 2, reach}};
        // on the chapter level and closer the plate goes under the node when the path to the goal runs upwards
        if (level != Level.BOOK && route.length > 1 && route[0] == here && layout.y[route[1]] < layout.y[here]) {
            tries = new float[][] {{0, reach}, {0, -reach}};
        }
        float cx = hx;
        float cy = hy + reach;
        for (float[] at : tries) {
            if (!collides(hx + at[0] - w / 2, hy + at[1] - h / 2, w, h)) {
                cx = hx + at[0];
                cy = hy + at[1];
                break;
            }
        }
        fill(g, cx - w / 2, cy - h / 2, w, h, 10, t.c("panel.bg"));
        border(g, cx - w / 2, cy - h / 2, w, h, 10, t.c("button.border"), 1);
        fill(g, cx - w / 2 + 8, cy - 3, 6, 6, 3, t.c("text.primary"));
        label(g, text, cx - w / 2 + 19, cy, 11, Type.Weight.REGULAR, t.c("text.primary"));
        labelBoxes.add(new float[] {cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2});
        return true;
    }

    private boolean collides(float x, float y, float w, float h) {
        for (float[] box : labelBoxes) {
            if (x < box[2] + 3 && x + w > box[0] - 3 && y < box[3] + 2 && y + h > box[1] - 2) {
                return true;
            }
        }
        return false;
    }

    private byte epochState(int epoch) {
        boolean all = true;
        boolean none = true;
        boolean any = false;
        for (MapBook.Chap chapter : map.chapters()) {
            if (chapter.epoch() != epoch) {
                continue;
            }
            any = true;
            byte st = chapterState[chapter.index()];
            all &= st == CH_DONE;
            none &= st == CH_LOCKED;
        }
        return !any ? CH_LOCKED : all ? CH_DONE : none ? CH_LOCKED : CH_CURRENT;
    }

    private String epochTitle(int epoch) {
        String title = map.epochTitle(epoch);
        return title.isEmpty() ? "" : text(title);
    }

    static String roman(int n) {
        if (n <= 0) {
            return "";
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                out.append(letters[i]);
                n -= values[i];
            }
        }
        return out.toString();
    }

    // --- minimap ---

    private void drawMinimap(GuiGraphics g, Theme t) {
        float w = 172;
        float h = 104;
        float x0 = vx + vw - w - 12;
        float y0 = vy + vh - h - 12;
        fill(g, x0, y0, w, h, 10, t.c("sidebar.bg"));
        border(g, x0, y0, w, h, 10, t.c("button.border"), 1);
        double bw = layout.maxX - layout.minX;
        double bh = layout.maxY - layout.minY;
        double k = Math.min((w - 8) / bw, (h - 8) / bh);
        double ox = x0 + (w - bw * k) / 2;
        double oy = y0 + (h - bh * k) / 2;
        int done = t.c("map.node.done");
        int open = t.c("map.node.open");
        int locked = Draw.mix(t.c("scroll.thumb"), t.c("map.node.locked"), 0.35f);
        for (int q = 0; q < map.quests().size(); q++) {
            if (!layout.hub(q)) {
                continue;
            }
            float px = (float) (ox + (layout.x[q] - layout.minX) * k);
            float py = (float) (oy + (layout.y[q] - layout.minY) * k);
            int color = state[q] == DONE ? done : state[q] == OPEN ? open : locked;
            Draw.rect(g, Math.round(px / PER_GUI), Math.round(py / PER_GUI), 1, 1, color);
        }
        double s = camera.scale;
        float rx = (float) (ox + (camera.cx - vw / 2 / s - layout.minX) * k);
        float ry = (float) (oy + (camera.cy - vh / 2 / s - layout.minY) * k);
        float rw = (float) (vw / s * k);
        float rh = (float) (vh / s * k);
        float cx0 = Math.max(rx, x0 + 2);
        float cy0 = Math.max(ry, y0 + 2);
        float cx1 = Math.min(rx + rw, x0 + w - 2);
        float cy1 = Math.min(ry + rh, y0 + h - 2);
        if (cx1 > cx0 && cy1 > cy0) {
            fill(g, cx0, cy0, cx1 - cx0, cy1 - cy0, 2, Draw.alpha(t.c("text.primary"), 0.08f));
            border(g, cx0, cy0, cx1 - cx0, cy1 - cy0, 2, t.c("text.primary"), 1.2f);
        }
    }

    // --- loading (picture 49) ---

    private void drawLoading(GuiGraphics g, Theme t) {
        float cx = vx + vw / 2;
        float cy = vy + vh / 2 - 20;
        double progress = Math.max(0, Math.min(1, loading.getAsDouble()));
        // a turning arc above the title
        float angle = (System.currentTimeMillis() % 1000L) * 0.36f;
        Draw.ring(g, cx / PER_GUI, (cy - 46) / PER_GUI, 20 / PER_GUI, 3 / PER_GUI, t.c("slot.border"));
        Draw.arc(g, cx / PER_GUI, (cy - 46) / PER_GUI, 18.5f / PER_GUI, angle, 110, 3 / PER_GUI, t.c("text.primary"));
        String title = Component.translatable("rysquests.map.loading").getString();
        float tw = Draw.labelWidth(title, 16, Type.Weight.DISPLAY) * PER_GUI;
        label(g, title, cx - tw / 2, cy - 2, 16, Type.Weight.DISPLAY, t.c("text.primary"));
        float bw = 220;
        fill(g, cx - bw / 2, cy + 22, bw, 3, 1.5f, t.c("slot.bg"));
        fill(g, cx - bw / 2, cy + 22, (float) Math.max(3, bw * progress), 3, 1.5f, t.c("text.secondary"));
        String text = Component.translatable("rysquests.canvas.loading_text").getString();
        List<String> lines = Draw.wrapLabel(text, 280 / PER_GUI, 12, Type.Weight.REGULAR, 3);
        float ly = cy + 46;
        for (String line : lines) {
            float lw = Draw.labelWidth(line, 12, Type.Weight.REGULAR) * PER_GUI;
            label(g, line, cx - lw / 2, ly, 12, Type.Weight.REGULAR, t.c("text.muted"));
            ly += 18;
        }
        if (openList != null) {
            String button = Component.translatable("rysquests.canvas.open_list").getString();
            float bwid = Draw.labelWidth(button, 12, Type.Weight.MEDIUM) * PER_GUI + 22;
            float bx = cx - bwid / 2;
            float by = ly + 6;
            boolean hot = mouseX >= bx && mouseX < bx + bwid && mouseY >= by && mouseY < by + 26;
            fill(g, bx, by, bwid, 26, 8, hot ? Draw.mix(t.c("button.bg"), t.c("text.primary"), 0.08f) : t.c("button.bg"));
            border(g, bx, by, bwid, 26, 8, t.c("button.border"), 1);
            label(g, button, bx + 11, by + 13, 12, Type.Weight.MEDIUM, t.c("button.fg"));
            loadingButton = new float[] {bx, by, bwid, 26};
        }
    }

    private float[] loadingButton;

    /** A click on the loading page's button, for the screen to pass on: true when it was the button. */
    public boolean clickLoading(double mx, double my) {
        if (layout == null && openList != null && loadingButton != null && mx >= loadingButton[0] && mx < loadingButton[0] + loadingButton[2] && my >= loadingButton[1]
                && my < loadingButton[1] + loadingButton[3]) {
            openList.run();
            return true;
        }
        return false;
    }

    // --- drawing helpers, mockup pixels in ---

    private static void fill(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
        Draw.round(g, x / PER_GUI, y / PER_GUI, w / PER_GUI, h / PER_GUI, Math.max(0.02f, r / PER_GUI), color);
    }

    private static void border(GuiGraphics g, float x, float y, float w, float h, float r, int color, float width) {
        Draw.roundBorder(g, x / PER_GUI, y / PER_GUI, w / PER_GUI, h / PER_GUI, Math.max(0.02f, r / PER_GUI), color, width / PER_GUI);
    }

    /** A dot of radius {@code r}, centre and radius in GUI units: one atlas quad. */
    private static void dot(GuiGraphics g, float x, float y, float r, int color) {
        if (r <= 0) {
            return;
        }
        Draw.round(g, x - r, y - r, 2 * r, 2 * r, r, color);
    }

    /** A title from its key, without the formatting codes of imported FTB texts, which the font would draw as letters. */
    private static String text(String key) {
        String shown = Component.translatable(key).getString();
        if (shown.indexOf('§') < 0) {
            return shown;
        }
        String plain = net.minecraft.ChatFormatting.stripFormatting(shown);
        return plain == null ? shown : plain;
    }

    private static void label(GuiGraphics g, String text, float x, float cy, float px, Type.Weight weight, int color) {
        Draw.label(g, text, x / PER_GUI, cy / PER_GUI, px, weight, color);
    }

    /** The core's lock, 7 by 8 GUI units, drawn at three quarters so it sits with 11 pixel text. */
    private static void lock(GuiGraphics g, float x, float y, int color) {
        g.pose().pushPose();
        g.pose().translate(x / PER_GUI, y / PER_GUI, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        Draw.lock(g, 0, 1, color);
        g.pose().popPose();
    }

    private static void icon(GuiGraphics g, Icons.Shape shape, float x, float y, float size, int color) {
        Icons.draw(g, shape, x / PER_GUI, y / PER_GUI, size / PER_GUI, color);
    }

    /**
     * An item's size that draws its pixels whole: a multiple of 16 mockup pixels, half its own size at GUI scale 2 and up from there,
     * so the closest view shows the item sharp and not stretched by a fraction.
     */
    private static float crisp(float size) {
        return size < 16 ? size : (float) Math.floor(size / 16) * 16;
    }

    /** Screen pixels in one mockup pixel: the family's interface unit is two mockup pixels. */
    private static double screenPerPx() {
        double pixels = UiScale.pixels();
        return pixels > 0 ? pixels / PER_GUI : 1;
    }

    private static void item(GuiGraphics g, ItemStack stack, float x, float y, float size) {
        float k = size / 32f;
        g.pose().pushPose();
        g.pose().translate(x / PER_GUI, y / PER_GUI, 0);
        g.pose().scale(k, k, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private int index(@Nullable ResourceLocation quest) {
        return map == null || quest == null ? -1 : map.quest(quest);
    }
}
