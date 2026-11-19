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

    public static final ModConfigSpec.BooleanValue HUD_COLLAPSED = BUILDER
            .comment("Pinned quests folded to their titles, without the lines of their tasks.")
            .define("hudCollapsed", false);

    public static final ModConfigSpec.BooleanValue COMPLETION_TOAST = BUILDER
            .comment("Show a message when a quest is completed.")
            .define("completionToast", true);

    public static final ModConfigSpec.BooleanValue TEAM_QUEST_TOASTS = BUILDER
            .comment("Show a message when a teammate completes a quest.")
            .define("teamQuestToasts", true);

    public static final ModConfigSpec.BooleanValue REWARD_SOUND = BUILDER
            .comment("Play a sound when a reward is taken.")
            .define("rewardSound", false);

    /** "Понятно" under the three lines about the book on "Now" at the first opening: they are not shown again (picture 12). */
    public static final ModConfigSpec.BooleanValue NOW_INTRO_SEEN = BUILDER
            .comment("The lines about the book on the first page were read and closed.")
            .define("nowIntroSeen", false);

    /** The hint of the book's key on the first entry was shown and the book opened since (picture 31b): it never comes back. */
    public static final ModConfigSpec.BooleanValue KEY_HINT_SHOWN = BUILDER
            .comment("The hint of the book's key on the first entry is done with.")
            .define("keyHintShown", false);

    /** "Побочные квесты" in the map's legend: side quests on the canvas. */
    public static final ModConfigSpec.BooleanValue MAP_SIDE_QUESTS = BUILDER
            .comment("Show side quests on the map.")
            .define("mapSideQuests", true);

    /** "Побочные связи" in the map's legend: dashed roads between chapters and the roads of side quests. */
    public static final ModConfigSpec.BooleanValue MAP_SIDE_LINKS = BUILDER
            .comment("Show the dashed roads between chapters and the roads of side quests on the map.")
            .define("mapSideLinks", true);

    /** "Все / Основные" over the sidebar: counts and the check of a finished chapter take side quests in too, or main ones only. */
    public static final ModConfigSpec.BooleanValue COUNT_ALL_QUESTS = BUILDER
            .comment("Count side quests too in the book's counts and finished chapters, not main quests only.")
            .define("countAllQuests", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
