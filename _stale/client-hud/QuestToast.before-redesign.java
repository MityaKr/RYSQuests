package works.repka.rysquests.client.hud;

import net.minecraft.network.chat.Component;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.screen.Ui;
import works.repka.rysquests.data.Quest;
import works.repka.ryscore.ui.Draw;

/** "Quest complete", a short-lived card on the board, from {@code design/Hud.dc.html}. */
final class QuestToast implements Sticker {
    private static final int HEIGHT = 50;

    private final Quest quest;

    QuestToast(Quest quest) {
        this.quest = quest;
    }

    @Override
    public String id() {
        return "done:" + quest.id();
    }

    @Override
    public int height(Ui ui, int width) {
        return HEIGHT;
    }

    @Override
    public void render(Ui ui, int x, int y, int width) {
        ui.shadow(x, y, width, HEIGHT, 12, 0.4f);
        ui.box(x, y, width, HEIGHT, 12, Draw.alpha(ui.c("hud.bg"), 0.92f), ui.doneBorder());
        float cy = y + HEIGHT / 2f;
        ui.circle(x + 15, cy - 13, 26, ui.c("state.ok"));
        ui.check(x + 21, cy - 7, 14, ui.c("accent.text"));
        float textX = x + 51;
        float textW = width - 51 - 15;
        String title = Component.translatable("rysquests.toast.done", Component.translatable(quest.title())).getString();
        ui.labelFit(title, textX, cy - 8, textW, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.labelFit(Component.translatable("rysquests.toast.waiting", QuestKeys.label(QuestKeys.OPEN_BOOK)).getString(), textX, cy + 8, textW, 11, Ui.REGULAR, ui.c("text.muted"));
    }
}
