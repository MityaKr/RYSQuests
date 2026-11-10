package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * The recipe of a quest's item, for the small preview in the book. Looks results up in an index that is built in the
 * background once per recipe reload, since a large pack has tens of thousands of recipes.
 */
final class RecipePreview {
    /** @param grid the inputs by slot of a crafting grid, empty stacks where nothing goes */
    record Shown(List<ItemStack> inputs, ItemStack output, List<ItemStack> grid) {
    }

    private static final int GRID_SIDE = 3;

    @Nullable
    private static volatile Map<Item, RecipeHolder<?>> index;
    @Nullable
    private static RecipeManager indexedFor;
    private static final Map<Item, Shown> SHOWN = new HashMap<>();

    private RecipePreview() {
    }

    static String viewerName() {
        return ModList.get().isLoaded("ryscope") ? "RYScope" : "JEI";
    }

    @Nullable
    static Shown of(ItemStack target) {
        Minecraft mc = Minecraft.getInstance();
        if (target.isEmpty() || mc.level == null) {
            return null;
        }
        RecipeManager manager = mc.level.getRecipeManager();
        if (manager != indexedFor) {
            indexedFor = manager;
            index = null;
            SHOWN.clear();
            RegistryAccess access = mc.level.registryAccess();
            CompletableFuture.supplyAsync(() -> build(manager, access), Util.backgroundExecutor())
                    .thenAcceptAsync(built -> {
                        if (manager == indexedFor) {
                            index = built;
                        }
                    }, mc);
        }
        Map<Item, RecipeHolder<?>> current = index;
        if (current == null) {
            return null;
        }
        RecipeHolder<?> holder = current.get(target.getItem());
        if (holder == null) {
            return null;
        }
        return SHOWN.computeIfAbsent(target.getItem(), item -> shown(holder, mc.level.registryAccess()));
    }

    private static Map<Item, RecipeHolder<?>> build(RecipeManager manager, RegistryAccess access) {
        Map<Item, RecipeHolder<?>> built = new HashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            try {
                Recipe<?> recipe = holder.value();
                ItemStack result = recipe.getResultItem(access);
                if (result.isEmpty() || recipe.getIngredients().isEmpty()) {
                    continue;
                }
                if (recipe instanceof CraftingRecipe) {
                    built.put(result.getItem(), holder);
                } else {
                    built.putIfAbsent(result.getItem(), holder);
                }
            } catch (RuntimeException ignored) {
                // A recipe of some mod that cannot report its result is simply not previewed.
            }
        }
        return built;
    }

    private static Shown shown(RecipeHolder<?> holder, RegistryAccess access) {
        List<ItemStack> inputs = new ArrayList<>();
        for (Ingredient ingredient : holder.value().getIngredients()) {
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) {
                continue;
            }
            ItemStack option = options[0];
            ItemStack same = inputs.stream().filter(stack -> stack.is(option.getItem())).findFirst().orElse(null);
            if (same != null) {
                same.grow(Math.max(1, option.getCount()));
            } else {
                inputs.add(option.copy());
            }
        }
        return new Shown(inputs, holder.value().getResultItem(access).copy(), grid(holder.value()));
    }

    private static List<ItemStack> grid(Recipe<?> recipe) {
        List<ItemStack> grid = new ArrayList<>();
        List<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : GRID_SIDE;
        for (int slot = 0; slot < GRID_SIDE * GRID_SIDE; slot++) {
            int column = slot % GRID_SIDE;
            int index = slot / GRID_SIDE * width + column;
            boolean inside = column < width && index < ingredients.size();
            ItemStack[] options = inside ? ingredients.get(index).getItems() : new ItemStack[0];
            grid.add(options.length == 0 ? ItemStack.EMPTY : options[0]);
        }
        return grid;
    }

    /** How many of an input the player holds. */
    static int held(ItemStack input) {
        var inventory = Minecraft.getInstance().player.getInventory();
        int have = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(input.getItem())) {
                have += stack.getCount();
            }
        }
        return have;
    }

    /** The first input the player does not hold enough of, or empty. */
    static ItemStack firstMissing(Shown recipe) {
        for (ItemStack input : recipe.inputs()) {
            if (held(input) < input.getCount()) {
                return input;
            }
        }
        return ItemStack.EMPTY;
    }
}
