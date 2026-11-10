package works.repka.rysquests.data;

import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.FluidTask;
import works.repka.rysquests.task.CheckboxTask;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.BookReport.Issue;
import works.repka.rysquests.data.BookReport.Severity;
import works.repka.rysquests.task.ItemRef;

/** Links a parsed book and reports what is wrong. Broken quests are left out, the rest of the book still loads. */
public final class BookValidator {
    public record Result(QuestBook book, BookReport report) {
    }

    private BookValidator() {
    }

    public static Result validate(RawBook raw, Predicate<ResourceLocation> itemExists, DynamicOps<JsonElement> ops) {
        List<Issue> issues = new ArrayList<>(raw.issues());
        Map<ResourceLocation, Chapter> chapters = dedupeChapters(raw, issues);
        Map<ResourceLocation, Quest> quests = dedupeQuests(raw, issues);
        Map<ResourceLocation, Guide> guides = checkGuides(raw, itemExists, issues);

        Set<ResourceLocation> broken = new LinkedHashSet<>();
        quests.values().forEach(quest -> checkQuest(quest, chapters, quests, itemExists, broken, issues));
        quests.values().forEach(quest -> checkGuideLink(quest, guides, issues));
        Map<ResourceLocation, SectionDef> sections = SectionValidator.check(raw.sections(), quests, guides, itemExists, issues);
        checkSections(raw.meta(), sections, issues);
        findCycles(quests, broken, issues);
        spreadBrokenDependencies(quests, broken, issues);
        chapters.values().forEach(chapter -> checkChapter(chapter, chapters, issues));

        List<Quest> valid = quests.values().stream().filter(quest -> !broken.contains(quest.id())).toList();
        List<Chapter> ordered = orderChapters(raw.meta(), chapters, issues);
        List<Guide> guideList = List.copyOf(guides.values());
        List<SectionDef> sectionList = List.copyOf(sections.values());
        List<ShopOffer> offers = checkOffers(raw, quests, itemExists, issues);
        List<Achievement> achievementList = checkAchievements(raw, itemExists, issues);
        QuestBook book = QuestBook.create(raw.meta(), ordered, valid, guideList, sectionList, BookHash.compute(ordered, valid, guideList, sectionList, offers, achievementList, ops)).withOffers(offers).withAchievements(achievementList);
        return new Result(book, new BookReport(List.copyOf(issues)));
    }

    private static Map<ResourceLocation, Chapter> dedupeChapters(RawBook raw, List<Issue> issues) {
        Map<ResourceLocation, Chapter> chapters = new LinkedHashMap<>();
        for (Chapter chapter : raw.chapters()) {
            if (chapters.putIfAbsent(chapter.id(), chapter) != null) {
                issues.add(error("duplicate_chapter", chapter.id(), ""));
            }
        }
        return chapters;
    }

    private static Map<ResourceLocation, Quest> dedupeQuests(RawBook raw, List<Issue> issues) {
        Map<ResourceLocation, Quest> quests = new LinkedHashMap<>();
        for (Quest quest : raw.quests()) {
            if (quests.putIfAbsent(quest.id(), quest) != null) {
                issues.add(error("duplicate_quest", quest.id(), ""));
            }
        }
        return quests;
    }

    /** A guide without readable pages is left out; the rest of the book is not affected. */
    private static Map<ResourceLocation, Guide> checkGuides(RawBook raw, Predicate<ResourceLocation> itemExists, List<Issue> issues) {
        Map<ResourceLocation, Guide> guides = new LinkedHashMap<>();
        for (Guide guide : raw.guides()) {
            if (guides.containsKey(guide.id())) {
                issues.add(error("duplicate_guide", guide.id(), ""));
                continue;
            }
            if (guide.pages().isEmpty()) {
                issues.add(error("empty_guide", guide.id(), ""));
                continue;
            }
            Set<String> pages = new HashSet<>();
            boolean valid = true;
            for (Guide.Page page : guide.pages()) {
                if (!pages.add(page.id())) {
                    issues.add(error("duplicate_guide_page", guide.id(), page.id()));
                    valid = false;
                }
            }
            if (valid) {
                guides.put(guide.id(), guide);
                guide.icon().filter(icon -> !itemExists.test(icon)).ifPresent(icon -> issues.add(warning("unknown_item", guide.id(), icon.toString())));
            }
        }
        return guides;
    }

    private static void checkGuideLink(Quest quest, Map<ResourceLocation, Guide> guides, List<Issue> issues) {
        quest.guide().ifPresent(ref -> {
            Guide guide = guides.get(ref.guide());
            if (guide == null) {
                issues.add(warning("unknown_guide", quest.id(), ref.toString()));
            } else if (ref.page().filter(page -> guide.pageIndex(page) < 0).isPresent()) {
                issues.add(warning("unknown_guide_page", quest.id(), ref.toString()));
            }
        });
    }

