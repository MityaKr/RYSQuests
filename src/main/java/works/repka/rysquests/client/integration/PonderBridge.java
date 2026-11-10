package works.repka.rysquests.client.integration;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/** The only class that touches Ponder. Reached through {@link Ponders}, so it never loads without the mod. */
final class PonderBridge {
    private PonderBridge() {
    }

    static boolean exists(ResourceLocation id) {
        return PonderIndex.getSceneAccess().doScenesExistForId(id);
    }

    static boolean isPonder(Screen screen) {
        return screen instanceof PonderUI;
    }

    static void open(ResourceLocation id) {
        ScreenOpener.transitionTo(PonderUI.of(id));
    }
}
