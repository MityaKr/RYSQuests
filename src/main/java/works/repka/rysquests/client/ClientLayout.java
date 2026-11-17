package works.repka.rysquests.client;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.layout.LayoutBaker;
import works.repka.rysquests.data.BookLayout;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.network.LayoutBakeRequestPayload;
import works.repka.rysquests.network.LayoutSavePayload;

/**
 * The baked map on the client. A player uses the layout that came with the book and lays the map out only when there is none or
 * it is stale. An operator's client also bakes and saves: "Save" in the editor, "Save layout" in the check, and
 * {@code /rysquests layout bake}. The laying out itself is the map's, given through {@link #setBaker}.
 */
public final class ClientLayout {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLayout.class);

    @Nullable
    private static LayoutBaker baker;
    /** A bake the server asked for while this client still held an older book: done when the book it meant arrives. */
    @Nullable
    private static String pendingHash;

    private ClientLayout() {
    }

    /** The map registers how it lays a book out. */
    public static void setBaker(LayoutBaker newBaker) {
        baker = newBaker;
    }

    /** The layout that came with the book, if it fits the book as it is now. */
    public static Optional<BookLayout> baked() {
        return ClientBook.current().freshLayout();
    }

    /** The form the book is shown in: the one its settings name, or the automatic one. */
    public static MapForm form(QuestBook book) {
        return MapForms.choose(book).form();
    }

    /**
     * Lays the current book out in its form and sends the result to be written as {@code layout.json}. Only an operator's save is
     * accepted; the server answers with an editor result.
     *
     * @return false when nothing can lay the map out yet
     */
    public static boolean bakeAndSave() {
        QuestBook book = ClientBook.current();
        if (baker == null) {
            return false;
        }
        MapForm form = form(book);
        baker.bake(book, form).whenCompleteAsync((layout, failure) -> {
            if (failure != null) {
                LOGGER.error("Could not lay the map out", failure);
            } else if (layout.hash().equals(ClientBook.current().layoutHash())) {
                PacketDistributor.sendToServer(new LayoutSavePayload(layout));
            }
        }, Minecraft.getInstance());
        return true;
    }

    static void onBakeRequest(LayoutBakeRequestPayload payload) {
        if (payload.layoutHash().equals(ClientBook.current().layoutHash())) {
            bakeOrSay();
        } else {
            pendingHash = payload.layoutHash();
        }
    }

    /** A new book arrived: a bake that waited for it can run now. */
    static void onBook(QuestBook book) {
        if (pendingHash != null && pendingHash.equals(book.layoutHash())) {
            pendingHash = null;
            bakeOrSay();
        }
    }

    private static void bakeOrSay() {
        if (!bakeAndSave() && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("rysquests.layout.no_baker"), false);
        }
    }

    static void clear() {
        pendingHash = null;
    }
}
