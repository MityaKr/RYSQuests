package works.repka.rysquests.client.screen;

import works.repka.rysquests.data.Achievement;

/**
 * The levels of an achievement as segments (mockup {@code .lv}, picture 28): one segment per level, the current one filled in
 * part. A level's color comes from its place, not its name: the last is gold, the one before silver, every earlier one bronze
 * ({@link Achievement#medal(int, int)}). A level that is reached gets a border in its color.
 *
 * <pre>
 * LevelSegments.render(ui, x, y, w, new float[] {1f, 0.37f, 0f});   // the wide row of a card: segments share the width
 * </pre>
 */
final class LevelSegments {
    static final int HEIGHT = 8;
    private static final int GAP = 4;

    private LevelSegments() {
    }

    /** The token of a level's medal color. */
    static String token(int index, int count) {
        return switch (Achievement.medal(index, count)) {
            case GOLD -> "medal.gold";
            case SILVER -> "medal.silver";
            case BRONZE -> "medal.bronze";
        };
    }

    /** @param fills how full each level is, 0 to 1, in level order */
    static void render(Ui ui, float x, float y, float w, float[] fills) {
        int n = fills.length;
        if (n == 0) {
            return;
        }
        float segW = (w - GAP * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            float sx = x + i * (segW + GAP);
            int color = ui.c(token(i, n));
            float fill = Math.clamp(fills[i], 0f, 1f);
            // In the bevelled theme an empty segment is light with a border, so that dark silver stands out from it (picture 45).
            int plate = ui.theme.beveled() ? ui.c("panel.bg") : ui.c("slot.bg");
            ui.box(sx, y, segW, HEIGHT, 2, plate, fill >= 1f ? color : ui.c("slot.border"));
            if (fill > 0) {
                ui.fill(sx + 1, y + 1, Math.max(1, (segW - 2) * fill), HEIGHT - 2, 1, color);
            }
        }
    }
}
