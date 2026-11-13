package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.data.ViewMode;

/**
 * The quest's menu "⋯" in the head of its panel (the author's note on 0.2.0): its lines for an operator, "Завершить" completes the
 * quest without its tasks and leaves the reward to take, "Сбросить прогресс" starts tasks and reward again; each asks first.
 * {@code gradlew runAutotest -Pautotest=quest_menu}, on the example book in a world with cheats (an operator).
 */
final class QuestMenuScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestMenuScenario.class);
    private static final ResourceLocation CHAPTER = ResourceLocation.parse("example:01_start");
    private static final ResourceLocation QUEST = ResourceLocation.parse("example:start/first_tools");

    private QuestMenuScenario() {
    }

    static void build() {
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("start the quest from nothing, one log in hand", () -> {
            command("rysquests progress @s reset " + QUEST);
            command("give @s minecraft:oak_log 1");
        }, 40);
        step("open the book on the list with the quest", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.LIST);
            BookScreen.selectForTest(CHAPTER, QUEST);
        }, 20);
        expect("the book is open", () -> mc().screen instanceof BookScreen);
        step("open the quest's menu", () -> {
            moveMouse(1, 1);
            BookScreen.questMenuForTest(true);
        }, 10);
        screenshot("quest_menu_open");
        expect("an operator has the four lines, reset off while nothing is done", () -> {
            List<String> items = BookScreen.questMenuItemsForTest();
            LOGGER.info("[autotest] quest menu: {}", items);
            return items.size() == 4 && items.get(0).endsWith("(off)") && !items.get(1).endsWith("(off)");
        });
        step("press Завершить", () -> BookScreen.questMenuPressForTest(1), 10);
        expect("the menu asks first", BookScreen::dialogOpenForTest);
        screenshot("quest_menu_complete_question");
        step("answer yes", BookScreen::questMenuYesForTest, 20);
        expect("the quest is completed, its reward waits", () -> ClientProgress.get(QUEST).completed() && ClientProgress.get(QUEST).claimed().isEmpty());
        screenshot("quest_menu_completed");
        step("open the menu again and press Сбросить прогресс", () -> {
            BookScreen.questMenuForTest(true);
            BookScreen.questMenuPressForTest(0);
        }, 10);
        expect("the reset asks first", BookScreen::dialogOpenForTest);
        screenshot("quest_menu_reset_question");
        step("answer yes", BookScreen::questMenuYesForTest, 20);
        expect("the quest starts again", () -> !ClientProgress.get(QUEST).completed() && ClientProgress.get(QUEST).tasks().stream().allMatch(value -> value == 0));
        expect("complete is pressable again", () -> !BookScreen.questMenuItemsForTest().get(1).endsWith("(off)"));
        screenshot("quest_menu_reset");
        step("close the book", () -> mc().setScreen(null), 5);
    }
}
