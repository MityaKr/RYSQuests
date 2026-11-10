package works.repka.rysquests.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Scenes of Ponder, the mod Create brings along. Safe to call without it. */
public final class Ponders {
    private static final String MOD = "ponder";
    @Nullable
    private static Screen returnTo;
    private static boolean pondering;

    private Ponders() {
    }

    public static boolean installed() {
        return ModList.get().isLoaded(MOD);
    }

    /** @param id the item a scene is about, as Ponder keeps them */
    public static boolean exists(ResourceLocation id) {
        return installed() && PonderBridge.exists(id);
    }

    public static void open(ResourceLocation id) {
        if (exists(id)) {
            returnTo = Minecraft.getInstance().screen;
            pondering = false;
            PonderBridge.open(id);
        }
    }

    /** Ponder closes everything when it is left, and a reader expects to be back on the page. */
    public static void tick() {
        if (returnTo == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (PonderBridge.isPonder(mc.screen)) {
            pondering = true;
        } else if (pondering) {
            // Ponder puts its screen up a moment after being asked, so only leaving it counts.
            Screen back = returnTo;
            returnTo = null;
            pondering = false;
            if (mc.screen == null && mc.level != null) {
                mc.setScreen(back);
            }
        }
    }
}
