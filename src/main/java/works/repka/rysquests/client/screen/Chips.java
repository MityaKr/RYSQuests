package works.repka.rysquests.client.screen;

import java.util.List;
import java.util.function.Consumer;
import works.repka.ryscore.ui.Draw;

/** The row of filter chips the section screens share. Chips that do not fit go to the next line. */
final class Chips {
    static final int HEIGHT = 27;
    static final int ROW = 33;
    private static final int GAP = 6;

    /** @param key what the filter compares, empty for "all" @param count how many it holds, negative to show no number */
    record Chip(String key, String label, int count) {
    }

    private Chips() {
    }

    /** @return where the chips ended: the next free x and the top of the last row */
    static float[] render(Ui ui, List<Chip> chips, String selected, Consumer<String> pick, float x, float y, float w) {
        float pen = x;
        float row = y;
        for (Chip chip : chips) {
            String count = chip.count < 0 ? "" : String.valueOf(chip.count);
            float labelW = ui.width(count.isEmpty() ? chip.label : chip.label + " ", 12, Ui.REGULAR);
            float chipW = labelW + ui.width(count, 12, Ui.REGULAR) + 24;
            if (pen + chipW > x + w && pen > x) {
                pen = x;
                row += ROW;
            }
            boolean on = chip.key.equals(selected);
            boolean over = ui.over(pen, row, chipW, HEIGHT);
            ui.box(pen, row, chipW, HEIGHT, HEIGHT / 2f, on || over ? ui.c("button.bg") : 0, on ? ui.c("button.border") : ui.c("panel.border"));
            int fg = ui.c(on ? "text.primary" : "button.fg");
            float cy = row + HEIGHT / 2f;
            ui.label(chip.label, pen + 12, cy, 12, Ui.REGULAR, fg);
            ui.label(count, pen + 12 + labelW, cy, 12, Ui.REGULAR, Draw.alpha(fg, 0.6f));
            ui.hit(pen, row, chipW, HEIGHT, () -> pick.accept(chip.key));
            pen += chipW + GAP;
        }
        return new float[] {pen, row};
    }
}
