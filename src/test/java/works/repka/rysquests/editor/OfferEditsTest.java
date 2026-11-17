package works.repka.rysquests.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.OptionalInt;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.ShopOffer.Entry;
import works.repka.rysquests.data.ShopOffer.Kind;
import works.repka.rysquests.data.TestBooks;

/**
 * The editor's shop since 0.2.7 (shop plan, step M9; ADR-58): every edit keeps what it does not touch, a delivery and the line of the
 * closed card are edited by the rules the book checks, and a trade saved by the editor reads back as it was, its strings included.
 */
class OfferEditsTest {
    private static final DataComponentPatch EPIC = DataComponentPatch.builder().set(DataComponents.RARITY, Rarity.EPIC).build();
    /** A trade with every property a pack author can give it since 0.2.7. */
    private static final String TROPHY = """
            {"id":"t:shop/trophy","title":"shop.t.trophy","icon":"minecraft:nether_star","category":"shop.t.trophies","delivery":true,
             "cost":[{"type":"item","id":"minecraft:iron_ingot","count":4096},{"type":"item","id":"minecraft:gold_ingot","count":512},
                     {"type":"currency","id":"token","amount":500}],
             "gives":[{"type":"item","id":"minecraft:paper","count":1,"components":{"minecraft:rarity":"epic"}}],
             "limit":{"per":"team","count":1},"stage":"t:epoch_8","stage_title":"shop.t.epoch_8",
             "quest":"t:_achievements/slayer_ignis/gold","lock_text":"shop.t.trophy.lock"}""";

    @TempDir
    Path folder;

