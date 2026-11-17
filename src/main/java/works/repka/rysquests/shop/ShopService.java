package works.repka.rysquests.shop;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.network.ShopDeliverPayload;
import works.repka.rysquests.network.ShopResultPayload;
import works.repka.rysquests.network.ShopStatePayload;
import works.repka.rysquests.network.ShopTradePayload;
import works.repka.rysquests.progress.ProgressStore;

/** Makes trades. Nothing the client says is believed: balance, limit, stage, quest and free space are all checked here. */
public final class ShopService {
    /** What a command or a script does to a balance. */
    public enum WalletOp {
        ADD, TAKE, SET
    }

    private ShopService() {
    }

    private static ProgressMode mode() {
        return BookHolder.current().book().meta().progressMode();
    }

    /** The team a player shares a treasury and team limits with; the player alone when there is none. */
    private static UUID team(ServerPlayer player) {
        return ProgressStore.get(player.server).teamOf(player.getUUID(), ProgressMode.TEAM);
    }

    private static boolean inTeam(ServerPlayer player) {
        return !team(player).equals(player.getUUID());
    }

    /** The right of a team mod (RYSTeams) to pay from the team's treasury. Putting money into it needs no right. */
    public static final String SPEND_TREASURY = "spend_treasury";

    /** Whether a trade takes money from the treasury: a purchase paid from it, not a sale that puts money in. */
    static boolean spendsTreasury(ShopOffer offer, boolean treasury) {
        return treasury && offer.cost().stream().anyMatch(ShopOffer.Entry::isCurrency);
    }

    /**
     * Whether a player may pay from their team's treasury. Asked by id alone, so that it holds for a player who is not online or
     * has no screen open: a shop block pays on its owner's behalf.
     */
    public static boolean maySpendTreasury(UUID player) {
        return TeamsApi.get().can(player, SPEND_TREASURY);
    }

    public static void handle(ServerPlayer player, ShopTradePayload payload) {
        ShopOffer offer = BookHolder.current().book().offers().get(payload.offer());
        // A closed book is not sent to players, its shop included.
        boolean closed = !BookHolder.current().book().meta().published() && !player.hasPermissions(BookHolder.OPERATOR);
        String problem = offer == null || closed ? "rysquests.shop.error.unknown" : trade(player, offer, payload.times(), payload.treasury());
        PacketDistributor.sendToPlayer(player, new ShopResultPayload(problem == null, problem == null ? "" : problem));
        if (problem == null) {
            works.repka.rysquests.progress.Feedback.trade(player, offer.isSale());
            sendToTeam(player);
        }
    }

    /**
     * Whether the trade's quest lets this player trade: done once by their team, whoever did it. The tier of an achievement is a
     * quest too, so a trophy that opens after a boss (shop plan, step M3) opens for the whole team on the first kill by any member.
     * Asked by id alone, like the treasury's right.
     */
    public static boolean questOpens(ProgressStore store, UUID player, ShopOffer offer, ProgressMode mode) {
        return offer.quest().map(quest -> store.get(store.teamOf(player, mode), quest).everDone()).orElse(true);
    }

    /** The trade's core over this server's progress, the book's progress mode and today's date. */
    public static TradeCore core(MinecraftServer server) {
        return new TradeCore(ProgressStore.get(server), mode(), LocalDate.now());
    }

    /** What a player pays with: the main inventory, never armour or the offhand. */
    private static TradeSource source(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        return TradeSource.of(inventory.items, inventory::setChanged);
    }

    /** Where a player's goods go: the main inventory, and at the player's feet what does not fit after all. */
    private static TradeSink sink(ServerPlayer player) {
        return TradeSink.player(player.getInventory().items, stack -> ItemHandlerHelper.giveItemToPlayer(player, stack));
    }

    /** @return the lang key of why the trade cannot be made, or null when it was made */
    private static String trade(ServerPlayer player, ShopOffer offer, int times, boolean treasury) {
        return core(player.server).trade(offer, Trader.player(player, treasury), times, false, source(player), sink(player), System.currentTimeMillis()).problem();
    }

