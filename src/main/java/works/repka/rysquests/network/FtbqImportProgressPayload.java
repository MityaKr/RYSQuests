package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/**
 * How far the FTB Quests import has come.
 *
 * @param phase {@link #READ}, {@link #CONVERT}, {@link #WRITE}, {@link #RELOAD} or {@link #PROGRESS}
 * @param done how much of the phase is done
 * @param total how much there is, 0 when it is not known
 */
public record FtbqImportProgressPayload(String phase, int done, int total) implements CustomPacketPayload {
    public static final String READ = "read";
    public static final String CONVERT = "convert";
    public static final String WRITE = "write";
    public static final String RELOAD = "reload";
    public static final String PROGRESS = "progress";

    public static final Type<FtbqImportProgressPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "ftbq_import_progress"));

    public static final StreamCodec<ByteBuf, FtbqImportProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FtbqImportProgressPayload::phase,
            ByteBufCodecs.VAR_INT, FtbqImportProgressPayload::done,
            ByteBufCodecs.VAR_INT, FtbqImportProgressPayload::total,
            FtbqImportProgressPayload::new);

    @Override
    public Type<FtbqImportProgressPayload> type() {
        return TYPE;
    }
}
