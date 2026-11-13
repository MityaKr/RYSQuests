package works.repka.rysquests.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.screen.TaskText;
import works.repka.rysquests.client.screen.Ui;
import works.repka.rysquests.data.MatrixSection;

/** A pinned way of a matrix section: what it is and where in the section it comes from. */
final class WaySticker implements Sticker {
    private static final int HEIGHT = 48;
    private static final int PAD_LEFT = 12;
    private static final int PAD_RIGHT = 12;

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
    public int height(Ui ui, int width, boolean folded) {
        return HEIGHT;
    }

    @Override
    public void render(Ui ui, int x, int y, int width, boolean folded) {
        HudLook.plate(ui, x, y, width, HEIGHT, 0.92f, false);
        float left = x + PAD_LEFT;
        float inner = width - PAD_LEFT - PAD_RIGHT;
        float cy = y + HEIGHT / 2f;
        ui.item(pinned.way().icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), left, cy - 12, 24);
        String name = Component.translatable(pinned.section().wayName(pinned.row(), pinned.column(), pinned.way())).getString();
        String where = Component.translatable(pinned.section().rowTitle(pinned.row())).getString() + " · " + Component.translatable(pinned.column().title()).getString();
        ui.labelFit(name, left + 32, cy - 8, inner - 32, HudLook.TITLE_PX, Ui.SEMIBOLD, HudLook.primary(ui));
        ui.labelFit(where, left + 32, cy + 8, inner - 32, HudLook.ROW_PX, Ui.REGULAR, HudLook.muted(ui));
    }
}
