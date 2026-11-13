package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;

/**
 * A reward as a chip (mockup {@code .rw}, picture 21): an item with its count and name, experience ("300 опыта", the number green),
 * coins ("● 4 монеты", the coin in its own token, never the accent), or a line icon with words ("титул «Шахтёр»", "особая награда
 * сборки"). Chips flow in rows with {@link #row}.
 *
 * <pre>
 * float h = RewardChip.row(ui, x, y, w, List.of(RewardChip.xp(25, "опыта"), RewardChip.coins(1, "монета")));
 * </pre>
 */
final class RewardChip {
    static final int HEIGHT = 36;
    private static final int ROW_GAP = 6;
    private static final float PX = 12.5f;

    private enum Kind {
        ITEM, XP, COINS, GLYPH
    }

    private final Kind kind;
    private final String text;
    @Nullable
    private final String number;
    @Nullable
    private final ItemStack stack;
    private final int count;
    @Nullable
    private final Icons.Shape glyph;

    private RewardChip(Kind kind, String text, @Nullable String number, @Nullable ItemStack stack, int count, @Nullable Icons.Shape glyph) {
        this.kind = kind;
        this.text = text;
        this.number = number;
        this.stack = stack;
        this.count = count;
        this.glyph = glyph;
    }

    static RewardChip item(ItemStack stack, int count, String name) {
        return new RewardChip(Kind.ITEM, name, null, stack, count, null);
    }

    /** @param word the noun after the number in its right form: "опыта" */
    static RewardChip xp(long amount, String word) {
        return new RewardChip(Kind.XP, word, works.repka.ryscore.ui.Numbers.compact(amount), null, 0, null);
    }

    /** @param word the noun in its right form: "монета", "монет в казну" */
    static RewardChip coins(long amount, String word) {
        return new RewardChip(Kind.COINS, word, works.repka.ryscore.ui.Numbers.compact(amount), null, 0, null);
    }

    static RewardChip glyph(Icons.Shape shape, String text) {
        return new RewardChip(Kind.GLYPH, text, null, null, 0, shape);
    }

    /** The chip's natural width; a pixel of slack so that a label measured exactly is never cut by rounding. */
    float width(Ui ui) {
        return 2 + switch (kind) {
            case ITEM -> 4 + 28 + 7 + ui.width(text, PX, Ui.REGULAR) + 10;
            case XP -> 10 + ui.width(number, PX, Ui.SEMIBOLD) + 7 + ui.width(text, PX, Ui.REGULAR) + 10;
            case COINS -> 10 + 11 + 7 + ui.width(number, PX, Ui.SEMIBOLD) + 7 + ui.width(text, PX, Ui.REGULAR) + 10;
            case GLYPH -> 10 + 14 + 7 + ui.width(text, PX, Ui.REGULAR) + 10;
        };
    }

    /** Draws the chip, at most {@code maxW} wide (a longer name is cut and shown whole on hover). @return its width */
    float render(Ui ui, float x, float y, float maxW) {
        float w = Math.min(maxW, width(ui));
        ui.box(x, y, w, HEIGHT, 9, ui.c("card.bg"), ui.c("panel.border"));
        float cy = y + HEIGHT / 2f;
        int body = ui.c("text.body");
        float pen;
        switch (kind) {
            case ITEM -> {
                ui.box(x + 4, cy - 14, 28, 28, 6, ui.c("slot.bg"), ui.c("slot.border"));
                ui.item(stack, x + 8, cy - 10, 20);
                if (count > 1) {
                    ui.count(count, x + 31, cy + 16);
                }
                pen = x + 4 + 28 + 7;
            }
            case XP -> {
                ui.label(number, x + 10, cy, PX, Ui.SEMIBOLD, ui.c("state.ok"));
                pen = x + 10 + ui.width(number, PX, Ui.SEMIBOLD) + 7;
            }
            case COINS -> {
                ui.circle(x + 10, cy - 5.5f, 11, ui.c("coin"));
                ui.label(number, x + 28, cy, PX, Ui.SEMIBOLD, ui.c("text.primary"));
                pen = x + 28 + ui.width(number, PX, Ui.SEMIBOLD) + 7;
            }
            default -> {
                ui.icon(glyph, x + 10, cy - 7, 14, ui.c("text.secondary"));
                pen = x + 10 + 14 + 7;
            }
        }
        ui.labelFit(text, pen, cy, x + w - 10 - pen, PX, Ui.REGULAR, body);
        return w;
    }

    /** Chips in rows, 6 apart, wrapping at {@code w}. @return the height taken */
    static float row(Ui ui, float x, float y, float w, List<RewardChip> chips) {
        float pen = x;
        float line = y;
        for (RewardChip chip : chips) {
            float cw = Math.min(w, chip.width(ui));
            if (pen > x && pen + cw > x + w) {
                pen = x;
                line += HEIGHT + ROW_GAP;
            }
            pen += chip.render(ui, pen, line, w) + ROW_GAP;
        }
        return chips.isEmpty() ? 0 : line + HEIGHT - y;
    }
}
