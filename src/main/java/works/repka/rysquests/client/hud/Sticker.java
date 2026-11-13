package works.repka.rysquests.client.hud;

import works.repka.rysquests.client.screen.Ui;

/** One card over the world, in mockup pixels. Kept this small so that the board can move to RYSCore. */
public interface Sticker {
    String id();

    /** @param folded the stack is folded (key B): a card shows its head only */
    int height(Ui ui, int width, boolean folded);

    void render(Ui ui, int x, int y, int width, boolean folded);

    /** The width the card wants; the stack's cards take the stack's width. */
    default int width(Ui ui) {
        return StickerBoard.WIDTH;
    }

    /** Higher comes first. */
    default int priority() {
        return 0;
    }
}
