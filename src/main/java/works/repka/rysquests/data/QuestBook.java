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
        Map<ResourceLocation, Guide> guides,
        Map<ResourceLocation, SectionDef> sections,
        Map<ResourceLocation, List<ResourceLocation>> unlocks,
        Map<ResourceLocation, List<ResourceLocation>> byItem,
        String hash,
        BookAssets assets,
        Map<ResourceLocation, ShopOffer> offers,
        Map<ResourceLocation, Achievement> achievements,
        Map<ResourceLocation, StructureDef> structures) {

    public static final QuestBook EMPTY = create(BookMeta.DEFAULT, List.of(), List.of(), List.of(), List.of(), "");

    /** The book contents as they travel to clients. */
    public record Wire(BookMeta meta, List<Chapter> chapters, List<Quest> quests, List<Guide> guides, List<SectionDef> sections, BookAssets assets, List<ShopOffer> offers, List<Achievement> achievements, List<StructureDef> structures) {
        public static final Codec<Wire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BookMeta.CODEC.fieldOf("meta").forGetter(Wire::meta),
                Chapter.CODEC.listOf().fieldOf("chapters").forGetter(Wire::chapters),
                Quest.CODEC.listOf().fieldOf("quests").forGetter(Wire::quests),
                Guide.CODEC.listOf().optionalFieldOf("guides", List.of()).forGetter(Wire::guides),
                SectionDef.CODEC.listOf().optionalFieldOf("sections", List.of()).forGetter(Wire::sections),
                BookAssets.CODEC.optionalFieldOf("assets", BookAssets.EMPTY).forGetter(Wire::assets),
                ShopOffer.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(Wire::offers),
                Achievement.CODEC.listOf().optionalFieldOf("achievements", List.of()).forGetter(Wire::achievements),
                StructureDef.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(Wire::structures)
        ).apply(instance, Wire::new));
    }

    public static QuestBook create(BookMeta meta, List<Chapter> chapters, List<Quest> quests, List<Guide> guides, List<SectionDef> sections, String hash) {
        Map<ResourceLocation, Chapter> chapterMap = new LinkedHashMap<>();
        chapters.forEach(chapter -> chapterMap.put(chapter.id(), chapter));
        Map<ResourceLocation, Quest> questMap = new LinkedHashMap<>();
        Map<ResourceLocation, Guide> guideMap = new LinkedHashMap<>();
        guides.forEach(guide -> guideMap.put(guide.id(), guide));
        Map<ResourceLocation, SectionDef> sectionMap = new LinkedHashMap<>();
        sections.forEach(section -> sectionMap.put(section.id(), section));
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
        return new QuestBook(meta, Collections.unmodifiableMap(chapterMap), Collections.unmodifiableMap(questMap), Collections.unmodifiableMap(guideMap), Collections.unmodifiableMap(sectionMap),
                Map.copyOf(unlocks), Map.copyOf(byItem), hash, BookAssets.EMPTY, Map.of(), Map.of(), Map.of());
    }

    public QuestBook withAssets(BookAssets newAssets) {
        return new QuestBook(meta, chapters, quests, guides, sections, unlocks, byItem, hash, newAssets, offers, achievements, structures);
    }

    public QuestBook withOffers(List<ShopOffer> newOffers) {
        Map<ResourceLocation, ShopOffer> offerMap = new LinkedHashMap<>();
        newOffers.forEach(offer -> offerMap.put(offer.id(), offer));
        return new QuestBook(meta, chapters, quests, guides, sections, unlocks, byItem, hash, assets, Collections.unmodifiableMap(offerMap), achievements, structures);
    }

    public QuestBook withAchievements(List<Achievement> newAchievements) {
        Map<ResourceLocation, Achievement> map = new LinkedHashMap<>();
        newAchievements.forEach(achievement -> map.put(achievement.id(), achievement));
        return new QuestBook(meta, chapters, quests, guides, sections, unlocks, byItem, hash, assets, offers, Collections.unmodifiableMap(map), structures);
    }

    public QuestBook withStructures(List<StructureDef> newStructures) {
        Map<ResourceLocation, StructureDef> map = new LinkedHashMap<>();
        newStructures.forEach(structure -> map.put(structure.id(), structure));
        return new QuestBook(meta, chapters, quests, guides, sections, unlocks, byItem, hash, assets, offers, achievements, Collections.unmodifiableMap(map));
    }

    public Wire wire() {
        return new Wire(meta, List.copyOf(chapters.values()), List.copyOf(quests.values()), List.copyOf(guides.values()), List.copyOf(sections.values()), assets, List.copyOf(offers.values()), List.copyOf(achievements.values()), List.copyOf(structures.values()));
    }

    public static QuestBook fromWire(Wire wire, String hash) {
        return create(wire.meta(), wire.chapters(), wire.quests(), wire.guides(), wire.sections(), hash).withAssets(wire.assets()).withOffers(wire.offers()).withAchievements(wire.achievements()).withStructures(wire.structures());
    }
}
