package works.repka.rysquests.client.screen;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Who looks at the book. An operator ({@code hasPermissions(2)}, the server's own test) sees the editor, the publication lock and
 * the first-run buttons; a player sees none of them. Every page asks here rather than the player, so that a test can look through a
 * player's eyes in a world where it is an operator.
 */
public final class BookRole {
    @Nullable
    private static Boolean forced;

    private BookRole() {
    }

    public static boolean operator() {
        if (forced != null) {
            return forced;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    /** Pretends a role, or stops pretending with null. For tests only. */
    public static void forceForTest(@Nullable Boolean operator) {
        forced = operator;
    }
}
