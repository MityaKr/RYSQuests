package works.repka.rysquests.client.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;

/**
 * What the canvas is laid out from: the chapters on the map in book order and their live quests, by index. Chapters off the map,
 * hidden ones, drafts and archived quests are left out, as {@link MapForms#onMap} and {@link works.repka.rysquests.data.BookHash#layout}
 * have it. Pure data, safe to read from any thread.
 */
public final class MapBook {
    /** One chapter on the map; {@code number} is its place among them, from 1, as the canvas labels it. */
    public record Chap(ResourceLocation id, int index, int number, int epoch, int[] quests, String version) {
        public boolean hasEpoch() {
            return epoch > 0;
        }
    }

    /** One quest on the map; {@code requires} are indices of quests on the map it depends on, in any chapter. */
    public record Q(ResourceLocation id, int index, int chapter, int[] requires, boolean optional, boolean milestone) {
    }

    /** One dependency drawn as a road; {@code trunk} when both ends are the main quests of their wheels, set by the layout. */
    public record Edge(int from, int to, boolean cross) {
    }

    private final String hash;
    private final List<Chap> chapters;
    private final List<Q> quests;
    private final Map<ResourceLocation, Integer> questIndex;
    private final Map<ResourceLocation, Integer> chapterIndex;
    private final List<BookMeta.Epoch> epochs;
    private final List<Edge> edges;
    private final int[][] dependents;
    private final int optionalCount;

