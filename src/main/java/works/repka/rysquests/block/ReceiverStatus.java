package works.repka.rysquests.block;

import org.jetbrains.annotations.Nullable;

/**
 * What a shop block is doing (shop plan, section 4.3), shown three ways that always agree: the lamp of the block, the status line of
 * its window and the comparator. The intake block and the dispenser share it: each uses the states that can happen to it.
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
    NO_OWNER(ReceiverLamp.OFF),
    /** The dispenser pays from the treasury, and the owner lacks the right to spend it (ADR-52). */
    NO_RIGHT(ReceiverLamp.RED),
    /** The dispenser has nowhere to put the goods: no inventory on its side, a full one, or its own slot is full. Nothing is paid. */
    NO_SPACE(ReceiverLamp.YELLOW),
    /** The dispenser's stock is made up: the neighbour holds as many as "keep at most" says. The block's aim, so no notice. */
    STOCKED(ReceiverLamp.YELLOW),
    /** The dispenser spent all its budget since the counter was last reset. */
    BUDGET(ReceiverLamp.YELLOW),
    /** One more lot would take the balance below what "keep on the account" says. */
    RESERVE(ReceiverLamp.YELLOW),
    /** The dispenser works only on a redstone signal, and there is none. */
    REDSTONE(ReceiverLamp.OFF);

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

    /** The lang key of the intake block's status line. */
    public String key() {
        return key("receiver");
    }

    /** The lang key of a block's status line. @param block "receiver" or "dispenser" */
    public String key(String block) {
        return "rysquests." + block + ".status." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The comparator's level when it reads the state: 15 while the block works, 10 while it waits, 5 when it stopped, 0 when it is off.
     * A comparator after the block can so tell "working" from "stuck" with one more comparator in subtract mode.
     */
    public int signal() {
        return switch (lamp) {
            case GREEN -> 15;
            case YELLOW -> 10;
            case RED -> 5;
            case OFF -> 0;
        };
    }

    /**
     * Whether the owner hears of it when the block comes to this state (decision on question 17 of the plan): every yellow and red
     * state but a made-up stock, which is what the owner asked for.
     */
    public boolean notifies() {
        return (lamp == ReceiverLamp.YELLOW || lamp == ReceiverLamp.RED) && this != STOCKED;
    }

    /** What a refusal of the trade's core means for the block. @param problem the lang key of the refusal, or null when it went */
    public static ReceiverStatus of(@Nullable String problem) {
        if (problem == null) {
            return WORKING;
        }
        return switch (problem) {
            case "rysquests.shop.error.not_enough" -> WAITING;
            case "rysquests.shop.error.delivery_full" -> FULL;
            case "rysquests.shop.error.no_team" -> NO_TEAM;
            case "rysquests.shop.error.no_right" -> NO_RIGHT;
            case "rysquests.shop.error.no_space" -> NO_SPACE;
            case "rysquests.shop.error.limit", "rysquests.shop.error.bought", "rysquests.shop.error.team_bought" -> LIMIT;
            default -> CLOSED;
        };
    }
}
