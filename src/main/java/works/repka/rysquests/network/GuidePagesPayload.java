package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** The guide pages this player has read: all of them when {@code full}, otherwise only the new ones. */
public record GuidePagesPayload(boolean full, List<String> pages) implements CustomPacketPayload {
    public static final Type<GuidePagesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "guide_pages"));

    public static final StreamCodec<ByteBuf, GuidePagesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GuidePagesPayload::full,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), GuidePagesPayload::pages,
            GuidePagesPayload::new);

    @Override
    public Type<GuidePagesPayload> type() {
        return TYPE;
    }
}
