package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** An operator opens the book to players or closes it again; the server answers with {@link EditorResultPayload}. */
public record PublishPayload(boolean open) implements CustomPacketPayload {
    public static final Type<PublishPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "publish"));

    public static final StreamCodec<ByteBuf, PublishPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PublishPayload::open,
            PublishPayload::new);

    @Override
    public Type<PublishPayload> type() {
        return TYPE;
    }
}
