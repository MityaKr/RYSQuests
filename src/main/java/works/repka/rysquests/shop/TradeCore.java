package works.repka.rysquests.shop;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.shop.TradeSink.Goods;

/**
 * The trade itself, with no player in it (shop plan, step M5): a check that changes nothing and an execution of what the check allowed,
 * over a {@link TradeSource} the items are paid from and a {@link TradeSink} the goods go to. The book's shop uses it with the player's
 * inventory on both sides; the intake and dispenser blocks will use it with their buffer and the inventory next to them, for an owner
 * who may be offline.
 *
 * <p>Nothing here is heavy: a check is a few sums and lookups and, for goods, one simulated insertion (a few more when a trade that may
 * be made fewer times does not fit at once). The store is only marked changed; it is saved with the world.
 */
public final class TradeCore {
    /** A trade the check allowed: how many times, and whose wallet, limit and delivery count it touches. */
    public record Plan(ShopOffer offer, Trader trader, int times, UUID wallet, UUID limitOwner, UUID deliveryOwner) {
    }

    /** What a check found: a plan, or the lang key of why the trade cannot be made. */
    public record Check(@Nullable Plan plan, @Nullable String problem) {
        static Check refused(String problem) {
            return new Check(null, problem);
        }

        public boolean ok() {
            return plan != null;
        }
    }

    /**
     * How a trade went. @param problem the lang key of a refusal, or null when it was made @param times how many times it was made
     * @param leftover goods the sink did not take after all, for the caller to keep: a block holds them back until there is room
     */
    public record Outcome(@Nullable String problem, int times, List<Goods> leftover) {
        static Outcome refused(String problem) {
            return new Outcome(problem, 0, List.of());
        }

        public boolean ok() {
            return problem == null;
        }
    }

    /** What a delivery took, by item. @param problem the lang key of a refusal, or null */
    public record Delivered(@Nullable String problem, Map<String, Long> taken) {
        static Delivered refused(String problem) {
            return new Delivered(problem, Map.of());
        }

        public boolean ok() {
            return problem == null;
        }
    }

    private final ProgressStore store;
    private final ProgressMode mode;
    private final LocalDate today;

    /** @param mode the book's progress mode, for the trade's quest @param today the server's day, for limits that reset */
    public TradeCore(ProgressStore store, ProgressMode mode, LocalDate today) {
        this.store = store;
        this.mode = mode;
        this.today = today;
    }

    /** The team an owner shares a treasury, team limits and delivery counts with; the owner alone when there is none. */
    private UUID team(UUID owner) {
        return store.teamOf(owner, ProgressMode.TEAM);
    }

