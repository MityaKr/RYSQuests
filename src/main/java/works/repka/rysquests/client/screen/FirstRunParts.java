package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.RYSQuests;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Type;

/**
 * The pieces the first-run pages share, from the mockups' {@code ui.css}: the step marks of the import ({@code .stepn}), report rows
 * ({@code .lrow}), rubrics, notes, tags, big buttons and the bar of actions at the bottom ({@code .act}). Mockup pixels throughout.
 */
final class FirstRunParts {
    static final ResourceLocation MOD_ICON = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "textures/gui/sidebar_book.png");
    static final float STEP = 22;
    static final float BIG_H = 42;
    static final float BIG_PX = 13.5f;
    static final float BIG_PAD = 20;
    /** The bar of actions at the bottom: a line, 12 of padding, a big button, 12 of padding. */
    static final float ACT_H = 1 + 12 + BIG_H + 12;

    private FirstRunParts() {
    }

    // --- texts ---

    static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** Puts a {@link FirstRun.Text} into words, with numbers grouped as "4 853" and words agreeing with them. */
    static String str(Object value) {
        if (value instanceof FirstRun.Text text) {
            if (text.fallback() != null && !I18n.exists(text.key())) {
                return str(text.fallback());
            }
            Object[] args = text.args().stream().map(FirstRunParts::str).toArray();
            return Component.translatable(text.key(), args).getString();
        }
        if (value instanceof FirstRun.Plural plural) {
            return counted(plural.key(), plural.count());
        }
        if (value instanceof FirstRun.Joined joined) {
            List<String> parts = joined.parts().stream().map(FirstRunParts::str).toList();
            if (parts.size() <= 1) {
                return parts.isEmpty() ? "" : parts.getFirst();
            }
            return String.join(", ", parts.subList(0, parts.size() - 1)) + t(FirstRun.P + "join.and") + parts.getLast();
        }
        if (value instanceof Number number) {
            return Sidebar.full(number.longValue());
        }
        return String.valueOf(value);
    }

    /** "1 квест", "2 квеста", "5 квестов", the number grouped by thousands. */
    static String counted(String key, int count) {
        boolean russian = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("ru");
        int tens = count % 100;
        int units = count % 10;
        String form;
        if (!russian) {
            form = count == 1 ? "one" : "many";
        } else if (tens >= 11 && tens <= 14) {
            form = "many";
        } else if (units == 1) {
            form = "one";
        } else if (units >= 2 && units <= 4) {
            form = "few";
        } else {
            form = "many";
        }
        return Component.translatable(key + "." + form, Sidebar.full(count)).getString();
    }

    // --- the header of the import ---

    enum Mark {
        NUMBER, CURRENT, DONE, FAILED
    }

    /** A round step mark, 22 across ({@code .stepn}): a number, the current step, done (a check) or failed (a cross). */
    static void stepMark(Ui ui, float x, float y, Mark mark, String text) {
        switch (mark) {
            case CURRENT -> ui.circle(x, y, STEP, ui.c("text.primary"));
            case DONE -> ui.circle(x, y, STEP, ui.c("state.ok"));
            case FAILED -> {
                ui.circle(x, y, STEP, ui.c("button.bg"));
                ui.ring(x, y, STEP, 1, ui.c("state.bad"));
            }
            default -> {
                ui.circle(x, y, STEP, ui.c("button.bg"));
                ui.ring(x, y, STEP, 1, ui.c("button.border"));
            }
        }
        float c = STEP / 2f;
        switch (mark) {
            case DONE -> ui.icon(Icons.Shape.CHECK, x + 5, y + 5, 12, ui.c("accent.text"));
            case FAILED -> ui.icon(Icons.Shape.CROSS, x + 5, y + 5, 12, ui.c("state.bad"));
            case CURRENT -> ui.labelCentered(text, x + c, y + c, 11.5f, Ui.SEMIBOLD, ui.c("accent.text"));
            default -> ui.labelCentered(text, x + c, y + c, 11.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        }
    }

    /** "① Перенос ——— ② Вид карты" in the header; the first step shows a check once it is done. */
    static void steps(Ui ui, float x, float cy, int current) {
        String first = t(FirstRun.P + "step.transfer");
        String second = t(FirstRun.P + "step.map");
        float pen = x;
        stepMark(ui, pen, cy - STEP / 2f, current > 1 ? Mark.DONE : Mark.CURRENT, "1");
        pen += STEP + 8;
        ui.label(first, pen, cy, 12.5f, Ui.REGULAR, ui.c(current == 1 ? "text.primary" : "text.muted"));
        pen += ui.width(first, 12.5f, Ui.REGULAR) + 10;
        ui.rect(pen, cy, 36, 1, ui.c("button.border"));
        pen += 36 + 10;
        stepMark(ui, pen, cy - STEP / 2f, current == 2 ? Mark.CURRENT : Mark.NUMBER, "2");
        pen += STEP + 8;
        ui.label(second, pen, cy, 12.5f, Ui.REGULAR, ui.c(current == 2 ? "text.primary" : "text.muted"));
    }

    // --- rows ---

    /** A rubric in capitals with an optional plain note at the right ({@code .rub}). @return its height */
    static float rubric(Ui ui, float x, float y, float w, String left, @Nullable String right) {
        float cy = y + 7;
        ui.caps(left, x, cy, ui.c("text.dim"));
        if (right != null) {
            ui.labelRight(right, x + w, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        return 14;
    }

    /** What stands at the left of a row: a step mark, the map's tile, a count or "!". */
    interface Lead {
        float width();

        void draw(Ui ui, float x, float cy);
    }

    static Lead mark(Mark mark, String text) {
        return new Lead() {
            @Override
            public float width() {
                return STEP;
            }

            @Override
            public void draw(Ui ui, float x, float cy) {
                stepMark(ui, x, cy - STEP / 2f, mark, text);
            }
        };
    }

    /** The slot-like tile with a line icon ({@code .tico}), 32 across. */
    static Lead tile(Icons.Shape icon) {
        return new Lead() {
            @Override
            public float width() {
                return 32;
            }

            @Override
            public void draw(Ui ui, float x, float cy) {
                ui.box(x, cy - 16, 32, 32, 6, ui.c("slot.bg"), ui.c("slot.border"));
                ui.icon(icon, x + 7.5f, cy - 8.5f, 17, ui.c("text.secondary"));
            }
        };
    }

    /**
     * A row of the report ({@code .lrow}, padding 5 by 12): lead, title, an optional second line, a value or a chevron at the right.
     * The row is 40 high, 46 with a second line.
     *
     * @return its height
     */
    static float row(Ui ui, float x, float y, float w, Lead lead, String title, @Nullable String sub, @Nullable String value, boolean chevron,
            float minHeight) {
        float pad = 12;
        float textX = x + pad + lead.width() + 11;
        float right = x + w - pad;
        float valueW = value != null ? ui.width(value, 12, Ui.SEMIBOLD) + 10 : chevron ? 14 + 10 : 0;
        float textW = right - valueW - textX;
        List<String> subLines = sub == null ? List.of() : ui.wrap(sub, textW, 11.5f, Ui.REGULAR, 2);
        float titleH = 16;
        float subH = subLines.size() * 14.5f;
        float h = Math.max(minHeight, 10 + titleH + (subLines.isEmpty() ? 0 : 1 + subH) + 2);
        ui.box(x, y, w, h, 10, ui.c("card.bg"), ui.c("panel.border"));
        float cy = y + h / 2f;
        lead.draw(ui, x + pad, cy);
        float top = cy - (titleH + (subLines.isEmpty() ? 0 : 1 + subH)) / 2f;
        ui.labelFit(title, textX, top + titleH / 2f, textW, 13, Ui.MEDIUM, ui.c("text.primary"));
        for (int i = 0; i < subLines.size(); i++) {
            ui.label(subLines.get(i), textX, top + titleH + 1 + 14.5f * i + 7.25f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        if (value != null) {
            ui.labelRight(value, right, cy, 12, Ui.SEMIBOLD, ui.c("text.primary"));
        } else if (chevron) {
            ui.icon(Icons.Shape.CHEVRON_RIGHT, right - 14, cy - 7, 14, ui.c("text.dim"));
        }
        return h;
    }

    // --- words ---

    /** A part of a paragraph and its weight: the bold opening of a note and the rest. */
    record Run(String text, Type.Weight weight, int color) {
    }

    /**
     * Draws runs of text wrapped to a width, word by word with each word in its own weight.
     *
     * @return the height taken
     */
    static float paragraph(Ui ui, float x, float y, float w, float px, float lineH, List<Run> runs) {
        record Word(String text, Type.Weight weight, int color, float width) {
        }
        List<List<Word>> lines = new ArrayList<>();
        List<Word> line = new ArrayList<>();
        float pen = 0;
        float space = ui.width(" ", px, Ui.REGULAR);
        for (Run run : runs) {
            for (String token : run.text().split(" ")) {
                if (token.isEmpty()) {
                    continue;
                }
                float tw = ui.width(token, px, run.weight());
                if (!line.isEmpty() && pen + space + tw > w) {
                    lines.add(line);
                    line = new ArrayList<>();
                    pen = 0;
                }
                pen += (line.isEmpty() ? 0 : space) + tw;
                line.add(new Word(token, run.weight(), run.color(), tw));
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        for (int i = 0; i < lines.size(); i++) {
            float wx = x;
            float cy = y + lineH * i + lineH / 2f;
            for (Word word : lines.get(i)) {
                ui.label(word.text(), wx, cy, px, word.weight(), word.color());
                wx += word.width() + space;
            }
        }
        return lines.size() * lineH;
    }

    /** A grey box with an icon and words ({@code .note}). @return its height */
    static float note(Ui ui, float x, float y, float w, float padY, Icons.Shape icon, List<Run> runs) {
        float textX = x + 12 + 15 + 10;
        float textW = x + w - 12 - textX;
        float lineH = 18.1f;
        float textH = measure(ui, textW, 12.5f, lineH, runs);
        float h = padY * 2 + textH + 2;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        if (icon == Icons.Shape.DOT) {
            infoIcon(ui, x + 12, y + padY + 2, 15, ui.c("text.secondary"));
        } else {
            ui.icon(icon, x + 12, y + padY + 2, 15, ui.c("text.secondary"));
        }
        paragraph(ui, textX, y + padY + 1, textW, 12.5f, lineH, runs);
        return h;
    }

    static float measure(Ui ui, float w, float px, float lineH, List<Run> runs) {
        // The same wrapping as paragraph(), without drawing.
        float space = ui.width(" ", px, Ui.REGULAR);
        int lines = 0;
        float pen = 0;
        boolean empty = true;
        for (Run run : runs) {
            for (String token : run.text().split(" ")) {
                if (token.isEmpty()) {
                    continue;
                }
                float tw = ui.width(token, px, run.weight());
                if (!empty && pen + space + tw > w) {
                    lines++;
                    pen = 0;
                    empty = true;
                }
                pen += (empty ? 0 : space) + tw;
                empty = false;
            }
        }
        return (lines + (empty ? 0 : 1)) * lineH;
    }

    /** The help icon of the mockups: a ring with an "i". */
    static void infoIcon(Ui ui, float x, float y, float size, int color) {
        // the mockup's 24 unit drawing: a circle of radius 9 and an "i", stroke 2; in interface units, half the pixels
        float k = size / 24f / 2f;
        float cx = x / 2f + 12 * k;
        float cy = y / 2f + 12 * k;
        float stroke = 2 * k;
        Draw.ring(ui.g, cx, cy, 9 * k + stroke / 2f, stroke, color);
        Draw.line(ui.g, cx, cy - 1 * k, cx, cy + 5 * k, stroke, color);
        Draw.disc(ui.g, cx, cy - 4.5f * k, stroke * 0.6f, color);
    }

    /** A small label ({@code .tag}), 18 high; the amber one ({@code .tag.a}) marks the advised form. @return its width */
    static float tag(Ui ui, String text, float x, float cy, boolean amber) {
        float w = ui.width(text, 11, Ui.SEMIBOLD) + 14;
        if (amber) {
            ui.box(x, cy - 9, w, 18, 5, ui.c("callout.bg"), ui.c("callout.border"));
        } else {
            ui.fill(x, cy - 9, w, 18, 5, ui.c("button.bg"));
        }
        ui.label(text, x + 7, cy, 11, Ui.SEMIBOLD, ui.c(amber ? "accent" : "text.secondary"));
        return w;
    }

    // --- buttons ---

    /** The width of a big button ({@code .btn.big}) with an optional icon. */
    static float bigWidth(Ui ui, String label, boolean primary, @Nullable Icons.Shape icon) {
        return BIG_PAD * 2 + ui.width(label, BIG_PX, primary ? Ui.SEMIBOLD : Ui.MEDIUM) + (icon != null ? 15 + 7 : 0);
    }

    /**
     * A big button: amber for the main action, grey otherwise; the icon stands before the label or, for "next", after it.
     *
     * @param tip a tooltip under the cursor, or null
     * @return its width
     */
    static float big(Ui ui, String label, float x, float y, boolean primary, @Nullable Icons.Shape icon, boolean iconAfter, @Nullable String tip,
            @Nullable Runnable action) {
        float w = bigWidth(ui, label, primary, icon);
        boolean over = ui.over(x, y, w, BIG_H) && action != null;
        int fg;
        if (primary) {
            ui.fill(x, y, w, BIG_H, 9, over ? Draw.mix(ui.c("accent"), 0xFFFFFFFF, 0.1f) : ui.c("accent"));
            fg = ui.c("accent.text");
        } else {
            ui.box(x, y, w, BIG_H, 9, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
            fg = ui.c("text.primary");
        }
        Type.Weight weight = primary ? Ui.SEMIBOLD : Ui.MEDIUM;
        float pen = x + BIG_PAD;
        float cy = y + BIG_H / 2f;
        if (icon != null && !iconAfter) {
            ui.icon(icon, pen, cy - 7.5f, 15, fg);
            pen += 15 + 7;
        }
        ui.label(label, pen, cy, BIG_PX, weight, fg);
        pen += ui.width(label, BIG_PX, weight) + 7;
        if (icon != null && iconAfter) {
            ui.icon(icon, pen, cy - 7.5f, 15, fg);
        }
        if (action != null) {
            ui.hit(x, y, w, BIG_H, action);
        }
        if (tip != null) {
            ui.tooltipIfOver(x, y, w, BIG_H, Component.literal(tip));
        }
        return w;
    }

    /** The bar of actions at the bottom ({@code .act}): its top line. @return the vertical middle of its buttons */
    static float actions(Ui ui, float x, float y, float w) {
        ui.rect(x, y, w, 1, ui.c("panel.border"));
        return y + 1 + 12 + BIG_H / 2f;
    }

    /** The padlock line of a closed book: "Книга закрыта для игроков." in bold, then the rest. */
    static float lockLine(Ui ui, float x, float cy, float w, String bold, String rest, float px) {
        ui.lock(x + 2, cy - 7, 12, ui.c("text.secondary"));
        List<Run> runs = new ArrayList<>();
        if (!bold.isEmpty()) {
            runs.add(new Run(bold, Ui.SEMIBOLD, ui.c("text.primary")));
        }
        runs.add(new Run(rest, Ui.REGULAR, ui.c("text.secondary")));
        float lineH = Math.round(px * 1.45f);
        float h = measure(ui, w - 25, px, lineH, runs);
        paragraph(ui, x + 25, cy - h / 2f, w - 25, px, lineH, runs);
        return h;
    }
}
