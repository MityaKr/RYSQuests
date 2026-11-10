package works.repka.rysquests.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.screen.TaskText;
import works.repka.rysquests.client.screen.Ui;
import works.repka.rysquests.data.MatrixSection;
import works.repka.ryscore.ui.Draw;

/** A pinned way of a matrix section: what it is and where in the section it comes from. */
final class WaySticker implements Sticker {
    private static final int HEIGHT = 58;
    private static final int PAD_LEFT = 17;
    private static final int PAD_RIGHT = 15;

    private final String key;
    private final MatrixSection.Pinned pinned;

    WaySticker(String key, MatrixSection.Pinned pinned) {
        this.key = key;
        this.pinned = pinned;
    }

    @Override
    public String id() {
        return key;
    }

    @Override
    public int height(Ui ui, int width) {
        return HEIGHT;
    }

    @Override
    public void render(Ui ui, int x, int y, int width) {
        ui.shadow(x, y, width, HEIGHT, 12, 0.4f);
        ui.box(x, y, width, HEIGHT, 12, Draw.alpha(ui.c("hud.bg"), 0.92f), ui.c("hud.border"));
        ui.fill(x, y + 5, 3, HEIGHT - 10, 1.5f, ui.c("accent"));
        float left = x + PAD_LEFT;
        float inner = width - PAD_LEFT - PAD_RIGHT;
        float cy = y + HEIGHT / 2f;
        ui.item(pinned.way().icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), left, cy - 11, 22);
        String name = Component.translatable(pinned.section().wayName(pinned.row(), pinned.column(), pinned.way())).getString();
        String where = Component.translatable(pinned.section().rowTitle(pinned.row())).getString() + " · " + Component.translatable(pinned.column().title()).getString();
        boolean plain = ui.theme.beveled();
        ui.labelFit(name, left + 30, cy - 8, inner - 30, 13, Ui.SEMIBOLD, ui.c(plain ? "tooltip.fg" : "text.primary"));
        ui.labelFit(where, left + 30, cy + 9, inner - 30, 11, Ui.REGULAR, ui.c(plain ? "tooltip.fg" : "text.muted"));
    }
}
