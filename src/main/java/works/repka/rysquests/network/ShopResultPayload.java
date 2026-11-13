package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** How a trade went. @param reason the lang key of why it was refused, empty on success @param args the arguments of its text */
public record ShopResultPayload(boolean ok, String reason, List<String> args) implements CustomPacketPayload {
    public static final Type<ShopResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "shop_result"));

    public static final StreamCodec<ByteBuf, ShopResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ShopResultPayload::ok,
            ByteBufCodecs.STRING_UTF8, ShopResultPayload::reason,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), ShopResultPayload::args,
            ShopResultPayload::new);

    public ShopResultPayload(boolean ok, String reason) {
        this(ok, reason, List.of());
    }

    @Override
    public Type<ShopResultPayload> type() {
        return TYPE;
    }
}
