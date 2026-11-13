package works.repka.rysquests.client.screen;

import net.minecraft.network.chat.Component;

/**
 * "Новая книга" (picture 09a): the book's name and one optional question, "Как устроена сборка?". A stub of the frame: the first-run
 * screen replaces its body. It opens as a page over the empty book with a bare header; "Назад" closes it.
 */
final class NewBookView implements BookView {
    private final BookScreen screen;

    NewBookView(BookScreen screen) {
        this.screen = screen;
    }

    @Override
    public boolean bareHeader() {
        return true;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        StubPage.render(ui, x, y, w, h, Component.translatable("rysquests.first.new_book").getString(), screen::closePage);
    }
}
