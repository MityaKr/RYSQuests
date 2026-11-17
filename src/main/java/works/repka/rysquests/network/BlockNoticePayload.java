package works.repka.rysquests.network;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.block.ReceiverStatus;

/**
 * A shop block of the player stopped (decision on question 17 of the shop plan): the client shows it as one of the family's notices.
 *
 * @param kind "receiver" or "dispenser"
 * @param more how many other blocks of the player stopped too since the last notice
 */
public record BlockNoticePayload(String kind, BlockPos pos, ReceiverStatus status, Optional<ResourceLocation> offer, int more) implements CustomPacketPayload {
    public static final Type<BlockNoticePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "block_notice"));

    public static final StreamCodec<FriendlyByteBuf, BlockNoticePayload> STREAM_CODEC = StreamCodec.of(BlockNoticePayload::write, BlockNoticePayload::read);

    private static void write(FriendlyByteBuf buf, BlockNoticePayload p) {
        buf.writeUtf(p.kind);
        buf.writeBlockPos(p.pos);
        buf.writeEnum(p.status);
        buf.writeOptional(p.offer, FriendlyByteBuf::writeResourceLocation);
        buf.writeVarInt(p.more);
    }

    private static BlockNoticePayload read(FriendlyByteBuf buf) {
        return new BlockNoticePayload(buf.readUtf(), buf.readBlockPos(), buf.readEnum(ReceiverStatus.class), buf.readOptional(FriendlyByteBuf::readResourceLocation),
                buf.readVarInt());
    }

    @Override
    public Type<BlockNoticePayload> type() {
        return TYPE;
    }
}
