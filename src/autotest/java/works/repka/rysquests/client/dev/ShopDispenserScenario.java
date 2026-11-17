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
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.block.ComparatorMode;
import works.repka.rysquests.block.DispenserBlockEntity;
import works.repka.rysquests.block.DispenserMenu;
import works.repka.rysquests.block.DispenserSettings;
import works.repka.rysquests.block.DispenserView;
import works.repka.rysquests.block.ModBlocks;
import works.repka.rysquests.block.ReceiverBlock;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.screen.BlockStopNotice;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.DispenserScreen;
import works.repka.rysquests.network.DispenserConfigPayload;
import works.repka.rysquests.progress.ProgressStore;

/**
 * The dispenser "Выдача" (shop plan, step M7; RYSQuests 0.2.7) on a copy of RepYS. A world datapack adds a purchase: 16 iron for 3
 * coins. The player places the block with a chest under it by command and opens it with a real click, which makes them its owner, and
 * sends the goods down. Then every limit in turn, each checked on the server by the coins, the chest and the lamp: "spend at most 30"
 * buys 10 lots and stops yellow, which brings the one notice of the minute; "keep on the account 61" buys 3 more and stops again with no
 * second notice; "keep at most 240" fills the chest to 240; "only on a signal" waits until a redstone block comes and then spends the
 * rest. Last the own slot: the goods stay in the block, a comparator reads its fill, a pipe takes from it and nothing goes in. The
 * treasury is refused to a player without a team, and a stranger's block does not open. Screenshots of the window, its list of outputs
 * and the notice. {@code gradlew runRepysAutotest -Pautotest=shop_dispenser --offline}.
 */
