package works.repka.rysquests.block;

import java.util.UUID;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.Nullable;

/**
 * Who may do what with an intake block (shop plan, section 4.4). The owner is the player who placed it; the team is looked up at the
 * moment, so a player who left the team loses the block's window at once and one who joined gets it. Anyone may put items in: that is
 * a gift to the owner. Mods' automation goes as a fake player and is turned away before it gets here.
 */
public final class ReceiverAccess {
    private ReceiverAccess() {
    }

    /**
     * Whether a player may open the window and change the settings: the owner and the members of the owner's team. A block without
     * an owner (set with a command) belongs to the first player who opens it.
     *
     * @param team the team of a player, the player alone when there is none
     */
    public static boolean mayUse(@Nullable UUID owner, UUID player, UnaryOperator<UUID> team) {
        return owner == null || owner.equals(player) || team.apply(owner).equals(team.apply(player));
    }

    /** Whether a player may break the block: whoever may use it, and an operator. */
    public static boolean mayBreak(@Nullable UUID owner, UUID player, UnaryOperator<UUID> team, boolean operator) {
        return operator || mayUse(owner, player, team);
    }

    /**
     * Whether the coins of a sale may go to the treasury: only when the owner is in a team. Putting coins into a treasury needs no
     * right (ADR-52); spending it, which the dispenser will do, does.
     */
    public static boolean mayChooseTreasury(@Nullable UUID owner, UnaryOperator<UUID> team) {
        // The same test the trade's core makes: a player whose team is themselves has no treasury.
        return owner != null && !team.apply(owner).equals(owner);
    }

    /**
     * Whether a dispenser may pay from the treasury (shop plan, section 4.4): the owner is in a team, and whoever switches it on has the
     * right to spend it. Once it is on, the trade's core asks the owner's own right at every purchase, so an owner who loses the right
     * or leaves the team stops the block at once.
     *
     * @param wasOn the block pays from the treasury already, so the setting is only kept
     * @param askerHasRight the player who changes the settings may spend the treasury
     */
    public static boolean mayPayFromTreasury(@Nullable UUID owner, UnaryOperator<UUID> team, boolean wasOn, boolean askerHasRight) {
        return mayChooseTreasury(owner, team) && (wasOn || askerHasRight);
    }
}
