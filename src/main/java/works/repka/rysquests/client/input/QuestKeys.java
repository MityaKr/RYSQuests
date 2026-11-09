package works.repka.rysquests.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.network.QuestActionPayload;

/** Key bindings of the mod. {@code P} rather than {@code A} pins, because RYScope claims {@code A} over items. */
public final class QuestKeys {
    private static final String CATEGORY = "key.categories.rysquests";
    public static final KeyMapping OPEN_BOOK = new KeyMapping("key.rysquests.open_book", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping SUBMIT_READY = new KeyMapping("key.rysquests.submit_ready", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping PIN = new KeyMapping("key.rysquests.pin", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);

    private QuestKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BOOK);
        event.register(SUBMIT_READY);
        event.register(PIN);
    }

    public static void tick() {
        while (OPEN_BOOK.consumeClick()) {
            Minecraft.getInstance().setScreen(new BookScreen());
        }
        while (SUBMIT_READY.consumeClick()) {
            sendAll(QuestActionPayload.SUBMIT_ALL);
        }
    }

    public static void sendAll(int action) {
        PacketDistributor.sendToServer(new QuestActionPayload(action, QuestActionPayload.ALL, 0, List.of()));
    }
}
