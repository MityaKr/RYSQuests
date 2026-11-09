package works.repka.rysquests.client.hud;

import works.repka.rysquests.client.screen.Ui;

/** One card on the board over the world, in mockup pixels. Kept this small so that the board can move to RYSCore. */
public interface Sticker {
    String id();

    int height(Ui ui, int width);

    void render(Ui ui, int x, int y, int width);

    /** Higher comes first. */
    default int priority() {
        return 0;
    }
}
