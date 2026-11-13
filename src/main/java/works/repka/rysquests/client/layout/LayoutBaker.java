package works.repka.rysquests.client.layout;

import java.util.concurrent.CompletableFuture;
import works.repka.rysquests.data.BookLayout;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.QuestBook;

/**
 * Lays the map of a whole book out: the hook the map fills in with {@link works.repka.rysquests.client.ClientLayout#setBaker}.
 * The data layer bakes, saves and sends layouts through it, and knows nothing of how a form is drawn.
 */
@FunctionalInterface
public interface LayoutBaker {
    /**
     * @param form the form to lay out in, never {@link MapForm#AUTO}
     * @return the layout, with {@link BookLayout#hash()} set to {@code book.layoutHash()}; it may be worked out in portions off
     *         the main thread, as long as the future completes
     */
    CompletableFuture<BookLayout> bake(QuestBook book, MapForm form);
}
