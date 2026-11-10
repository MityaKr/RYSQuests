package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.PagesSection;

/** What the editor saves: one guide or one section of pages, its texts in the author's language and new pictures. */
public record EditorSavePayload(Optional<Guide> guide, Optional<PagesSection> section, String language, Map<String, String> texts,
        Map<ResourceLocation, byte[]> images) implements CustomPacketPayload {
    public static final Type<EditorSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "editor_save"));
    private static final int MAX_TEXT = 1 << 20;

    public static final StreamCodec<ByteBuf, EditorSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.fromCodec(Guide.CODEC)), EditorSavePayload::guide,
            ByteBufCodecs.optional(ByteBufCodecs.fromCodec(PagesSection.CODEC.codec())), EditorSavePayload::section,
            ByteBufCodecs.STRING_UTF8, EditorSavePayload::language,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(MAX_TEXT), ByteBufCodecs.stringUtf8(MAX_TEXT)), EditorSavePayload::texts,
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.BYTE_ARRAY), EditorSavePayload::images,
            EditorSavePayload::new);

    @Override
    public Type<EditorSavePayload> type() {
        return TYPE;
    }
}
