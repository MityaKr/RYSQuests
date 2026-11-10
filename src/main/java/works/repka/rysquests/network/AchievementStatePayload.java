package works.repka.rysquests.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** How rare each tier is on this server, in percent, and the titles this player owns. */
public record AchievementStatePayload(Map<String, Integer> rarity, List<String> titles, String activeTitle) implements CustomPacketPayload {
    public static final Type<AchievementStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "achievement_state"));

    public static final StreamCodec<ByteBuf, AchievementStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Codec.unboundedMap(Codec.STRING, Codec.INT)), AchievementStatePayload::rarity,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AchievementStatePayload::titles,
            ByteBufCodecs.STRING_UTF8, AchievementStatePayload::activeTitle,
            AchievementStatePayload::new);

    @Override
    public Type<AchievementStatePayload> type() {
        return TYPE;
    }
}
