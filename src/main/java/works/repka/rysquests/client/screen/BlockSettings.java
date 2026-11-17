package works.repka.rysquests.client.screen;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Kit;
import works.repka.rysquests.block.ComparatorMode;

/**
 * What both shop block windows share (shop plan, step M7): a row with a switch and two lines of words (mockup 06, the limits), and
 * the block's own settings at the bottom of the right column: what a comparator reads and whether the owner hears when the block
 * stops. Every number is in mockup pixels.
 */
final class BlockSettings {
    /** A switch row of mockup 06: the switch, a title and a muted line under it. */
    static final float ROW_H = 30;
    static final float ROW_STEP = 38;
    /** The switch, 18×10 in units of the interface, 36×20 in mockup pixels. */
    private static final float SWITCH_W = 36;
    private static final float SWITCH_H = 20;
    private static final float GAP = 11;

    private BlockSettings() {
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /**
     * A row with a switch on the left, a title and a line under it. A click anywhere on the switch or the words flips it.
     *
     * @param room how wide the words may be
     * @param sub the muted line, or null
     * @param subAction what a click on the part of the line after the last "·" does, or null (mockup 06: "сбросить счётчик")
     */
    static void switchRow(Ui ui, float x, float y, float room, boolean on, String title, @Nullable String sub, @Nullable Runnable subAction, Runnable flip) {
        float switchY = y + (ROW_H - SWITCH_H) / 2f;
        boolean hot = ui.over(x, y, SWITCH_W + 12 + room, ROW_H);
        Kit.toggle(ui.g, ui.theme, Math.round(x / 2f), Math.round(switchY / 2f), on, hot);
        float textX = x + SWITCH_W + 12;
        ui.labelFit(title, textX, y + 7, room, 13, Ui.MEDIUM, ui.c(on ? "text.primary" : "text.secondary"));
        ui.hit(x, y, SWITCH_W + 12 + room, ROW_H, flip);
        if (sub == null) {
            return;
        }
        int dot = subAction == null ? -1 : sub.lastIndexOf(" · ");
        if (dot < 0) {
            ui.labelFit(sub, textX, y + 23, room, 11, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        String head = sub.substring(0, dot + 3);
        String link = sub.substring(dot + 3);
        float headW = ui.width(head, 11, Ui.REGULAR);
        float linkW = ui.width(link, 11, Ui.REGULAR);
        boolean overLink = ui.over(textX + headW, y + 16, linkW, 14);
        ui.label(head, textX, y + 23, 11, Ui.REGULAR, ui.c("text.muted"));
        ui.label(link, textX + headW, y + 23, 11, Ui.REGULAR, ui.c(overLink ? "text.primary" : "link"));
        ui.hit(textX + headW - 2, y + 14, linkW + 4, 18, subAction);
    }

    /** The height of the block's settings: a rubric, the comparator's row and the notices' switch. */
    static float height() {
        return Ui.lineHeight(11.5f) + 8 + Segments.HEIGHT + GAP + ROW_H;
    }

    /**
     * The block's settings (decision on question 17 and the comparator of step M7).
     *
     * @param fillLabel what "fill" means for this block: "по буферу", "по слоту"
     */
    static void render(Ui ui, float x, float y, float w, ComparatorMode mode, String fillLabel, boolean notify, Consumer<ComparatorMode> pick, Runnable flipNotify) {
        float pen = y;
        float rubricH = Ui.lineHeight(11.5f);
        ui.caps(tr("rysquests.block.settings"), x, pen + rubricH / 2f, ui.c("text.dim"));
        pen += rubricH + 8;
        List<Segments.Item> items = List.of(Segments.item(tr("rysquests.block.comparator.state")), Segments.item(fillLabel));
        int at = mode == ComparatorMode.FILL ? 1 : 0;
        float sw = Segments.width(items, at);
        ui.labelFit(tr("rysquests.block.comparator"), x, pen + Segments.HEIGHT / 2f, w - sw - 12, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.tooltipIfOver(x, pen, w - sw - 12, Segments.HEIGHT, Component.translatable("rysquests.block.comparator.tip"));
        Segments.render(ui, x + w - sw, pen, items, at, index -> pick.accept(index == 1 ? ComparatorMode.FILL : ComparatorMode.STATE));
        pen += Segments.HEIGHT + GAP;
        switchRow(ui, x, pen, w - SWITCH_W - 12, notify, tr("rysquests.block.notify"), tr("rysquests.block.notify.sub"), null, flipNotify);
    }
}
