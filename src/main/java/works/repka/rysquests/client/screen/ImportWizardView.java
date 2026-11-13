package works.repka.rysquests.client.screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientImport;
import works.repka.rysquests.integration.ftbq.FtbqFiles;
import works.repka.rysquests.integration.ftbq.ImportReport;
import works.repka.rysquests.network.FtbqImportErrorPayload;
import works.repka.rysquests.network.FtbqImportProgressPayload;
import works.repka.ryscore.ui.Icons;

/**
 * The FTB Quests import wizard (plan, section 5): two steps, "Перенос" and "Вид карты", in a bare header that names the window.
 * Opening it starts the import ({@link ClientImport}). While it runs the page shows how far it is; a file that cannot be read stops
 * it on the error page (picture 50) with "Перенести без этой главы"; then comes the report (06) and the choice of the map form
 * ({@link MapFormStep}, 07 and 07b). After the choice the book itself opens, closed to players (08).
 */
final class ImportWizardView implements BookView {
    private static final String P = FirstRun.P;
    private static final float PAD_X = 28;
    private static final float PAD_Y = 20;

    /** Set by tests that look at the pages without starting an import. */
    static boolean startSuppressed;

    private final BookScreen screen;
    private int step = 1;
    @Nullable
    private MapFormStep forms;

    ImportWizardView(BookScreen screen) {
        this.screen = screen;
        if (ClientImport.stage() != ClientImport.Stage.RUNNING && !startSuppressed) {
            ClientImport.start("", true);
        }
    }

    @Override
    public boolean bareHeader() {
        return true;
    }

    @Override
    public String headerTitle() {
        return FirstRunParts.t(P + "import.title");
    }

    @Override
    public void renderHeader(Ui ui, float x, float cy) {
        FirstRunParts.steps(ui, x + 18 + 40, cy, step);
    }

    int step() {
        return step;
    }

    @Nullable
    MapFormStep forms() {
        return forms;
    }

    /** "Дальше: вид карты". */
    void next() {
        if (forms == null) {
            forms = new MapFormStep(screen, () -> step = 1);
        }
        step = 2;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        ImportReport report = ClientImport.report();
        switch (ClientImport.stage()) {
            case FAILED -> {
                step = 1;
                renderError(ui, x, y, w, h);
            }
            case DONE -> {
                if (report == null) {
                    renderRunning(ui, x, y, w, h);
                } else if (step == 2 && forms != null) {
                    forms.render(ui, x, y, w, h);
                } else {
                    renderReport(ui, x, y, w, h, report);
                }
            }
            default -> renderRunning(ui, x, y, w, h);
        }
    }

    // --- running ---

