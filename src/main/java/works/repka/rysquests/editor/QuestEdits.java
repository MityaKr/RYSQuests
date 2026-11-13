package works.repka.rysquests.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Layout;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;

/** Changes to quests as new immutable values. No game state here, so that each step can be tested. */
public final class QuestEdits {
    public static final int GRID = 24;

    private QuestEdits() {
    }

    private static String dotted(ResourceLocation id) {
        return id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public static String titleKey(ResourceLocation quest) {
        return "quest." + dotted(quest) + ".title";
    }

    public static String subtitleKey(ResourceLocation quest) {
        return "quest." + dotted(quest) + ".subtitle";
    }

    public static String textKey(ResourceLocation quest) {
        return "quest." + dotted(quest) + ".text";
    }

    public static String chapterTitleKey(ResourceLocation chapter) {
        return "chapter." + dotted(chapter) + ".title";
    }

    public static String groupKey(ResourceLocation chapter, String group) {
        return "chapter." + dotted(chapter) + "." + group;
    }

    public static ResourceLocation freshChapter(String namespace, Set<ResourceLocation> taken) {
        int number = taken.size() + 1;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, "chapter_" + number);
        while (taken.contains(id)) {
            number++;
            id = ResourceLocation.fromNamespaceAndPath(namespace, "chapter_" + number);
        }
        return id;
    }

    public static ResourceLocation freshQuest(ResourceLocation chapter, Set<ResourceLocation> taken) {
        int number = 1;
        ResourceLocation id = chapter.withSuffix("/quest_" + number);
        while (taken.contains(id)) {
            number++;
            id = chapter.withSuffix("/quest_" + number);
        }
        return id;
    }

