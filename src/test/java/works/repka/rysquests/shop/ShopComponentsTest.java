package works.repka.rysquests.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.BookParser;
import works.repka.rysquests.data.BookReport;
import works.repka.rysquests.data.BookValidator;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.ShopOffer.Entry;
import works.repka.rysquests.data.ShopOffer.Kind;

/** A trade gives an item with components as it is (a Create schematic with its file), and only what the player gets has them. */
class ShopComponentsTest {
    private static final DataComponentPatch EPIC = DataComponentPatch.builder().set(DataComponents.RARITY, Rarity.EPIC).build();

    private static ShopOffer offer(String id, List<Entry> cost, List<Entry> gives) {
        return new ShopOffer(ResourceLocation.parse(id), Optional.empty(), Optional.empty(), Optional.empty(), cost, gives, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Entry coins(int amount) {
        return new Entry(Kind.CURRENCY, "coin", amount);
    }

    private static Entry epicPaper(int count) {
        return new Entry(Kind.ITEM, "minecraft:paper", count, Optional.of(EPIC));
    }

    @Test
    void componentsOfWhatIsGivenAreReadAndWrittenBack() {
        ShopOffer read = ShopOffer.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"t:schem","cost":[{"type":"currency","id":"coin","amount":2}],
                 "gives":[{"type":"item","id":"minecraft:paper","count":1,"components":{"minecraft:rarity":"epic"}}],
                 "limit":{"per":"team","count":1}}""")).getOrThrow();

        Entry gives = read.gives().getFirst();
        assertEquals(Optional.of(EPIC), gives.components());
        assertTrue(read.limit().orElseThrow().onceForTeam());
        assertEquals(read, ShopOffer.CODEC.parse(JsonOps.INSTANCE, ShopOffer.CODEC.encodeStart(JsonOps.INSTANCE, read).getOrThrow()).getOrThrow());
        assertFalse(read.cost().getFirst().hasComponents());
    }

    @Test
    void theStackOfAnEntryCarriesItsComponents() {
        ItemStack stack = epicPaper(1).stack();

        assertTrue(stack.is(Items.PAPER));
        assertEquals(Rarity.EPIC, stack.get(DataComponents.RARITY));
        assertTrue(new Entry(Kind.ITEM, "minecraft:paper", 1).stack().getComponentsPatch().isEmpty());
        assertTrue(coins(3).stack().isEmpty());
    }

    @Test
    void anEmptyComponentsFieldIsNoComponents() {
        ShopOffer read = ShopOffer.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"t:a","cost":[{"type":"currency","id":"coin","amount":2}],
                 "gives":[{"type":"item","id":"minecraft:paper","components":{}}]}""")).getOrThrow();

        assertFalse(read.gives().getFirst().hasComponents());
    }

    @Test
    void componentsInWhatThePlayerPaysLeaveTheTradeOut() {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.parse("t:chapters/c/_chapter"), JsonParser.parseString("{\"id\":\"t:c\",\"title\":\"C\"}"));
        files.put(ResourceLocation.parse("t:shop/good"), JsonParser.parseString("""
                {"id":"t:good","cost":[{"type":"currency","id":"coin","amount":2}],
                 "gives":[{"type":"item","id":"minecraft:paper","components":{"minecraft:rarity":"epic"}}]}"""));
        files.put(ResourceLocation.parse("t:shop/bad"), JsonParser.parseString("""
                {"id":"t:bad","cost":[{"type":"item","id":"minecraft:paper","components":{"minecraft:rarity":"epic"}}],
                 "gives":[{"type":"currency","id":"coin","amount":2}]}"""));

        BookValidator.Result result = BookValidator.validate(BookParser.parse(files, JsonOps.INSTANCE), BuiltInRegistries.ITEM::containsKey, JsonOps.INSTANCE);

        assertTrue(result.book().offers().containsKey(ResourceLocation.parse("t:good")));
        assertFalse(result.book().offers().containsKey(ResourceLocation.parse("t:bad")));
        assertTrue(result.report().issues().stream().map(BookReport.Issue::code).anyMatch("offer_cost_components"::equals));
    }

    /** 35 slots of one plain paper each and one empty slot. */
    private static List<ItemStack> inventoryOfPlainPaper() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            items.add(new ItemStack(Items.PAPER));
        }
        items.add(ItemStack.EMPTY);
        return items;
    }

    @Test
    void anItemWithComponentsDoesNotFitOnTopOfPlainOnes() {
        List<ItemStack> items = inventoryOfPlainPaper();

        // 128 plain paper go onto the 35 stacks already held; the same amount with components needs two free slots.
        assertTrue(ShopService.hasRoom(items, offer("t:plain", List.of(coins(1)), List.of(new Entry(Kind.ITEM, "minecraft:paper", 128))), 1));
        assertTrue(ShopService.hasRoom(items, offer("t:one", List.of(coins(1)), List.of(epicPaper(64))), 1));
        assertFalse(ShopService.hasRoom(items, offer("t:two", List.of(coins(1)), List.of(epicPaper(128))), 1));
    }

    @Test
    void anItemWithComponentsFitsOnTopOfTheSameOne() {
        List<ItemStack> items = inventoryOfPlainPaper();
        ItemStack epic = new ItemStack(Items.PAPER, 1);
        epic.applyComponents(EPIC);
        items.set(0, epic);
        items.set(35, new ItemStack(Items.STONE));

        assertTrue(ShopService.hasRoom(items, offer("t:on_top", List.of(coins(1)), List.of(epicPaper(63))), 1));
        assertFalse(ShopService.hasRoom(items, offer("t:more", List.of(coins(1)), List.of(epicPaper(64))), 1));
    }

    @Test
    void buyingAnItemWithComponentsAndSellingThePlainOneIsNoCycle() {
        // The bought item cannot pay: a sale takes only plain items, so the two trades do not chain.
        assertTrue(ShopCycles.find(List.of(
                offer("t:buy", List.of(coins(1)), List.of(epicPaper(1))),
                offer("t:sell", List.of(new Entry(Kind.ITEM, "minecraft:paper", 1)), List.of(coins(5))))).isEmpty());
    }
}
