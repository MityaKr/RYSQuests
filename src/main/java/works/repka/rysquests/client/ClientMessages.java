package works.repka.rysquests.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.network.BookMessagePayload;

/**
 * The server's latest word about what the player did (plan, picture 48). The book shows {@link #latest()} as its one message line
 * over the bottom of the page; a new message replaces the old one. Nothing goes above the hotbar (plan, section 3, HUD).
 */
public final class ClientMessages {
    /**
     * @param key  the lang key the server sent, which the book's message line uses to find the message's actions
     * @param args its arguments as the server sent them
     * @param at   when it came, from {@link System#currentTimeMillis()}
     */
    public record Message(String key, List<String> args, Component text, boolean ok, long at) {
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
        latest = new Message(payload.key(), List.copyOf(payload.args()), text, payload.ok(), System.currentTimeMillis());
    }

    /** Shows a message as if the server had sent it: the book's own refusals, and tests. */
    public static void show(String key, List<String> args, boolean ok) {
        receive(new BookMessagePayload(key, args, ok));
    }

    static void clear() {
        latest = null;
    }
}
