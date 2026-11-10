package works.repka.rysquests.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.SectionDef;

/**
 * What the editor saves: a guide or a section, its texts in the author's language and new pictures.
 * @param rail the new order of the sections on the rail, empty to leave it alone
 */
public record EditorSavePayload(Optional<Guide> guide, Optional<SectionDef> section, String language, Map<String, String> texts,
        Map<ResourceLocation, byte[]> images, List<String> rail) implements CustomPacketPayload {
    public static final Type<EditorSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "editor_save"));
    private static final int MAX_TEXT = 1 << 20;

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.fromCodec(Guide.CODEC)), EditorSavePayload::guide,
            ByteBufCodecs.optional(ByteBufCodecs.fromCodecWithRegistries(SectionDef.CODEC)), EditorSavePayload::section,
            ByteBufCodecs.STRING_UTF8, EditorSavePayload::language,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(MAX_TEXT), ByteBufCodecs.stringUtf8(MAX_TEXT)), EditorSavePayload::texts,
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.BYTE_ARRAY), EditorSavePayload::images,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), EditorSavePayload::rail,
            EditorSavePayload::new);

    @Override
    public Type<EditorSavePayload> type() {
        return TYPE;
    }
}
