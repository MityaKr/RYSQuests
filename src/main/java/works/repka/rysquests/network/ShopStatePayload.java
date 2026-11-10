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
import works.repka.rysquests.progress.ProgressStore;

/** A player's money, what the limits have used up and the latest operations. Small enough to send whole on each change. */
public record ShopStatePayload(Map<String, Long> wallet, Map<String, Long> treasury, boolean inTeam, Map<String, Integer> used, List<ProgressStore.ShopLine> history) implements CustomPacketPayload {
    public static final Type<ShopStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "shop_state"));

    public static final StreamCodec<ByteBuf, ShopStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Codec.unboundedMap(Codec.STRING, Codec.LONG)), ShopStatePayload::wallet,
            ByteBufCodecs.fromCodec(Codec.unboundedMap(Codec.STRING, Codec.LONG)), ShopStatePayload::treasury,
            ByteBufCodecs.BOOL, ShopStatePayload::inTeam,
            ByteBufCodecs.fromCodec(Codec.unboundedMap(Codec.STRING, Codec.INT)), ShopStatePayload::used,
            ByteBufCodecs.fromCodec(ProgressStore.ShopLine.CODEC.listOf()), ShopStatePayload::history,
            ShopStatePayload::new);

    @Override
    public Type<ShopStatePayload> type() {
        return TYPE;
    }
}
