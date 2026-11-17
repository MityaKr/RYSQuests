package works.repka.rysquests.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
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
 * @param done        for the quests this update completes: who did it and what became of the reward, for the message about them.
 *                    Empty in a full update.
 */
public record ProgressPayload(boolean full, Map<ResourceLocation, QuestProgress> quests, Optional<ResourceLocation> lastChapter,
        Map<ResourceLocation, Done> done) implements CustomPacketPayload {
    public static final Type<ProgressPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "progress"));

    public static final StreamCodec<ByteBuf, ProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ProgressPayload::full,
            ByteBufCodecs.fromCodec(Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC)), ProgressPayload::quests,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ProgressPayload::lastChapter,
            ByteBufCodecs.map(java.util.HashMap::new, ResourceLocation.STREAM_CODEC, Done.STREAM_CODEC), ProgressPayload::done,
            ProgressPayload::new);

    public ProgressPayload(boolean full, Map<ResourceLocation, QuestProgress> quests) {
        this(full, quests, Optional.empty(), Map.of());
    }

    public ProgressPayload(boolean full, Map<ResourceLocation, QuestProgress> quests, Optional<ResourceLocation> lastChapter) {
        this(full, quests, lastChapter, Map.of());
    }

    /**
     * A quest this update completed.
     *
     * @param by     the player whose doing completed it; empty for an operator's command
     * @param name   that player's name, for "Выполнил Stepan_K"
     * @param reward what became of the reward for the player this goes to: {@link #NONE}, {@link #WAITING}, {@link #RECEIVED} (given at
     *               once, as achievement tiers are) or {@link #TEAM_TAKEN} (only one of the team could take it and it is taken)
     */
    public record Done(Optional<UUID> by, String name, int reward) {
        public static final int NONE = 0, WAITING = 1, RECEIVED = 2, TEAM_TAKEN = 3;

        public static final StreamCodec<ByteBuf, Done> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Done::by,
                ByteBufCodecs.STRING_UTF8, Done::name,
                ByteBufCodecs.VAR_INT, Done::reward,
                Done::new);
    }

    @Override
    public Type<ProgressPayload> type() {
        return TYPE;
    }
}
