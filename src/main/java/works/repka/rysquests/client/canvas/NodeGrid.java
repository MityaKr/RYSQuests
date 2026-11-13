package works.repka.rysquests.client.canvas;

import java.util.Arrays;
import java.util.function.IntPredicate;
import works.repka.rysquests.client.layout.CanvasLayout;

/** Nodes of a layout in square cells, to find the node under the pointer and count nodes under a label without a scan. */
final class NodeGrid {
    private static final float CELL = 32;
    private final CanvasLayout layout;
    private final int cols;
    private final int rows;
    private final int[] start;
    private final int[] items;

    NodeGrid(CanvasLayout layout) {
        this.layout = layout;
        cols = Math.max(1, (int) Math.ceil((layout.maxX - layout.minX) / CELL) + 1);
        rows = Math.max(1, (int) Math.ceil((layout.maxY - layout.minY) / CELL) + 1);
        int n = layout.x.length;
        int[] cellOf = new int[n];
        int[] counts = new int[cols * rows + 1];
        for (int q = 0; q < n; q++) {
            cellOf[q] = cell(layout.x[q], layout.y[q]);
            counts[cellOf[q] + 1]++;
        }
        for (int i = 1; i < counts.length; i++) {
            counts[i] += counts[i - 1];
        }
        start = Arrays.copyOf(counts, counts.length);
        items = new int[n];
        int[] fill = Arrays.copyOf(counts, counts.length);
        for (int q = 0; q < n; q++) {
            items[fill[cellOf[q]]++] = q;
        }
    }

    private int col(double x) {
        return Math.max(0, Math.min(cols - 1, (int) ((x - layout.minX) / CELL)));
    }

    private int row(double y) {
        return Math.max(0, Math.min(rows - 1, (int) ((y - layout.minY) / CELL)));
    }

    private int cell(double x, double y) {
        return row(y) * cols + col(x);
    }

    /** The node nearest to the point within {@code reach}, skipping those {@code skip} says; -1 when none. */
    int nearest(double x, double y, double reach, IntPredicate skip) {
        int best = -1;
        double bestD = reach * reach;
        for (int r = row(y - reach); r <= row(y + reach); r++) {
            for (int c = col(x - reach); c <= col(x + reach); c++) {
                int cell = r * cols + c;
                for (int i = start[cell]; i < start[cell + 1]; i++) {
                    int q = items[i];
                    if (skip != null && skip.test(q)) {
                        continue;
                    }
                    double dx = layout.x[q] - x;
                    double dy = layout.y[q] - y;
                    double d = dx * dx + dy * dy;
                    if (d <= bestD) {
                        bestD = d;
                        best = q;
                    }
                }
            }
        }
        return best;
    }

    /** How many nodes lie in the box of half sizes {@code hw}, {@code hh} round the point. */
    int count(double x, double y, double hw, double hh) {
        int count = 0;
        for (int r = row(y - hh); r <= row(y + hh); r++) {
            for (int c = col(x - hw); c <= col(x + hw); c++) {
                int cell = r * cols + c;
                for (int i = start[cell]; i < start[cell + 1]; i++) {
                    int q = items[i];
                    if (Math.abs(layout.x[q] - x) <= hw && Math.abs(layout.y[q] - y) <= hh) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
