package works.repka.rysquests.client.integration;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The recipe viewer, if any. With RYSIndex in the game JEI is always there, and its recipe window is RYSIndex's own. */
public final class Recipes {
    /** What the book needs from a recipe viewer. */
    public interface Bridge {
        void showRecipes(ItemStack stack);

        void showUsages(ItemStack stack);

        /** @param category a category of the viewer, or null for the first that makes the item */
        @Nullable
        default Layout layout(ItemStack output, @Nullable ResourceLocation category) {
            return null;
        }
    }

    /** A recipe the viewer draws itself. Sizes and coordinates are in GUI units, from its top left corner. */
    public interface Layout {
        int width();

        int height();

        void draw(GuiGraphics graphics, int mouseX, int mouseY);

        ItemStack under(int mouseX, int mouseY);
    }

    private static volatile Bridge bridge;

    private Recipes() {
    }

    public static boolean available() {
        return bridge != null;
    }

    /** May be called from JEI's loading thread, so this only stores the reference. */
    public static void set(Bridge newBridge) {
        bridge = newBridge;
    }

    @Nullable
    public static Layout layout(ItemStack output, @Nullable ResourceLocation category) {
        Bridge current = bridge;
        return current == null || output.isEmpty() ? null : current.layout(output, category);
    }

    public static void showRecipes(ItemStack stack) {
        Bridge current = bridge;
        if (current != null && !stack.isEmpty()) {
            current.showRecipes(stack);
        }
    }

    public static void showUsages(ItemStack stack) {
        Bridge current = bridge;
        if (current != null && !stack.isEmpty()) {
            current.showUsages(stack);
        }
    }
}
