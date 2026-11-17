package works.repka.rysquests.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.shop.TradeCore;
import works.repka.rysquests.shop.TradeSink;

/**
 * What a dispenser knows and does (shop plan, section 4.2), with no world in it, so that the tests can drive it: its owner and
 * settings, its own slot, the counters since they were last reset and its status.
 *
 * <p>Once a second ({@link #cycle}) it buys as many lots as the limits allow and the goods fit, up to {@link #MAX_LOTS}: the trade must
 * be open to the owner's team, the treasury needs the owner's right, then the three limits, then a simulated insertion; only then are
 * the coins taken and the goods put. What does not go in after all lies in the block's own slot, and the block buys nothing more until
 * the slot is empty again.
 *
 * <p>The limits are counted in coins and pieces, never in time (the author's principle: no waiting):
 * <ul>
 *   <li>"spend at most X": the block spends no more than X coins since its counter was last reset with the window's button;</li>
 *   <li>"keep on the account X": a lot is bought only when the balance stays at X or above after it;</li>
 *   <li>"keep at most N": the block buys only while the inventory it fills holds fewer than N of the goods.</li>
 * </ul>
 */
public final class Dispenser {
    /** Ticks between two purchases: one a second, as the intake block's sale. */
    public static final int CYCLE_TICKS = 20;
    /** Lots bought at most in one go: a stack's worth of purchases a second, never an unbounded heap. */
    public static final int MAX_LOTS = 64;
    /** Slots of the block's own store: a dispenser's worth. */
    public static final int SLOTS = 9;

    /** A sink that takes everything: a check of the trade itself, before the room is looked at. */
    private static final TradeSink ROOMY = new TradeSink() {
        @Override
        public boolean fits(List<Goods> goods) {
            return true;
        }

        @Override
        public List<Goods> put(List<Goods> goods) {
            return goods;
        }
    };

