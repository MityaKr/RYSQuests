package works.repka.rysquests.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.TestBooks;
import works.repka.rysquests.progress.QuestProgress;

/**
 * The book's index for the recipe viewer's item links (plan _audit/ryscope-quests, step 2): which offers and quests an item leads to,
 * in which role, and the spoiler rule: only what the book itself shows, closed but visible with a lock, the rest neither shown nor
 * counted.
 */
class ItemLinkIndexTest {
    private static final ResourceLocation FRAGMENT = ResourceLocation.parse("minecraft:echo_shard");
    private static final ResourceLocation INGOT = ResourceLocation.parse("minecraft:netherite_ingot");
    private static final ResourceLocation DIAMOND = ResourceLocation.parse("minecraft:diamond");
    private static final ResourceLocation PLANKS = ResourceLocation.parse("minecraft:oak_planks");
    private static final ResourceLocation PLANKS_TAG = ResourceLocation.parse("minecraft:planks");
    private static final Function<ResourceLocation, QuestProgress> NO_PROGRESS = id -> QuestProgress.EMPTY;
    private static final Predicate<ResourceLocation> NO_STAGES = id -> false;

    private static void put(Map<ResourceLocation, JsonElement> files, String path, String json) {
        files.put(ResourceLocation.fromNamespaceAndPath("t", path), TestBooks.json(json));
    }

    private static Map<ResourceLocation, JsonElement> files() {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        put(files, "chapters/c/_chapter", "{\"id\":\"t:c\",\"title\":\"C\"}");
        put(files, "chapters/secret/_chapter", "{\"id\":\"t:secret\",\"title\":\"S\",\"hidden\":true}");
        // the first fragment: asked for in a task, it opens the trophy
        put(files, "chapters/c/first", """
                {"id":"t:first","title":"First","tasks":[{"type":"rysquests:item","item":"minecraft:echo_shard","mode":"observe"}]}""");
        put(files, "chapters/c/later", """
                {"id":"t:later","title":"Later","requires":["t:first"],
                 "tasks":[{"type":"rysquests:item","item":"minecraft:echo_shard","count":3}],
                 "rewards":[{"type":"rysquests:item","item":"minecraft:diamond","count":2}]}""");
        put(files, "chapters/c/choice", """
                {"id":"t:choice","title":"Choice","rewards":[{"type":"rysquests:choice","of":[
                    {"type":"rysquests:item","item":"minecraft:echo_shard"},{"type":"rysquests:item","item":"minecraft:diamond"}]}]}""");
        put(files, "chapters/c/planks", """
                {"id":"t:planks","title":"Planks","tasks":[{"type":"rysquests:item","item":"#minecraft:planks","count":8}]}""");
        // what a player never sees, whatever its items
        put(files, "chapters/c/hidden", """
                {"id":"t:hidden","title":"Hidden","hidden":true,"requires":["t:first"],
                 "tasks":[{"type":"rysquests:item","item":"minecraft:echo_shard"}],"rewards":[{"type":"rysquests:item","item":"minecraft:echo_shard"}]}""");
        put(files, "chapters/c/draft", """
                {"id":"t:draft","title":"Draft","draft":true,"tasks":[{"type":"rysquests:item","item":"minecraft:echo_shard"}]}""");
        put(files, "chapters/c/archived", """
                {"id":"t:archived","title":"Archived","archived":true,"rewards":[{"type":"rysquests:item","item":"minecraft:echo_shard"}]}""");
        put(files, "chapters/c/staged", """
                {"id":"t:staged","title":"Staged","stage_required":"nether","tasks":[{"type":"rysquests:item","item":"minecraft:echo_shard"}]}""");
        put(files, "chapters/secret/inside", """
                {"id":"t:inside","title":"Inside","rewards":[{"type":"rysquests:item","item":"minecraft:echo_shard"}]}""");
        // the shop: a closed delivery that gives the fragment, a purchase that takes it, a sale of it
        put(files, "shop/trophy", """
                {"id":"t:trophy","delivery":true,"quest":"t:first","lock_text":"shop.t.trophy.lock",
                 "cost":[{"type":"item","id":"minecraft:netherite_ingot","count":1984},{"type":"currency","id":"coin","amount":1000}],
                 "gives":[{"type":"item","id":"minecraft:echo_shard"}]}""");
        put(files, "shop/gem", """
                {"id":"t:gem","cost":[{"type":"item","id":"minecraft:echo_shard","count":2},{"type":"currency","id":"coin","amount":5}],
                 "gives":[{"type":"item","id":"minecraft:diamond"}]}""");
        put(files, "shop/sell", """
                {"id":"t:sell","cost":[{"type":"item","id":"minecraft:echo_shard"}],"gives":[{"type":"currency","id":"coin","amount":7}]}""");
        return files;
    }

    private static ItemLinkIndex index(Map<ResourceLocation, JsonElement> files) {
        return ItemLinkIndex.build(TestBooks.book(files));
    }

    private static List<String> ids(List<?> list) {
        return list.stream().map(entry -> entry instanceof Quest quest ? quest.id().toString() : ((ShopOffer) entry).id().toString()).toList();
    }

    private static List<String> visible(ItemLinkIndex index, List<Quest> quests, Function<ResourceLocation, QuestProgress> progress, Predicate<ResourceLocation> stages) {
        return ids(quests.stream().filter(quest -> index.visible(quest, progress, stages)).toList());
    }

