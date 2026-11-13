package works.repka.rysquests.client.screen;

import net.minecraft.network.chat.Component;

/** The body of a page that another screen fills later: its title, one line saying so, and "Назад". */
final class StubPage {
    private StubPage() {
    }

    static void render(Ui ui, int x, int y, int w, int h, String title, Runnable back) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        ui.labelCentered(title, cx, cy - 30, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelCentered(Component.translatable("rysquests.first.stub").getString(), cx, cy, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        String label = Component.translatable("rysquests.page.back_plain").getString();
        float bw = ui.buttonWidth(label, 12.5f, Ui.MEDIUM, 14);
        ui.button(label, cx - bw / 2f, cy + 22, bw, 34, 9, 12.5f, Ui.MEDIUM, 14, Ui.Style.PLAIN, back);
    }
}
