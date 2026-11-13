package works.repka.rysquests.client.screen;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A row of filter chips beside a page's first line (mockup {@code .chip}, pictures 23, 25, 27b, 28): text chips 28 high, the chosen
 * one on the plate of the active tab. Unlike {@link Chips} they have no border and never wrap: a page has at most a few of them.
 *
 * <pre>
 * float w = FilterChips.render(ui, x, y, List.of("Все", "В работе", "Полученные"), selected, index -> filter = index, false);
 * </pre>
 */
final class FilterChips {
    static final int HEIGHT = 28;
    private static final int GAP = 4;
    private static final float PX = 12.5f;
    private static final float PAD = 11;
    private static final float PAD_TIGHT = 8;

    private FilterChips() {
    }

    static float width(Ui ui, List<String> labels, boolean tight) {
        float w = GAP * Math.max(0, labels.size() - 1);
        for (String label : labels) {
            w += ui.width(label, PX, Ui.REGULAR) + (tight ? PAD_TIGHT : PAD) * 2;
        }
        return w;
    }

    /** Draws the chips from (x, y). @return their width */
    static float render(Ui ui, float x, float y, List<String> labels, int selected, IntConsumer pick, boolean tight) {
        float pen = x;
        float pad = tight ? PAD_TIGHT : PAD;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            float w = ui.width(label, PX, Ui.REGULAR) + pad * 2;
            boolean on = i == selected;
            boolean over = ui.over(pen, y, w, HEIGHT);
            if (on) {
                ui.fill(pen, y, w, HEIGHT, 6, ui.c("tab.active.bg"));
            } else if (over) {
                ui.fill(pen, y, w, HEIGHT, 6, ui.c("button.bg"));
            }
            ui.label(label, pen + pad, y + HEIGHT / 2f, PX, Ui.REGULAR, ui.c(on ? "text.primary" : over ? "text.secondary" : "text.muted"));
            int index = i;
            ui.hit(pen, y, w, HEIGHT, () -> pick.accept(index));
            pen += w + GAP;
        }
        return pen - GAP - x;
    }
}
