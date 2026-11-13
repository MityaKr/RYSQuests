package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import works.repka.rysquests.RYSQuests;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;

/**
 * A book without quests (pictures 03, 04, 05, plan section 2): one title everywhere, "Квестов пока нет", and a line for the role.
 * An operator gets "Собрать книгу" with three steps (03), or, when FTB Quests is in the pack, two ways as cards (05). A player gets
 * a line for a pack author without operator rights (04). The buttons lead to the first-run pages ({@link NewBookView},
 * {@link ImportWizardView}).
 */
final class EmptyBookView implements BookView {
    private static final ResourceLocation MOD_ICON = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "textures/gui/sidebar_book.png");
    private static final float TITLE_PX = 24;
    private static final float TEXT_PX = 13.5f;
    private static final float TEXT_LINE = 20.25f;
    private static final int TEXT_W = 520;
    private static final int GAP = 14;

    private final BookScreen screen;

    EmptyBookView(BookScreen screen) {
        this.screen = screen;
    }

    /** Set by tests to see the page as with or without FTB Quests; null asks the mod list. */
    @org.jetbrains.annotations.Nullable
    static Boolean ftbForTest;

    /** Whether FTB Quests is in the pack: its book can be carried over. */
    static boolean ftbPresent() {
        return ftbForTest != null ? ftbForTest : ModList.get().isLoaded("ftbquests");
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        boolean operator = BookRole.operator();
        if (operator && ftbPresent()) {
            renderWays(ui, x, y, w, h);
        } else if (operator) {
            renderOperator(ui, x, y, w, h);
        } else {
            renderPlayer(ui, x, y, w, h);
        }
    }

    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private void renderOperator(Ui ui, int x, int y, int w, int h) {
        float cx = x + w / 2f;
        List<String> text = ui.wrap(t("rysquests.empty.operator"), TEXT_W, TEXT_PX, Ui.REGULAR, 5);
        float buttonH = 46;
        float stepsH = 22;
        float lockH = Ui.lineHeight(11.5f);
        float total = 72 + GAP + Ui.headingHeight(TITLE_PX) + GAP + text.size() * TEXT_LINE + GAP + buttonH + GAP + 6 + stepsH + GAP + 10 + lockH;
        float pen = y + (h - total) / 2f;
        ui.image(MOD_ICON, cx - 36, pen, 72, 72, 16, 16);
        pen += 72 + GAP;
        pen = title(ui, cx, pen);
        pen = paragraph(ui, cx, pen, text);
        String build = t("rysquests.empty.build");
        float bw = 28 + 16 + 7 + ui.width(build, 14.5f, Ui.SEMIBOLD) + 28;
        float bx = cx - bw / 2f;
        boolean over = ui.over(bx, pen, bw, buttonH);
        ui.fill(bx, pen, bw, buttonH, 9, over ? Draw.mix(ui.c("accent"), 0xFFFFFFFF, 0.1f) : ui.c("accent"));
        ui.icon(Icons.Shape.PENCIL, bx + 28, pen + buttonH / 2f - 8, 16, ui.c("accent.text"));
        ui.label(build, bx + 28 + 16 + 7, pen + buttonH / 2f, 14.5f, Ui.SEMIBOLD, ui.c("accent.text"));
        ui.hit(bx, pen, bw, buttonH, () -> screen.openPage(new NewBookView(screen)));
        pen += buttonH + GAP + 6;
        String[] steps = {t("rysquests.empty.step1"), t("rysquests.empty.step2"), t("rysquests.empty.step3")};
        float stepsW = 0;
        for (String step : steps) {
            stepsW += 22 + 8 + ui.width(step, 12.5f, Ui.REGULAR);
        }
        stepsW += 16 * (steps.length - 1);
        float sx = cx - stepsW / 2f;
        for (int i = 0; i < steps.length; i++) {
            ui.box(sx, pen, 22, 22, 11, ui.c("button.bg"), ui.c("button.border"));
            ui.labelCentered(String.valueOf(i + 1), sx + 11, pen + 11, 11.5f, Ui.SEMIBOLD, ui.c("text.secondary"));
            ui.label(steps[i], sx + 30, pen + 11, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
            sx += 22 + 8 + ui.width(steps[i], 12.5f, Ui.REGULAR) + 16;
        }
        pen += stepsH + GAP + 10;
        lockLine(ui, cx, pen);
    }

    private void renderPlayer(Ui ui, int x, int y, int w, int h) {
        float cx = x + w / 2f;
        List<String> text = ui.wrap(t("rysquests.empty.player"), TEXT_W, TEXT_PX, Ui.REGULAR, 5);
        float noteW = 560 - 15 - 8;
        List<String> note = ui.wrap(t("rysquests.empty.player_note"), noteW, 12, Ui.REGULAR, 4);
        float noteLine = 17.4f;
        float total = 72 + GAP + Ui.headingHeight(TITLE_PX) + GAP + text.size() * TEXT_LINE + GAP + 14 + note.size() * noteLine;
        float pen = y + (h - total) / 2f;
        ui.image(MOD_ICON, cx - 36, pen, 72, 72, 16, 16);
        ui.fill(cx - 36, pen, 72, 72, 0, Draw.alpha(ui.c("panel.bg"), 0.55f));
        pen += 72 + GAP;
        pen = title(ui, cx, pen);
        pen = paragraph(ui, cx, pen, text);
        pen += 14;
        float widest = 0;
        for (String line : note) {
            widest = Math.max(widest, ui.width(line, 12, Ui.REGULAR));
        }
        float nx = cx - (15 + 8 + widest) / 2f;
        infoIcon(ui, nx, pen + noteLine / 2f - 7.5f, 15, ui.c("text.muted"));
        for (int i = 0; i < note.size(); i++) {
            ui.label(note.get(i), nx + 15 + 8, pen + noteLine * i + noteLine / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
        }
    }

    private void renderWays(Ui ui, int x, int y, int w, int h) {
        float cx = x + w / 2f;
        float cardW = 400;
        float cardH = 276;
        float lockH = Ui.lineHeight(11.5f);
        float total = Ui.headingHeight(TITLE_PX) + 18 - 8 + Ui.lineHeight(TEXT_PX) + 18 + cardH + 18 + lockH;
        float pen = y + (h - total) / 2f;
        pen = title(ui, cx, pen) - GAP + 18 - 8;
        ui.labelCentered(t("rysquests.empty.ways"), cx, pen + Ui.lineHeight(TEXT_PX) / 2f, TEXT_PX, Ui.REGULAR, ui.c("text.secondary"));
        pen += Ui.lineHeight(TEXT_PX) + 18;
        float left = cx - cardW - 8;
        card(ui, left, pen, cardW, cardH, true, null, t("rysquests.empty.ftb_title"), t("rysquests.empty.ftb_text"), t("rysquests.empty.ftb_button"),
                t("rysquests.empty.ftb_note"), () -> screen.openPage(new ImportWizardView(screen)));
        card(ui, cx + 8, pen, cardW, cardH, false, MOD_ICON, t("rysquests.empty.scratch_title"), t("rysquests.empty.scratch_text"),
                t("rysquests.empty.scratch_button"), t("rysquests.empty.scratch_note"), () -> screen.openPage(new NewBookView(screen)));
        pen += cardH + 18;
        lockLine(ui, cx, pen);
    }

    /** One way of the two (mockup {@code .hero}): icon, title, words, its button, a note. The first is the advised one. */
    private static void card(Ui ui, float x, float y, float w, float h, boolean advised, ResourceLocation icon, String title, String text, String button,
            String note, Runnable action) {
        ui.box(x, y, w, h, 14, ui.c(advised ? "tab.active.bg" : "card.bg"), ui.c(advised ? "text.secondary" : "card.border"));
        float pad = 20;
        float inner = w - pad * 2;
        float pen = y + pad;
        ui.box(x + pad, pen, 48, 48, 6, ui.c("slot.bg"), ui.c("slot.border"));
        if (icon != null) {
            ui.image(icon, x + pad, pen, 48, 48, 16, 16);
        } else {
            net.minecraft.world.item.ItemStack book = TaskText.stackOf(ResourceLocation.parse("ftbquests:book"));
            ui.item(book.isEmpty() ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK) : book, x + pad + 8, pen + 8, 32);
        }
        float tx = x + pad + 48 + 12;
        if (advised) {
            ui.label(title, tx, pen + 13, 17, Ui.DISPLAY, ui.c("text.primary"));
            String tag = Component.translatable("rysquests.empty.advised").getString();
            float tagW = ui.width(tag, 11, Ui.SEMIBOLD) + 14;
            ui.box(tx, pen + 29, tagW, 18, 5, ui.c("button.bg"), ui.c("button.border"));
            ui.label(tag, tx + 7, pen + 38, 11, Ui.SEMIBOLD, ui.c("text.primary"));
        } else {
            ui.label(title, tx, pen + 24, 17, Ui.DISPLAY, ui.c("text.primary"));
        }
        pen += 48 + 12;
        List<String> lines = ui.wrap(text, inner, 12.5f, Ui.REGULAR, 5);
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x + pad, pen + 18.75f * i + 9.4f, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
        }
        pen += Math.max(76, lines.size() * 18.75f) + 12;
        ui.button(button, x + pad, pen, inner, 42, 9, 13.5f, Ui.SEMIBOLD, 20, advised ? Ui.Style.ACCENT : Ui.Style.PLAIN, action);
        pen += 42 + 12;
        List<String> noteLines = ui.wrap(note, inner, 11.5f, Ui.REGULAR, 3);
        for (int i = 0; i < noteLines.size(); i++) {
            ui.label(noteLines.get(i), x + pad, pen + 16.1f * i + 8, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
    }

    private static float title(Ui ui, float cx, float y) {
        float h = Ui.headingHeight(TITLE_PX);
        ui.labelCentered(t("rysquests.empty.title"), cx, y + h / 2f, TITLE_PX, Ui.DISPLAY, ui.c("text.primary"));
        return y + h + GAP;
    }

    private static float paragraph(Ui ui, float cx, float y, List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            ui.labelCentered(lines.get(i), cx, y + TEXT_LINE * i + TEXT_LINE / 2f, TEXT_PX, Ui.REGULAR, ui.c("text.secondary"));
        }
        return y + lines.size() * TEXT_LINE + GAP;
    }

    /** The help icon of the mockups: a ring with an "i", 15 high. */
    private static void infoIcon(Ui ui, float x, float y, float size, int color) {
        ui.ring(x, y, size, 1.5f, color);
        float cx = x + size / 2f;
        ui.rect(cx - 0.75f, y + size * 0.28f, 1.5f, 1.5f, color);
        ui.rect(cx - 0.75f, y + size * 0.45f, 1.5f, size * 0.3f, color);
    }

    /** "While you build the book, players do not see it." with a padlock. */
    private static void lockLine(Ui ui, float cx, float y) {
        String text = t("rysquests.empty.lock");
        float w = 12 + 6 + ui.width(text, 11.5f, Ui.REGULAR);
        float x = cx - w / 2f;
        float cy = y + Ui.lineHeight(11.5f) / 2f;
        ui.lock(x + 2, cy - 5, 9, ui.c("text.dim"));
        ui.label(text, x + 18, cy, 11.5f, Ui.REGULAR, ui.c("text.dim"));
    }
}
