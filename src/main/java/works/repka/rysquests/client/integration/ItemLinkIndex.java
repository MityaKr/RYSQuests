package works.repka.rysquests.client.integration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.task.ItemRef;

/**
 * Where an item stands in one book, for the recipe viewer's item links (RYSCore {@code links.ItemLinks}, plan
 * _audit/ryscope-quests): the shop offers that give it or take it, the quests that ask for it in a task and the quests that give it
 * as a reward. Built once per book off the main thread from ids alone, so a question about an item is a look into a map.
 *
 * <p>What a player may see is the book's own rule, kept here in one place: the shop shows every offer, closed ones with a lock; a
 * quest counts only when the book shows it on its map (a live quest of a chapter the book lists, not an epoch's reward, which stands
 * at its epoch), and of those a hidden one only once it has opened and none behind a stage that is still closed. Random rewards and
 * loot tables name no exact item and stay out.
 */
public final class ItemLinkIndex {
    public static final ItemLinkIndex EMPTY = new ItemLinkIndex(QuestBook.EMPTY, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), false);

    private final QuestBook book;
    private final Map<ResourceLocation, List<ShopOffer>> giving;
    private final Map<ResourceLocation, List<ShopOffer>> taking;
    private final Map<ResourceLocation, List<Quest>> asking;
    private final Map<ResourceLocation, List<Quest>> askingTag;
    private final Map<ResourceLocation, List<Quest>> rewarding;
    /** The place of each listed quest in the book, so that quests found by item and by tag keep the book's order. */
    private final Map<ResourceLocation, Integer> order;
    private final boolean shop;

    private ItemLinkIndex(QuestBook book, Map<ResourceLocation, List<ShopOffer>> giving, Map<ResourceLocation, List<ShopOffer>> taking,
            Map<ResourceLocation, List<Quest>> asking, Map<ResourceLocation, List<Quest>> askingTag, Map<ResourceLocation, List<Quest>> rewarding,
            Map<ResourceLocation, Integer> order, boolean shop) {
        this.book = book;
        this.giving = giving;
        this.taking = taking;
        this.asking = asking;
        this.askingTag = askingTag;
        this.rewarding = rewarding;
        this.order = order;
        this.shop = shop;
    }

    /** Safe off the main thread: it reads ids only and names nothing. */
    public static ItemLinkIndex build(QuestBook book) {
        Map<ResourceLocation, List<ShopOffer>> giving = new HashMap<>();
        Map<ResourceLocation, List<ShopOffer>> taking = new HashMap<>();
        boolean shop = allowed(book, BookMeta.SHOP);
        if (shop) {
            for (ShopOffer offer : book.offers().values()) {
                itemsOf(offer.gives()).forEach(item -> giving.computeIfAbsent(item, key -> new ArrayList<>()).add(offer));
                itemsOf(offer.cost()).forEach(item -> taking.computeIfAbsent(item, key -> new ArrayList<>()).add(offer));
            }
        }
        Map<ResourceLocation, List<Quest>> asking = new HashMap<>();
        Map<ResourceLocation, List<Quest>> askingTag = new HashMap<>();
        Map<ResourceLocation, List<Quest>> rewarding = new HashMap<>();
        Map<ResourceLocation, Integer> order = new HashMap<>();
        Set<ResourceLocation> chapters = shownChapters(book);
        for (Quest quest : book.quests().values()) {
            if (!listed(book, chapters, quest)) {
                continue;
            }
            order.put(quest.id(), order.size());
            Set<ResourceLocation> items = new LinkedHashSet<>();
            Set<ResourceLocation> tags = new LinkedHashSet<>();
            for (TaskDef task : quest.tasks()) {
                for (ItemRef ref : task.itemRefs()) {
                    (ref.tag() ? tags : items).add(ref.id());
                }
            }
            items.forEach(item -> asking.computeIfAbsent(item, key -> new ArrayList<>()).add(quest));
            tags.forEach(tag -> askingTag.computeIfAbsent(tag, key -> new ArrayList<>()).add(quest));
            Set<ResourceLocation> rewards = new LinkedHashSet<>();
            quest.rewards().forEach(reward -> rewardItems(reward, rewards));
            rewards.forEach(item -> rewarding.computeIfAbsent(item, key -> new ArrayList<>()).add(quest));
        }
        return new ItemLinkIndex(book, freeze(giving), freeze(taking), freeze(asking), freeze(askingTag), freeze(rewarding), Map.copyOf(order), shop);
    }

    private static <T> Map<ResourceLocation, List<T>> freeze(Map<ResourceLocation, List<T>> map) {
        Map<ResourceLocation, List<T>> out = new HashMap<>();
        map.forEach((key, list) -> out.put(key, List.copyOf(list)));
        return Map.copyOf(out);
    }

    private static Set<ResourceLocation> itemsOf(List<ShopOffer.Entry> entries) {
        Set<ResourceLocation> items = new LinkedHashSet<>();
        for (ShopOffer.Entry entry : entries) {
            entry.item().ifPresent(items::add);
        }
        return items;
    }

    /** The exact items a reward gives: an item, or the items of a choice. Random rewards and loot tables name none for sure. */
    static void rewardItems(RewardDef reward, Set<ResourceLocation> out) {
        if (reward instanceof ItemReward item) {
            out.add(item.item());
        } else if (reward instanceof ChoiceReward choice) {
            choice.of().forEach(option -> rewardItems(option, out));
        }
    }

    // --- what the book shows ---

    /** Whether the book lists a section: a book that names no sections lists them all (BookTabs.allowed). */
    static boolean allowed(QuestBook book, String section) {
        List<String> listed = book.meta().sections();
        return listed.isEmpty() || listed.contains(section);
    }

    /**
     * The chapters the book shows (BookData.chapters): story chapters that are not hidden, and not a chapter that only keeps the
     * rewards of epochs. None when the book shows no story at all.
     */
    static Set<ResourceLocation> shownChapters(QuestBook book) {
        if (!allowed(book, Chapter.STORY)) {
            return Set.of();
        }
        Set<ResourceLocation> service = MapBook.rewardsOnly(book.meta(), book.quests().values());
        Set<ResourceLocation> out = new HashSet<>();
        for (Chapter chapter : book.chapters().values()) {
            if (!chapter.hidden() && chapter.isStory() && !service.contains(chapter.id())) {
                out.add(chapter.id());
            }
        }
        return out;
    }

    /** The part of the rule that does not change with progress: a live quest on a chapter the book shows, not an epoch's reward. */
    static boolean listed(QuestBook book, Set<ResourceLocation> chapters, Quest quest) {
        return quest.options().live() && quest.chapter().filter(chapters::contains).isPresent() && !book.isEpochReward(quest.id());
    }

    /**
     * The part that changes with progress: a hidden quest stays a secret until its dependencies are done (BookData.shown), and a
     * quest behind a stage the player has not reached is not shown at all.
     */
    public static boolean shownNow(Quest quest, Function<ResourceLocation, QuestProgress> progress, Predicate<ResourceLocation> stageLocked) {
        return (!quest.options().hidden() || QuestLogic.isUnlocked(quest, progress)) && !stageLocked.test(quest.id());
    }

    /** Whether the quest is on the map the player sees now: listed and shown. */
    public boolean visible(Quest quest, Function<ResourceLocation, QuestProgress> progress, Predicate<ResourceLocation> stageLocked) {
        return order.containsKey(quest.id()) && shownNow(quest, progress, stageLocked);
    }

    // --- questions ---

    public QuestBook book() {
        return book;
    }

    /** Whether the book has a shop the player can open. */
    public boolean shop() {
        return shop;
    }

    /** Offers whose goods hold the item, in book order: where it comes from. */
    public List<ShopOffer> offersGiving(ResourceLocation item) {
        return giving.getOrDefault(item, List.of());
    }

    /** Offers that take the item, a sale or a delivery among them, in book order: where it goes. */
    public List<ShopOffer> offersTaking(ResourceLocation item) {
        return taking.getOrDefault(item, List.of());
    }

    /**
     * Listed quests whose tasks ask for the item, by its id or by a tag it carries, in book order. Whether they are shown now is
     * for {@link #visible} to say. @param hasTag whether the item carries a tag
     */
    public List<Quest> asking(ResourceLocation item, Predicate<ResourceLocation> hasTag) {
        List<Quest> direct = asking.getOrDefault(item, List.of());
        List<Quest> byTag = new ArrayList<>();
        askingTag.forEach((tag, quests) -> {
            if (hasTag.test(tag)) {
                byTag.addAll(quests);
            }
        });
        if (byTag.isEmpty()) {
            return direct;
        }
        Set<ResourceLocation> seen = new HashSet<>();
        List<Quest> out = new ArrayList<>();
        for (Quest quest : direct) {
            if (seen.add(quest.id())) {
                out.add(quest);
            }
        }
        for (Quest quest : byTag) {
            if (seen.add(quest.id())) {
                out.add(quest);
            }
        }
        out.sort(Comparator.comparingInt(quest -> order.getOrDefault(quest.id(), Integer.MAX_VALUE)));
        return out;
    }

    /** Listed quests that give the item as a reward (an item, or one of a choice), in book order. */
    public List<Quest> rewarding(ResourceLocation item) {
        return rewarding.getOrDefault(item, List.of());
    }
}
