package works.repka.rysquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.BookReport.Issue;
import works.repka.rysquests.data.BookReport.Severity;
import works.repka.rysquests.migrate.Migration;

/** Turns the files of a datapack into a {@link RawBook}. One broken file never stops the others. */
public final class BookParser {
    private static final String CHAPTERS = "chapters/";
    private static final String GUIDES = "guides/";
    private static final String SECTIONS = "sections/";
    private static final String SHOP = "shop/";
    private static final String CHAPTER_FILE = "_chapter";
    private static final String QUESTS_FILE = "_quests";
    private static final String BOOK_FILE = "book";
    private static final String MIGRATIONS_FILE = "migrations";

    private BookParser() {
    }

    /** @param files file contents by id relative to the {@code rysquests} folder, without the extension */
    public static RawBook parse(Map<ResourceLocation, JsonElement> files, DynamicOps<JsonElement> ops) {
        Map<ResourceLocation, JsonElement> sorted = new TreeMap<>(files);
        List<Issue> issues = new ArrayList<>();
        BookMeta meta = parseMeta(sorted, ops, issues);

        List<Chapter> chapters = new ArrayList<>();
        Map<ResourceLocation, ResourceLocation> chapterByFolder = new HashMap<>();
        sorted.forEach((id, json) -> {
            if (CHAPTER_FILE.equals(fileName(id)) && isInChapter(id)) {
                decode(Chapter.CODEC, json, ops, id, issues).ifPresent(chapter -> {
                    chapters.add(chapter);
                    chapterByFolder.put(folder(id), chapter.id());
                });
            }
        });

        List<Quest> quests = new ArrayList<>();
        sorted.forEach((id, json) -> {
            String name = fileName(id);
            if (!isInChapter(id) || CHAPTER_FILE.equals(name)) {
                return;
            }
            Codec<List<Quest>> codec = QUESTS_FILE.equals(name) ? Quest.CODEC.listOf() : Quest.CODEC.xmap(List::of, List::getFirst);
            decode(codec, json, ops, id, issues).ifPresent(parsed -> parsed.forEach(quest -> quests.add(linkChapter(quest, id, chapterByFolder))));
        });
        List<Guide> guides = new ArrayList<>();
        sorted.forEach((id, json) -> {
            if (id.getPath().startsWith(GUIDES)) {
                decode(Guide.CODEC, json, ops, id, issues).ifPresent(guides::add);
            }
        });
        List<SectionDef> sections = new ArrayList<>();
        sorted.forEach((id, json) -> {
            if (id.getPath().startsWith(SECTIONS) && json.isJsonObject()) {
                JsonObject withId = json.getAsJsonObject().deepCopy();
                withId.addProperty("id", id.withPath(id.getPath().substring(SECTIONS.length())).toString());
                decode(SectionDef.CODEC, withId, ops, id, issues).ifPresent(sections::add);
            }
        });
        sections.stream().filter(CardsSection.class::isInstance).map(CardsSection.class::cast).forEach(section -> addCardConditions(section, chapters, quests));
        List<ShopOffer> offers = new ArrayList<>();
        sorted.forEach((id, json) -> {
            if (id.getPath().startsWith(SHOP)) {
                decode(ShopOffer.CODEC, json, ops, id, issues).ifPresent(offers::add);
            }
        });
        List<Migration> migrations = new ArrayList<>();
        sorted.forEach((id, json) -> {
            if (MIGRATIONS_FILE.equals(id.getPath())) {
                decode(Migration.FILE_CODEC, json, ops, id, issues).ifPresent(migrations::addAll);
            }
        });
        return new RawBook(meta, chapters, quests, guides, sections, offers, issues, migrations);
    }

    /** Each card with a condition becomes a hidden quest in a chapter of its own section, out of sight of the story. */
    private static void addCardConditions(CardsSection section, List<Chapter> chapters, List<Quest> quests) {
        if (section.cards().stream().allMatch(card -> card.done().isEmpty())) {
            return;
        }
        ResourceLocation holder = section.holderChapter();
        chapters.add(new Chapter(holder, section.title(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), true, section.id().toString()));
        for (CardsSection.Card card : section.cards()) {
            card.done().ifPresent(task -> quests.add(new Quest(section.questId(card), Optional.of(holder), Optional.empty(), section.cardKey(card, "name"),
                    Optional.empty(), Optional.empty(), card.icon(), List.of(), RequiresMode.ALL, Optional.empty(), List.of(task), List.of(), Optional.empty(),
                    new QuestOptions(Layout.DEFAULT, 1, false, true, Optional.empty(), false, List.of()))));
        }
    }

    private static BookMeta parseMeta(Map<ResourceLocation, JsonElement> files, DynamicOps<JsonElement> ops, List<Issue> issues) {
        List<ResourceLocation> books = files.keySet().stream().filter(id -> BOOK_FILE.equals(id.getPath())).toList();
        if (books.isEmpty()) {
            return BookMeta.DEFAULT;
        }
        if (books.size() > 1) {
            issues.add(new Issue(Severity.WARNING, "multiple_books", books.getFirst().toString(), books.subList(1, books.size()).toString()));
        }
        ResourceLocation first = books.getFirst();
        return decode(BookMeta.CODEC, files.get(first), ops, first, issues).orElse(BookMeta.DEFAULT);
    }

    private static Quest linkChapter(Quest quest, ResourceLocation file, Map<ResourceLocation, ResourceLocation> chapterByFolder) {
        if (quest.chapter().isPresent()) {
            return quest;
        }
        ResourceLocation folder = folder(file);
        return quest.withChapter(chapterByFolder.getOrDefault(folder, folder));
    }

    private static <A> java.util.Optional<A> decode(Codec<A> codec, JsonElement json, DynamicOps<JsonElement> ops, ResourceLocation file, List<Issue> issues) {
        DataResult<A> result = codec.parse(ops, json);
        result.error().ifPresent(error -> issues.add(new Issue(Severity.ERROR, "parse_error", file.toString(), error.message())));
        return result.result();
    }

    private static boolean isInChapter(ResourceLocation id) {
        return id.getPath().startsWith(CHAPTERS) && id.getPath().indexOf('/', CHAPTERS.length()) > 0;
    }

    private static String fileName(ResourceLocation id) {
        return id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
    }

    /** The chapter folder of a file: {@code ns:chapters/dir/sub/file} gives {@code ns:dir}. */
    private static ResourceLocation folder(ResourceLocation id) {
        String path = id.getPath();
        return id.withPath(path.substring(CHAPTERS.length(), path.indexOf('/', CHAPTERS.length())));
    }
}
