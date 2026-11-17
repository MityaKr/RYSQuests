package works.repka.rysquests.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.TestBooks;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;

/**
 * A boss trophy opens for the whole team after the first kill of the boss by any member (shop plan, step M3, the author's
 * decision). No new kind of condition is needed: the trade's {@code quest} is the tier of the slayer achievement, a hidden quest
 * whose progress the team shares like any other. What happens when players move between teams is what happens to quest progress.
 */
class BossTrophyTest {
    private static final ResourceLocation TIER = ResourceLocation.parse("t:_achievements/slayer_ignis/gold");
    private static final ResourceLocation IGNIS = ResourceLocation.parse("cataclysm:ignis");
    private static final String SLAYER = """
            {"id":"t:slayer_ignis","category":"boss","icon":"cataclysm:flame_eye","task":{"type":"rysquests:kill","entity":"cataclysm:ignis"},
             "tiers":[{"tier":"gold","amount":1,"rewards":[{"type":"rysquests:currency","id":"coin","amount":10}]}]}""";
    private static final String TROPHY = """
            {"id":"t:trophy_ignitium","category":"trophies","cost":[{"type":"currency","id":"coin","amount":500}],
             "gives":[{"type":"item","id":"cataclysm:ignitium_ingot","count":1}],"quest":"t:_achievements/slayer_ignis/gold"}""";

    private final UUID team = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID killer = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    /** Who is in which team, as a team mod (RYSTeams) says; a player not listed is alone. */
    private final Map<UUID, UUID> teams = new HashMap<>();
    private final ProgressStore store = new ProgressStore();
    private TeamsApi before;
    private QuestBook book;
    private ShopOffer trophy;

    @BeforeEach
    void setUp() {
        before = TeamsApi.get();
        TeamsApi.provide(new TeamsApi() {
            @Override
            public UUID teamOf(UUID player) {
                return teams.getOrDefault(player, player);
            }

            @Override
            public Collection<UUID> members(UUID of) {
                return teams.entrySet().stream().filter(entry -> entry.getValue().equals(of)).map(Map.Entry::getKey).toList();
            }

            @Override
            public boolean can(UUID player, String permission) {
                return true;
            }

            @Override
            public void post(UUID to, Post post) {
            }
        });
        teams.put(killer, team);
        teams.put(friend, team);
        book = TestBooks.book(Map.of(
                ResourceLocation.parse("t:book"), TestBooks.json("{\"progress_mode\":\"team\"}"),
                ResourceLocation.parse("t:achievements/slayer_ignis"), TestBooks.json(SLAYER),
                ResourceLocation.parse("t:shop/trophy_ignitium"), TestBooks.json(TROPHY)));
        trophy = book.offers().get(ResourceLocation.parse("t:trophy_ignitium"));
    }

    @AfterEach
    void tearDown() {
        TeamsApi.provide(before);
    }

    /** The kill as ProgressService records it: the tier of the achievement, done for the killer's team. */
    private void kill(UUID player) {
        store.put(store.teamOf(player, ProgressMode.TEAM), Map.of(TIER, new QuestProgress(List.of(1), 1_000L, List.of())));
    }

    private boolean opens(UUID player) {
        return ShopService.questOpens(store, player, trophy, ProgressMode.TEAM);
    }

    @Test
    void theTrophyWaitsForTheSlayerTierOfItsBoss() {
        assertEquals(Optional.of(IGNIS), trophy.bossToDefeat(book.quests()));
        assertTrue(book.quests().containsKey(TIER), "the achievement makes its hidden tier quest");
        assertFalse(opens(killer));
    }

    @Test
    void theFirstKillOpensItForTheWholeTeam() {
        kill(killer);

        assertTrue(opens(killer));
        assertTrue(opens(friend), "a member who was not there gets it too");
        assertFalse(opens(stranger), "another team does not");
        assertFalse(store.get(killer, TIER).everDone(), "the kill is kept for the team, not for the player");
    }

    @Test
    void itStaysOpenOnceTheAchievementIsClaimed() {
        kill(killer);
        store.put(team, Map.of(TIER, store.get(team, TIER).withClaimed(List.of(0))));

        assertTrue(opens(friend));
    }

