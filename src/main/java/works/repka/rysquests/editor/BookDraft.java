package works.repka.rysquests.editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.TaskDef;

/**
 * Everything the editor changed in the book and has not saved yet (plan, section 6): whole chapters with their quests, the book's
 * settings, shop offers, achievements, guides, the author's tabs and the strings typed for them, with a line in words for every edit.
 * The number of those lines is the N of "Сохранить · N", and they are the list of the question "Сохранить N правок?" (picture 33b).
 *
 * <p>An immutable value: every edit makes a new one, so the editor's undo is a stack of drafts. It is written to disk after each
 * edit ({@link EditorDrafts#saveBook}) and so outlives the game. Knows nothing about the client, so that it can be tested.
 *
 * @param chapters     the chapters the author touched, each whole, by id in the order they were first touched
 * @param meta         the book's settings when changed: the map's form, the title, the epochs
 * @param offers       new or changed shop offers
 * @param removed      shop offers taken out: an offer is removed at once rather than archived (plan, section 6)
 * @param achievements new or changed achievements, with the task their tiers count
 * @param guides       new or changed guides
 * @param sections     new or changed tabs of the author (cards, pages, matrices)
 * @param texts        strings typed in the author's language, by lang key
 * @param log          one line in words per edit, oldest first
 * @param language     the language the strings are in
 */
