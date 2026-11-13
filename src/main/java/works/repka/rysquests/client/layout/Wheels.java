package works.repka.rysquests.client.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Wheels of one chapter, the first step of every form. A wheel is a main quest with the quests that hang on it sitting on orbits
 * around it; a grove is a wheel without a main quest, for side quests that hang on nothing.
 *
 * <p>Two ways to tell the main quest, by {@link Style}: a book that marks side quests with {@code optional} (RepYS) hangs them on
 * their nearest main ancestor; a book where the flag does not divide the book (ATM10) is read as a tree, where a quest something
 * depends on is a main quest and a leaf sits on the orbit of its parent ({@code quests-hub-fable}).
 *
 * <p>Angles on the orbits come from a random seeded by the chapter id: the same chapter always lies the same, whatever the rest
 * of the book is, so that one chapter can be laid out again on its own.
 */
public final class Wheels {
    /** How the main quests of a chapter are told, with the sizes of that layout. */
    public enum Style {
        /** Main quests are those without {@code optional}; sizes of {@code quests-skilltree-fable/layout.py}. */
        OPTIONAL(11f, 16f, 12f, 11f, 6f, 6f),
        /** Main quests are those something depends on; sizes of {@code quests-hub-fable/hub.py}. */
        TREE(9f, 13f, 10f, 7.5f, 5f, 4f);

        /** Distance between neighbours on an orbit. */
        final float step;
        /** Radius of the first orbit around a main quest. */
        final float orbit0;
        /** From one orbit to the next. */
        final float ring;
        /** Radius of a wheel with nothing on its orbits. */
        final float bare;
        /** From the last orbit to the edge of the wheel. */
        final float rim;
        /** Gap between two wheels. */
        public final float pad;

        Style(float step, float orbit0, float ring, float bare, float rim, float pad) {
            this.step = step;
            this.orbit0 = orbit0;
            this.ring = ring;
            this.bare = bare;
            this.rim = rim;
            this.pad = pad;
        }
    }

    /** At most this share of optional quests and the flag is taken not to divide the book: wheels are read as a tree. */
    public static final double TREE_BELOW = 0.25;

    private Wheels() {
    }

    /** The style a book is laid out in. */
    public static Style style(MapBook book) {
        int total = book.quests().size();
        return total == 0 || book.optionalCount() < total * TREE_BELOW ? Style.TREE : Style.OPTIONAL;
    }

    /** One wheel. Its centre {@code (x, y)} is set by the form; members sit at {@code (x + relX, y + relY)}. */
    public static final class Wheel {
        public final int chapter;
        /** The main quest in the middle, or -1 for a grove. */
        public final int hub;
        public final int[] members;
        public final float[] relX;
        public final float[] relY;
        public final float[] orbits;
        public final float r;
        float x;
        float y;

        Wheel(int chapter, int hub, int[] members, float[] relX, float[] relY, float[] orbits, float r) {
            this.chapter = chapter;
            this.hub = hub;
            this.members = members;
            this.relX = relX;
            this.relY = relY;
            this.orbits = orbits;
            this.r = r;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        void at(float nx, float ny) {
            x = nx;
            y = ny;
        }

        /**
         * The same wheel not yet placed, with every quest index moved by {@code shift}: a chapter's quests are numbered in a row,
         * so a kept chapter fits a book where the chapters before it grew or shrank. Orbit arrays are shared, they never change.
         */
        Wheel copy(int shift, int newChapter) {
            int[] moved = members.clone();
            for (int i = 0; i < moved.length; i++) {
                moved[i] += shift;
            }
            return new Wheel(newChapter, hub < 0 ? -1 : hub + shift, moved, relX, relY, orbits, r);
        }
    }

    /**
     * The chapter's wheels in packing order: main quests so that chains run one after another, each after the ones it depends on,
     * then the groves.
     */
    public static List<Wheel> of(MapBook book, MapBook.Chap chapter, Style style) {
        List<Wheel> wheels = style == Style.OPTIONAL ? byOptional(book, chapter, style) : byTree(book, chapter, style);
        return order(book, wheels);
    }

    // --- OPTIONAL: side quests hang on their nearest main ancestor ---

