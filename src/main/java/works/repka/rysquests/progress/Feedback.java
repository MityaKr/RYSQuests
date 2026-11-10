package works.repka.rysquests.progress;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Quiet sounds that confirm a reward or a trade, heard only by the player who made it. */
public final class Feedback {
    private static final float REWARD_VOLUME = 0.55f;
    private static final float TRADE_VOLUME = 0.3f;
    private static final float BUY_PITCH = 1.5f;
    private static final float SELL_PITCH = 1.12f;

    private Feedback() {
    }

    public static void reward(ServerPlayer player) {
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, REWARD_VOLUME, 1f);
    }

    public static void trade(ServerPlayer player, boolean sale) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, TRADE_VOLUME, sale ? SELL_PITCH : BUY_PITCH);
    }
}
