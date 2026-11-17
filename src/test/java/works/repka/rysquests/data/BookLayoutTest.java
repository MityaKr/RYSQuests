package works.repka.rysquests.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.editor.EditorFiles;

/** layout.json beside the book, the hash it is checked by, and what players are sent. */
class BookLayoutTest {
    @TempDir
    Path root;

    private static Map<ResourceLocation, JsonElement> book(String questA, String questB) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("t:book"), TestBooks.json("{\"title\":\"book.t.title\"}"));
        files.put(ResourceLocation.parse("t:chapters/c/_chapter"), TestBooks.json("{\"id\":\"t:c\",\"title\":\"c\",\"epoch\":1}"));
        files.put(ResourceLocation.parse("t:chapters/c/a"), TestBooks.json(questA));
        files.put(ResourceLocation.parse("t:chapters/c/b"), TestBooks.json(questB));
        return files;
    }

    private static final String A = "{\"id\":\"t:c/a\",\"title\":\"a\",\"tasks\":[{\"type\":\"rysquests:checkbox\"}]}";
    private static final String B = "{\"id\":\"t:c/b\",\"title\":\"b\",\"requires\":[\"t:c/a\"],\"tasks\":[{\"type\":\"rysquests:checkbox\"}]}";

    private static BookLayout layoutFor(QuestBook book) {
        return new BookLayout(MapForm.SERPENTINE, book.layoutHash(), Map.of("t:c/a", new BookLayout.Point(0, 0), "t:c/b", new BookLayout.Point(200, 0)),
                Map.of("t:c", new BookLayout.Area(-50, -30, 300, 60), BookLayout.epochArea(1), new BookLayout.Area(-60, -40, 320, 80)));
    }

    @Test
    void theFileRoundTripsThroughItsCodecAndItsWriter() {
        QuestBook book = TestBooks.book(book(A, B));
        BookLayout layout = layoutFor(book);
        JsonElement encoded = BookLayout.CODEC.encodeStart(JsonOps.INSTANCE, layout).getOrThrow();
        assertEquals(layout, BookLayout.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        String text = EditorFiles.layoutText(layout);
        assertEquals(layout, BookLayout.CODEC.parse(JsonOps.INSTANCE, TestBooks.json(text)).getOrThrow());
        assertTrue(text.contains("    \"t:c/a\": [0, 0],\n    \"t:c/b\": [200, 0]\n"), "a node to a line, sorted: " + text);
    }

    @Test
    void theServerReadsTheLayoutWithTheBookAndSendsIt() throws Exception {
        QuestBook first = TestBooks.book(book(A, B));
        EditorFiles.saveLayout(root, "t", layoutFor(first), true);
        Map<ResourceLocation, JsonElement> files = book(A, B);
        files.put(ResourceLocation.parse("t:layout"), TestBooks.json(Files.readString(root.resolve("data/t/rysquests/layout.json"))));
        BookValidator.Result result = TestBooks.validate(files);
        assertTrue(result.book().freshLayout().isPresent());
        assertTrue(result.report().issues().stream().noneMatch(issue -> issue.code().equals("layout_stale")));
        QuestBook.Wire wire = result.book().wire();
        QuestBook decoded = QuestBook.fromWire(QuestBook.Wire.CODEC.parse(JsonOps.INSTANCE, QuestBook.Wire.CODEC.encodeStart(JsonOps.INSTANCE, wire).getOrThrow()).getOrThrow(), result.book().hash());
        assertEquals(result.book().layoutHash(), decoded.layoutHash(), "the client arrives at the same hash");
        assertTrue(decoded.freshLayout().isPresent(), "the layout travels with the book");
    }

    @Test
    void aLayoutOfAnotherBookIsStale() {
        QuestBook first = TestBooks.book(book(A, B));
        Map<ResourceLocation, JsonElement> files = book(A, "{\"id\":\"t:c/b\",\"title\":\"b\",\"tasks\":[{\"type\":\"rysquests:checkbox\"}]}");
        files.put(ResourceLocation.parse("t:layout"), BookLayout.CODEC.encodeStart(JsonOps.INSTANCE, layoutFor(first)).getOrThrow());
        BookValidator.Result result = TestBooks.validate(files);
        assertTrue(result.book().layout().isPresent(), "a stale layout still goes out; the client lays out itself");
        assertTrue(result.book().freshLayout().isEmpty());
        assertTrue(result.report().issues().stream().anyMatch(issue -> issue.code().equals("layout_stale")), "the check says the layout is stale");
    }

    @Test
    void textsDoNotMakeTheLayoutStaleButLinksDo() {
        String hash = TestBooks.book(book(A, B)).layoutHash();
        String retitled = TestBooks.book(book(A.replace("\"title\":\"a\"", "\"title\":\"other\",\"text\":\"t\""), B)).layoutHash();
        assertEquals(hash, retitled, "fixing a typo does not move anything");
        String unlinked = TestBooks.book(book(A, "{\"id\":\"t:c/b\",\"title\":\"b\",\"tasks\":[{\"type\":\"rysquests:checkbox\"}]}")).layoutHash();
        assertNotEquals(hash, unlinked);
        String milestone = TestBooks.book(book(A.replace("\"title\":\"a\"", "\"title\":\"a\",\"milestone\":true"), B)).layoutHash();
        assertNotEquals(hash, milestone);
        Map<ResourceLocation, JsonElement> galaxy = book(A, B);
        galaxy.put(ResourceLocation.parse("t:book"), TestBooks.json("{\"title\":\"book.t.title\",\"map_form\":\"galaxy\"}"));
        assertNotEquals(hash, TestBooks.book(galaxy).layoutHash(), "another form is another layout");
    }

    @Test
    void playersAreNotSentDraftsAndTheirHashIsTheSame() {
        Map<ResourceLocation, JsonElement> files = book(A, B);
        files.put(ResourceLocation.parse("t:chapters/c/d"), TestBooks.json("{\"id\":\"t:c/d\",\"title\":\"d\",\"draft\":true,\"tasks\":[{\"type\":\"rysquests:checkbox\"}]}"));
        files.put(ResourceLocation.parse("t:chapters/c/e"), TestBooks.json("{\"id\":\"t:c/e\",\"title\":\"e\",\"archived\":true,\"tasks\":[{\"type\":\"rysquests:checkbox\"}]}"));
        QuestBook book = TestBooks.book(files);
        assertEquals(4, book.quests().size(), "operators get everything");
        QuestBook shown = book.playerView();
        assertEquals(2, shown.quests().size());
        assertFalse(shown.quests().containsKey(ResourceLocation.parse("t:c/d")));
        assertEquals(book.layoutHash(), shown.layoutHash(), "drafts do not take part in the layout");
        assertEquals(TestBooks.book(book(A, B)).layoutHash(), book.layoutHash());
    }

    @Test
    void aClosedBookReachesPlayersEmpty() {
        Map<ResourceLocation, JsonElement> files = book(A, B);
        files.put(ResourceLocation.parse("t:book"), TestBooks.json("{\"title\":\"book.t.title\",\"published\":false}"));
        QuestBook book = TestBooks.book(files);
        assertEquals(2, book.quests().size());
        QuestBook shown = book.playerView();
        assertTrue(shown.quests().isEmpty() && shown.chapters().isEmpty());
        assertFalse(shown.meta().published());
        assertEquals(book.meta().title(), shown.meta().title(), "the name of the book still shows");
    }

    @Test
    void anOpenBookWithoutDraftsIsSentAsItIs() {
        QuestBook book = TestBooks.book(book(A, B));
        assertTrue(book.playerView() == book);
    }
}
