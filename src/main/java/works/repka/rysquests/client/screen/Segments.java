package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Kit;

/**
 * A segmented switch: "Map | List", "Buy | Sell | History", "Slate | Glass | Vanilla". Since RYSCore 1.18 it is the core's
 * {@link Kit#segmented}: the selected item sits on its plate with the same inset on all sides at every interface scale (author's
 * note 24.11). Mockup {@code .seg}: plate 34 high, input color, border, radius 9. Coordinates here are mockup pixels, two per unit.
 *
 * <pre>
 * float w = Segments.render(ui, x, y, List.of(Segments.item("Карта", Icons.Shape.MAP), Segments.item("Список", Icons.Shape.LIST)),
 *         selected, index -> ...);
 * </pre>
 */
final class Segments {
    static final int HEIGHT = Math.round(Kit.SEG_H * 2);

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

    private static List<Kit.Segment> segments(List<Item> items) {
        List<Kit.Segment> out = new ArrayList<>(items.size());
        for (Item item : items) {
            out.add(Kit.Segment.of(item.label()).icon(item.icon()).disabled(item.disabled()));
        }
        return out;
    }

    /** The width the switch takes, plate included. It does not depend on the selected item. */
    static float width(List<Item> items, int selected) {
        return Kit.segmentedWidth(ThemeManager.get(), segments(items)) * 2;
    }

    /** Draws the switch with its top left corner at (x, y). @return its width */
    static float render(Ui ui, float x, float y, List<Item> items, int selected, IntConsumer pick) {
        List<Kit.Segment> segments = segments(items);
        int hovered = Kit.segmented(ui.g, ui.theme, ui.kitHits, x / 2f, y / 2f, Kit.SEG_H, segments, selected,
                ui.mouseX / 2.0, ui.mouseY / 2.0, pick);
        if (hovered >= 0 && items.get(hovered).tooltip() != null) {
            ui.hoveredText = items.get(hovered).tooltip();
        }
        return Kit.segmentedWidth(ui.theme, segments) * 2;
    }
}
