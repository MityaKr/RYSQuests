package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.api.QuestsApi;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.hud.StickerBoard;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.CollectionMarks;
import works.repka.rysquests.progress.ProgressStore;

/**
 * A collection in the achievements and the limit of pinned cards (RYSQuests 0.2.8, ADR-62), on the small team book of
 * {@code src/autotest/stats_pack}: "Сокровища недр" collects eight items (levels at 3, 6 and 8), coal counted by 16 held at once.
 *
 * <ol>
 *     <li>an offline teammate is in our team; the diamond and the emerald come as a script carries over the marks it kept before
 *     collections came to the book ({@code RYSQuests.collect}), the player picks up iron, gold, redstone and 10 coal of 16;</li>
 *     <li>five of eight are collected, the bronze level is reached, the silver one counts 5 of 6; the coal waits at 10;</li>
 *     <li>the window shows the items with their marks (screenshot {@code collection_panel});</li>
 *     <li>three levels pinned fill the HUD: the pin of a fourth achievement is greyed out, and its tooltip, in the family's look, says
 *     why (screenshot {@code collection_pin_full}); the pinned ones carry the pin on their cards;</li>
 *     <li>the HUD: the card of the collection's level has its bar like the others (screenshot {@code collection_hud});</li>
 *     <li>the teammate leaves: the team keeps its marks.</li>
 * </ol>
 * {@code gradlew runAutotest -Pautotest=collection}: the dev run, no copy of RepYS needed.
 */
