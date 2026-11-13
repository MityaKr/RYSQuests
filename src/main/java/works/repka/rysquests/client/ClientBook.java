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
import works.repka.rysquests.data.BookAssets;
import works.repka.rysquests.data.BookSerializer;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.integration.ftbq.ImportedPack;
import net.neoforged.neoforge.network.PacketDistributor;
import works.repka.rysquests.network.BookSyncPayload;
import works.repka.rysquests.network.PublishPayload;

/** The client's copy of the book. Updated only from packets. */
public final class ClientBook {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBook.class);
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private static volatile QuestBook current = QuestBook.EMPTY;
    private static volatile boolean received;
    private static boolean reloadedForImport;

    private ClientBook() {
    }

    public static QuestBook current() {
        return current;
    }

    /**
     * Whether the server has sent the book since joining. Until then the book is loading, which is not the same as an empty book:
     * a player of a closed book gets an empty one (plan, pictures 04 and 04b).
     */
    public static boolean received() {
        return received;
    }

    /** Opens the book to players or closes it again; only an operator may, and the server answers with an editor result. */
    public static void requestPublish(boolean open) {
        PacketDistributor.sendToServer(new PublishPayload(open));
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
                        received = true;
                        ClientAssets.apply(book.assets());
                        LOGGER.info("Received book: {} chapters, {} quests", book.chapters().size(), book.quests().size());
                        reloadForNewTranslations(book);
                        ClientLayout.onBook(book);
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
        received = false;
        ClientAssets.apply(BookAssets.EMPTY);
    }
}
