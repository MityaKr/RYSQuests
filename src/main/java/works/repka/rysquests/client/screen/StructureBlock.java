package works.repka.rysquests.client.screen;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.scene.BlockScene;
import works.repka.ryscore.scene.SceneView;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;
import works.repka.rysquests.client.integration.Multiblocks;
import works.repka.rysquests.data.StructureDef;

/** A build on a page, from the structure block of {@code design/GuidePage.dc.html}: turned with the mouse, shown layer by layer. */
final class StructureBlock implements AutoCloseable {
    private static final int VIEW_H = 300;
    private static final int GRID_STEP = 18;
    private static final int LIST_ROW = 30;
    private static final float GUI_PER_PIXEL = 0.5f;

    final StructureDef definition;
    @Nullable
    private volatile BlockScene scene;
    @Nullable
    private SceneView view;
    private boolean listOpen;

    StructureBlock(StructureDef definition) {
        this.definition = definition;
        CompletableFuture.supplyAsync(() -> build(definition), Util.backgroundExecutor()).thenAcceptAsync(built -> scene = built, Minecraft.getInstance());
    }

    private static BlockScene build(StructureDef definition) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        definition.forEachBlock((x, y, z, spec) -> {
            BlockState state = Multiblocks.state(spec);
            if (state != null) {
                blocks.put(new BlockPos(x, y, z), state);
            }
        });
        return BlockScene.of(blocks);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private int layers() {
        BlockScene built = scene;
        return built == null ? 0 : built.size().getY();
    }

    private void stepLayer(int delta) {
        if (view == null) {
            return;
        }
        Integer current = view.layer();
        int next = current == null ? (delta > 0 ? 0 : layers() - 1) : current + delta;
        view.setLayer(next < 0 || next >= layers() ? null : Integer.valueOf(next));
    }

