package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;

/** One frame of drawing: the shared context of the book's views, and the clickable areas they register. */
final class Ui {
    static final int LINE = 10;
    static final int SLOT = 18;

    record Hit(int x, int y, int w, int h, Runnable action) {
    }

    final GuiGraphics g;
    final Theme theme = ThemeManager.get();
    final Font font = Minecraft.getInstance().font;
    final int mouseX;
    final int mouseY;
    final List<Hit> hits;
    @Nullable
    ItemStack hoveredStack;
    @Nullable
    Component hoveredText;
    private final java.util.ArrayDeque<int[]> clipStack = new java.util.ArrayDeque<>();
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;

    Ui(GuiGraphics g, int mouseX, int mouseY, List<Hit> hits) {
        this.g = g;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.hits = hits;
    }

    void clip(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
        clipStack.push(new int[] {clipTop, clipBottom});
        clipTop = y;
        clipBottom = y + h;
    }

    void unclip() {
        g.disableScissor();
        int[] previous = clipStack.pop();
        clipTop = previous[0];
        clipBottom = previous[1];
    }

    /** Runs drawing code only to learn its result: nothing shows and nothing becomes clickable. */
    int measure(java.util.function.IntSupplier layout) {
        clip(0, 0, 0, 0);
        int result = layout.getAsInt();
        unclip();
        return result;
    }

    boolean over(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY >= clipTop && mouseY < clipBottom;
    }

    void hit(int x, int y, int w, int h, Runnable action) {
        int top = Math.max(y, clipTop);
        int bottom = Math.min(y + h, clipBottom);
        if (bottom > top) {
            hits.add(new Hit(x, top, w, bottom - top, action));
        }
    }

    int color(String token) {
        return theme.c(token);
    }

    void slot(ItemStack stack, int x, int y, boolean accent) {
        boolean over = over(x, y, SLOT, SLOT);
        theme.slot(g, x, y, SLOT, over || accent);
        item(stack, x + 1, y + 1);
        g.renderItemDecorations(font, stack, x + 1, y + 1);
        if (over) {
            hoveredStack = stack;
        }
        hit(x, y, SLOT, SLOT, () -> Recipes.showRecipes(stack));
    }

    void item(ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            g.renderItem(stack, x, y);
        }
    }

    void text(String text, int x, int y, String token) {
        Draw.text(g, text, x, y, color(token));
    }

    void text(Component text, int x, int y, String token) {
        Draw.text(g, text, x, y, color(token));
    }

    void small(String text, int x, int y, String token) {
        Draw.textScaled(g, text, x, y, 0.75f, color(token));
    }

    /** @return the y below the wrapped text */
    int wrapped(Component text, int x, int y, int width, String token) {
        for (FormattedCharSequence line : font.split(text, width)) {
            g.drawString(font, line, x, y, color(token), false);
            y += LINE;
        }
        return y;
    }

    void bar(int x, int y, int w, int h, float ratio, String token) {
        Draw.round(g, x, y, w, h, h / 2f, color("slot.bg"));
        if (ratio > 0) {
            Draw.round(g, x, y, Math.max(h, Math.round(w * Math.min(1f, ratio))), h, h / 2f, color(token));
        }
    }

    /** @return the width used, so that buttons can be laid out in a row */
    int button(Component label, int x, int y, int h, boolean accent, Runnable action) {
        int w = font.width(label) + 14;
        theme.button(g, x, y, w, h, over(x, y, w, h), accent);
        Draw.textCentered(g, label.getString(), x + w / 2, y + (h - 8) / 2, color(accent ? "accent.text" : "text.body"));
        hit(x, y, w, h, action);
        return w;
    }

    void tooltipIfOver(int x, int y, int w, int h, Component text) {
        if (over(x, y, w, h)) {
            hoveredText = text;
        }
    }
}
