package works.repka.rysquests.client.screen;

import org.jetbrains.annotations.Nullable;

/**
 * Something the book shows in its window: the content of a tab, a page such as the settings, a first-run step. All coordinates are
 * in mockup pixels, see {@link Ui}. Clicks reach a view through the hits it registers while drawing ({@code ui.hit}); everything
 * else comes through the methods below, all optional but {@link #render}.
 *
 * <p>How a view gets into the book: a tab is one line in {@link BookTabs}, a page is opened with {@link BookScreen#openPage}.
 */
interface BookView {
    /** Draws the view into the rectangle under the header. */
    void render(Ui ui, int x, int y, int w, int h);

    /** The mouse wheel over the view, {@code amount} positive when turned up. */
    default void scroll(double mouseX, double mouseY, double amount) {
    }

    /** The mouse moved with a button held, by this much in mockup pixels. @return true when the view used it */
    default boolean drag(double deltaX, double deltaY) {
        return false;
    }

    /** A key press the header and the frame did not take. @return true when the view used it */
    default boolean key(int key) {
        return false;
    }

    /** A typed character, for views with their own text fields. */
    default boolean typed(char c) {
        return false;
    }

    /** A mouse button went down at this point, before any hit runs. */
    default void press(double x, double y, int button) {
    }

    /** A mouse button went up. @return true when the view used it */
    default boolean release(double x, double y, int button) {
        return false;
    }

    /** The book closes or the view is replaced: free what it holds (3D scenes, for example). */
    default void closed() {
    }

    /** A page that wants the bare header, the book's name and close only: a first-run step. */
    default boolean bareHeader() {
        return false;
    }

    /**
     * The name a page with a bare header gives the window instead of the book's ("Перенос из FTB Quests", picture 06), or null to
     * keep the book's name.
     */
    @Nullable
    default String headerTitle() {
        return null;
    }

    /** Draws after the name in a bare header, as the import's steps do (picture 06). {@code x} is where the name ends. */
    default void renderHeader(Ui ui, float x, float cy) {
    }

    /**
     * Where the book's message line lies over this view, {@code {x, y, w, h}} of the view's own column, or null for the whole view:
     * a view with a detail panel on the right keeps the line over its middle column (picture 48).
     */
    @Nullable
    default int[] messageArea(int x, int y, int w, int h) {
        return null;
    }
}
