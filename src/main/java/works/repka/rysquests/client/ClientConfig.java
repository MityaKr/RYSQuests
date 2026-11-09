package works.repka.rysquests.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings. The theme is not here: its choice is shared by the whole mod family in RYSCore. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HUD_IN_COMBAT = BUILDER
            .comment("Keep pinned quests on screen while fighting.")
            .define("hudInCombat", true);

    public static final ModConfigSpec.IntValue BOARD_TOP = BUILDER
            .comment("Distance of the sticker board from the top of the screen, in mockup pixels. Raise it if a minimap sits in that corner.")
            .defineInRange("boardTop", 20, 0, 2000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
