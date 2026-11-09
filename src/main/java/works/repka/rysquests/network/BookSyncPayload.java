package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/** The whole book, gzipped. Oversized packets are split by NeoForge's packet splitter. */
public record BookSyncPayload(String hash, byte[] data) implements CustomPacketPayload {
    public static final Type<BookSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "book_sync"));

    public static final StreamCodec<ByteBuf, BookSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BookSyncPayload::hash,
            ByteBufCodecs.BYTE_ARRAY, BookSyncPayload::data,
            BookSyncPayload::new);

    @Override
    public Type<BookSyncPayload> type() {
        return TYPE;
    }
}
