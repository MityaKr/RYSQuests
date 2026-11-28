package works.repka.rysquests.client.screen;

import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.network.NewBookPayload;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;

/**
 * "Новая книга" (picture 09a): the book's name, an icon from the hand, and one question that may go unanswered, "Как устроена
 * сборка?": the answer picks the map form ({@link FirstRun.Answer}). "Создать книгу" makes the book on the server, closed to players,
 * and then opens {@link #firstQuests}: the editor's table of a book from scratch (picture 09).
 */
final class NewBookView implements BookView {
    private static final String P = FirstRun.P;
    private static final float COLUMN = 620;
    private static final float ROW_H = 62;
    private static final long WAIT_MS = 15_000;

    /** Where "Создать книгу" leads once the server has made the book. */
    interface Start {
        void open(BookScreen screen, String title, FirstRun.Answer answer);
    }

    /**
     * The editor's table of a book from scratch (picture 09): the title and the form of the answer go into the draft ("пока не знаю"
     * keeps {@link works.repka.rysquests.data.MapForm#AUTO}), and only "по порядку" makes the first epoch.
     */
    static Start firstQuests = (screen, title, answer) -> screen.editor.startNewBook(title, answer.form, answer == FirstRun.Answer.ORDER);

    private final BookScreen screen;
    private final TextArea name = new TextArea(false, 14, 18, Ui.SEMIBOLD, () -> { });
    private FirstRun.Answer answer = FirstRun.Answer.UNKNOWN;
    @Nullable
    private ResourceLocation icon;
    @Nullable
    private String sent;
    private long sentAt;

    NewBookView(BookScreen screen) {
        this.screen = screen;
        name.setText(FirstRunParts.t(P + "new.default_name"));
        name.place(name.text().length());
        name.focus(true);
    }

    @Override
    public boolean bareHeader() {
        return true;
    }

    FirstRun.Answer answer() {
        return answer;
    }

    void answer(FirstRun.Answer value) {
        answer = value;
    }

    /** "Создать книгу". */
    void create() {
        String title = FirstRun.bookTitle(name.text(), NewBookPayload.MAX_TITLE);
        if (title == null || sent != null) {
            name.focus(true);
            return;
        }
        sent = title;
        sentAt = Util.getMillis();
        ClientBook.requestNewBook(title, Optional.ofNullable(icon), answer.form);
    }

    /** The server made the book: its name came back. */
    private void watchCreated() {
        if (sent == null) {
            return;
        }
        if (ClientBook.current().meta().title().filter(sent::equals).isPresent()) {
            String title = sent;
            sent = null;
            firstQuests.open(screen, title, answer);
        } else if (Util.getMillis() - sentAt > WAIT_MS) {
            sent = null;
        }
    }

