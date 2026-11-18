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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.client.integration.BookLinks;
import works.repka.rysquests.client.integration.ItemLinkIndex;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.RepysHooks;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.network.ShopDeliverPayload;
import works.repka.rysquests.reward.ItemReward;
import works.repka.ryscore.links.ItemLinks;

/**
 * The book in the recipe viewer's card of an item (RYSCore 1.10 links, plan _audit/ryscope-quests, step 2), on the copy of RepYS with
 * RYScope 0.2.6 or later: the Gate Fragment ("Осколок Врат") has the shop's category with its closed trophy delivery, the lock in the
 * book's words and the quest that opens it; the chip counts the quests that ask for it; a quest reward is a source of its item; the
 * chip's row and the card's button open the book on the quest and on the offer; once the quest is done the card opens and shows what
 * the team has delivered. {@code gradlew runRepysAutotest -Pautotest=links --offline}.
 */
final class ItemLinksScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemLinksScenario.class);
    private static final ResourceLocation OFFER = ResourceLocation.parse("repys:trophy_gate_fragment");
    private static final ResourceLocation FIRST = ResourceLocation.parse("repys:b26_e9_creation/first_gate_fragment");
    private static final ResourceLocation ETERNITY = ResourceLocation.parse("repys:b26_e9_creation/fragments_of_eternity");
    private static final ResourceLocation FRAGMENT = ResourceLocation.parse("kubejs:gate_fragment");
    private static final ResourceLocation DRACONIUM = ResourceLocation.parse("draconicevolution:draconium_ingot");
    /** The trophy as the pack has it since 18.11, for a copy whose kubejs is older. Written only into the test world. */
    private static final String OFFER_JSON = """
            {
              "id": "repys:trophy_gate_fragment",
              "icon": "kubejs:gate_fragment",
              "category": "shop.repys.trophies",
              "delivery": true,
              "cost": [
                { "type": "item", "id": "draconicevolution:draconium_ingot", "count": 1984 },
                { "type": "item", "id": "kubejs:plasma_cell", "count": 192 },
                { "type": "item", "id": "mekanism:pellet_antimatter", "count": 14 },
                { "type": "currency", "id": "coin", "amount": 1000 }
              ],
              "gives": [ { "type": "item", "id": "kubejs:gate_fragment", "count": 1 } ],
              "quest": "repys:b26_e9_creation/first_gate_fragment",
              "lock_text": "shop.repys.trophy_gate_fragment.lock"
            }
            """;
    private static ItemStack rewardItem = ItemStack.EMPTY;
    private static int versionBefore;

    private ItemLinksScenario() {
    }

    static void check() {
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        expect("the first Gate Fragment quest is in the book", () -> ClientBook.current().quests().containsKey(FIRST));
        step("the trophy of the Gate Fragment, from a datapack of the test world if the copy's kubejs lacks it", () -> {
            if (!ClientBook.current().offers().containsKey(OFFER)) {
                writePack();
                command("reload");
            }
        }, 40);
        expect("the trophy reached the client", () -> ClientBook.current().offers().containsKey(OFFER));
        step("a player of no team, the quest not done, nothing delivered", () -> {
            BookRole.forceForTest(false);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            command("rysquests progress @s reset " + FIRST);
            command("rysquests shop delivery reset @s " + OFFER);
        }, 40);
        expect("the book is registered with the family's item links", () -> provider() != null);
        expect("the index of the book is ready", () -> BookLinks.current() != null);
        expect("the trophy is closed", () -> !ClientProgress.get(FIRST).everDone());

        expect("Источники of the fragment: the shop's closed delivery with the book's lock and the quest under it", () -> {
            ItemLinks.Card card = card(stack(FRAGMENT), ItemLinks.Role.GIVES, BookLinks.SHOP, OFFER);
            if (card == null) {
                return false;
            }
            LOGGER.info("[autotest] links: card '{}' / '{}', state {}, lock '{}', link '{}', takes {}, open '{}'", card.title().getString(),
                    text(card.subtitle()), card.state(), text(card.lock()), card.lockLink() == null ? "" : card.lockLink().label().getString(),
                    card.takes().size(), card.open() == null ? "" : card.open().label().getString());
            return card.state() == ItemLinks.State.LOCKED && card.lock() != null && card.lockLink() != null && card.open() != null
                    && card.takes().size() == 4 && card.takes().stream().allMatch(part -> part.have() < 0);
        });
        expect("the shop counts its offers in its own words", () -> {
            ItemLinks.Provider provider = provider();
            ItemLinks.Card card = card(stack(FRAGMENT), ItemLinks.Role.GIVES, BookLinks.SHOP, OFFER);
            return provider != null && card != null && provider.count(card.group(), 1) != null;
        });
        expect("Использования of draconium hold the delivery that takes it", () -> card(stack(DRACONIUM), ItemLinks.Role.TAKES, BookLinks.SHOP, OFFER) != null);
        expect("the chip: the quests that ask for the fragment, the shop after them", () -> {
            ItemLinks.Mentions mentions = mentions(stack(FRAGMENT));
            if (mentions == null) {
                return false;
            }
            LOGGER.info("[autotest] links: chip '{}', title '{}', rows {}", mentions.label().getString(), mentions.title().getString(),
                    mentions.items().stream().map(row -> row.section().getString() + ": " + row.title().getString() + " (" + text(row.detail()) + ", " + row.state() + ")").toList());
            return mentions.items().stream().anyMatch(row -> row.title().getString().equals(questTitle(FIRST)))
                    && mentions.items().stream().anyMatch(row -> row.title().getString().equals(questTitle(ETERNITY)))
                    && mentions.items().stream().anyMatch(row -> row.state() != null && row.open() != null);
        });
        expect("the list holds exactly the quests the book shows: hidden ones, drafts and closed stages neither shown nor counted", () -> {
            ItemLinks.Mentions mentions = mentions(stack(FRAGMENT));
            ItemLinkIndex index = BookLinks.current();
            if (mentions == null || index == null) {
                return false;
            }
            String shop = net.minecraft.network.chat.Component.translatable("rysquests.links.section.shop").getString();
            long rows = mentions.items().stream().filter(row -> !row.section().getString().equals(shop)).count();
            long listed = index.asking(FRAGMENT, tag -> false).size() + index.rewarding(FRAGMENT).size();
            long shown = index.asking(FRAGMENT, tag -> false).stream().filter(ItemLinksScenario::shown).count()
                    + index.rewarding(FRAGMENT).stream().filter(ItemLinksScenario::shown).count();
            LOGGER.info("[autotest] links: {} quest row(s), {} listed in the book, {} shown now", rows, listed, shown);
            return rows == shown;
        });
        step("find an item that a quest of the map gives as a reward", () -> rewardItem = findRewardItem(), 5);
        expect("Источники of a reward: the category Награды квестов", () -> {
            if (rewardItem.isEmpty()) {
                return false;
            }
            List<ItemLinks.Card> cards = cards(rewardItem, ItemLinks.Role.GIVES).stream().filter(card -> card.group().id().equals(BookLinks.REWARDS)).toList();
            LOGGER.info("[autotest] links: reward item {}, {} card(s), first '{}' / '{}', {}", BuiltInRegistries.ITEM.getKey(rewardItem.getItem()), cards.size(),
                    cards.isEmpty() ? "" : cards.getFirst().title().getString(), cards.isEmpty() ? "" : text(cards.getFirst().subtitle()),
                    cards.isEmpty() ? "" : cards.getFirst().state());
            return !cards.isEmpty() && cards.getFirst().open() != null;
        });

        boolean viewer = ModList.get().isLoaded("ryscope");
        step("the recipe viewer on the fragment (RYScope " + version("ryscope") + ")", () -> {
            if (viewer && Recipes.available()) {
                Recipes.showRecipes(stack(FRAGMENT));
            } else {
                LOGGER.info("[autotest] links: no RYScope in this copy, the viewer's pictures are left out");
            }
            moveMouse(5, 5);
        }, 60);
        screenshot("links_gate_locked");
        step("the recipe viewer on the reward", () -> {
            if (viewer && Recipes.available() && !rewardItem.isEmpty()) {
                Recipes.showRecipes(rewardItem);
            }
            moveMouse(5, 5);
        }, 60);
        screenshot("links_reward");

        step("a row of the chip: the quest that asks for the fragment", () -> {
            mc().setScreen(null);
            ItemLinks.Mentions mentions = mentions(stack(FRAGMENT));
            mentions.items().stream().filter(row -> row.title().getString().equals(questTitle(FIRST))).findFirst()
                    .ifPresent(row -> row.open().run().run());
            moveMouse(5, 5);
        }, 40);
        expect("the book opened on that quest", () -> FIRST.equals(RepysHooks.chosenQuest()));
        screenshot("links_chip_to_book");
        step("Открыть в магазине on the card", () -> {
            mc().setScreen(null);
            card(stack(FRAGMENT), ItemLinks.Role.GIVES, BookLinks.SHOP, OFFER).open().run().run();
            moveMouse(5, 5);
        }, 40);
        expect("the book opened in the shop on the trophy", () -> RepysHooks.shopOn(OFFER));
        screenshot("links_open_shop");

        step("the quest done, a part of the draconium delivered", () -> {
            mc().setScreen(null);
            versionBefore = ItemLinks.version();
            command("rysquests progress @s complete " + FIRST);
            command("give @s " + DRACONIUM + " 640");
        }, 40);
        expect("the trophy opened", () -> ClientProgress.get(FIRST).everDone());
        step("deliver", () -> PacketDistributor.sendToServer(new ShopDeliverPayload(OFFER)), 40);
        expect("the team's delivery reached the client and the links' version grew", () ->
                ClientShop.delivered(OFFER).getOrDefault(DRACONIUM.toString(), 0L) > 0 && ItemLinks.version() != versionBefore);
        expect("the card is open now and counts what was delivered", () -> {
            ItemLinks.Card card = card(stack(FRAGMENT), ItemLinks.Role.GIVES, BookLinks.SHOP, OFFER);
            if (card == null) {
                return false;
            }
            LOGGER.info("[autotest] links: open card '{}', state {}, note '{}', rows {}", text(card.subtitle()), card.state(), text(card.note()),
                    card.takes().stream().map(part -> part.have() + "/" + part.amount()).toList());
            return card.state() == ItemLinks.State.ACTIVE && card.lock() == null && card.takes().getFirst().have() > 0;
        });
        step("the recipe viewer on the fragment again", () -> {
            if (viewer && Recipes.available()) {
                Recipes.showRecipes(stack(FRAGMENT));
            }
            moveMouse(5, 5);
        }, 60);
        screenshot("links_gate_open");
        step("put the quest and the delivery back", () -> {
            mc().setScreen(null);
            command("rysquests progress @s reset " + FIRST);
            command("rysquests shop delivery reset @s " + OFFER);
            command("clear @s " + DRACONIUM);
            BookRole.forceForTest(null);
        }, 20);
    }

    @Nullable
    private static ItemLinks.Provider provider() {
        return ItemLinks.providers().stream().filter(provider -> provider.id().equals(BookLinks.ID)).findFirst().orElse(null);
    }

    private static List<ItemLinks.Card> cards(ItemStack stack, ItemLinks.Role role) {
        ItemLinks.Provider provider = provider();
        return provider == null ? List.of() : provider.cards(stack, role);
    }

    @Nullable
    private static ItemLinks.Card card(ItemStack stack, ItemLinks.Role role, ResourceLocation group, ResourceLocation id) {
        return cards(stack, role).stream().filter(card -> card.group().id().equals(group) && card.id().equals(id)).findFirst().orElse(null);
    }

    @Nullable
    private static ItemLinks.Mentions mentions(ItemStack stack) {
        ItemLinks.Provider provider = provider();
        return provider == null ? null : provider.mentions(stack);
    }

    private static ItemStack stack(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static String text(@Nullable net.minecraft.network.chat.Component component) {
        return component == null ? "" : component.getString();
    }

    private static String questTitle(ResourceLocation id) {
        Quest quest = ClientBook.current().quests().get(id);
        return quest == null ? "" : net.minecraft.network.chat.Component.translatable(quest.title()).getString();
    }

    private static boolean shown(Quest quest) {
        ItemLinkIndex index = BookLinks.current();
        return index != null && index.visible(quest, ClientProgress::get, ClientProgress::isStageLocked);
    }

    /** The first item a quest on the map gives as a reward, one the shop does not sell, so that the card is the quest's. */
    private static ItemStack findRewardItem() {
        ItemLinkIndex index = BookLinks.current();
        if (index == null) {
            return ItemStack.EMPTY;
        }
        for (Quest quest : ClientBook.current().quests().values()) {
            if (!index.visible(quest, ClientProgress::get, ClientProgress::isStageLocked)) {
                continue;
            }
            for (RewardDef reward : quest.rewards()) {
                if (reward instanceof ItemReward item && BuiltInRegistries.ITEM.containsKey(item.item()) && index.offersGiving(item.item()).isEmpty()) {
                    LOGGER.info("[autotest] links: {} gives {}", quest.id(), item.item());
                    return stack(item.item());
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static String version(String mod) {
        return ModList.get().getModContainerById(mod).map(container -> container.getModInfo().getVersion().toString()).orElse("none");
    }

    /** Only ever writes into the test world: a datapack of its own, which /reload picks up. */
    private static void writePack() {
        Path packs = mc().getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("rysquests_autotest_links");
        try {
            Files.createDirectories(packs.resolve("data/repys/rysquests/shop"));
            Files.writeString(packs.resolve("pack.mcmeta"), "{ \"pack\": { \"pack_format\": 48, \"description\": \"RYSQuests autotest links\" } }");
            Files.writeString(packs.resolve("data/repys/rysquests/shop/trophy_gate_fragment.json"), OFFER_JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
