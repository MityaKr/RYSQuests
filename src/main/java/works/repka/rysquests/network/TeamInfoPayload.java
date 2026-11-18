package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/**
 * How many players are in this player's team and its name, for the line of the team on "Сейчас".
 *
 * @param name the team's name as the team mod gives it (RYSCore {@code TeamsApi.name}); empty for a player alone
 */
public record TeamInfoPayload(int members, String name) implements CustomPacketPayload {
    /** A team name longer than this is cut: the line has room for a few words only. */
    public static final int MAX_NAME = 64;

    public static final Type<TeamInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "team_info"));

    public static final StreamCodec<ByteBuf, TeamInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TeamInfoPayload::members,
            ByteBufCodecs.stringUtf8(MAX_NAME), TeamInfoPayload::name,
            TeamInfoPayload::new);

    public TeamInfoPayload {
        name = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    @Override
    public Type<TeamInfoPayload> type() {
        return TYPE;
    }
}