final class ShopDispenserScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopDispenserScenario.class);
    private static final ResourceLocation BUY = ResourceLocation.parse("repys:autotest_dispenser_buy");
    private static final UUID STRANGER = UUID.fromString("7e57a000-0000-4000-8000-00000000006b");
    private static final String BUY_JSON = """
            {
              "id": "repys:autotest_dispenser_buy",
              "category": "Autotest",
              "cost": [ { "type": "currency", "id": "coin", "amount": 3 } ],
              "gives": [ { "type": "item", "id": "minecraft:iron_ingot", "count": 16 } ]
            }
            """;
    /** Where the block stands: beside the player, found on the server; the chest is under it. */
    private static volatile BlockPos pos = BlockPos.ZERO;
    /** What the server says, read on its own thread and kept for the client's checks. */
    private static volatile String serverView = "";
    private static volatile String pipe = "";
    private static volatile int noticesBefore;

    private ShopDispenserScenario() {
    }

    static void check() {
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        step("a datapack with a purchase, then reload", () -> {
            writePack();
            command("reload");
        }, 40);
        expect("the purchase reached the client", () -> ClientBook.current().offers().containsKey(BUY));
        step("a player alone with 100 coins and an empty hand, the block set beside them with a chest under it", () -> {
            BookRole.forceForTest(false);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            command("rysquests wallet set @s coin 100");
            command("item replace entity @s weapon.mainhand with minecraft:air");
            onServer(player -> {
                pos = player.blockPosition().relative(player.getDirection(), 2);
                player.serverLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
                player.serverLevel().setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
                player.serverLevel().setBlockAndUpdate(pos, ModBlocks.DISPENSER.get().defaultBlockState());
            });
        }, 20);
        step("open it with a click", ShopDispenserScenario::click, 30);
        expect("the window opened and the block is the player's now", () -> mc().screen instanceof DispenserScreen
                && mc().player.getUUID().equals(view().owner()));
        noticesBefore = BlockStopNotice.shownForTest();

        // "Spend at most 30": 10 lots of 3 coins, then the budget stops it, yellow, and the owner hears of it once.
        step("choose the purchase, the goods down into the chest, spend at most 30", () -> configure(s -> s.withOffer(Optional.of(BUY))
                .withOutput(Optional.of(Direction.DOWN)).withBudget(true, 30)), 60);
        step("ask the server", () -> onServer(ShopDispenserScenario::look), 10);
        expect("10 lots bought into the chest, the budget spent, the lamp yellow and the comparator at 10", () -> serverView.startsWith("wallet=70 chest=160 slot=0")
                && serverView.contains("status=BUDGET lamp=yellow signal=10"));
        expect("one notice: the dispenser stopped on its budget", () -> BlockStopNotice.shownForTest() == noticesBefore + 1
                && BlockStopNotice.lastForTest().equals("dispenser BUDGET 0"));
        step("move the mouse away", () -> moveMouse(5, 5), 5);
        screenshot("dispenser_budget");

        // "Keep on the account 61": 3 more lots; a second stop within the minute brings no second notice.
        step("reset the counter, no budget, keep 61 on the account", () -> configure(s -> s.withBudget(false, 30).withReserve(true, 61), true), 60);
        step("ask the server", () -> onServer(ShopDispenserScenario::look), 10);
        expect("3 lots more, 61 coins left, the lamp yellow", () -> serverView.startsWith("wallet=61 chest=208") && serverView.contains("status=RESERVE lamp=yellow"));
        expect("no second notice within the minute", () -> BlockStopNotice.shownForTest() == noticesBefore + 1);

        // "Keep at most 240": the chest fills to 240 and the block waits, which is its aim.
        step("no reserve, keep at most 240 in the chest", () -> configure(s -> s.withReserve(false, 61).withStock(true, 240)), 60);
        step("ask the server", () -> onServer(ShopDispenserScenario::look), 10);
        expect("2 lots more, 240 iron in the chest, the stock made up", () -> serverView.startsWith("wallet=55 chest=240") && serverView.contains("status=STOCKED"));

        // "Only on a signal": dark without one, buys with a redstone block on top.
        step("no stock limit, work only on a redstone signal", () -> configure(s -> s.withStock(false, 240).withRedstone(true)), 60);
        step("ask the server", () -> onServer(ShopDispenserScenario::look), 10);
        expect("nothing bought without a signal, the lamp off", () -> serverView.startsWith("wallet=55 chest=240") && serverView.contains("status=REDSTONE lamp=off signal=0"));
        step("a redstone block on top", () -> command(String.format("setblock %d %d %d minecraft:redstone_block", pos.getX(), pos.getY() + 1, pos.getZ())), 60);
        step("ask the server", () -> onServer(ShopDispenserScenario::look), 10);
        expect("the rest of the coins spent: 18 lots, then short of coins", () -> serverView.startsWith("wallet=1 chest=528") && serverView.contains("status=WAITING"));
        step("the redstone block goes, no more signal needed", () -> {
            command(String.format("setblock %d %d %d minecraft:air", pos.getX(), pos.getY() + 1, pos.getZ()));
            configure(s -> s.withRedstone(false));
        }, 20);

        // The own slot: goods stay in the block, a comparator reads its fill, a pipe takes and puts nothing in.
        step("goods into the own slot, the comparator reads the fill, 30 coins", () -> {
            // The settings go first: the coins must not reach the chest in between.
            configure(s -> s.withOutput(Optional.empty()).withComparator(ComparatorMode.FILL));
            command("rysquests wallet set @s coin 30");
        }, 60);
        step("ask the server", () -> onServer(ShopDispenserScenario::look), 10);
        expect("10 lots in the own slot, the comparator at 4 (two stacks and a half in nine slots)", () -> serverView.startsWith("wallet=0 chest=528 slot=160")
                && serverView.contains("signal=4"));
        step("a pipe takes a stack and tries to put dirt in", () -> onServer(player -> {
            IItemHandler handler = player.serverLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            int taken = handler.extractItem(0, 64, false).getCount();
            int refused = handler.insertItem(1, new ItemStack(Items.DIRT, 5), false).getCount();
            pipe = "taken=" + taken + " refused=" + refused;
            look(player);
        }), 10);
        expect("the pipe took 64 and the dirt stayed out", () -> "taken=64 refused=5".equals(pipe) && serverView.contains("slot=96"));
        step("move the mouse away", () -> moveMouse(5, 5), 5);
        screenshot("dispenser_own_slot");
        step("open the list of outputs", () -> {
            if (mc().screen instanceof DispenserScreen screen) {
                screen.openOutputsForTest();
            }
        }, 5);
        screenshot("dispenser_outputs");

        // The treasury: a player with no team has none, and the server refuses it.
        step("ask for the treasury without a team", () -> configure(s -> s.withTreasury(true)), 20);
        expect("the treasury is refused", () -> view() != null && !view().settings().treasury());

        step("close; the block now belongs to someone else", () -> {
            mc().player.closeContainer();
            onServer(player -> {
                if (player.serverLevel().getBlockEntity(pos) instanceof DispenserBlockEntity entity) {
                    entity.dispenser().setOwner(STRANGER, "Stranger");
                }
            });
        }, 20);
        step("try to open it", ShopDispenserScenario::click, 30);
        expect("a stranger's block does not open", () -> mc().screen == null);
        step("clean up: the block and the chest go, the coins are reset", () -> {
            command(String.format("setblock %d %d %d minecraft:air", pos.getX(), pos.getY(), pos.getZ()));
            command(String.format("setblock %d %d %d minecraft:air", pos.getX(), pos.getY() - 1, pos.getZ()));
            command("kill @e[type=item,distance=..8]");
            command("rysquests wallet set @s coin 0");
        }, 20);
    }

    private static DispenserView view() {
        return mc().screen instanceof DispenserScreen screen ? screen.getMenu().view() : null;
    }

    /** A real use of the block with an empty hand, as a right click sends it. */
    private static void click() {
        BlockPos at = pos;
        mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    private static void configure(UnaryOperator<DispenserSettings> change) {
        configure(change, false);
    }

    /** The window's settings with one change, as the window sends them. */
    private static void configure(UnaryOperator<DispenserSettings> change, boolean reset) {
        if (mc().player.containerMenu instanceof DispenserMenu menu) {
            PacketDistributor.sendToServer(new DispenserConfigPayload(menu.containerId, change.apply(menu.view().settings()), reset));
        }
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
        int chest = 0;
        if (player.serverLevel().getBlockEntity(pos.below()) instanceof ChestBlockEntity box) {
            for (int slot = 0; slot < box.getContainerSize(); slot++) {
                chest += box.getItem(slot).getCount();
            }
        }
        long slot = 0;
        String status = "?";
        String lamp = "?";
        int signal = -1;
        if (player.serverLevel().getBlockEntity(pos) instanceof DispenserBlockEntity entity) {
            for (int i = 0; i < entity.dispenser().slot().getSlots(); i++) {
                slot += entity.dispenser().slot().getStackInSlot(i).getCount();
            }
            status = entity.dispenser().status().name();
            lamp = player.serverLevel().getBlockState(pos).getValue(ReceiverBlock.LAMP).getSerializedName();
            signal = player.serverLevel().getBlockState(pos).getAnalogOutputSignal(player.serverLevel(), pos);
        }
        serverView = "wallet=" + store.balance(player.getUUID(), "coin") + " chest=" + chest + " slot=" + slot + " status=" + status + " lamp=" + lamp
                + " signal=" + signal;
        LOGGER.info("[autotest] shop_dispenser: {}", serverView);
    }

    /** Only ever writes into the test world: a datapack of its own, which /reload picks up. */
    private static void writePack() {
        Path packs = mc().getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("rysquests_autotest_dispenser");
        try {
            Files.createDirectories(packs.resolve("data/repys/rysquests/shop"));
            Files.writeString(packs.resolve("pack.mcmeta"), "{ \"pack\": { \"pack_format\": 48, \"description\": \"RYSQuests autotest dispenser\" } }");
            Files.writeString(packs.resolve("data/repys/rysquests/shop/autotest_dispenser_buy.json"), BUY_JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
