package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.ryscore.ui.Field;

/**
 * Search over the whole book: quest names, the items of their tasks and rewards, and chapter names. A word like
 * {@code глава:II} limits the search to one chapter. The texts are collected in the background once per book.
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
        List<Chapter> chapters = BookScreen.chapters();
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
                    text.append('\n').append(QuestCard.stack(item).getHoverName().getString());
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

    /** The chapter filter in the search field is drawn as a chip. */
    static List<Field.Span> spans(String query) {
        Matcher matcher = CHAPTER.matcher(query);
        return matcher.find() ? List.of(new Field.Span(matcher.start(1), matcher.end(1), "mod")) : List.of();
    }
}
