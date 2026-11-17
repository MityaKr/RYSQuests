package works.repka.rysquests.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.progress.ProgressStore;

/**
 * A player's money, what the limits have used up and the latest operations. Small enough to send whole on each change.
 *
 * @param spendTreasury whether the player's team lets them pay from its treasury
 * @param delivered what the player's team (or the player alone) has delivered towards each delivery trade, by item
 */
public record ShopStatePayload(Map<String, Long> wallet, Map<String, Long> treasury, boolean inTeam, Map<String, Integer> used, List<ProgressStore.ShopLine> history,
        boolean spendTreasury, Map<String, Map<String, Long>> delivered) implements CustomPacketPayload {
    public static final Type<ShopStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "shop_state"));

    private static final StreamCodec<ByteBuf, Map<String, Long>> MONEY = ByteBufCodecs.fromCodec(Codec.unboundedMap(Codec.STRING, Codec.LONG));
    private static final StreamCodec<ByteBuf, Map<String, Integer>> USED = ByteBufCodecs.fromCodec(Codec.unboundedMap(Codec.STRING, Codec.INT));
    private static final StreamCodec<ByteBuf, List<ProgressStore.ShopLine>> HISTORY = ByteBufCodecs.fromCodec(ProgressStore.ShopLine.CODEC.listOf());
    private static final StreamCodec<ByteBuf, Map<String, Map<String, Long>>> DELIVERED = ByteBufCodecs.fromCodec(
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.LONG)));

    /** Seven fields, one more than {@code StreamCodec.composite} takes, so written out by hand in the same order. */
    public static final StreamCodec<ByteBuf, ShopStatePayload> STREAM_CODEC = StreamCodec.of((buf, state) -> {
        MONEY.encode(buf, state.wallet());
        MONEY.encode(buf, state.treasury());
        ByteBufCodecs.BOOL.encode(buf, state.inTeam());
        USED.encode(buf, state.used());
        HISTORY.encode(buf, state.history());
        ByteBufCodecs.BOOL.encode(buf, state.spendTreasury());
        DELIVERED.encode(buf, state.delivered());
    }, buf -> new ShopStatePayload(MONEY.decode(buf), MONEY.decode(buf), ByteBufCodecs.BOOL.decode(buf), USED.decode(buf), HISTORY.decode(buf),
            ByteBufCodecs.BOOL.decode(buf), DELIVERED.decode(buf)));

    /** A state where being in a team means the treasury may be spent too, and nothing is delivered. */
    public ShopStatePayload(Map<String, Long> wallet, Map<String, Long> treasury, boolean inTeam, Map<String, Integer> used, List<ProgressStore.ShopLine> history) {
        this(wallet, treasury, inTeam, used, history, inTeam, Map.of());
    }

    /** A state with nothing delivered. */
    public ShopStatePayload(Map<String, Long> wallet, Map<String, Long> treasury, boolean inTeam, Map<String, Integer> used, List<ProgressStore.ShopLine> history,
            boolean spendTreasury) {
        this(wallet, treasury, inTeam, used, history, spendTreasury, Map.of());
    }

    @Override
    public Type<ShopStatePayload> type() {
        return TYPE;
    }
}
