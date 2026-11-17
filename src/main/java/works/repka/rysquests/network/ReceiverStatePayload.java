package works.repka.rysquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.block.ReceiverView;

/** What the open window of an intake block shows, sent only while it is open and only when it changed (shop plan, step M6). */
public record ReceiverStatePayload(int containerId, ReceiverView view) implements CustomPacketPayload {
    public static final Type<ReceiverStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "receiver_state"));

    public static final StreamCodec<FriendlyByteBuf, ReceiverStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReceiverStatePayload::containerId,
            ReceiverView.STREAM_CODEC, ReceiverStatePayload::view,
            ReceiverStatePayload::new);

    @Override
    public Type<ReceiverStatePayload> type() {
        return TYPE;
    }
}
