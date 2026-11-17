package works.repka.rysquests.network;

import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/**
 * The settings an intake block's window asks for (shop plan, step M6): the mode, the trade, where the coins go, and whether to reset
 * the counters. The server checks all of it: who asks, that the trade exists and fits the mode, that there is a treasury.
 */
public record ReceiverConfigPayload(int containerId, ResourceLocation program, Optional<ResourceLocation> offer, boolean treasury, boolean reset)
        implements CustomPacketPayload {
    public static final Type<ReceiverConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "receiver_config"));

    public static final StreamCodec<FriendlyByteBuf, ReceiverConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReceiverConfigPayload::containerId,
            ResourceLocation.STREAM_CODEC, ReceiverConfigPayload::program,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ReceiverConfigPayload::offer,
            ByteBufCodecs.BOOL, ReceiverConfigPayload::treasury,
            ByteBufCodecs.BOOL, ReceiverConfigPayload::reset,
            ReceiverConfigPayload::new);

    @Override
    public Type<ReceiverConfigPayload> type() {
        return TYPE;
    }
}
