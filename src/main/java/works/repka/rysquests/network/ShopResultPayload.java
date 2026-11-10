package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** How a trade went. @param reason the lang key of why it failed, empty on success */
public record ShopResultPayload(boolean ok, String reason) implements CustomPacketPayload {
    public static final Type<ShopResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "shop_result"));

    public static final StreamCodec<ByteBuf, ShopResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ShopResultPayload::ok,
            ByteBufCodecs.STRING_UTF8, ShopResultPayload::reason,
            ShopResultPayload::new);

    @Override
    public Type<ShopResultPayload> type() {
        return TYPE;
    }
}
