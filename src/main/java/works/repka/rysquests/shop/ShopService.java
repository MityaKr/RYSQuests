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
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.network.ShopResultPayload;
import works.repka.rysquests.network.ShopStatePayload;
import works.repka.rysquests.network.ShopTradePayload;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.stage.Stages;

/** Makes trades. Nothing the client says is believed: balance, limit, stage, quest and free space are all checked here. */
public final class ShopService {
    private static final int MAX_TIMES = 64;

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

    public static void handle(ServerPlayer player, ShopTradePayload payload) {
        ShopOffer offer = BookHolder.current().book().offers().get(payload.offer());
        String problem = offer == null ? "rysquests.shop.error.unknown" : trade(player, offer, payload.times(), payload.treasury());
        PacketDistributor.sendToPlayer(player, new ShopResultPayload(problem == null, problem == null ? "" : problem));
        if (problem == null) {
            works.repka.rysquests.progress.Feedback.trade(player, offer.isSale());
            sendToTeam(player);
        }
    }

    /** @return the lang key of why the trade cannot be made, or null when it was made */
    private static String trade(ServerPlayer player, ShopOffer offer, int times, boolean treasury) {
        if (times < 1 || times > MAX_TIMES) {
            return "rysquests.shop.error.unknown";
        }
        MinecraftServer server = player.server;
        ProgressStore store = ProgressStore.get(server);
        if (treasury && !inTeam(player)) {
            return "rysquests.shop.error.no_team";
        }
        if (offer.stage().filter(stage -> !Stages.has(player, stage)).isPresent()) {
            return "rysquests.shop.error.locked";
        }
        if (offer.quest().filter(quest -> !store.get(store.teamOf(player.getUUID(), mode()), quest).everDone()).isPresent()) {
            return "rysquests.shop.error.locked";
        }
        LocalDate today = LocalDate.now();
        UUID limitOwner = offer.limit().filter(limit -> limit.per() == ShopOffer.Per.TEAM).isPresent() ? team(player) : player.getUUID();
        if (offer.limit().isPresent()) {
            ShopOffer.Limit limit = offer.limit().get();
            if (ShopLimits.used(limit, store.shopUsage(limitOwner).get(offer.id().toString()), today) + times > limit.count()) {
                return "rysquests.shop.error.limit";
            }
        }
        UUID wallet = treasury ? team(player) : player.getUUID();
        Inventory inventory = player.getInventory();
        for (ShopOffer.Entry cost : offer.cost()) {
            long needed = (long) cost.amount() * times;
            if (cost.isCurrency() ? store.balance(wallet, cost.id()) < needed : count(inventory, item(cost)) < needed) {
                return "rysquests.shop.error.not_enough";
            }
        }
        if (!hasRoom(inventory, offer, times)) {
            return "rysquests.shop.error.no_space";
        }
        for (ShopOffer.Entry cost : offer.cost()) {
            long needed = (long) cost.amount() * times;
            if (cost.isCurrency()) {
                store.addBalance(wallet, cost.id(), -needed);
            } else {
                remove(inventory, item(cost), (int) needed);
            }
        }
        for (ShopOffer.Entry gives : offer.gives()) {
            long amount = (long) gives.amount() * times;
            if (gives.isCurrency()) {
                store.addBalance(wallet, gives.id(), amount);
            } else {
                give(player, item(gives), (int) amount);
            }
        }
        offer.limit().ifPresent(limit -> store.setShopUsage(limitOwner, offer.id().toString(),
                ShopLimits.after(limit, store.shopUsage(limitOwner).get(offer.id().toString()), times, today)));
        ShopOffer.Entry money = offer.isSale() ? offer.gives().getFirst() : offer.cost().stream().filter(ShopOffer.Entry::isCurrency).findFirst().orElse(null);
        if (money != null) {
            long delta = (offer.isSale() ? 1 : -1) * (long) money.amount() * times;
            store.addShopHistory(player.getUUID(), new ProgressStore.ShopLine(System.currentTimeMillis(), delta, money.id(), offer.id().toString(), times));
        }
        return null;
    }

    private static Item item(ShopOffer.Entry entry) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.id()));
    }

    /**
     * Whether a stack can pay: from the main inventory only, never armour or the offhand, and plain, so that a worn,
     * damaged, enchanted or renamed piece is never sold as an ordinary one.
     */
    private static boolean pays(ItemStack stack, Item item) {
        return stack.is(item) && stack.getComponentsPatch().isEmpty();
    }

    private static int count(Inventory inventory, Item item) {
        int have = 0;
        for (ItemStack stack : inventory.items) {
            if (pays(stack, item)) {
                have += stack.getCount();
            }
        }
        return have;
    }

    private static void remove(Inventory inventory, Item item, int amount) {
        int left = amount;
        for (int slot = 0; slot < inventory.items.size() && left > 0; slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (pays(stack, item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        inventory.setChanged();
    }

    /** Whether what the trade gives fits, in the free slots and on top of stacks already held. */
    private static boolean hasRoom(Inventory inventory, ShopOffer offer, int times) {
        int free = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        for (ShopOffer.Entry gives : offer.gives()) {
            if (gives.isCurrency()) {
                continue;
            }
            Item item = item(gives);
            int maxStack = new ItemStack(item).getMaxStackSize();
            long left = (long) gives.amount() * times;
            for (ItemStack stack : inventory.items) {
                if (stack.is(item)) {
                    left -= maxStack - stack.getCount();
                }
            }
            if (left > 0) {
                free -= (int) ((left + maxStack - 1) / maxStack);
            }
        }
        return free >= 0;
    }

    private static void give(ServerPlayer player, Item item, int amount) {
        int maxStack = new ItemStack(item).getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int part = Math.min(left, maxStack);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(item, part));
            left -= part;
        }
    }

    /** A currency reward: a shared one goes to the treasury of the team. */
    public static void deposit(ServerPlayer player, String currency, long amount, boolean toTreasury, ResourceLocation source) {
        ProgressStore store = ProgressStore.get(player.server);
        UUID wallet = toTreasury && inTeam(player) ? team(player) : player.getUUID();
        store.addBalance(wallet, currency, amount);
        store.addShopHistory(player.getUUID(), new ProgressStore.ShopLine(System.currentTimeMillis(), amount, currency, source.toString(), 0));
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
            store.addShopHistory(player.getUUID(), new ProgressStore.ShopLine(System.currentTimeMillis(), delta, currency, ProgressStore.ShopLine.TRANSFER, 0));
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

    /** Forgets what a player has used of the limits, and their team's too. @param offer one trade, or null for all */
    public static void resetLimits(ServerPlayer player, @Nullable ResourceLocation offer) {
        ProgressStore store = ProgressStore.get(player.server);
        String key = offer == null ? null : offer.toString();
        store.clearShopUsage(player.getUUID(), key);
        store.clearShopUsage(team(player), key);
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
        PacketDistributor.sendToPlayer(player, new ShopStatePayload(Map.copyOf(store.balances(player.getUUID())), inTeam(player) ? Map.copyOf(store.balances(team)) : Map.of(),
                inTeam(player), used, List.copyOf(store.shopHistory(player.getUUID()))));
    }
}