    private static void checkSections(BookMeta meta, Map<ResourceLocation, SectionDef> sections, List<Issue> issues) {
        for (String section : meta.sections()) {
            ResourceLocation id = ResourceLocation.tryParse(section);
            boolean custom = id != null && sections.containsKey(id);
            if (!section.equals(Chapter.STORY) && !section.equals(BookMeta.GUIDES) && !section.equals(BookMeta.SHOP) && !section.equals(BookMeta.ACHIEVEMENTS) && !custom) {
                issues.add(new Issue(Severity.WARNING, "unknown_section", "book", section));
            }
        }
    }

    /** Tiers have to grow: the next tier is what a player is told to reach. */
    private static List<Achievement> checkAchievements(RawBook raw, Predicate<ResourceLocation> itemExists, List<Issue> issues) {
        Map<ResourceLocation, Achievement> achievements = new LinkedHashMap<>();
        for (Achievement achievement : raw.achievements()) {
            if (achievements.containsKey(achievement.id())) {
                issues.add(error("duplicate_achievement", achievement.id(), ""));
                continue;
            }
            boolean ascending = true;
            for (int i = 1; i < achievement.tiers().size(); i++) {
                ascending &= achievement.tiers().get(i).amount() > achievement.tiers().get(i - 1).amount();
            }
            if (!ascending) {
                issues.add(error("achievement_tiers_order", achievement.id(), ""));
                continue;
            }
            achievement.icon().filter(icon -> !itemExists.test(icon)).ifPresent(icon -> issues.add(warning("unknown_item", achievement.id(), icon.toString())));
            raw.quests().stream().filter(quest -> quest.id().equals(achievement.questId(achievement.tiers().getFirst()))).findFirst().ifPresent(quest -> {
                TaskDef task = quest.tasks().getFirst();
                boolean takesItems = task instanceof ItemTask item && item.mode() == ItemTask.Mode.CONSUME || task instanceof FluidTask fluid && fluid.mode() == ItemTask.Mode.CONSUME;
                if (takesItems || task instanceof CheckboxTask) {
                    issues.add(warning("achievement_task", achievement.id(), task.type().toString()));
                }
            });
            achievements.put(achievement.id(), achievement);
        }
        return List.copyOf(achievements.values());
    }

    /** A trade with an item that does not exist cannot be made, so it is left out. A broken quest link only closes it. */
    private static List<ShopOffer> checkOffers(RawBook raw, Map<ResourceLocation, Quest> quests, Predicate<ResourceLocation> itemExists, List<Issue> issues) {
        Map<ResourceLocation, ShopOffer> offers = new LinkedHashMap<>();
        for (ShopOffer offer : raw.offers()) {
            if (offers.containsKey(offer.id())) {
                issues.add(error("duplicate_offer", offer.id(), ""));
                continue;
            }
            boolean valid = true;
            for (ShopOffer.Entry entry : java.util.stream.Stream.concat(offer.cost().stream(), offer.gives().stream()).toList()) {
                if (!entry.isCurrency() && entry.item().filter(itemExists).isEmpty()) {
                    issues.add(error("unknown_item", offer.id(), entry.id()));
                    valid = false;
                }
            }
            if (valid) {
                offers.put(offer.id(), offer);
                offer.quest().filter(quest -> !quests.containsKey(quest)).ifPresent(quest -> issues.add(warning("unknown_offer_quest", offer.id(), quest.toString())));
            }
        }
        return List.copyOf(offers.values());
    }

    private static void checkQuest(Quest quest, Map<ResourceLocation, Chapter> chapters, Map<ResourceLocation, Quest> quests,
            Predicate<ResourceLocation> itemExists, Set<ResourceLocation> broken, List<Issue> issues) {
        ResourceLocation id = quest.id();
        Chapter chapter = chapters.get(quest.chapter().orElseThrow());
        if (chapter == null) {
            issues.add(error("unknown_chapter", id, quest.chapter().orElseThrow().toString()));
            broken.add(id);
        } else if (quest.group().isPresent() && chapter.groups().stream().noneMatch(group -> group.id().equals(quest.group().get()))) {
            issues.add(warning("unknown_group", id, quest.group().get()));
        }
        for (ResourceLocation dependency : quest.requires()) {
            if (!quests.containsKey(dependency)) {
                issues.add(error("missing_dependency", id, dependency.toString()));
                broken.add(id);
            }
        }
        for (TaskDef task : quest.tasks()) {
            for (ItemRef ref : task.itemRefs()) {
                if (!ref.tag() && !itemExists.test(ref.id())) {
                    issues.add(error("unknown_item", id, ref.toString()));
                    broken.add(id);
                }
            }
        }
        for (RewardDef reward : quest.rewards()) {
            if (reward.isEmpty()) {
                issues.add(error("empty_reward", id, ""));
                broken.add(id);
            }
            for (ResourceLocation item : reward.itemIds()) {
                if (!itemExists.test(item)) {
                    issues.add(error("unknown_item", id, item.toString()));
                    broken.add(id);
                }
            }
        }
        quest.icon().filter(icon -> !itemExists.test(icon)).ifPresent(icon -> issues.add(warning("unknown_item", id, icon.toString())));
    }

