package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.EditorTesting;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.editor.OfferEdits;

/**
 * The editor's shop since 0.2.7 (shop plan, step M9; ADR-58) on the real RepYS book, as an operator: a new offer becomes a delivery
 * of the held iron, its count is typed in thousands, it opens after the first win over a boss of the book's slayer achievements,
 * and the line of its closed card is typed. A delivery refuses to become a sale, switched off it keeps its items. What the draft
 * holds is written as the save writes it and read back with the game's registries: nothing is lost. Nothing is saved to the pack:
 * every edit is undone at the end, so the copy's editor folder stays as it was.
 * {@code gradlew runRepysAutotest -Pautotest=shop_editor --offline}.
 */
final class ShopEditorScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopEditorScenario.class);
    private static final ResourceLocation IRON = ResourceLocation.parse("minecraft:iron_ingot");
    private static final String LOCK = "Победи босса, и поставка откроется";
    /** Edits of the draft before the scenario's own, read when it runs. */
    private static volatile int before;

    private ShopEditorScenario() {
    }

    private static ShopOffer offer() {
        return EditorTesting.chosenOffer();
    }

    static void check() {
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        expect("the book has a boss to wait for", () -> !OfferEdits.bosses(ClientBook.current()).isEmpty());
        step("an operator with 64 iron in hand, the editor's shop open", () -> {
            command("item replace entity @s weapon.mainhand with minecraft:iron_ingot 64");
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            BookRole.forceForTest(null);
            mc().setScreen(new BookScreen());
            EditorTesting.open("shop");
        }, 30);
        expect("the editor is on", EditorTesting::on);
        step("a new offer of the held iron", () -> {
            before = EditorTesting.unsaved();
            EditorTesting.newOffer();
        }, 10);
        expect("the offer is chosen, a purchase for coins", () -> offer() != null && !offer().isSale() && !offer().delivery());

        // a delivery takes the held item as its first row, plain
        step("switch the delivery on", () -> EditorTesting.offerDelivery(true), 5);
        expect("a delivery of 64 iron, the coins kept for the goods", () -> offer().delivery()
                && OfferEdits.payItems(offer()).equals(List.of(new ShopOffer.Entry(ShopOffer.Kind.ITEM, IRON.toString(), 64))) && OfferEdits.price(offer()) == 10);
        step("type 4096 into the row's count", () -> EditorTesting.offerPayCount(0, "4096"), 5);
        step("type a count that is no number", () -> EditorTesting.offerPayCount(0, "4k"), 5);
        expect("4096 iron to deliver, the wrong count changed nothing", () -> OfferEdits.payItems(offer()).getFirst().amount() == 4096);
        step("open it after a boss", EditorTesting::offerOpensAfterBoss, 5);
        expect("it waits for the first tier of a slayer achievement", () -> offer().quest().filter(Achievement::isTierQuest).isPresent()
                && offer().bossToDefeat(ClientBook.current().quests()).isPresent());
        step("type the line of the closed card", () -> EditorTesting.offerLock(LOCK), 5);
        expect("the line is a key of the offer, its text in the draft", () -> offer().lockText().map(EditorTesting::text).filter(LOCK::equals).isPresent()
                && offer().textKeys().contains(offer().lockText().get()));
        expect("nothing the book would leave out", () -> OfferEdits.problems(offer()).isEmpty());
        step("look at the panel", () -> moveMouse(5, 5), 10);
        screenshot("shop_editor_delivery");

        // what the save writes reads back the same with the game's registries
        expect("the offer survives the file and back", () -> {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc().level.registryAccess());
            JsonElement json = ShopOffer.CODEC.encodeStart(ops, offer()).getOrThrow();
            ShopOffer back = ShopOffer.CODEC.parse(ops, json).getOrThrow();
            LOGGER.info("[autotest] shop_editor: {}", json);
            return back.equals(offer()) && json.getAsJsonObject().get("delivery").getAsBoolean() && json.getAsJsonObject().has("lock_text");
        });

        // a delivery stays a purchase; switched off, its items stay and are paid at once
        step("try to make it a sale", () -> EditorTesting.offerFlip(true), 5);
        expect("still a purchase and a delivery", () -> !offer().isSale() && offer().delivery());
        step("switch the delivery off", () -> EditorTesting.offerDelivery(false), 5);
        expect("a barter: the iron is paid at once with the coins", () -> !offer().delivery() && OfferEdits.payItems(offer()).getFirst().amount() == 4096
                && offer().lockText().isPresent());
        screenshot("shop_editor_barter");

        step("undo every edit of the scenario", () -> {
            for (int i = 0; i < 40 && EditorTesting.unsaved() > before; i++) {
                EditorTesting.undo();
            }
            LOGGER.info("[autotest] shop_editor: {} edits before the offer, {} left after undo", before, EditorTesting.unsaved());
        }, 10);
        expect("the draft is as before the scenario, nothing reaches the pack", () -> EditorTesting.unsaved() == before && offer() == null);
        step("leave the editor and close the book", () -> {
            EditorTesting.leave();
            mc().setScreen(null);
            command("item replace entity @s weapon.mainhand with minecraft:air");
        }, 10);
    }
}
