package works.repka.rysquests.client.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Type;

/**
 * One frame of drawing. Every position and size here is in mockup pixels, so that numbers can be copied from the
 * mockups in {@code design/}: at GUI scale 2 one mockup pixel is one screen pixel.
 */
public final class Ui {
    public static final Type.Weight REGULAR = Type.Weight.REGULAR;
    public static final Type.Weight MEDIUM = Type.Weight.MEDIUM;
    public static final Type.Weight SEMIBOLD = Type.Weight.SEMIBOLD;
    /** Headings: Space Grotesk where it has the glyphs. */
    public static final Type.Weight DISPLAY = Type.Weight.DISPLAY;

    private static final float PER_GUI = 2f;
    private static final float SQUARE = 0.02f;
    private static final float CAPS_PX = 11f;
    private static final float CAPS_TRACKING = 0.88f;
    private static final int ITEM_PX = 32;
    private static final int ABOVE_ITEMS = 250;
    private static final float TAB_PAD = 12f;

    record Hit(int x, int y, int w, int h, Runnable action) {
    }

    enum Style {
        ACCENT, PLAIN, FLAT
    }

    final GuiGraphics g;
    public final Theme theme = ThemeManager.get();
    final Font font = Minecraft.getInstance().font;
    final int mouseX;
    final int mouseY;
    final List<Hit> hits;
    final List<Hit> rightHits;
    @Nullable
    ItemStack hoveredStack;
    @Nullable
    Component hoveredText;
    private final ArrayDeque<int[]> clipStack = new ArrayDeque<>();
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;

    public Ui(GuiGraphics g, int guiMouseX, int guiMouseY, List<Hit> hits, List<Hit> rightHits) {
        this.g = g;
        this.mouseX = Math.round(guiMouseX * PER_GUI);
        this.mouseY = Math.round(guiMouseY * PER_GUI);
        this.hits = hits;
        this.rightHits = rightHits;
    }

    // --- colors ---

    public int c(String token) {
        return theme.c(token);
    }

    int mix(String from, String to, float t) {
        return Draw.mix(c(from), c(to), t);
    }

    /** #181a21: the navigation rail and the bottom bar. */
    int railBg() {
        return mix("sidebar.bg", "panel.bg", 0.3f);
    }

    /** #191c24: the chapter column. */
    int columnBg() {
        return mix("sidebar.bg", "panel.bg", 0.5f);
    }

    /** #15171e: under the map. */
    int mapBg() {
        return mix("sidebar.bg", "slot.bg", 0.25f);
    }

    /** #1e2029: the window and task rows of the flow. */
    int flowBg() {
        return mix("panel.bg", "card.bg", 0.5f);
    }

    /** #2e3340: borders of the flow. */
    int flowBorder() {
        return mix("panel.border", "card.border", 0.25f);
    }

    /** #262a35: the current quest in the flow. */
    int currentBg() {
        return mix("card.bg", "card.border", 0.3f);
    }

    /** #3a3320 and #4a3f20: a border that hints at the accent. */
    int warmBorder(float t) {
        return mix("slot.bg", "accent", t);
    }

    /** #2f6b3f: a finished quest. */
    public int doneBorder() {
        return mix("slot.have.border", "slot.bg", 0.15f);
    }

    /** #3a3f4d: a dependency that is still closed. */
    int idleLine() {
        return mix("card.border", "text.dim", 0.1f);
    }

    /** #f2f3f7: names in lists. */
    public int nameFg() {
        return mix("text.primary", "text.body", 0.3f);
    }

    /** #d7dbe6: quest text in the reading column. */
    int readingFg() {
        return mix("text.body", "button.fg", 0.5f);
    }

    // --- clipping and input ---

