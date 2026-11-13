package works.repka.rysquests.client.screen;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The ring around an achievement's icon (mockup {@code .medal}, picture 28): 3 wide in the color of the highest level reached, grey
 * while none is; a secret achievement has no icon yet. Sizes in the mockups: 40 on a card, 56 in the detail panel, 14 in a level row.
 *
 * <pre>
 * AchievementRing.render(ui, x, y, 40, stack, reached >= 0 ? LevelSegments.token(reached, tiers) : null);
 * </pre>
 */
final class AchievementRing {
    private static final float STROKE = 3;

    private AchievementRing() {
    }

    /** @param medalToken the token of the highest level reached ({@link LevelSegments#token}), or null for none yet */
    static void render(Ui ui, float x, float y, float size, @Nullable ItemStack icon, @Nullable String medalToken) {
        ui.circle(x, y, size, ui.c("slot.bg"));
        ui.ring(x, y, size, STROKE, ui.c(medalToken != null ? medalToken : "slot.border"));
        if (icon != null && !icon.isEmpty()) {
            float inner = Math.round(size * 0.6f);
            ui.item(icon, x + (size - inner) / 2f, y + (size - inner) / 2f, inner);
        }
    }

    /** A small ring with nothing inside: the level rows of the detail panel and the totals ("○ 5 ○ 3 ○ 2"). */
    static void dot(Ui ui, float x, float y, float size, String medalToken) {
        ui.ring(x, y, size, Math.max(2, size * 0.2f), ui.c(medalToken));
    }
}
