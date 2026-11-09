package works.repka.rysquests.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/** One card on the board over the world. Kept this small so that the board can move to RYSCore. */
public interface Sticker {
    String id();

    int height(int width);

    void render(GuiGraphics g, int x, int y, int width);

    /** Higher comes first. */
    default int priority() {
        return 0;
    }
}
