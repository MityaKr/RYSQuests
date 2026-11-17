package works.repka.rysquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.block.DispenserView;

/** What the open window of a dispenser shows, sent only while it is open and only when it changed (shop plan, step M7). */
public record DispenserStatePayload(int containerId, DispenserView view) implements CustomPacketPayload {
    public static final Type<DispenserStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "dispenser_state"));

    public static final StreamCodec<FriendlyByteBuf, DispenserStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DispenserStatePayload::containerId,
            DispenserView.STREAM_CODEC, DispenserStatePayload::view,
            DispenserStatePayload::new);

    @Override
    public Type<DispenserStatePayload> type() {
        return TYPE;
    }
}
