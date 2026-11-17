package works.repka.rysquests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.ShopOffer.Entry;
import works.repka.rysquests.data.ShopOffer.Kind;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.shop.TradeCore;
import works.repka.rysquests.shop.TradeSink;
import works.repka.rysquests.shop.TradeSource;
import works.repka.rysquests.shop.Trader;

/**
 * The dispenser without a world (shop plan, step M7): purchases into the own slot and into a neighbour, the three limits ("spend at
 * most", "keep on the account", "keep at most"), the treasury with and without the right, no room, the redstone switch, the leftovers,
 * the comparator, the pipes that only take, and what the block saves.
 */
class DispenserTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    private static final String COIN = "coin";

    private final ProgressStore store = new ProgressStore();
    private final TradeCore core = new TradeCore(store, ProgressMode.TEAM, DAY);
    private final Map<ResourceLocation, ShopOffer> offers = new HashMap<>();
    private final UUID owner = UUID.randomUUID();
    private final UUID mate = UUID.randomUUID();
    private final UUID team = UUID.randomUUID();
    private final TeamsApi before = TeamsApi.get();

    /** The shop over a bare progress store, as {@link ReceiverMarket#of} is over the server's. */
    private final ReceiverMarket market = new ReceiverMarket() {
        @Override
        public @Nullable ShopOffer offer(ResourceLocation id) {
            return offers.get(id);
        }

        @Override
        public TradeCore.Outcome sell(UUID who, ShopOffer offer, int times, boolean treasury, TradeSource from) {
            return core.trade(offer, Trader.owner(who, null, treasury), times, true, from, TradeSink.NONE, 1);
        }

        @Override
        public @Nullable String check(UUID who, ShopOffer offer, boolean treasury, TradeSource from) {
            return core.check(offer, Trader.owner(who, null, treasury), 1, true, from, TradeSink.NONE).problem();
        }

        @Override
        public TradeCore.Outcome buy(UUID who, ShopOffer offer, int times, boolean treasury, TradeSink to) {
            return core.trade(offer, Trader.owner(who, null, treasury), times, true, TradeSource.NONE, to, 1);
        }

        @Override
        public @Nullable String checkBuy(UUID who, ShopOffer offer, boolean treasury, TradeSink to) {
            return core.check(offer, Trader.owner(who, null, treasury), 1, true, TradeSource.NONE, to).problem();
        }

        @Override
        public TradeCore.Delivered deliver(UUID who, ShopOffer offer, TradeSource from, boolean simulate) {
            return core.deliver(offer, Trader.owner(who, null, false), from, simulate);
        }

        @Override
        public UUID team(UUID player) {
            return store.teamOf(player, ProgressMode.TEAM);
        }

        @Override
        public long balance(UUID who, boolean treasury, String currency) {
            return store.balance(treasury ? team(who) : who, currency);
        }
    };

    @AfterEach
    void restoreTeams() {
        TeamsApi.provide(before);
    }

    /** The owner and a mate in one team; the right to spend its treasury only when given. */
    private void inTeam(boolean right) {
        TeamsApi.provide(new TeamsApi() {
            @Override
            public UUID teamOf(UUID player) {
                return player.equals(owner) || player.equals(mate) ? team : player;
            }

            @Override
            public Collection<UUID> members(UUID of) {
                return of.equals(team) ? List.of(owner, mate) : List.of(of);
            }

            @Override
            public boolean can(UUID player, String permission) {
                return right && player.equals(owner);
            }

            @Override
            public void post(UUID of, Post post) {
            }
        });
    }

    private static ShopOffer offer(String id, List<Entry> cost, List<Entry> gives) {
        return new ShopOffer(ResourceLocation.parse(id), Optional.empty(), Optional.empty(), Optional.empty(), cost, gives, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    private static Entry coins(int amount) {
        return new Entry(Kind.CURRENCY, COIN, amount);
    }

    private static Entry item(String id, int count) {
        return new Entry(Kind.ITEM, id, count);
    }

    /** 16 wheat for 4 coins, in the book. */
    private ShopOffer buyWheat() {
        ShopOffer offer = offer("t:buy", List.of(coins(4)), List.of(item("minecraft:wheat", 16)));
        offers.put(offer.id(), offer);
        return offer;
    }

    private Dispenser dispenser(DispenserSettings settings) {
        Dispenser dispenser = new Dispenser();
        dispenser.setOwner(owner, "Owner");
        dispenser.configure(settings.withOffer(Optional.of(buyWheat().id())));
        return dispenser;
    }

    private static DispenserSettings toSide() {
        return DispenserSettings.DEFAULT.withOutput(Optional.of(Direction.DOWN));
    }

    private static long count(ItemStackHandler handler) {
        long sum = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            sum += handler.getStackInSlot(slot).getCount();
        }
        return sum;
    }

    @Test
    void onlyTradesPaidWithMoneyThatGiveItemsCanBeBought() {
        assertTrue(Dispenser.fits(buyWheat()));
        assertFalse(Dispenser.fits(offer("t:sell", List.of(item("minecraft:wheat", 16)), List.of(coins(2)))));
        assertFalse(Dispenser.fits(offer("t:swap", List.of(item("minecraft:wheat", 16), coins(1)), List.of(item("minecraft:bread", 1)))));
        assertEquals(4, Dispenser.price(buyWheat()));
        assertEquals(COIN, Dispenser.currency(buyWheat()));
    }

    @Test
    void itBuysWhatTheMoneyAllowsIntoItsOwnSlotAndThenWaits() {
        store.addBalance(owner, COIN, 100);
        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT);

        assertTrue(dispenser.cycle(market, null, false).isEmpty());
        assertEquals(ReceiverStatus.WORKING, dispenser.status());
        assertEquals(25, dispenser.lots());
        assertEquals(100, dispenser.spent());
        assertEquals(400, count(dispenser.slot()));
        assertEquals(0, store.balance(owner, COIN));

        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.WAITING, dispenser.status());
        assertEquals(ReceiverLamp.YELLOW, dispenser.status().lamp());
        assertEquals(25, dispenser.lots());
    }

    @Test
    void aFullOwnSlotStopsThePurchaseBeforeAnyCoinIsTaken() {
        store.addBalance(owner, COIN, 1000);
        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT);
        dispenser.cycle(market, null, false);
        // Nine slots of 64 wheat: 36 lots of 16, then the slot is full.
        assertEquals(36, dispenser.lots());
        assertEquals(1000 - 36 * 4, store.balance(owner, COIN));

        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.NO_SPACE, dispenser.status());
        assertEquals(1000 - 36 * 4, store.balance(owner, COIN));

        // A pipe takes from the slot: the block buys again.
        assertEquals(64, dispenser.pipes().extractItem(0, 64, false).getCount());
        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.WORKING, dispenser.status());
        assertEquals(40, dispenser.lots());
    }

    @Test
    void pipesOnlyTakeFromTheOwnSlot() {
        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT);
        ItemStack wheat = new ItemStack(Items.WHEAT, 10);
        assertEquals(10, dispenser.pipes().insertItem(0, wheat, false).getCount());
        assertFalse(dispenser.pipes().isItemValid(0, wheat));
        assertTrue(dispenser.slotEmpty());
    }

    @Test
    void spendAtMostCountsCoinsSinceTheLastResetNotTime() {
        store.addBalance(owner, COIN, 1000);
        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT.withBudget(true, 22));

        dispenser.cycle(market, null, false);
        // 22 coins buy 5 lots of 4: never a coin over the budget.
        assertEquals(5, dispenser.lots());
        assertEquals(20, dispenser.spent());
        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.BUDGET, dispenser.status());
        assertEquals(980, store.balance(owner, COIN));

        dispenser.resetCounters();
        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.WORKING, dispenser.status());
        assertEquals(20, dispenser.spent());
        assertEquals(960, store.balance(owner, COIN));
    }

    @Test
    void keepOnTheAccountNeverTakesTheBalanceBelowIt() {
        store.addBalance(owner, COIN, 100);
        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT.withReserve(true, 90));

        dispenser.cycle(market, null, false);
        assertEquals(2, dispenser.lots());
        assertEquals(92, store.balance(owner, COIN));
        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.RESERVE, dispenser.status());
        assertEquals(92, store.balance(owner, COIN));

        // Below the price of a lot it is simply short of coins.
        store.addBalance(owner, COIN, -90);
        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.WAITING, dispenser.status());
    }

    @Test
    void keepAtMostBuysUpToTheStockInTheNeighbourAndNoMore() {
        store.addBalance(owner, COIN, 1000);
        ItemStackHandler chest = new ItemStackHandler(27);
        Dispenser dispenser = dispenser(toSide().withStock(true, 40));

        dispenser.cycle(market, chest, false);
        assertEquals(32, count(chest));
        assertEquals(2, dispenser.lots());
        dispenser.cycle(market, chest, false);
        assertEquals(ReceiverStatus.STOCKED, dispenser.status());
        assertFalse(ReceiverStatus.STOCKED.notifies());

        chest.extractItem(0, 30, false);
        dispenser.cycle(market, chest, false);
        assertEquals(ReceiverStatus.WORKING, dispenser.status());
        assertEquals(34, count(chest));
    }

    @Test
    void aTreasuryWithoutTheRightStopsTheBlockRedAndTakesNothing() {
        inTeam(false);
        store.addBalance(team, COIN, 100);
        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT.withTreasury(true));

        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.NO_RIGHT, dispenser.status());
        assertEquals(ReceiverLamp.RED, dispenser.status().lamp());
        assertEquals(100, store.balance(team, COIN));
        assertTrue(dispenser.slotEmpty());

        // Red says more than a limit: a spent budget does not hide the missing right.
        Dispenser spent = dispenser(DispenserSettings.DEFAULT.withTreasury(true).withBudget(true, 0));
        spent.cycle(market, null, false);
        assertEquals(ReceiverStatus.NO_RIGHT, spent.status());

        inTeam(true);
        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.WORKING, dispenser.status());
        assertEquals(0, store.balance(team, COIN));
        assertEquals(25, dispenser.lots());
    }

    @Test
    void theTreasuryIsSwitchedOnOnlyWithATeamAndTheRight() {
        inTeam(false);
        assertFalse(ReceiverAccess.mayPayFromTreasury(owner, market::team, false, false));
        assertTrue(ReceiverAccess.mayPayFromTreasury(owner, market::team, false, true));
        // Once on, a mate without the right may change other settings without switching it off.
        assertTrue(ReceiverAccess.mayPayFromTreasury(owner, market::team, true, false));
        UUID alone = UUID.randomUUID();
        assertFalse(ReceiverAccess.mayPayFromTreasury(alone, market::team, true, true));

        Dispenser dispenser = new Dispenser();
        dispenser.setOwner(alone, "Alone");
        dispenser.configure(DispenserSettings.DEFAULT.withOffer(Optional.of(buyWheat().id())).withTreasury(true));
        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.NO_TEAM, dispenser.status());
    }

    @Test
    void noRoomNextToItMeansNoPurchaseAndTheCoinsStay() {
        store.addBalance(owner, COIN, 100);
        Dispenser dispenser = dispenser(toSide());

        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.NO_SPACE, dispenser.status());
        assertEquals(100, store.balance(owner, COIN));

        ItemStackHandler full = new ItemStackHandler(1);
        full.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        dispenser.cycle(market, full, false);
        assertEquals(ReceiverStatus.NO_SPACE, dispenser.status());
        assertTrue(ReceiverStatus.NO_SPACE.notifies());
        assertEquals(100, store.balance(owner, COIN));
    }

    @Test
    void leftoversGoOutFirstAndHoldThePurchaseUntilTheyDo() {
        store.addBalance(owner, COIN, 100);
        Dispenser dispenser = dispenser(toSide());
        dispenser.slot().setStackInSlot(0, new ItemStack(Items.WHEAT, 20));

        ItemStackHandler small = new ItemStackHandler(1);
        small.setStackInSlot(0, new ItemStack(Items.WHEAT, 50));
        dispenser.cycle(market, small, false);
        // 14 went out, 6 are still in the own slot: nothing is bought.
        assertEquals(ReceiverStatus.NO_SPACE, dispenser.status());
        assertEquals(6, count(dispenser.slot()));
        assertEquals(100, store.balance(owner, COIN));

        ItemStackHandler chest = new ItemStackHandler(27);
        dispenser.cycle(market, chest, false);
        assertTrue(dispenser.slotEmpty());
        assertEquals(ReceiverStatus.WORKING, dispenser.status());
        assertEquals(6 + 25 * 16, count(chest));
    }

    @Test
    void theRedstoneSwitchMakesItWorkOnlyOnASignal() {
        store.addBalance(owner, COIN, 100);
        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT.withRedstone(true));

        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.REDSTONE, dispenser.status());
        assertEquals(ReceiverLamp.OFF, dispenser.status().lamp());
        assertFalse(ReceiverStatus.REDSTONE.notifies());
        assertEquals(100, store.balance(owner, COIN));

        dispenser.cycle(market, null, true);
        assertEquals(ReceiverStatus.WORKING, dispenser.status());

        Dispenser always = dispenser(DispenserSettings.DEFAULT);
        store.addBalance(owner, COIN, 4);
        always.cycle(market, null, false);
        assertEquals(1, always.lots());
    }

    @Test
    void aTradeGoneFromTheBookOrNotChosenStopsTheBlock() {
        Dispenser none = new Dispenser();
        none.cycle(market, null, false);
        assertEquals(ReceiverStatus.NO_OWNER, none.status());
        none.setOwner(owner, "Owner");
        none.cycle(market, null, false);
        assertEquals(ReceiverStatus.NO_OFFER, none.status());

        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT);
        offers.clear();
        dispenser.cycle(market, null, false);
        assertEquals(ReceiverStatus.CLOSED, dispenser.status());
    }

    @Test
    void theComparatorReadsTheStateOrTheFillOfTheOwnSlot() {
        store.addBalance(owner, COIN, 4);
        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT);
        dispenser.cycle(market, null, false);
        assertEquals(15, dispenser.signal());
        dispenser.cycle(market, null, false);
        assertEquals(10, dispenser.signal());

        dispenser.configure(dispenser.settings().withComparator(ComparatorMode.FILL));
        // 16 wheat in nine slots: a chest with one part of 36 full gives 1.
        assertEquals(1, dispenser.signal());
        assertEquals(5, ReceiverStatus.NO_RIGHT.signal());
        assertEquals(0, ReceiverStatus.NO_OFFER.signal());
        assertEquals(0, ComparatorMode.fillSignal(0));
        assertEquals(15, ComparatorMode.fillSignal(1));
    }

    @Test
    void aNewTradeStartsTheCountersAgain() {
        store.addBalance(owner, COIN, 8);
        Dispenser dispenser = dispenser(DispenserSettings.DEFAULT.withBudget(true, 100));
        dispenser.cycle(market, null, false);
        assertEquals(8, dispenser.spent());
        assertFalse(dispenser.configure(dispenser.settings().withBudget(true, 50)));
        assertEquals(8, dispenser.spent());

        ShopOffer bread = offer("t:bread", List.of(coins(1)), List.of(item("minecraft:bread", 1)));
        offers.put(bread.id(), bread);
        assertTrue(dispenser.configure(dispenser.settings().withOffer(Optional.of(bread.id()))));
        assertEquals(0, dispenser.spent());
        assertEquals(0, dispenser.lots());
    }

    @Test
    void theNumbersOfTheSettingsAreBounded() {
        DispenserSettings wild = DispenserSettings.DEFAULT.withBudget(true, -5).withReserve(true, Long.MAX_VALUE).withStock(true, 7);
        assertEquals(0, wild.budget());
        assertEquals(DispenserSettings.MAX, wild.reserve());
        assertEquals(7, wild.stock());
    }

    @Test
    void theBlockSavesItsSettingsSlotAndCounters() {
        store.addBalance(owner, COIN, 8);
        Dispenser dispenser = dispenser(toSide().withBudget(true, 40).withReserve(true, 3).withStock(true, 64).withRedstone(true)
                .withComparator(ComparatorMode.FILL).withNotices(false).withTreasury(false));
        dispenser.cycle(market, new ItemStackHandler(27), true);
        dispenser.slot().setStackInSlot(4, new ItemStack(Items.WHEAT, 12));
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        CompoundTag saved = dispenser.save(registries);
        Dispenser loaded = new Dispenser();
        loaded.load(saved, registries);
        assertEquals(dispenser.settings(), loaded.settings());
        assertEquals(owner, loaded.owner());
        assertEquals(dispenser.lots(), loaded.lots());
        assertEquals(dispenser.spent(), loaded.spent());
        assertEquals(dispenser.status(), loaded.status());
        assertEquals(12, count(loaded.slot()));

        Dispenser fresh = new Dispenser();
        fresh.load(new CompoundTag(), registries);
        assertEquals(DispenserSettings.DEFAULT, fresh.settings());
    }
}
