package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.dev.DevReports;

/** The book report for the Dev tab. Sent only to operators. */
public record DevReportPayload(DevReports.Report report) implements CustomPacketPayload {
    public static final Type<DevReportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "dev_report"));

    public static final StreamCodec<ByteBuf, DevReportPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(DevReports.Report.CODEC), DevReportPayload::report,
            DevReportPayload::new);

    @Override
    public Type<DevReportPayload> type() {
        return TYPE;
    }
}
