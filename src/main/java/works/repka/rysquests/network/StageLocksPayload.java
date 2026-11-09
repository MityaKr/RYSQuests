package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** Quests the player cannot start because a stage is missing: the client has no way to tell by itself. */
public record StageLocksPayload(List<ResourceLocation> quests) implements CustomPacketPayload {
    public static final Type<StageLocksPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "stage_locks"));

    public static final StreamCodec<ByteBuf, StageLocksPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), StageLocksPayload::quests,
            StageLocksPayload::new);

    @Override
    public Type<StageLocksPayload> type() {
        return TYPE;
    }
}
