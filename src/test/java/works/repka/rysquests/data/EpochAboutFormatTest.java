package works.repka.rysquests.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.editor.EditorFiles;

/**
 * The fields of {@code book.json} that 0.2.7 adds (ADR-60): an epoch names the quest of its reward, and {@code about} names the
 * pages "О книге", a guide of the book or pages of their own. Old books read as before and are written as before.
 */
class EpochAboutFormatTest {
    private static final ResourceLocation ERA_1 = ResourceLocation.parse("t:start/i_mechanics");
    private static final ResourceLocation ERA_2 = ResourceLocation.parse("t:start/ii_industry");

    private static BookMeta meta(String json) {
        return BookMeta.CODEC.parse(JsonOps.INSTANCE, TestBooks.json(json)).getOrThrow();
    }

    private static JsonObject write(BookMeta meta) {
        return BookMeta.CODEC.encodeStart(JsonOps.INSTANCE, meta).getOrThrow().getAsJsonObject();
    }

    @Test
    void anEpochNamesItsRewardAndIsWrittenAsAnObjectOnlyThen() {
        BookMeta meta = meta("""
                {"epochs": ["book.t.epoch.1", {"title": "book.t.epoch.2", "quest": "t:start/ii_industry"}]}
                """);
        assertEquals(Optional.empty(), meta.epochs().get(0).quest());
        assertEquals(Optional.of(ERA_2), meta.epochs().get(1).quest());
        assertEquals(Map.of(ERA_2, 2), meta.epochRewards());
        assertEquals(Optional.of(ERA_2), meta.epochReward(2));
        assertEquals(Optional.empty(), meta.epochReward(1));
        JsonObject written = write(meta);
        assertTrue(written.getAsJsonArray("epochs").get(0).isJsonPrimitive(), "an epoch without a reward stays a plain string");
        JsonObject second = written.getAsJsonArray("epochs").get(1).getAsJsonObject();
        assertEquals("t:start/ii_industry", second.get("quest").getAsString());
        assertFalse(second.has("imported"), "defaults are not written");
        assertEquals(meta, BookMeta.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow());
    }

    @Test
    void aboutIsAGuideOrPagesOfItsOwn() {
        BookMeta guide = meta("{\"about\": \"t:book\"}");
        assertEquals(Optional.of(BookMeta.About.guide(ResourceLocation.parse("t:book"))), guide.about());
        assertEquals("t:book", write(guide).get("about").getAsString(), "a guide is written as its id");

        BookMeta pages = meta("""
                {"about": [ {"id": "welcome", "title": "book.t.about.welcome", "text": "book.t.about.welcome.text"},
                            {"id": "coins", "title": "book.t.about.coins", "text": "book.t.about.coins.text"} ]}
                """);
        BookMeta.About about = pages.about().orElseThrow();
        assertEquals(Optional.empty(), about.guide());
        assertEquals(List.of("welcome", "coins"), about.pages().stream().map(Guide.Page::id).toList());
        assertTrue(write(pages).get("about").isJsonArray());
        assertEquals(pages, BookMeta.CODEC.parse(JsonOps.INSTANCE, write(pages)).getOrThrow());

        BookMeta none = meta("{\"title\": \"book.t.title\"}");
        assertEquals(Optional.empty(), none.about());
        assertFalse(write(none).has("about"));
    }

    @Test
    void aboutIsWrittenLastInTheOrderOfTheFormat() {
        BookMeta meta = BookMeta.DEFAULT.withSections(List.of("story")).withAbout(Optional.of(BookMeta.About.guide(ResourceLocation.parse("t:book"))))
                .withEpochs(List.of(new BookMeta.Epoch("e", false, Optional.of(ERA_1)))).withPublished(false);
        JsonObject written = EditorFiles.encodeMeta(meta).getAsJsonObject();
        assertEquals(List.of("sections", "epochs", "published", "about"), List.copyOf(written.keySet()));
    }

