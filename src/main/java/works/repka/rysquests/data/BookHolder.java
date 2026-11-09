package works.repka.rysquests.data;

import java.util.List;
import works.repka.rysquests.migrate.Migration;
import works.repka.rysquests.network.BookSyncPayload;

/** The book currently loaded on the server, replaced as a whole on each reload. */
public final class BookHolder {
    public record Loaded(QuestBook book, BookReport report, BookSyncPayload payload, List<Migration> migrations) {
    }

    private static volatile Loaded current = new Loaded(QuestBook.EMPTY, BookReport.EMPTY, new BookSyncPayload("", new byte[0]), List.of());

    private BookHolder() {
    }

    public static Loaded current() {
        return current;
    }

    static void set(Loaded loaded) {
        current = loaded;
    }
}
