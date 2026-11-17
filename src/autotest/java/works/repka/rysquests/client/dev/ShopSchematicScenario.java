package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.ryscore.ui.UiScale;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.network.ShopTradePayload;

/**
 * The shop gives a Create schematic with its file, once per team (shop plan, steps M1 and M2; RYSQuests 0.2.6). A world datapack of
 * the test world adds one offer to the RepYS book; the player buys it, gets the schematic as it is, and cannot buy it again.
 * {@code gradlew runRepysAutotest -Pautotest=shop_schematic --offline}, on the copy of RepYS with Create.
 */
final class ShopSchematicScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopSchematicScenario.class);
    private static final ResourceLocation OFFER = ResourceLocation.parse("repys:autotest_schematic");
    private static final ResourceLocation SCHEMATIC = ResourceLocation.parse("create:schematic");
    private static final ResourceLocation FILE = ResourceLocation.parse("create:schematic_file");
    private static final String FILE_NAME = "repys_bio_incubator.nbt";
    private static final String OFFER_JSON = """
            {
              "id": "repys:autotest_schematic",
              "category": "Autotest",
              "cost": [ { "type": "currency", "id": "coin", "amount": 2 } ],
              "gives": [ { "type": "item", "id": "create:schematic", "count": 1,
                           "components": { "create:schematic_bounds": [11, 9, 11], "create:schematic_deployed": false,
                                           "create:schematic_file": "repys_bio_incubator.nbt", "create:schematic_owner": "RepYS" } } ],
              "limit": { "per": "team", "count": 1 }
            }
            """;
    private static long coinsBefore;

    private ShopSchematicScenario() {
    }

    static void check() {
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        step("a datapack with a schematic offer, then reload", () -> {
            writePack();
            command("reload");
        }, 40);
        expect("the offer reached the client", () -> ClientBook.current().offers().containsKey(OFFER));
        step("a player, the window of the mockups, coins, no schematics", () -> {
            BookRole.forceForTest(false);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            command("rysquests wallet add @s coin 10");
            command("clear @s create:schematic");
            command("rysquests shop reset @s");
        }, 40);
        expect("the coins arrived", () -> ClientShop.balance("coin") >= 10);
        // Commands go through the chat chain and can be overtaken by a trade: wait until the reused world is clean.
        expect("no schematic held, the limit free", () -> heldSchematics() == 0 && ClientShop.used(OFFER) == 0);
        step("open the shop on the offer", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("shop");
            BookScreen.shopSelectForTest(OFFER);
        }, 30);
        screenshot("shop_schem_offer");
        step("hover the schematic in the detail", ShopSchematicScenario::hoverCardItem, 8);
        screenshot("shop_schem_tooltip");
        expect("the hovered item is the schematic with its file", () -> fileOf(BookScreen.hoveredStack()).equals(FILE_NAME));
        step("buy it", () -> {
            coinsBefore = ClientShop.balance("coin");
            BookScreen.clearShopResultForTest();
            PacketDistributor.sendToServer(new ShopTradePayload(OFFER, 1, false));
        }, 30);
        expect("the schematic with its file is in the inventory", () -> heldSchematics() == 1);
        expect("the limit is used up", () -> ClientShop.used(OFFER) == 1);
        step("let the card redraw", () -> moveMouse(5, 5), 10);
        screenshot("shop_schem_bought");
        step("try again", () -> {
            BookScreen.clearShopResultForTest();
            PacketDistributor.sendToServer(new ShopTradePayload(OFFER, 1, false));
        }, 30);
        expect("the server refuses the second copy", () -> "rysquests.shop.error.bought".equals(BookScreen.lastShopResultForTest()));
        expect("still one schematic and one payment", () -> heldSchematics() == 1 && ClientShop.balance("coin") == coinsBefore - 2);
        step("close", () -> mc().setScreen(null), 5);
    }

    /** Only ever writes into the test world: a datapack of its own, which /reload picks up. */
    private static void writePack() {
        Path packs = mc().getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("rysquests_autotest_shop");
        try {
            Files.createDirectories(packs.resolve("data/repys/rysquests/shop"));
            Files.writeString(packs.resolve("pack.mcmeta"), "{ \"pack\": { \"pack_format\": 48, \"description\": \"RYSQuests autotest shop\" } }");
            Files.writeString(packs.resolve("data/repys/rysquests/shop/autotest_schematic.json"), OFFER_JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fileOf(ItemStack stack) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(FILE);
        if (stack.isEmpty() || type == null || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(SCHEMATIC)) {
            return "";
        }
        Object value = stack.get(type);
        return value == null ? "" : value.toString();
    }

    private static int heldSchematics() {
        int count = 0;
        for (ItemStack stack : mc().player.getInventory().items) {
            if (fileOf(stack).equals(FILE_NAME)) {
                count += stack.getCount();
            }
        }
        LOGGER.info("[autotest] shop_schematic: {} schematics with the file", count);
        return count;
    }

    /** Moves the mouse onto the goods in the detail panel: the rightmost item with a click of its own. */
    private static void hoverCardItem() {
        List<float[]> spots = BookScreen.itemSpotsForTest();
        float[] best = null;
        for (float[] spot : spots) {
            if (spot[4] == 0 && (best == null || spot[0] > best[0])) {
                best = spot;
            }
        }
        if (best == null) {
            LOGGER.info("[autotest] shop_schematic: no item spot among {}", spots.size());
            return;
        }
        moveMouse(UiScale.toGui((best[0] + best[2] / 2f) / 2f), UiScale.toGui((best[1] + best[3] / 2f) / 2f));
    }
}
