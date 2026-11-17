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
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.block.ComparatorMode;
import works.repka.rysquests.block.ReceiverBlock;
import works.repka.rysquests.block.ReceiverBlockEntity;
import works.repka.rysquests.block.ReceiverMenu;
import works.repka.rysquests.block.ReceiverPrograms;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.ReceiverScreen;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.network.ReceiverConfigPayload;
import works.repka.rysquests.progress.ProgressStore;

/**
 * The intake block "Приёмщик" (shop plan, step M6; RYSQuests 0.2.7) on a copy of RepYS. A world datapack adds a sale (16 iron for 3
 * coins) and a delivery (64 iron and 32 copper, then 5 coins for a nether star). The player places the block by command and opens it
 * with a real click, which makes them its owner; chooses the sale; items go in through the block's item handler and through a vanilla
 * hopper on top, a foreign item stays in the hopper; whole lots are sold a second later. Then the delivery: only what is missing goes
 * in, and the lamp turns yellow once it is full. A block of another owner does not open. Screenshots of the window in both modes.
 * {@code gradlew runRepysAutotest -Pautotest=shop_receiver --offline}.
 */
final class ShopReceiverScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopReceiverScenario.class);
    private static final ResourceLocation SALE = ResourceLocation.parse("repys:autotest_receiver_sale");
    private static final ResourceLocation DELIVERY = ResourceLocation.parse("repys:autotest_receiver_delivery");
    private static final UUID STRANGER = UUID.fromString("7e57a000-0000-4000-8000-00000000006a");
    private static final String SALE_JSON = """
            {
              "id": "repys:autotest_receiver_sale",
              "category": "Autotest",
              "cost": [ { "type": "item", "id": "minecraft:iron_ingot", "count": 16 } ],
              "gives": [ { "type": "currency", "id": "coin", "amount": 3 } ]
            }
            """;
    private static final String DELIVERY_JSON = """
            {
              "id": "repys:autotest_receiver_delivery",
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
    /** Where the block stands: beside the player, found on the server. */
    private static volatile BlockPos pos = BlockPos.ZERO;
    /** What the server says, read on its own thread and kept for the client's checks. */
    private static volatile String serverView = "";
    /** The book before the reload: a world kept from an earlier run already has the offers. */
    private static volatile works.repka.rysquests.data.QuestBook before;
    private static volatile boolean asking;
    private static volatile int rest = -1;

    private ShopReceiverScenario() {
    }

    static void check() {
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        step("a datapack with a sale and a delivery, then reload", () -> {
            before = ClientBook.current();
            writePack();
            command("reload");
        }, 40);
        expect("both offers reached the client", () -> ClientBook.current() != before && ClientBook.current().offers().containsKey(SALE) && ClientBook.current().offers().containsKey(DELIVERY));
        step("a player alone with no coins and an empty hand, the block set beside them", () -> {
            BookRole.forceForTest(false);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            command("rysquests wallet set @s coin 0");
            command("item replace entity @s weapon.mainhand with minecraft:air");
            command("kill @e[type=item,distance=..8]");
            onServer(player -> {
                pos = player.blockPosition().relative(player.getDirection(), 2);
                ProgressStore.get(player.server).deliveries().clear(player.getUUID(), null);
                player.serverLevel().setBlockAndUpdate(pos.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                // The test world is kept between runs: clear the old block, or its buffer and owner stay.
                player.serverLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                player.serverLevel().setBlockAndUpdate(pos, works.repka.rysquests.block.ModBlocks.RECEIVER.get().defaultBlockState());
            });
        }, 20);
        // After a reload the server thread stays busy for seconds on a large pack: wait for the block, not for a fixed time.
        expect("the block reached the client", () -> !BlockPos.ZERO.equals(pos)
                && mc().level.getBlockState(pos).is(works.repka.rysquests.block.ModBlocks.RECEIVER.get()));
        step("open it with a click", ShopReceiverScenario::click, 30);
        expect("the window opened and the block is the player's now", () -> mc().screen instanceof ReceiverScreen
                && mc().player.getUUID().equals(((ReceiverScreen) mc().screen).getMenu().view().owner()));
        step("choose the sale, coins to the own wallet", () -> configure(ReceiverPrograms.SELL.id(), SALE), 20);
        expect("the sale is chosen and the lamp is green", () -> view().offer().filter(SALE::equals).isPresent() && "WORKING".equals(view().status().name()));
        step("move the mouse away", () -> moveMouse(5, 5), 5);
        screenshot("receiver_sell_empty");
        step("40 iron and 5 dirt through the item handler", () -> onServer(player -> {
            IItemHandler handler = handler(player);
            int dirt = handler.insertItem(0, new ItemStack(Items.DIRT, 5), false).getCount();
            int simulated = handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 40), true).getCount();
            rest = dirt * 1000 + simulated * 100 + handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 40), false).getCount();
        }), 40);
        step("ask the server", () -> onServer(ShopReceiverScenario::look), 10);
        expect("dirt refused, iron taken, two lots sold a second later, 8 iron left in the buffer", () -> ask() && rest == 5000
                && serverView.startsWith("wallet=6 buffer=8 lamp=green"));
        step("a hopper on top with 16 iron and a dirt", () -> command(String.format("setblock %d %d %d minecraft:hopper[facing=down]{Items:[{Slot:0b,id:\"minecraft:iron_ingot\",count:16},{Slot:1b,id:\"minecraft:dirt\",count:1}]}",
                pos.getX(), pos.getY() + 1, pos.getZ())), 200);
        step("ask the server", () -> onServer(ShopReceiverScenario::look), 10);
        expect("the hopper gave its iron, one more lot sold, the dirt stays in the hopper", () -> ask() && serverView.startsWith("wallet=9 buffer=8 lamp=green") && serverView.endsWith("hopper=1"));
        screenshot("receiver_sell");
        step("the hopper goes, so that nothing it picks up reaches the delivery", () -> command(String.format("setblock %d %d %d minecraft:air", pos.getX(), pos.getY() + 1, pos.getZ())), 10);
        step("switch to the delivery: the 8 iron of the sale drop on top", () -> configure(ReceiverPrograms.DELIVERY.id(), DELIVERY), 20);
        step("80 iron and 40 copper through the item handler", () -> onServer(player -> {
            IItemHandler handler = handler(player);
            rest = handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false).getCount() * 1000
                    + handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 16), false).getCount() * 100
                    + handler.insertItem(0, new ItemStack(Items.COPPER_INGOT, 40), false).getCount();
        }), 40);
        step("ask the server", () -> onServer(ShopReceiverScenario::look), 10);
        expect("only what was missing went in, the delivery is full and the lamp yellow", () -> ask() && rest == 1608
                && serverView.contains("delivered=64/32") && serverView.contains("lamp=yellow"));
        step("move the mouse away", () -> moveMouse(5, 5), 5);
        screenshot("receiver_delivery");
        step("close; the block now belongs to someone else", () -> {
            mc().player.closeContainer();
            onServer(player -> {
                if (player.serverLevel().getBlockEntity(pos) instanceof ReceiverBlockEntity entity) {
                    entity.receiver().setOwner(STRANGER, "Stranger");
                }
            });
        }, 20);
        step("try to open it", ShopReceiverScenario::click, 30);
        expect("a stranger's block does not open", () -> mc().screen == null);
        step("clean up: the block and the hopper go, the count is reset", () -> {
            command(String.format("setblock %d %d %d minecraft:air", pos.getX(), pos.getY() + 1, pos.getZ()));
            command(String.format("setblock %d %d %d minecraft:air", pos.getX(), pos.getY(), pos.getZ()));
            command("kill @e[type=item,distance=..8]");
            command("rysquests shop delivery reset @s");
            command("rysquests wallet set @s coin 0");
        }, 20);
    }

    private static works.repka.rysquests.block.ReceiverView view() {
        return mc().screen instanceof ReceiverScreen screen ? screen.getMenu().view() : null;
    }

    /** A real use of the block with an empty hand, as a right click sends it. */
    private static void click() {
        BlockPos at = pos;
        mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    private static void configure(ResourceLocation program, ResourceLocation offer) {
        if (mc().player.containerMenu instanceof ReceiverMenu menu) {
            PacketDistributor.sendToServer(new ReceiverConfigPayload(menu.containerId, program, Optional.of(offer), false, false, ComparatorMode.STATE, true));
        }
    }

    private static IItemHandler handler(ServerPlayer player) {
        return player.serverLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    }

    /** Asks the server again unless a question is on its way: a busy server answers late, a fixed wait reads an old view. */
    private static boolean ask() {
        if (!asking) {
            asking = true;
            MinecraftServer server = mc().getSingleplayerServer();
            server.execute(() -> {
                try {
                    ServerPlayer player = server.getPlayerList().getPlayer(mc().player.getUUID());
                    if (player != null) {
                        look(player);
                    }
                } finally {
                    asking = false;
                }
            });
        }
        return true;
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

    private static void look(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        UUID own = player.getUUID();
        String buffer = "?";
        String lamp = "?";
        if (player.serverLevel().getBlockEntity(pos) instanceof ReceiverBlockEntity entity) {
            buffer = Long.toString(entity.receiver().buffered(Items.IRON_INGOT));
            lamp = player.serverLevel().getBlockState(pos).getValue(ReceiverBlock.LAMP).getSerializedName();
        }
        UUID team = store.teamOf(own, ProgressMode.TEAM);
        String goal = DELIVERY.toString();
        int hopper = 0;
        if (player.serverLevel().getBlockEntity(pos.above()) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity chest) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                hopper += chest.getItem(slot).getCount();
            }
        }
        serverView = "wallet=" + store.balance(own, "coin") + " buffer=" + buffer + " lamp=" + lamp
                + " delivered=" + store.deliveries().count(team, goal, "minecraft:iron_ingot") + "/" + store.deliveries().count(team, goal, "minecraft:copper_ingot")
                + " hopper=" + hopper;
        LOGGER.info("[autotest] shop_receiver: {}", serverView);
    }

    /** Only ever writes into the test world: a datapack of its own, which /reload picks up. */
    private static void writePack() {
        Path packs = mc().getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("rysquests_autotest_receiver");
        try {
            Files.createDirectories(packs.resolve("data/repys/rysquests/shop"));
            Files.writeString(packs.resolve("pack.mcmeta"), "{ \"pack\": { \"pack_format\": 48, \"description\": \"RYSQuests autotest receiver\" } }");
            Files.writeString(packs.resolve("data/repys/rysquests/shop/autotest_receiver_sale.json"), SALE_JSON);
            Files.writeString(packs.resolve("data/repys/rysquests/shop/autotest_receiver_delivery.json"), DELIVERY_JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
