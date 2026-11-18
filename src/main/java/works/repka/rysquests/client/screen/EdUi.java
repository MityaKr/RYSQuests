package works.repka.rysquests.client.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Type;

/**
 * The small pieces the editor's pages share, at the sizes of the mockups ({@code ui.css}): an input ({@code .inp}), a field with its
 * label ({@code .fld}), buttons ({@code .btn}, {@code .btn.sm}, {@code .tb}), the dashed "+" button ({@code .addb}), a row of a list in a
 * form ({@code .erow}), a switch ({@code .sw}) and its row ({@code .setr}), a wide switch of one of a few ({@code .seg}), a removable
 * tag, a note with an icon ({@code .note}), filter chips and a list of options under a select. Colours come from the theme only.
 */
final class EdUi {
    static final int INPUT_H = 34;
    static final int BTN_H = 34;
    static final int SM_H = 28;
    static final int TB_H = 30;
    static final int ADD_H = 32;
    static final int ROW_H = 40;

    private EdUi() {
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // --- text fields ---

    /**
     * The text fields of one page, each kept by an id between frames. A field shows the value it is given until it has the keyboard;
     * what is typed goes back through the field's listener, so the draft is the one place a value lives.
     */
    static final class Fields {
        private final Map<String, Bound> bound = new HashMap<>();
        private double pressX;
        private double pressY;

        private static final class Bound {
            final TextArea area;
            String shown;
            Consumer<String> apply = value -> { };
            boolean enter;

            Bound(TextArea area, String value) {
                this.area = area;
                shown = value;
                area.setText(value);
            }
        }

        /** Where the mouse went down: a click in a field puts the caret there. */
        void press(double x, double y) {
            pressX = x;
            pressY = y;
        }

        TextArea area(String id, boolean multiline, float px, Type.Weight weight, String value, Consumer<String> apply) {
            Bound b = bound.computeIfAbsent(id, key -> new Bound(new TextArea(multiline, px, multiline ? Math.round(px * 1.45f) : Math.round(px * 1.4f), weight, () -> { }), value));
            b.apply = apply;
            if (!b.area.focused() && !b.area.text().equals(value)) {
                b.area.setText(value);
                b.shown = value;
            }
            return b.area;
        }

        /** Gives the keyboard to a field and puts the caret where the mouse went down. */
        void click(TextArea area) {
            bound.values().forEach(b -> b.area.focus(b.area == area));
            area.click(pressX, pressY);
        }

        void focus(String id) {
            Bound target = bound.get(id);
            bound.values().forEach(b -> b.area.focus(b == target));
            if (target != null) {
                target.area.place(target.area.text().length());
            }
        }

        void blur() {
            bound.values().forEach(b -> b.area.focus(false));
        }

        boolean typing() {
            return bound.values().stream().anyMatch(b -> b.area.focused());
        }

        @Nullable
        String focusedId() {
            return bound.entrySet().stream().filter(e -> e.getValue().area.focused()).map(Map.Entry::getKey).findFirst().orElse(null);
        }

        boolean typed(char c) {
            for (Bound b : bound.values()) {
                if (b.area.typed(c)) {
                    sync(b);
                    return true;
                }
            }
            return false;
        }

        /** Esc leaves the field; Enter in a line field and Tab leave it too, the page decides what comes next. */
        boolean key(int key) {
            for (Bound b : bound.values()) {
                if (!b.area.focused()) {
                    continue;
                }
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    b.area.focus(false);
                    return true;
                }
                if (b.area.key(key)) {
                    sync(b);
                    return true;
                }
                return false;
            }
            return false;
        }

        private void sync(Bound b) {
            if (!b.area.text().equals(b.shown)) {
                b.shown = b.area.text();
                b.apply.accept(b.shown);
            }
        }

        /** Forgets the fields: the page shows something else. */
        void clear() {
            bound.clear();
        }

        void drag(double x, double y) {
            bound.values().forEach(b -> b.area.drag(x, y));
        }
    }

    /** An input ({@code .inp}) with its text. @return its height */
    static float input(Ui ui, Fields fields, String id, float x, float y, float w, float h, float px, Type.Weight weight, String value, String hint,
            Consumer<String> apply) {
        boolean multiline = h > INPUT_H + 2;
        TextArea area = fields.area(id, multiline, px, weight, value, apply);
        ui.box(x, y, w, h, 8, ui.c("input.bg"), ui.c(area.focused() ? "text.secondary" : "panel.border"));
        float lineH = multiline ? Math.round(px * 1.45f) : Math.round(px * 1.4f);
        float ty = multiline ? y + 8 : y + (h - lineH) / 2f;
        area.render(ui, x + 10, ty, w - 20, multiline ? h - 16 : lineH, ui.inputText(multiline ? "text.secondary" : "text.primary"), hint);
        ui.hit(x, y, w, h, () -> fields.click(area));
        return h;
    }

