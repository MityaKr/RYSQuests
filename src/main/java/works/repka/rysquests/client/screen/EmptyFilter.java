package works.repka.rysquests.client.screen;

import org.jetbrains.annotations.Nullable;

/**
 * A filter left nothing (picture 25d, plan section 2): where the grid or list was, one line of text and a link that brings "All"
 * back. Everything else on the page stays where it stood. Mockup: line 14 semibold, link 12.5 medium in the link color, gap 6,
 * the pair a little above the middle of the area.
 *
 * <pre>
 * EmptyFilter.render(ui, x, y, w, h, "Начатым квестам сейчас ничего не нужно из магазина", "Показать все товары", () -> filter = ALL);
 * </pre>
 */
final class EmptyFilter {
    private static final float LINE_PX = 14;
    private static final float LINK_PX = 12.5f;
    private static final float GAP = 6;
    private static final float RAISE = 30;

    private EmptyFilter() {
    }

    /** @param link the link's words, or null when nothing can be shown instead (shop history of a new player) */
    static void render(Ui ui, float x, float y, float w, float h, String line, @Nullable String link, @Nullable Runnable action) {
        float lineH = Ui.lineHeight(LINE_PX);
        float linkH = link == null ? 0 : Ui.lineHeight(LINK_PX);
        float total = lineH + (link == null ? 0 : GAP + linkH);
        float top = y + (h - total) / 2f - RAISE;
        float cx = x + w / 2f;
        ui.labelCentered(ui.fit(line, w - 32, LINE_PX, Ui.SEMIBOLD), cx, top + lineH / 2f, LINE_PX, Ui.SEMIBOLD, ui.c("text.primary"));
        if (link != null) {
            float linkW = ui.width(link, LINK_PX, Ui.MEDIUM);
            float linkY = top + lineH + GAP;
            boolean over = action != null && ui.over(cx - linkW / 2f, linkY, linkW, linkH);
            ui.labelCentered(link, cx, linkY + linkH / 2f, LINK_PX, Ui.MEDIUM, over ? ui.c("text.primary") : ui.c("link"));
            if (action != null) {
                ui.hit(cx - linkW / 2f, linkY, linkW, linkH, action);
            }
        }
    }
}
