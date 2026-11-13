package works.repka.rysquests.client.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds finished branches to fold on the map. A quest folds when it and everything after it in the chapter is done;
 * it is then counted on the nearest later done quest that stays visible, or stays itself if there is none.
 */
public final class CollapsedBranches {
    /** @param hidden quests to leave out; @param foldedInto how many hidden quests each visible one stands for */
    public record Result(Set<String> hidden, Map<String, Integer> foldedInto) {
    }

    private CollapsedBranches() {
    }

    public static Result compute(List<LayeredLayout.Node> nodes, Predicate<String> done) {
        Map<String, List<String>> successors = new HashMap<>();
        Set<String> ids = new HashSet<>();
        nodes.forEach(node -> ids.add(node.id()));
        for (LayeredLayout.Node node : nodes) {
            for (String dependency : node.requires()) {
                if (ids.contains(dependency)) {
                    successors.computeIfAbsent(dependency, key -> new ArrayList<>()).add(node.id());
                }
            }
        }
        Map<String, Boolean> finished = new HashMap<>();
        Set<String> hidden = new HashSet<>();
        Map<String, Integer> folded = new HashMap<>();
        for (LayeredLayout.Node node : nodes) {
            if (isFinished(node.id(), successors, done, finished) && !successors.getOrDefault(node.id(), List.of()).isEmpty()) {
                hidden.add(node.id());
                String target = lastDone(node.id(), successors);
                folded.merge(target, 1, Integer::sum);
            }
        }
        return new Result(hidden, folded);
    }

    private static boolean isFinished(String id, Map<String, List<String>> successors, Predicate<String> done, Map<String, Boolean> known) {
        Boolean cached = known.get(id);
        if (cached != null) {
            return cached;
        }
        boolean result = done.test(id);
        for (String next : successors.getOrDefault(id, List.of())) {
            result &= isFinished(next, successors, done, known);
        }
        known.put(id, result);
        return result;
    }

    private static String lastDone(String id, Map<String, List<String>> successors) {
        String current = id;
        while (!successors.getOrDefault(current, List.of()).isEmpty()) {
            current = successors.get(current).getFirst();
        }
        return current;
    }
}
