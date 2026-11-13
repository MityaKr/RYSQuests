package works.repka.rysquests.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.data.BookHash;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.TestBooks;

/** The editor's draft of the whole book (plan, section 6): edits land in it, it lays over the book, and it outlives the game on disk. */
class BookDraftTest {
    private static final ResourceLocation CHAPTER = ResourceLocation.parse("t:c");

    @TempDir
    Path folder;

    private static QuestBook book() {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("t:chapters/c/_chapter"), TestBooks.json("{\"id\":\"t:c\",\"title\":\"c\"}"));
        files.put(ResourceLocation.parse("t:chapters/c/a"), TestBooks.json("{\"id\":\"t:c/a\",\"title\":\"a\"}"));
        files.put(ResourceLocation.parse("t:chapters/c/b"), TestBooks.json("{\"id\":\"t:c/b\",\"title\":\"b\",\"requires\":[\"t:c/a\"]}"));
        files.put(ResourceLocation.parse("t:chapters/d/_chapter"), TestBooks.json("{\"id\":\"t:d\",\"title\":\"d\"}"));
        files.put(ResourceLocation.parse("t:chapters/d/x"), TestBooks.json("{\"id\":\"t:d/x\",\"title\":\"x\"}"));
        return TestBooks.book(files);
    }

    private static BookDraft.Work work(QuestBook book, ResourceLocation chapter) {
        List<Quest> quests = book.quests().values().stream().filter(quest -> quest.chapter().filter(chapter::equals).isPresent()).toList();
        return new BookDraft.Work(book.chapters().get(chapter), quests, BookHash.chapterVersion(book, chapter, JsonOps.INSTANCE), false);
    }

    @Test
    void editsLayOverTheBookAndCount() {
        QuestBook book = book();
        BookDraft.Work work = work(book, CHAPTER);
        ResourceLocation id = QuestEdits.freshQuest(CHAPTER, book.quests().keySet());
        Quest added = QuestEdits.newQuest(id, CHAPTER, QuestEdits.Kind.SIDE, List.of(ResourceLocation.parse("t:c/b")));
        BookDraft draft = BookDraft.empty("en_us")
                .withWork(work.withQuestAfter(added, ResourceLocation.parse("t:c/a"))).withText(added.title(), "New one").logged("chapter:t:c\tnew quest")
                .withMeta(book.meta().withMapForm(MapForm.SPIRAL)).logged("meta\tform");
        assertEquals(2, draft.count(), "a line per edit: the N of the save button");
        QuestBook applied = draft.apply(book);
        assertTrue(applied.quests().containsKey(id), "the new quest is in the book as it will be");
        assertTrue(applied.quests().get(id).options().optional(), "a side quest");
        assertEquals(MapForm.SPIRAL, applied.meta().mapForm());
        assertTrue(applied.quests().containsKey(ResourceLocation.parse("t:d/x")), "chapters the draft did not touch stay as they are");
        assertEquals(List.of(ResourceLocation.parse("t:c/a"), id, ResourceLocation.parse("t:c/b")),
                draft.chapters().get(CHAPTER).quests().stream().map(Quest::id).toList(), "a quest typed under a row stays under it");
        assertEquals(Optional.of("New one"), draft.shown(added.title()));
        assertTrue(BookDraft.keysOf(draft.chapters().get(CHAPTER)).contains(added.title()), "the save sends the strings the chapter's files name");
    }

    @Test
    void anOfferIsRemovedAtOnceAndANewOneSimplyGoes() {
        ShopOffer offer = new ShopOffer(ResourceLocation.parse("t:o"), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(new ShopOffer.Entry(ShopOffer.Kind.CURRENCY, "coin", 5)), List.of(new ShopOffer.Entry(ShopOffer.Kind.ITEM, "minecraft:bread", 1)),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        BookDraft added = BookDraft.empty("en_us").withOffer(offer);
        assertTrue(added.withoutOffer(offer.id(), false).removed().isEmpty(), "an unsaved offer leaves the draft only");
        assertEquals(List.of(offer.id()), added.withoutOffer(offer.id(), true).removed(), "a saved one is removed from the pack on save");
        assertTrue(added.withoutOffer(offer.id(), true).withOffer(offer).removed().isEmpty(), "put back, it is no longer removed");
    }

    @Test
    void theDraftOutlivesTheGame() throws Exception {
        QuestBook book = book();
        Quest renamed = QuestEdits.withTexts(book.quests().get(ResourceLocation.parse("t:c/a")), "quest.t.c.a.title", Optional.empty(), Optional.empty());
        BookDraft draft = BookDraft.empty("ru_ru").withWork(work(book, CHAPTER).withQuest(renamed)).withText("quest.t.c.a.title", "Верстак").logged("chapter:t:c\t«a»: название");
        EditorDrafts.saveBook(folder, draft, JsonOps.INSTANCE);
        BookDraft back = EditorDrafts.loadBook(folder, JsonOps.INSTANCE).orElseThrow();
        assertEquals(draft.count(), back.count());
        assertEquals(draft.chapters().get(CHAPTER).baseVersion(), back.chapters().get(CHAPTER).baseVersion(), "the version the editing began from survives, for the conflict check");
        assertEquals(Optional.of("Верстак"), back.shown("quest.t.c.a.title"));
        assertEquals(draft.apply(book).quests(), back.apply(book).quests());
        assertTrue(EditorDrafts.chapters(folder).isEmpty(), "the book's file never reads as a chapter's draft");
        EditorDrafts.discardBook(folder);
        assertFalse(EditorDrafts.loadBook(folder, JsonOps.INSTANCE).isPresent(), "a saved or dropped draft is gone");
        assertTrue(Files.isRegularFile(folder.resolve(EditorDrafts.BOOK_FILE + ".old")), "but put aside rather than deleted");
    }

    @Test
    void aLinkThatClosesACircleIsFound() {
        QuestBook book = book();
        assertTrue(QuestEdits.wouldCycle(book.quests(), ResourceLocation.parse("t:c/a"), ResourceLocation.parse("t:c/b")), "a after b, while b is after a");
        assertFalse(QuestEdits.wouldCycle(book.quests(), ResourceLocation.parse("t:c/b"), ResourceLocation.parse("t:d/x")));
        Quest milestone = QuestEdits.withKind(book.quests().get(ResourceLocation.parse("t:c/a")), QuestEdits.Kind.MILESTONE);
        assertEquals(QuestEdits.Kind.MILESTONE, QuestEdits.kind(milestone));
        assertEquals(QuestEdits.Kind.MAIN, QuestEdits.kind(QuestEdits.withKind(milestone, QuestEdits.Kind.MAIN)), "one switch: main, side or milestone");
    }

    @Test
    void offersAndAchievementsAreWrittenAndARemovedOfferIsPutAside() throws Exception {
        ShopOffer offer = new ShopOffer(ResourceLocation.parse("t:shop/bread"), Optional.of("shop.t.bread"), Optional.empty(), Optional.empty(),
                List.of(new ShopOffer.Entry(ShopOffer.Kind.CURRENCY, "coin", 5)), List.of(new ShopOffer.Entry(ShopOffer.Kind.ITEM, "minecraft:bread", 1)),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        JsonObject achievement = JsonParser.parseString("{\"id\":\"t:miner\",\"title\":\"achievement.t.miner.title\",\"tiers\":[{\"tier\":\"tier_1\",\"amount\":10}],"
                + "\"task\":{\"type\":\"rysquests:stat\",\"stat\":\"minecraft:mined\",\"target\":\"minecraft:stone\"}}").getAsJsonObject();
        EditorFiles.saveBookEdits(folder, "t", Optional.empty(), List.of(offer), List.of(), List.of(achievement), "en_us",
                Map.of("shop.t.bread", "Bread", "achievement.t.miner.title", "Miner"), JsonOps.INSTANCE, true);
        Path offerFile = folder.resolve("data/t/rysquests/shop/shop/bread.json");
        assertTrue(Files.isRegularFile(offerFile));
        assertTrue(Files.isRegularFile(folder.resolve("data/t/rysquests/achievements/miner.json")));
        assertTrue(Files.readString(folder.resolve("assets/t/lang/en_us.json")).contains("Miner"));
        EditorFiles.saveBookEdits(folder, "t", Optional.empty(), List.of(), List.of(offer.id()), List.of(), "en_us", Map.of(), JsonOps.INSTANCE, true);
        assertFalse(Files.exists(offerFile), "a removed offer leaves the data");
        assertTrue(Files.isRegularFile(folder.resolve("_removed/data/t/rysquests/shop/shop/bread.json")), "and is put aside, not deleted");
    }
}
