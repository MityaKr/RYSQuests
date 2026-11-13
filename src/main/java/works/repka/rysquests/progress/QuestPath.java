package works.repka.rysquests.progress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RequiresMode;

/**
 * The way to a quest that is still closed (plan, section 3, "Closed quest"; pictures 21b, 22): every unfinished quest it still needs, in
 * an order in which each comes after what it needs, the goal last. One count: a step is an unfinished quest of the way, the goal is
 * the last step. Where a quest needs any one of several (requires mode "any" or "at least n"), the way takes the nearest ones, those
 * with the fewest steps of their own, and remembers the others as the other way.
 *
 * <p>This is the data the map lights the way with ("lead to the goal"): {@link #steps} in order, the goal last.
 *
 * @param goal     the quest the way leads to
 * @param steps    the unfinished quests of the way in order, the goal last; the first one can be done first
 * @param done     how many quests the steps need directly that are already done ("выполнено 1")
 * @param choices  the places where the way picked some of several quests, in the order of the steps
 */
public record QuestPath(Quest goal, List<Quest> steps, int done, List<Choice> choices) {
    /**
     * A quest that needs any of several, and which of them the way takes.
     *
     * @param quest  the quest that needs any of them
     * @param taken  the ones the way goes through
     * @param others the unfinished ones it leaves aside, nearest first
     */
    public record Choice(Quest quest, List<Quest> taken, List<Quest> others) {
    }

    /** The number of steps, the goal included. */
    public int count() {
        return steps.size();
    }

    /** The first step, the one to start with. */
    public Quest first() {
        return steps.getFirst();
    }

    /**
     * The way to a quest.
     *
     * @param quests   the book's quests by id
     * @param progress the team's progress
     */
    public static QuestPath of(Quest goal, Map<ResourceLocation, Quest> quests, Function<ResourceLocation, QuestProgress> progress) {
        Walker walker = new Walker(quests, progress);
        walker.visit(goal);
        Set<ResourceLocation> stepIds = new HashSet<>();
        walker.order.forEach(quest -> stepIds.add(quest.id()));
        Set<ResourceLocation> doneNeeds = new HashSet<>();
        for (Quest step : walker.order) {
            for (ResourceLocation need : step.requires()) {
                if (quests.containsKey(need) && progress.apply(need).everDone()) {
                    doneNeeds.add(need);
                }
            }
        }
        List<Choice> choices = new ArrayList<>();
        for (Quest step : walker.order) {
            Choice choice = walker.choices.get(step.id());
            if (choice != null) {
                choices.add(choice);
            }
        }
        return new QuestPath(goal, List.copyOf(walker.order), doneNeeds.size(), List.copyOf(choices));
    }

    /** How many steps a quest takes by itself: it and everything unfinished it needs, 0 when it is done. */
    public static int cost(Quest quest, Map<ResourceLocation, Quest> quests, Function<ResourceLocation, QuestProgress> progress) {
        return new Walker(quests, progress).needed(quest).size();
    }

    /** The other way through a quest that needs any of several: its unfinished requirements, nearest first, with their costs. */
    public static List<Option> options(Quest quest, Map<ResourceLocation, Quest> quests, Function<ResourceLocation, QuestProgress> progress) {
        Walker walker = new Walker(quests, progress);
        List<Option> options = new ArrayList<>();
        for (ResourceLocation id : quest.requires()) {
            Quest need = quests.get(id);
            if (need != null) {
                options.add(new Option(need, walker.needed(need).size(), progress.apply(id).everDone()));
            }
        }
        List<Option> sorted = new ArrayList<>(options);
        sorted.sort(Comparator.comparingInt((Option option) -> option.done() ? 0 : 1).thenComparingInt(Option::cost));
        return sorted;
    }

    /** One requirement of a quest that needs any of several. @param cost steps it takes, 0 when done */
    public record Option(Quest quest, int cost, boolean done) {
    }

    /** How many requirements a quest still waits for before it opens: 0 when open; the "n" of "any one of three". */
    public static int needs(Quest quest, Function<ResourceLocation, QuestProgress> progress) {
        int total = quest.requires().size();
        long done = quest.requires().stream().filter(id -> progress.apply(id).everDone()).count();
        int wanted = switch (quest.requiresMode()) {
            case RequiresMode.AtLeast atLeast -> Math.min(atLeast.n(), total);
            case RequiresMode.Named named -> named == RequiresMode.ANY ? Math.min(1, total) : total;
        };
        return (int) Math.max(0, wanted - done);
    }

