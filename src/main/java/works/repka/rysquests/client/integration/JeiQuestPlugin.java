package works.repka.rysquests.client.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.client.screen.BookScreen;

/** Found by JEI itself, so this class is never loaded without JEI. Callbacks may run on JEI's loading thread. */
@JeiPlugin
public final class JeiQuestPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(JeiQuestPlugin.class);
    private static final int ITEM_SIZE = 16;
    private static final long TICK_MS = 50;

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
        Map<String, Optional<Recipes.Layout>> layouts = new HashMap<>();
        Recipes.set(new Recipes.Bridge() {
            @Override
            public Recipes.Layout layout(ItemStack output, ResourceLocation category) {
                return layouts.computeIfAbsent(BuiltInRegistries.ITEM.getKey(output.getItem()) + "#" + category, key -> find(runtime, output, category)).orElse(null);
            }

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

    /** A category of some mod that fails is the same as a recipe nobody knows: the book then shows what it can without it. */
    private static Optional<Recipes.Layout> find(IJeiRuntime runtime, ItemStack output, @Nullable ResourceLocation category) {
        try {
            IFocus<ItemStack> focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, output);
            IFocusGroup group = runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of(focus));
            IRecipeManager manager = runtime.getRecipeManager();
            return manager.createRecipeCategoryLookup().limitFocus(List.of(focus)).get()
                    .filter(found -> category == null || found.getRecipeType().getUid().equals(category))
                    .map(found -> layoutOf(manager, found, focus, group))
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (RuntimeException e) {
            LOGGER.debug("No recipe layout for {}", output, e);
            return Optional.empty();
        }
    }

    private static <T> Optional<Recipes.Layout> layoutOf(IRecipeManager manager, IRecipeCategory<T> category, IFocus<ItemStack> focus, IFocusGroup group) {
        return manager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().findFirst()
                .flatMap(recipe -> manager.createRecipeLayoutDrawable(category, recipe, group))
                .map(JeiLayout::new);
    }

    private static final class JeiLayout implements Recipes.Layout {
        private final IRecipeLayoutDrawable<?> drawable;
        private long lastTick;

        JeiLayout(IRecipeLayoutDrawable<?> drawable) {
            this.drawable = drawable;
            drawable.setPosition(0, 0);
        }

        @Override
        public int width() {
            return drawable.getRect().getWidth();
        }

        @Override
        public int height() {
            return drawable.getRect().getHeight();
        }

        @Override
        public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
            long now = Util.getMillis();
            if (now - lastTick >= TICK_MS) {
                lastTick = now;
                drawable.tick();
            }
            drawable.drawRecipe(graphics, mouseX, mouseY);
        }

        @Override
        public ItemStack under(int mouseX, int mouseY) {
            return drawable.getItemStackUnderMouse(mouseX, mouseY).orElse(ItemStack.EMPTY);
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        Recipes.set(null);
    }
}