    private void iconFromHand() {
        var player = Minecraft.getInstance().player;
        if (player != null && !player.getMainHandItem().isEmpty()) {
            icon = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
        }
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        watchCreated();
        float left = x + (w - COLUMN) / 2f;
        String note = FirstRunParts.t(P + "new.note");
        List<String> noteLines = ui.wrap(note, COLUMN, 12, Ui.REGULAR, 3);
        float noteH = noteLines.size() * 17.4f;
        float total = Ui.headingHeight(24) + 4 + 20 + 14 + 46 + 14 + 14 + 6 + 4 * ROW_H + 3 * 6 + 14 + noteH + 14 + FirstRunParts.BIG_H;
        float pen = y + Math.max(16, (h - total) / 2f);
        ui.label(FirstRunParts.t("rysquests.first.new_book"), left, pen + Ui.headingHeight(24) / 2f, 24, Ui.DISPLAY, ui.c("text.primary"));
        pen += Ui.headingHeight(24) + 4;
        ui.label(FirstRunParts.t(P + "new.sub"), left, pen + 10, 13.5f, Ui.REGULAR, ui.c("text.secondary"));
        pen += 20 + 14;
        renderName(ui, left, pen);
        pen += 46 + 14;
        pen += FirstRunParts.rubric(ui, left, pen, COLUMN, FirstRunParts.t(P + "new.question"), FirstRunParts.t(P + "new.optional")) + 6;
        for (FirstRun.Answer option : FirstRun.Answer.values()) {
            renderAnswer(ui, option, left, pen);
            pen += ROW_H + 6;
        }
        pen += 14 - 6;
        for (int i = 0; i < noteLines.size(); i++) {
            ui.label(noteLines.get(i), left, pen + 17.4f * i + 8.7f, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        pen += noteH + 14;
        renderActions(ui, left, pen);
    }

    private void renderName(Ui ui, float x, float y) {
        ui.box(x, y, 46, 46, 6, ui.c("slot.bg"), ui.c("slot.border"));
        ItemStack stack = icon == null ? ItemStack.EMPTY : TaskText.stackOf(icon);
        if (!stack.isEmpty()) {
            ui.item(stack, x + 7, y + 7, 32);
        } else {
            ui.image(FirstRunParts.MOD_ICON, x + 6, y + 6, 34, 34, 16, 16);
        }
        String hand = FirstRunParts.t(P + "new.hand");
        float handW = 10 + 13 + 6 + ui.width(hand, 12, Ui.MEDIUM) + 10;
        float fieldX = x + 46 + 10;
        float fieldW = COLUMN - 46 - 10 - 10 - handW;
        float fy = y + 3;
        ui.box(fieldX, fy, fieldW, 40, 8, ui.c("input.bg"), ui.c(name.focused() ? "accent" : "panel.border"));
        name.render(ui, fieldX + 11, fy + 11, fieldW - 22, 18, ui.inputText("text.primary"), FirstRunParts.t(P + "new.default_name"));
        ui.hit(fieldX, fy, fieldW, 40, () -> name.click(ui.mouseX, ui.mouseY));
        float hx = x + COLUMN - handW;
        float hy = y + 9;
        boolean over = ui.over(hx, hy, handW, 28);
        ui.box(hx, hy, handW, 28, 8, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        ui.icon(Icons.Shape.HAND, hx + 10, hy + 7.5f, 13, ui.c("text.secondary"));
        ui.label(hand, hx + 10 + 13 + 6, hy + 14, 12, Ui.MEDIUM, ui.c("text.secondary"));
        ui.hit(hx, hy, handW, 28, this::iconFromHand);
        ui.tooltipIfOver(hx, hy, handW, 28, Component.translatable(P + "new.hand_tip"));
    }

    private void renderAnswer(Ui ui, FirstRun.Answer option, float x, float y) {
        boolean on = option == answer;
        int mx = Math.round(ui.mouseX / 2f);
        int my = Math.round(ui.mouseY / 2f);
        Kit.radioRow(ui.g, ui.theme, ui.kitHits, Math.round(x / 2f), Math.round(y / 2f), Math.round(COLUMN / 2f), Math.round(ROW_H / 2f), on, mx, my,
                () -> answer = option);
        glyph(ui, option, x + 10, y + (ROW_H - 40) / 2f, ui.c("text.secondary"));
        float textX = x + 10 + 40 + 12;
        String tag = FirstRunParts.t(option.key() + ".form");
        float tagW = ui.width(tag, 11, Ui.SEMIBOLD) + 14;
        float tagX = x + COLUMN - 14 - 14 - 12 - tagW;
        float textW = tagX - 12 - textX;
        ui.labelFit(FirstRunParts.t(option.key()), textX, y + ROW_H / 2f - 8, textW, 13.5f, Ui.MEDIUM, ui.c("text.primary"));
        ui.labelFit(FirstRunParts.t(option.key() + ".sub"), textX, y + ROW_H / 2f + 9, textW, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        FirstRunParts.tag(ui, tag, tagX, y + ROW_H / 2f, false);
    }

    /** The 40 unit drawings of the answers, line 1.6 wide (mockup 09a). */
    private static void glyph(Ui ui, FirstRun.Answer option, float x, float y, int color) {
        float u = 0.5f;
        float stroke = 1.6f * u;
        float ox = x * u;
        float oy = y * u;
        var g = ui.g;
        switch (option) {
            case ORDER -> {
                Draw.line(g, ox + 5 * u, oy + 9 * u, ox + 31 * u, oy + 9 * u, stroke, color);
                Draw.arc(g, ox + 31 * u, oy + 14 * u, 5 * u, -90, 180, stroke, color);
                Draw.line(g, ox + 31 * u, oy + 19 * u, ox + 9 * u, oy + 19 * u, stroke, color);
                Draw.arc(g, ox + 9 * u, oy + 24 * u, 5 * u, 90, 180, stroke, color);
                Draw.line(g, ox + 9 * u, oy + 29 * u, ox + 35 * u, oy + 29 * u, stroke, color);
            }
            case FINALE -> {
                int points = 72;
                float[] xy = new float[points * 2];
                for (int i = 0; i < points; i++) {
                    double t = i / (double) (points - 1);
                    double angle = t * Math.PI * 3.6;
                    double r = 2 + t * 11;
                    xy[i * 2] = (float) (ox + (20 + r * Math.cos(angle)) * u);
                    xy[i * 2 + 1] = (float) (oy + (20 + r * Math.sin(angle)) * u);
                }
                Draw.polyline(g, xy, points, false, stroke, color);
            }
            case CORE -> {
                Draw.ring(g, ox + 20 * u, oy + 20 * u, 4 * u + stroke / 2, stroke, color);
                curve(g, ox, oy, u, stroke, color, 26, 18, 31, 17, 34, 13, 34, 8);
                curve(g, ox, oy, u, stroke, color, 14, 22, 9, 23, 6, 27, 6, 32);
                curve(g, ox, oy, u, stroke, color, 22, 26, 23, 31, 27, 34, 32, 34);
                curve(g, ox, oy, u, stroke, color, 18, 14, 17, 9, 13, 6, 8, 6);
            }
            case UNKNOWN -> {
                Draw.ring(g, ox + 11 * u, oy + 13 * u, 5 * u + stroke / 2, stroke, color);
                Draw.ring(g, ox + 27 * u, oy + 12 * u, 6 * u + stroke / 2, stroke, color);
                Draw.ring(g, ox + 20 * u, oy + 28 * u, 6 * u + stroke / 2, stroke, color);
            }
        }
    }

    /** A cubic curve of the mockup's path, sampled as one polyline. */
    private static void curve(net.minecraft.client.gui.GuiGraphics g, float ox, float oy, float u, float stroke, int color, float x0, float y0, float x1, float y1,
            float x2, float y2, float x3, float y3) {
        int points = 16;
        float[] xy = new float[points * 2];
        for (int i = 0; i < points; i++) {
            float t = i / (float) (points - 1);
            float a = (1 - t) * (1 - t) * (1 - t);
            float b = 3 * (1 - t) * (1 - t) * t;
            float c = 3 * (1 - t) * t * t;
            float d = t * t * t;
            xy[i * 2] = ox + (a * x0 + b * x1 + c * x2 + d * x3) * u;
            xy[i * 2 + 1] = oy + (a * y0 + b * y1 + c * y2 + d * y3) * u;
        }
        Draw.polyline(g, xy, points, false, stroke, color);
    }

    private void renderActions(Ui ui, float x, float y) {
        String create = FirstRunParts.t(P + "new.create");
        String back = FirstRunParts.t("rysquests.page.back_plain");
        float createW = FirstRunParts.bigWidth(ui, create, true, null);
        float backW = FirstRunParts.bigWidth(ui, back, false, null);
        float createX = x + COLUMN - createW;
        float backX = createX - 8 - backW;
        boolean waiting = sent != null;
        FirstRunParts.big(ui, create, createX, y, true, null, false, waiting ? FirstRunParts.t(P + "new.creating") : null, waiting ? null : this::create);
        FirstRunParts.big(ui, back, backX, y, false, null, false, null, screen::closePage);
        String lock = FirstRunParts.t(P + "new.lock");
        float lockW = backX - 10 - x;
        List<String> lines = ui.wrap(lock, lockW - 19, 12, Ui.REGULAR, 2);
        float cy = y + FirstRunParts.BIG_H / 2f;
        float lineH = 17.4f;
        float top = cy - lines.size() * lineH / 2f;
        ui.lock(x + 1, top + lineH / 2f - 6.5f, 11, ui.c("text.muted"));
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x + 19, top + lineH * i + lineH / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
        }
    }

    // --- input ---

    @Override
    public boolean key(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            create();
            return true;
        }
        if (name.focused()) {
            name.key(key);
            // Letters arrive as typed characters; the book's own keys (K closes it) must not act while the name is being typed.
            return true;
        }
        return false;
    }

    @Override
    public boolean typed(char c) {
        if (name.focused() && name.text().length() < NewBookPayload.MAX_TITLE) {
            return name.typed(c);
        }
        return name.focused();
    }

    @Override
    public void press(double x, double y, int button) {
        if (!name.contains(x, y)) {
            name.focus(false);
        }
    }
}
