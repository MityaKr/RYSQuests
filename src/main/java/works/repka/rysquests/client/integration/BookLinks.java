package works.repka.rysquests.client.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.block.ModBlocks;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.client.screen.LinkCards;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ShopOffer;
import works.repka.ryscore.links.ItemLinks;

/**
 * The book in the recipe viewer's card of an item (RYSCore 1.10 {@code links.ItemLinks}, plan _audit/ryscope-quests, step 2): the
 * shop's offers as a category "Магазин" (an offer that gives the item under "Источники", one that takes it, a sale or a delivery,
 * under "Использования"), the quests that give it as a category "Награды квестов" under "Источники", and the chip "2 квеста" with the
 * quests that ask for it or give it and the shop's offers.
 *
 * <p>Everything comes from the client's copies: the book that came whole, the progress that comes in diffs and the shop's state. No
 * packet of its own and nothing per frame: the index is built once per book in the background ({@link ItemLinkIndex}), a question is
 * a look into it, and {@link #version()} grows with a new book, a diff of progress and a new state of the shop, so an open card asks
 * again only then. What the player may see is the book's rule ({@link ItemLinkIndex#visible}).
 */
public final class BookLinks implements ItemLinks.Provider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookLinks.class);
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "book");
    public static final ResourceLocation SHOP = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "shop");
    public static final ResourceLocation REWARDS = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "rewards");

    private static volatile ItemLinkIndex index = ItemLinkIndex.EMPTY;
    @Nullable
    private static QuestBook builtFor;
    /** Grows when an index is ready: an open card that got nothing while it was being built asks again. */
    private static int ready;
    @Nullable
    private static ItemLinks.Group shopGroup;
    @Nullable
    private static ItemLinks.Group rewardsGroup;

    private BookLinks() {
    }

    /** At the start of the client: the viewer finds the book among the family's providers. */
    public static void register() {
        ItemLinks.register(new BookLinks());
    }

    /** Starts the index of the current book in the background unless it is built or on its way. Main thread. */
    public static void prepare() {
        QuestBook book = ClientBook.current();
        if (book == builtFor) {
            return;
        }
        builtFor = book;
        index = ItemLinkIndex.EMPTY;
        CompletableFuture.supplyAsync(() -> ItemLinkIndex.build(book), Util.backgroundExecutor())
                .whenCompleteAsync((built, failure) -> {
                    if (failure != null) {
                        LOGGER.warn("Could not index the book for the recipe viewer", failure);
                    } else if (book == builtFor) {
                        index = built;
                        ready++;
                        LOGGER.debug("Indexed the book for the recipe viewer: {} offers, {} quests", book.offers().size(), book.quests().size());
                    }
                }, Minecraft.getInstance());
    }

    /** The index of the book the client holds now, or null while it is being built. */
    @Nullable
    public static ItemLinkIndex current() {
        prepare();
        ItemLinkIndex now = index;
        return now.book() == ClientBook.current() && now != ItemLinkIndex.EMPTY ? now : null;
    }

    /** "Магазин", with the shop block for its icon. Made once: the viewer keeps its category while the group stays equal. */
    private static ItemLinks.Group shopGroup() {
        if (shopGroup == null) {
            shopGroup = new ItemLinks.Group(SHOP, Component.translatable("rysquests.links.group.shop"), new ItemStack(ModBlocks.RECEIVER_ITEM.get()), 0);
        }
        return shopGroup;
    }

    private static ItemLinks.Group rewardsGroup() {
        if (rewardsGroup == null) {
            rewardsGroup = new ItemLinks.Group(REWARDS, Component.translatable("rysquests.links.group.rewards"), new ItemStack(Items.WRITABLE_BOOK), 1);
        }
        return rewardsGroup;
    }

    private static boolean visible(ItemLinkIndex index, Quest quest) {
        return index.visible(quest, ClientProgress::get, ClientProgress::isStageLocked);
    }

    private static List<Quest> visible(ItemLinkIndex index, List<Quest> quests) {
        List<Quest> out = new ArrayList<>(quests.size());
        for (Quest quest : quests) {
            if (visible(index, quest)) {
                out.add(quest);
            }
        }
        return out;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int version() {
        prepare();
        int hash = ClientBook.version();
        hash = hash * 31 + ClientProgress.statusVersion();
        hash = hash * 31 + ClientShop.version();
        return hash * 31 + ready;
    }

    @Override
    public List<ItemLinks.Card> cards(ItemStack stack, ItemLinks.Role role) {
        ItemLinkIndex index = current();
        if (index == null || stack.isEmpty()) {
            return List.of();
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        List<ItemLinks.Card> out = new ArrayList<>();
        List<ShopOffer> offers = role == ItemLinks.Role.GIVES ? index.offersGiving(item) : index.offersTaking(item);
        for (ShopOffer offer : offers) {
            out.add(LinkCards.offer(shopGroup(), offer, quest -> visible(index, quest)));
        }
        if (role == ItemLinks.Role.GIVES) {
            for (Quest quest : visible(index, index.rewarding(item))) {
                out.add(LinkCards.reward(rewardsGroup(), quest, other -> visible(index, other)));
            }
        }
        return out;
    }

    @Override
    @Nullable
    public ItemLinks.Mentions mentions(ItemStack stack) {
        ItemLinkIndex index = current();
        if (index == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        List<Quest> asking = visible(index, index.asking(item, tag -> stack.is(TagKey.create(Registries.ITEM, tag))));
        List<Quest> rewarding = visible(index, index.rewarding(item));
        List<ShopOffer> offers = new ArrayList<>(index.offersGiving(item));
        index.offersTaking(item).stream().filter(offer -> !offers.contains(offer)).forEach(offers::add);
        return LinkCards.mentions(asking, rewarding, offers);
    }

    @Override
    @Nullable
    public Component count(ItemLinks.Group group, int count) {
        if (group.id().equals(SHOP)) {
            return LinkCards.count(true, count);
        }
        return group.id().equals(REWARDS) ? LinkCards.count(false, count) : null;
    }
}
