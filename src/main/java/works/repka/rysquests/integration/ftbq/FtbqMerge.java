package works.repka.rysquests.integration.ftbq;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.progress.QuestProgress;

/**
 * Folds FTB Quests progress into ours without overwriting anything (ADR-65): a quest that already has a record here keeps it,
 * whatever FTB says, and only quests without a record come over. A record is ours once it exists, an empty one too: an operator's
 * reset writes an empty record, and the import must not undo it. Done again, the fold adds nothing.
 */
public final class FtbqMerge {
    private FtbqMerge() {
    }

    /**
     * What the fold would add.
     *
     * @param additions per team or player, only the quests without a record here
     * @param newTeams owners with no record here at all
     * @param mergedTeams owners with records here that get some quests from FTB
     * @param sameTeams owners whose every FTB quest already has a record here
     * @param added quests that come over
     * @param kept FTB quests left alone because a record is here
     * @param merged owners of {@code mergedTeams}, with the count of quests each gets
     * @param same owners of {@code sameTeams}
     */
    public record Plan(Map<UUID, Map<ResourceLocation, QuestProgress>> additions, int newTeams, int mergedTeams, int sameTeams, int added, int kept,
            Map<UUID, Integer> merged, List<UUID> same) {
        public boolean empty() {
            return additions.isEmpty();
        }
    }

    public static Plan plan(Map<UUID, Map<ResourceLocation, QuestProgress>> ftb, Map<UUID, Map<ResourceLocation, QuestProgress>> own) {
        Map<UUID, Map<ResourceLocation, QuestProgress>> additions = new LinkedHashMap<>();
        Map<UUID, Integer> merged = new LinkedHashMap<>();
        List<UUID> same = new java.util.ArrayList<>();
        int newTeams = 0, added = 0, kept = 0;
        Map<UUID, Map<ResourceLocation, QuestProgress>> sorted = new TreeMap<>(Comparator.naturalOrder());
        sorted.putAll(ftb);
        for (Map.Entry<UUID, Map<ResourceLocation, QuestProgress>> team : sorted.entrySet()) {
            if (team.getValue().isEmpty()) {
                continue;
            }
            Map<ResourceLocation, QuestProgress> mine = own.getOrDefault(team.getKey(), Map.of());
            Map<ResourceLocation, QuestProgress> missing = new HashMap<>();
            team.getValue().forEach((quest, progress) -> {
                if (!mine.containsKey(quest)) {
                    missing.put(quest, progress);
                }
            });
            kept += team.getValue().size() - missing.size();
            if (missing.isEmpty()) {
                same.add(team.getKey());
                continue;
            }
            if (mine.isEmpty()) {
                newTeams++;
            } else {
                merged.put(team.getKey(), missing.size());
            }
            added += missing.size();
            additions.put(team.getKey(), missing);
        }
        return new Plan(additions, newTeams, merged.size(), same.size(), added, kept, merged, List.copyOf(same));
    }
}