    /** A request to deliver from the inventory towards a delivery trade (shop plan, step M4). */
    public static void handleDeliver(ServerPlayer player, ShopDeliverPayload payload) {
        ShopOffer offer = BookHolder.current().book().offers().get(payload.offer());
        boolean closed = !BookHolder.current().book().meta().published() && !player.hasPermissions(BookHolder.OPERATOR);
        String problem = offer == null || closed || !offer.delivery() ? "rysquests.shop.error.unknown"
                : core(player.server).deliver(offer, Trader.player(player, false), source(player), false).problem();
        PacketDistributor.sendToPlayer(player, new ShopResultPayload(problem == null, problem == null ? "" : problem));
        if (problem == null) {
            works.repka.rysquests.progress.Feedback.submit(player);
            sendToTeam(player);
        }
    }

    /**
     * A trade a shop block makes on its owner's behalf (shop plan, step M5), the owner online or not: as many times as the money, the
     * goods and the room allow, up to {@code times}, with no stack's ceiling. Its history is one running line, and the owner's team
     * hears about the money at most once a second. A closed book trades nothing.
     *
     * @param owner the player who placed the block
     * @param treasury pay from and to the team's treasury, the owner's right to spend it asked at every trade
     */
    public static TradeCore.Outcome tradeFor(MinecraftServer server, UUID owner, ShopOffer offer, int times, boolean treasury, TradeSource from, TradeSink to) {
        if (!BookHolder.current().book().meta().published()) {
            return new TradeCore.Outcome("rysquests.shop.error.unknown", 0, List.of());
        }
        TradeCore.Outcome outcome = core(server).trade(offer, Trader.owner(owner, server.getPlayerList().getPlayer(owner), treasury), times, true, from, to, System.currentTimeMillis());
        if (outcome.ok()) {
            BROADCAST.mark(ProgressStore.get(server).teamOf(owner, ProgressMode.TEAM));
        }
        return outcome;
    }

    /**
     * Whether a shop block could make a trade once now, changing nothing (shop plan, step M6): the lamp of an intake block that has
     * nothing to sell yet still says whether the trade is open. @return the lang key of a refusal, or null
     */
    public static @Nullable String checkFor(MinecraftServer server, UUID owner, ShopOffer offer, boolean treasury, TradeSource from, TradeSink to) {
        if (!BookHolder.current().book().meta().published()) {
            return "rysquests.shop.error.unknown";
        }
        return core(server).check(offer, Trader.owner(owner, server.getPlayerList().getPlayer(owner), treasury), 1, true, from, to).problem();
    }

    /**
     * A delivery a shop block makes towards its owner's team's count (shop plan, step M5). @param simulate only say what would be taken
     */
    public static TradeCore.Delivered deliverFor(MinecraftServer server, UUID owner, ShopOffer offer, TradeSource from, boolean simulate) {
        if (!BookHolder.current().book().meta().published()) {
            return new TradeCore.Delivered("rysquests.shop.error.unknown", Map.of());
        }
        TradeCore.Delivered delivered = core(server).deliver(offer, Trader.owner(owner, server.getPlayerList().getPlayer(owner), false), from, simulate);
        if (delivered.ok() && !simulate) {
            BROADCAST.mark(ProgressStore.get(server).teamOf(owner, ProgressMode.TEAM));
        }
        return delivered;
    }

    private static final ShopBroadcast BROADCAST = new ShopBroadcast();