    private MapBook(String hash, List<Chap> chapters, List<Q> quests, List<BookMeta.Epoch> epochs) {
        this.hash = hash;
        this.chapters = List.copyOf(chapters);
        this.quests = List.copyOf(quests);
        this.epochs = List.copyOf(epochs);
        Map<ResourceLocation, Integer> qi = new HashMap<>();
        quests.forEach(q -> qi.put(q.id(), q.index()));
        this.questIndex = Collections.unmodifiableMap(qi);
        Map<ResourceLocation, Integer> ci = new HashMap<>();
        chapters.forEach(c -> ci.put(c.id(), c.index()));
        this.chapterIndex = Collections.unmodifiableMap(ci);
        List<Edge> list = new ArrayList<>();
        List<List<Integer>> kids = new ArrayList<>();
        quests.forEach(q -> kids.add(new ArrayList<>()));
        int opt = 0;
        for (Q q : quests) {
            if (q.optional()) {
                opt++;
            }
            for (int r : q.requires()) {
                list.add(new Edge(r, q.index(), quests.get(r).chapter() != q.chapter()));
                kids.get(r).add(q.index());
            }
        }
        this.edges = List.copyOf(list);
        this.dependents = new int[quests.size()][];
        for (int i = 0; i < quests.size(); i++) {
            dependents[i] = kids.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
        this.optionalCount = opt;
    }

    /** The map of a book as it is now. */
    public static MapBook of(QuestBook book) {
        return of(book.meta(), List.copyOf(book.chapters().values()), List.copyOf(book.quests().values()), book.layoutHash());
    }

    /** @param chapters in book order, as {@link QuestBook#chapters()} keeps them */
    public static MapBook of(BookMeta meta, List<Chapter> chapters, List<Quest> quests, String hash) {
        // a chapter without an epoch in a book whose author set epochs is off the map, as the sidebar lists it under "Вне карты"
        boolean authorEpochs = chapters.stream().anyMatch(chapter -> chapter.epoch().isPresent() && meta.authorEpoch(chapter.epoch()));
        List<Chapter> onMap = chapters.stream().filter(MapForms::onMap).filter(chapter -> !authorEpochs || chapter.epoch().isPresent()).toList();
        Map<ResourceLocation, Integer> chapterAt = new HashMap<>();
        for (int i = 0; i < onMap.size(); i++) {
            chapterAt.put(onMap.get(i).id(), i);
        }
        Map<Integer, List<Quest>> byChapter = new HashMap<>();
        for (Quest quest : quests) {
            if (!quest.options().live()) {
                continue;
            }
            Integer at = quest.chapter().map(chapterAt::get).orElse(null);
            if (at != null) {
                byChapter.computeIfAbsent(at, key -> new ArrayList<>()).add(quest);
            }
        }
        List<Quest> ordered = new ArrayList<>();
        int[] firstOf = new int[onMap.size() + 1];
        for (int i = 0; i < onMap.size(); i++) {
            firstOf[i] = ordered.size();
            List<Quest> own = byChapter.getOrDefault(i, new ArrayList<>());
            own.sort(Comparator.comparing(Quest::id));
            ordered.addAll(own);
        }
        firstOf[onMap.size()] = ordered.size();
        Map<ResourceLocation, Integer> index = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            index.put(ordered.get(i).id(), i);
        }
        List<Q> qs = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            Quest quest = ordered.get(i);
            int chapter = chapterAt.get(quest.chapter().get());
            int[] requires = quest.requires().stream().map(index::get).filter(r -> r != null).mapToInt(Integer::intValue).distinct().toArray();
            qs.add(new Q(quest.id(), i, chapter, requires, quest.options().optional(), quest.options().milestone()));
        }
        List<Chap> chs = new ArrayList<>(onMap.size());
        int number = 0;
        for (int i = 0; i < onMap.size(); i++) {
            Chapter chapter = onMap.get(i);
            int[] own = new int[firstOf[i + 1] - firstOf[i]];
            for (int k = 0; k < own.length; k++) {
                own[k] = firstOf[i] + k;
            }
            chs.add(new Chap(chapter.id(), i, ++number, chapter.epoch().orElse(0), own, version(chapter, own, qs)));
        }
        return new MapBook(hash, chs, qs, meta.epochs());
    }

    /** What a chapter's own laying out depends on: its quests and the links between them. Used to keep a chapter that did not change. */
    private static String version(Chapter chapter, int[] own, List<Q> qs) {
        StringBuilder out = new StringBuilder(chapter.id().toString());
        for (int q : own) {
            Q quest = qs.get(q);
            out.append('|').append(quest.id()).append(quest.optional() ? 'o' : '-').append(quest.milestone() ? 'm' : '-');
            for (int r : quest.requires()) {
                out.append(',').append(qs.get(r).id());
            }
        }
        return Integer.toHexString(out.toString().hashCode()) + ":" + out.length();
    }

    public String hash() {
        return hash;
    }

    public List<Chap> chapters() {
        return chapters;
    }

    public List<Q> quests() {
        return quests;
    }

    public List<Edge> edges() {
        return edges;
    }

    public int[] dependents(int quest) {
        return dependents[quest];
    }

    public List<BookMeta.Epoch> epochs() {
        return epochs;
    }

    /** Index of the quest on the map, or -1 when it is not on the map. */
    public int quest(ResourceLocation id) {
        return questIndex.getOrDefault(id, -1);
    }

    /** Index of the chapter on the map, or -1. */
    public int chapter(ResourceLocation id) {
        return chapterIndex.getOrDefault(id, -1);
    }

    public int optionalCount() {
        return optionalCount;
    }

    /** The epoch numbers present on the map, in the order they first appear. */
    public List<Integer> epochNumbers() {
        List<Integer> out = new ArrayList<>();
        for (Chap chapter : chapters) {
            if (chapter.hasEpoch() && !out.contains(chapter.epoch())) {
                out.add(chapter.epoch());
            }
        }
        return out;
    }

    /** The epoch's title from the book, or empty when the book does not name it. */
    public String epochTitle(int epoch) {
        return epoch >= 1 && epoch <= epochs.size() ? epochs.get(epoch - 1).title() : "";
    }

    public boolean importedEpoch(int epoch) {
        return epoch >= 1 && epoch <= epochs.size() && epochs.get(epoch - 1).imported();
    }
}
