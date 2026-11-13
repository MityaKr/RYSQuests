package works.repka.rysquests.client.hud;

import net.minecraft.client.KeyMapping;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.screen.Ui;
import works.repka.ryscore.ui.Draw;

/** What the cards over the world share (mockup {@code .hud}, {@code .toast}, {@code .kbd}): the plate, the key cap, the colors. */
final class HudLook {
    static final float TITLE_PX = 12.5f;
    static final float TITLE_LINE = 15.5f;
    static final float ROW_PX = 11.5f;

    private HudLook() {
    }

    static void plate(Ui ui, float x, float y, float w, float h, float alpha, boolean shadow) {
        if (shadow) {
            ui.shadow(x, y, w, h, 12, 0.4f);
        }
        ui.box(x, y, w, h, 12, Draw.alpha(ui.c("hud.bg"), alpha), ui.c("hud.border"));
    }

    /** A key cap ("K"). @return its width */
    static float key(Ui ui, KeyMapping key, float right, float cy) {
        String label = QuestKeys.label(key).getString();
        float w = keyWidth(ui, key);
        ui.box(right - w, cy - 8, w, 16, 5, ui.c("button.bg"), ui.c("button.border"));
        ui.label(label, right - w + 5, cy, 11, Ui.REGULAR, muted(ui));
        return w;
    }

    static float keyWidth(Ui ui, KeyMapping key) {
        return ui.width(QuestKeys.label(key).getString(), 11, Ui.REGULAR) + 10;
    }

    /** Text colors: the bevelled theme draws the cards as tooltips, light on dark. */
    static int primary(Ui ui) {
        return ui.c(ui.theme.beveled() ? "tooltip.fg" : "text.primary");
    }

    static int secondary(Ui ui) {
        return ui.c(ui.theme.beveled() ? "tooltip.fg" : "text.secondary");
    }

    static int muted(Ui ui) {
        return ui.c(ui.theme.beveled() ? "tooltip.fg" : "text.muted");
    }
}
