package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** A request to deliver what the inventory holds towards a delivery trade (shop plan, step M4). The server decides how much it takes. */
public record ShopDeliverPayload(ResourceLocation offer) implements CustomPacketPayload {
    public static final Type<ShopDeliverPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "shop_deliver"));

    public static final StreamCodec<ByteBuf, ShopDeliverPayload> STREAM_CODEC = ResourceLocation.STREAM_CODEC.map(ShopDeliverPayload::new, ShopDeliverPayload::offer);

    @Override
    public Type<ShopDeliverPayload> type() {
        return TYPE;
    }
}
