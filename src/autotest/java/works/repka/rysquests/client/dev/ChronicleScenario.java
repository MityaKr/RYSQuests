package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.data.ViewMode;

/**
 * The epoch's reward on the map, the pages "О книге" and the atlases for players (plan _audit/chronicle, B1 and B2; RYSQuests 0.2.7,
 * ADR-60), on the small book of {@code src/autotest/chronicle_pack}: two epochs whose rewards are quests of the off-map chapter
 * "Начало", the guide "О книге" named by {@code about}, and the matrix "Атлас сборки".
 *
 * <ol>
 *     <li>"Начало" keeps only the epochs' rewards, so it is not in the sidebar, and none of them is a card;</li>
 *     <li>epoch I is passed from the start: its plate carries the mark, the card opens, "Забрать награду" with a pick gives the
 *     reward once, a second press gives nothing;</li>
 *     <li>epoch II waits for "Латунный механизм", then waits on "Сейчас" among the rewards;</li>
 *     <li>the book's name opens "О книге" over the tab, the dot goes out once a page is read;</li>
 *     <li>a player who is not an operator sees "Атлас сборки" inside "Гайды".</li>
 * </ol>
 * {@code gradlew runAutotest -Pautotest=chronicle}: the dev run, no copy of RepYS needed.
 */
final class ChronicleScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronicleScenario.class);
    private static final ResourceLocation START = ResourceLocation.parse("chronicle:start");
    private static final ResourceLocation ERA_1 = ResourceLocation.parse("chronicle:start/i_mechanics");
    private static final ResourceLocation ERA_2 = ResourceLocation.parse("chronicle:start/ii_industry");
    private static final ResourceLocation ALLOY = ResourceLocation.parse("chronicle:mech/alloy");
    private static final ResourceLocation MECHANISM = ResourceLocation.parse("chronicle:mech/mechanism");
    private static final ResourceLocation PICKED = ResourceLocation.parse("minecraft:iron_ingot");
    private static final ResourceLocation NOTE = ResourceLocation.parse("minecraft:paper");
    /** The reward of epoch I that is a choice, and the option the scenario picks: 32 iron ingots. */
    private static final int CHOICE = 2;
    private static final int OPTION = 1;

    private ChronicleScenario() {
    }

    static void build() {
        openWorld();
        step("match the window of the mockups, Russian", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        expect("the chronicle book reached the client", () -> ClientBook.current().quests().containsKey(ERA_2));
        step("every quest from nothing, an empty inventory", () -> {
            for (ResourceLocation quest : new ResourceLocation[] {ERA_1, ERA_2, ALLOY, MECHANISM}) {
                command("rysquests progress @s reset " + quest);
            }
            command("clear @s");
        }, 40);
        expect("\"Начало\" keeps only rewards: not in the sidebar, no cards", () -> !BookScreen.shownChaptersForTest().contains(START)
                && BookScreen.chapterQuestsForTest(START).isEmpty());
        expect("epoch I is passed, epoch II waits", () -> "READY".equals(BookScreen.epochRewardStateForTest(1)) && "LOCKED".equals(BookScreen.epochRewardStateForTest(2)));

        step("open the map on the whole book", () -> {
            BookRole.forceForTest(false);
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
            BookScreen.mapFlyForTest(null, 0, null);
            moveMouse(2, 2);
        }, 60);
        screenshot("chronicle_map_mark");
        step("open the card of epoch I", () -> BookScreen.showEpochRewardForTest(1), 60);
        expect("the card is open", () -> BookScreen.epochCardForTest() == 1);
        screenshot("chronicle_card_ready");
        step("pick the iron and take the reward", () -> BookScreen.claimEpochForTest(1, CHOICE, OPTION), 30);
        expect("the reward is taken", () -> "CLAIMED".equals(BookScreen.epochRewardStateForTest(1)) && ClientProgress.get(ERA_1).completed());
        expect("the pick and the note are in the inventory", () -> held(PICKED) == 32 && held(NOTE) == 1);
        screenshot("chronicle_card_claimed");
        step("press it once more", () -> BookScreen.claimEpochForTest(1, CHOICE, 0), 30);
        expect("nothing is given twice", () -> {
            LOGGER.info("[autotest] chronicle: iron {}, note {}", held(PICKED), held(NOTE));
            return held(PICKED) == 32 && held(NOTE) == 1 && held(ResourceLocation.parse("minecraft:iron_pickaxe")) == 0;
        });

        step("open the card of epoch II, still closed", () -> BookScreen.showEpochRewardForTest(2), 40);
        screenshot("chronicle_card_locked");
        step("finish the key quest of epoch I", () -> {
            command("rysquests progress @s complete " + ALLOY);
            command("rysquests progress @s complete " + MECHANISM);
        }, 40);
        expect("epoch II is passed", () -> "READY".equals(BookScreen.epochRewardStateForTest(2)));
        step("\"Сейчас\" lists the reward of epoch II", () -> {
            BookScreen.openTabForTest("now");
            moveMouse(2, 2);
        }, 30);
        screenshot("chronicle_now");

        expect("the dot on the book's name lights before any page is read", BookScreen::aboutUnreadForTest);
        step("press the book's name", BookScreen::toggleAboutForTest, 40);
        expect("\"О книге\" is open on its first page", () -> BookScreen.aboutPageForTest() == 0);
        expect("the page counts as read", () -> !BookScreen.aboutUnreadForTest());
        screenshot("chronicle_about");
        step("press the name again", BookScreen::toggleAboutForTest, 10);
        expect("the tab is back", () -> BookScreen.aboutPageForTest() == -1);

        expect("a player sees the atlas inside \"Гайды\"", () -> {
            LOGGER.info("[autotest] chronicle: author's tabs for a player {}", BookScreen.authorSectionsForTest());
            return BookScreen.authorSectionsForTest().contains("chronicle:atlas");
        });
        step("open the atlas as a player", () -> {
            BookScreen.openTabForTest("guides");
            BookScreen.showSectionForTest("chronicle:atlas");
            moveMouse(2, 2);
        }, 40);
        screenshot("chronicle_atlas_player");
        step("close the book", () -> {
            mc().setScreen(null);
            BookRole.forceForTest(null);
        }, 5);
    }

    private static int held(ResourceLocation item) {
        int count = 0;
        for (ItemStack stack : mc().player.getInventory().items) {
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
