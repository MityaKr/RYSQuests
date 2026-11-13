package works.repka.rysquests.client.screen;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;

/**
 * A task of a quest in the detail panel (mockup {@code .task}, picture 20): the task's action as its title ("Получить:
 * Электромеханизм"), one line of state under it, the progress written once (text and bar), and an optional shortage line with its
 * own "Как сделать" link. States: plain, ready to hand in (light border), done (green border and line).
 *
 * <pre>
 * float h = TaskRow.of("Сдать: Электромеханизм ×32").item(stack, 32, TaskRow.Slot.MISSING)
 *         .line("сдано 12 из 32 · в инвентаре 8, можно сдать").progress(12 / 32f).state(TaskRow.State.READY)
 *         .render(ui, x, y, w);
 * </pre>
 */
final class TaskRow {
    /** The row's border and the color of its line. */
    enum State {
        PLAIN, READY, DONE
    }

    /** The border of the item's slot: plain, enough in the inventory, or missing. */
    enum Slot {
        PLAIN, HAVE, MISSING
    }

    private static final int ICON = 32;
    private static final int GAP = 10;
    private static final float TITLE_PX = 13;
    private static final float LINE_PX = 11.5f;
    private static final int BAR_W = 120;

    private final String title;
    @Nullable
    private ItemStack item;
    private int count;
    private Slot slot = Slot.PLAIN;
    @Nullable
    private Icons.Shape glyph;
    @Nullable
    private String line;
    private float progress = -1;
    @Nullable
    private String right;
    private State state = State.PLAIN;
    @Nullable
    private String need;
    @Nullable
    private String needLink;
    @Nullable
    private Runnable needAction;
    @Nullable
    private Runnable click;
    private int checkbox = -1;
    @Nullable
    private String button;
    @Nullable
    private Runnable buttonAction;

    private TaskRow(String title) {
        this.title = title;
    }

    static TaskRow of(String title) {
        return new TaskRow(title);
    }

    TaskRow item(@Nullable ItemStack stack, int stackCount, Slot border) {
        item = stack;
        count = stackCount;
        slot = border;
        return this;
    }

    /** A line icon on a slot plate, for tasks without an item: kill, visit, statistic, check. */
    TaskRow glyph(Icons.Shape shape) {
        glyph = shape;
        return this;
    }

    TaskRow line(@Nullable String text) {
        line = text;
        return this;
    }

    TaskRow progress(float ratio) {
        progress = ratio;
        return this;
    }

    /** Short words on the right, rarely needed: the progress belongs in {@link #line}. */
    TaskRow right(@Nullable String text) {
        right = text;
        return this;
    }

    TaskRow state(State value) {
        state = value;
        return this;
    }

    /** The shortage block under the row: "Не хватает 2 шт." and a link such as "Как сделать". */
    TaskRow need(String text, @Nullable String link, @Nullable Runnable action) {
        need = text;
        needLink = link;
        needAction = action;
        return this;
    }

    TaskRow onClick(@Nullable Runnable action) {
        click = action;
        return this;
    }

    /** A checkbox on the slot plate instead of an item, for a task done by ticking it (picture 20, "Отметить: прочитал"). */
    TaskRow checkbox(boolean checked) {
        checkbox = checked ? 1 : 0;
        return this;
    }

    /**
     * A small grey button on the right of the row ("Сдать", "Отметить"), only where a quest has several such tasks: with one, the
     * panel's main button does it (plan, section 3, "Quest").
     */
    TaskRow button(String label, Runnable action) {
        button = label;
        buttonAction = action;
        return this;
    }

    /** The line under the title, as given. */
    @Nullable
    String lineForTest() {
        return line;
    }

    float height() {
        float text = Ui.lineHeight(TITLE_PX) + (line == null ? 0 : 1 + Ui.lineHeight(LINE_PX)) + (progress >= 0 ? 9 : 0);
        return 16 + Math.max(ICON, text) + (need == null ? 0 : 7 + 26);
    }

