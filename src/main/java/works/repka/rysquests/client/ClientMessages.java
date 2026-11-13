package works.repka.rysquests.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.network.BookMessagePayload;

/**
 * The server's latest word about what the player did (plan, picture 48). The book shows {@link #latest()} as its one message line;
 * it also goes above the hotbar at once, for when the book is closed.
 */
public final class ClientMessages {
    /** @param at when it came, from {@link System#currentTimeMillis()} */
    public record Message(Component text, boolean ok, long at) {
    }

    @Nullable
    private static Message latest;

    private ClientMessages() {
    }

    /** The message the book has not dismissed yet, or null. */
    @Nullable
    public static Message latest() {
        return latest;
    }

    /** The book's cross: the message is gone until the next one comes. */
    public static void dismiss() {
        latest = null;
    }

    static void receive(BookMessagePayload payload) {
        Component text = Component.translatable(payload.key(), payload.args().toArray());
        latest = new Message(text, payload.ok(), System.currentTimeMillis());
        // Above the hotbar as before, so that nothing is lost while the book does not show its own line yet.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(text, true);
        }
    }

    static void clear() {
        latest = null;
    }
}
