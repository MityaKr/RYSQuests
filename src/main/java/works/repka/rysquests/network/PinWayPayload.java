package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** Pins a way of a matrix section to the player's screen, or unpins it. */
public record PinWayPayload(String key) implements CustomPacketPayload {
    public static final Type<PinWayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "pin_way"));

    public static final StreamCodec<ByteBuf, PinWayPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PinWayPayload::key,
            PinWayPayload::new);

    @Override
    public Type<PinWayPayload> type() {
        return TYPE;
    }
}
