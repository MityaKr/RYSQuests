package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.clickMouse;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.ryscore.ui.UiScale;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.task.ItemTask;

/**
 * Items in the book act like items of the recipe viewer (the author's note on 0.2.1): the game's tooltip on hover, the recipe on a
 * click, the usages on the right button; a shop card keeps its click for selecting the offer and gives the tooltip and usages.
 * {@code gradlew repysAutotest -Pautotest=items}, on the copy of RepYS with JEI and RYSIndex.
 */
final class ItemsScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemsScenario.class);
    private static Quest quest;

    private ItemsScenario() {
    }

    static void check() {
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        expect("the recipe viewer is there", Recipes::available);
        step("a player, the window of the mockups", () -> {
            BookRole.forceForTest(false);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            quest = ClientBook.current().quests().values().stream()
                    .filter(q -> q.requires().isEmpty() && q.tasks().stream().anyMatch(t -> t instanceof ItemTask) && q.rewards().stream().anyMatch(r -> r instanceof ItemReward))
                    .findFirst().orElse(null);
            LOGGER.info("[autotest] items: quest {}", quest == null ? null : quest.id());
        }, 20);
        expect("a quest with an item task and an item reward", () -> quest != null);
        step("open the quest in the list", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.LIST);
            BookScreen.selectForTest(quest.chapter().orElse(null), quest.id());
        }, 20);
        step("hover the first item of the panel", () -> hover(0), 6);
        screenshot("items_task_tooltip");
        expect("the item is hovered", () -> !BookScreen.hoveredStack().isEmpty());
        step("click it", () -> clickMouse(0), 20);
        expect("RYSIndex shows its recipes", () -> mc().screen != null && mc().screen.getClass().getName().contains("rysindex"));
        screenshot("items_task_recipes");
        step("back to the book, the shop", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("shop");
        }, 20);
        step("hover a card's item", () -> hover(1), 6);
        screenshot("items_shop_tooltip");
        expect("the card's item is hovered", () -> !BookScreen.hoveredStack().isEmpty());
        step("right click it", () -> clickMouse(1), 20);
        expect("RYSIndex shows its usages", () -> mc().screen != null && mc().screen.getClass().getName().contains("rysindex"));
        screenshot("items_shop_usages");
        step("close", () -> mc().setScreen(null), 5);
    }

    /** Moves the mouse onto the first item drawn with the given kind: 0 with a click of its own, 1 hover only. */
    private static void hover(int kind) {
        List<float[]> spots = BookScreen.itemSpotsForTest();
        LOGGER.info("[autotest] items: {} spots", spots.size());
        for (float[] spot : spots) {
            if (spot[4] == kind) {
                moveMouse(UiScale.toGui((spot[0] + spot[2] / 2f) / 2f), UiScale.toGui((spot[1] + spot[3] / 2f) / 2f));
                return;
            }
        }
        LOGGER.info("[autotest] items: no spot of kind {}", kind);
    }
}