    @Test
    void ourOwnTeamCommandsShareItTooWithoutATeamMod() {
        TeamsApi.provide(before);
        store.setTeam(killer, team);
        store.setTeam(friend, team);

        kill(killer);

        assertTrue(opens(friend));
        assertFalse(opens(stranger));
    }

    @Test
    void leavingWithACopyOfTheProgressKeepsTheWin() {
        kill(killer);

        store.copyTeam(team, friend);
        teams.remove(friend);

        assertTrue(opens(friend));
        assertTrue(opens(killer));
    }

    @Test
    void leavingWithoutACopyStartsWithoutIt() {
        kill(killer);

        teams.remove(friend);

        assertFalse(opens(friend));
        assertTrue(opens(killer), "the win stays with the team");
    }

    @Test
    void aWinAloneComesAlongWhenTheTeamTakesProgress() {
        kill(stranger);

        store.mergeInto(stranger, team);
        teams.put(stranger, team);

        assertTrue(opens(friend));
    }

    @Test
    void aWinAloneStaysBehindWhenTheTeamTakesNoProgress() {
        kill(stranger);

        teams.put(stranger, team);

        assertFalse(opens(stranger), "the team has not won");
        teams.remove(stranger);
        assertTrue(opens(stranger), "back alone, the own win is there again");
    }

    @Test
    void mergedTeamsShareTheWin() {
        teams.put(stranger, other);
        kill(stranger);

        store.mergeInto(other, team);
        teams.put(stranger, team);

        assertTrue(opens(killer));
    }

    @Test
    void onlyAKillTierNamesABoss() {
        assertEquals(Optional.empty(), offer(TROPHY.replace("t:_achievements/slayer_ignis/gold", "t:start/first")).bossToDefeat(book.quests()));
        assertEquals(Optional.empty(), offer(TROPHY.replace(",\"quest\":\"t:_achievements/slayer_ignis/gold\"", "")).bossToDefeat(book.quests()));
    }

    @Test
    void lockTextIsReadAndWritten() {
        ShopOffer read = offer(TROPHY.replace("\"category\"", "\"lock_text\":\"shop.t.wait_ignis\",\"category\""));

        assertEquals(Optional.of("shop.t.wait_ignis"), read.lockText());
        assertEquals(read, ShopOffer.CODEC.parse(JsonOps.INSTANCE, ShopOffer.CODEC.encodeStart(JsonOps.INSTANCE, read).getOrThrow()).getOrThrow());
        assertEquals(Optional.empty(), trophy.lockText());
    }

    /** Every slayer achievement of the real RepYS book counts kills of one boss, so a trophy can point at its tier. */
    @Test
    void repysSlayerAchievementsNameTheirBosses() throws IOException {
        String property = System.getProperty("rysquests.repysBook");
        assumeTrue(property != null && Files.isDirectory(Path.of(property, "achievements")), "no RepYS book at " + property);
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("repys:book"), TestBooks.json("{\"progress_mode\":\"team\"}"));
        Map<ResourceLocation, ShopOffer> offers = new HashMap<>();
        try (Stream<Path> paths = Files.list(Path.of(property, "achievements"))) {
            for (Path file : paths.filter(path -> path.getFileName().toString().startsWith("slayer_")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String name = file.getFileName().toString().replace(".json", "");
                files.put(ResourceLocation.parse("repys:achievements/" + name), json);
                String entity = json.getAsJsonObject("task").get("entity").getAsString();
                String tier = json.getAsJsonArray("tiers").get(0).getAsJsonObject().get("tier").getAsString();
                ShopOffer probe = offer(TROPHY.replace("t:_achievements/slayer_ignis/gold", "repys:_achievements/" + name + "/" + tier));
                offers.put(ResourceLocation.parse(entity), probe);
            }
        }
        assumeTrue(!offers.isEmpty(), "no slayer achievements");
        QuestBook repys = TestBooks.book(files);
        offers.forEach((boss, probe) -> assertEquals(Optional.of(boss), probe.bossToDefeat(repys.quests()), probe.quest().toString()));
        assertTrue(offers.containsKey(IGNIS));
    }

    private static ShopOffer offer(String json) {
        return ShopOffer.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
