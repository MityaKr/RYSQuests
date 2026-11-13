package works.repka.rysquests.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings. The theme is not here: its choice is shared by the whole mod family in RYSCore. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HUD_IN_COMBAT = BUILDER
            .comment("Keep pinned quests on screen while fighting.")
            .define("hudInCombat", true);

    /** Where the book opens: on "Now" every time, or where it was closed. The first opening of a game session is always "Now". */
    public static final ModConfigSpec.BooleanValue OPEN_WHERE_CLOSED = BUILDER
            .comment("Open the book where it was closed rather than on its first tab.")
            .define("openWhereClosed", true);

    public static final ModConfigSpec.BooleanValue HUD_PINNED = BUILDER
            .comment("Show pinned quests on screen.")
            .define("hudPinned", true);

    public static final ModConfigSpec.BooleanValue HUD_RIGHT = BUILDER
            .comment("Pinned quests on the right side of the screen instead of the left.")
            .define("hudRight", false);

    public static final ModConfigSpec.BooleanValue COMPLETION_TOAST = BUILDER
            .comment("Show a message when a quest is completed.")
            .define("completionToast", true);

    public static final ModConfigSpec.BooleanValue REWARD_SOUND = BUILDER
            .comment("Play a sound when a reward is taken.")
            .define("rewardSound", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