    /** Whether a quest needs only some of its requirements: "any" or "at least n" below their count. */
    public static boolean anyOf(Quest quest) {
        int total = quest.requires().size();
        return total > 1 && switch (quest.requiresMode()) {
            case RequiresMode.AtLeast atLeast -> atLeast.n() < total;
            case RequiresMode.Named named -> named == RequiresMode.ANY;
        };
    }

    /** Walks the requirements depth first, so that what a quest needs is placed before it. */
    private static final class Walker {
        private final Map<ResourceLocation, Quest> quests;
        private final Function<ResourceLocation, QuestProgress> progress;
        private final Map<ResourceLocation, Set<ResourceLocation>> needed = new HashMap<>();
        private final Set<ResourceLocation> walking = new HashSet<>();
        private final Set<ResourceLocation> placed = new HashSet<>();
        private final List<Quest> order = new ArrayList<>();
        private final Map<ResourceLocation, Choice> choices = new HashMap<>();

        Walker(Map<ResourceLocation, Quest> quests, Function<ResourceLocation, QuestProgress> progress) {
            this.quests = quests;
            this.progress = progress;
        }

        /** Places the quest and what it needs, in order. */
        void visit(Quest quest) {
            if (placed.contains(quest.id()) || progress.apply(quest.id()).everDone()) {
                return;
            }
            placed.add(quest.id());
            for (Quest need : chosen(quest)) {
                visit(need);
            }
            order.add(quest);
        }

        /** The requirements the way goes through: all of them, or the nearest unfinished ones of "any". */
        private List<Quest> chosen(Quest quest) {
            List<Quest> unfinished = new ArrayList<>();
            for (ResourceLocation id : quest.requires()) {
                Quest need = quests.get(id);
                if (need != null && !progress.apply(id).everDone()) {
                    unfinished.add(need);
                }
            }
            if (!anyOf(quest)) {
                return unfinished;
            }
            int wanted = needs(quest, progress);
            if (wanted <= 0) {
                return List.of();
            }
            List<Quest> nearest = new ArrayList<>(unfinished);
            nearest.sort(Comparator.comparingInt(need -> needed(need).size()));
            List<Quest> taken = List.copyOf(nearest.subList(0, Math.min(wanted, nearest.size())));
            List<Quest> others = List.copyOf(nearest.subList(taken.size(), nearest.size()));
            if (!others.isEmpty()) {
                choices.put(quest.id(), new Choice(quest, taken, others));
            }
            return taken;
        }

        /** The quest and everything unfinished it needs, along the nearest ways. */
        Set<ResourceLocation> needed(Quest quest) {
            Set<ResourceLocation> known = needed.get(quest.id());
            if (known != null) {
                return known;
            }
            if (progress.apply(quest.id()).everDone()) {
                return Set.of();
            }
            if (!walking.add(quest.id())) {
                // A cycle the validator should have caught: count the quest once and stop.
                return Set.of(quest.id());
            }
            Set<ResourceLocation> all = new LinkedHashSet<>();
            List<Set<ResourceLocation>> parts = new ArrayList<>();
            for (ResourceLocation id : quest.requires()) {
                Quest need = quests.get(id);
                if (need != null && !progress.apply(id).everDone()) {
                    parts.add(needed(need));
                }
            }
            if (anyOf(quest)) {
                int wanted = needs(quest, progress);
                parts.sort(Comparator.comparingInt(Set::size));
                parts = parts.subList(0, Math.min(Math.max(0, wanted), parts.size()));
            }
            parts.forEach(all::addAll);
            all.add(quest.id());
            walking.remove(quest.id());
            needed.put(quest.id(), all);
            return all;
        }
    }

    /** The step a quest has on the way, from 1, or empty when it is not on it. */
    public Optional<Integer> stepOf(Quest quest) {
        int index = steps.indexOf(quest);
        return index < 0 ? Optional.empty() : Optional.of(index + 1);
    }
}
