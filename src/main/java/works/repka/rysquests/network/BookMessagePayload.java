package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;

/**
 * A word from the server about something the player did: a reward that was not given, the limit of pinned quests, what a bulk
 * hand-in did. The open book shows it as its one message line (plan, picture 48); without the book it goes above the hotbar.
 *
 * @param key the lang key of the text
 * @param args the arguments of the text
 * @param ok whether it reports success rather than a refusal
 */
public record BookMessagePayload(String key, List<String> args, boolean ok) implements CustomPacketPayload {
    public static final Type<BookMessagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "book_message"));

    public static final StreamCodec<ByteBuf, BookMessagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BookMessagePayload::key,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), BookMessagePayload::args,
            ByteBufCodecs.BOOL, BookMessagePayload::ok,
            BookMessagePayload::new);

    @Override
    public Type<BookMessagePayload> type() {
        return TYPE;
    }
}
