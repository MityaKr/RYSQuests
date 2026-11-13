package works.repka.rysquests.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientLayout;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.canvas.MapCanvas;
import works.repka.rysquests.client.canvas.MapLayouts;
import works.repka.rysquests.client.layout.CanvasLayout;
import works.repka.rysquests.client.layout.FormFacts;
import works.repka.rysquests.client.layout.LayoutEngine;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.network.LayoutSavePayload;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;

/**
 * "Какой будет карта?" (pictures 07, 07b): the four forms laid out on the book's own quests by the map's engine
 * ({@link MapLayouts#preview}), the advised one first and the others in turn, each with a line of reason. The chosen form is shown
 * large on the right as players will see it, with why the advice is what it is. One can choose without waiting for the rest.
 * Choosing writes the form ({@code auto} when it is the advised one, so that the book keeps following the rule), saves the
 * layout for players and opens the book.
 */
final class MapFormStep {
    private static final String P = FirstRun.P;
    private static final float LEFT_W = 536;
    private static final float CARD_GAP = 10;
    private static final float PREVIEW_H = 116;
    private static final float CANVAS_H = 420;
    /** Thumbnails are drawn at twice the mockup's pixels and filtered down. */
    private static final int THUMB_SCALE = 2;

    private final BookScreen screen;
    private final Runnable back;
    private final MapCanvas canvas = new MapCanvas();
    private final Map<MapForm, Thumb> thumbs = new EnumMap<>(MapForm.class);
    @Nullable
    private String startedFor;
    @Nullable
    private QuestBook book;
    @Nullable
    private MapBook map;
    private Map<MapForm, LayoutEngine.Job> jobs = Map.of();
    @Nullable
    private FirstRun.Book facts;
    private MapForm advised = MapForm.GALAXY;
    @Nullable
    private MapForm selected;
    @Nullable
    private MapForm shown;
    private boolean shownReady;
    private final float[] canvasRect = new float[4];
    private double pointerX;
    private double pointerY;
    private boolean canvasHeld;
    private int[] categories = new int[0];
    private final List<String> legend = new ArrayList<>();
    private int thumbCount;

    MapFormStep(BookScreen screen, Runnable back) {
        this.screen = screen;
        this.back = back;
        canvas.setInsets(12, 12, 12);
        canvas.setMinimap(false);
    }

    // --- state ---

    /** Starts the previews for the book as it is now; a book that changed (it arrives after the import) starts them again. */
    private void ensureStarted() {
        QuestBook current = ClientBook.current();
        if (current.quests().isEmpty() || current.layoutHash().equals(startedFor)) {
            return;
        }
        releaseThumbs();
        cancelJobs(null);
        startedFor = current.layoutHash();
        book = current;
        map = MapBook.of(current);
        MapForms.Choice auto = MapForms.auto(current.meta(), List.copyOf(current.chapters().values()), List.copyOf(current.quests().values()));
        advised = auto.form();
        FormFacts galaxy = FormFacts.of(map);
        List<BookMeta.Epoch> epochs = current.meta().epochs();
        facts = new FirstRun.Book(auto, epochs.size(), !epochs.isEmpty() && epochs.stream().allMatch(BookMeta.Epoch::imported), map.chapters().size(),
                galaxy.core(), galaxy.arms());
        if (selected == null || selected == MapForm.AUTO) {
            selected = advised;
        }
        shown = null;
        jobs = MapLayouts.preview(current);
        colours(map);
        canvas.setStatus(id -> {
            Quest quest = current.quests().get(id);
            return quest == null ? QuestStatus.LOCKED : BookState.status(quest);
        }, ClientProgress::version);
    }

    /** Dots are coloured by section: by epoch in a book with epochs, otherwise by chapter. */
    private void colours(MapBook book) {
        legend.clear();
        List<Integer> epochs = book.epochNumbers();
        categories = new int[book.chapters().size()];
        boolean loose = false;
        for (MapBook.Chap chapter : book.chapters()) {
            if (epochs.isEmpty()) {
                categories[chapter.index()] = chapter.index();
            } else if (chapter.hasEpoch()) {
                categories[chapter.index()] = epochs.indexOf(chapter.epoch());
            } else {
                categories[chapter.index()] = epochs.size();
                loose = true;
            }
        }
        if (epochs.isEmpty()) {
            for (MapBook.Chap chapter : book.chapters()) {
                var source = this.book == null ? null : this.book.chapters().get(chapter.id());
                legend.add(source == null ? chapter.id().getPath() : Component.translatable(source.title()).getString());
            }
        } else {
            for (int epoch : epochs) {
                String title = book.epochTitle(epoch);
                legend.add(title.isEmpty() ? Ui.roman(epoch) : Component.translatable(title).getString());
            }
            if (loose) {
                legend.add(FirstRunParts.t(P + "form.loose"));
            }
        }
    }

