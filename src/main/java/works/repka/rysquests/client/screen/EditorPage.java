package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Type;

/**
 * A page of the editor: a view of the book whose unsaved work is the editor's draft ({@link EditorBook}), so that the header shows
 * "Сохранить · N", undo and "Открыть игрокам" over it (pictures 09, 33). Text fields, keys and the open list of a select are handled
 * here once for every page.
 */
abstract class EditorPage implements BookView, EditorSession {
    final BookScreen screen;
    final EdUi.Fields fields = new EdUi.Fields();
    final EdUi.Choices choices = new EdUi.Choices();

    EditorPage(BookScreen screen) {
        this.screen = screen;
    }

    static EditorBook book() {
        return EditorBook.get();
    }

    static String tr(String key, Object... args) {
        return EditorBook.tr(key, args);
    }

    // --- the header's questions ---

    @Override
    public int unsaved() {
        return book().unsaved();
    }

    @Override
    public void save() {
        fields.blur();
        book().save(screen, null);
    }

    @Override
    public boolean canUndo() {
        return book().canUndo();
    }

    @Override
    public void undo() {
        fields.blur();
        book().undo();
    }

    @Override
    public void publish() {
        ClosedBookLine.askToOpen(screen);
    }

    // --- input every page shares ---

    @Override
    public void press(double x, double y, int button) {
        fields.press(x, y);
        if (button == 0) {
            fields.blur();
            book().endTyping();
        }
    }

    @Override
    public boolean drag(double deltaX, double deltaY) {
        return false;
    }

    @Override
    public boolean typed(char c) {
        return fields.typed(c);
    }

    @Override
    public boolean key(int key) {
        if (key == 256 && choices.anyOpen()) {
            choices.close();
            return true;
        }
        if (fields.typing()) {
            if (!fields.key(key)) {
                fieldKey(key);
            }
            // while a field has the keyboard, no key of the book (K, /, Backspace) acts
            return true;
        }
        if (key == 90 && net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            undo();
            return true;
        }
        return false;
    }

    /** A key a focused field did not take: Enter, Tab. @return true when the page used it */
    boolean fieldKey(int key) {
        return false;
    }

    /** The edits of the draft as a short list in a question (pictures 33b and the restore question), in GUI units. */
    static void drawLines(GuiGraphics g, List<String> lines, int x, int y, int w) {
        var theme = ThemeManager.get();
        float pen = y + 4.5f;
        for (String line : lines) {
            Icons.draw(g, Icons.Shape.PENCIL, x, pen - 3.25f, 6.5f, theme.c("text.secondary"));
            Draw.label(g, Draw.ellipsize(line, w - 10, 12.5f, Type.Weight.REGULAR), x + 10, pen, 12.5f, Type.Weight.REGULAR, theme.c("text.secondary"));
            pen += 9;
        }
    }
}