    @Test
    void anOfferThatGivesTheItemIsASourceAndOneThatTakesItAUse() {
        ItemLinkIndex index = index(files());
        assertTrue(index.shop());
        assertEquals(List.of("t:trophy"), ids(index.offersGiving(FRAGMENT)), "the trophy delivery gives the fragment: Источники");
        assertEquals(Set.of("t:gem", "t:sell"), Set.copyOf(ids(index.offersTaking(FRAGMENT))), "a price and a sale take it: Использования");
        assertEquals(List.of("t:trophy"), ids(index.offersTaking(INGOT)), "what a delivery asks for is a use of that item");
        assertEquals(List.of("t:gem"), ids(index.offersGiving(DIAMOND)));
        assertTrue(index.offersGiving(INGOT).isEmpty());
    }

    @Test
    void questsAskForTheItemOrGiveItIncludingOneOfAChoice() {
        ItemLinkIndex index = index(files());
        assertEquals(Set.of("t:first", "t:later", "t:staged"), Set.copyOf(visible(index, index.asking(FRAGMENT, tag -> false), NO_PROGRESS, NO_STAGES)));
        assertEquals(List.of("t:choice"), visible(index, index.rewarding(FRAGMENT), NO_PROGRESS, NO_STAGES), "a choice gives each of its items");
        assertEquals(Set.of("t:later", "t:choice"), Set.copyOf(ids(index.rewarding(DIAMOND))));
    }

    @Test
    void aTagInATaskFindsEveryItemThatCarriesIt() {
        ItemLinkIndex index = index(files());
        assertEquals(List.of("t:planks"), ids(index.asking(PLANKS, PLANKS_TAG::equals)));
        assertTrue(index.asking(PLANKS, tag -> false).isEmpty(), "an item without the tag is not asked for");
    }

    @Test
    void draftsTheArchiveAndHiddenChaptersAreNeverListed() {
        ItemLinkIndex index = index(files());
        List<String> all = ids(index.asking(FRAGMENT, tag -> false));
        List<String> rewards = ids(index.rewarding(FRAGMENT));
        for (String gone : List.of("t:draft", "t:archived", "t:inside")) {
            assertFalse(all.contains(gone) || rewards.contains(gone), gone + " is not in the book a player sees");
        }
    }

    @Test
    void aHiddenQuestShowsOnlyOnceItHasOpened() {
        ItemLinkIndex index = index(files());
        assertFalse(visible(index, index.asking(FRAGMENT, tag -> false), NO_PROGRESS, NO_STAGES).contains("t:hidden"), "neither shown nor counted");
        assertFalse(visible(index, index.rewarding(FRAGMENT), NO_PROGRESS, NO_STAGES).contains("t:hidden"));
        QuestProgress done = new QuestProgress(List.of(1), 1L, List.of());
        Function<ResourceLocation, QuestProgress> firstDone = id -> id.equals(ResourceLocation.parse("t:first")) ? done : QuestProgress.EMPTY;
        assertTrue(visible(index, index.asking(FRAGMENT, tag -> false), firstDone, NO_STAGES).contains("t:hidden"), "open now: an ordinary quest");
    }

    @Test
    void aQuestBehindAClosedStageIsNotShown() {
        ItemLinkIndex index = index(files());
        Predicate<ResourceLocation> staged = id -> id.equals(ResourceLocation.parse("t:staged"));
        assertTrue(visible(index, index.asking(FRAGMENT, tag -> false), NO_PROGRESS, NO_STAGES).contains("t:staged"));
        assertFalse(visible(index, index.asking(FRAGMENT, tag -> false), NO_PROGRESS, staged).contains("t:staged"));
    }

    @Test
    void aLockedButVisibleQuestStaysWithItsLock() {
        ItemLinkIndex index = index(files());
        // "Later" waits for "First": closed, yet on the map, so it is shown (the card draws the lock).
        assertTrue(visible(index, index.asking(FRAGMENT, tag -> false), NO_PROGRESS, NO_STAGES).contains("t:later"));
    }

    @Test
    void aBookWithoutAShopOrStoryGivesNothingOfThem() {
        Map<ResourceLocation, JsonElement> files = files();
        put(files, "book", "{\"sections\":[\"guides\"]}");
        ItemLinkIndex index = index(files);
        assertFalse(index.shop());
        assertTrue(index.offersGiving(FRAGMENT).isEmpty() && index.offersTaking(FRAGMENT).isEmpty());
        assertTrue(index.asking(FRAGMENT, tag -> false).isEmpty() && index.rewarding(FRAGMENT).isEmpty());
    }

    @Test
    void anEpochsRewardStaysAtItsEpoch() {
        Map<ResourceLocation, JsonElement> files = files();
        put(files, "book", "{\"epochs\":[{\"title\":\"book.t.epoch.1\",\"quest\":\"t:choice\"}]}");
        put(files, "chapters/c/_chapter", "{\"id\":\"t:c\",\"title\":\"C\",\"epoch\":1}");
        ItemLinkIndex index = index(files);
        assertFalse(ids(index.rewarding(FRAGMENT)).contains("t:choice"));
    }

    @Test
    void anEmptyBookKnowsNothing() {
        ItemLinkIndex index = ItemLinkIndex.build(QuestBook.EMPTY);
        assertTrue(index.offersGiving(FRAGMENT).isEmpty() && index.asking(FRAGMENT, tag -> true).isEmpty() && index.rewarding(FRAGMENT).isEmpty());
        assertEquals(Set.of(), ItemLinkIndex.shownChapters(QuestBook.EMPTY));
    }
}