    /** A book of RepYS's shape: two epochs with their rewards in an off-map chapter "Начало" that keeps nothing else. */
    private static Map<ResourceLocation, JsonElement> book(String epochs, String about) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("t:book"), TestBooks.json("{\"epochs\": " + epochs + (about.isEmpty() ? "" : ", \"about\": " + about) + "}"));
        files.put(ResourceLocation.parse("t:chapters/mech/_chapter"), TestBooks.json("{\"id\": \"t:mech\", \"title\": \"Mech\", \"epoch\": 1}"));
        files.put(ResourceLocation.parse("t:chapters/mech/key"), TestBooks.json("{\"id\": \"t:mech/key\", \"title\": \"Key\", \"milestone\": true}"));
        files.put(ResourceLocation.parse("t:chapters/start/_chapter"), TestBooks.json("{\"id\": \"t:start\", \"title\": \"Start\", \"off_map\": true}"));
        files.put(ResourceLocation.parse("t:chapters/start/i_mechanics"), TestBooks.json("""
                {"id": "t:start/i_mechanics", "title": "I", "tasks": [ {"type": "rysquests:checkbox"} ], "rewards": [ {"type": "rysquests:xp", "points": 300} ]}
                """));
        files.put(ResourceLocation.parse("t:chapters/start/ii_industry"), TestBooks.json("""
                {"id": "t:start/ii_industry", "title": "II", "requires": ["t:mech/key"], "tasks": [ {"type": "rysquests:checkbox"} ],
                 "rewards": [ {"type": "rysquests:xp", "points": 300} ]}
                """));
        files.put(ResourceLocation.parse("t:guides/book"), TestBooks.json("""
                {"id": "t:book", "title": "About", "pages": [ {"id": "welcome", "title": "Welcome", "text": "Hello"} ]}
                """));
        return files;
    }

    private static final String EPOCHS = "[{\"title\": \"Mechanics\", \"quest\": \"t:start/i_mechanics\"}, {\"title\": \"Industry\", \"quest\": \"t:start/ii_industry\"}]";

    private static Set<String> codes(BookValidator.Result result) {
        return result.report().issues().stream().map(BookReport.Issue::code).collect(Collectors.toSet());
    }

    @Test
    void aBookWithEpochRewardsAndAboutIsClean() {
        BookValidator.Result result = TestBooks.validate(book(EPOCHS, "\"t:book\""));
        assertEquals(List.of(), result.report().issues());
        QuestBook book = result.book();
        assertEquals(1, book.epochOf(ERA_1));
        assertEquals(2, book.epochOf(ERA_2));
        assertFalse(book.isEpochReward(ResourceLocation.parse("t:mech/key")));
        assertEquals(ResourceLocation.parse("t:book"), book.aboutGuide().orElseThrow().id());
        assertEquals(Set.of(ResourceLocation.parse("t:start")), MapBook.rewardsOnly(book.meta(), book.quests().values()),
                "a chapter that only keeps epoch rewards is a service one");
    }

    @Test
    void theBooksOwnPagesAreReadAsAGuide() {
        QuestBook book = TestBooks.book(book(EPOCHS, "[{\"id\": \"a\", \"title\": \"A\", \"text\": \"a\"}, {\"id\": \"b\", \"title\": \"B\", \"text\": \"b\"}]"));
        Guide about = book.aboutGuide().orElseThrow();
        assertEquals(BookMeta.About.OWN, about.id());
        assertEquals(2, about.pages().size());
        assertEquals(Optional.of(about), book.guideOrAbout(BookMeta.About.OWN), "the server marks its pages read like a guide's");
        assertTrue(book.guideOrAbout(ResourceLocation.parse("t:book")).isPresent());
    }

    @Test
    void brokenReferencesAreReported() {
        String broken = "[{\"title\": \"A\", \"quest\": \"t:start/nothing\"}, {\"title\": \"B\", \"quest\": \"t:start/i_mechanics\"}, {\"title\": \"C\", \"quest\": \"t:start/i_mechanics\"}]";
        Set<String> found = codes(TestBooks.validate(book(broken, "\"t:no_guide\"")));
        assertTrue(found.containsAll(Set.of("epoch_unknown_quest", "epoch_quest_twice", "about_unknown_guide")), found.toString());

        Set<String> pages = codes(TestBooks.validate(book(EPOCHS, "[{\"id\": \"a\", \"title\": \"A\", \"text\": \"a\"}, {\"id\": \"a\", \"title\": \"B\", \"text\": \"b\"}]")));
        assertTrue(pages.contains("duplicate_guide_page"), pages.toString());
        assertTrue(codes(TestBooks.validate(book(EPOCHS, "[]"))).contains("about_empty"));
    }

    @Test
    void theAutotestBookIsClean() throws java.io.IOException {
        // the book of the in-game scenario "chronicle", so that the scenario never stops on its own data
        BookValidator.Result result = TestBooks.validate(TestBooks.folder(java.nio.file.Path.of(System.getProperty("rysquests.examplesDir")).resolveSibling("src/autotest/chronicle_pack/data/chronicle/rysquests"), "chronicle"));
        assertEquals(List.of(), result.report().issues());
        QuestBook book = result.book();
        assertEquals(2, book.epochOf(ResourceLocation.parse("chronicle:start/ii_industry")));
        assertEquals(ResourceLocation.parse("chronicle:about"), book.aboutGuide().orElseThrow().id());
        assertTrue(book.sections().containsKey(ResourceLocation.parse("chronicle:atlas")));
    }

    @Test
    void anEpochsRewardLeavesTheMapsLayout() {
        QuestBook plain = TestBooks.book(book("[\"Mechanics\", \"Industry\"]", ""));
        QuestBook rewarded = TestBooks.book(book(EPOCHS, ""));
        assertNotEquals(plain.layoutHash(), rewarded.layoutHash(), "the rewards no longer stand in their chapter");
        QuestBook again = TestBooks.book(book(EPOCHS, "\"t:book\""));
        assertEquals(rewarded.layoutHash(), again.layoutHash(), "the pages \"О книге\" do not move the map");
    }
}
