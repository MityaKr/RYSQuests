package works.repka.rysquests.client.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.client.layout.ChapterLayout.Metrics;
import works.repka.rysquests.client.layout.ChapterLayout.Node;
import works.repka.rysquests.client.layout.LayeredLayout.Placed;

class ChapterLayoutTest {
    /** The map's own sizes. */
    private static final Metrics METRICS = new Metrics(160, 47, 48, 24, 12, 24, 12, 10, 20, 34, 14);

    /** A chain of main path quests, and blocks of optional quests hanging off its steps, as in the chapters imported from FTB. */
    private static List<Node> chapter(int steps, int[] blocksPerStep, int perBlock) {
        List<Node> nodes = new ArrayList<>();
        for (int s = 0; s < steps; s++) {
            nodes.add(new Node("main" + s, s == 0 ? List.of() : List.of("main" + (s - 1))));
        }
        for (int s = 0; s < steps; s++) {
            for (int b = 0; b < blocksPerStep[s]; b++) {
                for (int q = 0; q < perBlock; q++) {
                    nodes.add(new Node("g" + s + "_" + b + "_" + q, List.of("main" + s), "g" + s + "_" + b));
                }
            }
        }
        return nodes;
    }

    private static List<String> groupsOf(List<Node> nodes) {
        return nodes.stream().map(Node::group).flatMap(Optional::stream).distinct().toList();
    }

    private static Map<String, Placed> layout(List<Node> nodes) {
        return ChapterLayout.compute(nodes, groupsOf(nodes), METRICS);
    }

    private static int tallestColumn(Map<String, Placed> placed) {
        Map<Integer, Integer> columns = new HashMap<>();
        placed.values().forEach(at -> columns.merge(at.x(), 1, Integer::sum));
        return columns.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static List<Node> random(int size, long seed) {
        Random random = new Random(seed);
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            List<String> requires = new ArrayList<>();
            for (int k = random.nextInt(3); k > 0 && i > 0; k--) {
                requires.add("n" + random.nextInt(i));
            }
            Optional<String> group = random.nextInt(3) == 0 ? Optional.of("g" + random.nextInt(6)) : Optional.empty();
            nodes.add(new Node("n" + i, requires, group, group.isPresent(), OptionalInt.empty(), OptionalInt.empty()));
        }
        return nodes;
    }

    @Test
    void equalInputGivesEqualOutput() {
        assertEquals(layout(random(300, 7)), layout(random(300, 7)));
    }

    @Test
    void dependenciesAreToTheLeft() {
        List<Node> nodes = random(500, 3);
        Map<String, Placed> placed = layout(nodes);

        for (Node node : nodes) {
            for (String dependency : node.requires()) {
                assertTrue(placed.get(dependency).x() < placed.get(node.id()).x(), node.id() + " after " + dependency);
            }
        }
    }

    @Test
    void nodesDoNotOverlap() {
        for (List<Node> nodes : List.of(random(400, 11), chapter(3, new int[] {0, 0, 7}, 6), chapter(12, new int[] {0, 1, 1, 1, 2, 0, 1, 1, 1, 1, 0, 1}, 6))) {
            List<Placed> placed = List.copyOf(layout(nodes).values());
            for (int i = 0; i < placed.size(); i++) {
                for (int j = i + 1; j < placed.size(); j++) {
                    boolean apartX = Math.abs(placed.get(i).x() - placed.get(j).x()) >= METRICS.nodeWidth();
                    boolean apartY = Math.abs(placed.get(i).y() - placed.get(j).y()) >= METRICS.nodeHeight();
                    assertTrue(apartX || apartY, "overlap " + i + " and " + j);
                }
            }
        }
    }

    /** "Логистика": three main steps and seven blocks on the last one used to make a column of 38. */
    @Test
    void blocksOnOneStepSpreadUpAndDown() {
        List<Node> nodes = chapter(3, new int[] {0, 0, 7}, 6);
        Map<String, Placed> placed = layout(nodes);

        assertTrue(tallestColumn(placed) <= 14, "tallest column " + tallestColumn(placed));
        int line = placed.get("main2").y();
        boolean above = placed.keySet().stream().filter(id -> id.startsWith("g")).anyMatch(id -> placed.get(id).y() < line);
        boolean below = placed.keySet().stream().filter(id -> id.startsWith("g")).anyMatch(id -> placed.get(id).y() > line);
        assertTrue(above && below, "blocks on both sides of the main path");
    }

