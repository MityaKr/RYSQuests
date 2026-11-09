package works.repka.rysquests.client;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.RegistryAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.BookSerializer;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.integration.ftbq.ImportedPack;
import works.repka.rysquests.network.BookSyncPayload;

/** The client's copy of the book. Updated only from packets. */
public final class ClientBook {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBook.class);
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private static volatile QuestBook current = QuestBook.EMPTY;
    private static boolean reloadedForImport;

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
                        reloadForNewTranslations(book);
                    }
                }, Minecraft.getInstance());
    }

    /** A freshly imported pack brings translations the client has not loaded yet; one resource reload picks them up. */
    private static void reloadForNewTranslations(QuestBook book) {
        boolean missing = book.chapters().values().stream().findFirst().filter(chapter -> !I18n.exists(chapter.title())).isPresent();
        if (missing && !reloadedForImport && Files.isRegularFile(ImportedPack.root().resolve("pack.mcmeta"))) {
            reloadedForImport = true;
            Minecraft.getInstance().reloadResourcePacks();
        }
    }

    static void clear() {
        GENERATION.incrementAndGet();
        current = QuestBook.EMPTY;
    }
}
