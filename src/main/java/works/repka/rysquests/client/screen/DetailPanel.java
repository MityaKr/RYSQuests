package works.repka.rysquests.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;

/**
 * The detail panel on the right: a quest, a chapter, an offer, an achievement, an issue of the check (mockup {@code .panel}, pictures
 * 11, 16, 17, 25, 28). The shell only: a scrolled body the caller fills, and at the bottom one main action, the one amber button of
 * the screen ("Как сделать: Электромеханизм"), or a state in words where there is nothing to press ("Идёт само: 3 задачи").
 *
 * <p>Keep one instance per view (it holds the scroll), draw it every frame and pass it the wheel:
 *
 * <pre>
 * private final DetailPanel panel = new DetailPanel();
 * panel.render(ui, x, y, w, h, (ui2, bx, by, bw) -> {
 *     float pen = by + DetailPanel.header(ui2, bx, by, bw, stack, "Электромеханизм", "Ток · основной · шаг 7 из 15", pin);
 *     pen += 12 + DetailPanel.rubric(ui2, bx, pen, bw, "Что сделать", "0 из 1");
 *     ...
 *     return pen - by;                       // the body's height, for the scroll
 * }, DetailPanel.Action.button("Как сделать: Электромеханизм", () -> ...));
 * // in scroll(): if (panel.contains(mouseX, mouseY)) panel.scroll(amount);
 * </pre>
 */
final class DetailPanel {
    /** Width of the panel in the mockups; pages may use 300 to 360. */
    static final int WIDTH = 360;
    static final int PAD_X = 18;
    static final int PAD_TOP = 16;
    static final int GAP = 12;
    private static final int ACTION_PAD_Y = 12;
    private static final int BUTTON_H = 42;
    private static final int WHEEL = 48;
    private static final int LINK_GAP = 8;

    /** The body of the panel, drawn at (x, y) in width w. */
    @FunctionalInterface
    interface Body {
        /** @return the height of what was drawn */
        float render(Ui ui, float x, float y, float w);
    }

    /**
     * What stands at the bottom: the main button, a state in words, or nothing.
     *
     * @param label  the button's words, which say what it does
     * @param action null for a state in words: the label is then drawn as muted text, nothing to press
     * @param plain  a grey button instead of the amber one, for a page that has its amber button elsewhere
     */
    record Action(String label, @Nullable Runnable action, boolean plain, @Nullable String link, @Nullable Runnable linkAction) {
        Action(String label, @Nullable Runnable action, boolean plain) {
            this(label, action, plain, null, null);
        }

        static Action button(String label, Runnable action) {
            return new Action(label, action, false);
        }

        static Action plainButton(String label, Runnable action) {
            return new Action(label, action, true);
        }

        static Action state(String words) {
            return new Action(words, null, false);
        }

        /** A link centered under the button or the state ("Рецепт вместо покупки", picture 25). */
        Action withLink(String words, Runnable onClick) {
            return new Action(label, action, plain, words, onClick);
        }
    }

    private final Scroll scroll = new Scroll();
    private float left;
    private float top;
    private float right;
    private float bottom;
    private float bodyBottom;

