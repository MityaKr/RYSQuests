package works.repka.rysquests.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.shop.TradeSource;

/**
 * What an intake block knows and does (shop plan, section 4.1), with no world in it, so that the tests can drive it: its owner, its
 * program, trade and wallet, the buffer of items short of a whole lot, the counters since it was placed and its status.
 *
 * <p>Nothing here runs on a tick. A pipe's insertion is a check and a sum; the block asks for {@link #settle} once, a second after
 * items came in, and that sells every whole lot in one trade. A red or dark block takes nothing; it looks again at most once a second,
 * and only when something knocks.
 */
public final class Receiver {
    /** Ticks from the first item in to the trade: one trade a second, whatever comes in meanwhile. */
    public static final int SETTLE_TICKS = 20;
    /** A stopped block looks at its trade again no sooner than this after it last did. */
    public static final int RECHECK_TICKS = 20;
    /** How many lots of each item the buffer holds before it says no: a second of the fastest pipes, never an unbounded heap. */
    public static final long BUFFER_LOTS = 256;

    private @Nullable UUID owner;
    private String ownerName = "";
    private ResourceLocation program = ReceiverPrograms.SELL.id();
    private @Nullable ResourceLocation offer;
    private boolean treasury;
    private ComparatorMode comparator = ComparatorMode.STATE;
    private boolean notify = true;
    private final Map<Item, Long> buffer = new LinkedHashMap<>();
    private long accepted;
    private long lots;
    private long earned;
    private ReceiverStatus status = ReceiverStatus.NO_OWNER;
    private long checkedAt = Long.MIN_VALUE / 2;
    private boolean pending;
    /** The time of the insertion being handled, for a refusal a program sees. */
    private long now;

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

    public ResourceLocation programId() {
        return program;
    }

    public @Nullable ReceiverProgram program() {
        return ReceiverProgram.Registry.get(program);
    }

    public @Nullable ResourceLocation offerId() {
        return offer;
    }

    public boolean treasury() {
        return treasury;
    }

    public ReceiverStatus status() {
        return status;
    }

    /** What a comparator next to the block reads. */
    public ComparatorMode comparator() {
        return comparator;
    }

    public void setComparator(ComparatorMode comparator) {
        this.comparator = comparator;
    }

    /** Whether the owner hears when the block stops. */
    public boolean notifies() {
        return notify;
    }

    public void setNotify(boolean notify) {
        this.notify = notify;
    }

    /**
     * The comparator's level: the lamp, or how full the buffer is, as a chest's fill is read. The buffer of a sale holds up to
     * {@link #BUFFER_LOTS} lots of each item; a delivery keeps no buffer, so its fill is always 0.
     */
    public int signal(ReceiverMarket market) {
        if (comparator == ComparatorMode.STATE) {
            return status.signal();
        }
        ShopOffer trade = offer(market);
        if (trade == null || buffer.isEmpty()) {
            return 0;
        }
        long room = 0;
        long held = 0;
        for (ShopOffer.Entry entry : trade.cost()) {
            if (!entry.isCurrency()) {
                room += entry.amount() * BUFFER_LOTS;
            }
        }
        for (long count : buffer.values()) {
            held += count;
        }
        return room <= 0 ? 0 : ComparatorMode.fillSignal((double) held / room);
    }

    public long accepted() {
        return accepted;
    }

    public long lots() {
        return lots;
    }

    public long earned() {
        return earned;
    }

    /** Whether items came in that the next settlement has to look at. */
    public boolean pending() {
        return pending;
    }

    /** The trade, when it is still in the book and fits the program. */
    public @Nullable ShopOffer offer(ReceiverMarket market) {
        ReceiverProgram mode = program();
        ShopOffer found = offer == null || mode == null ? null : market.offer(offer);
        return found != null && mode.fits(found) ? found : null;
    }

    // --- the buffer ---

    public long buffered(Item item) {
        return buffer.getOrDefault(item, 0L);
    }

    public Map<Item, Long> buffer() {
        return java.util.Collections.unmodifiableMap(buffer);
    }

    public boolean bufferEmpty() {
        return buffer.isEmpty();
    }

    /** Items a program took into the buffer. */
    void buffer(Item item, long count) {
        buffer.merge(item, count, Long::sum);
        accepted += count;
        pending = true;
    }

    /** Items a program gave straight to a count elsewhere, such as a delivery. */
    void counted(long count) {
        accepted += count;
        pending = true;
    }

    /** Lots a program sold, and the coins they brought. */
    void sold(long times, long coins) {
        lots += times;
        earned += coins;
    }

    /** A program saw the trade refuse: the block shows it at once, and the next settlement looks again. */
    void refused(ReceiverStatus status) {
        this.status = status;
        this.checkedAt = now;
        pending = true;
    }