    /** Sends the states that blocks changed, at most once a second per team: nothing to do on most ticks. */
    public static void onServerTick(ServerTickEvent.Post event) {
        List<UUID> teams = BROADCAST.due(event.getServer().getTickCount());
        if (teams.isEmpty()) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (teams.contains(team(player))) {
                send(player);
            }
        }
    }

    /**
     * Whether what the trade gives fits, in the free slots and on top of stacks already held. A stack counts only when it is the
     * same item with the same components: a plain schematic does not take a schematic with its file.
     */
    static boolean hasRoom(List<ItemStack> items, ShopOffer offer, int times) {
        return TradeSink.roomIn(items, TradeCore.goods(offer, times));
    }

    /** A currency reward: a shared one goes to the treasury of the team. */
    public static void deposit(ServerPlayer player, String currency, long amount, boolean toTreasury, ResourceLocation source) {
        ProgressStore store = ProgressStore.get(player.server);
        boolean shared = toTreasury && inTeam(player);
        UUID wallet = shared ? team(player) : player.getUUID();
        store.addBalance(wallet, currency, amount);
        store.addShopHistory(player.getUUID(), new ProgressStore.ShopLine(System.currentTimeMillis(), amount, currency, source.toString(), 0, shared));
        sendToTeam(player);
    }

    public static boolean hasTreasury(ServerPlayer player) {
        return inTeam(player);
    }

    public static long balance(ServerPlayer player, boolean treasury, String currency) {
        return treasury && !inTeam(player) ? 0 : ProgressStore.get(player.server).balance(treasury ? team(player) : player.getUUID(), currency);
    }

    /** A change from a command or a script. @return false for a treasury without a team, or for taking more than there is */
    public static boolean change(ServerPlayer player, boolean treasury, String currency, WalletOp op, long amount) {
        if (treasury && !inTeam(player)) {
            return false;
        }
        ProgressStore store = ProgressStore.get(player.server);
        UUID owner = treasury ? team(player) : player.getUUID();
        long before = store.balance(owner, currency);
        long delta = switch (op) {
            case ADD -> amount;
            case TAKE -> -amount;
            case SET -> amount - before;
        };
        if (before + delta < 0) {
            return false;
        }
        if (delta != 0) {
            store.addBalance(owner, currency, delta);
            store.addShopHistory(player.getUUID(), new ProgressStore.ShopLine(System.currentTimeMillis(), delta, currency, ProgressStore.ShopLine.TRANSFER, 0, treasury));
            sendToTeam(player);
        }
        return true;
    }

    /** @return the coins added. Takes items first in whole batches, so that nothing can be paid twice or lost. */
    public static long exchange(ServerPlayer player, String item, String currency, int items, long coins, boolean heldOnly) {
        if (items < 1 || coins < 1) {
            throw new IllegalArgumentException("An exchange needs a positive number of items and coins");
        }
        Predicate<ItemStack> matches = matcher(item);
        Inventory inventory = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        if (heldOnly) {
            stacks.add(player.getMainHandItem());
        } else {
            stacks.addAll(inventory.items);
            stacks.addAll(inventory.offhand);
        }
        long held = stacks.stream().filter(matches).mapToLong(ItemStack::getCount).sum();
        long batches = held / items;
        if (batches == 0) {
            return 0;
        }
        long left = batches * items;
        for (ItemStack stack : stacks) {
            if (left > 0 && matches.test(stack)) {
                int taken = (int) Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        inventory.setChanged();
        long payout = batches * coins;
        ProgressStore store = ProgressStore.get(player.server);
        store.addBalance(player.getUUID(), currency, payout);
        store.addShopHistory(player.getUUID(), new ProgressStore.ShopLine(System.currentTimeMillis(), payout, currency, ProgressStore.ShopLine.EXCHANGE, (int) batches));
        sendToTeam(player);
        return payout;
    }

    private static Predicate<ItemStack> matcher(String item) {
        if (item.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(item.substring(1)));
            return stack -> stack.is(tag);
        }
        ResourceLocation id = ResourceLocation.parse(item);
        return stack -> !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    /** The trades of the book limited per team, by id. */
    private static Map<String, ShopOffer.Limit> teamLimits() {
        Map<String, ShopOffer.Limit> out = new HashMap<>();
        for (ShopOffer offer : BookHolder.current().book().offers().values()) {
            offer.limit().filter(limit -> limit.per() == ShopOffer.Per.TEAM).ifPresent(limit -> out.put(offer.id().toString(), limit));
        }
        return out;
    }

    /**
     * A player joined a team: what they bought alone of trades limited per team counts for the team too, and what they delivered alone
     * moves into the team's count.
     */
    public static void joinedTeam(MinecraftServer server, UUID team, UUID player) {
        ProgressStore store = ProgressStore.get(server);
        store.foldTeamLimits(player, team, teamLimits(), LocalDate.now());
        store.foldDeliveries(player, team);
    }

    /**
     * A player left a team, or it broke up: what the team bought of trades limited per team stays bought for them. What the team
     * delivered stays with the team: the player starts their own count from nothing, and what they deliver alone joins the team again
     * if they come back.
     */
    public static void leftTeam(MinecraftServer server, UUID team, UUID player) {
        ProgressStore.get(server).carryTeamLimits(team, player, teamLimits());
    }

    /** Two teams became one: what each bought of trades limited per team adds up, and so does what each delivered. */
    public static void mergedTeams(MinecraftServer server, UUID from, UUID into) {
        ProgressStore store = ProgressStore.get(server);
        store.addTeamLimits(from, into, teamLimits(), LocalDate.now());
        store.deliveries().move(from, into);
    }

    /** Forgets what a player's team, and the player alone, delivered. @param offer one trade, or null for all */
    public static void resetDeliveries(ServerPlayer player, @Nullable ResourceLocation offer) {
        ProgressStore store = ProgressStore.get(player.server);
        String key = offer == null ? null : offer.toString();
        store.deliveries().clear(team(player), key);
        store.deliveries().clear(player.getUUID(), key);
        sendToTeam(player);
    }

    /** Forgets what a player has used of the limits, and their team's too. @param offer one trade, or null for all */
    public static void resetLimits(ServerPlayer player, @Nullable ResourceLocation offer) {
        ProgressStore store = ProgressStore.get(player.server);
        String key = offer == null ? null : offer.toString();
        store.clearShopUsage(player.getUUID(), key);
        store.clearShopUsage(team(player), key);
        // Otherwise what was counted before the reset would hide the next purchase made alone from the team.
        store.clearCountedUsage(player.getUUID(), key);
        sendToTeam(player);
    }

    /** A treasury and team limits are shared, so everyone online in the team hears about a change. */
    private static void sendToTeam(ServerPlayer player) {
        UUID team = team(player);
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player || team(other).equals(team) && !team.equals(other.getUUID())) {
                send(other);
            }
        }
    }

    public static void send(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        UUID team = team(player);
        Map<String, Integer> used = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (ShopOffer offer : BookHolder.current().book().offers().values()) {
            offer.limit().ifPresent(limit -> {
                UUID owner = limit.per() == ShopOffer.Per.TEAM ? team : player.getUUID();
                int count = ShopLimits.used(limit, store.shopUsage(owner).get(offer.id().toString()), today);
                if (count > 0) {
                    used.put(offer.id().toString(), count);
                }
            });
        }
        // What the team delivered, only for the book's delivery trades: a count of anything else is no business of the client.
        Map<String, Map<String, Long>> delivered = new HashMap<>();
        store.deliveries().all(team).forEach((goal, items) -> {
            ResourceLocation id = ResourceLocation.tryParse(goal);
            ShopOffer offer = id == null ? null : BookHolder.current().book().offers().get(id);
            if (offer != null && offer.delivery()) {
                delivered.put(goal, items);
            }
        });
        PacketDistributor.sendToPlayer(player, new ShopStatePayload(Map.copyOf(store.balances(player.getUUID())), inTeam(player) ? Map.copyOf(store.balances(team)) : Map.of(),
                inTeam(player), used, List.copyOf(store.shopHistory(player.getUUID())), inTeam(player) && maySpendTreasury(player.getUUID()), delivered));
    }
}