public record BookDraft(
        Map<ResourceLocation, Work> chapters,
        Optional<BookMeta> meta,
        Map<ResourceLocation, ShopOffer> offers,
        List<ResourceLocation> removed,
        Map<ResourceLocation, AchievementWork> achievements,
        Map<ResourceLocation, Guide> guides,
        Map<ResourceLocation, SectionDef> sections,
        Map<String, String> texts,
        List<String> log,
        String language) {

    /**
     * One chapter as edited.
     *
     * @param quests      every quest of the chapter, in the order of the chapter
     * @param baseVersion {@link works.repka.rysquests.data.BookHash#chapterVersion} of the chapter when its editing began, empty for
     *                    a new chapter: the server refuses to save over what another operator saved meanwhile
     * @param created     the chapter is not in the book yet
     */
    public record Work(Chapter chapter, List<Quest> quests, String baseVersion, boolean created) {
        public static final Codec<Work> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Chapter.CODEC.fieldOf("chapter").forGetter(Work::chapter),
                Quest.CODEC.listOf().fieldOf("quests").forGetter(Work::quests),
                Codec.STRING.optionalFieldOf("base_version", "").forGetter(Work::baseVersion),
                Codec.BOOL.optionalFieldOf("new", false).forGetter(Work::created)
        ).apply(instance, Work::new));

        public Map<ResourceLocation, Quest> questMap() {
            Map<ResourceLocation, Quest> map = new LinkedHashMap<>();
            quests.forEach(quest -> map.put(quest.id(), quest));
            return map;
        }

        public Work withChapter(Chapter newChapter) {
            return new Work(newChapter, quests, baseVersion, created);
        }

        /** Puts the quest in place of the one with its id, or at the end. */
        public Work withQuest(Quest quest) {
            List<Quest> out = new ArrayList<>(quests);
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i).id().equals(quest.id())) {
                    out.set(i, quest);
                    return new Work(chapter, List.copyOf(out), baseVersion, created);
                }
            }
            out.add(quest);
            return new Work(chapter, List.copyOf(out), baseVersion, created);
        }

        /** Puts the quest right after another one, as the table's quick entry does under a selected row. */
        public Work withQuestAfter(Quest quest, ResourceLocation after) {
            List<Quest> out = new ArrayList<>(quests);
            out.removeIf(each -> each.id().equals(quest.id()));
            int at = out.size();
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i).id().equals(after)) {
                    at = i + 1;
                }
            }
            out.add(at, quest);
            return new Work(chapter, List.copyOf(out), baseVersion, created);
        }

        public Work withoutQuest(ResourceLocation id) {
            return new Work(chapter, quests.stream().filter(quest -> !quest.id().equals(id)).toList(), baseVersion, created);
        }
    }

    /** An achievement as edited, with the task its tiers count: the file keeps it beside the achievement. */
    public record AchievementWork(Achievement achievement, Optional<TaskDef> task) {
        public static final Codec<AchievementWork> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Achievement.CODEC.fieldOf("achievement").forGetter(AchievementWork::achievement),
                TaskDef.CODEC.optionalFieldOf("task").forGetter(AchievementWork::task)
        ).apply(instance, AchievementWork::new));
    }

    public static final Codec<BookDraft> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Work.CODEC.listOf().optionalFieldOf("chapters", List.of()).forGetter(draft -> List.copyOf(draft.chapters.values())),
            BookMeta.CODEC.optionalFieldOf("meta").forGetter(BookDraft::meta),
            ShopOffer.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(draft -> List.copyOf(draft.offers.values())),
            ResourceLocation.CODEC.listOf().optionalFieldOf("removed_offers", List.of()).forGetter(BookDraft::removed),
            AchievementWork.CODEC.listOf().optionalFieldOf("achievements", List.of()).forGetter(draft -> List.copyOf(draft.achievements.values())),
            Guide.CODEC.listOf().optionalFieldOf("guides", List.of()).forGetter(draft -> List.copyOf(draft.guides.values())),
            SectionDef.CODEC.listOf().optionalFieldOf("sections", List.of()).forGetter(draft -> List.copyOf(draft.sections.values())),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("texts", Map.of()).forGetter(draft -> new TreeMap<>(draft.texts)),
            Codec.STRING.listOf().optionalFieldOf("log", List.of()).forGetter(BookDraft::log),
            Codec.STRING.optionalFieldOf("language", "en_us").forGetter(BookDraft::language)
    ).apply(instance, (chapters, meta, offers, removed, achievements, guides, sections, texts, log, language) -> new BookDraft(
            byId(chapters, work -> work.chapter().id()), meta, byId(offers, ShopOffer::id), removed,
            byId(achievements, work -> work.achievement().id()), byId(guides, Guide::id), byId(sections, SectionDef::id),
            Map.copyOf(texts), log, language)));

    public static BookDraft empty(String language) {
        return new BookDraft(Map.of(), Optional.empty(), Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), language);
    }

    private static <T> Map<ResourceLocation, T> byId(Collection<T> values, java.util.function.Function<T, ResourceLocation> id) {
        Map<ResourceLocation, T> map = new LinkedHashMap<>();
        values.forEach(value -> map.put(id.apply(value), value));
        return java.util.Collections.unmodifiableMap(map);
    }

    private static <K, V> Map<K, V> with(Map<K, V> map, K key, V value) {
        Map<K, V> out = new LinkedHashMap<>(map);
        out.put(key, value);
        return java.util.Collections.unmodifiableMap(out);
    }

    /** Nothing to save. */
    public boolean isEmpty() {
        return log.isEmpty();
    }

    /** The N of "Сохранить · N": how many edits wait. */
    public int count() {
        return log.size();
    }

    // --- edits, each a new draft ---

    /** The same draft with one more line in the log. */
    public BookDraft logged(String line) {
        List<String> out = new ArrayList<>(log);
        out.add(line);
        return new BookDraft(chapters, meta, offers, removed, achievements, guides, sections, texts, List.copyOf(out), language);
    }

    public BookDraft withWork(Work work) {
        return new BookDraft(with(chapters, work.chapter().id(), work), meta, offers, removed, achievements, guides, sections, texts, log, language);
    }

    public BookDraft withText(String key, String value) {
        return new BookDraft(chapters, meta, offers, removed, achievements, guides, sections, with(texts, key, value), log, language);
    }

    public BookDraft withMeta(BookMeta newMeta) {
        return new BookDraft(chapters, Optional.of(newMeta), offers, removed, achievements, guides, sections, texts, log, language);
    }

    public BookDraft withOffer(ShopOffer offer) {
        List<ResourceLocation> gone = removed.stream().filter(id -> !id.equals(offer.id())).toList();
        return new BookDraft(chapters, meta, with(offers, offer.id(), offer), gone, achievements, guides, sections, texts, log, language);
    }

    /** @param saved whether the offer is in the book already: a new one simply leaves the draft */
    public BookDraft withoutOffer(ResourceLocation id, boolean saved) {
        Map<ResourceLocation, ShopOffer> out = new LinkedHashMap<>(offers);
        out.remove(id);
        List<ResourceLocation> gone = new ArrayList<>(removed);
        if (saved && !gone.contains(id)) {
            gone.add(id);
        }
        return new BookDraft(chapters, meta, java.util.Collections.unmodifiableMap(out), List.copyOf(gone), achievements, guides, sections, texts, log, language);
    }

    public BookDraft withAchievement(AchievementWork work) {
        return new BookDraft(chapters, meta, offers, removed, with(achievements, work.achievement().id(), work), guides, sections, texts, log, language);
    }

    public BookDraft withGuide(Guide guide) {
        return new BookDraft(chapters, meta, offers, removed, achievements, with(guides, guide.id(), guide), sections, texts, log, language);
    }

    public BookDraft withSection(SectionDef section) {
        return new BookDraft(chapters, meta, offers, removed, achievements, guides, with(sections, section.id(), section), texts, log, language);
    }

    /** The draft after a part of it was saved: that part leaves, the rest stays. */
    public BookDraft withoutChapter(ResourceLocation id) {
        Map<ResourceLocation, Work> out = new LinkedHashMap<>(chapters);
        out.remove(id);
        return new BookDraft(java.util.Collections.unmodifiableMap(out), meta, offers, removed, achievements, guides, sections, texts, log, language);
    }

    // --- reading ---

    /**
     * The book as it will be once the draft is saved: the base with every change of the draft laid over it. Chapters and quests keep
     * their lang keys; {@link #shown} gives the typed strings.
     */
    public QuestBook apply(QuestBook base) {
        Map<ResourceLocation, Chapter> chapterMap = new LinkedHashMap<>(base.chapters());
        Map<ResourceLocation, Quest> questMap = new LinkedHashMap<>();
        Set<ResourceLocation> replaced = new LinkedHashSet<>();
        for (Work work : chapters.values()) {
            chapterMap.put(work.chapter().id(), work.chapter());
            replaced.add(work.chapter().id());
        }
        Set<ResourceLocation> edited = new LinkedHashSet<>();
        chapters.values().forEach(work -> work.quests().forEach(quest -> edited.add(quest.id())));
        for (Quest quest : base.quests().values()) {
            boolean inReplaced = quest.chapter().filter(replaced::contains).isPresent();
            if (!inReplaced && !edited.contains(quest.id())) {
                questMap.put(quest.id(), quest);
            }
        }
        chapters.values().forEach(work -> work.quests().forEach(quest -> questMap.put(quest.id(), quest)));
        // Quests of a touched chapter that the draft no longer has (only unsaved ones can go) stay out.
        Map<ResourceLocation, ShopOffer> offerMap = new LinkedHashMap<>(base.offers());
        offerMap.putAll(offers);
        removed.forEach(offerMap::remove);
        Map<ResourceLocation, Achievement> achievementMap = new LinkedHashMap<>(base.achievements());
        achievements.values().forEach(work -> achievementMap.put(work.achievement().id(), work.achievement()));
        Map<ResourceLocation, Guide> guideMap = new LinkedHashMap<>(base.guides());
        guideMap.putAll(guides);
        Map<ResourceLocation, SectionDef> sectionMap = new LinkedHashMap<>(base.sections());
        sectionMap.putAll(sections);
        BookMeta bookMeta = meta.orElse(base.meta());
        return QuestBook.create(bookMeta, List.copyOf(chapterMap.values()), List.copyOf(questMap.values()), List.copyOf(guideMap.values()),
                        List.copyOf(sectionMap.values()), base.hash())
                .withAssets(base.assets()).withOffers(List.copyOf(offerMap.values())).withAchievements(List.copyOf(achievementMap.values()))
                .withStructures(List.copyOf(base.structures().values())).withLayout(base.layout());
    }

    /** A typed string, if the draft has one for this key. */
    public Optional<String> shown(String key) {
        return Optional.ofNullable(texts.get(key));
    }

    /** The strings a set of keys needs, for one payload of the save. */
    public Map<String, String> textsOf(Collection<String> keys) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : keys) {
            String value = texts.get(key);
            if (value != null) {
                out.put(key, value);
            }
        }
        return out;
    }

    /** Every lang key a chapter's files refer to: its title and groups, and the title, subtitle and text of each quest. */
    public static Set<String> keysOf(Work work) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(work.chapter().title());
        work.chapter().subtitle().ifPresent(keys::add);
        work.chapter().groups().forEach(group -> keys.add(group.title()));
        for (Quest quest : work.quests()) {
            keys.add(quest.title());
            quest.subtitle().ifPresent(keys::add);
            quest.text().ifPresent(keys::add);
        }
        return keys;
    }
}