    /** @return the height drawn */
    float render(Ui ui, float x, float y, float w) {
        float h = height();
        String border = switch (state) {
            case DONE -> "ok.border";
            case READY -> "text.secondary";
            case PLAIN -> "panel.border";
        };
        ui.card(x, y, w, h, 10, false, ui.c(border));
        if (state != State.PLAIN && ui.theme.beveled()) {
            // the state's border stays in vanilla too, over the bevel: a done or ready task is not only told by its words
            ui.strokeBold(x, y, w, h, 10, ui.c(border));
        }
        if (click != null) {
            ui.hit(x, y, w, h, click);
        }
        float rowH = h - (need == null ? 0 : 7 + 26) - 16;
        float cy = y + 8 + rowH / 2f;
        float iconX = x + 8;
        String slotBorder = switch (slot) {
            case HAVE -> "slot.have.border";
            case MISSING -> "slot.miss.border";
            case PLAIN -> "slot.border";
        };
        ui.plate(iconX, cy - ICON / 2f, ICON, ICON, 6);
        if (slot != Slot.PLAIN) {
            ui.stroke(iconX, cy - ICON / 2f, ICON, ICON, 6, ui.c(slotBorder));
        }
        if (item != null) {
            ui.item(item, iconX + 4, cy - 12, 24);
            if (count > 1) {
                ui.count(count, iconX + ICON - 1, cy + ICON / 2f + 2);
            }
        } else if (checkbox >= 0) {
            works.repka.ryscore.ui.Kit.checkbox(ui.g, ui.theme, ui.kitHits, Math.round((iconX + 7) / 2f), Math.round((cy - 9) / 2f), 9, checkbox == 1, false,
                    ui.mouseX / 2, ui.mouseY / 2, null);
        } else if (glyph != null) {
            ui.icon(glyph, iconX + 7.5f, cy - 8.5f, 17, ui.c("text.secondary"));
        }
        float textX = iconX + ICON + GAP;
        float buttonW = button == null ? 0 : ui.buttonWidth(button, 12, Ui.MEDIUM, 10);
        if (button != null) {
            ui.button(button, x + w - 10 - buttonW, cy - 14, buttonW, 28, 7, 12, Ui.MEDIUM, 10, Ui.Style.PLAIN, buttonAction);
        }
        float rightW = (right == null ? 0 : ui.width(right, 12, Ui.REGULAR) + 8) + (button == null ? 0 : buttonW + 8);
        float textW = x + w - 10 - rightW - textX;
        float textH = Ui.lineHeight(TITLE_PX) + (line == null ? 0 : 1 + Ui.lineHeight(LINE_PX)) + (progress >= 0 ? 9 : 0);
        float top = cy - textH / 2f;
        ui.labelFit(title, textX, top + Ui.lineHeight(TITLE_PX) / 2f, textW, TITLE_PX, Ui.MEDIUM, ui.c("text.primary"));
        float pen = top + Ui.lineHeight(TITLE_PX);
        if (line != null) {
            ui.labelFit(line, textX, pen + 1 + Ui.lineHeight(LINE_PX) / 2f, textW, LINE_PX, Ui.REGULAR, ui.c(state == State.DONE ? "state.ok" : "text.muted"));
            pen += 1 + Ui.lineHeight(LINE_PX);
        }
        if (progress >= 0) {
            ui.bar(textX, pen + 5, Math.min(BAR_W, textW), 4, 2, progress, ui.c("text.secondary"));
        }
        if (right != null) {
            ui.labelRight(right, x + w - 10, cy, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        if (need != null) {
            float ny = y + 8 + rowH + 7;
            float nx = x + 8;
            float nw = w - 18;
            ui.box(nx, ny, nw, 26, 8, ui.c("status.bg"), ui.c("panel.border"));
            ui.label(need, nx + 9, ny + 13, 12, Ui.REGULAR, ui.c("text.body"));
            if (needLink != null) {
                float lw = ui.width(needLink, 12, Ui.MEDIUM);
                boolean over = needAction != null && ui.over(nx + nw - 9 - lw, ny, lw + 9, 26);
                ui.label(needLink, nx + nw - 9 - lw, ny + 13, 12, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
                if (needAction != null) {
                    ui.hit(nx + nw - 9 - lw, ny, lw + 9, 26, needAction);
                }
            }
        }
        return h;
    }
}
