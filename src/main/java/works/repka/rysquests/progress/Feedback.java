package works.repka.rysquests.progress;

import java.util.Arrays;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import works.repka.rysquests.network.BookMessagePayload;

/** Quiet sounds that confirm a reward or a trade, heard only by the player who made it, and the book's message line. */
public final class Feedback {
    private static final float REWARD_VOLUME = 0.55f;
    private static final float TRADE_VOLUME = 0.3f;
    private static final float BUY_PITCH = 1.5f;
    private static final float SELL_PITCH = 1.12f;
    private static final float SUBMIT_PITCH = 1.3f;

    private Feedback() {
    }

    /** A refusal in words: the open book shows it in its message line, otherwise it goes above the hotbar. */
    public static void refuse(ServerPlayer player, String key, Object... args) {
        message(player, false, key, args);
    }

    /** What an action did, in words. */
    public static void inform(ServerPlayer player, String key, Object... args) {
        message(player, true, key, args);
    }

    private static void message(ServerPlayer player, boolean ok, String key, Object... args) {
        PacketDistributor.sendToPlayer(player, new BookMessagePayload(key, Arrays.stream(args).map(String::valueOf).toList(), ok));
    }

    public static void reward(ServerPlayer player) {
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, REWARD_VOLUME, 1f);
    }

    public static void submit(ServerPlayer player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, TRADE_VOLUME, SUBMIT_PITCH);
    }

    public static void trade(ServerPlayer player, boolean sale) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, TRADE_VOLUME, sale ? SELL_PITCH : BUY_PITCH);
    }
}
