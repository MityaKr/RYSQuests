package works.repka.rysquests.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.screen.BookScreen;

/** Key bindings of the mod. */
public final class QuestKeys {
    public static final KeyMapping OPEN_BOOK = new KeyMapping("key.rysquests.open_book", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.rysquests");

    private QuestKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BOOK);
    }

    public static void tick() {
        while (OPEN_BOOK.consumeClick()) {
            Minecraft.getInstance().setScreen(new BookScreen());
        }
    }
}
