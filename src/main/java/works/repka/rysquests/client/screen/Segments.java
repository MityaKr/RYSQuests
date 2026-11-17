package works.repka.rysquests.client.screen;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.Type;

/**
 * A segmented switch: "Map | List", "Buy | Sell | History", "Slate | Glass | Vanilla". It is a row of {@link Kit#pill} on a shared
 * plate, the family's rule (RYSCore 1.9), and not a widget of its own. Mockup {@code .seg}: plate 34 high, input color, border, radius 9.
 *
 * <pre>
 * float w = Segments.render(ui, x, y, List.of(Segments.item("Карта", Icons.Shape.MAP), Segments.item("Список", Icons.Shape.LIST)),
 *         selected, index -> ...);
 * </pre>
 */
final class Segments {
    static final int HEIGHT = 34;
    private static final int INSET = 3;
    private static final int GAP = 2;
    private static final float PX = 12.5f;
    private static final float ICON = 14;
    private static final float ICON_GAP = 6;
    private static final float PILL_PAD = 12;

    /**
     * One segment.
     *
     * @param icon     a line icon before the label, or null
     * @param disabled shown faded and not clickable, with {@code tooltip} saying why ("Этой главы нет на карте")
     */
    record Item(String label, @Nullable Icons.Shape icon, boolean disabled, @Nullable Component tooltip) {
    }

    private Segments() {
    }

    static Item item(String label) {
        return new Item(label, null, false, null);
    }

    static Item item(String label, Icons.Shape icon) {
        return new Item(label, icon, false, null);
    }

    /** The width the switch takes, plate included. */
    static float width(List<Item> items, int selected) {
        float w = INSET * 2 + GAP * Math.max(0, items.size() - 1);
        for (int i = 0; i < items.size(); i++) {
            w += pillWidth(items.get(i), i == selected);
        }
        return w;
    }

    /** Draws the switch with its top left corner at (x, y). @return its width */
    static float render(Ui ui, float x, float y, List<Item> items, int selected, IntConsumer pick) {
        float w = width(items, selected);
        ui.box(x, y, w, HEIGHT, 9, ui.c("input.bg"), ui.c("panel.border"));
        float pen = x + INSET;
        int unitY = Math.round((y + INSET) / 2f);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean active = i == selected;
            int index = i;
            String label = padded(item);
            int unitX = Math.round(pen / 2f);
            Runnable action = item.disabled() ? () -> { } : () -> pick.accept(index);
            int unitsW = Kit.pill(ui.g, ui.theme, ui.kitHits, unitX, unitY, label, active, ui.mouseX / 2, ui.mouseY / 2, action);
            float pillX = unitX * 2f;
            float pillW = unitsW * 2f;
            if (item.icon() != null) {
                int color = ui.c(active ? "text.primary" : "text.muted");
                ui.icon(item.icon(), pillX + PILL_PAD, unitY * 2f + (28 - ICON) / 2f, ICON, item.disabled() ? Draw.alpha(color, 0.4f) : color);
            }
            if (item.disabled()) {
                ui.fill(pillX, unitY * 2f, pillW, 28, 8, Draw.alpha(ui.c("input.bg"), 0.6f));
            }
            if (item.tooltip() != null) {
                ui.tooltipIfOver(pillX, unitY * 2f, pillW, 28, item.tooltip());
            }
            pen += pillW + GAP;
        }
        return w;
    }

    private static float pillWidth(Item item, boolean active) {
        Type.Weight weight = active ? Type.Weight.MEDIUM : Type.Weight.REGULAR;
        return (float) (Math.ceil(Draw.labelWidth(padded(item), PX, weight)) + 12) * 2;
    }

    /** Room for the icon before the label: the pill draws text only, so the label starts with spaces as wide as icon and gap. */
    private static String padded(Item item) {
        if (item.icon() == null) {
            return item.label();
        }
        float space = Math.max(0.5f, Draw.labelWidth(" ", PX, Type.Weight.REGULAR));
        int count = (int) Math.ceil((ICON + ICON_GAP) / 2f / space);
        return " ".repeat(count) + item.label();
    }
}