    private static List<Wheel> byOptional(MapBook book, MapBook.Chap chapter, Style style) {
        Random random = new Random(seed(chapter));
        List<Integer> mains = new ArrayList<>();
        List<Integer> sides = new ArrayList<>();
        for (int q : chapter.quests()) {
            (book.quests().get(q).optional() ? sides : mains).add(q);
        }
        Map<Integer, List<Integer>> by = new LinkedHashMap<>();
        List<Integer> free = new ArrayList<>();
        for (int side : sides) {
            int anchor = nearestMain(book, chapter.index(), side);
            if (anchor < 0) {
                free.add(side);
            } else {
                by.computeIfAbsent(anchor, key -> new ArrayList<>()).add(side);
            }
        }
        List<Wheel> out = new ArrayList<>();
        for (int main : mains) {
            out.add(build(book, chapter.index(), main, by.getOrDefault(main, List.of()), style, random));
        }
        // groves: linked groups of side quests without a main ancestor; a group of four or more is its own, the rest by ten
        Set<Integer> freeSet = new HashSet<>(free);
        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (int q : free) {
            for (int r : book.quests().get(q).requires()) {
                if (freeSet.contains(r)) {
                    adj.computeIfAbsent(q, key -> new ArrayList<>()).add(r);
                    adj.computeIfAbsent(r, key -> new ArrayList<>()).add(q);
                }
            }
        }
        Set<Integer> seen = new HashSet<>();
        List<Integer> small = new ArrayList<>();
        for (int q : free) {
            if (!seen.add(q)) {
                continue;
            }
            List<Integer> comp = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(q));
            while (!queue.isEmpty()) {
                int c = queue.poll();
                comp.add(c);
                for (int n : adj.getOrDefault(c, List.of())) {
                    if (seen.add(n)) {
                        queue.add(n);
                    }
                }
            }
            if (comp.size() >= 4) {
                out.add(build(book, chapter.index(), -1, comp, style, random));
            } else {
                small.addAll(comp);
            }
        }
        for (int k = 0; k < small.size(); k += 10) {
            out.add(build(book, chapter.index(), -1, small.subList(k, Math.min(small.size(), k + 10)), style, random));
        }
        return out;
    }

