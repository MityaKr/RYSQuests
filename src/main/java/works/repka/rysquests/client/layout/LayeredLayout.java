package works.repka.rysquests.client.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Layered graph layout (a simplified Sugiyama): layers by longest path, crossings reduced by barycenters over four
 * sweeps. Dependencies always end up to the left, and equal input gives equal output.
 */
public final class LayeredLayout {
    private static final int SWEEPS = 4;

    /** @param requires ids of nodes this one depends on; ids outside the graph are ignored */
    public record Node(String id, List<String> requires, OptionalInt fixedX, OptionalInt fixedY) {
        public Node(String id, List<String> requires) {
            this(id, requires, OptionalInt.empty(), OptionalInt.empty());
        }
    }

    public record Metrics(int nodeWidth, int nodeHeight, int gapX, int gapY, int grid) {
    }

    public record Placed(int x, int y, int layer) {
    }

    private LayeredLayout() {
    }

    public static Map<String, Placed> compute(List<Node> input, Metrics metrics) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        input.forEach(node -> nodes.put(node.id(), node));
        Map<String, List<String>> predecessors = new HashMap<>();
        Map<String, List<String>> successors = new HashMap<>();
        nodes.keySet().forEach(id -> {
            predecessors.put(id, new ArrayList<>());
            successors.put(id, new ArrayList<>());
        });
        for (Node node : nodes.values()) {
            for (String dependency : node.requires()) {
                if (nodes.containsKey(dependency)) {
                    predecessors.get(node.id()).add(dependency);
                    successors.get(dependency).add(node.id());
                }
            }
        }
        Map<String, Integer> layerOf = assignLayers(nodes, predecessors, successors, metrics);
        List<List<String>> layers = group(nodes, layerOf);
        for (int sweep = 0; sweep < SWEEPS; sweep++) {
            order(layers, predecessors, false);
            order(layers, successors, true);
        }
        return place(nodes, layers, metrics);
    }

    private static Map<String, Integer> assignLayers(Map<String, Node> nodes, Map<String, List<String>> predecessors,
            Map<String, List<String>> successors, Metrics metrics) {
        Map<String, Integer> layer = new HashMap<>();
        Map<String, Integer> waiting = new HashMap<>();
        List<String> ready = new ArrayList<>();
        for (String id : nodes.keySet()) {
            waiting.put(id, predecessors.get(id).size());
            layer.put(id, fixedLayer(nodes.get(id), metrics));
            if (predecessors.get(id).isEmpty()) {
                ready.add(id);
            }
        }
        for (int i = 0; i < ready.size(); i++) {
            String id = ready.get(i);
            for (String next : successors.get(id)) {
                layer.merge(next, layer.get(id) + 1, Math::max);
                if (waiting.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        return layer;
    }

    private static int fixedLayer(Node node, Metrics metrics) {
        return node.fixedX().isPresent() ? Math.max(0, node.fixedX().getAsInt() / (metrics.nodeWidth() + metrics.gapX())) : 0;
    }

    private static List<List<String>> group(Map<String, Node> nodes, Map<String, Integer> layerOf) {
        int count = layerOf.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        List<List<String>> layers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            layers.add(new ArrayList<>());
        }
        nodes.keySet().forEach(id -> layers.get(layerOf.get(id)).add(id));
        return layers;
    }

    private static void order(List<List<String>> layers, Map<String, List<String>> neighbours, boolean reverse) {
        Map<String, Double> position = new HashMap<>();
        layers.forEach(layer -> {
            for (int i = 0; i < layer.size(); i++) {
                position.put(layer.get(i), (double) i);
            }
        });
        int size = layers.size();
        for (int i = 1; i < size; i++) {
            List<String> layer = layers.get(reverse ? size - 1 - i : i);
            Map<String, Double> barycenter = new HashMap<>();
            for (String id : layer) {
                barycenter.put(id, neighbours.get(id).stream().mapToDouble(position::get).average().orElse(position.get(id)));
            }
            layer.sort(Comparator.comparingDouble(barycenter::get));
            for (int j = 0; j < layer.size(); j++) {
                position.put(layer.get(j), (double) j);
            }
        }
    }

    private static Map<String, Placed> place(Map<String, Node> nodes, List<List<String>> layers, Metrics metrics) {
        int tallest = layers.stream().mapToInt(List::size).max().orElse(0);
        int rowHeight = metrics.nodeHeight() + metrics.gapY();
        Map<String, Placed> placed = new LinkedHashMap<>();
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            List<String> layer = layers.get(layerIndex);
            int x = snap(layerIndex * (metrics.nodeWidth() + metrics.gapX()), metrics.grid());
            int top = (tallest - layer.size()) * rowHeight / 2;
            for (int row = 0; row < layer.size(); row++) {
                Node node = nodes.get(layer.get(row));
                int y = snap(top + row * rowHeight, metrics.grid());
                placed.put(node.id(), new Placed(node.fixedX().orElse(x), node.fixedY().orElse(y), layerIndex));
            }
        }
        return placed;
    }

    private static int snap(int value, int grid) {
        return Math.round((float) value / grid) * grid;
    }
}
