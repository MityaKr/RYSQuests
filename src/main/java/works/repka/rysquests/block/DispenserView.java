package works.repka.rysquests.block;

import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the window of a dispenser shows, as the server sees it: sent when the window opens and again while it is open, only when
 * something changed (shop plan, section 4.6). The trades themselves the client has from the book.
 *
 * @param owner the owner, or a zero id for a block nobody owns yet
 * @param ownerInTeam whether the owner has a team, and so a treasury
 * @param mayTreasury whether the player looking may switch the payment to the treasury: a team, and the right to spend it
 * @param wallet the owner's own coins, in the trade's currency
 * @param treasuryBalance the coins of the owner's team's treasury
 * @param sides the sides with an inventory next to them, one bit per {@link Direction#get3DDataValue}
 * @param held how many items lie in the block's own slot
 */
public record DispenserView(UUID owner, String ownerName, boolean ownerInTeam, boolean mayTreasury, DispenserSettings settings, String currency, long wallet,
        long treasuryBalance, int sides, long held, long lots, long spent, ReceiverStatus status) {
    public static final StreamCodec<FriendlyByteBuf, DispenserView> STREAM_CODEC = StreamCodec.of(DispenserView::write, DispenserView::read);

    private static void write(FriendlyByteBuf buf, DispenserView view) {
        UUIDUtil.STREAM_CODEC.encode(buf, view.owner);
        buf.writeUtf(view.ownerName);
        buf.writeBoolean(view.ownerInTeam);
        buf.writeBoolean(view.mayTreasury);
        DispenserSettings.STREAM_CODEC.encode(buf, view.settings);
        buf.writeUtf(view.currency);
        buf.writeVarLong(view.wallet);
        buf.writeVarLong(view.treasuryBalance);
        buf.writeVarInt(view.sides);
        buf.writeVarLong(view.held);
        buf.writeVarLong(view.lots);
        buf.writeVarLong(view.spent);
        buf.writeEnum(view.status);
    }

    private static DispenserView read(FriendlyByteBuf buf) {
        return new DispenserView(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), DispenserSettings.STREAM_CODEC.decode(buf),
                buf.readUtf(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readEnum(ReceiverStatus.class));
    }

    public boolean owned() {
        return !owner.equals(ReceiverView.NOBODY);
    }

    /** Whether there is an inventory on a side. */
    public boolean hasInventory(Direction side) {
        return (sides & 1 << side.get3DDataValue()) != 0;
    }
}
