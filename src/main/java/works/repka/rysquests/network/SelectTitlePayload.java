package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** Picks the title shown beside a player's name. An empty one takes it off. */
public record SelectTitlePayload(String title) implements CustomPacketPayload {
    public static final Type<SelectTitlePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "select_title"));

    public static final StreamCodec<ByteBuf, SelectTitlePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SelectTitlePayload::title,
            SelectTitlePayload::new);

    @Override
    public Type<SelectTitlePayload> type() {
        return TYPE;
    }
}
