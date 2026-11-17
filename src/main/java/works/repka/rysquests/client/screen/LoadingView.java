package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.network.chat.Component;
import works.repka.ryscore.ui.Draw;

/**
 * The book has not come from the server yet (picture 04b, plan section 2): "Книга загружается", not "Квестов пока нет". There is
 * no progress number, the client does not know how much is left. A turning arc over a ring, the title and one paragraph.
 */
final class LoadingView implements BookView {
    private static final float TITLE_PX = 20;
    private static final float TEXT_PX = 13.5f;
    private static final float TEXT_LINE = 20.25f;
    private static final int SPINNER = 44;
    private static final int GAP = 14;
    private static final long TURN_MS = 900;

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        float cx = x + w / 2f;
        List<String> text = ui.wrap(Component.translatable("rysquests.loading.text").getString(), 520, TEXT_PX, Ui.REGULAR, 4);
        float titleH = Ui.headingHeight(TITLE_PX);
        float total = SPINNER + GAP + titleH + GAP + text.size() * TEXT_LINE;
        float pen = y + (h - total) / 2f;
        float sx = cx / 2f;
        float sy = (pen + SPINNER / 2f) / 2f;
        Draw.ring(ui.g, sx, sy, 20 / 2f, 4 / 2f, ui.c("button.border"));
        float start = (System.currentTimeMillis() % TURN_MS) / (float) TURN_MS * 360f - 90f;
        Draw.arc(ui.g, sx, sy, 18 / 2f, start, 90, 4 / 2f, ui.c("text.primary"));
        pen += SPINNER + GAP;
        ui.labelCentered(Component.translatable("rysquests.loading.title").getString(), cx, pen + titleH / 2f, TITLE_PX, Ui.DISPLAY, ui.c("text.primary"));
        pen += titleH + GAP;
        for (int i = 0; i < text.size(); i++) {
            ui.labelCentered(text.get(i), cx, pen + TEXT_LINE * i + TEXT_LINE / 2f, TEXT_PX, Ui.REGULAR, ui.c("text.secondary"));
        }
    }
}
