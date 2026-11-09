package works.repka.rysquests.client.screen;

/** A way of showing the open chapter. All coordinates are in mockup pixels, see {@link Ui}. */
interface BookView {
    void render(Ui ui, int x, int y, int w, int h);

    void scroll(double mouseX, double mouseY, double amount);

    default boolean drag(double deltaX, double deltaY) {
        return false;
    }

    default boolean key(int key) {
        return false;
    }
}
