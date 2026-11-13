package works.repka.rysquests.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.progress.QuestProgress;

/**
 * Quest progress for the client: everything when {@code full}, otherwise only what changed.
 *
 * @param lastChapter the chapter of the quest this player last completed by their own doing, for the next step on "Now". In a
 *                    full update empty means none yet; in a partial one it means unchanged. It is the player's own, never a
 *                    teammate's.
 */
public record ProgressPayload(boolean full, Map<ResourceLocation, QuestProgress> quests, Optional<ResourceLocation> lastChapter) implements CustomPacketPayload {
    public static final Type<ProgressPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "progress"));

    public static final StreamCodec<ByteBuf, ProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ProgressPayload::full,
            ByteBufCodecs.fromCodec(Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC)), ProgressPayload::quests,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ProgressPayload::lastChapter,
            ProgressPayload::new);

    public ProgressPayload(boolean full, Map<ResourceLocation, QuestProgress> quests) {
        this(full, quests, Optional.empty());
    }

    @Override
    public Type<ProgressPayload> type() {
        return TYPE;
    }
}
