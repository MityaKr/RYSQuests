package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.MapForm;

/**
 * An operator makes a book from scratch ("Новая книга", picture 09a): its name, an icon and the form the answer to "Как устроена
 * сборка?" picked. The book starts closed to players. The server refuses when the book already has quests, writes {@code book.json}
 * and answers with {@link EditorResultPayload}.
 */
public record NewBookPayload(String title, Optional<ResourceLocation> icon, MapForm form) implements CustomPacketPayload {
    public static final int MAX_TITLE = 64;
    public static final Type<NewBookPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "new_book"));

    public static final StreamCodec<ByteBuf, NewBookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_TITLE * 4), NewBookPayload::title,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), NewBookPayload::icon,
            ByteBufCodecs.fromCodec(MapForm.CODEC), NewBookPayload::form,
            NewBookPayload::new);

    @Override
    public Type<NewBookPayload> type() {
        return TYPE;
    }
}
