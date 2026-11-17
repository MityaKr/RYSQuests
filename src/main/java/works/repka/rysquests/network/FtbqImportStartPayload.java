package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/**
 * An operator starts the FTB Quests import from the book. "Retry" sends it again as it was; "Carry over without this chapter"
 * sends it with the broken file added to {@code skip}. The server answers with {@link FtbqImportProgressPayload} while it works,
 * then {@link FtbqImportReportPayload} or {@link FtbqImportErrorPayload}.
 *
 * @param namespace the namespace of the new book, {@code ftbq} when empty
 * @param skip files of the FTB book to leave out, as the error names them
 * @param withProgress also carry over the progress of FTB Quests teams in this world
 */
public record FtbqImportStartPayload(String namespace, List<String> skip, boolean withProgress) implements CustomPacketPayload {
    public static final Type<FtbqImportStartPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "ftbq_import_start"));

    public static final StreamCodec<ByteBuf, FtbqImportStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FtbqImportStartPayload::namespace,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(1024)), FtbqImportStartPayload::skip,
            ByteBufCodecs.BOOL, FtbqImportStartPayload::withProgress,
            FtbqImportStartPayload::new);

    @Override
    public Type<FtbqImportStartPayload> type() {
        return TYPE;
    }
}
