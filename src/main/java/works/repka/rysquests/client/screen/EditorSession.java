package works.repka.rysquests.client.screen;

/**
 * The editor's unsaved work, as the header shows it in editor mode (pictures 09, 10, 33): "Редактор" pressed, "Открыть игрокам" while
 * the book is closed, undo, and the one amber button "Сохранить · N". A page of the editor implements this; while such a page is
 * open, the header switches to editor mode by itself.
 */
interface EditorSession {
    /** Edits not saved yet: the N of "Сохранить · N"; 0 greys the button out. */
    int unsaved();

    void save();

    boolean canUndo();

    void undo();

    /** "Открыть игрокам": asks before opening the book (picture 10). By default it sends the request straight away. */
    default void publish() {
        works.repka.rysquests.client.ClientBook.requestPublish(true);
    }
}
