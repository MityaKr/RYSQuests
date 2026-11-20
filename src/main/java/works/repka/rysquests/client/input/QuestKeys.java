package works.repka.rysquests.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.hud.StickerBoard;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.network.QuestActionPayload;

/**
 * Key bindings of the mod: K opens the book. "Закрепить" and "Свернуть закреплённое" are there to bind but have no key by default
 * (logic review 19.11): P opened the game's social interactions too and B the backpack of Sophisticated Backpacks, since every
 * action on a key fires. Pinning is the pin of the book, folding the cards is a setting of the book. The G key that handed in
 * everything ready is gone (plan, section 8); "Hand in all ready" is a button of the book.
 */
public final class QuestKeys {
    private static final String CATEGORY = "key.categories.rysquests";
    public static final KeyMapping OPEN_BOOK = new KeyMapping("key.rysquests.open_book", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping PIN = new KeyMapping("key.rysquests.pin", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final KeyMapping BOARD = new KeyMapping("key.rysquests.board", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    private QuestKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BOOK);
        event.register(PIN);
        event.register(BOARD);
        // players who launched 0.2.10 or older still have P and B in options.txt: the core clears them once if untouched
        works.repka.ryscore.action.KeyDefaults.retire("rysquests.pin.p", PIN, "key.keyboard.p");
        works.repka.ryscore.action.KeyDefaults.retire("rysquests.board.b", BOARD, "key.keyboard.b");
    }

    /** A letter key by its Latin name, the way it is printed in the book, whatever the keyboard layout is. */
    public static Component label(KeyMapping mapping) {
        String name = mapping.getKey().getName();
        String prefix = "key.keyboard.";
        boolean letter = name.startsWith(prefix) && name.length() == prefix.length() + 1;
        return letter ? Component.literal(name.substring(prefix.length()).toUpperCase(Locale.ROOT)) : mapping.getTranslatedKeyMessage();
    }

    public static void tick() {
        while (OPEN_BOOK.consumeClick()) {
            // While a message about a finished quest is on screen, the key opens the book on that quest.
            if (!works.repka.ryscore.ui.Notices.press(works.repka.ryscore.ui.Notice.QUESTS)) {
                BookScreen.open();
            }
        }
        while (BOARD.consumeClick()) {
            StickerBoard.toggleCollapsed();
        }

    }

    public static void sendAll(int action) {
        PacketDistributor.sendToServer(new QuestActionPayload(action, QuestActionPayload.ALL, 0, List.of()));
    }
}
