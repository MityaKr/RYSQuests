package works.repka.rysquests.client.screen;

import java.net.URI;
import java.net.URISyntaxException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens a web address from a page of the book as the game's chat opens one: not at all when the player turned links off in the chat
 * settings, through the game's own "Открыть ссылку?" with its warning when they ask to confirm (the default), at once otherwise. The
 * book comes back after the question.
 */
final class WebLinks {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebLinks.class);

    private WebLinks() {
    }

    static void open(String url) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.chatLinks().get()) {
            return;
        }
        URI uri;
        try {
            // The same check as chat: http or https only.
            uri = Util.parseAndValidateUntrustedUri(url);
        } catch (URISyntaxException e) {
            LOGGER.debug("A page links to an address that does not open: {}", url, e);
            return;
        }
        if (!mc.options.chatLinksPrompt().get()) {
            Util.getPlatform().openUri(uri);
            return;
        }
        Screen back = mc.screen;
        mc.setScreen(new ConfirmLinkScreen(open -> {
            if (open) {
                Util.getPlatform().openUri(uri);
            }
            mc.setScreen(back);
        }, url, false));
    }
}
