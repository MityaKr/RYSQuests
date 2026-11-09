package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** How many players are in this player's team, for the chip in the top bar of the book. */
public record TeamInfoPayload(int members) implements CustomPacketPayload {
    public static final Type<TeamInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "team_info"));

    public static final StreamCodec<ByteBuf, TeamInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TeamInfoPayload::members,
            TeamInfoPayload::new);

    @Override
    public Type<TeamInfoPayload> type() {
        return TYPE;
    }
}
