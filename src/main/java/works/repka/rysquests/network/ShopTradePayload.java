package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** A request to make a trade. The server checks everything. */
public record ShopTradePayload(ResourceLocation offer, int times, boolean treasury) implements CustomPacketPayload {
    public static final Type<ShopTradePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "shop_trade"));

    public static final StreamCodec<ByteBuf, ShopTradePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ShopTradePayload::offer,
            ByteBufCodecs.VAR_INT, ShopTradePayload::times,
            ByteBufCodecs.BOOL, ShopTradePayload::treasury,
            ShopTradePayload::new);

    @Override
    public Type<ShopTradePayload> type() {
        return TYPE;
    }
}
