package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientImport;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.integration.ftbq.ImportReport;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;

/**
 * "Открыть книгу игрокам?" (picture 10, plan section 4): before the book opens, the question says whether the check found errors,
 * how many chapters and quests players will see and in how many tabs, what stays hidden, and how to close the book again. The only
 * amber "Открыть игрокам" lives here; it sends {@link ClientBook#requestPublish}.
 */
final class PublishQuestion {
    private static final String P = FirstRun.P + "publish.";
    /** The question's width in mockup pixels; the dialog takes interface units, half of that. */
    private static final int WIDTH = 500;
    private static final float PAD = 20;
    private static final float PX = 12.5f;
    private static final float LINE = 17.5f;
    private static final float GAP = 12;
    private static final float TEXT_X = 15 + 10;

    private PublishQuestion() {
    }

    private enum Mark {
        OK, WARN, EYE, LOCK, INFO
    }

    private record Point(Mark mark, String bold, String text) {
    }

    /**
     * The one window of the book's lock: "Открыть игрокам" from the closed book's line, the header and "Проверка" while the book is
     * closed, and "Закрыть для игроков…" of "Проверка" while it is open.
     */
    static void ask(BookScreen screen) {
        QuestBook book = BookData.book();
        if (book.meta().published()) {
            askClose(screen);
            return;
        }
        FirstRun.Publish facts = FirstRun.publish(book);
        List<Point> points = new ArrayList<>();
        if (facts.clean()) {
            points.add(new Point(Mark.OK, FirstRunParts.t(P + "clean"), FirstRunParts.t(P + "clean_sub")));
        } else {
            points.add(new Point(Mark.WARN, FirstRunParts.counted(P + "issues", facts.broken() + facts.stuck()), FirstRunParts.t(P + "issues_sub")));
        }
        List<BookTabs.Tab> tabs = playerTabs(book);
        String tabNames = String.join(FirstRunParts.t(FirstRun.P + "join.and"), tabs.stream().map(tab -> "«" + tab.label() + "»").toList());
        String tabsText = tabs.isEmpty() ? "" : FirstRunParts.t(P + "tabs", FirstRunParts.counted(P + "tabs_n", tabs.size()), tabNames);
        ImportReport report = ClientImport.report();
        String progress = report != null && report.teams() > 0 ? FirstRunParts.t(P + "progress", FirstRunParts.counted(P + "teams", report.teams())) + " " : "";
        points.add(new Point(Mark.EYE, FirstRunParts.t(P + "see", FirstRunParts.counted(P + "chapters", facts.chapters()), FirstRunParts.counted(P + "quests", facts.quests())),
                progress + tabsText));
        if (facts.drafts() > 0) {
            points.add(new Point(Mark.LOCK, FirstRunParts.counted(P + "hidden", facts.drafts()), FirstRunParts.t(P + "hidden_sub")));
        }
        points.add(new Point(Mark.INFO, FirstRunParts.t(P + "close"), FirstRunParts.t(P + "close_sub")));
        float inner = WIDTH - PAD;
        float textW = inner - TEXT_X;
        float height = 0;
        List<List<String>> wrapped = new ArrayList<>();
        for (Point point : points) {
            List<String> lines = new ArrayList<>(works.repka.ryscore.ui.Draw.wrapLabel(point.bold(), textW / 2f, PX, Ui.SEMIBOLD, 2));
            int boldLines = lines.size();
            if (!point.text().isEmpty()) {
                lines.addAll(works.repka.ryscore.ui.Draw.wrapLabel(point.text(), textW / 2f, PX, Ui.REGULAR, 4));
            }
            lines.add(0, String.valueOf(boldLines));
            wrapped.add(lines);
            height += (lines.size() - 1) * LINE;
        }
        height += GAP * (points.size() - 1) + 6;
        int bodyH = Math.round(height / 2f);
        screen.ask(new Kit.Dialog(FirstRunParts.t("rysquests.closed.ask_title"))
                .body(bodyH, (g, x, y, w, mx, my) -> {
                    Ui ui = new Ui(g, mx, my, new ArrayList<>(), new ArrayList<>(), new Kit.Hits());
                    float pen = y * 2f + 4;
                    for (int i = 0; i < points.size(); i++) {
                        List<String> lines = wrapped.get(i);
                        int boldLines = Integer.parseInt(lines.getFirst());
                        mark(ui, points.get(i).mark(), x * 2f, pen + 2);
                        for (int l = 1; l < lines.size(); l++) {
                            boolean bold = l <= boldLines;
                            ui.label(lines.get(l), x * 2f + TEXT_X, pen + LINE * (l - 1) + LINE / 2f, PX, bold ? Ui.SEMIBOLD : Ui.REGULAR,
                                    ui.c(bold ? "text.primary" : "text.secondary"));
                        }
                        pen += (lines.size() - 1) * LINE + GAP;
                    }
                })
                .width(WIDTH / 2)
                .no(FirstRunParts.t("rysquests.closed.ask_no"), screen.answer(() -> { }))
                .yes(FirstRunParts.t("rysquests.closed.open"), screen.answer(() -> ClientBook.requestPublish(true))));
    }

    /** "Закрыть книгу для игроков?": players see "Квестов пока нет" again, progress stays. */
    private static void askClose(BookScreen screen) {
        screen.ask(new Kit.Dialog(FirstRunParts.t("rysquests.ed.check.close_title"))
                .text(FirstRunParts.t("rysquests.ed.check.close_text"))
                .no(FirstRunParts.t("rysquests.ed.check.close_no"), screen.answer(() -> { }))
                .yes(FirstRunParts.t("rysquests.ed.check.close_yes"), screen.answer(() -> ClientBook.requestPublish(false))));
    }

    /** The tabs players will have: those with something in them, matrices aside, which are only for the author. */
    private static List<BookTabs.Tab> playerTabs(QuestBook book) {
        return BookRole.asPlayer(() -> BookTabs.visible(book));
    }

    private static void mark(Ui ui, Mark mark, float x, float y) {
        switch (mark) {
            case OK -> ui.icon(Icons.Shape.CHECK, x, y, 15, ui.c("state.ok"));
            case WARN -> ui.icon(Icons.Shape.WARN, x, y, 15, ui.c("state.warn"));
            case EYE -> ui.icon(Icons.Shape.EYE, x, y, 15, ui.c("text.secondary"));
            case LOCK -> ui.lock(x + 2, y + 1, 13, ui.c("text.secondary"));
            case INFO -> FirstRunParts.infoIcon(ui, x, y, 15, ui.c("text.secondary"));
        }
    }
}