    void clip(int x, int y, int w, int h) {
        g.enableScissor(Math.floorDiv(x, 2), Math.floorDiv(y, 2), Math.floorDiv(x + w + 1, 2), Math.floorDiv(y + h + 1, 2));
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
    int measure(IntSupplier layout) {
        clip(0, 0, 0, 0);
        int result = layout.getAsInt();
        unclip();
        return result;
    }

    boolean over(float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY >= clipTop && mouseY < clipBottom;
    }

    void hit(float x, float y, float w, float h, Runnable action) {
        add(hits, x, y, w, h, action);
    }

    void rightHit(float x, float y, float w, float h, Runnable action) {
        add(rightHits, x, y, w, h, action);
    }

    private void add(List<Hit> list, float x, float y, float w, float h, Runnable action) {
        int top = Math.max(Math.round(y), clipTop);
        int bottom = Math.min(Math.round(y + h), clipBottom);
        if (bottom > top) {
            list.add(new Hit(Math.round(x), top, Math.round(w), bottom - top, action));
        }
    }

    void tooltipIfOver(float x, float y, float w, float h, Component text) {
        if (over(x, y, w, h)) {
            hoveredText = text;
        }
    }

    // --- shapes ---

    private float radius(float r) {
        return theme.beveled() ? SQUARE : Math.max(SQUARE, r / PER_GUI);
    }

    public void fill(float x, float y, float w, float h, float r, int color) {
        Draw.round(g, x / PER_GUI, y / PER_GUI, w / PER_GUI, h / PER_GUI, radius(r), color);
    }

    public void rect(float x, float y, float w, float h, int color) {
        Draw.round(g, x / PER_GUI, y / PER_GUI, w / PER_GUI, h / PER_GUI, SQUARE, color);
    }

    /** A fill rounded only at the named corners: columns that touch the window's edge. */
    void fillCorners(float x, float y, float w, float h, float r, int color, boolean topLeft, boolean topRight, boolean bottomRight, boolean bottomLeft) {
        fill(x, y, w, h, r, color);
        if (!topLeft) {
            rect(x, y, r, r, color);
        }
        if (!topRight) {
            rect(x + w - r, y, r, r, color);
        }
        if (!bottomRight) {
            rect(x + w - r, y + h - r, r, r, color);
        }
        if (!bottomLeft) {
            rect(x, y + h - r, r, r, color);
        }
    }

    void stroke(float x, float y, float w, float h, float r, int color) {
        Draw.roundBorder(g, x / PER_GUI, y / PER_GUI, w / PER_GUI, h / PER_GUI, radius(r), color, 0.5f);
    }

    /** A border two mockup pixels thick. */
    void strokeBold(float x, float y, float w, float h, float r, int color) {
        Draw.roundBorder(g, x / PER_GUI, y / PER_GUI, w / PER_GUI, h / PER_GUI, radius(r), color, 1f);
    }

    public void box(float x, float y, float w, float h, float r, int fill, int border) {
        fill(x, y, w, h, r, fill);
        stroke(x, y, w, h, r, border);
    }

    public void shadow(float x, float y, float w, float h, float r, float strength) {
        Draw.shadow(g, x / PER_GUI, y / PER_GUI, w / PER_GUI, h / PER_GUI, r / PER_GUI, strength);
    }

    public void circle(float x, float y, float size, int color) {
        fill(x, y, size, size, size / 2f, color);
    }

    public void bar(float x, float y, float w, float h, float r, float ratio, int color) {
        fill(x, y, w, h, r, c("slot.bg"));
        if (ratio > 0) {
            fill(x, y, w * Math.min(1f, ratio), h, r, color);
        }
    }

    void dashed(float x, float y, float w, float h, int color) {
        final int on = 4;
        final int step = 8;
        for (int i = 0; i < w; i += step) {
            rect(x + i, y, Math.min(on, w - i), 1, color);
            rect(x + i, y + h - 1, Math.min(on, w - i), 1, color);
        }
        for (int i = 0; i < h; i += step) {
            rect(x, y + i, 1, Math.min(on, h - i), color);
            rect(x + w - 1, y + i, 1, Math.min(on, h - i), color);
        }
    }

    void icon(Icons.Shape shape, float x, float y, float size, int color) {
        Icons.draw(g, shape, x / PER_GUI, y / PER_GUI, size / PER_GUI, color);
    }

    private void segment(float x1, float y1, float x2, float y2, float thick, int color) {
        int steps = Math.max(1, Math.round(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            rect(x1 + (x2 - x1) * t - thick / 2, y1 + (y2 - y1) * t - thick / 2, thick, thick, color);
        }
    }

    /** A check mark filling the given square. */
    public void check(float x, float y, float size, int color) {
        float thick = Math.max(1f, size / 7f);
        float midX = x + size * 0.4f;
        float midY = y + size * 0.78f;
        segment(x + size * 0.12f, y + size * 0.5f, midX, midY, thick, color);
        segment(midX, midY, x + size * 0.88f, y + size * 0.2f, thick, color);
    }

    /** A padlock of the given height, its width three quarters of that. */
    void lock(float x, float y, float size, int color) {
        float w = size * 0.75f;
        float bodyY = y + size * 0.42f;
        stroke(x + w * 0.18f, y, w * 0.64f, size * 0.7f, w * 0.32f, color);
        fill(x, bodyY, w, size - size * 0.42f, 1.5f, color);
    }

    /** The mod's mark: the mockup's 56 unit drawing at any size. */
    void logo(float x, float y, float size) {
        float k = size / 56f;
        fill(x + 6 * k, y + 6 * k, 44 * k, 44 * k, 8 * k, c("slot.bg"));
        stroke(x + 6 * k, y + 6 * k, 44 * k, 44 * k, 8 * k, c("card.border"));
        int diamond = c("text.dim");
        float cx = x + 24 * k;
        float cy = y + 30 * k;
        float half = 10 * k;
        for (float dy = -half; dy < half; dy += 1f) {
            float span = half - Math.abs(dy + 0.5f);
            rect(cx - span, cy + dy, span * 2, 1, diamond);
        }
        int accent = c("accent");
        float r = 10 * k;
        float lensX = x + 36 * k - r;
        float lensY = y + 34 * k - r;
        fill(lensX, lensY, r * 2, r * 2, r, Draw.alpha(accent, 0.18f));
        Draw.roundBorder(g, lensX / PER_GUI, lensY / PER_GUI, r, r, r / PER_GUI, accent, Math.max(0.5f, 1.5f * k));
        segment(x + 43 * k, y + 41 * k, x + 50 * k, y + 48 * k, Math.max(1f, 4 * k), accent);
    }

    // --- text ---

    /** The height of a line in the mockups' font, which browsers round per ascent and per descent. */
    static float lineHeight(float px) {
        return Math.round(px * 0.96875f) + Math.round(px * 0.2412f);
    }

    /** The height of a heading: those use a display font with taller metrics in the mockups. */
    static float headingHeight(float px) {
        return Math.round(px * 0.984f) + Math.round(px * 0.292f);
    }

    /** @param cy the vertical middle of the line */
    public void label(String text, float x, float cy, float px, Type.Weight weight, int color) {
        Draw.label(g, text, x / PER_GUI, cy / PER_GUI, px, weight, color);
    }

    public void labelRight(String text, float right, float cy, float px, Type.Weight weight, int color) {
        label(text, right - width(text, px, weight), cy, px, weight, color);
    }

    void labelCentered(String text, float centerX, float cy, float px, Type.Weight weight, int color) {
        label(text, centerX - width(text, px, weight) / 2f, cy, px, weight, color);
    }

    /** A line of text clipped with an ellipsis to the given width. */
    public void labelFit(String text, float x, float cy, float maxWidth, float px, Type.Weight weight, int color) {
        label(fit(text, maxWidth, px, weight), x, cy, px, weight, color);
    }

    public float width(String text, float px, Type.Weight weight) {
        return Draw.labelWidth(text, px, weight) * PER_GUI;
    }

    String fit(String text, float maxWidth, float px, Type.Weight weight) {
        return Draw.ellipsize(text, maxWidth / PER_GUI, px, weight);
    }

    List<String> wrap(String text, float maxWidth, float px, Type.Weight weight, int maxLines) {
        return Draw.wrapLabel(text, Math.max(1f, maxWidth) / PER_GUI, px, weight, maxLines);
    }

    /** The narrowest a text can get when it wraps: its longest word. */
    float minWidth(String text, float px, Type.Weight weight) {
        float widest = 0;
        for (String word : text.split(" ")) {
            widest = Math.max(widest, width(word, px, weight));
        }
        return widest;
    }

    /** Several lines centered as a block on {@code cy}. */
    void lines(List<String> lines, float x, float cy, float lineHeight, float px, Type.Weight weight, int color) {
        float first = cy - (lines.size() - 1) * lineHeight / 2f;
        for (int i = 0; i < lines.size(); i++) {
            label(lines.get(i), x, first + i * lineHeight, px, weight, color);
        }
    }

    /** A section heading: small capitals with wide spacing. */
    void caps(String text, float x, float cy, int color) {
        float pen = x;
        for (int i = 0; i < text.length(); ) {
            int codepoint = Character.toUpperCase(text.codePointAt(i));
            i += Character.charCount(text.codePointAt(i));
            String glyph = Character.toString(codepoint);
            label(glyph, pen, cy, CAPS_PX, MEDIUM, color);
            pen += width(glyph, CAPS_PX, MEDIUM) + CAPS_TRACKING;
        }
    }

    float capsWidth(String text) {
        return width(text.toUpperCase(java.util.Locale.ROOT), CAPS_PX, MEDIUM) + CAPS_TRACKING * text.length();
    }

    void capsRight(String text, float right, float cy, int color) {
        caps(text, right - capsWidth(text), cy, color);
    }

    /** Underlined text, the look of a link. */
    void link(String text, float x, float cy, float px, int color) {
        label(text, x, cy, px, REGULAR, color);
        rect(x, cy + px * 0.5f, width(text, px, REGULAR), 1, color);
    }

    /** Runs drawing that has to land above item icons, which are drawn closer to the screen than anything else. */
    void aboveItems(Runnable drawing) {
        g.pose().pushPose();
        g.pose().translate(0, 0, ABOVE_ITEMS);
        drawing.run();
        g.pose().popPose();
    }

    // --- items ---

    public void item(ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) {
            return;
        }
        float k = size / ITEM_PX;
        g.pose().pushPose();
        g.pose().translate(x / PER_GUI, y / PER_GUI, 0);
        g.pose().scale(k, k, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /** An item centered in a box, with the tooltip and the recipe click every item in the book has. */
    void cell(ItemStack stack, float x, float y, float w, float h, float r, float icon, int fill, int border) {
        box(x, y, w, h, r, fill, border);
        item(stack, x + (w - icon) / 2f, y + (h - icon) / 2f, icon);
        interactive(stack, x, y, w, h);
    }

    void interactive(ItemStack stack, float x, float y, float w, float h) {
        if (stack.isEmpty()) {
            return;
        }
        if (over(x, y, w, h)) {
            hoveredStack = stack;
        }
        hit(x, y, w, h, () -> Recipes.showRecipes(stack));
    }

    /** Fades what is under it towards a color, which is how the mockups' opacity looks on an item. */
    void veil(float x, float y, float w, float h, float r, int color, float amount) {
        aboveItems(() -> fill(x, y, w, h, r, Draw.alpha(color, amount)));
    }

    /** A stack size in the corner of a slot. */
    void count(int count, float right, float bottom) {
        if (count > 1) {
            aboveItems(() -> labelRight(String.valueOf(count), right, bottom - 7, 11, REGULAR, 0xFFFFFFFF));
        }
    }

    // --- controls ---

    float buttonWidth(String label, float px, Type.Weight weight, float pad) {
        return width(label, px, weight) + pad * 2;
    }

    float buttonMinWidth(String label, float px, Type.Weight weight, float pad) {
        return minWidth(label, px, weight) + pad * 2;
    }

    /** A button of a given size. A label wider than the button wraps, as text in the mockups' buttons does. */
    void button(String label, float x, float y, float w, float h, float r, float px, Type.Weight weight, float pad, Style style, Runnable action) {
        boolean over = over(x, y, w, h);
        int color;
        switch (style) {
            case ACCENT -> {
                fill(x, y, w, h, r, over ? Draw.mix(c("accent"), 0xFFFFFFFF, 0.1f) : c("accent"));
                color = c("accent.text");
            }
            case PLAIN -> {
                box(x, y, w, h, r, over ? c("slot.hover") : c("button.bg"), c("button.border"));
                color = c("text.primary");
            }
            default -> {
                if (over) {
                    fill(x, y, w, h, r, c("button.bg"));
                }
                color = c("text.primary");
            }
        }
        float lineHeight = Math.round(px * 1.16f);
        List<String> lines = wrap(label, w - pad * 2, px, weight, 3);
        float first = y + h / 2f - (lines.size() - 1) * lineHeight / 2f;
        for (int i = 0; i < lines.size(); i++) {
            labelCentered(lines.get(i), x + w / 2f, first + i * lineHeight, px, weight, color);
        }
        hit(x, y, w, h, action);
    }

    /** A tab of a window's sections: text with a line under the active one. @return its width */
    float tab(String label, float x, float cy, float height, float px, boolean active, Runnable action) {
        Type.Weight weight = active ? MEDIUM : REGULAR;
        float w = width(label, px, weight) + TAB_PAD * 2;
        float y = cy - height / 2f;
        boolean over = over(x, y, w, height);
        theme.tabLine(g, Math.round(x / PER_GUI), Math.round(y / PER_GUI), Math.round(w / PER_GUI), Math.round(height / PER_GUI), active, over);
        label(label, x + TAB_PAD, cy, px, weight, theme.tabLineFg(active, over));
        hit(x, y, w, height, action);
        return w;
    }

    /** A rounded label such as "почти готово". @return its width */
    float pill(String text, float x, float cy, float px, Type.Weight weight, float padX, float height, float r, int fill, int color) {
        float w = width(text, px, weight) + padX * 2;
        fill(x, cy - height / 2f, w, height, r, fill);
        label(text, x + padX, cy, px, weight, color);
        return w;
    }

    // --- layout ---

    /**
     * Shrinks the items of a row as CSS flexbox does when they do not fit: each gives up space in proportion to its
     * size, and none goes below its minimum.
     */
    static float[] shrink(float room, float gap, float[] base, float[] min) {
        int n = base.length;
        float[] out = base.clone();
        float total = gap * Math.max(0, n - 1);
        for (float b : base) {
            total += b;
        }
        if (total <= room) {
            return out;
        }
        boolean[] frozen = new boolean[n];
        for (int round = 0; round < n + 1; round++) {
            float free = room - gap * Math.max(0, n - 1);
            float share = 0;
            for (int i = 0; i < n; i++) {
                free -= frozen[i] ? out[i] : base[i];
                share += frozen[i] ? 0 : base[i];
            }
            if (share <= 0) {
                break;
            }
            boolean violated = false;
            for (int i = 0; i < n; i++) {
                if (!frozen[i]) {
                    out[i] = base[i] + free * base[i] / share;
                    if (out[i] < min[i]) {
                        out[i] = min[i];
                        frozen[i] = true;
                        violated = true;
                    }
                }
            }
            if (!violated) {
                break;
            }
        }
        return out;
    }

    /** Roman numeral of a chapter or an epoch. */
    static String roman(int number) {
        final int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        final String[] digits = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        int rest = number;
        for (int i = 0; i < values.length; i++) {
            while (rest >= values[i]) {
                out.append(digits[i]);
                rest -= values[i];
            }
        }
        return out.toString();
    }

    /** Word forms for numbers: "1 квест", "2 квеста", "5 квестов" and the English pair. */
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
        return Component.translatable(key + "." + form, count).getString();
    }

    static List<ItemStack> firstStacks(List<ItemStack> stacks, int limit) {
        return new ArrayList<>(stacks.subList(0, Math.min(limit, stacks.size())));
    }
}
