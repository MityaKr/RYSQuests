package works.repka.rysquests.client.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import works.repka.rysquests.client.layout.LayeredLayout.Placed;

/**
 * The automatic layout of a chapter on the map (ADR-44).
 *
 * <p>Quests outside groups form the main path: a column per depth of the dependency chain, centered on one horizontal
 * line, with a second column of the same depth when more than {@value #MAX_STACK} share it. A group is a block: its
 * quests of one depth sit in a grid, three in a row or more for a large group, and the block hangs above or below the
 * main path at the step it depends on, the next block of the same step on the other side. A chapter with no main path
 * puts its groups side by side in shelves. Room for a group's label and for the larger selected quest is kept free.
 * Dependencies always end up to the left, and equal input gives equal output.
 */
public final class ChapterLayout {
    /** Quests outside groups stacked in one column before a second column of the same depth opens. */
    public static final int MAX_STACK = 6;
    private static final int GRID_MIN_COLUMNS = 3;
    private static final int SHELF_COLUMNS = 10;
    /** How far right of its step a block may move, in columns, and what a pixel of that costs against a pixel away from the line. */
    private static final int MAX_SHIFT = 6;
    private static final double SHIFT_COST = 0.3;

    /** @param requires ids of nodes this one depends on; ids outside the chapter are ignored */
    public record Node(String id, List<String> requires, Optional<String> group, boolean optional, OptionalInt fixedX, OptionalInt fixedY) {
        public Node(String id, List<String> requires) {
            this(id, requires, Optional.empty(), false, OptionalInt.empty(), OptionalInt.empty());
        }

        public Node(String id, List<String> requires, String group) {
            this(id, requires, Optional.of(group), true, OptionalInt.empty(), OptionalInt.empty());
        }
    }

    /**
     * @param framePadX how far a group's frame reaches left and right of its quests
     * @param framePadTop how far it reaches above them; its label sits {@code labelAbove} higher still
     * @param blockGap the least room between blocks, and between a block and a quest of the main path
     * @param reachX how far the selected quest grows past its place on each side
     */
    public record Metrics(int nodeWidth, int nodeHeight, int gapX, int gapY, int framePadX, int framePadTop, int framePadBottom,
            int labelAbove, int blockGap, int reachX, int reachY) {
        int column() {
            return nodeWidth + gapX;
        }

        int row() {
            return nodeHeight + gapY;
        }
    }

    private record Rect(int x, int y, int w, int h) {
    }

    /** A group laid out on its own: each member's cell, and the size of the quests' area. */
    private static final class Block {
        final String group;
        final List<String> members;
        final Map<String, int[]> cells = new LinkedHashMap<>();
        int anchor;
        /** Columns from the anchor to the first quest outside that depends on the block; the block ends before it. */
        int limit = Integer.MAX_VALUE;
        int columns;
        int rows;
        int x;
        int y;

        Block(String group, List<String> members) {
            this.group = group;
            this.members = members;
        }

        int width(Metrics m) {
            return columns * m.column() - m.gapX();
        }

        int height(Metrics m) {
            return rows * m.row() - m.gapY();
        }

        int outerWidth(Metrics m) {
            return width(m) + m.framePadX() * 2;
        }

        int outerHeight(Metrics m) {
            return height(m) + m.framePadTop() + m.labelAbove() + m.framePadBottom();
        }

        int top(Metrics m) {
            return m.framePadTop() + m.labelAbove();
        }
    }

    private ChapterLayout() {
    }

