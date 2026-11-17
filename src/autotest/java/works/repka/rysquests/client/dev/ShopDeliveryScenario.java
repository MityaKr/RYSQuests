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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.network.ShopDeliverPayload;
import works.repka.rysquests.network.ShopTradePayload;
import works.repka.rysquests.progress.DeliveryLedger;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.shop.ShopService;

/**
 * A trade paid in parts (shop plan, step M4; RYSQuests 0.2.7). A world datapack of the test world adds a delivery of 64 iron and 32
 * copper for a nether star and 5 coins. The player, in a team with a member who is away, delivers part of it; leaves, delivers more
 * alone and comes back: the team's count holds what was really brought, no more. The goods are taken for coins once, a second take is
 * refused; the next round is paid from the treasury. {@code gradlew runRepysAutotest -Pautotest=shop_delivery --offline}.
 */
final class ShopDeliveryScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopDeliveryScenario.class);
    private static final ResourceLocation OFFER = ResourceLocation.parse("repys:autotest_delivery");
    private static final String GOAL = OFFER.toString();
    private static final String IRON = "minecraft:iron_ingot";
    private static final String COPPER = "minecraft:copper_ingot";
    private static final ResourceLocation STAR = ResourceLocation.parse("minecraft:nether_star");
    /** The team and its member who is away: fixed, so that a reused world finds the same ones. */
    private static final UUID TEAM = UUID.fromString("7e57a000-0000-4000-8000-00000000004a");
    private static final UUID AWAY = UUID.fromString("7e57a000-0000-4000-8000-00000000004b");
    private static final String OFFER_JSON = """
            {
              "id": "repys:autotest_delivery",
              "category": "Autotest",
              "delivery": true,
              "cost": [
                { "type": "item", "id": "minecraft:iron_ingot", "count": 64 },
                { "type": "item", "id": "minecraft:copper_ingot", "count": 32 },
                { "type": "currency", "id": "coin", "amount": 5 }
              ],
              "gives": [ { "type": "item", "id": "minecraft:nether_star", "count": 1 } ]
            }
            """;
    /** What the server says, read on its own thread and kept for the client's checks. */
    private static volatile String serverView = "";

    private ShopDeliveryScenario() {
    }

    static void check() {
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        step("a datapack with a delivery offer, then reload", () -> {
            writePack();
            command("reload");
        }, 40);
        expect("the offer reached the client as a delivery", () -> ClientBook.current().offers().containsKey(OFFER) && ClientBook.current().offers().get(OFFER).delivery());
        step("a player in a team with a member away, an empty count, coins, iron", () -> {
            BookRole.forceForTest(false);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            onServer(player -> {
                // A run that broke off may have left a count behind: start from nothing.
                ProgressStore.get(player.server).deliveries().clear(TEAM, null);
                ProgressStore.get(player.server).deliveries().clear(player.getUUID(), null);
                joinTeam(player);
            });
            command("clear @s minecraft:iron_ingot");
            command("clear @s minecraft:copper_ingot");
            command("clear @s minecraft:nether_star");
            command("rysquests wallet set @s coin 20");
            command("give @s minecraft:iron_ingot 40");
        }, 40);
        step("ask the server", () -> onServer(ShopDeliveryScenario::look), 10);
        expect("nothing delivered yet", () -> serverView.equals("team=0/0 own=0/0") && held(IRON) == 40);
        step("deliver from the inventory", () -> deliver(), 30);
        step("ask the server", () -> onServer(ShopDeliveryScenario::look), 10);
        expect("40 iron went to the team's count, the client sees them", () -> serverView.equals("team=40/0 own=0/0") && held(IRON) == 0
                && ClientShop.delivered(OFFER).getOrDefault(IRON, 0L) == 40 && "".equals(BookScreen.lastShopResultForTest()));
        step("open the shop on the delivery", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("shop");
            BookScreen.shopSelectForTest(OFFER);
            moveMouse(5, 5);
        }, 30);
        screenshot("shop_delivery_part");
        step("close, leave the team, get more iron and copper", () -> {
            mc().setScreen(null);
            onServer(ShopDeliveryScenario::leaveTeam);
            command("give @s minecraft:iron_ingot 30");
            command("give @s minecraft:copper_ingot 40");
        }, 30);
        step("ask the server", () -> onServer(ShopDeliveryScenario::look), 10);
        expect("leaving took nothing along: the team keeps its 40, the player starts from nothing", () -> serverView.equals("team=40/0 own=0/0"));
        step("deliver alone", () -> deliver(), 30);
        step("ask the server", () -> onServer(ShopDeliveryScenario::look), 10);
        expect("alone the player brought 30 iron and 32 of the 40 copper", () -> serverView.equals("team=40/0 own=30/32") && held(IRON) == 0 && held(COPPER) == 8);
        step("come back to the team", () -> onServer(ShopDeliveryScenario::joinTeam), 20);
        step("ask the server", () -> onServer(ShopDeliveryScenario::look), 10);
        expect("what was brought alone joined the team once: 70 iron, 32 copper", () -> serverView.equals("team=70/32 own=0/0"));
        step("deliver again with the delivery complete", () -> deliver(), 30);
        expect("the server takes nothing more", () -> "rysquests.shop.error.delivery_full".equals(BookScreen.lastShopResultForTest()) && held(COPPER) == 8);
        step("open the shop on the complete delivery", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("shop");
            BookScreen.shopSelectForTest(OFFER);
            moveMouse(5, 5);
        }, 30);
        screenshot("shop_delivery_full");
        step("take the goods", () -> trade(false), 30);
        step("ask the server", () -> onServer(ShopDeliveryScenario::look), 10);
        expect("the star is in the inventory, 5 coins paid, one round taken off, the 6 iron beyond it kept", () -> held(STAR.toString()) == 1
                && ClientShop.balance("coin") == 15 && serverView.equals("team=6/0 own=0/0") && "".equals(BookScreen.lastShopResultForTest()));
        step("take the goods a second time", () -> trade(false), 30);
        expect("the server refuses a second take", () -> "rysquests.shop.error.delivery_short".equals(BookScreen.lastShopResultForTest()) && held(STAR.toString()) == 1);
        step("the next round, paid from the treasury", () -> {
            command("give @s minecraft:iron_ingot 64");
            command("give @s minecraft:copper_ingot 32");
            command("rysquests wallet treasury set @s coin 7");
        }, 20);
        step("deliver", () -> deliver(), 30);
        step("take from the treasury", () -> trade(true), 30);
        step("ask the server", () -> onServer(ShopDeliveryScenario::look), 10);
        expect("a second star, the treasury paid 5, the own wallet kept its 15", () -> held(STAR.toString()) == 2 && ClientShop.treasury("coin") == 2
                && ClientShop.balance("coin") == 15 && serverView.equals("team=0/0 own=0/0") && held(IRON) == 6);
        step("close, reset the count and leave the team", () -> {
            mc().setScreen(null);
            command("rysquests shop delivery reset @s");
            onServer(ShopDeliveryScenario::cleanUp);
        }, 20);
    }

    private static void deliver() {
        BookScreen.clearShopResultForTest();
        PacketDistributor.sendToServer(new ShopDeliverPayload(OFFER));
    }

    private static void trade(boolean treasury) {
        BookScreen.clearShopResultForTest();
        PacketDistributor.sendToServer(new ShopTradePayload(OFFER, 1, treasury));
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

    /** Our own team, as /rysquests team makes it, with one more member who is not online; joining goes through the shop's rule. */
    private static void joinTeam(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        store.setTeam(player.getUUID(), TEAM);
        store.setTeam(AWAY, TEAM);
        ShopService.joinedTeam(player.server, TEAM, player.getUUID());
        resync(player);
    }

    /** As /rysquests team leave does it. */
    private static void leaveTeam(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        store.leaveTeam(player.getUUID());
        ShopService.leftTeam(player.server, TEAM, player.getUUID());
        resync(player);
    }

    private static void resync(ServerPlayer player) {
        ProgressService.invalidateAll();
        player.server.getPlayerList().getPlayers().forEach(ProgressService::sendAll);
        ShopService.send(player);
    }

    private static void look(ServerPlayer player) {
        DeliveryLedger ledger = ProgressStore.get(player.server).deliveries();
        UUID own = player.getUUID();
        serverView = "team=" + ledger.count(TEAM, GOAL, IRON) + "/" + ledger.count(TEAM, GOAL, COPPER)
                + " own=" + ledger.count(own, GOAL, IRON) + "/" + ledger.count(own, GOAL, COPPER);
        LOGGER.info("[autotest] shop_delivery: team of the player {}, {}", ProgressStore.get(player.server).teamOf(own, works.repka.rysquests.data.ProgressMode.TEAM), serverView);
    }

    private static void cleanUp(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        store.deliveries().clear(TEAM, null);
        store.leaveTeam(player.getUUID());
        store.leaveTeam(AWAY);
        resync(player);
    }

    /** Only ever writes into the test world: a datapack of its own, which /reload picks up. */
    private static void writePack() {
        Path packs = mc().getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("rysquests_autotest_delivery");
        try {
            Files.createDirectories(packs.resolve("data/repys/rysquests/shop"));
            Files.writeString(packs.resolve("pack.mcmeta"), "{ \"pack\": { \"pack_format\": 48, \"description\": \"RYSQuests autotest delivery\" } }");
            Files.writeString(packs.resolve("data/repys/rysquests/shop/autotest_delivery.json"), OFFER_JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int held(String item) {
        ResourceLocation id = ResourceLocation.parse(item);
        int count = 0;
        for (ItemStack stack : mc().player.getInventory().items) {
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
