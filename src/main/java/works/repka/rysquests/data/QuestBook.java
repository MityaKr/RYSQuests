package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.task.ItemRef;

/** The immutable, linked book with its indexes. Chapters keep their display order. */
public record QuestBook(
        BookMeta meta,
        Map<ResourceLocation, Chapter> chapters,
        Map<ResourceLocation, Quest> quests,
        Map<ResourceLocation, List<ResourceLocation>> unlocks,
        Map<ResourceLocation, List<ResourceLocation>> byItem,
        String hash) {

    public static final QuestBook EMPTY = create(BookMeta.DEFAULT, List.of(), List.of(), "");

    /** The book contents as they travel to clients. */
    public record Wire(BookMeta meta, List<Chapter> chapters, List<Quest> quests) {
        public static final Codec<Wire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BookMeta.CODEC.fieldOf("meta").forGetter(Wire::meta),
                Chapter.CODEC.listOf().fieldOf("chapters").forGetter(Wire::chapters),
                Quest.CODEC.listOf().fieldOf("quests").forGetter(Wire::quests)
        ).apply(instance, Wire::new));
    }

    public static QuestBook create(BookMeta meta, List<Chapter> chapters, List<Quest> quests, String hash) {
        Map<ResourceLocation, Chapter> chapterMap = new LinkedHashMap<>();
        chapters.forEach(chapter -> chapterMap.put(chapter.id(), chapter));
        Map<ResourceLocation, Quest> questMap = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> unlocks = new HashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> byItem = new HashMap<>();
        for (Quest quest : quests) {
            questMap.put(quest.id(), quest);
            quest.requires().forEach(dependency -> unlocks.computeIfAbsent(dependency, key -> new ArrayList<>()).add(quest.id()));
            quest.tasks().stream()
                    .flatMap(task -> task.itemRefs().stream())
                    .filter(ref -> !ref.tag())
                    .map(ItemRef::id)
                    .distinct()
                    .forEach(item -> byItem.computeIfAbsent(item, key -> new ArrayList<>()).add(quest.id()));
        }
        return new QuestBook(meta, Collections.unmodifiableMap(chapterMap), Collections.unmodifiableMap(questMap),
                Map.copyOf(unlocks), Map.copyOf(byItem), hash);
    }

    public Wire wire() {
        return new Wire(meta, List.copyOf(chapters.values()), List.copyOf(quests.values()));
    }

    public static QuestBook fromWire(Wire wire, String hash) {
        return create(wire.meta(), wire.chapters(), wire.quests(), hash);
    }
}