    /** A field with its label above ({@code .fld}). @return its height */
    static float field(Ui ui, Fields fields, String id, String label, float x, float y, float w, float h, String value, String hint, Consumer<String> apply) {
        float labelH = Ui.lineHeight(11.5f);
        ui.label(label, x, y + labelH / 2f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        float top = y + labelH + 5;
        input(ui, fields, id, x, top, w, h, h > INPUT_H + 2 ? 12.5f : 13, Ui.REGULAR, value, hint, apply);
        return labelH + 5 + h;
    }

    /** A label over a control. @return its height with the gap */
    static float label(Ui ui, String label, float x, float y) {
        float labelH = Ui.lineHeight(11.5f);
        ui.label(label, x, y + labelH / 2f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        return labelH + 5;
    }

    // --- buttons ---

    static float buttonWidth(Ui ui, String label, @Nullable Icons.Shape icon, boolean small) {
        float px = small ? 12 : 12.5f;
        float pad = small ? 10 : 14;
        float iconW = icon == null ? 0 : (small ? 13 : 14) + (label.isEmpty() ? 0 : 7);
        return pad * 2 + iconW + (label.isEmpty() ? 0 : ui.width(label, px, Ui.MEDIUM));
    }

    /**
     * A grey button ({@code .btn}, small {@code .btn.sm}); {@code on} draws it chosen ({@code .btn.on}), {@code strong} with a light border
     * ({@code .btn.h}). Without an action it is greyed. @return its width
     */
    static float button(Ui ui, String label, @Nullable Icons.Shape icon, float x, float y, boolean small, boolean on, boolean strong, @Nullable Runnable action) {
        float w = buttonWidth(ui, label, icon, small);
        float h = small ? SM_H : BTN_H;
        boolean over = action != null && ui.over(x, y, w, h);
        ui.box(x, y, w, h, 9, ui.c(on ? "tab.active.bg" : over ? "slot.hover" : "button.bg"), ui.c(on || strong ? "text.secondary" : "button.border"));
        int fg = ui.c(on || strong || over ? "text.primary" : "text.secondary");
        if (action == null) {
            fg = Draw.alpha(fg, 0.5f);
        }
        float pen = x + (small ? 10 : 14);
        float iconSize = small ? 13 : 14;
        if (icon != null) {
            ui.icon(icon, pen, y + (h - iconSize) / 2f, iconSize, fg);
            pen += iconSize + 7;
        }
        if (!label.isEmpty()) {
            ui.label(label, pen, y + h / 2f, small ? 12 : 12.5f, strong ? Ui.SEMIBOLD : Ui.MEDIUM, fg);
        }
        if (action != null) {
            ui.hit(x, y, w, h, action);
        }
        return w;
    }

    /** A button without a frame ({@code .btn.ghost}): "В архив" at the foot of a form. @return its width */
    static float ghost(Ui ui, String label, @Nullable Icons.Shape icon, float x, float y, @Nullable Runnable action) {
        float w = buttonWidth(ui, label, icon, false);
        boolean over = action != null && ui.over(x, y, w, BTN_H);
        int fg = ui.c(over ? "text.primary" : "text.muted");
        if (over) {
            ui.fill(x, y, w, BTN_H, 9, ui.c("button.bg"));
        }
        float pen = x + 14;
        if (icon != null) {
            ui.icon(icon, pen, y + (BTN_H - 14) / 2f, 14, fg);
            pen += 21;
        }
        ui.label(label, pen, y + BTN_H / 2f, 12.5f, Ui.MEDIUM, fg);
        if (action != null) {
            ui.hit(x, y, w, BTN_H, action);
        }
        return w;
    }

    /** A tool of the canvas ({@code .tb}), square when it has no words. @return its width */
    static float tool(Ui ui, String label, @Nullable Icons.Shape icon, float x, float y, boolean on, @Nullable Component tip, Runnable action) {
        float w = label.isEmpty() ? TB_H : 10 + (icon == null ? 0 : 14 + 6) + ui.width(label, 12, Ui.MEDIUM) + 10;
        boolean over = ui.over(x, y, w, TB_H);
        ui.box(x, y, w, TB_H, 9, ui.c(over ? "slot.hover" : "button.bg"), ui.c(on ? "text.secondary" : "button.border"));
        int fg = ui.c(on || over ? "text.primary" : "text.secondary");
        if (label.isEmpty() && icon != null) {
            ui.icon(icon, x + 8, y + 8, 14, fg);
        } else {
            float pen = x + 10;
            if (icon != null) {
                ui.icon(icon, pen, y + 8, 14, fg);
                pen += 20;
            }
            ui.label(label, pen, y + TB_H / 2f, 12, Ui.MEDIUM, fg);
        }
        if (tip != null) {
            ui.tooltipIfOver(x, y, w, TB_H, tip);
        }
        ui.hit(x, y, w, TB_H, action);
        return w;
    }

    /** The dashed button that adds something ({@code .addb}). @return its height */
    static float add(Ui ui, String label, float x, float y, float w, boolean lit, Runnable action) {
        boolean over = ui.over(x, y, w, ADD_H);
        ui.dashed(x, y, w, ADD_H, ui.c(lit || over ? "text.secondary" : "button.border"));
        int fg = ui.c(lit || over ? "text.primary" : "text.secondary");
        float tw = 13 + 6 + ui.width(label, 12.5f, Ui.REGULAR);
        float tx = x + (w - tw) / 2f;
        ui.icon(Icons.Shape.PLUS, tx, y + (ADD_H - 13) / 2f, 13, fg);
        ui.label(label, tx + 19, y + ADD_H / 2f, 12.5f, Ui.REGULAR, fg);
        ui.hit(x, y, w, ADD_H, action);
        return ADD_H;
    }

    /** A plain text link ({@code .link}). @return its width */
    static float link(Ui ui, String label, float x, float cy, Runnable action) {
        float w = ui.width(label, 12.5f, Ui.MEDIUM);
        boolean over = ui.over(x, cy - 9, w, 18);
        ui.label(label, x, cy, 12.5f, Ui.MEDIUM, over ? ui.c("text.primary") : ui.c("link"));
        ui.hit(x, cy - 9, w, 18, action);
        return w;
    }

    // --- rows ---

    /**
     * A row of a list inside a form ({@code .erow}): an item or a glyph, a title, a line under it, a grip and a cross on the right.
     *
     * @return its height
     */
    static float row(Ui ui, float x, float y, float w, @Nullable ItemStack stack, @Nullable Icons.Shape glyph, String title, @Nullable String sub,
            @Nullable Runnable click, @Nullable Runnable remove) {
        float h = sub == null ? ROW_H - 4 : ROW_H + 2;
        boolean over = click != null && ui.over(x, y, w, h);
        ui.box(x, y, w, h, 9, ui.c(over ? "slot.hover" : "card.bg"), ui.c("panel.border"));
        float pen = x + 4;
        if (stack != null || glyph != null) {
            ui.box(pen, y + (h - 30) / 2f, 30, 30, 6, ui.c("slot.bg"), ui.c("slot.border"));
            if (stack != null) {
                ui.item(stack, pen + 5, y + (h - 20) / 2f, 20);
            } else {
                ui.icon(glyph, pen + 7, y + (h - 16) / 2f, 16, ui.c("text.secondary"));
            }
            pen += 30 + 9;
        } else {
            pen += 6;
        }
        float right = x + w - 8;
        if (remove != null) {
            boolean overX = ui.over(right - 16, y + (h - 16) / 2f, 16, 16);
            ui.icon(Icons.Shape.CROSS, right - 14, y + (h - 14) / 2f, 14, ui.c(overX ? "text.primary" : "text.dim"));
            right -= 14 + 8;
            ui.icon(Icons.Shape.GRIP, right - 14, y + (h - 14) / 2f, 14, ui.c("text.dim"));
            right -= 14 + 8;
        }
        float room = right - pen - 4;
        if (sub == null) {
            ui.labelFitTip(title, pen, y + h / 2f, room, 12.5f, Ui.MEDIUM, ui.c("text.primary"));
        } else {
            ui.labelFitTip(title, pen, y + h / 2f - 8, room, 12.5f, Ui.MEDIUM, ui.c("text.primary"));
            ui.labelFitTip(sub, pen, y + h / 2f + 8, room, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        if (click != null) {
            ui.hit(x, y, w, h, click);
        }
        if (remove != null) {
            ui.hit(x + w - 8 - 18, y + (h - 20) / 2f, 20, 20, remove);
        }
        return h;
    }

    // --- switches ---

    /** A switch ({@code .sw}): light when on. */
    static void toggle(Ui ui, float x, float y, boolean on, @Nullable Runnable action) {
        ui.box(x, y, 34, 20, 10, ui.c(on ? "text.secondary" : "button.bg"), ui.c(on ? "text.secondary" : "button.border"));
        ui.fill(x + (on ? 16 : 2), y + 3, 14, 14, 7, ui.c(on ? "sidebar.bg" : "text.muted"));
        if (action != null) {
            ui.hit(x, y, 34, 20, action);
        }
    }

    /** A setting row ({@code .setr}): a title and a line under it on the left, the switch on the right. @return its height */
    static float setting(Ui ui, float x, float y, float w, String title, @Nullable String sub, boolean on, Runnable action) {
        float room = w - 34 - 12;
        List<String> lines = sub == null ? List.of() : ui.wrap(sub, room, 11.5f, Ui.REGULAR, 3);
        float textH = Ui.lineHeight(13) + lines.size() * Ui.lineHeight(11.5f);
        float h = Math.max(36, textH);
        float ty = y + (h - textH) / 2f;
        ui.label(title, x, ty + Ui.lineHeight(13) / 2f, 13, Ui.REGULAR, ui.c("text.body"));
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x, ty + Ui.lineHeight(13) + (i + 0.5f) * Ui.lineHeight(11.5f), 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        toggle(ui, x + w - 34, y + (h - 20) / 2f, on, action);
        ui.hit(x, y, w - 40, h, action);
        return h;
    }

    /** One of a few across the whole width ({@code .seg} with stretched parts): the type of a quest. @return its height */
    static float wide(Ui ui, float x, float y, float w, List<String> labels, List<Icons.Shape> icons, int selected, IntConsumer pick) {
        float h = 34;
        ui.box(x, y, w, h, 9, ui.c("input.bg"), ui.c("panel.border"));
        float part = (w - 6 - 2 * (labels.size() - 1)) / labels.size();
        for (int i = 0; i < labels.size(); i++) {
            float px = x + 3 + i * (part + 2);
            boolean on = i == selected;
            boolean over = ui.over(px, y + 3, part, 26);
            if (on) {
                ui.fill(px, y + 3, part, h - 6, 6, ui.c("tab.active.bg"));
            }
            int fg = ui.c(on || over ? "text.primary" : "text.muted");
            Icons.Shape icon = i < icons.size() ? icons.get(i) : null;
            String label = labels.get(i);
            float tw = ui.width(label, 12.5f, on ? Ui.MEDIUM : Ui.REGULAR) + (icon == null ? 0 : 12 + 6);
            float tx = px + (part - tw) / 2f;
            if (icon != null) {
                ui.icon(icon, tx, y + (h - 12) / 2f, 12, fg);
                tx += 18;
            }
            ui.label(label, tx, y + h / 2f, 12.5f, on ? Ui.MEDIUM : Ui.REGULAR, fg);
            int index = i;
            ui.hit(px, y + 3, part, h - 6, () -> pick.accept(index));
        }
        return h;
    }

    /** A removable tag ({@code .tag}, 24 high): a dependency, a chapter off the map. @return its width */
    static float tag(Ui ui, String label, float x, float y, @Nullable Runnable remove) {
        float w = 7 + ui.width(label, 12, Ui.MEDIUM) + (remove == null ? 7 : 4 + 11 + 7);
        ui.fill(x, y, w, 24, 5, ui.c("button.bg"));
        ui.label(label, x + 7, y + 12, 12, Ui.MEDIUM, ui.c("text.secondary"));
        if (remove != null) {
            boolean over = ui.over(x + w - 18, y, 18, 24);
            ui.icon(Icons.Shape.CROSS, x + w - 7 - 11, y + 6.5f, 11, ui.c(over ? "text.primary" : "text.muted"));
            ui.hit(x + w - 18, y, 18, 24, remove);
        }
        return w;
    }

    /** A dashed tag that adds ("+ связь"). @return its width */
    static float addTag(Ui ui, String label, float x, float y, Runnable action) {
        float w = 7 + 11 + 4 + ui.width(label, 12, Ui.MEDIUM) + 7;
        boolean over = ui.over(x, y, w, 24);
        ui.dashed(x, y, w, 24, ui.c(over ? "text.secondary" : "button.border"));
        ui.icon(Icons.Shape.PLUS, x + 7, y + 6.5f, 11, ui.c("text.muted"));
        ui.label(label, x + 22, y + 12, 12, Ui.MEDIUM, ui.c(over ? "text.primary" : "text.muted"));
        ui.hit(x, y, w, 24, action);
        return w;
    }

    /** A small label tag ({@code .tag}, 18 high); {@code ok} tints it as "выбрано". @return its width */
    static float badge(Ui ui, String label, float x, float cy, boolean ok) {
        float w = 7 + ui.width(label, 11, Ui.SEMIBOLD) + 7;
        ui.fill(x, cy - 9, w, 18, 5, ok ? Draw.alpha(ui.c("state.ok"), 0.12f) : ui.c("button.bg"));
        ui.label(label, x + 7, cy, 11, Ui.SEMIBOLD, ui.c(ok ? "state.ok" : "text.secondary"));
        return w;
    }

    /** A note with an icon ({@code .note}). @return its height */
    static float note(Ui ui, float x, float y, float w, Icons.Shape icon, String text) {
        List<String> lines = ui.wrap(text, w - 24 - 25, 12.5f, Ui.REGULAR, 6);
        float lineH = Math.round(12.5f * 1.45f);
        float h = 20 + lines.size() * lineH;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        ui.icon(icon, x + 12, y + 11, 15, ui.c("text.secondary"));
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x + 12 + 15 + 10, y + 10 + (i + 0.5f) * lineH, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
        }
        return h;
    }

    /** Filter chips ({@code .chip}): the chosen one on a grey plate. @return their width */
    static float chips(Ui ui, float x, float y, List<String> labels, int selected, IntConsumer pick) {
        float pen = x;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            float w = 11 * 2 + ui.width(label, 12.5f, Ui.REGULAR);
            boolean on = i == selected;
            boolean over = ui.over(pen, y, w, 28);
            if (on) {
                ui.fill(pen, y, w, 28, 8, ui.c("tab.active.bg"));
            }
            ui.label(label, pen + 11, y + 14, 12.5f, Ui.REGULAR, ui.c(on || over ? "text.primary" : "text.muted"));
            int index = i;
            ui.hit(pen, y, w, 28, () -> pick.accept(index));
            pen += w + 4;
        }
        return pen - x - 4;
    }

    static float chipsWidth(Ui ui, List<String> labels) {
        float w = 0;
        for (String label : labels) {
            w += 22 + ui.width(label, 12.5f, Ui.REGULAR) + 4;
        }
        return Math.max(0, w - 4);
    }

    /**
     * "Предварительное выполнение" of a quest or a chapter (ADR-66): its label and a select of "Как у главы (выкл)", "Вкл", "Выкл",
     * where the first says what the parent gives now. @return its height
     *
     * @param id the select's id in the page's {@link Choices}
     * @param inherit the key of the first choice, which takes one argument: the parent's value in words
     * @param parent what the parent (chapter or book) says now
     * @param own the quest's or chapter's own word, empty when it takes the parent's
     */
    static float preComplete(Ui ui, Choices choices, String id, float x, float y, float w, String inherit, boolean parent,
            java.util.Optional<Boolean> own, java.util.function.Consumer<java.util.Optional<Boolean>> pick) {
        float pen = y + label(ui, EditorBook.tr("rysquests.ed.pre_complete"), x, y);
        List<String> labels = List.of(EditorBook.tr(inherit, EditorBook.tr(parent ? "rysquests.ed.pre_complete.on_lower" : "rysquests.ed.pre_complete.off_lower")),
                EditorBook.tr("rysquests.ed.pre_complete.on"), EditorBook.tr("rysquests.ed.pre_complete.off"));
        int chosen = own.map(value -> value ? 1 : 2).orElse(0);
        float sy = pen;
        pen += select(ui, x, pen, w, labels.get(chosen), choices.isOpen(id), () -> choices.toggle(id, x, sy + INPUT_H + 4, w, labels, chosen,
                index -> pick.accept(index == 0 ? java.util.Optional.empty() : java.util.Optional.of(index == 1))));
        List<String> lines = ui.wrap(EditorBook.tr("rysquests.ed.pre_complete.sub"), w, 11.5f, Ui.REGULAR, 4);
        for (String line : lines) {
            ui.label(line, x, pen + 4 + Ui.lineHeight(11.5f) / 2f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
            pen += Ui.lineHeight(11.5f);
        }
        return pen + 4 - y;
    }

    /** A select that looks like an input with a chevron; the list it opens is the page's ({@link Choices}). @return its height */
    static float select(Ui ui, float x, float y, float w, String value, boolean open, Runnable toggle) {
        boolean over = ui.over(x, y, w, INPUT_H);
        ui.box(x, y, w, INPUT_H, 8, ui.c("input.bg"), ui.c(open || over ? "text.secondary" : "panel.border"));
        ui.labelFitTip(value, x + 10, y + INPUT_H / 2f, w - 20 - 20, 13, Ui.REGULAR, ui.inputText("text.primary"));
        ui.icon(Icons.Shape.CHEVRON_DOWN, x + w - 10 - 14, y + 10, 14, ui.c("text.muted"));
        ui.hit(x, y, w, INPUT_H, toggle);
        return INPUT_H;
    }

    /** The open list of a select, drawn last over the page; a click elsewhere closes it. */
    static final class Choices {
        @Nullable
        private String open;
        private float x;
        private float y;
        private float w;
        private List<String> options = List.of();
        private int selected;
        @Nullable
        private IntConsumer pick;

        boolean isOpen(String id) {
            return id.equals(open);
        }

        boolean anyOpen() {
            return open != null;
        }

        void toggle(String id, float atX, float atY, float width, List<String> values, int chosen, IntConsumer onPick) {
            if (id.equals(open)) {
                open = null;
                return;
            }
            open = id;
            x = atX;
            y = atY;
            w = width;
            options = values;
            selected = chosen;
            pick = onPick;
        }

        void close() {
            open = null;
        }

        /** Draws the open list, if any; call last. */
        void render(Ui ui, float bottom) {
            if (open == null || pick == null) {
                return;
            }
            float rowH = 30;
            float h = Math.min(options.size() * rowH + 8, Math.max(rowH * 3, bottom - y - 8));
            float top = y + h > bottom ? y - h - INPUT_H - 8 : y;
            ui.aboveItems(() -> {
                ui.hit(0, 0, 100000, 100000, this::close);
                ui.shadow(x, top, w, h, 10, 0.8f);
                ui.box(x, top, w, h, 10, ui.c("sidebar.bg"), ui.c("button.border"));
                ui.clip(Math.round(x), Math.round(top + 4), Math.round(w), Math.round(h - 8));
                for (int i = 0; i < options.size(); i++) {
                    float ry = top + 4 + i * rowH;
                    boolean over = ui.over(x + 4, ry, w - 8, rowH);
                    if (over || i == selected) {
                        ui.fill(x + 4, ry, w - 8, rowH, 7, ui.c(i == selected ? "tab.active.bg" : "button.bg"));
                    }
                    ui.labelFitTip(options.get(i), x + 14, ry + rowH / 2f, w - 28, 12.5f, Ui.REGULAR, ui.c(over || i == selected ? "text.primary" : "text.secondary"));
                    int index = i;
                    IntConsumer chosen = pick;
                    ui.hit(x + 4, ry, w - 8, rowH, () -> {
                        close();
                        chosen.accept(index);
                    });
                }
                ui.unclip();
            });
        }
    }

    /** An item on its slot plate, 46 as in the forms' heads. */
    static void plate(Ui ui, @Nullable ItemStack stack, @Nullable Icons.Shape glyph, float x, float y, float size) {
        ui.box(x, y, size, size, 6, ui.c("slot.bg"), ui.c("slot.border"));
        float icon = Math.round(size * 0.7f);
        if (stack != null && !stack.isEmpty()) {
            ui.item(stack, x + (size - icon) / 2f, y + (size - icon) / 2f, icon);
        } else if (glyph != null) {
            ui.icon(glyph, x + (size - icon * 0.6f) / 2f, y + (size - icon * 0.6f) / 2f, icon * 0.6f, ui.c("text.secondary"));
        }
    }

    /** A small × in a corner of a panel's head. */
    static void close(Ui ui, float x, float y, Runnable action) {
        boolean over = ui.over(x, y, 28, 28);
        ui.icon(Icons.Shape.CROSS, x + 7, y + 7, 14, ui.c(over ? "text.primary" : "text.muted"));
        ui.hit(x, y, 28, 28, action);
    }

    /** The foot of a panel ({@code .act}): a line above and room for buttons. @return the y of its buttons */
    static float foot(Ui ui, float x, float y, float w) {
        ui.rect(x, y, w, 1, ui.c("panel.border"));
        return y + 1 + 12;
    }

    static final int FOOT_H = 1 + 12 + BTN_H + 12;
}
