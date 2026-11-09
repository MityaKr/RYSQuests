package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** The player's pinned quests, in order. */
public record PinnedPayload(List<ResourceLocation> quests) implements CustomPacketPayload {
    public static final Type<PinnedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "pinned"));

    public static final StreamCodec<ByteBuf, PinnedPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(64)), PinnedPayload::quests,
            PinnedPayload::new);

    @Override
    public Type<PinnedPayload> type() {
        return TYPE;
    }
}