    /**
     * Checks a trade without changing anything. The refusals come in the order a player has always seen them: the request, the team,
     * the treasury's right, the stage, the quest, the limit, the delivery, the payment, the room.
     *
     * @param times how many times to make it
     * @param fewer make it as many times as possible up to {@code times} rather than exactly {@code times}, as a block does
     */
    public Check check(ShopOffer offer, Trader trader, int times, boolean fewer, TradeSource from, TradeSink to) {
        UUID owner = trader.owner();
        // The goods of a delivery are taken one round at a time: each round is delivered anew.
        int max = offer.delivery() ? 1 : trader.maxTimes();
        if (times < 1 || !fewer && times > max) {
            return Check.refused("rysquests.shop.error.unknown");
        }
        int want = Math.min(times, max);
        UUID team = team(owner);
        boolean inTeam = !team.equals(owner);
        if (trader.treasury() && !inTeam) {
            return Check.refused("rysquests.shop.error.no_team");
        }
        // Asked of the owner by id at every trade: an owner who left the team or lost the right stops their blocks at once.
        if (ShopService.spendsTreasury(offer, trader.treasury()) && !TeamsApi.get().can(owner, ShopService.SPEND_TREASURY)) {
            return Check.refused("rysquests.shop.error.no_right");
        }
        if (offer.stage().filter(stage -> !trader.stages().test(stage)).isPresent()) {
            return Check.refused("rysquests.shop.error.locked");
        }
        if (!ShopService.questOpens(store, owner, offer, mode)) {
            return Check.refused("rysquests.shop.error.locked");
        }
        UUID limitOwner = offer.limit().filter(limit -> limit.per() == ShopOffer.Per.TEAM).isPresent() ? team : owner;
        if (offer.limit().isPresent()) {
            ShopOffer.Limit limit = offer.limit().get();
            int left = limit.count() - ShopLimits.used(limit, store.shopUsage(limitOwner).get(offer.id().toString()), today);
            if (left < (fewer ? 1 : want)) {
                return Check.refused(!limit.onceForTeam() ? "rysquests.shop.error.limit" : inTeam ? "rysquests.shop.error.team_bought" : "rysquests.shop.error.bought");
            }
            want = Math.min(want, left);
        }
        // The items of a delivery are in the team's count already; only its coins are paid now.
        if (offer.delivery() && !offer.deliveryComplete(store.deliveries().get(team, offer.id().toString()))) {
            return Check.refused("rysquests.shop.error.delivery_short");
        }
        UUID wallet = trader.treasury() ? team : owner;
        for (Map.Entry<String, Long> coins : currencies(offer.cost()).entrySet()) {
            long can = store.balance(wallet, coins.getKey()) / coins.getValue();
            if (can < (fewer ? 1 : want)) {
                return Check.refused("rysquests.shop.error.not_enough");
            }
            want = (int) Math.min(want, can);
        }
        if (!offer.delivery()) {
            for (Map.Entry<Item, Long> items : items(offer.cost()).entrySet()) {
                long can = from.count(items.getKey()) / items.getValue();
                if (can < (fewer ? 1 : want)) {
                    return Check.refused("rysquests.shop.error.not_enough");
                }
                want = (int) Math.min(want, can);
            }
        }
        if (!to.fits(goods(offer, want))) {
            if (!fewer) {
                return Check.refused("rysquests.shop.error.no_space");
            }
            want = mostThatFit(offer, want, to);
            if (want == 0) {
                return Check.refused("rysquests.shop.error.no_space");
            }
        }
        return new Check(new Plan(offer, trader, want, wallet, limitOwner, team), null);
    }

    /** The largest number of times below {@code tooMany} whose goods fit, 0 when not even one does: a binary search of simulations. */
    private static int mostThatFit(ShopOffer offer, int tooMany, TradeSink to) {
        int fits = 0;
        int fails = tooMany;
        while (fails - fits > 1) {
            int middle = fits + (fails - fits) / 2;
            if (to.fits(goods(offer, middle))) {
                fits = middle;
            } else {
                fails = middle;
            }
        }
        return fits;
    }

    /**
     * Makes a trade a check allowed, in the same call on the server's thread, so that nothing changed in between: takes a delivery's
     * round from the count, the payment, then gives the goods and the money, counts the limit and writes the history.
     *
     * @param now the time of the history line
     */
    public Outcome execute(Plan plan, TradeSource from, TradeSink to, long now) {
        ShopOffer offer = plan.offer();
        int times = plan.times();
        String goal = offer.id().toString();
        if (offer.delivery() && !store.deliveries().take(plan.deliveryOwner(), goal, offer.deliveryRows())) {
            return Outcome.refused("rysquests.shop.error.delivery_short");
        }
        currencies(offer.cost()).forEach((currency, amount) -> store.addBalance(plan.wallet(), currency, -amount * times));
        if (!offer.delivery()) {
            items(offer.cost()).forEach((item, amount) -> from.take(item, amount * times));
        }
        currencies(offer.gives()).forEach((currency, amount) -> store.addBalance(plan.wallet(), currency, amount * times));
        List<Goods> leftover = to.put(goods(offer, times));
        offer.limit().ifPresent(limit -> store.setShopUsage(plan.limitOwner(), goal, ShopLimits.after(limit, store.shopUsage(plan.limitOwner()).get(goal), times, today)));
        ShopOffer.Entry money = offer.isSale() ? offer.gives().getFirst() : offer.cost().stream().filter(ShopOffer.Entry::isCurrency).findFirst().orElse(null);
        if (money != null) {
            long delta = (offer.isSale() ? 1 : -1) * (long) money.amount() * times;
            ProgressStore.ShopLine line = new ProgressStore.ShopLine(now, delta, money.id(), goal, times, plan.trader().treasury());
            if (plan.trader().summary()) {
                store.addShopSummary(plan.trader().owner(), line);
            } else {
                store.addShopHistory(plan.trader().owner(), line);
            }
        }
        return new Outcome(null, times, leftover);
    }

