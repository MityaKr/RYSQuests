package works.repka.rysquests.client.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;

/** Orders a chapter's quests so that each comes after the ones it depends on; ties keep book order. */
final class ChapterOrder {
    private ChapterOrder() {
    }

    static List<Quest> of(QuestBook book, ResourceLocation chapter) {
        List<Quest> quests = book.quests().values().stream()
                .filter(quest -> quest.chapter().filter(chapter::equals).isPresent())
                .toList();
        Map<ResourceLocation, Integer> blockers = new HashMap<>();
        Map<ResourceLocation, List<Quest>> dependents = new HashMap<>();
        Map<ResourceLocation, Quest> inChapter = new HashMap<>();
        quests.forEach(quest -> inChapter.put(quest.id(), quest));
        for (Quest quest : quests) {
            int count = 0;
            for (ResourceLocation dependency : quest.requires()) {
                if (inChapter.containsKey(dependency)) {
                    dependents.computeIfAbsent(dependency, key -> new ArrayList<>()).add(quest);
                    count++;
                }
            }
            blockers.put(quest.id(), count);
        }
        Deque<Quest> ready = new ArrayDeque<>(quests.stream().filter(quest -> blockers.get(quest.id()) == 0).toList());
        List<Quest> ordered = new ArrayList<>(quests.size());
        while (!ready.isEmpty()) {
            Quest quest = ready.poll();
            ordered.add(quest);
            for (Quest dependent : dependents.getOrDefault(quest.id(), List.of())) {
                if (blockers.merge(dependent.id(), -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        return ordered;
    }
}
