package works.repka.rysquests.client.screen;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.client.canvas.MapCanvas;
import works.repka.rysquests.client.canvas.MapLayouts;
import works.repka.rysquests.client.layout.CanvasLayout;
import works.repka.rysquests.client.layout.LayoutEngine;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.progress.QuestStatus;

/**
 * "Вид карты" (picture 36): the four forms laid out on the author's own quests, dots coloured by epoch, a line of reason under each. A
 * click on a card puts the form into the draft at once: there is no "Применить" and no "Вернуть", undo is the editor's. "сейчас у
 * игроков" marks the form players see; "Выбирать форму автоматически" is only the switch, off once the author chose. A form that does
 * not fit the book has no preview, only its reason. On the right the chosen form big, as players will see it, and the chapters off
 * the map. The previews are laid out by the map's engine ({@link MapLayouts#preview}) off the main thread.
 */
final class MapFormPage {
    private static final List<MapForm> FORMS = List.of(MapForm.SERPENTINE, MapForm.SPIRAL, MapForm.GENERATOR, MapForm.GALAXY);
    private static final int LEFT_W = 560;
    private static final int CARD_H = 186;
    private static final int THUMB_H = 112;

    private final EditorPage page;
    private final Runnable back;
    private final MapCanvas big = new MapCanvas();
    @Nullable
    private String previewHash;
    private Map<MapForm, LayoutEngine.Job> previews = Map.of();
    @Nullable
    private MapBook previewMap;
    @Nullable
    private CanvasLayout bigLayout;

    MapFormPage(EditorPage page, Runnable back) {
        this.page = page;
        this.back = back;
        big.setStatus(id -> QuestStatus.AVAILABLE, () -> 0);
        big.setMinimap(false);
        big.setInsets(12, 10, 10);
    }

    void scroll(double x, double y, double amount) {
        big.mouseScrolled(x, y, amount);
    }

    void render(Ui ui, int x, int y, int w, int h) {
        EditorBook editor = EditorBook.get();
        QuestBook book = editor.view();
        QuestBook players = editor.base();
        MapForms.Choice auto = MapForms.auto(book.meta(), List.copyOf(book.chapters().values()), List.copyOf(book.quests().values()));
        MapForm drafted = book.meta().mapForm();
        MapForm chosen = drafted == MapForm.AUTO ? auto.form() : drafted;
        MapForm live = MapForms.choose(players).form();
        if (!book.layoutHash().equals(previewHash)) {
            previewHash = book.layoutHash();
            previews = MapLayouts.preview(book);
            previewMap = MapBook.of(book);
        }
        float lx = x + 22;
        float pen = y + 14;
        boolean overBack = ui.over(lx, pen, 120, 18);
        ui.icon(Icons.Shape.CHEVRON_LEFT, lx, pen + 1, 14, ui.c(overBack ? "text.primary" : "link"));
        ui.label(EditorBook.tr("rysquests.ed.form.back"), lx + 20, pen + 8, 12.5f, Ui.MEDIUM, ui.c(overBack ? "text.primary" : "link"));
        ui.hit(lx, pen, 120, 18, back);
        pen += 18 + 10;
        ui.label(EditorBook.tr("rysquests.ed.form.title"), lx, pen + 11, 19, Ui.DISPLAY, ui.c("text.primary"));
        pen += 22 + 3;
        ui.label(EditorBook.tr("rysquests.ed.form.sub"), lx, pen + 7, 12, Ui.REGULAR, ui.c("text.muted"));
        pen += 14 + 10;
        float cw = (LEFT_W - 10) / 2f;
        for (int i = 0; i < FORMS.size(); i++) {
            MapForm form = FORMS.get(i);
            float cx = lx + (i % 2) * (cw + 10);
            float cy = pen + (i / 2) * (CARD_H + 10);
            card(ui, book, auto, form, cx, cy, cw, form == chosen, form == live, form == chosen && form != live);
        }
        pen += CARD_H * 2 + 10 + 10;
        // the colours of the dots
        boolean epochs = !book.meta().epochs().isEmpty();
        DetailPanel.rubric(ui, lx, pen, LEFT_W, EditorBook.tr(epochs ? "rysquests.ed.form.legend_epoch" : "rysquests.ed.form.legend_chapter"), null);
        pen += 18;
        float row = lx;
        List<String> names = epochs ? book.meta().epochs().stream().map(BookMeta.Epoch::title).map(editor::text).toList()
                : EditorSidebar.ordered(book).stream().filter(MapForms::onMap).map(Chapter::title).limit(12).toList();
        for (int i = 0; i < names.size(); i++) {
            String label = (epochs ? Ui.roman(i + 1) + " " : "") + names.get(i);
            float lw = 8 + 6 + ui.width(label, 11.5f, Ui.REGULAR) + 14;
            if (row + lw > lx + LEFT_W) {
                row = lx;
                pen += 19;
            }
            ui.circle(row, pen + 5, 8, ui.theme.category(i));
            ui.label(label, row + 14, pen + 9, 11.5f, Ui.REGULAR, ui.c("text.secondary"));
            row += lw;
        }
        pen += 19 + 10;
        ui.rect(lx, pen, LEFT_W, 1, ui.c("panel.border"));
        pen += 8;
        String autoName = EditorBook.tr("rysquests.ed.form." + auto.form().getSerializedName()).toLowerCase(java.util.Locale.ROOT);
        EdUi.setting(ui, lx, pen, LEFT_W, EditorBook.tr("rysquests.ed.form.auto_switch"), EditorBook.tr("rysquests.ed.form.auto_sub", autoName), drafted == MapForm.AUTO,
                () -> editor.setMapForm(drafted == MapForm.AUTO ? auto.form() : MapForm.AUTO));
        renderRight(ui, book, chosen, x + 22 + LEFT_W + 22, y + 14, x + w - 22 - (x + 22 + LEFT_W + 22), h - 28);
    }

