package works.repka.rysquests.client.screen;

import net.minecraft.network.chat.Component;
import works.repka.rysquests.client.ClientBook;
import works.repka.ryscore.ui.Kit;

/**
 * "Книга закрыта для игроков" (picture 08, plan section 4): a narrow line under the header while the book is closed, for operators
 * only. It does not cover the map. Its buttons "К проверке" and "Открыть игрокам" are both grey: the amber "Открыть игрокам" lives
 * only in the question that follows (picture 10).
 */
final class ClosedBookLine {
    static final int HEIGHT = 38;

    private ClosedBookLine() {
    }

    /** Whether the line stands under the header: an operator looking at a closed book that has quests. */
    static boolean shown() {
        return BookRole.operator() && !BookData.book().meta().published() && !BookData.book().quests().isEmpty();
    }

    static void render(Ui ui, BookScreen screen, int x, int y, int w) {
        ui.rect(x, y, w, HEIGHT, ui.c("sidebar.bg"));
        ui.rect(x, y + HEIGHT - 1, w, 1, ui.c("panel.border"));
        float cy = y + (HEIGHT - 1) / 2f;
        ui.lock(x + 16 + 2, cy - 6.5f, 13, ui.c("text.secondary"));
        String open = Component.translatable("rysquests.closed.open").getString();
        String check = Component.translatable("rysquests.closed.check").getString();
        float openW = ui.buttonWidth(open, 12, Ui.MEDIUM, 10);
        float checkW = ui.buttonWidth(check, 12, Ui.MEDIUM, 10);
        float right = x + w - 12;
        ui.button(open, right - openW, cy - 14, openW, 28, 9, 12, Ui.MEDIUM, 10, Ui.Style.PLAIN, () -> askToOpen(screen));
        right -= openW + 10;
        ui.button(check, right - checkW, cy - 14, checkW, 28, 9, 12, Ui.MEDIUM, 10, Ui.Style.PLAIN, screen::toggleEditor);
        right -= checkW + 10;
        float textX = x + 16 + 15 + 10;
        String bold = Component.translatable("rysquests.closed.title").getString();
        long drafts = BookData.book().quests().values().stream().filter(quest -> quest.options().draft()).count();
        String rest = Component.translatable(EmptyBookView.ftbPresent() ? "rysquests.closed.text_ftb" : "rysquests.closed.text").getString()
                + (drafts > 0 ? " " + Ui.counted("rysquests.closed.drafts", (int) drafts) : "");
        ui.label(bold, textX, cy, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        float boldW = ui.width(bold + " ", 12.5f, Ui.SEMIBOLD);
        ui.labelFit(rest, textX + boldW, cy, right - textX - boldW, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
    }

    /** The question of picture 10 in its short form: how much players will see, "Пока нет" or "Открыть игрокам". */
    static void askToOpen(BookScreen screen) {
        long chapters = BookData.chapters().size();
        long quests = BookData.book().quests().values().stream().filter(quest -> quest.options().live()).count();
        screen.ask(new Kit.Dialog(Component.translatable("rysquests.closed.ask_title").getString())
                .text(Component.translatable("rysquests.closed.ask_text", chapters, quests).getString())
                .no(Component.translatable("rysquests.closed.ask_no").getString(), screen.answer(() -> { }))
                .yes(Component.translatable("rysquests.closed.open").getString(), screen.answer(() -> ClientBook.requestPublish(true))));
    }
}
