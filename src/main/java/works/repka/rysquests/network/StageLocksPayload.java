package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/**
 * Quests and guides closed to the player because a stage is missing, and which of the stages named by custom sections
 * the player has: the client has no way to tell by itself.
 */
public record StageLocksPayload(List<ResourceLocation> quests, List<ResourceLocation> guides, List<String> openStages) implements CustomPacketPayload {
    public static final Type<StageLocksPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "stage_locks"));

    public static final StreamCodec<ByteBuf, StageLocksPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), StageLocksPayload::quests,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), StageLocksPayload::guides,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), StageLocksPayload::openStages,
            StageLocksPayload::new);

    @Override
    public Type<StageLocksPayload> type() {
        return TYPE;
    }
}