    private static ShopOffer read(String json) {
        return ShopOffer.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static Entry item(String id, int count) {
        return new Entry(Kind.ITEM, id, count);
    }

    /** Everything but the sides of the trade, which each edit is free to change only where it says. */
    private static void keepsTheRest(ShopOffer before, ShopOffer after) {
        assertEquals(before.id(), after.id());
        assertEquals(before.icon(), after.icon());
        assertEquals(before.stage(), after.stage());
        assertEquals(before.stageTitle(), after.stageTitle());
    }

    @Test
    void everyEditKeepsWhatItDoesNotTouch() {
        ShopOffer trophy = read(TROPHY);

        ShopOffer priced = OfferEdits.withPrice(trophy, 700);
        keepsTheRest(trophy, priced);
        assertEquals(700, OfferEdits.price(priced));
        assertEquals("token", priced.cost().stream().filter(Entry::isCurrency).findFirst().orElseThrow().id(), "a token stays a token");
        assertEquals(OfferEdits.payItems(trophy), OfferEdits.payItems(priced), "the price leaves the delivery's rows alone");
        assertEquals(trophy.gives(), priced.gives());
        assertTrue(priced.delivery());
        assertEquals(trophy.lockText(), priced.lockText());
        assertEquals(trophy.limit(), priced.limit());
        assertEquals(trophy.quest(), priced.quest());

        ShopOffer more = OfferEdits.withGoods(trophy, List.of(trophy.gives().getFirst(), item("minecraft:bread", 3)));
        keepsTheRest(trophy, more);
        assertEquals(trophy.cost(), more.cost(), "new goods leave the price and the rows to deliver");
        assertEquals(Optional.of(EPIC), more.gives().getFirst().components(), "and the components of what was there");
        assertTrue(more.delivery());

        ShopOffer counted = OfferEdits.withCount(trophy, true, 1, 1024);
        assertEquals(List.of(item("minecraft:iron_ingot", 4096), item("minecraft:gold_ingot", 1024)), OfferEdits.payItems(counted));
        assertEquals(500, OfferEdits.price(counted));
        assertEquals(trophy.gives(), counted.gives());

        for (ShopOffer edited : List.of(OfferEdits.withTitle(trophy, Optional.of("shop.t.other")), OfferEdits.withCategory(trophy, Optional.empty()),
                OfferEdits.withLimit(trophy, Optional.empty()), OfferEdits.withQuest(trophy, Optional.empty()),
                OfferEdits.withComponents(trophy, 0, Optional.empty()), OfferEdits.withLockText(trophy, Optional.of("Победи Игниса")))) {
            keepsTheRest(trophy, edited);
            assertTrue(edited.delivery(), "no edit of another field drops the delivery");
            assertEquals(OfferEdits.payItems(trophy), OfferEdits.payItems(edited));
            assertEquals(500, OfferEdits.price(edited));
        }
        assertEquals(Optional.of("Победи Игниса"), OfferEdits.withLockText(trophy, Optional.of("Победи Игниса")).lockText());
        assertEquals(Optional.empty(), OfferEdits.withLockText(trophy, Optional.of("  ")).lockText(), "an empty line gives the card its own words back");
        assertEquals(Optional.empty(), OfferEdits.withComponents(trophy, 0, Optional.empty()).gives().getFirst().components());
    }

    @Test
    void aDeliveryNeedsSomethingToBringAndStaysAPurchase() {
        ShopOffer bread = read("""
                {"id":"t:bread","cost":[{"type":"currency","id":"coin","amount":5}],"gives":[{"type":"item","id":"minecraft:bread","count":1}]}""");

        assertSame(bread, OfferEdits.withDelivery(bread, true, Optional.empty()), "nothing to bring and an empty hand: it stays as it was");
        ShopOffer delivery = OfferEdits.withDelivery(bread, true,
                Optional.of(new Entry(Kind.ITEM, "minecraft:wheat", 64, Optional.of(EPIC))));
        assertTrue(delivery.delivery());
        assertEquals(List.of(item("minecraft:wheat", 64)), OfferEdits.payItems(delivery), "the held item, plain: a player brings plain items");
        assertEquals(5, OfferEdits.price(delivery), "the coins stay, paid when the goods are taken");
        assertTrue(OfferEdits.problems(delivery).isEmpty());

        assertSame(delivery, OfferEdits.withPayItems(delivery, List.of()), "a delivery keeps its last row");
        assertSame(delivery, OfferEdits.flipped(delivery), "a delivery is always a purchase");
        assertSame(delivery, OfferEdits.withCount(delivery, true, 0, 0), "a count that is not positive changes nothing");

        ShopOffer barter = OfferEdits.withDelivery(delivery, false, Optional.empty());
        assertFalse(barter.delivery());
        assertEquals(delivery.cost(), barter.cost(), "switched off, the items stay and are paid at once");
        ShopOffer plain = OfferEdits.withPayItems(barter, List.of());
        assertEquals(List.of(new Entry(Kind.CURRENCY, "coin", 5)), plain.cost(), "a barter may lose all its items");

        ShopOffer sale = OfferEdits.flipped(bread);
        assertTrue(sale.isSale());
        assertSame(sale, OfferEdits.withDelivery(sale, true, Optional.of(item("minecraft:wheat", 1))), "a sale is never a delivery");
        assertEquals(bread, OfferEdits.flipped(sale));
    }

    @Test
    void theEditorNamesWhatTheBookWouldLeaveOutOrNobodyWouldSee() {
        ShopOffer trophy = read(TROPHY);
        assertTrue(OfferEdits.problems(trophy).isEmpty());
        ShopOffer emptyDelivery = new ShopOffer(trophy.id(), trophy.title(), trophy.icon(), trophy.category(), List.of(new Entry(Kind.CURRENCY, "coin", 5)),
                trophy.gives(), trophy.limit(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("shop.t.lock"), true);
        assertEquals(List.of("delivery_items", "lock_unseen"), OfferEdits.problems(emptyDelivery));
        assertEquals(OptionalInt.of(4096), OfferEdits.positive(" 4096 "));
        assertEquals(OptionalInt.empty(), OfferEdits.positive("0"));
        assertEquals(OptionalInt.empty(), OfferEdits.positive("4k"));
        assertEquals("shop.t.trophy.lock", OfferEdits.lockKey(trophy, key -> key.startsWith("shop.")), "its own key is typed into");
        assertEquals("shop.t.shop.trophy.lock", OfferEdits.lockKey(OfferEdits.withLockText(trophy, Optional.of("Победи Игниса")), key -> false),
                "a line written as text gets a key beside the trade's name");
    }

    @Test
    void theBossesAreTheFirstTiersOfKillAchievements() {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("t:achievements/slayer_ignis"), TestBooks.json("""
                {"id":"t:slayer_ignis","task":{"type":"rysquests:kill","entity":"cataclysm:ignis"},
                 "tiers":[{"tier":"gold","amount":1},{"tier":"platinum","amount":10}]}"""));
        files.put(ResourceLocation.parse("t:achievements/miner"), TestBooks.json("""
                {"id":"t:miner","task":{"type":"rysquests:stat","stat":"minecraft:mined","target":"minecraft:stone"},"tiers":[{"tier":"tier_1","amount":10}]}"""));
        QuestBook book = TestBooks.book(files);

        assertEquals(List.of(new OfferEdits.Boss(ResourceLocation.parse("t:_achievements/slayer_ignis/gold"), ResourceLocation.parse("cataclysm:ignis"))),
                OfferEdits.bosses(book));
    }

    @Test
    void aTradeSavedByTheEditorReadsBackAsItWasWithItsStrings() throws Exception {
        // As the editor leaves it after typing: a delivery row added, its count typed, the line of the closed card typed into a new key.
        ShopOffer edited = OfferEdits.withCount(OfferEdits.withPayItems(read(TROPHY), List.of(item("minecraft:iron_ingot", 4096), item("minecraft:gold_ingot", 512),
                item("minecraft:copper_ingot", 64))), true, 2, 2048);
        String lockKey = OfferEdits.lockKey(OfferEdits.withLockText(edited, Optional.of("Победи Игниса")), key -> false);
        edited = OfferEdits.withLockText(edited, Optional.of(lockKey));
        Map<String, String> texts = Map.of("shop.t.trophy", "Слиток игнития", lockKey, "Победи Игниса", "shop.t.trophies", "Трофеи боссов");

        EditorFiles.saveBookEdits(folder, "t", Optional.empty(), List.of(edited), List.of(), List.of(), "ru_ru", texts, JsonOps.INSTANCE, true);

        Path file = folder.resolve("data/t/rysquests/shop/shop/trophy.json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertTrue(json.get("delivery").getAsBoolean());
        assertEquals(lockKey, json.get("lock_text").getAsString());
        assertEquals("epic", json.getAsJsonArray("gives").get(0).getAsJsonObject().getAsJsonObject("components").get("minecraft:rarity").getAsString());
        ShopOffer back = ShopOffer.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(edited, back, "nothing is lost on the way to the file and back");
        String lang = Files.readString(folder.resolve("assets/t/lang/ru_ru.json"));
        assertTrue(lang.contains("Победи Игниса") && lang.contains("Слиток игнития") && lang.contains("Трофеи боссов"), "the strings land in the lang file");
    }

    @Test
    void theStringsOfATradeAreTheKeysTheSaveAccepts() {
        ShopOffer trophy = read(TROPHY);
        assertEquals(List.of("shop.t.trophy", "shop.t.trophies", "shop.t.trophy.lock", "shop.t.epoch_8"), trophy.textKeys());
        // a string that is no key of the trade is still refused
        Map<String, String> stray = Map.of("shop.t.stray", "x");
        org.junit.jupiter.api.Assertions.assertThrows(EditorFiles.Rejected.class, () -> EditorFiles.saveBookEdits(folder, "t", Optional.empty(), List.of(trophy), List.of(),
                List.of(), "ru_ru", stray, JsonOps.INSTANCE, true));
    }
}
