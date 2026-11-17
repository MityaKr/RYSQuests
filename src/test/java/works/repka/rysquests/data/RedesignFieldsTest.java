package works.repka.rysquests.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.editor.EditorFiles;
import works.repka.rysquests.progress.ProgressStore;

/** The optional fields of the redesigned book: old files read as before, new ones round trip, and nothing is written needlessly. */
class RedesignFieldsTest {
    private static <A> JsonObject encode(com.mojang.serialization.Codec<A> codec, A value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();
    }

    private static <A> A decode(com.mojang.serialization.Codec<A> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, TestBooks.json(json)).getOrThrow();
    }

    @Test
    void anOldBookStaysOpenWithTheAutomaticForm() {
        BookMeta meta = decode(BookMeta.CODEC, "{\"progress_mode\":\"team\",\"title\":\"book.t.title\",\"chapter_order\":[\"t:a\"]}");
        assertTrue(meta.published(), "a book without the field must not close: RepYS would vanish for its players");
        assertEquals(MapForm.AUTO, meta.mapForm());
        assertEquals(List.of(), meta.epochs());
        JsonObject written = encode(BookMeta.CODEC, meta);
        assertFalse(written.has("published") || written.has("map_form") || written.has("epochs"), "defaults are not written: " + written);
    }

    @Test
    void epochsMapFormAndTheLockRoundTrip() {
        BookMeta meta = decode(BookMeta.CODEC, """
                {"epochs": ["book.t.epoch.1", {"title": "book.t.epoch.2", "imported": true}], "map_form": "galaxy", "published": false}
                """);
        assertEquals(List.of(new BookMeta.Epoch("book.t.epoch.1", false), new BookMeta.Epoch("book.t.epoch.2", true)), meta.epochs());
        assertEquals(MapForm.GALAXY, meta.mapForm());
        assertFalse(meta.published());
        JsonObject written = encode(BookMeta.CODEC, meta);
        assertEquals("book.t.epoch.1", written.getAsJsonArray("epochs").get(0).getAsString(), "an author's epoch is a plain string");
        assertTrue(written.getAsJsonArray("epochs").get(1).getAsJsonObject().get("imported").getAsBoolean());
        assertEquals(meta, BookMeta.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow());
        assertTrue(meta.authorEpoch(Optional.of(1)));
        assertFalse(meta.authorEpoch(Optional.of(2)), "an imported epoch is not the author's");
        assertTrue(meta.authorEpoch(Optional.of(3)), "an epoch without a name is still the author's");
        assertFalse(meta.authorEpoch(Optional.of(0)), "epoch 0 is no epoch");
    }

    @Test
    void bookSettingsAreWrittenInTheOrderOfTheFormat() {
        BookMeta meta = BookMeta.DEFAULT.withSections(List.of("story")).withEpochs(List.of(new BookMeta.Epoch("e", false))).withMapForm(MapForm.SPIRAL).withPublished(false);
        JsonObject written = EditorFiles.encodeMeta(meta).getAsJsonObject();
        assertEquals(List.of("sections", "epochs", "map_form", "published"), List.copyOf(written.keySet()));
    }

    @Test
    void sizeTwoReadsAsAMilestoneAndIsWrittenAsOne() {
        Quest quest = decode(Quest.CODEC, "{\"id\":\"t:q\",\"title\":\"q\",\"size\":2}");
        assertTrue(quest.options().milestone());
        JsonObject written = encode(Quest.CODEC, quest);
        assertTrue(written.get("milestone").getAsBoolean());
        assertFalse(written.has("size"), "the old field is not written again");
        assertEquals(quest, Quest.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow());

        Quest plain = decode(Quest.CODEC, "{\"id\":\"t:q\",\"title\":\"q\",\"size\":1}");
        assertFalse(plain.options().milestone());
        assertFalse(encode(Quest.CODEC, plain).has("milestone"));
    }

    @Test
    void draftAndArchiveRoundTripAndAreNotLive() {
        Quest quest = decode(Quest.CODEC, "{\"id\":\"t:q\",\"title\":\"q\",\"draft\":true,\"archived\":true,\"milestone\":true}");
        assertTrue(quest.options().draft());
        assertTrue(quest.options().archived());
        assertFalse(quest.options().live());
        assertEquals(quest, Quest.CODEC.parse(JsonOps.INSTANCE, encode(Quest.CODEC, quest)).getOrThrow());
        Quest plain = decode(Quest.CODEC, "{\"id\":\"t:q\",\"title\":\"q\"}");
        assertTrue(plain.options().live());
        JsonObject written = encode(Quest.CODEC, plain);
        assertFalse(written.has("draft") || written.has("archived"));
    }

    @Test
    void chapterOffTheMapRoundTrips() {
        Chapter chapter = decode(Chapter.CODEC, "{\"id\":\"t:c\",\"title\":\"c\",\"off_map\":true}");
        assertTrue(chapter.offMap());
        assertEquals(chapter, Chapter.CODEC.parse(JsonOps.INSTANCE, encode(Chapter.CODEC, chapter)).getOrThrow());
        Chapter plain = decode(Chapter.CODEC, "{\"id\":\"t:c\",\"title\":\"c\",\"epoch\":2}");
        assertFalse(plain.offMap());
        assertFalse(encode(Chapter.CODEC, plain).has("off_map"));
        assertTrue(plain.withGroups(List.of()).withOffMap(true).withEpoch(Optional.of(3)).offMap(), "copies keep the field");
    }

    @Test
    void anAchievementGoesToTheArchiveWithItsTiers() {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("t:achievements/jumper"), TestBooks.json("""
                {"id": "t:jumper", "archived": true, "task": {"type": "rysquests:stat", "stat": "minecraft:custom", "target": "minecraft:jump"},
                 "tiers": [{"tier": "bronze", "amount": 10}, {"tier": "platinum", "amount": 100}]}
                """));
        QuestBook book = TestBooks.book(files);
        Achievement achievement = book.achievements().get(ResourceLocation.parse("t:jumper"));
        assertTrue(achievement.archived());
        assertTrue(book.quests().values().stream().allMatch(quest -> quest.options().archived()), "its tiers are not counted");
        assertTrue(book.playerView().achievements().isEmpty(), "players are not sent it");
    }

    @Test
    void theMedalOfATierComesFromItsPlace() {
        assertEquals(List.of(Achievement.Medal.GOLD), medals(1));
        assertEquals(List.of(Achievement.Medal.SILVER, Achievement.Medal.GOLD), medals(2));
        assertEquals(List.of(Achievement.Medal.BRONZE, Achievement.Medal.SILVER, Achievement.Medal.GOLD), medals(3));
        assertEquals(List.of(Achievement.Medal.BRONZE, Achievement.Medal.BRONZE, Achievement.Medal.SILVER, Achievement.Medal.GOLD), medals(4), "Veteran: bronze, bronze, silver, gold");
    }

    private static List<Achievement.Medal> medals(int tiers) {
        return java.util.stream.IntStream.range(0, tiers).mapToObj(index -> Achievement.medal(index, tiers)).toList();
    }

    @Test
    void anOldShopLineIsThePlayersOwnWallet() {
        ProgressStore.ShopLine line = decode(ProgressStore.ShopLine.CODEC, "{\"time\":1,\"delta\":-5,\"currency\":\"coin\",\"ref\":\"t:o\",\"times\":1}");
        assertFalse(line.treasury());
        ProgressStore.ShopLine shared = new ProgressStore.ShopLine(1, -5, "coin", "t:o", 1, true);
        assertEquals(shared, ProgressStore.ShopLine.CODEC.parse(JsonOps.INSTANCE, encode(ProgressStore.ShopLine.CODEC, shared)).getOrThrow());
    }

    @Test
    void aPositionWithoutPinIsOnlyRemembered() {
        Layout kept = new Layout(false, Optional.of(48), Optional.of(-24));
        assertEquals(Optional.empty(), kept.fixedX());
        Layout pinned = new Layout(true, Optional.of(48), Optional.of(-24));
        assertEquals(Optional.of(48), pinned.fixedX());
        assertEquals(Optional.of(-24), pinned.fixedY());
    }

    @Test
    void theExampleBookStillLoadsClean() throws Exception {
        java.nio.file.Path pack = java.nio.file.Path.of(System.getProperty("rysquests.examplesDir")).resolve("example_pack/data/example/rysquests");
        BookValidator.Result result = TestBooks.validate(TestBooks.folder(pack, "example"));
        assertEquals(0, result.report().errors(), result.report().issues().toString());
        assertTrue(result.book().meta().published());
        assertTrue(result.book().quests().values().stream().anyMatch(quest -> quest.options().milestone()), "size 2 of the example reads as milestones");
    }
}
