package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.integration.ftbq.ImportReport;

/** The FTB Quests import is done and the new book is loaded: what it carried over, by kind (plan, picture 06). */
public record FtbqImportReportPayload(ImportReport report) implements CustomPacketPayload {
    public static final Type<FtbqImportReportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "ftbq_import_report"));

    public static final StreamCodec<ByteBuf, FtbqImportReportPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(ImportReport.CODEC), FtbqImportReportPayload::report,
            FtbqImportReportPayload::new);

    @Override
    public Type<FtbqImportReportPayload> type() {
        return TYPE;
    }
}
