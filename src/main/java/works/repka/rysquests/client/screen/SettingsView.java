package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.UiScale;

/**
 * The book's settings as a page of their own (picture 32, plan section 3): one column, no sidebar, "Назад: Сейчас" above. The gear
 * opens it and stays lit while it is open; no tab is lit meanwhile. "Back" returns where the player was.
 */
final class SettingsView implements BookView {
    private static final int COLUMN = 560;
    private static final int PAD_TOP = 18;
    private static final int ROW_MIN = 36;
    private static final int GAP = 10;
    private static final int[] SCALES = {UiScale.AUTO, 75, 100, 125, 150};

    private final BookScreen screen;
    private final String back;
    private final Scroll scroll = new Scroll();

    /** @param back the name of the place "Back" returns to */
    SettingsView(BookScreen screen, String back) {
        this.screen = screen;
        this.back = back;
    }

    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        int column = Math.min(COLUMN, w - 32);
        float left = x + (w - column) / 2f;
        ui.clip(x, y, w, h);
        float pen = y + PAD_TOP - scroll.offset();
        float top = pen;
        String backLabel = t("rysquests.page.back", back);
        float backW = 14 + 6 + ui.width(backLabel, 12.5f, Ui.MEDIUM);
        boolean overBack = ui.over(left, pen, backW, 18);
        ui.icon(Icons.Shape.CHEVRON_LEFT, left, pen + 2, 14, ui.c(overBack ? "text.primary" : "link"));
        ui.label(backLabel, left + 20, pen + 9, 12.5f, Ui.MEDIUM, ui.c(overBack ? "text.primary" : "link"));
        ui.hit(left, pen, backW, 18, screen::closePage);
        pen += 18 - 4 + 12;
        float titleH = Ui.headingHeight(19);
        ui.label(t("rysquests.settings.title"), left, pen + titleH / 2f, 19, Ui.DISPLAY, ui.c("text.primary"));
        pen += titleH + 14;

