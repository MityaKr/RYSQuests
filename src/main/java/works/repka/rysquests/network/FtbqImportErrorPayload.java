package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.integration.ftbq.FtbqFiles;

/**
 * The FTB Quests import stopped and wrote nothing (plan, picture 50): the file, the line and what was wrong. When the failure is
 * skippable the book offers "Carry over without this chapter", which starts again with the file in
 * {@link FtbqImportStartPayload#skip()}.
 *
 * @param folder the FTB Quests folder relative to the game folder, which the file is relative to
 * @param chaptersRead chapter files read before it stopped
 * @param chaptersTotal chapter files there are
 */
public record FtbqImportErrorPayload(FtbqFiles.Failure failure, String folder, int chaptersRead, int chaptersTotal) implements CustomPacketPayload {
    public static final Type<FtbqImportErrorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "ftbq_import_error"));

    public static final StreamCodec<ByteBuf, FtbqImportErrorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(FtbqFiles.Failure.CODEC), FtbqImportErrorPayload::failure,
            ByteBufCodecs.STRING_UTF8, FtbqImportErrorPayload::folder,
            ByteBufCodecs.VAR_INT, FtbqImportErrorPayload::chaptersRead,
            ByteBufCodecs.VAR_INT, FtbqImportErrorPayload::chaptersTotal,
            FtbqImportErrorPayload::new);

    @Override
    public Type<FtbqImportErrorPayload> type() {
        return TYPE;
    }
}
