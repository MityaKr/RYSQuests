package works.repka.rysquests.client.screen;

/** A way of showing the open chapter, in the area between the chapter list and the reading column. */
interface BookView {
    void render(Ui ui, int x, int y, int w, int h);

    void scroll(double mouseX, double mouseY, double amount);

    default boolean drag(double deltaX, double deltaY) {
        return false;
    }
}
