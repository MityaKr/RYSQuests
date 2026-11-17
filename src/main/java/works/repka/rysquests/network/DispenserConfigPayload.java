package works.repka.rysquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.block.DispenserSettings;

/**
 * The settings a dispenser's window asks for (shop plan, step M7), whole, and whether to reset the counters. The server checks all of
 * it: who asks, that the trade exists and a dispenser can buy it, that the treasury may be used, the bounds of every number.
 */
public record DispenserConfigPayload(int containerId, DispenserSettings settings, boolean reset) implements CustomPacketPayload {
    public static final Type<DispenserConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "dispenser_config"));

    public static final StreamCodec<FriendlyByteBuf, DispenserConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DispenserConfigPayload::containerId,
            DispenserSettings.STREAM_CODEC, DispenserConfigPayload::settings,
            ByteBufCodecs.BOOL, DispenserConfigPayload::reset,
            DispenserConfigPayload::new);

    @Override
    public Type<DispenserConfigPayload> type() {
        return TYPE;
    }
}
