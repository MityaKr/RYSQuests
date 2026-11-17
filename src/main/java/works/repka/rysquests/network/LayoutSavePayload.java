package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.BookLayout;

/**
 * The map an operator's client laid out, to be written as {@code rysquests/layout.json} and sent to everyone with the book. It is
 * sent by "Save" in the editor, by "Save layout" in the check, and in answer to {@link LayoutBakeRequestPayload}. The server
 * answers with {@link EditorResultPayload}.
 */
public record LayoutSavePayload(BookLayout layout) implements CustomPacketPayload {
    public static final Type<LayoutSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "layout_save"));

    public static final StreamCodec<ByteBuf, LayoutSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(BookLayout.CODEC), LayoutSavePayload::layout,
            LayoutSavePayload::new);

    @Override
    public Type<LayoutSavePayload> type() {
        return TYPE;
    }
}
