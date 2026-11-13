package works.repka.rysquests.client.dev;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import works.repka.rysquests.client.canvas.MapCanvas;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Batch;
import works.repka.ryscore.ui.Draw;

/**
 * A bare screen around the canvas for the canvas scenario: the canvas sits where the map area of the mockups is (1280×800 at GUI
 * scale 2), so that screenshots lie over pictures 13 to 49. It times every frame of the canvas on the CPU, with and without
 * flushing the batch it filled.
 */
public final class CanvasTestScreen extends Screen {
    public static final int X = 286;
    public static final int Y = 94;
    public static final int W = 953;
    public static final int H = 669;

    public final MapCanvas canvas = new MapCanvas();
    /** A pointer the scenario sets in mockup pixels; when null the real mouse is used. */
    public int[] pointer = {-100, -100};
    private long frames;
    private long sumBuild;
    private long sumTotal;
    private long maxTotal;

    public CanvasTestScreen() {
        super(Component.literal("canvas"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = ThemeManager.get();
        Draw.rect(g, 0, 0, width, height, t.c("panel.bg"));
        int mx = pointer == null ? mouseX * 2 : pointer[0];
        int my = pointer == null ? mouseY * 2 : pointer[1];
        Batch.flush();
        long start = System.nanoTime();
        canvas.render(g, X, Y, W, H, mx, my);
        long built = System.nanoTime();
        Batch.flush();
        long end = System.nanoTime();
        frames++;
        sumBuild += built - start;
        sumTotal += end - start;
        maxTotal = Math.max(maxTotal, end - start);
    }

    public void resetTiming() {
        frames = 0;
        sumBuild = 0;
        sumTotal = 0;
        maxTotal = 0;
    }

    /** Average and worst CPU time of a canvas frame in milliseconds: building the batch, and building plus flushing it. */
    public String timing() {
        if (frames == 0) {
            return "no frames";
        }
        MapCanvas.Stats stats = canvas.stats();
        return String.format(java.util.Locale.ROOT, "%d frames, canvas %.2f ms, with flush %.2f ms (worst %.2f ms); level %s, scale %.3f; nodes %d, roads %d, icons %d, orbits %d, labels %d",
                frames, sumBuild / 1e6 / frames, sumTotal / 1e6 / frames, maxTotal / 1e6, canvas.level(), canvas.scale(), stats.nodes(), stats.roads(), stats.icons(), stats.rings(),
                stats.labels());
    }

    public double averageMs() {
        return frames == 0 ? 0 : sumTotal / 1e6 / frames;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return canvas.mouseClicked(mouseX * 2, mouseY * 2, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return canvas.mouseDragged(mouseX * 2, mouseY * 2, dragX * 2, dragY * 2) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return canvas.mouseReleased(mouseX * 2, mouseY * 2, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return canvas.mouseScrolled(mouseX * 2, mouseY * 2, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