    /** @param groupOrder the chapter's groups in its own order, which blocks without a main path follow */
    public static Map<String, Placed> compute(List<Node> input, List<String> groupOrder, Metrics m) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        input.forEach(node -> nodes.put(node.id(), node));
        if (nodes.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> predecessors = new HashMap<>();
        Map<String, List<String>> successors = new HashMap<>();
        nodes.keySet().forEach(id -> {
            predecessors.put(id, new ArrayList<>());
            successors.put(id, new ArrayList<>());
        });
        for (Node node : nodes.values()) {
            for (String dependency : node.requires()) {
                if (nodes.containsKey(dependency) && !dependency.equals(node.id())) {
                    predecessors.get(node.id()).add(dependency);
                    successors.get(dependency).add(node.id());
                }
            }
        }
        Map<String, Integer> depth = depths(nodes, predecessors, successors);
        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<String> spine = new ArrayList<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (Node node : nodes.values()) {
            if (node.group().isPresent()) {
                groups.computeIfAbsent(node.group().get(), key -> new ArrayList<>()).add(node.id());
            } else {
                spine.add(node.id());
            }
        }

        // Columns: a depth is as wide as the stacks of its main path quests need.
        int[] spineCount = new int[maxDepth + 1];
        spine.forEach(id -> spineCount[depth.get(id)]++);
        int[] columnOf = new int[maxDepth + 2];
        for (int d = 0; d <= maxDepth; d++) {
            columnOf[d + 1] = columnOf[d] + Math.max(1, ceil(spineCount[d], MAX_STACK));
        }

        Map<String, int[]> position = new HashMap<>();
        placeSpine(spine, depth, predecessors, successors, nodes, columnOf, maxDepth, m, position);

        List<Block> blocks = new ArrayList<>();
        groups.forEach((group, members) -> blocks.add(block(group, members, depth, successors, columnOf, m)));
        Comparator<Block> byChapterOrder = Comparator.comparingInt(block -> {
            int index = groupOrder.indexOf(block.group);
            return index < 0 ? Integer.MAX_VALUE : index;
        });
        blocks.sort(byChapterOrder);
        if (spine.isEmpty()) {
            shelve(blocks, m);
        } else {
            List<Block> byStep = new ArrayList<>(blocks);
            byStep.sort(Comparator.comparingInt((Block block) -> block.anchor).thenComparing(byChapterOrder));
            hang(byStep, spine, position, columnOf, m);
        }
        for (Block block : blocks) {
            block.cells.forEach((id, cell) -> position.put(id, new int[] {block.x + cell[0] * m.column(), block.y + cell[1] * m.row()}));
        }

        for (Node node : nodes.values()) {
            int[] at = position.get(node.id());
            node.fixedX().ifPresent(x -> at[0] = x);
            node.fixedY().ifPresent(y -> at[1] = y);
        }
        return normalize(nodes, position, blocks, depth, m);
    }

