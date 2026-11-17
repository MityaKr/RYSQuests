package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.hud.StickerBoard;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.ProgressStore;

/**
 * Statistics counted for the whole team and the bar of a pinned level (RYSQuests 0.2.8, ADR-61), on the small team book of
 * {@code src/autotest/stats_pack}: "Прыгун" counts jumps (5, 1 000, 100 000), "Долгожитель" the time since death (1, 10, 50 hours).
 *
 * <ol>
 *     <li>a teammate who is offline is in our own team, with a statistics file of 600 jumps and 30 000 ticks since death;</li>
 *     <li>the player's 150 jumps add to the teammate's: level 2 counts 750, level 1 is reached; before 0.2.8 it counted the player's
 *     150 alone, while the teammate's own number was the only one that moved it for him;</li>
 *     <li>a streak is the longest current one: 50 000 of the player's, then the player "dies" (the game's reset of the statistic) and
 *     the counter comes down to the teammate's 30 000, as the text "Смерть начинает счёт заново" says;</li>
 *     <li>the two current levels pinned: each card has a thin bar to its own level's amount, bronze for "Долгожитель", silver for
 *     "Прыгун", the same colors as in the book.</li>
 * </ol>
 * {@code gradlew runAutotest -Pautotest=stat_team}: the dev run, no copy of RepYS needed.
 */
final class StatTeamScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatTeamScenario.class);
    private static final UUID MATE = UUID.fromString("5a7e0000-0000-4000-8000-00000000a7e5");
    private static final ResourceLocation JUMP_1 = ResourceLocation.parse("stats:_achievements/jumper/bronze");
    private static final ResourceLocation JUMP_2 = ResourceLocation.parse("stats:_achievements/jumper/silver");
    private static final ResourceLocation JUMP_3 = ResourceLocation.parse("stats:_achievements/jumper/gold");
    private static final ResourceLocation LIFE_1 = ResourceLocation.parse("stats:_achievements/long_liver/bronze");
    private static final ResourceLocation LIFE_2 = ResourceLocation.parse("stats:_achievements/long_liver/silver");
    private static final ResourceLocation LIFE_3 = ResourceLocation.parse("stats:_achievements/long_liver/gold");
    private static final int MATE_JUMPS = 600;
    private static final int MATE_LIFE = 30_000;
    private static final int OWN_JUMPS = 150;
    private static final int OWN_LIFE = 50_000;

    private StatTeamScenario() {
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
        expect("the stats book reached the client", () -> ClientBook.current().quests().containsKey(LIFE_2));
        step("free the board of earlier runs", () -> {
            ClientProgress.pinned().forEach(quest -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of())));
            StickerBoard.clearForTest();
        }, 20);
        step("an offline teammate with his own statistics, the player's own set", () -> onServer(player -> {
            writeMateStats(player.server);
            ProgressStore store = ProgressStore.get(player.server);
            store.setTeam(player.getUUID(), player.getUUID());
            store.setTeam(MATE, player.getUUID());
            player.getStats().setValue(player, Stats.CUSTOM.get(Stats.JUMP), OWN_JUMPS);
            player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH), OWN_LIFE);
        }), 10);
        step("every level from nothing", () -> {
            for (ResourceLocation quest : List.of(JUMP_1, JUMP_2, JUMP_3, LIFE_1, LIFE_2, LIFE_3)) {
                command("rysquests progress @s reset " + quest);
            }
        }, 60);
        expect("the teammate's jumps add to the player's", () -> {
            LOGGER.info("[autotest] stat_team: jumps {} {}, life {}", ClientProgress.get(JUMP_1).completed(), ClientProgress.get(JUMP_2).task(0),
                    ClientProgress.get(LIFE_1).task(0));
            return ClientProgress.get(JUMP_1).completed() && ClientProgress.get(JUMP_2).task(0) == OWN_JUMPS + MATE_JUMPS;
        });
        expect("the longest life is the player's", () -> ClientProgress.get(LIFE_1).task(0) >= OWN_LIFE && ClientProgress.get(LIFE_1).task(0) < OWN_LIFE + 400);

        step("pin both current levels", () -> {
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, LIFE_1, 0, List.of()));
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, JUMP_2, 0, List.of()));
        }, 20);
        expect("both are pinned", () -> ClientProgress.pinned().containsAll(List.of(LIFE_1, JUMP_2)));
        step("the world without the book", () -> mc().setScreen(null), 20);
        expect("two cards on the board", () -> StickerBoard.cardsForTest() == 2);
        screenshot("stat_team_hud");

        step("the player dies, as the game counts it", () -> onServer(player -> player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH))), 60);
        expect("the streak starts again: the teammate's life is the longest now", () -> {
            int life = ClientProgress.get(LIFE_1).task(0);
            LOGGER.info("[autotest] stat_team: life after the death {}", life);
            return life >= MATE_LIFE && life < OWN_LIFE;
        });
        expect("the jumps stay", () -> ClientProgress.get(JUMP_2).task(0) == OWN_JUMPS + MATE_JUMPS);
        screenshot("stat_team_hud_after_death");

        step("unpin and leave the team as it was", () -> {
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, LIFE_1, 0, List.of()));
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, JUMP_2, 0, List.of()));
            onServer(player -> ProgressStore.get(player.server).leaveTeam(MATE));
        }, 20);
    }

    /** The teammate's file in the world's stats folder, as the game writes one; he has never been online in this world. */
    private static void writeMateStats(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(MATE + ".json");
        String json = "{\"stats\":{\"minecraft:custom\":{\"minecraft:jump\":" + MATE_JUMPS + ",\"minecraft:time_since_death\":" + MATE_LIFE
                + "}},\"DataVersion\":3955}";
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not write the teammate's statistics", e);
        }
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
