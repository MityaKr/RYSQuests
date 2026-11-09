package works.repka.rysquests.client.integration;

import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.client.screen.BookScreen;

/** Found by JEI itself, so this class is never loaded without JEI. Callbacks may run on JEI's loading thread. */
@JeiPlugin
public final class JeiQuestPlugin implements IModPlugin {
    private static final int ITEM_SIZE = 16;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "book");
    }

    /** Tells JEI what is under the cursor in the book, so that its keys and RYScope's lens work there. */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        IIngredientManager ingredients = registration.getJeiHelpers().getIngredientManager();
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
                ItemStack stack = BookScreen.hoveredStack();
                if (stack.isEmpty()) {
                    return Optional.empty();
                }
                Rect2i area = new Rect2i((int) mouseX, (int) mouseY, ITEM_SIZE, ITEM_SIZE);
                return ingredients.createClickableIngredient(VanillaTypes.ITEM_STACK, stack, area, false).map(clickable -> clickable);
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        Recipes.set(new Recipes.Bridge() {
            @Override
            public void showRecipes(ItemStack stack) {
                show(stack, RecipeIngredientRole.OUTPUT);
            }

            @Override
            public void showUsages(ItemStack stack) {
                show(stack, RecipeIngredientRole.INPUT);
            }

            private void show(ItemStack stack, RecipeIngredientRole role) {
                runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, stack));
            }
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        Recipes.set(null);
    }
}