    /** The nearest main quest of the same chapter up the dependencies, through side quests only; -1 if none. */
    private static int nearestMain(MapBook book, int chapter, int side) {
        Set<Integer> seen = new HashSet<>(List.of(side));
        ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(side));
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int r : book.quests().get(current).requires()) {
                MapBook.Q req = book.quests().get(r);
                if (seen.contains(r) || req.chapter() != chapter) {
                    continue;
                }
                if (!req.optional()) {
                    return r;
                }
                seen.add(r);
                queue.add(r);
            }
        }
        return -1;
    }

    // --- TREE: a quest something depends on is a main quest, leaves sit around their parent ---

    private static List<Wheel> byTree(MapBook book, MapBook.Chap chapter, Style style) {
        Random random = new Random(seed(chapter));
        List<Integer> hubs = new ArrayList<>();
        for (int q : chapter.quests()) {
            if (book.dependents(q).length > 0) {
                hubs.add(q);
            }
        }
        Set<Integer> hubSet = new HashSet<>(hubs);
        Map<Integer, List<Integer>> by = new LinkedHashMap<>();
        List<Integer> free = new ArrayList<>();
        for (int q : chapter.quests()) {
            if (hubSet.contains(q)) {
                continue;
            }
            int parent = -1;
            for (int r : book.quests().get(q).requires()) {
                if (hubSet.contains(r)) {
                    parent = r;
                    break;
                }
            }
            if (parent < 0) {
                free.add(q);
            } else {
                by.computeIfAbsent(parent, key -> new ArrayList<>()).add(q);
            }
        }
        List<Wheel> out = new ArrayList<>();
        for (int hub : hubs) {
            out.add(build(book, chapter.index(), hub, by.getOrDefault(hub, List.of()), style, random));
        }
        // quests linked to nothing: groves of 24
        for (int k = 0; k < free.size(); k += 24) {
            out.add(build(book, chapter.index(), -1, free.subList(k, Math.min(free.size(), k + 24)), style, random));
        }
        return out;
    }

    // --- orbits ---

    private static Wheel build(MapBook book, int chapter, int hub, List<Integer> members, Style style, Random random) {
        List<Integer> flat = new ArrayList<>(members);
        if (style == Style.OPTIONAL) {
            Map<Integer, Integer> depth = depth(book, members);
            flat.sort(Comparator.<Integer>comparingInt(q -> Math.min(depth.get(q), 3)).thenComparingInt(depth::get).thenComparing(q -> book.quests().get(q).id()));
        }
        int n = flat.size();
        float[] relX = new float[n];
        float[] relY = new float[n];
        List<Float> orbits = new ArrayList<>();
        float r;
        if (hub >= 0) {
            r = style.orbit0;
        } else if (n <= 1) {
            r = 0f;
        } else {
            float min = style == Style.OPTIONAL ? 8f : 7f;
            r = (float) Math.max(min, Math.min(n, 8) * style.step / (2 * Math.PI));
        }
        float ringStep = style == Style.OPTIONAL ? style.step + 1 : style.ring;
        int idx = 0;
        while (idx < n) {
            int cap = r > 0 ? Math.max(1, (int) (2 * Math.PI * r / style.step)) : 1;
            int part = Math.min(cap, n - idx);
            double a0 = random.nextDouble() * 2 * Math.PI;
            int div = Math.max(part, r > 0 ? 3 : 1);
            for (int j = 0; j < part; j++) {
                double angle = a0 + 2 * Math.PI * j / div;
                relX[idx + j] = (float) (r * StrictMath.cos(angle));
                relY[idx + j] = (float) (r * StrictMath.sin(angle));
            }
            if (r > 0) {
                orbits.add(r);
            }
            idx += cap;
            r += ringStep;
        }
        float[] orbitArray = new float[orbits.size()];
        for (int i = 0; i < orbitArray.length; i++) {
            orbitArray[i] = orbits.get(i);
        }
        float radius = orbitArray.length > 0 ? orbitArray[orbitArray.length - 1] + style.rim : style.bare;
        return new Wheel(chapter, hub, flat.stream().mapToInt(Integer::intValue).toArray(), relX, relY, orbitArray, radius);
    }

    /** Length of the chain of each member down from the wheel's main quest: 1 for one hanging on it directly. */
    private static Map<Integer, Integer> depth(MapBook book, List<Integer> members) {
        Set<Integer> set = new HashSet<>(members);
        Map<Integer, Integer> depth = new HashMap<>();
        for (int m : members) {
            depthOf(book, m, set, depth, new HashSet<>());
        }
        return depth;
    }

    private static int depthOf(MapBook book, int q, Set<Integer> members, Map<Integer, Integer> depth, Set<Integer> stack) {
        Integer known = depth.get(q);
        if (known != null) {
            return known;
        }
        stack.add(q);
        int best = 1;
        for (int r : book.quests().get(q).requires()) {
            if (members.contains(r) && !stack.contains(r)) {
                best = Math.max(best, depthOf(book, r, members, depth, stack) + 1);
            }
        }
        stack.remove(q);
        depth.put(q, best);
        return best;
    }

    // --- order ---

    /** Main quests in the order of their links, depth first so that chains run together, then the groves. */
    private static List<Wheel> order(MapBook book, List<Wheel> wheels) {
        Map<Integer, Wheel> byHub = new LinkedHashMap<>();
        List<Wheel> groves = new ArrayList<>();
        for (Wheel wheel : wheels) {
            if (wheel.hub >= 0) {
                byHub.put(wheel.hub, wheel);
            } else {
                groves.add(wheel);
            }
        }
        Map<Integer, Integer> need = new HashMap<>();
        Map<Integer, List<Integer>> kids = new HashMap<>();
        for (int hub : byHub.keySet()) {
            int count = 0;
            for (int r : book.quests().get(hub).requires()) {
                if (byHub.containsKey(r)) {
                    count++;
                    kids.computeIfAbsent(r, key -> new ArrayList<>()).add(hub);
                }
            }
            need.put(hub, count);
        }
        Comparator<Integer> byId = Comparator.comparing(q -> book.quests().get(q).id());
        // a stack popped from its end with the ids sorted backwards: the smallest id is taken first, as the Python layouts do
        List<Integer> roots = new ArrayList<>(byHub.keySet().stream().filter(h -> need.get(h) == 0).toList());
        roots.sort(byId.reversed());
        List<Wheel> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        Deque stackList = new Deque(roots);
        while (!stackList.isEmpty()) {
            int hub = stackList.pop();
            if (!seen.add(hub)) {
                continue;
            }
            out.add(byHub.get(hub));
            List<Integer> children = new ArrayList<>(kids.getOrDefault(hub, List.of()));
            children.sort(byId.reversed());
            for (int child : children) {
                int left = need.merge(child, -1, Integer::sum);
                if (left <= 0) {
                    stackList.push(child);
                }
            }
        }
        for (Map.Entry<Integer, Wheel> entry : byHub.entrySet()) {
            if (!seen.contains(entry.getKey())) {
                out.add(entry.getValue());
            }
        }
        out.addAll(groves);
        return out;
    }

    /** A plain list used as the stack of the Python {@code list.pop()}: push appends, pop takes the last. */
    private static final class Deque {
        private final ArrayList<Integer> items = new ArrayList<>();

        Deque(List<Integer> bottomFirst) {
            items.addAll(bottomFirst);
        }

        boolean isEmpty() {
            return items.isEmpty();
        }

        int pop() {
            return items.removeLast();
        }

        void push(int value) {
            items.add(value);
        }
    }

    static long seed(MapBook.Chap chapter) {
        String id = chapter.id().toString();
        long h = 1125899906842597L;
        for (int i = 0; i < id.length(); i++) {
            h = 31 * h + id.charAt(i);
        }
        return h;
    }

    /** All members and main quests of the wheels, for checks. */
    static int[] quests(List<Wheel> wheels) {
        return wheels.stream().flatMapToInt(w -> w.hub >= 0 ? java.util.stream.IntStream.concat(java.util.stream.IntStream.of(w.hub), Arrays.stream(w.members)) : Arrays.stream(w.members)).toArray();
    }
}
