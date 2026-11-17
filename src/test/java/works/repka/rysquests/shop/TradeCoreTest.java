package works.repka.rysquests.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.ShopOffer.Entry;
import works.repka.rysquests.data.ShopOffer.Kind;
import works.repka.rysquests.data.ShopOffer.Limit;
import works.repka.rysquests.data.ShopOffer.Per;
import works.repka.rysquests.data.ShopOffer.Reset;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.ProgressStore.ShopLine;
import works.repka.rysquests.shop.TradeSink.Goods;

/**
 * The trade without a player (shop plan, step M5): an owner who is offline, a treasury spent without the right, no room, a trade made
 * as many times as it can, a delivery, the history's running line and the state sent at most once a second. The sources and sinks are
 * item handlers, as the intake and dispenser blocks will have.
 */
class TradeCoreTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    private static final String COIN = "coin";

    private final ProgressStore store = new ProgressStore();
    private final TradeCore core = new TradeCore(store, ProgressMode.TEAM, DAY);
    private final UUID owner = UUID.randomUUID();
    private final UUID team = UUID.randomUUID();
    private final TeamsApi before = TeamsApi.get();

    @AfterEach
    void restoreTeams() {
        TeamsApi.provide(before);
    }

    /** The owner in a team; the right to spend its treasury only when given. */
    private void inTeam(boolean right) {
        TeamsApi.provide(new TeamsApi() {
            @Override
            public UUID teamOf(UUID player) {
                return player.equals(owner) ? team : player;
            }

            @Override
            public Collection<UUID> members(UUID of) {
                return of.equals(team) ? List.of(owner) : List.of(of);
            }

            @Override
            public boolean can(UUID player, String permission) {
                return right && Set.of(owner).contains(player);
            }

            @Override
            public void post(UUID of, Post post) {
            }
        });
    }

    private static ShopOffer offer(String id, List<Entry> cost, List<Entry> gives, Optional<Limit> limit, Optional<String> stage, boolean delivery) {
        return new ShopOffer(ResourceLocation.parse(id), Optional.empty(), Optional.empty(), Optional.empty(), cost, gives, limit, stage,
                Optional.empty(), Optional.empty(), Optional.empty(), delivery);
    }

    /** 16 wheat for 4 coins. */
    private static ShopOffer buyWheat() {
        return offer("t:buy", List.of(coins(4)), List.of(item("minecraft:wheat", 16)), Optional.empty(), Optional.empty(), false);
    }

    /** 16 wheat sold for 2 coins. */
    private static ShopOffer sellWheat() {
        return offer("t:sell", List.of(item("minecraft:wheat", 16)), List.of(coins(2)), Optional.empty(), Optional.empty(), false);
    }

    private static Entry coins(int amount) {
        return new Entry(Kind.CURRENCY, COIN, amount);
    }

    private static Entry item(String id, int count) {
        return new Entry(Kind.ITEM, id, count);
    }

    /** A block whose owner is not in the world. */
    private Trader offline(boolean treasury) {
        return Trader.owner(owner, null, treasury);
    }

    private static long held(ItemStackHandler handler) {
        long sum = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            sum += handler.getStackInSlot(slot).getCount();
        }
        return sum;
    }

    private static ItemStackHandler filled(int slots, ItemStack stack) {
        ItemStackHandler handler = new ItemStackHandler(slots);
        for (int slot = 0; slot < slots; slot++) {
            handler.setStackInSlot(slot, stack.copy());
        }
        return handler;
    }

    @Test
    void anOfflineOwnerBuysAsManyTimesAsTheMoneyAllowsWithNoStackCeiling() {
        store.addBalance(owner, COIN, 1000);
        ItemStackHandler chest = new ItemStackHandler(100);

        TradeCore.Outcome outcome = core.trade(buyWheat(), offline(false), Integer.MAX_VALUE, true, TradeSource.NONE, TradeSink.of(chest), 1);

        assertTrue(outcome.ok());
        // 1000 coins buy 250 rounds of 16: far more than the 64 a player may ask for at once, and the chest of 100 slots holds them.
        assertEquals(250, outcome.times());
        assertEquals(0, store.balance(owner, COIN));
        assertEquals(4000, held(chest));
        assertTrue(outcome.leftover().isEmpty());
    }

    @Test
    void aPlayerStillTradesAStackAtMostAndExactlyWhatTheyAsked() {
        store.addBalance(owner, COIN, 1000);
        Trader player = new Trader(owner, false, stage -> false, Trader.PLAYER_MAX_TIMES, false);
        ItemStackHandler chest = new ItemStackHandler(100);

        assertEquals("rysquests.shop.error.unknown", core.check(buyWheat(), player, 65, false, TradeSource.NONE, TradeSink.of(chest)).problem());
        // Not enough for 300 rounds: a player is refused rather than given fewer.
        store.addBalance(owner, COIN, -1000 + 40);
        assertEquals("rysquests.shop.error.not_enough", core.check(buyWheat(), player, 11, false, TradeSource.NONE, TradeSink.of(chest)).problem());
        assertEquals(10, core.check(buyWheat(), player, 10, false, TradeSource.NONE, TradeSink.of(chest)).plan().times());
    }

    @Test
    void noRoomTakesNoMoneyAndPartRoomMakesPartOfTheTrade() {
        store.addBalance(owner, COIN, 100);
        ItemStackHandler full = filled(3, new ItemStack(Items.STONE, 64));

        TradeCore.Outcome refused = core.trade(buyWheat(), offline(false), 10, true, TradeSource.NONE, TradeSink.of(full), 1);
        assertEquals("rysquests.shop.error.no_space", refused.problem());
        assertEquals(100, store.balance(owner, COIN));

        // Two free slots and 40 wheat on top of a stack of 24: 2 * 64 + 40 = 168 wheat, so ten rounds of 16 fit, not eleven.
        ItemStackHandler chest = new ItemStackHandler(3);
        chest.setStackInSlot(0, new ItemStack(Items.WHEAT, 24));
        Trader exact = new Trader(owner, false, stage -> false, Integer.MAX_VALUE, true);
        assertEquals("rysquests.shop.error.no_space", core.check(buyWheat(), exact, 11, false, TradeSource.NONE, TradeSink.of(chest)).problem());

        TradeCore.Outcome part = core.trade(buyWheat(), offline(false), 25, true, TradeSource.NONE, TradeSink.of(chest), 1);
        assertTrue(part.ok());
        assertEquals(10, part.times());
        assertEquals(60, store.balance(owner, COIN));
        assertEquals(24 + 160, held(chest));
    }

    @Test
    void severalGoodsNeverCountOnTheSameSlot() {
        store.addBalance(owner, COIN, 100);
        ShopOffer two = offer("t:two", List.of(coins(1)), List.of(item("minecraft:wheat", 64), item("minecraft:carrot", 64)), Optional.empty(), Optional.empty(), false);

        // One free slot: either stack would fit alone, both together do not.
        assertFalse(TradeSink.of(new ItemStackHandler(1)).fits(TradeCore.goods(two, 1)));
        assertTrue(TradeSink.of(new ItemStackHandler(2)).fits(TradeCore.goods(two, 1)));
        assertEquals("rysquests.shop.error.no_space", core.trade(two, offline(false), 1, true, TradeSource.NONE, TradeSink.of(new ItemStackHandler(1)), 1).problem());
    }

    @Test
    void whatTheSinkRefusesAfterAllComesBackToTheCaller() {
        store.addBalance(owner, COIN, 100);
        TradeSink fickle = new TradeSink() {
            @Override
            public boolean fits(List<Goods> goods) {
                return true;
            }

            @Override
            public List<Goods> put(List<Goods> goods) {
                return goods;
            }
        };

        TradeCore.Outcome outcome = core.trade(buyWheat(), offline(false), 2, true, TradeSource.NONE, fickle, 1);

        // Paid, and the goods are handed back to be kept, never lost.
        assertTrue(outcome.ok());
        assertEquals(92, store.balance(owner, COIN));
        assertEquals(1, outcome.leftover().size());
        assertEquals(32, outcome.leftover().getFirst().count());
        assertTrue(outcome.leftover().getFirst().template().is(Items.WHEAT));
    }

    @Test
    void theTreasuryIsSpentOnlyWithTheOwnersRightAskedAtEveryTrade() {
        ItemStackHandler chest = new ItemStackHandler(9);

        assertEquals("rysquests.shop.error.no_team", core.trade(buyWheat(), offline(true), 1, true, TradeSource.NONE, TradeSink.of(chest), 1).problem());

        inTeam(false);
        store.addBalance(team, COIN, 40);
        assertEquals("rysquests.shop.error.no_right", core.trade(buyWheat(), offline(true), 1, true, TradeSource.NONE, TradeSink.of(chest), 1).problem());
        assertEquals(40, store.balance(team, COIN));
        // Selling into the treasury needs no right.
        ItemStackHandler buffer = filled(1, new ItemStack(Items.WHEAT, 32));
        assertTrue(core.trade(sellWheat(), offline(true), Integer.MAX_VALUE, true, TradeSource.of(buffer), TradeSink.NONE, 1).ok());
        assertEquals(44, store.balance(team, COIN));

        inTeam(true);
        TradeCore.Outcome bought = core.trade(buyWheat(), offline(true), 100, true, TradeSource.NONE, TradeSink.of(chest), 1);
        assertEquals(11, bought.times());
        assertEquals(0, store.balance(team, COIN));
        assertEquals(0, store.balance(owner, COIN));
    }

    @Test
    void aTradeBehindAStageIsClosedWhileTheOwnerIsAway() {
        store.addBalance(owner, COIN, 100);
        ShopOffer staged = offer("t:staged", List.of(coins(1)), List.of(item("minecraft:wheat", 1)), Optional.empty(), Optional.of("iron_age"), false);

        assertEquals("rysquests.shop.error.locked", core.trade(staged, offline(false), 1, true, TradeSource.NONE, TradeSink.of(new ItemStackHandler(1)), 1).problem());
        Trader there = new Trader(owner, false, "iron_age"::equals, Integer.MAX_VALUE, true);
        assertTrue(core.trade(staged, there, 1, true, TradeSource.NONE, TradeSink.of(new ItemStackHandler(1)), 1).ok());
    }

    @Test
    void aLimitCapsABlockAndRefusesAPlayerWhoAsksForMore() {
        store.addBalance(owner, COIN, 100);
        ShopOffer limited = offer("t:limited", List.of(coins(1)), List.of(item("minecraft:wheat", 1)), Optional.of(new Limit(Per.PLAYER, 3, Reset.NEVER)), Optional.empty(), false);
        Trader player = new Trader(owner, false, stage -> false, Trader.PLAYER_MAX_TIMES, false);

        assertEquals("rysquests.shop.error.limit", core.check(limited, player, 4, false, TradeSource.NONE, TradeSink.of(new ItemStackHandler(1))).problem());
        assertEquals(3, core.trade(limited, offline(false), 10, true, TradeSource.NONE, TradeSink.of(new ItemStackHandler(1)), 1).times());
        assertEquals("rysquests.shop.error.limit", core.trade(limited, offline(false), 10, true, TradeSource.NONE, TradeSink.of(new ItemStackHandler(1)), 1).problem());
    }

    @Test
    void aSaleFromABufferSellsWholeLotsAndLeavesTheRest() {
        ItemStackHandler buffer = new ItemStackHandler(2);
        buffer.setStackInSlot(0, new ItemStack(Items.WHEAT, 64));
        buffer.setStackInSlot(1, new ItemStack(Items.WHEAT, 36));

        TradeCore.Outcome sold = core.trade(sellWheat(), offline(false), Integer.MAX_VALUE, true, TradeSource.of(buffer), TradeSink.NONE, 1);

        assertEquals(6, sold.times());
        assertEquals(12, store.balance(owner, COIN));
        assertEquals(4, held(buffer));
        assertEquals("rysquests.shop.error.not_enough", core.trade(sellWheat(), offline(false), Integer.MAX_VALUE, true, TradeSource.of(buffer), TradeSink.NONE, 2).problem());
    }

    @Test
    void anEnchantedOrRenamedStackNeverPays() {
        ItemStack named = new ItemStack(Items.WHEAT, 64);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Gold"));
        ItemStackHandler buffer = filled(1, named);

        assertEquals(0, TradeSource.of(buffer).count(Items.WHEAT));
        assertEquals("rysquests.shop.error.not_enough", core.trade(sellWheat(), offline(false), Integer.MAX_VALUE, true, TradeSource.of(buffer), TradeSink.NONE, 1).problem());
    }

    @Test
    void aBlocksTradesAddUpInOneLineUntilSomethingElseHappens() {
        ItemStackHandler buffer = filled(4, new ItemStack(Items.WHEAT, 64));
        store.addBalance(owner, COIN, 100);

        core.trade(sellWheat(), offline(false), 4, true, TradeSource.of(buffer), TradeSink.NONE, 1);
        core.trade(sellWheat(), offline(false), 4, true, TradeSource.of(buffer), TradeSink.NONE, 2);
        List<ShopLine> lines = store.shopHistory(owner);
        assertEquals(1, lines.size());
        assertEquals(16, lines.getFirst().delta());
        assertEquals(8, lines.getFirst().times());
        assertEquals(2, lines.getFirst().time());

        // Another trade of a block has its own line; the first one comes back to the top when it trades again.
        core.trade(buyWheat(), offline(false), 1, true, TradeSource.NONE, TradeSink.of(new ItemStackHandler(1)), 3);
        core.trade(sellWheat(), offline(false), 1, true, TradeSource.of(buffer), TradeSink.NONE, 4);
        lines = store.shopHistory(owner);
        assertEquals(2, lines.size());
        assertEquals("t:sell", lines.getFirst().ref());
        assertEquals(18, lines.getFirst().delta());
        assertEquals(-4, lines.get(1).delta());

        // Anything else written to the history closes the running lines: the next trade starts a new one.
        store.addShopHistory(owner, new ShopLine(5, 7, COIN, ShopLine.TRANSFER, 0));
        core.trade(sellWheat(), offline(false), 1, true, TradeSource.of(buffer), TradeSink.NONE, 6);
        lines = store.shopHistory(owner);
        assertEquals(4, lines.size());
        assertEquals(2, lines.getFirst().delta());
    }

    @Test
    void aPlayersTradesStillWriteALineEach() {
        store.addBalance(owner, COIN, 100);
        Trader player = new Trader(owner, false, stage -> false, Trader.PLAYER_MAX_TIMES, false);
        List<ItemStack> inventory = new ArrayList<>(List.of(ItemStack.EMPTY, ItemStack.EMPTY));

        core.trade(buyWheat(), player, 1, false, TradeSource.of(inventory, () -> { }), TradeSink.player(inventory, stack -> inventory.set(inventory.indexOf(ItemStack.EMPTY), stack)), 1);
        core.trade(buyWheat(), player, 1, false, TradeSource.of(inventory, () -> { }), TradeSink.player(inventory, stack -> inventory.set(inventory.indexOf(ItemStack.EMPTY), stack)), 2);

        assertEquals(2, store.shopHistory(owner).size());
        assertEquals(92, store.balance(owner, COIN));
    }

    @Test
    void aDeliveryGoesThroughTheSamePathForABlock() {
        inTeam(true);
        ShopOffer trophy = offer("t:trophy", List.of(item("minecraft:wheat", 100), item("minecraft:carrot", 10), coins(5)),
                List.of(item("minecraft:nether_star", 1)), Optional.empty(), Optional.empty(), true);
        ItemStackHandler buffer = new ItemStackHandler(3);
        buffer.setStackInSlot(0, new ItemStack(Items.WHEAT, 64));
        buffer.setStackInSlot(1, new ItemStack(Items.WHEAT, 64));
        buffer.setStackInSlot(2, new ItemStack(Items.CARROT, 4));

        // A pipe asking whether it may insert: what would go in, and nothing taken.
        TradeCore.Delivered asked = core.deliver(trophy, offline(false), TradeSource.of(buffer), true);
        assertEquals(Map.of("minecraft:wheat", 100L, "minecraft:carrot", 4L), asked.taken());
        assertEquals(132, held(buffer));
        assertEquals(0, store.deliveries().count(team, "t:trophy", "minecraft:wheat"));

        TradeCore.Delivered given = core.deliver(trophy, offline(false), TradeSource.of(buffer), false);
        assertTrue(given.ok());
        // Only what each row lacks: the team's count, not the owner's own.
        assertEquals(100, store.deliveries().count(team, "t:trophy", "minecraft:wheat"));
        assertEquals(4, store.deliveries().count(team, "t:trophy", "minecraft:carrot"));
        assertEquals(28, held(buffer));
        assertEquals("rysquests.shop.error.nothing_to_deliver", core.deliver(trophy, offline(false), TradeSource.of(buffer), false).problem());

        ItemStackHandler chest = new ItemStackHandler(1);
        store.addBalance(owner, COIN, 50);
        assertEquals("rysquests.shop.error.delivery_short", core.trade(trophy, offline(false), 1, true, TradeSource.NONE, TradeSink.of(chest), 1).problem());
        store.deliveries().add(team, "t:trophy", "minecraft:carrot", 6);
        assertEquals("rysquests.shop.error.delivery_full", core.deliver(trophy, offline(false), TradeSource.of(buffer), false).problem());

        // Taken by the block: one round, the coins from the owner's wallet, whatever was asked.
        TradeCore.Outcome taken = core.trade(trophy, offline(false), 64, true, TradeSource.NONE, TradeSink.of(chest), 2);
        assertTrue(taken.ok());
        assertEquals(1, taken.times());
        assertEquals(45, store.balance(owner, COIN));
        assertTrue(chest.getStackInSlot(0).is(Items.NETHER_STAR));
        assertEquals(0, store.deliveries().count(team, "t:trophy", "minecraft:wheat"));
        assertEquals("rysquests.shop.error.delivery_short", core.trade(trophy, offline(false), 1, true, TradeSource.NONE, TradeSink.of(chest), 3).problem());
    }

    @Test
    void theStateOfATeamIsSentAtMostOnceASecond() {
        ShopBroadcast broadcast = new ShopBroadcast();
        UUID other = UUID.randomUUID();

        assertTrue(broadcast.due(0).isEmpty());
        broadcast.mark(team);
        assertEquals(List.of(team), broadcast.due(1));
        broadcast.mark(team);
        broadcast.mark(other);
        // The other team was not sent yet; this one waits for its second.
        assertEquals(List.of(other), broadcast.due(5));
        assertTrue(broadcast.due(20).isEmpty());
        assertEquals(List.of(team), broadcast.due(21));
        assertTrue(broadcast.due(22).isEmpty());
    }
}
