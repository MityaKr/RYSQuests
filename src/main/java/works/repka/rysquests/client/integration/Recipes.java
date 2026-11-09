package works.repka.rysquests.client.integration;

import net.minecraft.world.item.ItemStack;

/** The recipe viewer, if any. With RYScope in the game JEI is always there, and its recipe window is RYScope's own. */
public final class Recipes {
    /** What the book needs from a recipe viewer. */
    public interface Bridge {
        void showRecipes(ItemStack stack);

        void showUsages(ItemStack stack);
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
