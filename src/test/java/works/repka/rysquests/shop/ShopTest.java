package works.repka.rysquests.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.ShopOffer.Entry;
import works.repka.rysquests.data.ShopOffer.Kind;
import works.repka.rysquests.data.ShopOffer.Limit;
import works.repka.rysquests.data.ShopOffer.Per;
import works.repka.rysquests.data.ShopOffer.Reset;

class ShopTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    private static Entry coins(int amount) {
        return new Entry(Kind.CURRENCY, "coin", amount);
    }

    private static Entry item(String id, int count) {
        return new Entry(Kind.ITEM, id, count);
    }

    private static ShopOffer offer(String id, Entry cost, Entry gives) {
        return new ShopOffer(ResourceLocation.parse(id), Optional.empty(), Optional.empty(), Optional.empty(), List.of(cost), List.of(gives), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void offerReadsCountForItemsAndAmountForMoney() {
        ShopOffer read = ShopOffer.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"t:a","cost":[{"type":"currency","id":"coin","amount":40}],"gives":[{"type":"item","id":"minecraft:iron_ingot","count":32}],
                 "limit":{"per":"team","count":3,"reset":"weekly"}}""")).getOrThrow();

        assertEquals(40, read.cost().getFirst().amount());
        assertEquals(32, read.gives().getFirst().amount());
        assertEquals(new Limit(Per.TEAM, 3, Reset.WEEKLY), read.limit().orElseThrow());
        assertEquals(read, ShopOffer.CODEC.parse(JsonOps.INSTANCE, ShopOffer.CODEC.encodeStart(JsonOps.INSTANCE, read).getOrThrow()).getOrThrow());
    }

    @Test
    void aTradeThatGivesOnlyMoneyIsASale() {
        assertTrue(offer("t:a", item("minecraft:wheat", 16), coins(4)).isSale());
        assertTrue(!offer("t:b", coins(4), item("minecraft:wheat", 16)).isSale());
    }

    @Test
    void aDailyLimitEndsAtMidnight() {
        Limit limit = new Limit(Per.PLAYER, 3, Reset.DAILY);
        ShopLimits.Usage usage = ShopLimits.after(limit, null, 2, MONDAY);

        assertEquals(2, ShopLimits.used(limit, usage, MONDAY));
        assertEquals(0, ShopLimits.used(limit, usage, MONDAY.plusDays(1)));
        assertEquals(3, ShopLimits.used(limit, ShopLimits.after(limit, usage, 1, MONDAY), MONDAY));
    }

    @Test
    void aWeeklyLimitEndsOnMonday() {
        Limit limit = new Limit(Per.PLAYER, 3, Reset.WEEKLY);
        ShopLimits.Usage usage = ShopLimits.after(limit, null, 1, MONDAY.plusDays(2));

        assertEquals(1, ShopLimits.used(limit, usage, MONDAY.plusDays(6)));
        assertEquals(0, ShopLimits.used(limit, usage, MONDAY.plusDays(7)));
    }

    @Test
    void aLimitThatNeverResetsStaysUsed() {
        Limit limit = new Limit(Per.TEAM, 1, Reset.NEVER);
        ShopLimits.Usage usage = ShopLimits.after(limit, null, 1, MONDAY);

        assertEquals(1, ShopLimits.used(limit, usage, MONDAY.plusYears(5)));
    }

    @Test
    void buyingAndSellingAtALossIsNoCycle() {
        assertTrue(ShopCycles.find(List.of(
                offer("t:buy", coins(10), item("minecraft:diamond", 1)),
                offer("t:sell", item("minecraft:diamond", 1), coins(8)))).isEmpty());
    }

    @Test
    void sellingForMoreThanTheBuyingPriceIsFound() {
        List<List<ResourceLocation>> cycles = ShopCycles.find(List.of(
                offer("t:buy", coins(10), item("minecraft:diamond", 1)),
                offer("t:sell", item("minecraft:diamond", 1), coins(12))));

        assertEquals(1, cycles.size());
        assertEquals(2, cycles.getFirst().size());
    }

    @Test
    void aLongChainIsFoundToo() {
        assertEquals(1, ShopCycles.find(List.of(
                offer("t:a", coins(10), item("minecraft:iron_ingot", 2)),
                offer("t:b", item("minecraft:iron_ingot", 2), item("minecraft:gold_ingot", 1)),
                offer("t:c", item("minecraft:gold_ingot", 1), coins(11)),
                offer("t:other", coins(1), item("minecraft:dirt", 1)))).size());
    }

    @Test
    void aFairRoundTripIsNoCycle() {
        assertTrue(ShopCycles.find(List.of(
                offer("t:buy", coins(10), item("minecraft:diamond", 2)),
                offer("t:sell", item("minecraft:diamond", 1), coins(5)))).isEmpty());
    }
}
