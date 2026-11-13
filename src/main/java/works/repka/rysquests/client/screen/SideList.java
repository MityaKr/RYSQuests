package works.repka.rysquests.client.screen;

import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;

/**
 * The plain left column of a page that is not the map (mockup {@code .side}, pictures 23, 24, 28): a rubric on top, rows with a count,
 * group rubrics, a footer. The map's contents with epochs is {@link Sidebar}; this is the simple list the guides and the achievements
 * share. Stateless: the page keeps its own scroll.
 */
final class SideList {
    static final int WIDTH = 244;
    static final int ROW_H = 32;
    static final int SUB_H = 30;
    static final int HEAD_H = 40;
    static final int PAD = 8;

    private SideList() {
    }

    /** The column's plate and its right border. */
    static void plate(Ui ui, float x, float y, float h) {
        ui.rect(x, y, WIDTH, h, ui.c("sidebar.bg"));
        ui.rect(x + WIDTH - 1, y, 1, h, ui.c("panel.border"));
    }

    /** The rubric on top ("ГАЙДЫ"). @return its height */
    static float head(Ui ui, float x, float y, String text) {
        ui.caps(text, x + 14, y + HEAD_H / 2f, ui.c("text.dim"));
        return HEAD_H;
    }

    /** A rubric of a group ("ПО ТЕМАМ"). @return its height */
    static float group(Ui ui, float x, float y, String text) {
        ui.caps(text, x + PAD + 10, y + 12 + Ui.lineHeight(11) / 2f, ui.c("text.dim"));
        return 12 + Ui.lineHeight(11) + 5;
    }

    /**
     * One row (mockup {@code .srow}). @param sub the smaller row of a group @param count on the right, or null
     * @return its height
     */
    static float row(Ui ui, float x, float y, String label, @Nullable String count, boolean on, boolean sub, @Nullable Icons.Shape icon,
            @Nullable Runnable action) {
        float h = sub ? SUB_H : ROW_H;
        float rx = x + PAD;
        float rw = WIDTH - 1 - PAD * 2;
        boolean over = action != null && ui.over(rx, y, rw, h);
        if (on) {
            ui.fill(rx, y, rw, h, 8, ui.c("tab.active.bg"));
        } else if (over) {
            ui.fill(rx, y, rw, h, 8, ui.c("button.bg"));
        }
        float px = sub ? 12.5f : 13;
        float pen = rx + (sub ? 14 : 10);
        float cy = y + h / 2f;
        int fg = ui.c(on ? "text.primary" : "text.secondary");
        if (icon != null) {
            ui.icon(icon, pen, cy - 7.5f, 15, fg);
            pen += 15 + 9;
        }
        float countW = count == null ? 0 : ui.width(count, 11.5f, Ui.REGULAR) + 8;
        ui.labelFit(label, pen, cy, rx + rw - 10 - countW - pen, px, on ? Ui.MEDIUM : Ui.REGULAR, fg);
        if (count != null) {
            ui.labelRight(count, rx + rw - 10, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        if (action != null) {
            ui.hit(rx, y, rw, h, action);
        }
        return h;
    }

    /** The footer's top border; the page draws inside it. @return the footer's top */
    static float footer(Ui ui, float x, float bottom, float h) {
        float top = bottom - h;
        ui.rect(x, top, WIDTH - 1, 1, ui.c("panel.border"));
        return top;
    }
}
