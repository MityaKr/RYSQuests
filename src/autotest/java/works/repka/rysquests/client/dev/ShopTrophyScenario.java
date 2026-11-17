package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.network.ShopTradePayload;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.shop.ShopService;

/**
 * A boss trophy opens for the whole team after the first kill of the boss (shop plan, step M3; RYSQuests 0.2.7). A world datapack
 * of the test world adds a trade whose {@code quest} is the tier of RepYS's own achievement "slayer_ignis". The player is put in a
 * team with a member who is not online, Ignis is struck down by the player's own hit, and the trade opens for both of them, but not
 * for a player of no team. {@code gradlew runRepysAutotest -Pautotest=shop_trophy --offline}, on the copy of RepYS with Cataclysm.
 */
final class ShopTrophyScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopTrophyScenario.class);
    private static final ResourceLocation OFFER = ResourceLocation.parse("repys:autotest_trophy");
    private static final ResourceLocation TIER = ResourceLocation.parse("repys:_achievements/slayer_ignis/gold");
    private static final ResourceLocation IGNIS = ResourceLocation.parse("cataclysm:ignis");
    private static final ResourceLocation INGOT = ResourceLocation.parse("cataclysm:ignitium_ingot");
    /** The team and its member who is away: fixed, so that a reused world finds the same ones. */
    private static final UUID TEAM = UUID.fromString("7e57a000-0000-4000-8000-00000000003a");
    private static final UUID AWAY = UUID.fromString("7e57a000-0000-4000-8000-00000000003b");
    private static final UUID STRANGER = UUID.fromString("7e57a000-0000-4000-8000-00000000003c");
    private static final String OFFER_JSON = """
            {
              "id": "repys:autotest_trophy",
              "category": "Autotest",
              "cost": [ { "type": "currency", "id": "coin", "amount": 5 } ],
              "gives": [ { "type": "item", "id": "cataclysm:ignitium_ingot", "count": 1 } ],
              "quest": "repys:_achievements/slayer_ignis/gold"
            }
            """;
    /** What the server says, read on its own thread and kept for the client's checks. */
    private static volatile String serverView = "";
    private static volatile int ignis = -1;

    private ShopTrophyScenario() {
    }

    static void check() {
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        expect("the slayer tier of Ignis is in the book", () -> ClientBook.current().quests().containsKey(TIER));
        step("a datapack with a trophy offer, then reload", () -> {
            writePack();
            command("reload");
        }, 40);
        expect("the offer reached the client", () -> ClientBook.current().offers().containsKey(OFFER));
        step("a player in a team with a member away, coins, no trophy", () -> {
            BookRole.forceForTest(false);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            onServer(ShopTrophyScenario::joinTeam);
            command("rysquests wallet add @s coin 20");
            command("clear @s cataclysm:ignitium_ingot");
            command("rysquests achievement reset @s repys:slayer_ignis");
        }, 40);
        step("ask the server", () -> onServer(ShopTrophyScenario::look), 10);
        expect("the team has not won yet, so the trade is closed for all", () -> serverView.equals("team=false player=false away=false stranger=false own=false")
                && !ClientProgress.get(TIER).everDone() && heldIngots() == 0);
        expect("the card says whom to defeat", () -> !BookScreen.shopLockForTest(OFFER).isEmpty());
        step("open the shop on the closed offer", () -> {
            LOGGER.info("[autotest] shop_trophy: closed card says '{}'", BookScreen.shopLockForTest(OFFER));
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("shop");
            BookScreen.shopSelectForTest(OFFER);
            moveMouse(5, 5);
        }, 30);
        screenshot("shop_trophy_locked");
        step("try to buy it closed", () -> {
            BookScreen.clearShopResultForTest();
            PacketDistributor.sendToServer(new ShopTradePayload(OFFER, 1, false));
        }, 30);
        expect("the server refuses a closed trophy", () -> "rysquests.shop.error.locked".equals(BookScreen.lastShopResultForTest()) && heldIngots() == 0);
        step("close the book, Ignis appears and falls to the player's hit", () -> {
            mc().setScreen(null);
            onServer(ShopTrophyScenario::strikeIgnis);
        }, 40);
        step("a second blow if the first was turned aside", () -> onServer(ShopTrophyScenario::finishIgnis), 40);
        step("ask the server", () -> onServer(ShopTrophyScenario::look), 10);
        expect("the win is the team's: open for the player and the member away, not for a stranger", () ->
                serverView.equals("team=true player=true away=true stranger=false own=false") && ClientProgress.get(TIER).everDone());
        expect("the card is open", () -> BookScreen.shopLockForTest(OFFER).isEmpty());
        step("open the shop on the open offer", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("shop");
            BookScreen.shopSelectForTest(OFFER);
            moveMouse(5, 5);
        }, 30);
        screenshot("shop_trophy_open");
        step("buy the trophy", () -> {
            BookScreen.clearShopResultForTest();
            PacketDistributor.sendToServer(new ShopTradePayload(OFFER, 1, false));
        }, 30);
        expect("the trophy is in the inventory", () -> heldIngots() >= 1 && "".equals(BookScreen.lastShopResultForTest()));
        step("close, clean up the arena and leave the team", () -> {
            mc().setScreen(null);
            onServer(ShopTrophyScenario::cleanUp);
            command("kill @e[type=item,distance=..32]");
        }, 20);
    }

    private static void onServer(java.util.function.Consumer<ServerPlayer> action) {
        MinecraftServer server = mc().getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(mc().player.getUUID());
            if (player != null) {
                action.accept(player);
            }
        });
    }

    /** Our own team, as /rysquests team makes it, with one more member who is not online. */
    private static void joinTeam(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        store.setTeam(player.getUUID(), TEAM);
        store.setTeam(AWAY, TEAM);
        store.leaveTeam(STRANGER);
        // The player's own record is the one a stranger would have: make sure it holds no win of an earlier run.
        store.put(player.getUUID(), java.util.Map.of(TIER, works.repka.rysquests.progress.QuestProgress.EMPTY));
        store.put(TEAM, java.util.Map.of(TIER, works.repka.rysquests.progress.QuestProgress.EMPTY));
        ProgressService.invalidateAll();
        player.server.getPlayerList().getPlayers().forEach(ProgressService::sendAll);
    }

    private static void look(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        ShopOffer offer = BookHolder.current().book().offers().get(OFFER);
        ProgressMode mode = BookHolder.current().book().meta().progressMode();
        serverView = "team=" + store.get(TEAM, TIER).everDone()
                + " player=" + ShopService.questOpens(store, player.getUUID(), offer, mode)
                + " away=" + ShopService.questOpens(store, AWAY, offer, mode)
                + " stranger=" + ShopService.questOpens(store, STRANGER, offer, mode)
                + " own=" + store.get(player.getUUID(), TIER).everDone();
        LOGGER.info("[autotest] shop_trophy: mode {}, team of the player {}, {}", mode, store.teamOf(player.getUUID(), mode), serverView);
    }

    /** Ignis without its mind, so it neither fights nor changes phase, at its last breath; the player strikes as a sword does. */
    private static void strikeIgnis(ServerPlayer player) {
        Entity entity = BuiltInRegistries.ENTITY_TYPE.get(IGNIS).create(player.serverLevel());
        if (!(entity instanceof Mob boss)) {
            LOGGER.info("[autotest] shop_trophy: no Ignis to summon");
            return;
        }
        boss.moveTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
        boss.setNoAi(true);
        player.serverLevel().addFreshEntity(boss);
        boss.setHealth(1);
        ignis = boss.getId();
        boolean hit = boss.hurt(player.damageSources().playerAttack(player), 10);
        LOGGER.info("[autotest] shop_trophy: the player's hit {}, Ignis {}", hit ? "landed" : "was turned aside", boss.isAlive() ? "alive" : "dead");
    }

    /** Should Ignis have turned the blow aside, the player strikes once more with a blow nothing turns aside, still the player's. */
    private static void finishIgnis(ServerPlayer player) {
        if (player.serverLevel().getEntity(ignis) instanceof LivingEntity boss && boss.isAlive()) {
            boss.hurt(player.damageSources().source(DamageTypes.GENERIC_KILL, player), Float.MAX_VALUE);
            LOGGER.info("[autotest] shop_trophy: second blow, Ignis {}", boss.isAlive() ? "alive" : "dead");
        }
    }

    private static void cleanUp(ServerPlayer player) {
        Entity boss = player.serverLevel().getEntity(ignis);
        if (boss != null) {
            boss.discard();
        }
        ProgressStore store = ProgressStore.get(player.server);
        store.leaveTeam(player.getUUID());
        store.leaveTeam(AWAY);
        ProgressService.invalidateAll();
        player.server.getPlayerList().getPlayers().forEach(ProgressService::sendAll);
    }

    /** Only ever writes into the test world: a datapack of its own, which /reload picks up. */
    private static void writePack() {
        Path packs = mc().getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("rysquests_autotest_trophy");
        try {
            Files.createDirectories(packs.resolve("data/repys/rysquests/shop"));
            Files.writeString(packs.resolve("pack.mcmeta"), "{ \"pack\": { \"pack_format\": 48, \"description\": \"RYSQuests autotest trophy\" } }");
            Files.writeString(packs.resolve("data/repys/rysquests/shop/autotest_trophy.json"), OFFER_JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int heldIngots() {
        int count = 0;
        for (ItemStack stack : mc().player.getInventory().items) {
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(INGOT)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