    /** Depth-first search with an explicit stack, so a very long quest chain cannot overflow the thread stack. */
    private static void findCycles(Map<ResourceLocation, Quest> quests, Set<ResourceLocation> broken, List<Issue> issues) {
        Set<ResourceLocation> done = new HashSet<>();
        for (ResourceLocation start : quests.keySet()) {
            if (done.contains(start)) {
                continue;
            }
            Deque<ResourceLocation> path = new ArrayDeque<>();
            Deque<Integer> nextDependency = new ArrayDeque<>();
            Set<ResourceLocation> onPath = new HashSet<>();
            path.push(start);
            nextDependency.push(0);
            onPath.add(start);
            while (!path.isEmpty()) {
                List<ResourceLocation> requires = quests.get(path.peek()).requires();
                int next = nextDependency.pop();
                if (next >= requires.size()) {
                    onPath.remove(path.peek());
                    done.add(path.pop());
                    continue;
                }
                nextDependency.push(next + 1);
                ResourceLocation dependency = requires.get(next);
                if (onPath.contains(dependency)) {
                    reportCycle(path, dependency, broken, issues);
                } else if (quests.containsKey(dependency) && !done.contains(dependency)) {
                    path.push(dependency);
                    nextDependency.push(0);
                    onPath.add(dependency);
                }
            }
        }
    }

    private static void reportCycle(Deque<ResourceLocation> path, ResourceLocation closing, Set<ResourceLocation> broken, List<Issue> issues) {
        List<ResourceLocation> cycle = new ArrayList<>();
        for (ResourceLocation node : path) {
            cycle.add(node);
            if (node.equals(closing)) {
                break;
            }
        }
        broken.addAll(cycle);
        issues.add(error("dependency_cycle", closing, cycle.reversed().toString()));
    }

    private static void spreadBrokenDependencies(Map<ResourceLocation, Quest> quests, Set<ResourceLocation> broken, List<Issue> issues) {
        Map<ResourceLocation, List<ResourceLocation>> dependents = new HashMap<>();
        quests.values().forEach(quest -> quest.requires().forEach(
                dependency -> dependents.computeIfAbsent(dependency, key -> new ArrayList<>()).add(quest.id())));
        Deque<ResourceLocation> queue = new ArrayDeque<>(broken);
        while (!queue.isEmpty()) {
            ResourceLocation failed = queue.poll();
            for (ResourceLocation dependent : dependents.getOrDefault(failed, List.of())) {
                if (broken.add(dependent)) {
                    issues.add(warning("broken_dependency", dependent, failed.toString()));
                    queue.add(dependent);
                }
            }
        }
    }

    private static void checkChapter(Chapter chapter, Map<ResourceLocation, Chapter> chapters, List<Issue> issues) {
        for (ResourceLocation dependency : chapter.requires()) {
            if (!chapters.containsKey(dependency)) {
                issues.add(warning("unknown_chapter_dependency", chapter.id(), dependency.toString()));
            }
        }
    }

    /** Chapters named in {@code chapter_order} come first in that order, the others after them by id. */
    private static List<Chapter> orderChapters(BookMeta meta, Map<ResourceLocation, Chapter> chapters, List<Issue> issues) {
        Map<ResourceLocation, Chapter> ordered = new LinkedHashMap<>();
        for (ResourceLocation id : meta.chapterOrder()) {
            Chapter chapter = chapters.get(id);
            if (chapter == null) {
                issues.add(warning("unknown_chapter_order", id, ""));
            } else {
                ordered.put(id, chapter);
            }
        }
        chapters.values().stream()
                .sorted(Comparator.comparing(Chapter::id))
                .forEach(chapter -> ordered.putIfAbsent(chapter.id(), chapter));
        return List.copyOf(ordered.values());
    }

    private static Issue error(String code, ResourceLocation subject, String detail) {
        return new Issue(Severity.ERROR, code, subject.toString(), detail);
    }

    private static Issue warning(String code, ResourceLocation subject, String detail) {
        return new Issue(Severity.WARNING, code, subject.toString(), detail);
    }
}
