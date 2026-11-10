package works.repka.rysquests.client.screen;

import net.minecraft.world.item.ItemStack;
import works.repka.ryscore.ui.Draw;
import works.repka.rysquests.client.integration.Recipes;

/** A recipe as the viewer itself draws it, machines and their animations included, fitted to a width of the book. */
final class RecipeLayoutView {
    /** The viewer draws in GUI units, of which a mockup pixel is half. */
    private static final float PIXELS_PER_UNIT = 2f;

    private RecipeLayoutView() {
    }

    private static float scale(Recipes.Layout layout, float w) {
        return Math.min(1f, w / (layout.width() * PIXELS_PER_UNIT));
    }

    static float height(Recipes.Layout layout, float w) {
        return layout.height() * PIXELS_PER_UNIT * scale(layout, w);
    }

    /** Centered in the width. A slot under the cursor behaves like any item of the book. */
    static void render(Ui ui, Recipes.Layout layout, float x, float y, float w) {
        float scale = scale(layout, w);
        float shownW = layout.width() * PIXELS_PER_UNIT * scale;
        float shownH = height(layout, w);
        float left = x + (w - shownW) / 2f;
        float perPixel = PIXELS_PER_UNIT * scale;
        int mouseX = Math.round((ui.mouseX - left) / perPixel);
        int mouseY = Math.round((ui.mouseY - y) / perPixel);
        ui.g.pose().pushPose();
        ui.g.pose().translate(left / PIXELS_PER_UNIT, y / PIXELS_PER_UNIT, 0);
        ui.g.pose().scale(scale, scale, 1f);
        Draw.isolated(ui.g, () -> layout.draw(ui.g, mouseX, mouseY));
        ui.g.pose().popPose();
        if (ui.over(left, y, shownW, shownH)) {
            ItemStack stack = layout.under(mouseX, mouseY);
            if (!stack.isEmpty()) {
                ui.hoveredStack = stack;
                ui.hit(left, y, shownW, shownH, () -> Recipes.showRecipes(stack));
            }
        }
    }
}
