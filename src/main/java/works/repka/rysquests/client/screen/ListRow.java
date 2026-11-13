package works.repka.rysquests.client.screen;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;

/**
 * A row of a list: icon, title, a line under it, the state on the right (mockup {@code .lrow}, pictures 11, 19, 19c). Used by "Now",
 * the list of a chapter, search results. Build one per frame and draw it:
 *
 * <pre>
 * float h = ListRow.of("Медная катушка").icon(stack).sub("Ток · 25 опыта и 1 монета")
 *         .link("Забрать", () -> claim(quest)).selected(quest == selected).onClick(() -> select(quest))
 *         .render(ui, x, y, w);
 * y += h + 8;
 * </pre>
 */
final class ListRow {
    /** The row's look: a card with a border (default), flat with no frame, a child under its parent, a tight card. */
    enum Look {
        CARD(48, 7, 12, 8),
        FLAT(40, 4, 10, 6),
        CHILD(38, 4, 10, 6),
        TIGHT(34, 2, 10, 6);

        final int minH;
        final int padY;
        final int padRight;
        final int padLeft;

        Look(int minH, int padY, int padRight, int padLeft) {
            this.minH = minH;
            this.padY = padY;
            this.padRight = padRight;
            this.padLeft = padLeft;
        }
    }

    /** How far a child row stands in from its parent. */
    static final int CHILD_INDENT = 34;
    private static final int ICON = 34;
    private static final int GAP = 11;
    private static final float TITLE_PX = 13;
    private static final float SUB_PX = 11.5f;
    private static final float RIGHT_PX = 12;
    private static final int BAR_W = 150;

    private final String title;
    @Nullable
    private ItemStack icon;
    @Nullable
    private Icons.Shape glyph;
    @Nullable
    private String sub;
    private float progress = -1;
    @Nullable
    private String rightText;
    private String rightToken = "text.muted";
    @Nullable
    private Icons.Shape rightIcon;
    @Nullable
    private Runnable linkAction;
    private boolean selected;
    private boolean locked;
    private Look look = Look.CARD;
    @Nullable
    private Runnable click;
    @Nullable
    private Runnable rightClick;

    private ListRow(String title) {
        this.title = title;
    }

    static ListRow of(String title) {
        return new ListRow(title);
    }

    /** The item on its slot plate. */
    ListRow icon(@Nullable ItemStack stack) {
        icon = stack;
        return this;
    }

    /** A line icon on a slot plate instead of an item: a task kind, a flag of a chapter off the map. */
    ListRow glyph(@Nullable Icons.Shape shape) {
        glyph = shape;
        return this;
    }

    ListRow sub(@Nullable String line) {
        sub = line;
        return this;
    }

    /** A bar under the line, 0 to 1; negative for none. */
    ListRow progress(float ratio) {
        progress = ratio;
        return this;
    }

    /** Plain words on the right: "награда ждёт", "можно делать". */
    ListRow right(String text) {
        rightText = text;
        return this;
    }

    /** Words on the right in a state color, with an optional icon before them: a green check and "выполнен". */
    ListRow right(@Nullable Icons.Shape shape, @Nullable String text, String colorToken) {
        rightIcon = shape;
        rightText = text;
        rightToken = colorToken;
        return this;
    }

    /** A link on the right that does something by itself: "Забрать", "Выбрать награду". */
    ListRow link(String text, Runnable action) {
        rightText = text;
        rightToken = "link";
        linkAction = action;
        return this;
    }

    /** A chevron on the right: the row opens something. */
    ListRow chevron() {
        rightIcon = Icons.Shape.CHEVRON_RIGHT;
        rightToken = "text.dim";
        return this;
    }

    ListRow selected(boolean on) {
        selected = on;
        return this;
    }

    /** Faded to 0.55: nothing in it can be done yet. Say why in {@link #sub}. */
    ListRow locked(boolean on) {
        locked = on;
        return this;
    }

    ListRow look(Look value) {
        look = value;
        return this;
    }

    ListRow onClick(@Nullable Runnable action) {
        click = action;
        return this;
    }

    ListRow onRightClick(@Nullable Runnable action) {
        rightClick = action;
        return this;
    }

    /** The row's height for a width, without drawing. */
    float height(Ui ui, float w) {
        return Math.max(look.minH, look.padY * 2 + contentHeight());
    }

