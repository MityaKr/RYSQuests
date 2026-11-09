package works.repka.rysquests.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.BookSerializer;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.network.BookSyncPayload;

/** The client's copy of the book. Updated only from packets. */
public final class ClientBook {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBook.class);
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private static volatile QuestBook current = QuestBook.EMPTY;

    private ClientBook() {
    }

    public static QuestBook current() {
        return current;
    }

    /** Decodes in the background; a newer packet that arrives meanwhile wins over this one. */
    static void receive(BookSyncPayload payload, RegistryAccess registries) {
        int generation = GENERATION.incrementAndGet();
        CompletableFuture.supplyAsync(() -> BookSerializer.decode(payload.data(), payload.hash(), registries), Util.backgroundExecutor())
                .whenCompleteAsync((book, failure) -> {
                    if (failure != null) {
                        LOGGER.error("Could not decode the quest book", failure);
                    } else if (generation == GENERATION.get()) {
                        current = book;
                        LOGGER.info("Received book: {} chapters, {} quests", book.chapters().size(), book.quests().size());
                    }
                }, Minecraft.getInstance());
    }

    static void clear() {
        GENERATION.incrementAndGet();
        current = QuestBook.EMPTY;
    }
}