    /** @return the y below it @param press called when the view is pressed, which starts turning it */
    float render(Ui ui, String caption, float x, float y, float w, Runnable press) {
        BlockScene built = scene;
        if (view == null && built != null) {
            view = new SceneView(built);
        }
        List<Map.Entry<Item, Integer>> counts = built == null ? List.of() : built.counts().entrySet().stream().sorted(Map.Entry.<Item, Integer>comparingByValue(Comparator.reverseOrder())).toList();
        float inner = w - 20;
        float left = x + 10;
        float listH = listOpen ? 8 + counts.size() * LIST_ROW : 0;
        float height = 10 + 16 + 8 + VIEW_H + 8 + 28 + listH + 10;
        ui.box(x, y, w, height, 10, ui.c("card.bg"), ui.c("panel.border"));
        float titleY = y + 18;
        ui.labelFitTip(caption.isEmpty() ? tr("rysquests.structure.title") : caption, left, titleY, inner * 0.6f, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        renderLayers(ui, left + inner, titleY);
        float viewY = y + 34;
        renderView(ui, left, viewY, inner, press);
        float buttonsY = viewY + VIEW_H + 8;
        renderButtons(ui, caption, counts.size(), left, buttonsY, inner);
        if (listOpen) {
            renderList(ui, counts, left, buttonsY + 28 + 8, inner);
        }
        return y + height;
    }

    private void renderLayers(Ui ui, float right, float cy) {
        if (layers() < 2 || view == null) {
            return;
        }
        Integer current = view.layer();
        String label = current == null ? tr("rysquests.structure.all_layers") : tr("rysquests.structure.layer", current + 1, layers());
        float labelW = ui.width(label, 11, Ui.REGULAR);
        chevron(ui, Icons.Shape.CHEVRON_RIGHT, right - 18, cy, () -> stepLayer(1));
        ui.labelRight(label, right - 22, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        chevron(ui, Icons.Shape.CHEVRON_LEFT, right - 22 - labelW - 22, cy, () -> stepLayer(-1));
    }

    private static void chevron(Ui ui, Icons.Shape shape, float x, float cy, Runnable action) {
        boolean over = ui.over(x, cy - 9, 18, 18);
        if (over) {
            ui.fill(x, cy - 9, 18, 18, 4, ui.c("button.bg"));
        }
        ui.icon(shape, x + 4.5f, cy - 4.5f, 9, ui.c("button.fg"));
        ui.hit(x, cy - 9, 18, 18, action);
    }

    private void renderView(Ui ui, float x, float y, float w, Runnable press) {
        ui.fill(x, y, w, VIEW_H, 8, ui.c("slot.bg"));
        int line = Draw.alpha(ui.c("slot.border"), 0.5f);
        ui.clip(Math.round(x), Math.round(y), Math.round(w), VIEW_H);
        for (float px = x + GRID_STEP; px < x + w; px += GRID_STEP) {
            ui.rect(px, y, 1, VIEW_H, line);
        }
        for (float py = y + GRID_STEP; py < y + VIEW_H; py += GRID_STEP) {
            ui.rect(x, py, w, 1, line);
        }
        ui.unclip();
        if (view != null) {
            view.render(ui.g, Math.round(x * GUI_PER_PIXEL), Math.round(y * GUI_PER_PIXEL), Math.round(w * GUI_PER_PIXEL), Math.round(VIEW_H * GUI_PER_PIXEL));
        }
        ui.labelFit(tr("rysquests.structure.hint"), x + 8, y + VIEW_H - 12, w - 16, 10.5f, Ui.REGULAR, ui.c("text.dim"));
        ui.hit(x, y, w, VIEW_H, press);
    }

    private void renderButtons(Ui ui, String caption, int kinds, float x, float y, float w) {
        boolean show = Multiblocks.canShow(definition);
        float buttonW = show ? (w - 6) / 2f : w;
        float pen = x;
        if (show) {
            ui.button(tr("rysquests.structure.show"), pen, y, buttonW, 28, 7, 11.5f, Ui.REGULAR, 6, Ui.Style.PLAIN, () -> showInWorld(caption));
            pen += buttonW + 6;
        }
        ui.button(tr("rysquests.structure.list", kinds), pen, y, buttonW, 28, 7, 11.5f, Ui.REGULAR, 6, Ui.Style.PLAIN, () -> listOpen = !listOpen);
    }

    private void showInWorld(String caption) {
        Multiblocks.show(definition, caption.isEmpty() ? Component.translatable("rysquests.structure.title") : Component.literal(caption));
        Minecraft.getInstance().setScreen(null);
    }

    private static void renderList(Ui ui, List<Map.Entry<Item, Integer>> counts, float x, float y, float w) {
        float rowY = y;
        for (Map.Entry<Item, Integer> count : counts) {
            ItemStack stack = new ItemStack(count.getKey());
            ui.item(stack, x + 4, rowY + 7, 16);
            ui.interactive(stack, x, rowY, 24, LIST_ROW);
            int held = RecipePreview.held(stack);
            String amount = Numbers.compact(held) + " / " + Numbers.compact(count.getValue());
            float amountW = ui.width(amount, 12, Ui.REGULAR);
            ui.labelFitTip(stack.getHoverName().getString(), x + 28, rowY + LIST_ROW / 2f, w - 28 - amountW - 8, 12, Ui.REGULAR, ui.nameFg());
            ui.labelRight(amount, x + w - 4, rowY + LIST_ROW / 2f, 12, Ui.REGULAR, ui.c(held >= count.getValue() ? "state.ok" : "text.muted"));
            rowY += LIST_ROW;
        }
    }

    void drag(double deltaX, double deltaY) {
        if (view != null) {
            view.drag(deltaX, deltaY);
        }
    }

    void zoom(double amount) {
        if (view != null) {
            view.scroll(amount);
        }
    }

    boolean readyForTest() {
        return view != null;
    }

    void stepLayerForTest() {
        stepLayer(1);
    }

    @Override
    public void close() {
        if (view != null) {
            view.close();
            view = null;
        }
    }
}
