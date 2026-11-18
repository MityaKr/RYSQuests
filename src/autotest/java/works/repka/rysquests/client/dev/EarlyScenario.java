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
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestStatus;

/**
 * Early completion (ADR-66; RYSQuests 0.2.10), on the small book of {@code src/autotest/early_pack}, which counts early as a whole:
 * "Ворота" is a checkbox, and behind it "Алмаз заранее" (keep a diamond), "Сдать изумруд" (hand an emerald in) and "Строго по
 * порядку" (keep a diamond, but the quest says no to early completion).
 *
 * <ol>
 *     <li>with the gate closed the player holds a diamond and an emerald: the diamond counts for "Алмаз заранее", which stays locked
 *     and says its condition is met (screenshot {@code early_locked}); "Строго по порядку" counts nothing; the emerald stays in the
 *     inventory;</li>
 *     <li>the gate is ticked: "Алмаз заранее" completes at once with its reward waiting, "Строго по порядку" opens and completes by
 *     the usual poll, "Сдать изумруд" opens and waits for the hand in (screenshot {@code early_opened}).</li>
 * </ol>
 * {@code gradlew runAutotest -Pautotest=early}: the dev run, no copy of RepYS needed.
 */
final class EarlyScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(EarlyScenario.class);
    private static final ResourceLocation GATE = ResourceLocation.parse("early:start/gate");
    private static final ResourceLocation WATCH = ResourceLocation.parse("early:start/watch");
    private static final ResourceLocation HANDIN = ResourceLocation.parse("early:start/handin");
    private static final ResourceLocation STRICT = ResourceLocation.parse("early:start/strict");

    private EarlyScenario() {
    }

    private static QuestStatus status(ResourceLocation id) {
        return QuestLogic.status(ClientBook.current().quests().get(id), ClientProgress::get);
    }

    private static int emeralds() {
        return mc().player.getInventory().countItem(Items.EMERALD);
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
        expect("the early book reached the client", () -> ClientBook.current().quests().containsKey(WATCH) && ClientBook.current().meta().preComplete());
        step("nothing done, nothing held", () -> {
            command("gamemode survival @s");
            command("clear @s");
            for (ResourceLocation quest : List.of(GATE, WATCH, HANDIN, STRICT)) {
                command("rysquests progress @s reset " + quest);
            }
        }, 40);
        expect("the gate is open, the rest locked", () -> status(GATE) == QuestStatus.AVAILABLE && status(WATCH) == QuestStatus.LOCKED
                && status(STRICT) == QuestStatus.LOCKED);

        step("the player picks up a diamond and an emerald", () -> {
            command("give @s minecraft:diamond");
            command("give @s minecraft:emerald");
        }, 60);
        expect("the diamond counts early, the quest stays locked", () -> {
            LOGGER.info("[autotest] early: watch {} {}, strict {}, handin {}, emeralds {}", status(WATCH), ClientProgress.get(WATCH).task(0),
                    ClientProgress.get(STRICT).task(0), ClientProgress.get(HANDIN).task(0), emeralds());
            return ClientProgress.get(WATCH).task(0) == 1 && status(WATCH) == QuestStatus.LOCKED && !ClientProgress.get(WATCH).completed();
        });
        expect("the quest that says no counts nothing", () -> ClientProgress.get(STRICT).task(0) == 0);
        expect("the emerald is not taken while the quest is locked", () -> emeralds() == 1 && ClientProgress.get(HANDIN).task(0) == 0);
        step("open the book on the locked quest", () -> {
            BookScreen.openAt(WATCH);
            moveMouse(1, 1);
        }, 30);
        expect("the locked quest is selected", () -> WATCH.equals(BookScreen.selectedQuestForTest()));
        screenshot("early_locked");
        step("back to the world", () -> mc().setScreen(null), 10);

        step("tick the gate", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CHECK, GATE, 0, List.of())), 80);
        expect("the gate is done", () -> ClientProgress.get(GATE).completed());
        expect("the early quest completed as it opened, its reward waits", () -> {
            LOGGER.info("[autotest] early: after the gate watch {}, strict {}, handin {}", status(WATCH), status(STRICT), status(HANDIN));
            return status(WATCH) == QuestStatus.COMPLETED;
        });
        expect("the strict quest opened and completed by the usual poll", () -> ClientProgress.get(STRICT).completed());
        expect("the hand in waits for the player", () -> status(HANDIN) == QuestStatus.AVAILABLE && emeralds() == 1);
        step("open the book on the early quest", () -> {
            BookScreen.openAt(WATCH);
            moveMouse(1, 1);
        }, 30);
        screenshot("early_opened");
        step("back to the world, reset", () -> {
            mc().setScreen(null);
            command("clear @s");
            for (ResourceLocation quest : List.of(GATE, WATCH, HANDIN, STRICT)) {
                command("rysquests progress @s reset " + quest);
            }
        }, 20);
    }
}
