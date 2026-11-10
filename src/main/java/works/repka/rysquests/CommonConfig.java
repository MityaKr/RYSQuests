package works.repka.rysquests;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Settings that the client and the server both read. */
public final class CommonConfig {
    public static final String DEFAULT_EDITOR_FOLDER = "config/rysquests/pack";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> EDITOR_FOLDER = BUILDER
            .comment("Where the in-game editor writes, relative to the game folder. It holds data/ and assets/ like a pack. Use \"kubejs\" to keep the book in the KubeJS folders.")
            .define("editorFolder", DEFAULT_EDITOR_FOLDER);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }
}
