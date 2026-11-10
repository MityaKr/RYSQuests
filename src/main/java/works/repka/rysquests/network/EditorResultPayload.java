package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** How a save went. @param reason the lang key of why it failed, empty on success */
public record EditorResultPayload(boolean ok, String reason) implements CustomPacketPayload {
    public static final Type<EditorResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "editor_result"));

    public static final StreamCodec<ByteBuf, EditorResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, EditorResultPayload::ok,
            ByteBufCodecs.STRING_UTF8, EditorResultPayload::reason,
            EditorResultPayload::new);

    @Override
    public Type<EditorResultPayload> type() {
        return TYPE;
    }
}