    private void renderRunning(Ui ui, int x, int y, int w, int h) {
        float cx = x + w / 2f;
        FtbqImportProgressPayload progress = ClientImport.progress();
        String phase = progress == null ? FtbqImportProgressPayload.READ : progress.phase();
        String line = progress != null && progress.total() > 0
                ? FirstRunParts.t(P + "import.phase." + phase + "_n", Sidebar.full(progress.done()), Sidebar.full(progress.total()))
                : FirstRunParts.t(P + "import.phase." + phase);
        float ratio = progress != null && progress.total() > 0 ? progress.done() / (float) progress.total() : phaseRatio(phase);
        List<String> text = ui.wrap(FirstRunParts.t(P + "import.running_text"), 520, 13.5f, Ui.REGULAR, 3);
        float total = Ui.headingHeight(24) + 14 + text.size() * 20.25f + 14 + 4 + 9 + Ui.lineHeight(11.5f);
        float pen = y + (h - total) / 2f;
        ui.labelCentered(FirstRunParts.t(P + "import.running"), cx, pen + Ui.headingHeight(24) / 2f, 24, Ui.DISPLAY, ui.c("text.primary"));
        pen += Ui.headingHeight(24) + 14;
        for (int i = 0; i < text.size(); i++) {
            ui.labelCentered(text.get(i), cx, pen + 20.25f * i + 10.1f, 13.5f, Ui.REGULAR, ui.c("text.secondary"));
        }
        pen += text.size() * 20.25f + 14;
        ui.bar(cx - 160, pen, 320, 4, 2, ratio, ui.c("text.secondary"));
        pen += 4 + 9;
        ui.labelCentered(line, cx, pen + Ui.lineHeight(11.5f) / 2f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
    }

    /** The share of the whole import a phase without counts starts at. */
    private static float phaseRatio(String phase) {
        return switch (phase) {
            case FtbqImportProgressPayload.CONVERT -> 0.35f;
            case FtbqImportProgressPayload.WRITE -> 0.55f;
            case FtbqImportProgressPayload.RELOAD -> 0.75f;
            case FtbqImportProgressPayload.PROGRESS -> 0.9f;
            default -> 0.1f;
        };
    }

    // --- the error (picture 50) ---

    private void renderError(Ui ui, int x, int y, int w, int h) {
        FtbqImportErrorPayload error = ClientImport.error();
        if (error == null) {
            renderRunning(ui, x, y, w, h);
            return;
        }
        FtbqFiles.Failure failure = error.failure();
        float cx = x + w / 2f;
        boolean file = !failure.file().isEmpty();
        String text = FirstRunParts.t(P + (file && failure.skippable() ? "error.text_chapter" : file ? "error.text_file" : "error.text"));
        List<String> lines = ui.wrap(text, 520, 13.5f, Ui.REGULAR, 4);
        boolean chapters = error.chaptersTotal() > 0;
        float rowsH = (chapters ? 38 + 6 : 0) + 38;
        float textH = lines.size() * 20.25f;
        float total = 44 + 14 + Ui.headingHeight(24) + 14 + textH + 14 + rowsH + 14 + 6 + FirstRunParts.BIG_H;
        float pen = y + (h - total) / 2f;
        ui.icon(Icons.Shape.WARN, cx - 22, pen, 44, ui.c("state.bad"));
        pen += 44 + 14;
        ui.labelCentered(FirstRunParts.t(P + "error.title"), cx, pen + Ui.headingHeight(24) / 2f, 24, Ui.DISPLAY, ui.c("text.primary"));
        pen += Ui.headingHeight(24) + 14;
        for (int i = 0; i < lines.size(); i++) {
            ui.labelCentered(lines.get(i), cx, pen + 20.25f * i + 10.1f, 13.5f, Ui.REGULAR, ui.c("text.secondary"));
        }
        pen += textH + 14;
        float rowX = cx - 320;
        if (chapters) {
            FirstRunParts.row(ui, rowX, pen, 640, FirstRunParts.mark(FirstRunParts.Mark.DONE, ""), FirstRunParts.t(P + "error.read"), null, null, false, 38);
            ui.labelRight(FirstRunParts.t("rysquests.count.of", Sidebar.full(error.chaptersRead()), Sidebar.full(error.chaptersTotal())), rowX + 640 - 12,
                    pen + 19, 12, Ui.REGULAR, ui.c("text.muted"));
            pen += 38 + 6;
        }
        String where = file ? error.folder() + "/" + failure.file() : FirstRunParts.t(P + "error.no_file");
        String reason = reason(failure.reason());
        String detail = failure.line() > 0 ? FirstRunParts.t(P + "error.line", failure.line(), reason) : reason;
        float detailW = Math.min(300, ui.width(detail, 12, Ui.REGULAR));
        float whereW = 640 - 24 - FirstRunParts.STEP - 11 - detailW - 16;
        String shownWhere = ui.fit(where, whereW, 13, Ui.MEDIUM);
        FirstRunParts.row(ui, rowX, pen, 640, FirstRunParts.mark(FirstRunParts.Mark.FAILED, ""), shownWhere, null, null, false, 38);
        if (!shownWhere.equals(where)) {
            ui.tooltipIfOver(rowX, pen, 640 - detailW, 38, Component.literal(where));
        }
        ui.labelFit(detail, rowX + 640 - 12 - detailW, pen + 19, detailW, 12, Ui.REGULAR, ui.c("text.muted"));
        pen += 38 + 14 + 6;
        String show = FirstRunParts.t(P + "error.show");
        String retry = FirstRunParts.t(P + "error.retry");
        String skip = FirstRunParts.t(P + "error.skip");
        boolean canSkip = failure.skippable() && file;
        float buttonsW = FirstRunParts.bigWidth(ui, retry, false, Icons.Shape.REPEAT) + (file ? FirstRunParts.bigWidth(ui, show, false, null) + 8 : 0)
                + (canSkip ? FirstRunParts.bigWidth(ui, skip, true, null) + 8 : 0);
        float bx = cx - buttonsW / 2f;
        if (file) {
            Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve(error.folder());
            Path target = folder.resolve(failure.file()).getParent();
            bx += FirstRunParts.big(ui, show, bx, pen, false, null, false, FirstRunParts.t(P + "error.show_tip"),
                    () -> Util.getPlatform().openPath(target != null && Files.isDirectory(target) ? target : folder)) + 8;
        }
        bx += FirstRunParts.big(ui, retry, bx, pen, false, Icons.Shape.REPEAT, false, FirstRunParts.t(P + "error.retry_tip"), ClientImport::retry) + 8;
        if (canSkip) {
            FirstRunParts.big(ui, skip, bx, pen, true, null, false, FirstRunParts.t(P + "error.skip_tip"), ClientImport::skipFailedFile);
        }
    }

    /** What the reader said, in words: "не закрыта скобка". */
    static String reason(String reason) {
        String key = switch (reason) {
            case "unclosed object" -> "unclosed_object";
            case "unclosed array" -> "unclosed_array";
            case "unclosed string" -> "unclosed_string";
            case "unexpected end" -> "unexpected_end";
            case "trailing content" -> "trailing";
            case "expected a value" -> "no_value";
            case "no id" -> "no_id";
            case "not an object" -> "not_object";
            case "missing" -> "missing";
            case "no FTB Quests book" -> "no_book";
            case "an import is already running" -> "running";
            default -> null;
        };
        if (key != null) {
            return FirstRunParts.t(P + "error.reason." + key);
        }
        String colon = "expected ':' after key ";
        if (reason.startsWith(colon)) {
            return FirstRunParts.t(P + "error.reason.colon", reason.substring(colon.length()));
        }
        return reason;
    }

    // --- the report (picture 06) ---

    private void renderReport(Ui ui, int x, int y, int w, int h, ImportReport source) {
        FirstRun.Report report = FirstRun.report(source);
        float left = x + PAD_X;
        float inner = w - PAD_X * 2;
        float pen = y + PAD_Y;
        ui.label(FirstRunParts.t(P + "report.title"), left, pen + Ui.headingHeight(22) / 2f, 22, Ui.DISPLAY, ui.c("text.primary"));
        pen += Ui.headingHeight(22) + 3;
        List<String> sub = ui.wrap(FirstRunParts.t(P + "report.sub"), inner, 13, Ui.REGULAR, 2);
        for (int i = 0; i < sub.size(); i++) {
            ui.label(sub.get(i), left, pen + 9 + 18 * i, 13, Ui.REGULAR, ui.c("text.muted"));
        }
        pen += sub.size() * 18 + 14;
        float leftW = (inner - 16) / 2.15f;
        float rightX = left + leftW + 16;
        float rightW = inner - 16 - leftW;
        float col = pen;
        col += FirstRunParts.rubric(ui, left, col, leftW, FirstRunParts.t(P + "report.carried"), null) + 6;
        for (FirstRun.Row row : report.carried()) {
            col += drawRow(ui, left, col, leftW, row) + 6;
        }
        FirstRunParts.note(ui, left, col, leftW, 8, Icons.Shape.DOT,
                List.of(new FirstRunParts.Run(FirstRunParts.t(P + "report.again"), Ui.REGULAR, ui.c("text.secondary"))));
        col = pen;
        if (!report.placed().isEmpty()) {
            col += FirstRunParts.rubric(ui, rightX, col, rightW, FirstRunParts.t(P + "report.placed"), null) + 6;
            for (FirstRun.Row row : report.placed()) {
                col += drawRow(ui, rightX, col, rightW, row) + 6;
            }
            col += 4;
        }
        if (!report.manual().isEmpty()) {
            String hidden = report.hidden() > 0 ? FirstRunParts.counted(P + "report.hidden", report.hidden()) : null;
            col += FirstRunParts.rubric(ui, rightX, col, rightW, FirstRunParts.t(P + "report.manual"), hidden) + 6;
            for (FirstRun.Row row : report.manual()) {
                col += drawRow(ui, rightX, col, rightW, row) + 6;
            }
        }
        float actY = y + h - FirstRunParts.ACT_H;
        float cy = FirstRunParts.actions(ui, x, actY, w);
        String next = FirstRunParts.t(P + "report.next");
        float nextW = FirstRunParts.bigWidth(ui, next, true, Icons.Shape.CHEVRON_RIGHT);
        float nextX = x + w - 18 - nextW;
        FirstRunParts.big(ui, next, nextX, cy - FirstRunParts.BIG_H / 2f, true, Icons.Shape.CHEVRON_RIGHT, true, null, this::next);
        FirstRunParts.lockLine(ui, x + 18, cy, nextX - 20 - x - 18, FirstRunParts.t("rysquests.closed.title"),
                FirstRunParts.t(EmptyBookView.ftbPresent() ? P + "report.lock_ftb" : P + "report.lock"), 12.5f);
    }

    private static float drawRow(Ui ui, float x, float y, float w, FirstRun.Row row) {
        FirstRunParts.Lead lead = switch (row.badge()) {
            case DONE -> FirstRunParts.mark(FirstRunParts.Mark.DONE, "");
            case MAP -> FirstRunParts.tile(Icons.Shape.MAP);
            case OFF_MAP -> FirstRunParts.tile(Icons.Shape.FLAG);
            case COUNT -> FirstRunParts.mark(FirstRunParts.Mark.NUMBER, row.count() > 0 ? Sidebar.full(row.count()) : "!");
        };
        String title = FirstRunParts.str(row.title());
        String sub = row.sub() == null ? null : FirstRunParts.str(row.sub());
        String value = row.value() == null ? null : FirstRunParts.str(row.value());
        float h = FirstRunParts.row(ui, x, y, w, lead, title, sub, value, row.value() == null, 40);
        if (!row.quests().isEmpty()) {
            ui.tooltipIfOver(x, y, w, h, questsTip(row.quests()));
        }
        return h;
    }

    /** The quests of a row to fix, by name, for its tooltip: at most eight and how many more. */
    private static Component questsTip(List<String> ids) {
        List<String> names = new ArrayList<>();
        var book = ClientBook.current();
        for (String id : ids.stream().limit(8).toList()) {
            var location = net.minecraft.resources.ResourceLocation.tryParse(id);
            var quest = location == null ? null : book.quests().get(location);
            names.add(quest == null ? id : Component.translatable(quest.title()).getString());
        }
        String text = String.join("\n", names);
        if (ids.size() > 8) {
            text += "\n" + FirstRunParts.t(P + "report.more", Sidebar.full(ids.size() - 8));
        }
        return Component.literal(text);
    }

    // --- input, passed to the form step ---

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (step == 2 && forms != null) {
            forms.scroll(mouseX, mouseY, amount);
        }
    }

    @Override
    public boolean drag(double deltaX, double deltaY) {
        return step == 2 && forms != null && forms.drag(deltaX, deltaY);
    }

    @Override
    public void press(double x, double y, int button) {
        if (step == 2 && forms != null) {
            forms.press(x, y, button);
        }
    }

    @Override
    public boolean release(double x, double y, int button) {
        return step == 2 && forms != null && forms.release(x, y, button);
    }

    @Override
    public void closed() {
        if (forms != null) {
            forms.closed();
        }
    }
}
