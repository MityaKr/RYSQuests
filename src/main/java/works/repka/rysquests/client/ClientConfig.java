package works.repka.rysquests.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings. The theme is not here: its choice is shared by the whole mod family in RYSCore. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HUD_IN_COMBAT = BUILDER
            .comment("Keep pinned quests on screen while fighting.")
            .define("hudInCombat", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
