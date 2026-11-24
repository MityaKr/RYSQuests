package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.UiScale;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.screen.BookScreen;

/**
 * Author's notes 24.11 after swap-1124c (RYSCore 1.18.0): the book's settings with their segmented switches in slate, glass and
 * vanilla at 75, 100 (Auto), 125 and 150 %, and a tooltip of the header in vanilla. Screenshots:
 * {@code run/screenshots/rysquests_themefix_<theme>_<scale>.png}, {@code rysquests_themefix_tooltip_vanilla.png}.
 */
final class ThemeFixScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThemeFixScenario.class);
    private static final List<String> THEMES = List.of("vanilla", "glass", "slate");
    private static final int[] SCALES = {UiScale.AUTO, 75, 125, 150};

    private ThemeFixScenario() {
    }

    private static BookScreen book() {
        if (!(mc().screen instanceof BookScreen)) {
            mc().setScreen(new BookScreen());
        }
        return (BookScreen) mc().screen;
    }

    static void build() {
        openWorld();
        expect("the panel book reached the client", () -> !ClientBook.current().quests().isEmpty());
        step("match the window of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        for (String theme : System.getProperty("rysquests.autotest.tag", "").equals("tip") ? List.<String>of() : THEMES) {
            for (int scale : SCALES) {
                String name = theme + "_" + (scale == UiScale.AUTO ? "auto" : Integer.toString(scale));
                step("settings " + name, () -> {
                    moveMouse(1, 1);
                    ThemeManager.select("ryscore:" + theme);
                    UiScale.set(scale);
                    mc().setScreen(null);
                    mc().resizeDisplay();
                    book();
                    BookScreen.openTabForTest("now");
                    BookScreen.toggleSettingsForTest();
                }, 20);
                screenshot("themefix_" + name);
            }
        }
        // the brand button with its "i": window 1200×728 mockup pixels at (40, 36), bevel 4, header 56 high; brand from about x 60
        step("vanilla, Auto, header tooltip", () -> {
            ThemeManager.select("ryscore:vanilla");
            UiScale.set(UiScale.AUTO);
            mc().setScreen(null);
            mc().resizeDisplay();
            book();
            BookScreen.openTabForTest("now");
        }, 20);
        final double[] at = {-1, -1};
        // the gear of the settings at the right of the header (vanilla, Auto: about 1162×67 px of the window), then the left part
        for (int x : new int[] {581, 580, 583, 30, 50, 70, 90, 110}) {
            int gx = x;
            step("hover header at " + gx, () -> {
                if (at[0] < 0) moveMouse(gx, 34);
            }, 3);
            step("is there a tooltip at " + gx, () -> {
                if (at[0] < 0 && BookScreen.hoveredTextForTest() != null) {
                    at[0] = gx;
                    at[1] = 34;
                    LOGGER.info("[themefix] tooltip at gui {} 34: {}", gx, BookScreen.hoveredTextForTest());
                }
            }, 1);
        }
        expect("a header tooltip was found", () -> at[0] >= 0);
        step("hold the mouse there", () -> moveMouse(at[0], at[1]), 5);
        screenshot("themefix_tooltip_vanilla");
        step("back to slate, Auto", () -> {
            moveMouse(1, 1);
            ThemeManager.select("ryscore:slate");
            UiScale.set(UiScale.AUTO);
            mc().setScreen(null);
            mc().resizeDisplay();
        }, 10);
    }
}