    @Nullable
    private CanvasLayout ready(MapForm form) {
        LayoutEngine.Job job = jobs.get(form);
        if (job == null || !job.result.isDone() || job.result.isCompletedExceptionally()) {
            return null;
        }
        return job.result.getNow(null);
    }

    private boolean allReady() {
        return !jobs.isEmpty() && jobs.values().stream().allMatch(job -> job.result.isDone());
    }

    void select(MapForm form) {
        selected = form;
    }

    @Nullable
    MapForm selected() {
        return selected;
    }

    MapForm advised() {
        return advised;
    }

    /** Whether the advised form is laid out and drawn: for tests (picture 07b). */
    boolean advisedDone() {
        return ready(advised) != null && thumbs.containsKey(advised);
    }

    /** Whether the previews of every form are laid out: for tests. */
    boolean previewsDone() {
        return allReady() && thumbCount == jobs.size();
    }

    // --- drawing ---

    void render(Ui ui, int x, int y, int w, int h) {
        ensureStarted();
        float bodyH = h - FirstRunParts.ACT_H;
        float left = x + 24;
        float top = y + 18;
        float rightX = left + LEFT_W + 22;
        float rightW = x + w - 24 - rightX;
        renderLeft(ui, left, top);
        renderRight(ui, rightX, top, rightW, y + bodyH - 18 - top);
        renderActions(ui, x, y + bodyH, w);
    }