    private @Nullable UUID owner;
    private String ownerName = "";
    private DispenserSettings settings = DispenserSettings.DEFAULT;
    private Runnable changed = () -> {
    };
    private final ItemStackHandler slot = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int index) {
            changed.run();
        }
    };
    private long lots;
    private long spent;
    private ReceiverStatus status = ReceiverStatus.NO_OWNER;

    /** What pipes see of the own slot: they take from it, nothing goes in. */
    private final IItemHandler pipes = new IItemHandler() {
        @Override
        public int getSlots() {
            return slot.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            return slot.getStackInSlot(index);
        }

        @Override
        public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int index, int amount, boolean simulate) {
            return slot.extractItem(index, amount, simulate);
        }

        @Override
        public int getSlotLimit(int index) {
            return slot.getSlotLimit(index);
        }

        @Override
        public boolean isItemValid(int index, ItemStack stack) {
            return false;
        }
    };

    // --- what it is ---

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
    }

    /** Told whenever the own slot changes, by the block or by a pipe: the world saves the block and the comparator looks again. */
    public void onSlotChanged(Runnable changed) {
        this.changed = changed;
    }

    public DispenserSettings settings() {
        return settings;
    }

    public ReceiverStatus status() {
        return status;
    }

    public long lots() {
        return lots;
    }

    public long spent() {
        return spent;
    }

    /** The block's own slot, as the block itself fills it. */
    public ItemStackHandler slot() {
        return slot;
    }

    /** The own slot as pipes see it: take only. */
    public IItemHandler pipes() {
        return pipes;
    }

    public boolean slotEmpty() {
        for (int i = 0; i < slot.getSlots(); i++) {
            if (!slot.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Whether a trade can be bought by a dispenser: paid only with money, giving items. */
    public static boolean fits(ShopOffer offer) {
        return !offer.isSale() && !offer.delivery() && !offer.cost().isEmpty() && offer.cost().stream().allMatch(ShopOffer.Entry::isCurrency)
                && offer.gives().stream().anyMatch(entry -> !entry.isCurrency() && !entry.stack().isEmpty());
    }

    /** The currency the limits count: the first one the trade asks. */
    public static String currency(ShopOffer offer) {
        return offer.cost().stream().filter(ShopOffer.Entry::isCurrency).findFirst().map(ShopOffer.Entry::id).orElse("coin");
    }

    /** What one lot costs in that currency. */
    public static long price(ShopOffer offer) {
        String currency = currency(offer);
        return offer.cost().stream().filter(entry -> entry.isCurrency() && entry.id().equals(currency)).mapToLong(ShopOffer.Entry::amount).sum();
    }

    /** The goods "keep at most" counts: the first item the trade gives. */
    public static @Nullable ShopOffer.Entry goods(ShopOffer offer) {
        return offer.gives().stream().filter(entry -> !entry.isCurrency() && !entry.stack().isEmpty()).findFirst().orElse(null);
    }

    /** The trade, when it is still in the book and a dispenser can buy it. */
    public @Nullable ShopOffer offer(ReceiverMarket market) {
        ShopOffer found = settings.offer().map(market::offer).orElse(null);
        return found != null && fits(found) ? found : null;
    }

    /** How many of a stack an inventory holds, the same item with the same components. */
    static long count(IItemHandler handler, ItemStack template) {
        long have = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                have += stack.getCount();
            }
        }
        return have;
    }

    /** The comparator's level: the lamp, or the fill of the own slot as a chest's. */
    public int signal() {
        return settings.comparator() == ComparatorMode.FILL ? ItemHandlerHelper.calcRedstoneFromInventory(slot) : status.signal();
    }

    // --- what it does ---

    /**
     * Once a second: buys what it may and shows how it stands.
     *
     * @param target the inventory on the output side, null when there is none there (unused while the goods go to the own slot)
     * @param powered whether the block gets a redstone signal
     * @return stacks that went nowhere, not even into the own slot, for the world to drop: nothing paid for is ever lost
     */
    public List<ItemStack> cycle(ReceiverMarket market, @Nullable IItemHandler target, boolean powered) {
        List<ItemStack> spill = new ArrayList<>();
        ShopOffer trade = offer(market);
        if (owner == null) {
            status = ReceiverStatus.NO_OWNER;
            return spill;
        }
        if (settings.offer().isEmpty()) {
            status = ReceiverStatus.NO_OFFER;
            return spill;
        }
        if (trade == null) {
            status = ReceiverStatus.CLOSED;
            return spill;
        }
        if (settings.redstone() && !powered) {
            status = ReceiverStatus.REDSTONE;
            return spill;
        }
        IItemHandler into = slot;
        if (settings.output().isPresent()) {
            if (target == null) {
                status = ReceiverStatus.NO_SPACE;
                return spill;
            }
            // What was left over last time goes out first; while some of it is still here, the block buys nothing.
            if (!slotEmpty() && !push(target)) {
                status = ReceiverStatus.NO_SPACE;
                return spill;
            }
            into = target;
        }
        long price = price(trade);
        long times = MAX_LOTS;
        ReceiverStatus limit = null;
        if (settings.budgetOn() && price > 0) {
            long can = Math.max(0, settings.budget() - spent) / price;
            if (can < 1) {
                limit = ReceiverStatus.BUDGET;
            }
            times = Math.min(times, can);
        }
        if (limit == null && settings.reserveOn() && price > 0) {
            long balance = market.balance(owner, settings.treasury(), currency(trade));
            long can = Math.max(0, balance - settings.reserve()) / price;
            if (can < 1) {
                limit = balance >= price ? ReceiverStatus.RESERVE : ReceiverStatus.WAITING;
            }
            times = Math.min(times, can);
        }
        ShopOffer.Entry goods = goods(trade);
        if (limit == null && settings.stockOn() && goods != null) {
            long can = Math.max(0, settings.stock() - count(into, goods.stack())) / Math.max(1, goods.amount());
            if (can < 1) {
                limit = ReceiverStatus.STOCKED;
            }
            times = Math.min(times, can);
        }
        if (limit != null) {
            // A trade closed to the team or a treasury without the right says more than a limit: the lamp turns red.
            ReceiverStatus core = ReceiverStatus.of(market.checkBuy(owner, trade, settings.treasury(), ROOMY));
            status = core.lamp() == ReceiverLamp.RED ? core : limit;
            return spill;
        }
        TradeCore.Outcome outcome = market.buy(owner, trade, (int) times, settings.treasury(), TradeSink.of(into));
        if (!outcome.ok()) {
            status = ReceiverStatus.of(outcome.problem());
            return spill;
        }
        lots += outcome.times();
        spent += price * outcome.times();
        for (TradeSink.Goods left : outcome.leftover()) {
            keep(left, spill);
        }
        status = ReceiverStatus.WORKING;
        return spill;
    }

    /** Moves the own slot into the target. @return whether the slot is empty now */
    private boolean push(IItemHandler target) {
        for (int i = 0; i < slot.getSlots(); i++) {
            ItemStack stack = slot.getStackInSlot(i);
            if (!stack.isEmpty()) {
                slot.setStackInSlot(i, ItemHandlerHelper.insertItemStacked(target, stack.copy(), false));
            }
        }
        return slotEmpty();
    }

    /** Goods the sink did not take after all: into the own slot, and what not even that takes into the spill. */
    private void keep(TradeSink.Goods goods, List<ItemStack> spill) {
        long left = goods.count();
        int max = goods.template().getMaxStackSize();
        while (left > 0) {
            int part = (int) Math.min(left, max);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(slot, goods.template().copyWithCount(part), false);
            if (!rest.isEmpty()) {
                spill.add(rest);
            }
            left -= part;
        }
    }

    /**
     * New settings from the window, already checked on the server. @return whether the trade changed, so that the counters start
     * again: the budget of one trade is no budget of another
     */
    public boolean configure(DispenserSettings next) {
        boolean changed = !next.offer().equals(settings.offer());
        settings = next;
        if (changed) {
            resetCounters();
        }
        return changed;
    }

    /** The window's "reset": the counters, and with them the budget spent, start again from nothing. */
    public void resetCounters() {
        lots = 0;
        spent = 0;
    }

    /** Empties the own slot into stacks, for the world to drop when the block breaks. */
    public List<ItemStack> takeSlot() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < slot.getSlots(); i++) {
            ItemStack stack = slot.getStackInSlot(i);
            if (!stack.isEmpty()) {
                out.add(stack.copy());
                slot.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        return out;
    }

    // --- saving ---

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("owner_name", ownerName);
        }
        settings.offer().ifPresent(id -> tag.putString("offer", id.toString()));
        tag.putBoolean("treasury", settings.treasury());
        tag.putString("output", settings.output().map(Direction::getSerializedName).orElse("self"));
        tag.putBoolean("budget_on", settings.budgetOn());
        tag.putLong("budget", settings.budget());
        tag.putBoolean("reserve_on", settings.reserveOn());
        tag.putLong("reserve", settings.reserve());
        tag.putBoolean("stock_on", settings.stockOn());
        tag.putLong("stock", settings.stock());
        tag.putBoolean("redstone", settings.redstone());
        tag.putString("comparator", settings.comparator().key());
        tag.putBoolean("notify", settings.notices());
        tag.put("slot", slot.serializeNBT(registries));
        tag.putLong("lots", lots);
        tag.putLong("spent", spent);
        tag.putString("status", status.name());
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        Optional<ResourceLocation> offer = tag.contains("offer") ? Optional.ofNullable(ResourceLocation.tryParse(tag.getString("offer"))) : Optional.empty();
        Optional<Direction> output = Optional.ofNullable(Direction.byName(tag.getString("output")));
        settings = new DispenserSettings(offer, tag.getBoolean("treasury"), output, tag.getBoolean("budget_on"), tag.getLong("budget"), tag.getBoolean("reserve_on"),
                tag.getLong("reserve"), tag.getBoolean("stock_on"), tag.getLong("stock"), tag.getBoolean("redstone"), ComparatorMode.of(tag.getString("comparator")),
                !tag.contains("notify") || tag.getBoolean("notify"));
        if (tag.contains("slot")) {
            slot.deserializeNBT(registries, tag.getCompound("slot"));
        }
        lots = tag.getLong("lots");
        spent = tag.getLong("spent");
        try {
            status = ReceiverStatus.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException e) {
            status = owner == null ? ReceiverStatus.NO_OWNER : ReceiverStatus.NO_OFFER;
        }
    }
}
