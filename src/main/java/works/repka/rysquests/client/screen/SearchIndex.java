package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.task.ItemTask;
import works.repka.ryscore.ui.Field;

/**
 * Search over the whole book: quest names, the items of their tasks and rewards, and chapter names. A word like
 * {@code глава:II} limits the search to one chapter. The texts are collected in the background once per book.
 *
 * <p>The window of results under the header (pictures 30, 30b) asks {@link #book} instead: one index of names for quests, items,
 * guides, offers and achievements, built in the background as well, and {@link #maybe} for close words when nothing is found.
 */
final class SearchIndex {
    private static final Pattern CHAPTER = Pattern.compile("(?:^|\\s)((?:глава|chapter):(\\S*))", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private record Entry(Quest quest, String text, String numeral, String chapter) {
    }

    private static volatile List<Entry> entries = List.of();
    private static QuestBook builtFor;
    private static String builtLanguage = "";
    private static String lastQuery = "";
    private static List<Entry> lastEntries = List.of();
    private static List<Quest> lastResult = List.of();
    private static Set<ResourceLocation> lastIds = Set.of();

    private SearchIndex() {
    }

    private static void prepare() {
        QuestBook book = ClientBook.current();
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (book == builtFor && language.equals(builtLanguage)) {
            return;
        }
        builtFor = book;
        builtLanguage = language;
        entries = List.of();
        List<Chapter> chapters = BookData.chapters();
        CompletableFuture.supplyAsync(() -> build(book, chapters), Util.backgroundExecutor())
                .thenAcceptAsync(built -> {
                    if (book == builtFor) {
                        entries = built;
                    }
                }, Minecraft.getInstance());
    }

    private static List<Entry> build(QuestBook book, List<Chapter> chapters) {
        List<Entry> built = new ArrayList<>();
        int number = 0;
        for (Chapter chapter : chapters) {
            number++;
            String numeral = Ui.roman(number).toLowerCase(Locale.ROOT);
            String chapterTitle = lower(Component.translatable(chapter.title()).getString());
            for (Quest quest : ChapterOrder.of(book, chapter.id())) {
                if (!quest.options().hidden()) {
                    built.add(new Entry(quest, textOf(quest), numeral, chapterTitle));
                }
            }
        }
        return built;
    }

    private static String textOf(Quest quest) {
        StringBuilder text = new StringBuilder(Component.translatable(quest.title()).getString());
        try {
            for (TaskDef task : quest.tasks()) {
                text.append('\n').append(TaskText.describe(task).getString());
            }
            for (RewardDef reward : quest.rewards()) {
                if (reward instanceof works.repka.rysquests.reward.ItemReward item) {
                    text.append('\n').append(QuestFacts.stack(item).getHoverName().getString());
                }
            }
        } catch (RuntimeException ignored) {
            // A name a mod cannot produce off the main thread only makes this quest harder to find.
        }
        return lower(text.toString());
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    static List<Quest> find(String query) {
        prepare();
        List<Entry> current = entries;
        if (query.equals(lastQuery) && current == lastEntries) {
            return lastResult;
        }
        String chapter = "";
        String words = lower(query);
        Matcher matcher = CHAPTER.matcher(words);
        if (matcher.find()) {
            chapter = matcher.group(2);
            words = (words.substring(0, matcher.start()) + " " + words.substring(matcher.end())).trim();
        } else {
            words = words.trim();
        }
        List<Quest> result = new ArrayList<>();
        Set<ResourceLocation> ids = new HashSet<>();
        for (Entry entry : current) {
            boolean inChapter = chapter.isEmpty() || entry.numeral.equals(chapter) || entry.chapter.contains(chapter);
            boolean matchesWords = words.isEmpty() || entry.text.contains(words) || entry.chapter.contains(words);
            if (inChapter && matchesWords) {
                result.add(entry.quest);
                ids.add(entry.quest.id());
            }
        }
        lastQuery = query;
        lastEntries = current;
        lastResult = result;
        lastIds = ids;
        return result;
    }

    static boolean matches(Quest quest) {
        find(BookState.search);
        return lastIds.contains(quest.id());
    }

    // --- the whole book for the window of results (pictures 30, 30b) ---

    /** What a result is. The order is the order of the groups in the window. */
    enum Kind {
        QUEST, ITEM, GUIDE, OFFER, ACHIEVEMENT
    }

    /**
     * One name in the index. @param target the quest, the item's stack, the guide, the offer or the achievement
     * @param uses for an item, how many quests ask for it
     */
    record Named(Kind kind, String name, String lower, Object target, int uses) {
    }

    private static final Pattern WORD_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static volatile List<Named> named = List.of();
    private static volatile Set<String> words = Set.of();
    private static QuestBook namedFor;
    private static String namedLanguage = "";

    private static void prepareBook() {
        QuestBook book = ClientBook.current();
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (book == namedFor && language.equals(namedLanguage)) {
            return;
        }
        namedFor = book;
        namedLanguage = language;
        named = List.of();
        words = Set.of();
        CompletableFuture.supplyAsync(() -> buildBook(book), Util.backgroundExecutor())
                .thenAcceptAsync(built -> {
                    if (book == namedFor) {
                        named = built;
                        Set<String> all = new HashSet<>();
                        for (Named entry : built) {
                            for (String word : WORD_SPLIT.split(entry.lower)) {
                                if (word.length() >= 3) {
                                    all.add(word);
                                }
                            }
                        }
                        words = all;
                    }
                }, Minecraft.getInstance());
    }

    private static List<Named> buildBook(QuestBook book) {
        List<Named> out = new ArrayList<>();
        Map<ResourceLocation, int[]> uses = new LinkedHashMap<>();
        for (Quest quest : book.quests().values()) {
            boolean story = quest.chapter().map(id -> book.chapters().get(id)).map(Chapter::isStory).orElse(false);
            if (quest.options().hidden() || !story) {
                continue;
            }
            String title = Component.translatable(quest.title()).getString();
            out.add(new Named(Kind.QUEST, title, lower(title), quest, 0));
            for (TaskDef task : quest.tasks()) {
                if (task instanceof ItemTask items) {
                    items.itemRefs().stream().filter(ref -> !ref.tag()).forEach(ref -> uses.computeIfAbsent(ref.id(), id -> new int[1])[0]++);
                }
            }
            for (RewardDef reward : quest.rewards()) {
                if (reward instanceof ItemReward item) {
                    uses.computeIfAbsent(item.item(), id -> new int[1]);
                }
            }
        }
        for (ShopOffer offer : book.offers().values()) {
            if (!offer.isSale()) {
                for (ShopOffer.Entry entry : offer.gives()) {
                    entry.item().ifPresent(id -> uses.computeIfAbsent(id, key -> new int[1]));
                }
            }
        }
        uses.forEach((id, count) -> {
            try {
                ItemStack stack = TaskText.stackOf(id);
                if (!stack.isEmpty()) {
                    String name = stack.getHoverName().getString();
                    out.add(new Named(Kind.ITEM, name, lower(name), stack, count[0]));
                }
            } catch (RuntimeException ignored) {
                // An item a mod cannot name off the main thread is only missing from the results.
            }
        });
        for (Guide guide : book.guides().values()) {
            String title = Component.translatable(guide.title()).getString();
            out.add(new Named(Kind.GUIDE, title, lower(title), guide, 0));
        }
        for (ShopOffer offer : book.offers().values()) {
            if (!offer.isSale()) {
                try {
                    String title = ShopView.name(offer);
                    out.add(new Named(Kind.OFFER, title, lower(title), offer, 0));
                } catch (RuntimeException ignored) {
                    // As above.
                }
            }
        }
        for (Achievement achievement : book.achievements().values()) {
            if (!achievement.archived() && !achievement.hidden()) {
                String title = Component.translatable(achievement.titleKey()).getString();
                out.add(new Named(Kind.ACHIEVEMENT, title, lower(title), achievement, 0));
            }
        }
        return List.copyOf(out);
    }

    private static String lastBookQuery = "";
    private static List<Named> lastBookEntries = List.of();
    private static Map<Kind, List<Named>> lastBookResult = Map.of();

    /** The names that contain the query, by kind in the window's order. Empty while the index is still being built. */
    static Map<Kind, List<Named>> book(String query) {
        prepareBook();
        List<Named> current = named;
        String wanted = lower(query).trim();
        if (wanted.equals(lastBookQuery) && current == lastBookEntries) {
            return lastBookResult;
        }
        Map<Kind, List<Named>> out = new EnumMap<>(Kind.class);
        if (!wanted.isEmpty()) {
            for (Named entry : current) {
                if (entry.lower.contains(wanted)) {
                    out.computeIfAbsent(entry.kind, kind -> new ArrayList<>()).add(entry);
                }
            }
            // A name that starts with the query comes before one that only holds it, then the shorter one.
            out.values().forEach(list -> list.sort(Comparator.comparing((Named entry) -> !entry.lower.startsWith(wanted)).thenComparingInt(entry -> entry.lower.length())));
        }
        lastBookQuery = wanted;
        lastBookEntries = current;
        lastBookResult = out;
        return out;
    }

    /** Whether the index of names is there yet. */
    static boolean bookReady() {
        prepareBook();
        return !named.isEmpty() || ClientBook.current().quests().isEmpty();
    }

    /** How many names hold a word: the count beside a suggestion. */
    static int count(String word) {
        String wanted = lower(word);
        int count = 0;
        for (Named entry : named) {
            count += entry.lower.contains(wanted) ? 1 : 0;
        }
        return count;
    }

    /** Close words of the index for a query that found nothing ("может быть"), the ones that find most first. */
    static List<String> maybe(String query, int limit) {
        prepareBook();
        String wanted = lower(query).trim();
        if (wanted.length() < 3) {
            return List.of();
        }
        int most = wanted.length() <= 5 ? 1 : 2;
        List<String> close = new ArrayList<>();
        for (String word : words) {
            if (Math.abs(word.length() - wanted.length()) <= most && distance(wanted, word) <= most) {
                close.add(word);
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        close.forEach(word -> counts.put(word, count(word)));
        close.sort(Comparator.comparing((String word) -> -counts.get(word)).thenComparing(word -> distance(wanted, word)));
        return close.size() > limit ? List.copyOf(close.subList(0, limit)) : close;
    }

    /** The edit distance with swaps of neighbours, as typos go. */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    /** The chapter filter in the search field is drawn as a chip. */
    static List<Field.Span> spans(String query) {
        Matcher matcher = CHAPTER.matcher(query);
        return matcher.find() ? List.of(new Field.Span(matcher.start(1), matcher.end(1), "mod")) : List.of();
    }
}
