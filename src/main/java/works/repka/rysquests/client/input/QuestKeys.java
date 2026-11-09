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

/** Key bindings of the mod. {@code P} rather than {@code A} pins, because RYScope claims {@code A} over items. */
public final class QuestKeys {
    private static final String CATEGORY = "key.categories.rysquests";
    public static final KeyMapping OPEN_BOOK = new KeyMapping("key.rysquests.open_book", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping SUBMIT_READY = new KeyMapping("key.rysquests.submit_ready", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping PIN = new KeyMapping("key.rysquests.pin", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);

    public static final KeyMapping BOARD = new KeyMapping("key.rysquests.board", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    private QuestKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BOOK);
        event.register(SUBMIT_READY);
        event.register(PIN);
        event.register(BOARD);
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
            Minecraft.getInstance().setScreen(new BookScreen());
        }
        while (BOARD.consumeClick()) {
            StickerBoard.toggleCollapsed();
        }
        while (SUBMIT_READY.consumeClick()) {
            sendAll(QuestActionPayload.SUBMIT_ALL);
        }
    }

    public static void sendAll(int action) {
        PacketDistributor.sendToServer(new QuestActionPayload(action, QuestActionPayload.ALL, 0, List.of()));
    }
}
