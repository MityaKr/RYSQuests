package works.repka.rysquests.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * What the window of an intake block shows, as the server sees it: sent when the window opens and again while it is open, only when
 * something changed (shop plan, section 4.6). The trades themselves the client has from the book.
 *
 * @param owner the owner, or a zero id for a block nobody owns yet
 * @param ownerInTeam whether the owner has a team, and so a treasury the coins may go to
 * @param wallet the owner's own coins, in the trade's currency
 * @param treasuryBalance the coins of the owner's team's treasury
 * @param buffer what the buffer holds, by item id
 */
public record ReceiverView(UUID owner, String ownerName, boolean ownerInTeam, ResourceLocation program, Optional<ResourceLocation> offer, boolean treasury,
        String currency, long wallet, long treasuryBalance, Map<String, Long> buffer, long accepted, long lots, long earned, ReceiverStatus status) {
    public static final UUID NOBODY = new UUID(0, 0);

    public static final StreamCodec<FriendlyByteBuf, ReceiverView> STREAM_CODEC = StreamCodec.of(ReceiverView::write, ReceiverView::read);

    private static final StreamCodec<io.netty.buffer.ByteBuf, Map<String, Long>> COUNTS = ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG);

    private static void write(FriendlyByteBuf buf, ReceiverView view) {
        UUIDUtil.STREAM_CODEC.encode(buf, view.owner);
        buf.writeUtf(view.ownerName);
        buf.writeBoolean(view.ownerInTeam);
        buf.writeResourceLocation(view.program);
        buf.writeOptional(view.offer, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(view.treasury);
        buf.writeUtf(view.currency);
        buf.writeVarLong(view.wallet);
        buf.writeVarLong(view.treasuryBalance);
        COUNTS.encode(buf, view.buffer);
        buf.writeVarLong(view.accepted);
        buf.writeVarLong(view.lots);
        buf.writeVarLong(view.earned);
        buf.writeEnum(view.status);
    }

    private static ReceiverView read(FriendlyByteBuf buf) {
        return new ReceiverView(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readBoolean(), buf.readResourceLocation(), buf.readOptional(FriendlyByteBuf::readResourceLocation),
                buf.readBoolean(), buf.readUtf(), buf.readVarLong(), buf.readVarLong(), COUNTS.decode(buf), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readEnum(ReceiverStatus.class));
    }

    public boolean owned() {
        return !owner.equals(NOBODY);
    }

    public long buffered(String item) {
        return buffer.getOrDefault(item, 0L);
    }
}
