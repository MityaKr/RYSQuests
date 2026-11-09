package works.repka.rysquests.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.progress.QuestProgress;

/** Quest progress for the client: everything when {@code full}, otherwise only what changed. */
public record ProgressPayload(boolean full, Map<ResourceLocation, QuestProgress> quests) implements CustomPacketPayload {
    public static final Type<ProgressPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "progress"));

    public static final StreamCodec<ByteBuf, ProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ProgressPayload::full,
            ByteBufCodecs.fromCodec(Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC)), ProgressPayload::quests,
            ProgressPayload::new);

    @Override
    public Type<ProgressPayload> type() {
        return TYPE;
    }
}
