package works.repka.rysquests.shop;

import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.stage.Stages;

/**
 * Who a trade is made for (shop plan, step M5): a player at the book, or the owner of a shop block, who may well be offline. Everything
 * the trade asks about them goes by id: their team, the treasury's right, the trade's quest. Only a stage needs the player in the
 * world, so a trade behind a stage is closed to a block while its owner is away (plan, section 4.4).
 *
 * @param owner whose wallet, team and history the trade is
 * @param treasury the coins come from and go to the team's treasury rather than the owner's own wallet
 * @param stages whether the owner has a stage
 * @param maxTimes how many times one request may make the trade
 * @param summary the trade adds to one line of the history that sums up what the block does, instead of a line of its own
 */
public record Trader(UUID owner, boolean treasury, Predicate<String> stages, int maxTimes, boolean summary) {
    /** A player at the book trades a stack at most at once, as before. */
    public static final int PLAYER_MAX_TIMES = 64;

    public static Trader player(ServerPlayer player, boolean treasury) {
        return new Trader(player.getUUID(), treasury, stage -> Stages.has(player, stage), PLAYER_MAX_TIMES, false);
    }

    /**
     * A block trading on its owner's behalf: as many times as its money, its goods and the room allow, written to the history as one
     * running line. @param online the owner when they are in the world, for the stages
     */
    public static Trader owner(UUID owner, @Nullable ServerPlayer online, boolean treasury) {
        return new Trader(owner, treasury, online == null ? stage -> false : stage -> Stages.has(online, stage), Integer.MAX_VALUE, true);
    }
}
