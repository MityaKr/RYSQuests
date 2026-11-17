package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/**
 * How a save went.
 *
 * @param reason the lang key of why it failed, empty on success; {@link #CONFLICT} means another operator changed the chapter first
 * @param args the arguments of the reason's text, such as the name of that operator
 */
public record EditorResultPayload(boolean ok, String reason, List<String> args) implements CustomPacketPayload {
    public static final Type<EditorResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "editor_result"));
    /** The chapter changed since the editing began: the book offers to load the new version or to save over it. */
    public static final String CONFLICT = "rysquests.editor.error.conflict";

    public static final StreamCodec<ByteBuf, EditorResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, EditorResultPayload::ok,
            ByteBufCodecs.STRING_UTF8, EditorResultPayload::reason,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), EditorResultPayload::args,
            EditorResultPayload::new);

    public EditorResultPayload(boolean ok, String reason) {
        this(ok, reason, List.of());
    }

    @Override
    public Type<EditorResultPayload> type() {
        return TYPE;
    }
}
