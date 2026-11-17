package works.repka.rysquests.network;

import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.block.ComparatorMode;

/**
 * The settings an intake block's window asks for (shop plan, step M6): the mode, the trade, where the coins go, and whether to reset
 * the counters, what a comparator reads and whether the owner hears when the block stops. The server checks all of it: who asks, that
 * the trade exists and fits the mode, that there is a treasury.
 */
public record ReceiverConfigPayload(int containerId, ResourceLocation program, Optional<ResourceLocation> offer, boolean treasury, boolean reset,
        ComparatorMode comparator, boolean notices) implements CustomPacketPayload {
    public static final Type<ReceiverConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "receiver_config"));

    public static final StreamCodec<FriendlyByteBuf, ReceiverConfigPayload> STREAM_CODEC = StreamCodec.of(ReceiverConfigPayload::write, ReceiverConfigPayload::read);

    private static void write(FriendlyByteBuf buf, ReceiverConfigPayload p) {
        buf.writeVarInt(p.containerId);
        buf.writeResourceLocation(p.program);
        buf.writeOptional(p.offer, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(p.treasury);
        buf.writeBoolean(p.reset);
        buf.writeEnum(p.comparator);
        buf.writeBoolean(p.notices);
    }

    private static ReceiverConfigPayload read(FriendlyByteBuf buf) {
        return new ReceiverConfigPayload(buf.readVarInt(), buf.readResourceLocation(), buf.readOptional(FriendlyByteBuf::readResourceLocation), buf.readBoolean(),
                buf.readBoolean(), buf.readEnum(ComparatorMode.class), buf.readBoolean());
    }

    @Override
    public Type<ReceiverConfigPayload> type() {
        return TYPE;
    }
}