    /** The buffer as a source a trade is paid from. */
    TradeSource bufferSource() {
        return new TradeSource() {
            @Override
            public long count(Item item) {
                return buffered(item);
            }

            @Override
            public long take(Item item, long amount) {
                long have = buffered(item);
                long taken = Math.min(have, amount);
                if (have - taken > 0) {
                    buffer.put(item, have - taken);
                } else {
                    buffer.remove(item);
                }
                return taken;
            }
        };
    }

    /** Empties the buffer into stacks, for the world to drop when the block breaks or changes its trade. */
    public List<ItemStack> takeBuffer() {
        List<ItemStack> out = new ArrayList<>();
        buffer.forEach((item, count) -> {
            long left = count;
            int max = new ItemStack(item).getMaxStackSize();
            while (left > 0) {
                int part = (int) Math.min(left, max);
                out.add(new ItemStack(item, part));
                left -= part;
            }
        });
        buffer.clear();
        return out;
    }

    // --- what it does ---

    /**
     * A pipe, a hopper or a network offers a stack. @param now the game's time in ticks @return what is left of it; the same when
     * simulating, so a pipe is never told a stack goes in that does not
     */
    public ItemStack insert(ReceiverMarket market, ItemStack stack, boolean simulate, long now) {
        if (stack.isEmpty()) {
            return stack;
        }
        this.now = now;
        if (!status.takes() && now - checkedAt >= RECHECK_TICKS) {
            refresh(market, now);
        }
        ShopOffer trade = status.takes() && owner != null ? offer(market) : null;
        if (trade == null) {
            return stack;
        }
        return Objects.requireNonNull(program()).insert(this, market, trade, stack, simulate);
    }

    /** Once a second after items came in: sells the whole lots, or looks how a delivery stands, and shows the outcome. */
    public void settle(ReceiverMarket market, long now) {
        pending = false;
        checkedAt = now;
        ShopOffer trade = owner == null ? null : offer(market);
        if (trade == null) {
            refresh(market, now);
            return;
        }
        status = ReceiverStatus.of(Objects.requireNonNull(program()).settle(this, market, trade));
    }

    /** Looks at the trade without changing anything: the lamp of a block that has nothing coming in. */
    public void refresh(ReceiverMarket market, long now) {
        checkedAt = now;
        ReceiverProgram mode = program();
        if (owner == null) {
            status = ReceiverStatus.NO_OWNER;
        } else if (offer == null || mode == null) {
            status = ReceiverStatus.NO_OFFER;
        } else {
            ShopOffer trade = offer(market);
            status = trade == null ? ReceiverStatus.CLOSED : ReceiverStatus.of(mode.check(this, market, trade));
        }
    }

    /**
     * New settings from the window, already checked on the server. @return whether the trade or the program changed, so that what
     * the buffer holds of the old one has to go
     */
    public boolean configure(ResourceLocation program, @Nullable ResourceLocation offer, boolean treasury) {
        boolean changed = !program.equals(this.program) || !Objects.equals(offer, this.offer);
        this.program = program;
        this.offer = offer;
        this.treasury = treasury;
        return changed;
    }

    /** The window's "сбросить": the counters start again from nothing. */
    public void resetCounters() {
        accepted = 0;
        lots = 0;
        earned = 0;
    }

    // --- saving ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("owner_name", ownerName);
        }
        tag.putString("program", program.toString());
        if (offer != null) {
            tag.putString("offer", offer.toString());
        }
        tag.putBoolean("treasury", treasury);
        tag.putString("comparator", comparator.key());
        tag.putBoolean("notify", notify);
        CompoundTag held = new CompoundTag();
        buffer.forEach((item, count) -> held.putLong(BuiltInRegistries.ITEM.getKey(item).toString(), count));
        tag.put("buffer", held);
        tag.putLong("accepted", accepted);
        tag.putLong("lots", lots);
        tag.putLong("earned", earned);
        tag.putString("status", status.name());
        tag.putBoolean("pending", pending);
        return tag;
    }

    public void load(CompoundTag tag) {
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        ResourceLocation saved = ResourceLocation.tryParse(tag.getString("program"));
        program = saved != null ? saved : ReceiverPrograms.SELL.id();
        offer = tag.contains("offer") ? ResourceLocation.tryParse(tag.getString("offer")) : null;
        treasury = tag.getBoolean("treasury");
        comparator = ComparatorMode.of(tag.getString("comparator"));
        notify = !tag.contains("notify") || tag.getBoolean("notify");
        buffer.clear();
        CompoundTag held = tag.getCompound("buffer");
        for (String key : held.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            long count = held.getLong(key);
            // An item of a mod that is gone is not kept: nothing could ever drop it again.
            if (item != Items.AIR && count > 0) {
                buffer.put(item, count);
            }
        }
        accepted = tag.getLong("accepted");
        lots = tag.getLong("lots");
        earned = tag.getLong("earned");
        try {
            status = ReceiverStatus.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException e) {
            status = owner == null ? ReceiverStatus.NO_OWNER : ReceiverStatus.NO_OFFER;
        }
        pending = tag.getBoolean("pending");
        checkedAt = Long.MIN_VALUE / 2;
    }
}