    private void card(Ui ui, QuestBook book, MapForms.Choice auto, MapForm form, float x, float y, float w, boolean on, boolean live, boolean drafted) {
        String fit = unfit(book, auto, form);
        GridCard.of("").selected(on).onClick(fit == null ? () -> {
            if (!on) {
                EditorBook.get().setMapForm(form);
            }
        } : null).body((u, ix, iy, iw, ih) -> {
        }).render(ui, x, y, w, CARD_H);
        ui.fillCorners(x + 1, y + 1, w - 2, THUMB_H, 11, ui.c("map.bg"), true, true, false, false);
        if (fit != null) {
            ui.icon(Icons.Shape.CROSS, x + w / 2f - 8, y + THUMB_H / 2f - 18, 16, ui.c("text.muted"));
            ui.labelCentered(EditorBook.tr("rysquests.ed.form.unfit"), x + w / 2f, y + THUMB_H / 2f + 12, 12, Ui.REGULAR, ui.c("text.muted"));
        } else {
            thumb(ui, form, x + 8, y + 8, w - 16, THUMB_H - 16);
        }
        float ty = y + THUMB_H + 12;
        String name = EditorBook.tr("rysquests.ed.form." + form.getSerializedName());
        ui.label(name, x + 12, ty + 8, 13.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        float nx = x + 12 + ui.width(name, 13.5f, Ui.SEMIBOLD) + 8;
        if (live) {
            nx += EdUi.badge(ui, EditorBook.tr("rysquests.ed.form.live"), nx, ty + 8, false) + 6;
        }
        if (drafted) {
            EdUi.badge(ui, EditorBook.tr("rysquests.ed.form.drafted"), nx, ty + 8, true);
        }
        String reason = fit != null ? fit : reason(book, auto, form);
        List<String> lines = ui.wrap(reason, w - 24, 12, Ui.REGULAR, 2);
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x + 12, ty + 8 + 20 + i * 16, 12, Ui.REGULAR, ui.c("text.muted"));
        }
    }

    /** The dots of a form's preview, coloured by epoch, or the progress while it is laid out (picture 07b). */
    private void thumb(Ui ui, MapForm form, float x, float y, float w, float h) {
        LayoutEngine.Job job = previews.get(form);
        if (job == null || previewMap == null) {
            return;
        }
        if (!job.result.isDone() || job.result.isCompletedExceptionally()) {
            float p = (float) job.progress();
            ui.bar(x + w / 2f - 60, y + h / 2f, 120, 4, 2, p, ui.c("text.muted"));
            ui.labelCentered(EditorBook.tr(p > 0 ? "rysquests.ed.form.laying" : "rysquests.ed.form.queued"), x + w / 2f, y + h / 2f + 14, 11.5f, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        CanvasLayout layout = job.result.join();
        float sw = layout.maxX - layout.minX;
        float sh = layout.maxY - layout.minY;
        if (sw <= 0 || sh <= 0) {
            return;
        }
        float s = Math.min(w / sw, h / sh);
        float ox = x + (w - sw * s) / 2f;
        float oy = y + (h - sh * s) / 2f;
        List<MapBook.Q> quests = previewMap.quests();
        for (int i = 0; i < quests.size() && i < layout.x.length; i++) {
            int epoch = previewMap.chapters().get(quests.get(i).chapter()).epoch();
            int color = ui.theme.category(Math.max(0, epoch > 0 ? epoch - 1 : quests.get(i).chapter()));
            Draw.dot(ui.g, Math.round((ox + (layout.x[i] - layout.minX) * s) / 2f), Math.round((oy + (layout.y[i] - layout.minY) * s) / 2f), 1, color);
        }
    }

    /** Why a form does not fit the book, or null when it does. */
    @Nullable
    static String unfit(QuestBook book, MapForms.Choice auto, MapForm form) {
        return switch (form) {
            case SERPENTINE -> auto.chaptersWithEpoch() == 0 ? EditorBook.tr("rysquests.ed.form.unfit.serpentine") : null;
            case GALAXY -> auto.chapters() < MapForms.GALAXY_MIN_CHAPTERS || auto.crossLinks() > auto.links() * MapForms.FEW_LINKS * 4
                    ? EditorBook.tr("rysquests.ed.form.unfit.galaxy") : null;
            default -> null;
        };
    }

    /** The line of reason under a form, in the author's words. */
    static String reason(QuestBook book, MapForms.Choice auto, MapForm form) {
        return switch (form) {
            case SERPENTINE -> auto.chaptersWithEpoch() == auto.chapters() ? EditorBook.tr("rysquests.ed.form.why.serpentine_all", Plural.full("rysquests.ed.form.chapters", auto.chapters()))
                    : EditorBook.tr("rysquests.ed.form.why.serpentine_some", Plural.full("rysquests.ed.form.chapters", auto.chapters()), auto.chaptersWithEpoch());
            case SPIRAL -> {
                String last = EditorSidebar.ordered(book).stream().filter(MapForms::onMap).reduce((a, b) -> b).map(Chapter::title).orElse("");
                yield EditorBook.tr("rysquests.ed.form.why.spiral", last);
            }
            case GENERATOR -> EditorBook.tr("rysquests.ed.form.why.generator");
            case GALAXY -> EditorBook.tr("rysquests.ed.form.why.galaxy", auto.crossLinks(), auto.links());
            default -> "";
        };
    }

    private void renderRight(Ui ui, QuestBook book, MapForm chosen, float x, float y, float w, float h) {
        String name = EditorBook.tr("rysquests.ed.form." + chosen.getSerializedName());
        long chapters = book.chapters().values().stream().filter(MapForms::onMap).count();
        long quests = book.quests().values().stream().filter(q -> q.options().live() && q.chapter().map(id -> book.chapters().containsKey(id) && MapForms.onMap(book.chapters().get(id))).orElse(false)).count();
        float pen = y + 28 + 10 + 8;
        ui.label(name, x, pen, 15, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelFit(EditorBook.tr("rysquests.ed.form.as_players", Ui.counted("rysquests.ed.form.chapters", (int) chapters), Plural.full("rysquests.plural.quest", quests)),
                x + ui.width(name, 15, Ui.DISPLAY) + 10, pen, w - ui.width(name, 15, Ui.DISPLAY) - 10, 12, Ui.REGULAR, ui.c("text.muted"));
        pen += 14;
        float ph = Math.min(400, h - (pen - y) - 70);
        ui.box(x, pen, w, ph, 12, ui.c("map.bg"), ui.c("panel.border"));
        MapLayouts.State state = MapLayouts.get(book, chosen);
        if (state.ready() && state.layout() != bigLayout) {
            big.setLayout(book, state.map(), state.layout());
            bigLayout = state.layout();
            big.flyToBook();
        }
        if (!state.ready() && bigLayout != null && bigLayout.form != chosen) {
            big.setLayout(book, state.map(), null);
            bigLayout = null;
        }
        big.setLoading(bigLayout == null ? state.progress() : null, null);
        big.render(ui.g, Math.round(x + 1), Math.round(pen + 1), Math.round(w - 2), Math.round(ph - 2), ui.mouseX, ui.mouseY);
        pen += ph + 14;
        List<Chapter> off = book.chapters().values().stream().filter(c -> c.isStory() && c.offMap()).toList();
        DetailPanel.rubric(ui, x, pen, w, EditorBook.tr("rysquests.ed.form.off_map"), EditorBook.tr("rysquests.ed.form.off_map_note"));
        pen += 20;
        float row = x;
        for (Chapter chapter : off) {
            ResourceLocation id = chapter.id();
            row += EdUi.tag(ui, chapter.title(), row, pen, () -> EditorBook.get().editChapter(id, EditorBook.tr("rysquests.ed.log.chapter_map", chapter.title()),
                    work -> work.withChapter(work.chapter().withOffMap(false)))) + 6;
        }
        if (off.isEmpty()) {
            ui.label(EditorBook.tr("rysquests.ed.form.off_map_none"), x, pen + 12, 12, Ui.REGULAR, ui.c("text.dim"));
        }
    }
}
