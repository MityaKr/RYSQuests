package works.repka.rysquests.block;

import org.jetbrains.annotations.Nullable;

/**
 * What the intake block is doing (shop plan, section 4.3), shown three ways that always agree: the lamp of the block, the status line
 * of its window and, with the dispenser (step M7), the comparator.
 */
public enum ReceiverStatus {
    WORKING(ReceiverLamp.GREEN),
    /** The trade costs coins as well, and the wallet is short: items are still taken, up to the buffer. */
    WAITING(ReceiverLamp.YELLOW),
    /** Every row of the delivery is full: nothing more is taken until the goods are taken in the shop. */
    FULL(ReceiverLamp.YELLOW),
    /** Coins go to the treasury, and the owner is in no team. */
    NO_TEAM(ReceiverLamp.RED),
    /** The trade was removed from the book, is closed to the owner's team, or the book is closed. */
    CLOSED(ReceiverLamp.RED),
    /** The trade's limit is used up. */
    LIMIT(ReceiverLamp.RED),
    NO_OFFER(ReceiverLamp.OFF),
    NO_OWNER(ReceiverLamp.OFF);

    private final ReceiverLamp lamp;

    ReceiverStatus(ReceiverLamp lamp) {
        this.lamp = lamp;
    }

    public ReceiverLamp lamp() {
        return lamp;
    }

    /** Whether items are taken at all: a red or a dark block takes nothing, so a pipe never pours into a trade that cannot be made. */
    public boolean takes() {
        return lamp == ReceiverLamp.GREEN || this == WAITING;
    }

    /** The lang key of the status line. */
    public String key() {
        return "rysquests.receiver.status." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** What a refusal of the trade's core means for the block. @param problem the lang key of the refusal, or null when it went */
    public static ReceiverStatus of(@Nullable String problem) {
        if (problem == null) {
            return WORKING;
        }
        return switch (problem) {
            case "rysquests.shop.error.not_enough" -> WAITING;
            case "rysquests.shop.error.delivery_full" -> FULL;
            case "rysquests.shop.error.no_team", "rysquests.shop.error.no_right" -> NO_TEAM;
            case "rysquests.shop.error.limit", "rysquests.shop.error.bought", "rysquests.shop.error.team_bought" -> LIMIT;
            default -> CLOSED;
        };
    }
}