        pen = rubric(ui, left, pen, column, t("rysquests.settings.look"), null, null);
        List<Theme> themes = ThemeManager.all();
        List<Segments.Item> themeItems = new ArrayList<>();
        int themeIndex = 0;
        for (int i = 0; i < themes.size(); i++) {
            themeItems.add(Segments.item(themes.get(i).name));
            if (themes.get(i).id.equals(ThemeManager.get().id)) {
                themeIndex = i;
            }
        }
        pen = segmentsRow(ui, left, pen, column, t("rysquests.settings.theme"), t("rysquests.settings.theme_sub"), themeItems, themeIndex,
                index -> ThemeManager.select(themes.get(index).id));
        pen = segmentsRow(ui, left, pen, column, t("rysquests.settings.opens"), t("rysquests.settings.opens_sub"),
                List.of(Segments.item(t("rysquests.settings.opens_now")), Segments.item(t("rysquests.settings.opens_last"))),
                ClientConfig.OPEN_WHERE_CLOSED.get() ? 1 : 0, index -> set(ClientConfig.OPEN_WHERE_CLOSED, index == 1));
        List<Segments.Item> scaleItems = new ArrayList<>();
        int scaleIndex = 0;
        for (int i = 0; i < SCALES.length; i++) {
            scaleItems.add(Segments.item(SCALES[i] == UiScale.AUTO ? t("rysquests.settings.scale_auto") : t("rysquests.settings.percent", SCALES[i])));
            if (SCALES[i] == UiScale.setting()) {
                scaleIndex = i;
            }
        }
        pen = segmentsRow(ui, left, pen, column, t("rysquests.settings.scale"), t("rysquests.settings.scale_sub"), scaleItems, scaleIndex,
                index -> UiScale.set(SCALES[index]));
        pen += 6;
        pen = rubric(ui, left, pen, column, t("rysquests.settings.over_game"), null, null);
        pen = toggleRow(ui, left, pen, column, t("rysquests.settings.pinned"), t("rysquests.settings.pinned_sub"),
                ClientConfig.HUD_PINNED::get, on -> set(ClientConfig.HUD_PINNED, on));
        // The cards over the world cannot be clicked (they show only with no window open), so folding them is a setting here.
        pen = toggleRow(ui, left, pen, column, t("rysquests.settings.collapsed"), t("rysquests.settings.collapsed_sub"),
                ClientConfig.HUD_COLLAPSED::get, on -> set(ClientConfig.HUD_COLLAPSED, on));
        pen = segmentsRow(ui, left, pen, column, t("rysquests.settings.side"), t("rysquests.settings.side_sub"),
                List.of(Segments.item(t("rysquests.settings.side_left")), Segments.item(t("rysquests.settings.side_right"))),
                ClientConfig.HUD_RIGHT.get() ? 1 : 0, index -> set(ClientConfig.HUD_RIGHT, index == 1));
        pen = toggleRow(ui, left, pen, column, t("rysquests.settings.toast"), null, ClientConfig.COMPLETION_TOAST::get, on -> set(ClientConfig.COMPLETION_TOAST, on));
        pen = toggleRow(ui, left, pen, column, t("rysquests.settings.team_toasts"), null, ClientConfig.TEAM_QUEST_TOASTS::get, on -> set(ClientConfig.TEAM_QUEST_TOASTS, on));
        // The sound of notices is one setting for the whole family, kept by RYSCore.
        pen = toggleRow(ui, left, pen, column, t("rysquests.settings.notice_sound"), null, works.repka.ryscore.ui.Notices::soundOn, works.repka.ryscore.ui.Notices::setSoundOn);
        pen = toggleRow(ui, left, pen, column, t("rysquests.settings.sound"), null, ClientConfig.REWARD_SOUND::get, on -> set(ClientConfig.REWARD_SOUND, on));
        pen += 6;
        pen = rubric(ui, left, pen, column, t("rysquests.settings.keys"), t("rysquests.settings.keys_change"), () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new KeyBindsScreen(minecraft.screen, minecraft.options));
        });
        pen = keys(ui, left, pen, column);
        ui.unclip();
        scroll.content(pen - top + PAD_TOP + 18, h);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll.wheel(amount, 40);
    }

    private static void set(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value, boolean on) {
        value.set(on);
        value.save();
    }

    private static float rubric(Ui ui, float x, float y, float w, String text, String link, Runnable action) {
        float h = Ui.lineHeight(11.5f);
        ui.caps(text, x, y + h / 2f, ui.c("text.dim"));
        if (link != null) {
            float lw = ui.width(link, 11.5f, Ui.REGULAR);
            boolean over = ui.over(x + w - lw, y, lw, h);
            ui.labelRight(link, x + w, y + h / 2f, 11.5f, Ui.REGULAR, ui.c(over ? "text.primary" : "link"));
            ui.hit(x + w - lw, y, lw, h, action);
        }
        return y + h + GAP;
    }

    /** The words of a row: its name and a small line under it. @return the row's height */
    private static float words(Ui ui, float x, float y, float w, String name, String sub, float minH) {
        List<String> subLines = sub == null ? List.of() : ui.wrap(sub, w, 11.5f, Ui.REGULAR, 3);
        float nameH = Ui.lineHeight(13);
        float subLine = 15.5f;
        float textH = nameH + subLines.size() * subLine;
        float h = Math.max(minH, textH);
        float top = y + (h - textH) / 2f;
        ui.label(name, x, top + nameH / 2f, 13, Ui.REGULAR, ui.c("text.body"));
        for (int i = 0; i < subLines.size(); i++) {
            ui.label(subLines.get(i), x, top + nameH + subLine * i + subLine / 2f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        return h;
    }

    private static float segmentsRow(Ui ui, float x, float y, float w, String name, String sub, List<Segments.Item> items, int selected,
            java.util.function.IntConsumer pick) {
        float segW = Segments.width(items, selected);
        float h = words(ui, x, y, w - segW - 12, name, sub, ROW_MIN);
        Segments.render(ui, x + w - segW, y + (h - Segments.HEIGHT) / 2f, items, selected, pick);
        return y + h + GAP;
    }

    private static float toggleRow(Ui ui, float x, float y, float w, String name, String sub, BooleanSupplier on, Consumer<Boolean> set) {
        float h = words(ui, x, y, w - 36 - 12, name, sub, ROW_MIN);
        int tx = Math.round((x + w - 36) / 2f);
        int ty = Math.round((y + h / 2f - 10) / 2f);
        boolean hot = ui.over(tx * 2f, ty * 2f, 36, 20);
        Kit.toggle(ui.g, ui.theme, tx, ty, on.getAsBoolean(), hot);
        ui.hit(tx * 2f, ty * 2f, 36, 20, () -> set.accept(!on.getAsBoolean()));
        return y + h + GAP;
    }

    /** The book's keys as key caps with their meaning, in rows that wrap. */
    private static float keys(Ui ui, float x, float y, float w) {
        // Keys without a binding (pin and fold have none by default) are left out rather than shown as "not bound".
        List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[] {QuestKeys.label(QuestKeys.OPEN_BOOK).getString(), t("rysquests.settings.key_book")});
        if (!QuestKeys.PIN.isUnbound()) {
            rows.add(new String[] {QuestKeys.label(QuestKeys.PIN).getString(), t("rysquests.settings.key_pin")});
        }
        if (!QuestKeys.BOARD.isUnbound()) {
            rows.add(new String[] {QuestKeys.label(QuestKeys.BOARD).getString(), t("rysquests.settings.key_board")});
        }
        rows.add(new String[] {"/", t("rysquests.settings.key_search")});
        String[][] keys = rows.toArray(new String[0][]);
        float pen = x;
        float row = y;
        float rowH = 18;
        for (String[] key : keys) {
            float capW = Math.max(18, ui.width(key[0], 11, Ui.REGULAR) + 10);
            float itemW = capW + 6 + ui.width(key[1], 12.5f, Ui.REGULAR);
            if (pen > x && pen + itemW > x + w) {
                pen = x;
                row += rowH + 8;
            }
            ui.box(pen, row, capW, rowH, 5, ui.c("button.bg"), ui.c("button.border"));
            ui.labelCentered(key[0], pen + capW / 2f, row + rowH / 2f, 11, Ui.REGULAR, ui.c("text.muted"));
            ui.label(key[1], pen + capW + 6, row + rowH / 2f, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
            pen += itemW + 16;
        }
        return row + rowH;
    }
}
