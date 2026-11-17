package works.repka.rysquests.block;

import net.minecraft.util.StringRepresentable;

/** The colour of the lamp on the intake block (shop plan, section 4.3): the same thing the status line of its window says. */
public enum ReceiverLamp implements StringRepresentable {
    /** Nothing to do: no trade chosen, or no owner. */
    OFF("off"),
    /** Works. */
    GREEN("green"),
    /** Waits: short of coins, or the delivery is full. Nothing is lost, it goes on by itself. */
    YELLOW("yellow"),
    /** Stopped until someone changes something: the trade is closed or used up, the owner has no team for the treasury. */
    RED("red");

    private final String name;

    ReceiverLamp(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
