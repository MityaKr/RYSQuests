package works.repka.rysquests.block;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * What the owner sets in a dispenser's window (shop plan, section 4.2): the trade, where the coins come from, where the goods go, the
 * three limits, the redstone switch, what a comparator reads and whether the owner hears when the block stops. The window sends it
 * whole and the server checks every part of it; the view sends it back.
 *
 * @param output the side the goods go to, empty for the block's own slot, which pipes empty
 * @param budget "spend at most": coins the block may spend since its counter was last reset
 * @param reserve "keep on the account": the block never takes the balance below this
 * @param stock "keep at most": the block buys only while the inventory it fills holds fewer of the goods
 * @param redstone the block works only while it gets a redstone signal
 * @param notices the owner hears when the block stops
 */
public record DispenserSettings(Optional<ResourceLocation> offer, boolean treasury, Optional<Direction> output, boolean budgetOn, long budget,
        boolean reserveOn, long reserve, boolean stockOn, long stock, boolean redstone, ComparatorMode comparator, boolean notices) {
    /** The largest number a limit takes: more than any wallet will ever hold, small enough for every sum to stay exact. */
    public static final long MAX = 1_000_000_000_000L;

    /** A new block: nothing chosen, goods into its own slot, no limits, the comparator reads the state, notices on. */
    public static final DispenserSettings DEFAULT = new DispenserSettings(Optional.empty(), false, Optional.empty(), false, 0, false, 0, false, 0, false,
            ComparatorMode.STATE, true);

    public DispenserSettings {
        budget = clamp(budget);
        reserve = clamp(reserve);
        stock = clamp(stock);
    }

    private static long clamp(long value) {
        return Math.max(0, Math.min(MAX, value));
    }

    public static final StreamCodec<FriendlyByteBuf, DispenserSettings> STREAM_CODEC = StreamCodec.of(DispenserSettings::write, DispenserSettings::read);

    private static void write(FriendlyByteBuf buf, DispenserSettings s) {
        buf.writeOptional(s.offer, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(s.treasury);
        buf.writeOptional(s.output, FriendlyByteBuf::writeEnum);
        buf.writeBoolean(s.budgetOn);
        buf.writeVarLong(s.budget);
        buf.writeBoolean(s.reserveOn);
        buf.writeVarLong(s.reserve);
        buf.writeBoolean(s.stockOn);
        buf.writeVarLong(s.stock);
        buf.writeBoolean(s.redstone);
        buf.writeEnum(s.comparator);
        buf.writeBoolean(s.notices);
    }

    private static DispenserSettings read(FriendlyByteBuf buf) {
        return new DispenserSettings(buf.readOptional(FriendlyByteBuf::readResourceLocation), buf.readBoolean(), buf.readOptional(b -> b.readEnum(Direction.class)),
                buf.readBoolean(), buf.readVarLong(), buf.readBoolean(), buf.readVarLong(), buf.readBoolean(), buf.readVarLong(), buf.readBoolean(),
                buf.readEnum(ComparatorMode.class), buf.readBoolean());
    }

    public DispenserSettings withOffer(Optional<ResourceLocation> offer) {
        return new DispenserSettings(offer, treasury, output, budgetOn, budget, reserveOn, reserve, stockOn, stock, redstone, comparator, notices);
    }

    public DispenserSettings withTreasury(boolean treasury) {
        return new DispenserSettings(offer, treasury, output, budgetOn, budget, reserveOn, reserve, stockOn, stock, redstone, comparator, notices);
    }

    public DispenserSettings withOutput(Optional<Direction> output) {
        return new DispenserSettings(offer, treasury, output, budgetOn, budget, reserveOn, reserve, stockOn, stock, redstone, comparator, notices);
    }

    public DispenserSettings withBudget(boolean on, long budget) {
        return new DispenserSettings(offer, treasury, output, on, budget, reserveOn, reserve, stockOn, stock, redstone, comparator, notices);
    }

    public DispenserSettings withReserve(boolean on, long reserve) {
        return new DispenserSettings(offer, treasury, output, budgetOn, budget, on, reserve, stockOn, stock, redstone, comparator, notices);
    }

    public DispenserSettings withStock(boolean on, long stock) {
        return new DispenserSettings(offer, treasury, output, budgetOn, budget, reserveOn, reserve, on, stock, redstone, comparator, notices);
    }

    public DispenserSettings withRedstone(boolean redstone) {
        return new DispenserSettings(offer, treasury, output, budgetOn, budget, reserveOn, reserve, stockOn, stock, redstone, comparator, notices);
    }

    public DispenserSettings withComparator(ComparatorMode comparator) {
        return new DispenserSettings(offer, treasury, output, budgetOn, budget, reserveOn, reserve, stockOn, stock, redstone, comparator, notices);
    }

    public DispenserSettings withNotices(boolean notices) {
        return new DispenserSettings(offer, treasury, output, budgetOn, budget, reserveOn, reserve, stockOn, stock, redstone, comparator, notices);
    }
}
