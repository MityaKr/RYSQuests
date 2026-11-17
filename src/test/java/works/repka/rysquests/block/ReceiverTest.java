package works.repka.rysquests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * The intake block without a world (shop plan, step M6): what a pipe is told and what really goes in, the sale of whole lots once a
 * second, the buffer's ceiling, a delivery that takes only what is missing, the lamp of a closed or used-up trade and of a treasury
 * without a team, who may open and break the block, and what the block saves.
 */
class ReceiverTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    private static final String COIN = "coin";

    private final ProgressStore store = new ProgressStore();
    private final TradeCore core = new TradeCore(store, ProgressMode.TEAM, DAY);
    private final Map<ResourceLocation, ShopOffer> offers = new HashMap<>();
    private final UUID owner = UUID.randomUUID();
    private final UUID mate = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
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

    /** The owner and a mate in one team; the stranger alone. */
    private void inTeam() {
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
                return false;
            }

            @Override
            public void post(UUID of, Post post) {
            }
        });
    }

    private static ShopOffer offer(String id, List<Entry> cost, List<Entry> gives, Optional<ShopOffer.Limit> limit, boolean delivery) {
        return new ShopOffer(ResourceLocation.parse(id), Optional.empty(), Optional.empty(), Optional.empty(), cost, gives, limit, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), delivery);
    }

    private static Entry coins(int amount) {
        return new Entry(Kind.CURRENCY, COIN, amount);
    }

    private static Entry item(String id, int count) {
        return new Entry(Kind.ITEM, id, count);
    }

    /** 16 wheat sold for 2 coins, in the book. */
    private ShopOffer sellWheat() {
        return add(offer("t:sell", List.of(item("minecraft:wheat", 16)), List.of(coins(2)), Optional.empty(), false));
    }

    /** A trophy paid in parts: 32 wheat and 8 iron, then 5 coins for a diamond. */
    private ShopOffer deliverTrophy() {
        return add(offer("t:trophy", List.of(item("minecraft:wheat", 32), item("minecraft:iron_ingot", 8), coins(5)), List.of(item("minecraft:diamond", 1)), Optional.empty(), true));
    }

    private ShopOffer add(ShopOffer offer) {
        offers.put(offer.id(), offer);
        return offer;
    }

    private Receiver receiver(ReceiverProgram program, ShopOffer offer, boolean treasury) {
        Receiver receiver = new Receiver();
        receiver.setOwner(owner, "Owner");
        receiver.configure(program.id(), offer.id(), treasury);
        receiver.refresh(market, 0);
        return receiver;
    }

    private static ItemStack wheat(int count) {
        return new ItemStack(Items.WHEAT, count);
    }

    @Test
    void aSimulationChangesNothingAndWholeLotsAreSoldOnceASecond() {
        Receiver receiver = receiver(ReceiverPrograms.SELL, sellWheat(), false);
        assertEquals(ReceiverStatus.WORKING, receiver.status());

        assertTrue(receiver.insert(market, wheat(20), true, 1).isEmpty());
        assertTrue(receiver.bufferEmpty());
        assertFalse(receiver.pending());

        assertTrue(receiver.insert(market, wheat(20), false, 1).isEmpty());
        assertEquals(20, receiver.buffered(Items.WHEAT));
        assertTrue(receiver.pending());
        // Nothing is sold on the insertion itself: the trade waits for the settlement a second later.
        assertEquals(0, store.balance(owner, COIN));

        receiver.settle(market, 21);
        assertEquals(2, store.balance(owner, COIN));
        assertEquals(4, receiver.buffered(Items.WHEAT));
        assertEquals(20, receiver.accepted());
        assertEquals(1, receiver.lots());
        assertEquals(2, receiver.earned());
        assertFalse(receiver.pending());
        // One running line of the history for the block, however many settlements follow.
        receiver.insert(market, wheat(28), false, 30);
        receiver.settle(market, 50);
        assertEquals(6, store.balance(owner, COIN));
        assertEquals(1, store.shopHistory(owner).size());
        assertEquals(3, store.shopHistory(owner).getFirst().times());
    }

    @Test
    void onlyThePlainItemOfTheTradeGoesIn() {
        Receiver receiver = receiver(ReceiverPrograms.SELL, sellWheat(), false);
        ItemStack dirt = new ItemStack(Items.DIRT, 10);
        assertEquals(10, receiver.insert(market, dirt, false, 1).getCount());
        ItemStack named = wheat(16);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Golden wheat"));
        // A renamed or enchanted piece is never sold as an ordinary one: the pipe keeps it.
        assertEquals(16, receiver.insert(market, named, false, 1).getCount());
        assertTrue(receiver.bufferEmpty());
    }

    @Test
    void theBufferSaysNoAfterItsCeilingOfLots() {
        Receiver receiver = receiver(ReceiverPrograms.SELL, sellWheat(), false);
        long ceiling = 16 * Receiver.BUFFER_LOTS;
        long in = 0;
        for (int i = 0; i < ceiling / 64; i++) {
            ItemStack rest = receiver.insert(market, wheat(64), false, 1);
            in += 64 - rest.getCount();
        }
        assertEquals(ceiling, in);
        assertEquals(64, receiver.insert(market, wheat(64), true, 1).getCount());
        assertEquals(64, receiver.insert(market, wheat(64), false, 1).getCount());

        receiver.settle(market, 21);
        assertEquals(Receiver.BUFFER_LOTS * 2, store.balance(owner, COIN));
        assertTrue(receiver.bufferEmpty());
        assertTrue(receiver.insert(market, wheat(64), false, 22).isEmpty());
    }

    @Test
    void coinsForATreasuryNeedATeamButNoRight() {
        Receiver receiver = receiver(ReceiverPrograms.SELL, sellWheat(), true);
        assertEquals(ReceiverStatus.NO_TEAM, receiver.status());
        assertEquals(ReceiverLamp.RED, receiver.status().lamp());
        assertEquals(16, receiver.insert(market, wheat(16), false, 1).getCount());

        inTeam();
        // A stopped block looks again no sooner than a second after it last did.
        assertEquals(16, receiver.insert(market, wheat(16), false, 10).getCount());
        assertTrue(receiver.insert(market, wheat(16), false, 20).isEmpty());
        assertEquals(ReceiverStatus.WORKING, receiver.status());
        receiver.settle(market, 40);
        // Putting coins into the treasury needs no right (ADR-52): nobody in this team has one.
        assertEquals(2, store.balance(team, COIN));
        assertEquals(0, store.balance(owner, COIN));
        assertTrue(ReceiverAccess.mayChooseTreasury(owner, market::team));
    }

    @Test
    void aTradeGoneFromTheBookStopsTheBlockUntilItComesBack() {
        ShopOffer sale = sellWheat();
        Receiver receiver = receiver(ReceiverPrograms.SELL, sale, false);
        receiver.insert(market, wheat(8), false, 1);
        offers.clear();
        receiver.settle(market, 21);
        assertEquals(ReceiverStatus.CLOSED, receiver.status());
        assertEquals(16, receiver.insert(market, wheat(16), false, 22).getCount());

        add(sale);
        assertEquals(16, receiver.insert(market, wheat(16), false, 30).getCount());
        assertTrue(receiver.insert(market, wheat(16), false, 41).isEmpty());
        receiver.settle(market, 61);
        // What waited in the buffer while the trade was gone is sold with the rest.
        assertEquals(2, store.balance(owner, COIN));
        assertEquals(8, receiver.buffered(Items.WHEAT));
    }

    @Test
    void aUsedUpLimitStopsTheBlock() {
        ShopOffer once = add(offer("t:once", List.of(item("minecraft:wheat", 16)), List.of(coins(2)),
                Optional.of(new ShopOffer.Limit(ShopOffer.Per.PLAYER, 1, ShopOffer.Reset.NEVER)), false));
        Receiver receiver = receiver(ReceiverPrograms.SELL, once, false);
        receiver.insert(market, wheat(48), false, 1);
        receiver.settle(market, 21);
        assertEquals(2, store.balance(owner, COIN));
        assertEquals(1, receiver.lots());
        assertEquals(32, receiver.buffered(Items.WHEAT));

        receiver.insert(market, wheat(1), false, 22);
        receiver.settle(market, 42);
        assertEquals(ReceiverStatus.LIMIT, receiver.status());
        assertEquals(ReceiverLamp.RED, receiver.status().lamp());
        assertEquals(16, receiver.insert(market, wheat(16), false, 100).getCount());
    }

    @Test
    void aSaleThatAlsoCostsCoinsWaitsForThemAndKeepsTakingItems() {
        ShopOffer token = add(offer("t:token", List.of(item("minecraft:wheat", 16), coins(1)), List.of(new Entry(Kind.CURRENCY, "token", 3)), Optional.empty(), false));
        Receiver receiver = receiver(ReceiverPrograms.SELL, token, false);
        assertEquals(ReceiverStatus.WAITING, receiver.status());
        assertEquals(ReceiverLamp.YELLOW, receiver.status().lamp());
        assertTrue(receiver.insert(market, wheat(32), false, 1).isEmpty());

        store.addBalance(owner, COIN, 1);
        receiver.settle(market, 21);
        assertEquals(3, store.balance(owner, "token"));
        assertEquals(0, store.balance(owner, COIN));
        assertEquals(16, receiver.buffered(Items.WHEAT));
        assertEquals(ReceiverStatus.WAITING, receiver.status());
    }

    @Test
    void aDeliveryTakesOnlyWhatItLacksAndWaitsWhenFull() {
        inTeam();
        ShopOffer trophy = deliverTrophy();
        Receiver receiver = receiver(ReceiverPrograms.DELIVERY, trophy, false);
        assertEquals(ReceiverStatus.WORKING, receiver.status());
        String goal = trophy.id().toString();

        assertEquals(8, receiver.insert(market, wheat(40), true, 1).getCount());
        assertTrue(store.deliveries().get(team, goal).isEmpty());

        assertEquals(8, receiver.insert(market, wheat(40), false, 1).getCount());
        assertEquals(32L, store.deliveries().get(team, goal).get("minecraft:wheat"));
        // A full row takes no more, the others still do.
        assertEquals(5, receiver.insert(market, wheat(5), false, 2).getCount());
        assertEquals(ReceiverStatus.WORKING, receiver.status());
        assertEquals(2, receiver.insert(market, new ItemStack(Items.IRON_INGOT, 10), false, 3).getCount());
        assertEquals(40, receiver.accepted());

        receiver.settle(market, 23);
        assertEquals(ReceiverStatus.FULL, receiver.status());
        assertEquals(ReceiverLamp.YELLOW, receiver.status().lamp());
        assertEquals(1, receiver.insert(market, new ItemStack(Items.IRON_INGOT, 1), false, 50).getCount());
        // Nothing was sold or paid: the goods wait in the shop for the coins.
        assertEquals(0, store.balance(team, COIN));
    }

    @Test
    void aSaleAndADeliveryEachTakeOnlyTheirOwnTrades() {
        ShopOffer sale = sellWheat();
        ShopOffer trophy = deliverTrophy();
        assertTrue(ReceiverPrograms.SELL.fits(sale));
        assertFalse(ReceiverPrograms.SELL.fits(trophy));
        assertTrue(ReceiverPrograms.DELIVERY.fits(trophy));
        assertFalse(ReceiverPrograms.DELIVERY.fits(sale));

        Receiver wrong = receiver(ReceiverPrograms.DELIVERY, sale, false);
        assertEquals(ReceiverStatus.CLOSED, wrong.status());
        assertEquals(16, wrong.insert(market, wheat(16), false, 30).getCount());
    }

    @Test
    void theTrialIsKeptForLaterAndANewModeNeedsNoNewFormat() {
        assertNull(ReceiverProgram.Registry.get(ReceiverProgram.TRIAL));
        assertEquals(List.of(ReceiverPrograms.SELL, ReceiverPrograms.DELIVERY), List.copyOf(ReceiverProgram.Registry.all()));
        Receiver receiver = new Receiver();
        receiver.setOwner(owner, "Owner");
        receiver.configure(ReceiverProgram.TRIAL, sellWheat().id(), false);
        receiver.refresh(market, 0);
        assertEquals(ReceiverStatus.NO_OFFER, receiver.status());
        assertEquals(16, receiver.insert(market, wheat(16), false, 30).getCount());

        Receiver nobody = new Receiver();
        nobody.refresh(market, 0);
        assertEquals(ReceiverStatus.NO_OWNER, nobody.status());
        assertEquals(ReceiverLamp.OFF, nobody.status().lamp());
    }

    @Test
    void theOwnerAndTheTeamUseTheBlockAndAnOperatorBreaksIt() {
        inTeam();
        assertTrue(ReceiverAccess.mayUse(owner, owner, market::team));
        assertTrue(ReceiverAccess.mayUse(owner, mate, market::team));
        assertFalse(ReceiverAccess.mayUse(owner, stranger, market::team));
        assertFalse(ReceiverAccess.mayBreak(owner, stranger, market::team, false));
        assertTrue(ReceiverAccess.mayBreak(owner, stranger, market::team, true));
        assertTrue(ReceiverAccess.mayBreak(owner, mate, market::team, false));
        // A block set with a command belongs to nobody, and the first player to open it takes it.
        assertTrue(ReceiverAccess.mayUse(null, stranger, market::team));
        assertFalse(ReceiverAccess.mayChooseTreasury(stranger, market::team));
        assertFalse(ReceiverAccess.mayChooseTreasury(null, market::team));

        // Leaving the team takes the window away at once: the team is looked up every time.
        TeamsApi.provide(before);
        assertFalse(ReceiverAccess.mayUse(owner, mate, market::team));
    }

    @Test
    void theBlockSavesItsSettingsBufferAndCounters() {
        ShopOffer sale = sellWheat();
        Receiver receiver = receiver(ReceiverPrograms.SELL, sale, false);
        receiver.insert(market, wheat(40), false, 1);
        receiver.settle(market, 21);
        CompoundTag tag = receiver.save();

        Receiver loaded = new Receiver();
        loaded.load(tag);
        assertEquals(owner, loaded.owner());
        assertEquals("Owner", loaded.ownerName());
        assertEquals(ReceiverPrograms.SELL.id(), loaded.programId());
        assertEquals(sale.id(), loaded.offerId());
        assertFalse(loaded.treasury());
        assertEquals(8, loaded.buffered(Items.WHEAT));
        assertEquals(40, loaded.accepted());
        assertEquals(2, loaded.lots());
        assertEquals(4, loaded.earned());
        assertEquals(ReceiverStatus.WORKING, loaded.status());

        // The rest of the buffer drops whole when the block breaks; the counters start again on "сбросить".
        List<ItemStack> dropped = loaded.takeBuffer();
        assertEquals(1, dropped.size());
        assertEquals(8, dropped.getFirst().getCount());
        assertTrue(loaded.bufferEmpty());
        loaded.resetCounters();
        assertEquals(0, loaded.accepted() + loaded.lots() + loaded.earned());
    }

    @Test
    void aChangeOfTradeIsReportedSoTheBufferCanGo() {
        Receiver receiver = receiver(ReceiverPrograms.SELL, sellWheat(), false);
        assertFalse(receiver.configure(ReceiverPrograms.SELL.id(), receiver.offerId(), true));
        assertTrue(receiver.configure(ReceiverPrograms.DELIVERY.id(), null, false));
        receiver.refresh(market, 0);
        assertEquals(ReceiverStatus.NO_OFFER, receiver.status());
    }

    @Test
    void everyRefusalOfTheCoreHasItsLamp() {
        assertEquals(ReceiverStatus.WORKING, ReceiverStatus.of(null));
        assertEquals(ReceiverStatus.WAITING, ReceiverStatus.of("rysquests.shop.error.not_enough"));
        assertEquals(ReceiverStatus.FULL, ReceiverStatus.of("rysquests.shop.error.delivery_full"));
        assertEquals(ReceiverStatus.NO_TEAM, ReceiverStatus.of("rysquests.shop.error.no_team"));
        assertEquals(ReceiverStatus.LIMIT, ReceiverStatus.of("rysquests.shop.error.team_bought"));
        assertEquals(ReceiverStatus.CLOSED, ReceiverStatus.of("rysquests.shop.error.locked"));
        assertEquals(ReceiverStatus.CLOSED, ReceiverStatus.of("rysquests.shop.error.unknown"));
        for (ReceiverStatus status : ReceiverStatus.values()) {
            assertEquals(status.takes(), status == ReceiverStatus.WORKING || status == ReceiverStatus.WAITING);
        }
    }
}
