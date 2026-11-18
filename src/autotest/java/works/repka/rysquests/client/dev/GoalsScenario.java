package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.api.QuestsApi;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.QuestGoals;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.ryscore.map.MapApi;
import works.repka.ryscore.world.Labels;

/**
 * Goals of quests on the family's map (RYSMap plan, step T3; RYSQuests 0.2.9), on the small book of {@code src/autotest/goals_pack}:
 * "Лагерь у реки" sends the player to 40 70 40, "Форт в Незере" after it to a box in the Nether, "Любая деревня" to any village,
 * which has no place on the map.
 *
 * <ol>
 *     <li>the layer "Цели квестов" holds the camp alone: the fort is still closed, the village has no position;</li>
 *     <li>the camp's panel: "Цель квеста", its chapter, task and reward, "Открыть квест" and "Сохранить точкой"; the search finds it by
 *     a part of its name;</li>
 *     <li>no label in the world until the camp is pinned, then one, a goal;</li>
 *     <li>the book's panel of the camp has "На карте" on its task when RYSMap is there (screenshot {@code goals_book}), and the
 *     button opens the map;</li>
 *     <li>the player walks to the camp: it is done, its goal drops off, the fort's goal stands in the Nether at the middle of its box;</li>
 *     <li>the public reading of progress: {@code QuestsApi.isCompleted} and {@code completed} see the camp and not the fort.</li>
 * </ol>
 * {@code gradlew runAutotest -Pautotest=goals}: the dev run in {@code run-map}, whose mods hold a RYSMap jar; without it the map
 * steps are skipped and the rest still runs.
 */
final class GoalsScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoalsScenario.class);
    private static final ResourceLocation CAMP = ResourceLocation.parse("goals:trip/camp");
    private static final ResourceLocation FORT = ResourceLocation.parse("goals:trip/fort");
    private static final ResourceLocation VILLAGE = ResourceLocation.parse("goals:trip/village");

    private static volatile String serverAnswer = "";

    private GoalsScenario() {
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
        expect("the goals book reached the client", () -> ClientBook.current().quests().containsKey(CAMP));
        step("start far from the camp, with nothing done or pinned", () -> {
            ClientProgress.pinned().forEach(quest -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of())));
            command("execute in minecraft:overworld run tp @s 0 100 300");
            command("gamemode creative @s");
            for (ResourceLocation quest : List.of(CAMP, FORT, VILLAGE)) {
                command("rysquests progress @s reset " + quest);
            }
        }, 40);
        expect("the layer holds the camp alone", () -> {
            LOGGER.info("[autotest] goals: {}", QuestGoals.goalsForTest());
            return QuestGoals.goalsForTest().equals(List.of(CAMP));
        });
        expect("the layer is in the core's registry", () -> MapApi.layers().stream().anyMatch(layer -> layer.id().equals(QuestGoals.ID)));
        expect("one mark in the overworld, at the camp, a goal", () -> {
            Collection<MapApi.Marker> marks = QuestGoals.markersForTest(Level.OVERWORLD);
            return marks.size() == 1 && marks.stream().allMatch(mark -> mark.kind() == MapApi.Kind.GOAL && mark.x() == 40 && mark.z() == 40 && mark.radius() == 6);
        });
        expect("the camp's panel", () -> {
            MapApi.Panel panel = QuestGoals.panelForTest(CAMP);
            if (panel == null) {
                return false;
            }
            List<String> rows = panel.rows().stream().map(row -> row.key().getString() + "=" + row.value().getString()).toList();
            List<String> actions = panel.actions().stream().map(action -> action.label().getString()).toList();
            LOGGER.info("[autotest] goals: panel {} / {} / {} / {}", panel.title().getString(), panel.subtitle().getString(), rows, actions);
            return panel.title().getString().equals("Лагерь у реки") && panel.subtitle().getString().equals("Цель квеста")
                    && rows.contains("Глава=Поход") && actions.equals(List.of("Открыть квест", "Сохранить точкой"));
        });
        expect("the search finds the camp by a part of its name", () -> QuestGoals.searchForTest("реки").size() == 1
                && QuestGoals.searchForTest("деревня").isEmpty());
        expect("no label in the world before the pin", () -> QuestGoals.labelsForTest(Level.OVERWORLD).isEmpty());
        step("pin the camp", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, CAMP, 0, List.of())), 20);
        expect("one label in the world, a goal", () -> {
            Collection<Labels.Label> labels = QuestGoals.labelsForTest(Level.OVERWORLD);
            return labels.size() == 1 && labels.stream().allMatch(label -> label.kind() == Labels.Kind.GOAL && label.always());
        });

        step("open the book on the camp", () -> {
            LOGGER.info("[autotest] goals: RYSMap present {}", QuestGoals.mapPresent());
            BookScreen.openAt(CAMP);
            moveMouse(1, 1);
        }, 30);
        expect("the camp is selected", () -> CAMP.equals(BookScreen.selectedQuestForTest()));
        screenshot("goals_book");
        step("\"На карте\" opens the map, when RYSMap is there", () -> {
            if (QuestGoals.mapPresent()) {
                QuestGoals.show(ClientBook.current().quests().get(CAMP), 0);
            }
        }, 40);
        expect("the map is open, or there is no map", () -> {
            String screen = mc().screen == null ? "none" : mc().screen.getClass().getName();
            LOGGER.info("[autotest] goals: screen {}", screen);
            return !QuestGoals.mapPresent() || screen.contains("rysmap");
        });
        screenshot("goals_map");
        step("back to the world", () -> mc().setScreen(null), 10);

        step("walk to the camp", () -> command("execute in minecraft:overworld run tp @s 40 70 40"), 80);
        expect("the camp is done", () -> ClientProgress.get(CAMP).completed());
        expect("the camp's goal dropped off, the fort's stands in the Nether at the middle of its box", () -> {
            Collection<MapApi.Marker> nether = QuestGoals.markersForTest(Level.NETHER);
            LOGGER.info("[autotest] goals: after the camp {}", QuestGoals.goalsForTest());
            return QuestGoals.goalsForTest().equals(List.of(FORT)) && QuestGoals.markersForTest(Level.OVERWORLD).isEmpty()
                    && nether.size() == 1 && nether.stream().allMatch(mark -> mark.x() == 110 && mark.y() == 65 && mark.z() == 0 && mark.radius() == 20);
        });
        expect("the pinned camp has no label left", () -> QuestGoals.labelsForTest(Level.OVERWORLD).isEmpty());

        step("read progress through the public API", () -> onServer(player -> {
            boolean camp = QuestsApi.isCompleted(QuestsApi.progressOwner(player), CAMP);
            boolean fort = QuestsApi.isCompleted(player, FORT);
            Set<ResourceLocation> done = QuestsApi.completed(QuestsApi.progressOwner(player));
            serverAnswer = camp + " " + fort + " " + done.contains(CAMP) + " " + done.contains(FORT) + " " + QuestsApi.completed(java.util.UUID.randomUUID()).isEmpty();
        }), 10);
        expect("the API sees the camp and not the fort", () -> {
            LOGGER.info("[autotest] goals: api {}", serverAnswer);
            return serverAnswer.equals("true false true false true");
        });

        step("unpin and reset", () -> {
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, CAMP, 0, List.of()));
            for (ResourceLocation quest : List.of(CAMP, FORT, VILLAGE)) {
                command("rysquests progress @s reset " + quest);
            }
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
