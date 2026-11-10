package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/**
 * A player's request. {@code value} is the task index for a checkbox; {@code choices} holds, per reward, the picked
 * option of a choice reward or -1. The server checks everything again.
 */
public record QuestActionPayload(int action, ResourceLocation quest, int value, List<Integer> choices) implements CustomPacketPayload {
    public static final int SUBMIT = 0;
    public static final int CHECK = 1;
    public static final int CLAIM = 2;
    public static final int SUBMIT_ALL = 3;
    public static final int CLAIM_ALL = 4;
    public static final int PIN = 5;
    public static final int DEV_REPORT = 6;
    /** {@code quest} is the guide and {@code value} the page index. */
    public static final int READ_GUIDE_PAGE = 7;
    /** Quest id for actions that apply to the whole book. */
    public static final ResourceLocation ALL = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "all");

    public static final Type<QuestActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "quest_action"));

    public static final StreamCodec<ByteBuf, QuestActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestActionPayload::action,
            ResourceLocation.STREAM_CODEC, QuestActionPayload::quest,
            ByteBufCodecs.VAR_INT, QuestActionPayload::value,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), QuestActionPayload::choices,
            QuestActionPayload::new);

    @Override
    public Type<QuestActionPayload> type() {
        return TYPE;
    }
}
