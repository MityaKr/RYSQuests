package works.repka.rysquests.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.MapForm;

/**
 * An operator sets the form of the book's map (the form choice after the FTB Quests import, picture 07); {@link MapForm#AUTO}
 * leaves it to the rule. The server writes it into {@code book.json} and answers with {@link EditorResultPayload}.
 */
public record MapFormPayload(MapForm form) implements CustomPacketPayload {
    public static final Type<MapFormPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "map_form"));

    public static final StreamCodec<ByteBuf, MapFormPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(MapForm.CODEC), MapFormPayload::form,
            MapFormPayload::new);

    @Override
    public Type<MapFormPayload> type() {
        return TYPE;
    }
}