    /** Longest path from a node without dependencies; nodes in a cycle keep what they had reached. */
    private static Map<String, Integer> depths(Map<String, Node> nodes, Map<String, List<String>> predecessors, Map<String, List<String>> successors) {
        Map<String, Integer> depth = new HashMap<>();
        Map<String, Integer> waiting = new HashMap<>();
        List<String> ready = new ArrayList<>();
        for (String id : nodes.keySet()) {
            depth.put(id, 0);
            waiting.put(id, predecessors.get(id).size());
            if (predecessors.get(id).isEmpty()) {
                ready.add(id);
            }
        }
        for (int i = 0; i < ready.size(); i++) {
            String id = ready.get(i);
            for (String next : successors.get(id)) {
                depth.merge(next, depth.get(id) + 1, Math::max);
                if (waiting.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        return depth;
    }

    /** The main path: each depth a stack centered on the line, the quests that lead on in its middle. */
    private static void placeSpine(List<String> spine, Map<String, Integer> depth, Map<String, List<String>> predecessors,
            Map<String, List<String>> successors, Map<String, Node> nodes, int[] columnOf, int maxDepth, Metrics m, Map<String, int[]> position) {
        for (int d = 0; d <= maxDepth; d++) {
            int level = d;
            List<String> column = new ArrayList<>(spine.stream().filter(id -> depth.get(id) == level).toList());
            if (column.isEmpty()) {
                continue;
            }
            Map<String, Double> barycenter = new HashMap<>();
            for (String id : column) {
                barycenter.put(id, predecessors.get(id).stream().filter(position::containsKey).mapToDouble(p -> position.get(p)[1]).average().orElse(0));
            }
            column.sort(Comparator.comparingDouble(barycenter::get));
            List<String> leading = column.stream().filter(id -> !nodes.get(id).optional() && successors.get(id).stream().anyMatch(spine::contains)).toList();
            List<String> rest = column.stream().filter(id -> !leading.contains(id)).toList();
            List<String> ordered = new ArrayList<>(rest.subList(0, rest.size() / 2));
            ordered.addAll(leading);
            ordered.addAll(rest.subList(rest.size() / 2, rest.size()));

            int stacks = Math.max(1, ceil(ordered.size(), MAX_STACK));
            int perStack = ceil(ordered.size(), stacks);
            for (int i = 0; i < ordered.size(); i++) {
                int stack = i / perStack;
                int row = i % perStack;
                int inStack = Math.min(perStack, ordered.size() - stack * perStack);
                int x = (columnOf[d] + stack) * m.column();
                int y = Math.round((row - (inStack - 1) / 2f) * m.row());
                position.put(ordered.get(i), new int[] {x, y});
            }
        }
    }

    /** A group's own grid, anchored at the depth of its first quests. */
    private static Block block(String group, List<String> members, Map<String, Integer> depth, Map<String, List<String>> successors, int[] columnOf, Metrics m) {
        Block block = new Block(group, members);
        block.anchor = members.stream().mapToInt(depth::get).min().orElse(0);
        // A quest outside the group that depends on it stays to the right of the whole block.
        int limit = Integer.MAX_VALUE;
        for (String member : members) {
            for (String next : successors.get(member)) {
                if (!members.contains(next)) {
                    limit = Math.min(limit, Math.max(1, columnOf[depth.get(next)] - columnOf[block.anchor]));
                }
            }
        }
        block.limit = limit;
        TreeMap<Integer, List<String>> byDepth = new TreeMap<>();
        members.forEach(id -> byDepth.computeIfAbsent(depth.get(id) - block.anchor, key -> new ArrayList<>()).add(id));
        if (byDepth.size() == 1) {
            int count = members.size();
            int columns = Math.min(Math.min(count, Math.max(GRID_MIN_COLUMNS, (int) Math.ceil(Math.sqrt(count / 2.0)))), limit);
            block.columns = columns;
            block.rows = ceil(count, columns);
            for (int i = 0; i < count; i++) {
                block.cells.put(members.get(i), new int[] {i % columns, i / columns});
            }
        } else {
            // Each depth keeps its own column, so that the block's chains still read left to right.
            for (Map.Entry<Integer, List<String>> entry : byDepth.entrySet()) {
                int level = block.anchor + entry.getKey();
                List<String> quests = entry.getValue();
                int base = columnOf[level] - columnOf[block.anchor];
                int room = columnOf[level + 1] - columnOf[level];
                int stacks = Math.clamp(ceil(quests.size(), MAX_STACK), 1, room);
                int perStack = ceil(quests.size(), stacks);
                for (int i = 0; i < quests.size(); i++) {
                    block.cells.put(quests.get(i), new int[] {base + i / perStack, i % perStack});
                }
                block.columns = Math.max(block.columns, base + stacks);
                block.rows = Math.max(block.rows, perStack);
            }
        }
        return block;
    }

    /** No main path: the blocks stand side by side, a new shelf when the row grows too long. */
    private static void shelve(List<Block> blocks, Metrics m) {
        int cursor = 0;
        int shelfTop = 0;
        int shelfHeight = 0;
        int shelfColumns = 0;
        for (Block block : blocks) {
            if (shelfColumns > 0 && shelfColumns + block.columns > SHELF_COLUMNS) {
                shelfTop += shelfHeight + m.blockGap() * 2;
                cursor = 0;
                shelfHeight = 0;
                shelfColumns = 0;
            }
            block.x = cursor + m.framePadX();
            block.y = shelfTop + block.top(m);
            cursor += block.outerWidth(m) + m.blockGap() * 2;
            shelfHeight = Math.max(shelfHeight, block.outerHeight(m));
            shelfColumns += block.columns;
        }
    }

    /**
     * Blocks hang off the main path at their step, on the side where they come closest to it, taking turns on a tie.
     * A block may also move right along the path, which costs less than a whole tier further out: blocks of one step
     * then stand side by side above and below the line instead of piling up.
     */
    private static void hang(List<Block> blocks, List<String> spine, Map<String, int[]> position, int[] columnOf, Metrics m) {
        List<Rect> taken = new ArrayList<>();
        for (String id : spine) {
            int[] at = position.get(id);
            taken.add(new Rect(at[0] - m.reachX(), at[1] - m.reachY(), m.nodeWidth() + m.reachX() * 2, m.nodeHeight() + m.reachY() * 2));
        }
        int line = m.nodeHeight() / 2;
        boolean lastAbove = false;
        for (Block block : blocks) {
            int w = block.outerWidth(m);
            int h = block.outerHeight(m);
            int maxShift = Math.min(MAX_SHIFT, Math.max(0, block.limit - block.columns));
            double best = Double.MAX_VALUE;
            int bestX = 0;
            int bestTop = 0;
            boolean bestAbove = false;
            for (int shift = 0; shift <= maxShift; shift++) {
                int x = (columnOf[block.anchor] + shift) * m.column() - m.framePadX();
                int below = free(taken, x, w, h, line, true, m.blockGap());
                int above = free(taken, x, w, h, line - h, false, m.blockGap());
                double shiftCost = shift * m.column() * SHIFT_COST;
                double belowCost = below - line + shiftCost;
                double aboveCost = line - (above + h) + shiftCost;
                // Strictly better only, so that on a tie the nearer column and the turn of sides win.
                boolean preferAbove = aboveCost < belowCost || aboveCost == belowCost && !lastAbove;
                double cost = preferAbove ? aboveCost : belowCost;
                if (cost < best) {
                    best = cost;
                    bestX = x;
                    bestTop = preferAbove ? above : below;
                    bestAbove = preferAbove;
                }
            }
            lastAbove = bestAbove;
            taken.add(new Rect(bestX, bestTop, w, h));
            block.x = bestX + m.framePadX();
            block.y = bestTop + block.top(m);
        }
    }

    /** The first top, moving away from the line, at which a rectangle touches nothing taken. */
    private static int free(List<Rect> taken, int x, int w, int h, int start, boolean down, int gap) {
        int top = start;
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Rect other : taken) {
                boolean overlap = x < other.x() + other.w() + gap && x + w + gap > other.x() && top < other.y() + other.h() + gap && top + h + gap > other.y();
                if (overlap) {
                    top = down ? other.y() + other.h() + gap : other.y() - gap - h;
                    moved = true;
                }
            }
        }
        return top;
    }

    /** Everything moved so that the frames and quests start at zero. */
    private static Map<String, Placed> normalize(Map<String, Node> nodes, Map<String, int[]> position, List<Block> blocks, Map<String, Integer> depth, Metrics m) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (int[] at : position.values()) {
            minX = Math.min(minX, at[0]);
            minY = Math.min(minY, at[1]);
        }
        for (Block block : blocks) {
            minX = Math.min(minX, block.x - m.framePadX());
            minY = Math.min(minY, block.y - block.top(m));
        }
        Map<String, Placed> placed = new LinkedHashMap<>();
        for (Node node : nodes.values()) {
            int[] at = position.get(node.id());
            int x = node.fixedX().isPresent() ? at[0] : at[0] - minX;
            int y = node.fixedY().isPresent() ? at[1] : at[1] - minY;
            placed.put(node.id(), new Placed(x, y, depth.get(node.id())));
        }
        return placed;
    }

    private static int ceil(int value, int by) {
        return (value + by - 1) / by;
    }
}
