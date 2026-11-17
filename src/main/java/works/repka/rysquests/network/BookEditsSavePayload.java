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
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.ShopOffer;

/**
 * The part of the editor's draft that is not a chapter, a guide or a tab: the book's settings (the map's form chosen on picture 36,
 * the title of a new book), shop offers (picture 39) and achievements (picture 39b). Answered with an {@link EditorResultPayload}.
 *
 * @param meta         the book's settings to write, if they changed; {@code published} is kept as the server has it
 * @param offers       shop offers to write, one file each
 * @param removed      shop offers to take out of the editor's pack
 * @param achievements achievement files as JSON text, the achievement with the {@code task} its tiers count
 * @param texts        strings in the author's language, only keys the files above name
 */
public record BookEditsSavePayload(Optional<BookMeta> meta, List<ShopOffer> offers, List<ResourceLocation> removed, List<String> achievements, String language,
        Map<String, String> texts) implements CustomPacketPayload {
    public static final Type<BookEditsSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "book_edits_save"));
    private static final int MAX_TEXT = 1 << 20;

    public static final StreamCodec<RegistryFriendlyByteBuf, BookEditsSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.fromCodec(BookMeta.CODEC)), BookEditsSavePayload::meta,
            ByteBufCodecs.fromCodecWithRegistries(ShopOffer.CODEC.listOf()), BookEditsSavePayload::offers,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(4096)), BookEditsSavePayload::removed,
            ByteBufCodecs.stringUtf8(MAX_TEXT).apply(ByteBufCodecs.list(4096)), BookEditsSavePayload::achievements,
            ByteBufCodecs.STRING_UTF8, BookEditsSavePayload::language,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(MAX_TEXT), ByteBufCodecs.stringUtf8(MAX_TEXT)), BookEditsSavePayload::texts,
            BookEditsSavePayload::new);

    @Override
    public Type<BookEditsSavePayload> type() {
        return TYPE;
    }
}