    @Test
    void theMainPathIsOneLine() {
        Map<String, Placed> placed = layout(chapter(12, new int[] {0, 1, 1, 1, 2, 0, 1, 1, 1, 1, 0, 1}, 6));

        for (int s = 1; s < 12; s++) {
            assertEquals(placed.get("main0").y(), placed.get("main" + s).y(), "main" + s);
        }
    }

    @Test
    void aGroupIsAGridThreeInARow() {
        Map<String, Placed> placed = layout(chapter(1, new int[] {1}, 6));

        long columns = placed.keySet().stream().filter(id -> id.startsWith("g")).map(id -> placed.get(id).x()).distinct().count();
        long rows = placed.keySet().stream().filter(id -> id.startsWith("g")).map(id -> placed.get(id).y()).distinct().count();
        assertEquals(3, columns);
        assertEquals(2, rows);
    }

    /** "Летопись": no dependencies inside, only groups: blocks side by side, none taller than its own grid. */
    @Test
    void groupsWithoutAMainPathStandSideBySide() {
        List<Node> nodes = new ArrayList<>();
        int[] sizes = {20, 5, 9, 2, 30};
        for (int g = 0; g < sizes.length; g++) {
            for (int q = 0; q < sizes[g]; q++) {
                nodes.add(new Node("c" + g + "_" + q, List.of(), "c" + g));
            }
        }
        Map<String, Placed> placed = layout(nodes);

        assertTrue(tallestColumn(placed) <= 16, "tallest column " + tallestColumn(placed));
        assertTrue(placed.get("c1_0").x() > placed.get("c0_0").x(), "the second block stands to the right of the first");
    }

    @Test
    void aLongColumnWithoutGroupsWraps() {
        List<Node> nodes = new ArrayList<>(List.of(new Node("root", List.of())));
        for (int i = 0; i < 10; i++) {
            nodes.add(new Node("leaf" + i, List.of("root")));
        }
        Map<String, Placed> placed = layout(nodes);

        assertTrue(tallestColumn(placed) <= ChapterLayout.MAX_STACK);
    }

    /** The label above a frame, and the selected quest, which is larger, never cover each other. */
    @Test
    void framesKeepRoomForTheirLabels() {
        List<Node> nodes = chapter(12, new int[] {0, 1, 1, 1, 2, 0, 1, 1, 1, 1, 0, 1}, 6);
        Map<String, Placed> placed = layout(nodes);
        Map<String, int[]> frames = new HashMap<>();
        for (Node node : nodes) {
            node.group().ifPresent(group -> {
                Placed at = placed.get(node.id());
                frames.merge(group, new int[] {at.x(), at.y(), at.x() + METRICS.nodeWidth(), at.y() + METRICS.nodeHeight()},
                        (a, b) -> new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])});
            });
        }
        List<int[]> boxes = new ArrayList<>();
        frames.values().forEach(f -> boxes.add(new int[] {f[0] - 12, f[1] - 24 - 10, f[2] + 12, f[3] + 12}));
        int frameCount = boxes.size();
        for (Node node : nodes) {
            if (node.group().isEmpty()) {
                Placed at = placed.get(node.id());
                boxes.add(new int[] {at.x() - 34, at.y() - 14, at.x() + 160 + 34, at.y() + 47 + 14});
            }
        }
        // Frames against frames and against the main path; the main path's own quests may share their margins.
        for (int i = 0; i < frameCount; i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                int[] a = boxes.get(i);
                int[] b = boxes.get(j);
                boolean apart = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1];
                assertTrue(apart, "boxes " + i + " and " + j + " overlap");
            }
        }
    }

    @Test
    void fixedNodesStayWhereTheAuthorPutThem() {
        List<Node> nodes = List.of(new Node("a", List.of()), new Node("b", List.of("a"), Optional.empty(), false, OptionalInt.of(500), OptionalInt.of(77)));

        Placed b = layout(nodes).get("b");

        assertEquals(500, b.x());
        assertEquals(77, b.y());
    }

    @Test
    void edgesToOutsideTheChapterAreIgnored() {
        Map<String, Placed> placed = layout(List.of(new Node("a", List.of("elsewhere"))));

        assertEquals(0, placed.get("a").layer());
    }

    @Test
    void twoThousandNodesUnderTwoHundredMilliseconds() {
        List<Node> nodes = random(2000, 5);
        layout(nodes);

        long start = System.nanoTime();
        layout(nodes);

        assertTrue((System.nanoTime() - start) / 1_000_000 < 200);
    }
}
