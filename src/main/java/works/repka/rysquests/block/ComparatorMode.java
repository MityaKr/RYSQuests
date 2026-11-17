package works.repka.rysquests.block;

import java.util.Locale;

/**
 * What a comparator next to a shop block reads (shop plan, step M7), chosen in the block's window. The state is the default: it says
 * the same as the lamp ({@link ReceiverStatus#signal}). The fill is what the block holds: the dispenser's own slot, the intake block's
 * buffer, as a chest's fill is read.
 */
public enum ComparatorMode {
    STATE, FILL;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mode saved under a name, the state when the name is unknown. */
    public static ComparatorMode of(String name) {
        for (ComparatorMode mode : values()) {
            if (mode.key().equals(name)) {
                return mode;
            }
        }
        return STATE;
    }

    /** A fill from 0 to 1 as a chest gives it: 0 only when empty, 1 to 15 otherwise. */
    public static int fillSignal(double fill) {
        if (fill <= 0) {
            return 0;
        }
        return Math.min(15, 1 + (int) Math.floor(Math.min(1, fill) * 14));
    }
}