    /** Draws the panel; the left border is its own. */
    void render(Ui ui, float x, float y, float w, float h, Body body, @Nullable Action action) {
        left = x;
        top = y;
        right = x + w;
        bottom = y + h;
        ui.rect(x, y, w, h, ui.c("panel.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float linkH = action == null || action.link() == null ? 0 : LINK_GAP + Ui.lineHeight(12.5f);
        float actionH = action == null ? 0 : ACTION_PAD_Y * 2 + (action.action() == null ? Ui.lineHeight(12.5f) + 10 : BUTTON_H) + linkH + 1;
        float bodyH = h - actionH;
        bodyBottom = y + bodyH;
        int clipX = Math.round(x + 1);
        ui.clip(clipX, Math.round(y), Math.round(w - 1), Math.round(bodyH));
        int offset = scroll.offset();
        float content = body.render(ui, x + 1 + PAD_X, y + PAD_TOP - offset, w - 1 - PAD_X * 2);
        ui.unclip();
        scroll.content(content + PAD_TOP + GAP, bodyH);
        if (action != null) {
            float ay = y + bodyH;
            ui.rect(x + 1, ay, w - 1, 1, ui.c("panel.border"));
            float bx = x + 1 + PAD_X;
            float bw = w - 1 - PAD_X * 2;
            float mainH = actionH - 1 - linkH;
            if (action.action() == null) {
                ui.labelCentered(ui.fit(action.label(), bw, 12.5f, Ui.REGULAR), bx + bw / 2f, ay + 1 + mainH / 2f, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            } else {
                ui.button(action.label(), bx, ay + 1 + ACTION_PAD_Y, bw, BUTTON_H, 9, 13.5f, Ui.SEMIBOLD, 20,
                        action.plain() ? Ui.Style.PLAIN : Ui.Style.ACCENT, action.action());
            }
            if (action.link() != null) {
                float lh = Ui.lineHeight(12.5f);
                float ly = action.action() == null ? ay + 1 + mainH - ACTION_PAD_Y + LINK_GAP : ay + 1 + ACTION_PAD_Y + BUTTON_H + LINK_GAP;
                float lw = ui.width(action.link(), 12.5f, Ui.MEDIUM);
                boolean over = ui.over(bx + bw / 2f - lw / 2f, ly, lw, lh);
                ui.labelCentered(action.link(), bx + bw / 2f, ly + lh / 2f, 12.5f, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
                if (action.linkAction() != null) {
                    ui.hit(bx + bw / 2f - lw / 2f, ly, lw, lh, action.linkAction());
                }
            }
        }
    }

    /** The bottom of the body's visible area, for a block that stands at the foot of the body (shop: last operations). */
    float bodyBottom() {
        return bodyBottom;
    }

    boolean contains(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    void scroll(double amount) {
        scroll.wheel(amount, WHEEL);
    }

    /** Back to the top: call when the panel starts showing something else. */
    void reset() {
        scroll.reset();
    }

    /**
     * The panel's head (mockup {@code .qh}): icon 46 on its plate, title 18, a line under it, and on the right a pin and a cross when
     * given. @return its height
     */
    static float header(Ui ui, float x, float y, float w, @Nullable ItemStack icon, String title, @Nullable String sub,
            @Nullable Runnable pin, boolean pinned, @Nullable Runnable close) {
        float size = 46;
        float pen = x;
        if (icon != null) {
            ui.plate(x, y, size, size, 6);
            ui.item(icon, x + 7, y + 7, 32);
            pen += size + 12;
        }
        float buttons = 0;
        float bx = x + w;
        if (close != null) {
            bx -= 28;
            boolean over = ui.over(bx, y + 9, 28, 28);
            ui.icon(Icons.Shape.CROSS, bx + 7, y + 16, 14, ui.c(over ? "text.primary" : "text.muted"));
            ui.hit(bx, y + 9, 28, 28, close);
            buttons += 28 + 8;
        }
        if (pin != null) {
            bx -= 34 + (close != null ? 8 : 0);
            boolean over = ui.over(bx, y + 6, 34, 34);
            ui.box(bx, y + 6, 34, 34, 9, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c(pinned ? "text.secondary" : "button.border"));
            ui.icon(Icons.Shape.PIN, bx + 9, y + 15, 16, ui.c(pinned ? "text.primary" : "text.secondary"));
            ui.tooltipIfOver(bx, y + 6, 34, 34, Component.translatable(pinned ? "rysquests.book.unpin" : "rysquests.book.pin"));
            ui.hit(bx, y + 6, 34, 34, pin);
            buttons += 34 + 8;
        }
        float textW = x + w - buttons - pen;
        java.util.List<String> lines = ui.wrap(title, textW, 18, Ui.DISPLAY, 2);
        float titleLine = Ui.headingHeight(18) - 2;
        // The line under the title wraps to a second line rather than being cut ("Жетоны · лимит 3 в день, обновится в полночь сервера").
        java.util.List<String> subLines = sub == null ? java.util.List.of() : ui.wrap(sub, textW, 12, Ui.REGULAR, 2);
        float subH = sub == null ? 0 : 3 + subLines.size() * Ui.lineHeight(12);
        float textH = lines.size() * titleLine + subH;
        float blockH = Math.max(icon != null ? size : 0, textH);
        float ty = y + (blockH - textH) / 2f;
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), pen, ty + titleLine * i + titleLine / 2f, 18, Ui.DISPLAY, ui.c("text.primary"));
        }
        if (!String.join(" ", lines).equals(title)) {
            ui.tooltipIfOver(pen, ty, textW, lines.size() * titleLine, Component.literal(title));
        }
        for (int i = 0; i < subLines.size(); i++) {
            ui.labelFit(subLines.get(i), pen, ty + lines.size() * titleLine + 3 + i * Ui.lineHeight(12) + Ui.lineHeight(12) / 2f, textW, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        if (sub != null && subLines.size() == 2 && !String.join(" ", subLines).equals(sub)) {
            ui.tooltipIfOver(pen, ty + lines.size() * titleLine + 3, textW, 2 * Ui.lineHeight(12), Component.literal(sub));
        }
        return blockH;
    }

    /** A rubric over a block ("ЧТО СДЕЛАТЬ" on the left, "0 из 1" on the right). @return its height */
    static float rubric(Ui ui, float x, float y, float w, String left, @Nullable String rightText) {
        float h = Ui.lineHeight(11.5f);
        ui.caps(left, x, y + h / 2f, ui.c("text.dim"));
        if (rightText != null) {
            ui.labelRight(rightText, x + w, y + h / 2f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        return h;
    }
}
