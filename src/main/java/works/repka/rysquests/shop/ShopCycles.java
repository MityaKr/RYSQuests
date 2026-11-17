package works.repka.rysquests.shop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.ShopOffer;

/**
 * Finds chains of trades that end up with more than they started with, the classic way of printing money: buy a stack
 * and sell it back for more. Each trade with one thing on each side is an edge; a cycle whose rates multiply to more
 * than one is such a chain. Bellman-Ford on the logarithms of the rates finds it.
 */
public final class ShopCycles {
    private static final double EPSILON = 1e-9;

    private record Edge(String from, String to, double weight, ResourceLocation offer) {
    }

    private ShopCycles() {
    }

    private static String node(ShopOffer.Entry entry) {
        // An item with components is not the plain one: it cannot pay for anything, so it never closes a chain.
        return entry.type().getSerializedName() + ":" + entry.id() + entry.components().map(patch -> " " + patch).orElse("");
    }

    /** @return the trades of each profitable chain found */
    public static List<List<ResourceLocation>> find(Collection<ShopOffer> offers) {
        List<Edge> edges = new ArrayList<>();
        Set<String> nodes = new HashSet<>();
        for (ShopOffer offer : offers) {
            if (offer.cost().size() == 1 && offer.gives().size() == 1) {
                ShopOffer.Entry cost = offer.cost().getFirst();
                ShopOffer.Entry gives = offer.gives().getFirst();
                edges.add(new Edge(node(cost), node(gives), -Math.log((double) gives.amount() / cost.amount()), offer.id()));
                nodes.add(node(cost));
                nodes.add(node(gives));
            }
        }
        List<List<ResourceLocation>> cycles = new ArrayList<>();
        Set<Set<ResourceLocation>> seen = new HashSet<>();
        for (String start : nodes) {
            List<ResourceLocation> cycle = cycleFrom(start, nodes, edges);
            if (cycle != null && seen.add(new HashSet<>(cycle))) {
                cycles.add(cycle);
            }
        }
        return cycles;
    }

    private static List<ResourceLocation> cycleFrom(String start, Set<String> nodes, List<Edge> edges) {
        java.util.Map<String, Double> distance = new java.util.HashMap<>();
        java.util.Map<String, Edge> previous = new java.util.HashMap<>();
        distance.put(start, 0.0);
        String relaxed = null;
        for (int round = 0; round <= nodes.size(); round++) {
            relaxed = null;
            for (Edge edge : edges) {
                Double from = distance.get(edge.from);
                if (from != null && from + edge.weight < distance.getOrDefault(edge.to, Double.POSITIVE_INFINITY) - EPSILON) {
                    distance.put(edge.to, from + edge.weight);
                    previous.put(edge.to, edge);
                    relaxed = edge.to;
                }
            }
            if (relaxed == null) {
                return null;
            }
        }
        String inCycle = relaxed;
        for (int i = 0; i < nodes.size(); i++) {
            inCycle = previous.get(inCycle).from;
        }
        List<ResourceLocation> cycle = new ArrayList<>();
        String cursor = inCycle;
        do {
            Edge edge = previous.get(cursor);
            cycle.addFirst(edge.offer);
            cursor = edge.from;
        } while (!cursor.equals(inCycle));
        return cycle;
    }
}