    private float contentHeight() {
        float text = Ui.lineHeight(TITLE_PX) + (sub == null ? 0 : 1 + Ui.lineHeight(SUB_PX)) + (progress >= 0 ? 9 : 0);
        return Math.max(ICON, text);
    }

    /** @return the height drawn */
    float render(Ui ui, float x, float y, float w) {
        if (look == Look.CHILD) {
            x += CHILD_INDENT;
            w -= CHILD_INDENT;
        }
        float h = height(ui, w);
        boolean over = ui.over(x, y, w, h);
        int fade = locked ? 0x8C : 0xFF;
        switch (look) {
            case CARD, TIGHT -> ui.box(x, y, w, h, 10, ui.c(selected ? "tab.active.bg" : "card.bg"), ui.c(selected ? "text.secondary" : "panel.border"));
            case FLAT -> {
                if (selected || over) {
                    ui.box(x, y, w, h, 10, ui.c("tab.active.bg"), selected ? ui.c("button.border") : 0);
                }
            }
            case CHILD -> ui.box(x, y, w, h, 10, selected ? ui.c("tab.active.bg") : 0, ui.c(selected ? "text.secondary" : "panel.border"));
        }
        if (click != null) {
            ui.hit(x, y, w, h, click);
        }
        if (rightClick != null) {
            ui.rightHit(x, y, w, h, rightClick);
        }
        float cy = y + h / 2f;
        float pen = x + look.padLeft;
        if (icon != null || glyph != null) {
            ui.box(pen, cy - ICON / 2f, ICON, ICON, 6, ui.c("slot.bg"), ui.c("slot.border"));
            if (icon != null) {
                ui.item(icon, pen + 5, cy - 12, 24);
            } else {
                ui.icon(glyph, pen + 8.5f, cy - 8.5f, 17, faded(ui.c("text.secondary"), fade));
            }
            pen += ICON + GAP;
        }
        float right = x + w - look.padRight;
        float rightW = 0;
        if (rightText != null || rightIcon != null) {
            int color = faded(ui.c(linkAction != null && ui.over(right - rightWidth(ui), y, rightWidth(ui), h) ? "text.primary" : rightToken), fade);
            rightW = rightWidth(ui);
            float rx = right - rightW;
            if (rightIcon != null) {
                ui.icon(rightIcon, rx, cy - 7, 14, color);
                rx += 14 + (rightText != null ? 6 : 0);
            }
            if (rightText != null) {
                ui.label(rightText, rx, cy, RIGHT_PX, linkAction != null ? Ui.MEDIUM : Ui.REGULAR, color);
            }
            if (linkAction != null) {
                ui.hit(right - rightW, y, rightW, h, linkAction);
            }
        }
        float textW = right - (rightW > 0 ? rightW + 8 : 0) - pen;
        float textH = Ui.lineHeight(TITLE_PX) + (sub == null ? 0 : 1 + Ui.lineHeight(SUB_PX)) + (progress >= 0 ? 9 : 0);
        float top = cy - textH / 2f;
        ui.labelFit(title, pen, top + Ui.lineHeight(TITLE_PX) / 2f, textW, TITLE_PX, Ui.MEDIUM, faded(ui.c("text.primary"), fade));
        float line = top + Ui.lineHeight(TITLE_PX);
        if (sub != null) {
            ui.labelFit(sub, pen, line + 1 + Ui.lineHeight(SUB_PX) / 2f, textW, SUB_PX, Ui.REGULAR, faded(ui.c("text.muted"), fade));
            line += 1 + Ui.lineHeight(SUB_PX);
        }
        if (progress >= 0) {
            ui.bar(pen, line + 5, Math.min(BAR_W, textW), 4, 2, progress, ui.c("text.secondary"));
        }
        return h;
    }

    private float rightWidth(Ui ui) {
        float w = 0;
        if (rightIcon != null) {
            w += 14 + (rightText != null ? 6 : 0);
        }
        if (rightText != null) {
            w += ui.width(rightText, RIGHT_PX, linkAction != null ? Ui.MEDIUM : Ui.REGULAR);
        }
        return w;
    }

    private static int faded(int color, int alpha) {
        return alpha >= 0xFF ? color : Draw.alpha(color, alpha / 255f);
    }
}