    /** Checks and makes a trade in one go. */
    public Outcome trade(ShopOffer offer, Trader trader, int times, boolean fewer, TradeSource from, TradeSink to, long now) {
        Check check = check(offer, trader, times, fewer, from, to);
        return check.ok() ? execute(check.plan(), from, to, now) : Outcome.refused(check.problem());
    }

    /**
     * Gives what a source holds of a delivery's rows to the count of the owner's team, never more than a row still lacks, so that nothing
     * is taken for nothing (shop plan, step M4).
     *
     * @param simulate only say what would be taken, as an intake block must when a pipe asks whether it may insert
     */
    public Delivered deliver(ShopOffer offer, Trader trader, TradeSource from, boolean simulate) {
        if (!offer.delivery()) {
            return Delivered.refused("rysquests.shop.error.unknown");
        }
        if (offer.stage().filter(stage -> !trader.stages().test(stage)).isPresent() || !ShopService.questOpens(store, trader.owner(), offer, mode)) {
            return Delivered.refused("rysquests.shop.error.locked");
        }
        UUID owner = team(trader.owner());
        if (offer.limit().isPresent()) {
            ShopOffer.Limit limit = offer.limit().get();
            UUID limitOwner = limit.per() == ShopOffer.Per.TEAM ? owner : trader.owner();
            if (ShopLimits.used(limit, store.shopUsage(limitOwner).get(offer.id().toString()), today) >= limit.count()) {
                return Delivered.refused("rysquests.shop.error.limit");
            }
        }
        String goal = offer.id().toString();
        Map<String, Long> delivered = store.deliveries().get(owner, goal);
        if (offer.deliveryComplete(delivered)) {
            return Delivered.refused("rysquests.shop.error.delivery_full");
        }
        Map<String, Long> held = new HashMap<>();
        for (String item : offer.deliveryRows().keySet()) {
            Item known = item(item);
            if (known != null) {
                held.put(item, from.count(known));
            }
        }
        Map<String, Long> wanted = offer.toDeliver(delivered, held);
        if (wanted.isEmpty()) {
            return Delivered.refused("rysquests.shop.error.nothing_to_deliver");
        }
        if (simulate) {
            return new Delivered(null, wanted);
        }
        Map<String, Long> taken = new LinkedHashMap<>();
        wanted.forEach((item, amount) -> {
            long got = from.take(item(item), amount);
            if (got > 0) {
                store.deliveries().add(owner, goal, item, got);
                taken.put(item, got);
            }
        });
        return new Delivered(null, taken);
    }

    private static @Nullable Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key) ? BuiltInRegistries.ITEM.get(key) : null;
    }

    /** The money of one side of a trade, once per currency. */
    private static Map<String, Long> currencies(List<ShopOffer.Entry> side) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (ShopOffer.Entry entry : side) {
            if (entry.isCurrency()) {
                out.merge(entry.id(), (long) entry.amount(), Long::sum);
            }
        }
        return out;
    }

    /** The items of one side of a trade, once per item; an unknown item asks for air, which nothing pays. */
    private static Map<Item, Long> items(List<ShopOffer.Entry> side) {
        Map<Item, Long> out = new LinkedHashMap<>();
        for (ShopOffer.Entry entry : side) {
            if (!entry.isCurrency()) {
                out.merge(BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.id())), (long) entry.amount(), Long::sum);
            }
        }
        return out;
    }

    /** What a trade gives in items, made {@code times} times. */
    static List<Goods> goods(ShopOffer offer, int times) {
        List<Goods> out = new ArrayList<>();
        for (ShopOffer.Entry gives : offer.gives()) {
            if (gives.isCurrency()) {
                continue;
            }
            ItemStack template = gives.stack();
            if (!template.isEmpty()) {
                out.add(new Goods(template, (long) gives.amount() * times));
            }
        }
        return out;
    }
}
