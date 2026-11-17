package works.repka.rysquests.data;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import works.repka.rysquests.migrate.Migration;
import works.repka.rysquests.network.BookSyncPayload;

/** The book currently loaded on the server, replaced as a whole on each reload. */
public final class BookHolder {
    /** Who counts as an operator: may edit the book and sees it whole, closed or not. */
    public static final int OPERATOR = 2;

    /**
     * @param payload the whole book, for operators
     * @param playerPayload what other players get: nothing while the book is closed, and never drafts or the archive
     */
    public record Loaded(QuestBook book, BookReport report, BookSyncPayload payload, BookSyncPayload playerPayload, List<Migration> migrations) {
        public BookSyncPayload payloadFor(ServerPlayer player) {
            return player.hasPermissions(OPERATOR) ? payload : playerPayload;
        }
    }

    private static final BookSyncPayload NOTHING = new BookSyncPayload("", new byte[0]);
    private static volatile Loaded current = new Loaded(QuestBook.EMPTY, BookReport.EMPTY, NOTHING, NOTHING, List.of());

    private BookHolder() {
    }

    public static Loaded current() {
        return current;
    }

    static void set(Loaded loaded) {
        current = loaded;
    }
}