    public static Chapter newChapter(ResourceLocation id) {
        return new Chapter(id, chapterTitleKey(id), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), false, Chapter.STORY);
    }

    public static int snap(int value) {
        return Math.round(value / (float) GRID) * GRID;
    }

    public static Quest newQuest(ResourceLocation id, ResourceLocation chapter, int x, int y, boolean page) {
        Layout layout = new Layout(true, Optional.of(snap(x)), Optional.of(snap(y)));
        return new Quest(id, Optional.of(chapter), Optional.empty(), titleKey(id), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), RequiresMode.ALL, Optional.empty(),
                List.of(), List.of(), Optional.empty(), new QuestOptions(layout, 1, false, false, Optional.empty(), page, List.of()));
    }

    private static QuestOptions withLayout(QuestOptions options, Layout layout) {
        return options.withLayout(layout);
    }

    public static Quest moved(Quest quest, int x, int y) {
        return withOptions(quest, withLayout(quest.options(), new Layout(true, Optional.of(snap(x)), Optional.of(snap(y)))));
    }

    /** The quest goes back to the automatic layout. */
    public static Quest auto(Quest quest) {
        return withOptions(quest, withLayout(quest.options(), Layout.DEFAULT));
    }

    private static Quest withOptions(Quest q, QuestOptions options) {
        return new Quest(q.id(), q.chapter(), q.group(), q.title(), q.subtitle(), q.text(), q.icon(), q.requires(), q.requiresMode(), q.stageRequired(), q.tasks(), q.rewards(), q.guide(), options);
    }

    public static Quest withRequires(Quest q, List<ResourceLocation> requires, RequiresMode mode) {
        return new Quest(q.id(), q.chapter(), q.group(), q.title(), q.subtitle(), q.text(), q.icon(), List.copyOf(requires), mode, q.stageRequired(), q.tasks(), q.rewards(), q.guide(), q.options());
    }

    public static Quest withTexts(Quest q, String title, Optional<String> subtitle, Optional<String> text) {
        return new Quest(q.id(), q.chapter(), q.group(), title, subtitle, text, q.icon(), q.requires(), q.requiresMode(), q.stageRequired(), q.tasks(), q.rewards(), q.guide(), q.options());
    }

    public static Quest withIcon(Quest q, ResourceLocation icon) {
        return new Quest(q.id(), q.chapter(), q.group(), q.title(), q.subtitle(), q.text(), Optional.of(icon), q.requires(), q.requiresMode(), q.stageRequired(), q.tasks(), q.rewards(), q.guide(), q.options());
    }

    public static Quest withGroup(Quest q, Optional<String> group) {
        return new Quest(q.id(), q.chapter(), group, q.title(), q.subtitle(), q.text(), q.icon(), q.requires(), q.requiresMode(), q.stageRequired(), q.tasks(), q.rewards(), q.guide(), q.options());
    }

    public static Quest withTasks(Quest q, List<works.repka.rysquests.data.TaskDef> tasks) {
        return new Quest(q.id(), q.chapter(), q.group(), q.title(), q.subtitle(), q.text(), q.icon(), q.requires(), q.requiresMode(), q.stageRequired(), List.copyOf(tasks), q.rewards(), q.guide(), q.options());
    }

    public static Quest withRewards(Quest q, List<works.repka.rysquests.data.RewardDef> rewards) {
        return new Quest(q.id(), q.chapter(), q.group(), q.title(), q.subtitle(), q.text(), q.icon(), q.requires(), q.requiresMode(), q.stageRequired(), q.tasks(), List.copyOf(rewards), q.guide(), q.options());
    }

    /** Whether a quest needing this dependency would end up waiting for itself. */
    public static boolean wouldCycle(Map<ResourceLocation, Quest> quests, ResourceLocation quest, ResourceLocation dependency) {
        Deque<ResourceLocation> open = new ArrayDeque<>(List.of(dependency));
        Set<ResourceLocation> seen = new HashSet<>();
        while (!open.isEmpty()) {
            ResourceLocation next = open.pop();
            if (next.equals(quest)) {
                return true;
            }
            if (seen.add(next) && quests.containsKey(next)) {
                open.addAll(quests.get(next).requires());
            }
        }
        return false;
    }

    /** Adds the dependency, or takes it away if it is there. */
    public static Quest toggled(Quest quest, ResourceLocation dependency) {
        List<ResourceLocation> requires = new ArrayList<>(quest.requires());
        if (!requires.remove(dependency)) {
            requires.add(dependency);
        }
        return withRequires(quest, requires, quest.requiresMode());
    }

    /** A new id for an unsaved quest: other quests follow it, and so do its text keys while they are still the default ones. */
    public static Map<ResourceLocation, Quest> renamed(Map<ResourceLocation, Quest> quests, ResourceLocation from, ResourceLocation to) {
        Map<ResourceLocation, Quest> out = new LinkedHashMap<>();
        for (Quest quest : quests.values()) {
            if (quest.id().equals(from)) {
                out.put(to, new Quest(to, quest.chapter(), quest.group(), titleKey(quest.title(), titleKey(from), titleKey(to)), rekey(quest.subtitle(), subtitleKey(from), subtitleKey(to)), rekey(quest.text(), textKey(from), textKey(to)),
                        quest.icon(), quest.requires(), quest.requiresMode(), quest.stageRequired(), quest.tasks(), quest.rewards(), quest.guide(), quest.options()));
            } else {
                List<ResourceLocation> requires = quest.requires().stream().map(id -> id.equals(from) ? to : id).toList();
                out.put(quest.id(), withRequires(quest, requires, quest.requiresMode()));
            }
        }
        return out;
    }

    private static String titleKey(String current, String from, String to) {
        return current.equals(from) ? to : current;
    }

    private static Optional<String> rekey(Optional<String> current, String from, String to) {
        return current.map(key -> key.equals(from) ? to : key);
    }

    /** A copy beside the original, without its dependencies. */
    public static Quest duplicate(Quest quest, ResourceLocation id, int x, int y) {
        Quest copy = new Quest(id, quest.chapter(), quest.group(), titleKey(id), quest.subtitle().map(key -> subtitleKey(id)), quest.text().map(key -> textKey(id)), quest.icon(), List.of(), quest.requiresMode(),
                quest.stageRequired(), quest.tasks(), quest.rewards(), quest.guide(), quest.options());
        return moved(copy, x, y);
    }
}
