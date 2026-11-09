package works.repka.rysquests.client.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.client.layout.LayeredLayout.Metrics;
import works.repka.rysquests.client.layout.LayeredLayout.Node;
import works.repka.rysquests.client.layout.LayeredLayout.Placed;

class LayeredLayoutTest {
    private static final Metrics METRICS = new Metrics(84, 24, 22, 11, 1);

    private static List<Node> graph(int size, long seed) {
        Random random = new Random(seed);
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            List<String> requires = new ArrayList<>();
            for (int k = random.nextInt(3); k > 0 && i > 0; k--) {
                requires.add("n" + random.nextInt(i));
            }
            nodes.add(new Node("n" + i, requires));
        }
        return nodes;
    }

    @Test
    void equalInputGivesEqualOutput() {
        assertEquals(LayeredLayout.compute(graph(300, 7), METRICS), LayeredLayout.compute(graph(300, 7), METRICS));
    }

    @Test
    void dependenciesAreToTheLeft() {
        List<Node> nodes = graph(500, 3);
        Map<String, Placed> placed = LayeredLayout.compute(nodes, METRICS);

        for (Node node : nodes) {
            for (String dependency : node.requires()) {
                assertTrue(placed.get(dependency).x() < placed.get(node.id()).x(), node.id() + " after " + dependency);
            }
        }
    }

    @Test
    void nodesDoNotOverlap() {
        List<Placed> placed = List.copyOf(LayeredLayout.compute(graph(400, 11), METRICS).values());

        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                boolean apartX = Math.abs(placed.get(i).x() - placed.get(j).x()) >= METRICS.nodeWidth();
                boolean apartY = Math.abs(placed.get(i).y() - placed.get(j).y()) >= METRICS.nodeHeight();
                assertTrue(apartX || apartY, "overlap " + i + " and " + j);
            }
        }
    }

    @Test
    void fixedNodesStayWhereTheAuthorPutThem() {
        List<Node> nodes = List.of(new Node("a", List.of()), new Node("b", List.of("a"), OptionalInt.of(500), OptionalInt.of(77)));

        Placed b = LayeredLayout.compute(nodes, METRICS).get("b");

        assertEquals(500, b.x());
        assertEquals(77, b.y());
    }

    @Test
    void edgesToOutsideTheGraphAreIgnored() {
        Map<String, Placed> placed = LayeredLayout.compute(List.of(new Node("a", List.of("elsewhere"))), METRICS);

        assertEquals(0, placed.get("a").layer());
    }

    @Test
    void crossingsAreReducedByOrdering() {
        // Two parallel chains written in crossing order must come out straight.
        List<Node> nodes = List.of(new Node("a1", List.of()), new Node("b1", List.of()), new Node("b2", List.of("b1")), new Node("a2", List.of("a1")));

        Map<String, Placed> placed = LayeredLayout.compute(nodes, METRICS);

        assertFalse(placed.get("a1").y() < placed.get("b1").y() != placed.get("a2").y() < placed.get("b2").y());
    }

    @Test
    void twoThousandNodesUnderTwoHundredMilliseconds() {
        List<Node> nodes = graph(2000, 5);
        LayeredLayout.compute(nodes, METRICS);

        long start = System.nanoTime();
        LayeredLayout.compute(nodes, METRICS);

        assertTrue((System.nanoTime() - start) / 1_000_000 < 200);
    }
}