final class CollectionScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionScenario.class);
    private static final UUID MATE = UUID.fromString("5a7e0000-0000-4000-8000-00000000a7e5");
    private static final ResourceLocation TREASURES = ResourceLocation.parse("stats:treasures");
    private static final ResourceLocation MARKS = ResourceLocation.parse("stats:_achievements/treasures/_collection");
    private static final ResourceLocation BRONZE = ResourceLocation.parse("stats:_achievements/treasures/bronze");
    private static final ResourceLocation SILVER = ResourceLocation.parse("stats:_achievements/treasures/silver");
    private static final ResourceLocation GOLD = ResourceLocation.parse("stats:_achievements/treasures/gold");
    private static final ResourceLocation EXPLORER = ResourceLocation.parse("stats:_achievements/explorer/gold");
    private static final ResourceLocation JUMP_3 = ResourceLocation.parse("stats:_achievements/jumper/gold");
    private static final ResourceLocation LIFE_3 = ResourceLocation.parse("stats:_achievements/long_liver/gold");
    private static final int COAL = 6;

    private CollectionScenario() {
    }

    private static int collected() {
        Quest marks = ClientBook.current().quests().get(MARKS);
        return marks == null ? -1 : CollectionMarks.count(marks, ClientProgress.get(MARKS));
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
        expect("the collection reached the client with its marks", () -> ClientBook.current().quests().containsKey(MARKS)
                && ClientBook.current().quests().get(MARKS).tasks().size() == 8);
        step("free the board and the inventory of earlier runs", () -> {
            ClientProgress.pinned().forEach(quest -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of())));
            StickerBoard.clearForTest();
            command("clear @s");
        }, 20);
        step("an offline teammate in the player's own team", () -> onServer(player -> {
            ProgressStore store = ProgressStore.get(player.server);
            store.setTeam(player.getUUID(), player.getUUID());
            store.setTeam(MATE, player.getUUID());
        }), 10);
        step("the collection and the explorer from nothing", () -> {
            for (ResourceLocation quest : List.of(MARKS, BRONZE, SILVER, GOLD, EXPLORER)) {
                command("rysquests progress @s reset " + quest);
            }
        }, 40);
        expect("nothing is collected", () -> collected() == 0 && !ClientProgress.get(BRONZE).completed());

        step("the marks a script kept come over", () -> onServer(player -> {
            String owner = QuestsApi.progressOwner(player).toString();
            boolean diamond = QuestsApi.collect(player.server, owner, TREASURES.toString(), "minecraft:diamond");
            boolean emerald = QuestsApi.collect(player.server, owner, TREASURES.toString(), "minecraft:emerald");
            boolean twice = QuestsApi.collect(player.server, owner, TREASURES.toString(), "minecraft:diamond");
            boolean stranger = QuestsApi.collect(player.server, owner, TREASURES.toString(), "minecraft:stick");
            LOGGER.info("[autotest] collection: carried over diamond {} emerald {} twice {} stick {}", diamond, emerald, twice, stranger);
        }), 30);
        expect("two are collected", () -> collected() == 2);
        step("the player picks up iron, gold, redstone and some coal", () -> {
            command("give @s minecraft:iron_ingot");
            command("give @s minecraft:gold_ingot");
            command("give @s minecraft:redstone");
            command("give @s minecraft:coal " + (COAL + 4));
        }, 60);
        expect("five of eight, bronze reached, silver counts", () -> {
            LOGGER.info("[autotest] collection: collected {}, bronze {}, silver {}, coal {}", collected(), ClientProgress.get(BRONZE).completed(),
                    ClientProgress.get(SILVER).task(0), ClientProgress.get(MARKS).task(6));
            return collected() == 5 && ClientProgress.get(BRONZE).completed() && ClientProgress.get(SILVER).task(0) == 5;
        });
        expect("coal waits for 16 at once", () -> ClientProgress.get(MARKS).task(6) == COAL + 4);

        step("pin three levels", () -> {
            for (ResourceLocation quest : List.of(SILVER, JUMP_3, LIFE_3)) {
                PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of()));
            }
        }, 20);
        expect("three are pinned", () -> ClientProgress.pinned().containsAll(List.of(SILVER, JUMP_3, LIFE_3)));
        step("open the book on the collection", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.showSectionForTest("achievements");
            BookScreen.achievementSelectForTest(TREASURES);
            moveMouse(1, 1);
        }, 30);
        expect("achievements are open", () -> "achievements".equals(BookScreen.currentSectionForTest()));
        expect("the collection's level is pinned", () -> "PINNED".equals(BookScreen.achievementPinForTest(SILVER)));
        screenshot("collection_panel");

        step("select the explorer, whose pin has no place left", () -> BookScreen.achievementSelectForTest(ResourceLocation.parse("stats:explorer")), 10);
        expect("its pin is greyed out", () -> "FULL".equals(BookScreen.achievementPinForTest(EXPLORER)));
        step("hover the pin", () -> {
            float[] pin = BookScreen.achievementPinButtonForTest();
            if (pin != null) {
                moveMouse(pin[0] + pin[2] / 2, pin[1] + pin[3] / 2);
            }
        }, 10);
        expect("the tooltip says why", () -> {
            String text = BookScreen.hoveredTextForTest();
            LOGGER.info("[autotest] collection: pin tooltip {}", text);
            return text != null && text.contains("3 из 3");
        });
        screenshot("collection_pin_full");
        step("a press on a greyed pin pins nothing", () -> {
            float[] pin = BookScreen.achievementPinButtonForTest();
            if (pin != null) {
                AutoTest.click((pin[0] + pin[2] / 2) * 2, (pin[1] + pin[3] / 2) * 2);
            }
        }, 20);
        expect("still three pinned, the explorer not among them", () -> ClientProgress.pinned().size() == 3 && !ClientProgress.pinned().contains(EXPLORER));

        step("the world without the book", () -> mc().setScreen(null), 20);
        expect("three cards on the board", () -> StickerBoard.cardsForTest() == 3);
        screenshot("collection_hud");

        step("the teammate leaves", () -> onServer(player -> ProgressStore.get(player.server).leaveTeam(MATE)), 30);
        expect("the team keeps its marks", () -> collected() == 5);

        step("unpin and empty the inventory", () -> {
            for (ResourceLocation quest : List.of(SILVER, JUMP_3, LIFE_3)) {
                PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of()));
            }
            command("clear @s");
        }, 20);
    }

    private static void onServer(Consumer<ServerPlayer> action) {
        MinecraftServer server = mc().getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(mc().player.getUUID());
            if (player != null) {
                action.accept(player);
            }
        });
    }
}