    private void renderLeft(Ui ui, float x, float y) {
        float pen = y;
        ui.label(FirstRunParts.t(P + "form.title"), x, pen + Ui.headingHeight(22) / 2f, 22, Ui.DISPLAY, ui.c("text.primary"));
        pen += Ui.headingHeight(22) + 3;
        String sub = FirstRunParts.t(P + (allReady() ? "form.sub" : "form.sub_calculating"));
        List<String> lines = ui.wrap(sub, LEFT_W, 13, Ui.REGULAR, 3);
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x, pen + 9 + 18 * i, 13, Ui.REGULAR, ui.c("text.muted"));
        }
        pen += lines.size() * 18 + 10;
        float cardW = (LEFT_W - CARD_GAP) / 2f;
        float cardH = 0;
        for (int i = 0; i < FirstRun.FORMS.size(); i++) {
            float cx = x + (i % 2) * (cardW + CARD_GAP);
            float cy = pen + (i / 2) * (cardH + CARD_GAP);
            float drawn = card(ui, FirstRun.FORMS.get(i), cx, cy, cardW);
            if (i % 2 == 1 || i == FirstRun.FORMS.size() - 1) {
                cardH = Math.max(cardH, drawn);
            } else {
                cardH = drawn;
            }
        }
        pen += 2 * cardH + CARD_GAP + 10;
        if (!legend.isEmpty()) {
            pen += FirstRunParts.rubric(ui, x, pen, LEFT_W, FirstRunParts.t(P + "form.legend"), null) + 6;
            float lx = x;
            float lineH = 19;
            int shownCount = Math.min(legend.size(), 24);
            for (int i = 0; i < shownCount; i++) {
                String name = legend.get(i);
                float iw = 8 + 4 + ui.width(name, 11, Ui.REGULAR);
                if (lx > x && lx + iw > x + LEFT_W) {
                    lx = x;
                    pen += lineH;
                }
                float cy = pen + lineH / 2f - 2;
                ui.circle(lx, cy - 4, 8, ui.theme.category(i));
                ui.label(name, lx + 12, cy, 11, Ui.REGULAR, ui.c("text.muted"));
                lx += iw + 12;
            }
            if (legend.size() > shownCount) {
                ui.label(FirstRunParts.t(P + "report.more", legend.size() - shownCount), lx, pen + lineH / 2f - 2, 11, Ui.REGULAR, ui.c("text.muted"));
            }
        }
    }

    /** One form in the grid ({@code .fcard}): its preview, name, "советуем" on the advised one, a line of reason. @return its height */
    private float card(Ui ui, MapForm form, float x, float y, float w) {
        boolean on = form == selected;
        String name = FirstRunParts.t(P + "form." + form.getSerializedName());
        List<String> reason = facts == null ? List.of() : ui.wrap(FirstRunParts.str(FirstRun.formReason(form, facts)), w - 22, 11.5f, Ui.REGULAR, 3);
        float h = 1 + PREVIEW_H + 9 + 16 + 3 + Math.max(2, reason.size()) * 15.5f + 10 + 1;
        ui.box(x, y, w, h, 12, ui.c(on ? "tab.active.bg" : "card.bg"), ui.c(on ? "text.secondary" : "panel.border"));
        ui.fillCorners(x + 1, y + 1, w - 2, PREVIEW_H, 11, ui.c("map.bg"), true, true, false, false);
        preview(ui, form, x + 1, y + 1, w - 2, PREVIEW_H);
        float pen = y + 1 + PREVIEW_H + 9;
        ui.label(name, x + 11, pen + 8, 13, Ui.SEMIBOLD, ui.c("text.primary"));
        if (form == advised) {
            FirstRunParts.tag(ui, FirstRunParts.t("rysquests.empty.advised"), x + 11 + ui.width(name, 13, Ui.SEMIBOLD) + 7, pen + 8, true);
        }
        pen += 16 + 3;
        for (int i = 0; i < reason.size(); i++) {
            ui.label(reason.get(i), x + 11, pen + 15.5f * i + 7.75f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        ui.hit(x, y, w, h, () -> select(form));
        return h;
    }

    /** The preview area of a card: the thumbnail, or how far its layout is ({@code .calc}), or "в очереди". */
    private void preview(Ui ui, MapForm form, float x, float y, float w, float h) {
        LayoutEngine.Job job = jobs.get(form);
        CanvasLayout layout = ready(form);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        if (layout != null && map != null) {
            Thumb thumb = thumbs.computeIfAbsent(form, f -> Thumb.of(ui, map, layout, categories, Math.round(w), Math.round(h), ++thumbCount));
            ui.image(thumb.id, x, y, w, h, thumb.width, thumb.height);
        } else if (job != null && job.result.isCompletedExceptionally()) {
            ui.labelCentered(FirstRunParts.t(P + "form.failed"), cx, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        } else if (job == null || job.queued()) {
            String text = FirstRunParts.t(job == null ? P + "form.waiting_book" : P + "form.queued");
            clock(ui, cx - 9, cy - 9 - 7.75f - 4.5f, 18, ui.c("text.muted"));
            ui.labelCentered(text, cx, cy + 9 - 4.5f + 4.5f + 3, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        } else {
            int percent = (int) Math.round(job.progress() * 100);
            ui.bar(cx - 60, cy - 4.5f - 6, 120, 4, 2, (float) job.progress(), ui.c("text.secondary"));
            ui.labelCentered(FirstRunParts.t(P + "form.calculating", percent), cx, cy + 6, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
    }

    /** The clock of "в очереди": a ring and two hands. */
    private static void clock(Ui ui, float x, float y, float size, int color) {
        float k = size / 24f;
        ui.ring(x + 3.5f * k, y + 3.5f * k, 17 * k, 2 * k, color);
        float cx = (x + 12 * k) / 2f;
        float cy = (y + 12 * k) / 2f;
        Draw.line(ui.g, cx, cy, cx, (y + 7.5f * k) / 2f, k, color);
        Draw.line(ui.g, cx, cy, (x + 15 * k) / 2f, (y + 14 * k) / 2f, k, color);
    }

    private void renderRight(Ui ui, float x, float y, float w, float h) {
        MapForm form = selected == null ? advised : selected;
        float cy = y + 11;
        String name = FirstRunParts.t(P + "form." + form.getSerializedName());
        ui.label(name, x, cy, 16, Ui.DISPLAY, ui.c("text.primary"));
        float pen0 = x + ui.width(name, 16, Ui.DISPLAY) + 10;
        if (form == advised) {
            pen0 += FirstRunParts.tag(ui, FirstRunParts.t(P + "form.best"), pen0, cy, true) + 10;
        }
        // the quiet hint gives way when the line is short (the compact layout), it never runs under the tag
        String players = FirstRunParts.t(P + "form.players");
        if (pen0 + ui.width(players, 12, Ui.REGULAR) <= x + w) {
            ui.labelRight(players, x + w, cy, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        float pen = y + 22 + 10;
        float canvasH = Math.min(CANVAS_H, h - 22 - 10 - 10 - 80);
        ui.box(x, pen, w, canvasH, 12, ui.c("map.bg"), ui.c("panel.border"));
        renderCanvas(ui, form, x + 1, pen + 1, w - 2, canvasH - 2);
        ui.stroke(x, pen, w, canvasH, 12, ui.c("panel.border"));
        pen += canvasH + 10;
        if (facts != null) {
            String why = FirstRunParts.t(P + "form." + advised.getSerializedName() + ".why");
            FirstRunParts.note(ui, x, pen, w, 10, Icons.Shape.SPARK, List.of(
                    new FirstRunParts.Run(why, Ui.SEMIBOLD, ui.c("text.primary")),
                    new FirstRunParts.Run(FirstRunParts.str(FirstRun.why(facts)), Ui.REGULAR, ui.c("text.secondary"))));
        }
    }

    private void renderCanvas(Ui ui, MapForm form, float x, float y, float w, float h) {
        canvasRect[0] = x;
        canvasRect[1] = y;
        canvasRect[2] = w;
        canvasRect[3] = h;
        CanvasLayout layout = ready(form);
        if (book != null && map != null && (form != shown || (layout != null) != shownReady)) {
            shown = form;
            shownReady = layout != null;
            canvas.setLayout(book, map, layout);
            LayoutEngine.Job job = jobs.get(form);
            canvas.setLoading(layout == null && job != null ? job::progress : null, null);
        }
        if (book == null) {
            return;
        }
        canvas.render(ui.g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), ui.mouseX, ui.mouseY);
    }

    private void renderActions(Ui ui, float x, float y, float w) {
        float cy = FirstRunParts.actions(ui, x, y, w);
        float by = cy - FirstRunParts.BIG_H / 2f;
        String backLabel = FirstRunParts.t("rysquests.page.back_plain");
        float backW = FirstRunParts.big(ui, backLabel, x + 18, by, false, Icons.Shape.CHEVRON_LEFT, false, null, back);
        ui.label(FirstRunParts.t(P + (allReady() ? "form.hint" : "form.hint_calculating")), x + 18 + backW + 8 + 8, cy, 12, Ui.REGULAR, ui.c("text.muted"));
        MapForm form = selected == null ? advised : selected;
        String choose = FirstRunParts.t(P + "form.choose", FirstRunParts.t(P + "form." + form.getSerializedName() + ".acc"));
        float chooseW = FirstRunParts.bigWidth(ui, choose, true, null);
        FirstRunParts.big(ui, choose, x + w - 18 - chooseW, by, true, null, false, null, book == null ? null : this::choose);
    }

    // --- choosing ---

    /** "Выбрать галактику": writes the form, saves the layout for players and opens the book on its map. */
    void choose() {
        QuestBook current = ClientBook.current();
        MapForm picked = selected == null ? advised : selected;
        MapForm save = FirstRun.formToSave(picked, advised);
        // the other forms are not needed any more: their layouts stop, as the hint under the cards promises
        cancelJobs(picked);
        CanvasLayout layout = ready(picked);
        if (save == current.meta().mapForm() && map != null && layout != null && layout.hash.equals(current.layoutHash())) {
            PacketDistributor.sendToServer(new LayoutSavePayload(layout.toBaked(map)));
        } else if (save == current.meta().mapForm()) {
            ClientLayout.bakeAndSave();
        } else {
            ClientBook.requestMapForm(save);
            BakeWhenSaved.await(save);
        }
        screen.closePage();
        BookTabs.byId(BookTabs.MAP).open().run();
    }

    /** After a new form is written, the book comes back with it; then its layout is baked and saved for players. */
    private static final class BakeWhenSaved {
        @Nullable
        private static MapForm awaited;
        private static int ticks;
        private static boolean listening;

        static void await(MapForm form) {
            awaited = form;
            ticks = 0;
            if (!listening) {
                listening = true;
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
            }
        }

        private static void tick() {
            if (awaited == null) {
                return;
            }
            if (ClientBook.current().meta().mapForm() == awaited) {
                awaited = null;
                ClientLayout.bakeAndSave();
            } else if (++ticks > 20 * 60) {
                awaited = null;
            }
        }
    }

    // --- input ---

    private boolean overCanvas(double x, double y) {
        return x >= canvasRect[0] && y >= canvasRect[1] && x < canvasRect[0] + canvasRect[2] && y < canvasRect[1] + canvasRect[3];
    }

    void scroll(double x, double y, double amount) {
        if (overCanvas(x, y)) {
            canvas.mouseScrolled(x, y, amount);
        }
    }

    void press(double x, double y, int button) {
        if (overCanvas(x, y)) {
            canvasHeld = true;
            pointerX = x;
            pointerY = y;
            canvas.mouseClicked(x, y, button);
        }
    }

    boolean drag(double dx, double dy) {
        if (!canvasHeld) {
            return false;
        }
        pointerX += dx;
        pointerY += dy;
        canvas.mouseDragged(pointerX, pointerY, dx, dy);
        return true;
    }

    boolean release(double x, double y, int button) {
        if (!canvasHeld) {
            return false;
        }
        canvasHeld = false;
        canvas.mouseReleased(x, y, button);
        return true;
    }

    void closed() {
        releaseThumbs();
        cancelJobs(null);
    }

    /** Stops the layouts of the preview still being worked out, but the one of {@code keep} (none: all of them). */
    private void cancelJobs(@Nullable MapForm keep) {
        jobs.forEach((form, job) -> {
            if (form != keep && !job.result.isDone()) {
                job.cancel();
            }
        });
    }

    private void releaseThumbs() {
        thumbs.values().forEach(Thumb::release);
        thumbs.clear();
    }

    // --- thumbnails ---

    /** A preview drawn once into a texture: one dot per quest, coloured by section. */
    private record Thumb(ResourceLocation id, int width, int height) {
        static Thumb of(Ui ui, MapBook map, CanvasLayout layout, int[] categories, int w, int h, int serial) {
            int width = w * THUMB_SCALE;
            int height = h * THUMB_SCALE;
            float[] argb = new float[width * height * 4];
            float pad = 7 * THUMB_SCALE;
            float spanX = Math.max(1, layout.maxX - layout.minX);
            float spanY = Math.max(1, layout.maxY - layout.minY);
            float scale = Math.min((width - pad * 2) / spanX, (height - pad * 2) / spanY);
            float ox = (width - spanX * scale) / 2f - layout.minX * scale;
            float oy = (height - spanY * scale) / 2f - layout.minY * scale;
            float radius = Math.max(0.9f * THUMB_SCALE, Math.min(1.6f * THUMB_SCALE, scale * 9));
            int[] palette = new int[13];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = ui.theme.category(i);
            }
            for (int q = 0; q < layout.x.length && q < map.quests().size(); q++) {
                int chapter = map.quests().get(q).chapter();
                int colour = chapter >= 0 && chapter < categories.length ? palette[Math.floorMod(categories[chapter], 12)] : palette[0];
                float r = layout.hub(q) || layout.milestone(q) ? radius * 1.6f : radius;
                disc(argb, width, height, layout.x[q] * scale + ox, layout.y[q] * scale + oy, r, colour, 0.9f);
            }
            NativeImage image = new NativeImage(width, height, false);
            for (int py = 0; py < height; py++) {
                for (int px = 0; px < width; px++) {
                    int i = (py * width + px) * 4;
                    float a = argb[i + 3];
                    int abgr = a <= 0 ? 0 : (Math.round(a * 255) << 24) | (Math.round(argb[i + 2] / a * 255) << 16) | (Math.round(argb[i + 1] / a * 255) << 8)
                            | Math.round(argb[i] / a * 255);
                    image.setPixelRGBA(px, py, abgr);
                }
            }
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "form_preview/" + serial);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return new Thumb(id, width, height);
        }

        /** A soft-edged disc composited over the buffer, premultiplied: r, g, b, a per pixel. */
        private static void disc(float[] buffer, int width, int height, float cx, float cy, float r, int colour, float alpha) {
            float red = ((colour >> 16) & 255) / 255f;
            float green = ((colour >> 8) & 255) / 255f;
            float blue = (colour & 255) / 255f;
            int x0 = Math.max(0, (int) Math.floor(cx - r - 1));
            int x1 = Math.min(width - 1, (int) Math.ceil(cx + r + 1));
            int y0 = Math.max(0, (int) Math.floor(cy - r - 1));
            int y1 = Math.min(height - 1, (int) Math.ceil(cy + r + 1));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    float d = (float) Math.hypot(x + 0.5f - cx, y + 0.5f - cy) - r;
                    float cover = Math.max(0, Math.min(1, 0.5f - d)) * alpha;
                    if (cover <= 0) {
                        continue;
                    }
                    int i = (y * width + x) * 4;
                    float keep = 1 - cover;
                    buffer[i] = red * cover + buffer[i] * keep;
                    buffer[i + 1] = green * cover + buffer[i + 1] * keep;
                    buffer[i + 2] = blue * cover + buffer[i + 2] * keep;
                    buffer[i + 3] = cover + buffer[i + 3] * keep;
                }
            }
        }

        void release() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }
}
