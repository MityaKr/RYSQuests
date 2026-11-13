package works.repka.rysquests.client.screen;

import net.minecraft.network.chat.Component;

/**
 * The FTB Quests import wizard (pictures 06, 07, 07b, 50): report, map form, error. A stub of the frame: the first-run screen
 * replaces its body and drives {@code ClientImport}. It opens as a page over the empty book with a bare header; "Назад" closes it.
 */
final class ImportWizardView implements BookView {
    private final BookScreen screen;

    ImportWizardView(BookScreen screen) {
        this.screen = screen;
    }

    @Override
    public boolean bareHeader() {
        return true;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        StubPage.render(ui, x, y, w, h, Component.translatable("rysquests.first.import").getString(), screen::closePage);
    }
}
