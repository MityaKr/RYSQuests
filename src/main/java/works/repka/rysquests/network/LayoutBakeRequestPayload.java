package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/**
 * The server asks an operator's client to lay the map out and send it back with {@link LayoutSavePayload}: the answer to
 * {@code /rysquests layout bake}. Only a client can lay the map out.
 *
 * @param layoutHash the {@code layoutHash} of the book the server holds, so that a client still holding an older book can wait
 */
public record LayoutBakeRequestPayload(String layoutHash) implements CustomPacketPayload {
    public static final Type<LayoutBakeRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "layout_bake"));

    public static final StreamCodec<ByteBuf, LayoutBakeRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LayoutBakeRequestPayload::layoutHash,
            LayoutBakeRequestPayload::new);

    @Override
    public Type<LayoutBakeRequestPayload> type() {
        return TYPE;
    }
}
