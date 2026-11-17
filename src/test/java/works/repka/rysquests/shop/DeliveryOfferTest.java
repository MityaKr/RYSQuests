package works.repka.rysquests.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.BookParser;
import works.repka.rysquests.data.BookReport;
import works.repka.rysquests.data.BookValidator;
import works.repka.rysquests.data.ShopOffer;

/** A trade paid in parts (shop plan, step M4): its file, what it asks for, how far it has come and how much a delivery takes. */
class DeliveryOfferTest {
    private static final String IRON = "minecraft:iron_ingot";
    private static final String GOLD = "minecraft:gold_ingot";
    private static final String TROPHY = """
            {"id":"t:trophy","category":"trophies","delivery":true,
             "cost":[{"type":"item","id":"minecraft:iron_ingot","count":100},{"type":"item","id":"minecraft:gold_ingot","count":10},
                     {"type":"currency","id":"coin","amount":500}],
             "gives":[{"type":"item","id":"minecraft:nether_star","count":1}]}""";

    private static ShopOffer read(String json) {
        return ShopOffer.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void theFieldIsReadAndWrittenBack() {
        ShopOffer trophy = read(TROPHY);

        assertTrue(trophy.delivery());
        assertEquals(trophy, read(ShopOffer.CODEC.encodeStart(JsonOps.INSTANCE, trophy).getOrThrow().toString()));
        assertEquals(List.of(IRON, GOLD), List.copyOf(trophy.deliveryRows().keySet()));
        assertEquals(100L, trophy.deliveryRows().get(IRON));
    }

    @Test
    void anOrdinaryTradeIsNoDelivery() {
        ShopOffer plain = read("""
                {"id":"t:plain","cost":[{"type":"item","id":"minecraft:iron_ingot","count":4}],"gives":[{"type":"currency","id":"coin","amount":1}]}""");

        assertFalse(plain.delivery());
        assertTrue(plain.deliveryRows().isEmpty());
        assertFalse(ShopOffer.CODEC.encodeStart(JsonOps.INSTANCE, plain).getOrThrow().getAsJsonObject().has("delivery"));
    }

    @Test
    void rowsOfOneItemAddUp() {
        ShopOffer twice = read("""
                {"id":"t:twice","delivery":true,"cost":[{"type":"item","id":"minecraft:iron_ingot","count":10},{"type":"item","id":"minecraft:iron_ingot","count":5}],
                 "gives":[{"type":"item","id":"minecraft:nether_star"}]}""");

        assertEquals(Map.of(IRON, 15L), twice.deliveryRows());
    }

    @Test
    void progressIsTheMeanOfTheRows() {
        ShopOffer trophy = read(TROPHY);

        assertEquals(0f, trophy.deliveryProgress(Map.of()));
        // A full row of two is a half, whatever its numbers; a row is never counted beyond what it needs.
        assertEquals(0.5f, trophy.deliveryProgress(Map.of(IRON, 500L)), 1e-6);
        assertEquals(0.75f, trophy.deliveryProgress(Map.of(IRON, 100L, GOLD, 5L)), 1e-6);
        assertFalse(trophy.deliveryComplete(Map.of(IRON, 100L, GOLD, 9L)));
        assertTrue(trophy.deliveryComplete(Map.of(IRON, 100L, GOLD, 10L)));
    }

    @Test
    void aDeliveryTakesOnlyWhatARowLacks() {
        ShopOffer trophy = read(TROPHY);

        // 30 iron are in, 70 lack: of 200 held only 70 go; gold is full and stays in the inventory.
        assertEquals(Map.of(IRON, 70L), trophy.toDeliver(Map.of(IRON, 30L, GOLD, 10L), Map.of(IRON, 200L, GOLD, 64L)));
        assertEquals(Map.of(IRON, 12L, GOLD, 3L), trophy.toDeliver(Map.of(), Map.of(IRON, 12L, GOLD, 3L)));
        assertTrue(trophy.toDeliver(Map.of(IRON, 100L, GOLD, 10L), Map.of(IRON, 64L)).isEmpty());
        assertTrue(trophy.toDeliver(Map.of(), Map.of("minecraft:stone", 64L)).isEmpty());
    }

    @Test
    void aDeliveryWithoutItemsOrGivingOnlyCoinsIsLeftOut() {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("t:chapters/c/_chapter"), JsonParser.parseString("{\"id\":\"t:c\",\"title\":\"C\"}"));
        files.put(ResourceLocation.parse("t:shop/trophy"), JsonParser.parseString(TROPHY));
        files.put(ResourceLocation.parse("t:shop/coins_only"), JsonParser.parseString("""
                {"id":"t:coins_only","delivery":true,"cost":[{"type":"currency","id":"coin","amount":5}],"gives":[{"type":"item","id":"minecraft:nether_star"}]}"""));
        files.put(ResourceLocation.parse("t:shop/sale"), JsonParser.parseString("""
                {"id":"t:sale","delivery":true,"cost":[{"type":"item","id":"minecraft:iron_ingot","count":64}],"gives":[{"type":"currency","id":"coin","amount":5}]}"""));

        BookValidator.Result result = BookValidator.validate(BookParser.parse(files, JsonOps.INSTANCE), BuiltInRegistries.ITEM::containsKey, JsonOps.INSTANCE);

        assertTrue(result.book().offers().containsKey(ResourceLocation.parse("t:trophy")));
        assertFalse(result.book().offers().containsKey(ResourceLocation.parse("t:coins_only")));
        assertFalse(result.book().offers().containsKey(ResourceLocation.parse("t:sale")));
        List<String> codes = result.report().issues().stream().map(BookReport.Issue::code).toList();
        assertTrue(codes.contains("offer_delivery_items"));
        assertTrue(codes.contains("offer_delivery_sale"));
    }
}
