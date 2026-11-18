package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.ryscore.links.ItemLinks.Action;
import works.repka.ryscore.links.ItemLinks.Card;
import works.repka.ryscore.links.ItemLinks.Group;
import works.repka.ryscore.links.ItemLinks.Mention;
import works.repka.ryscore.links.ItemLinks.Mentions;
import works.repka.ryscore.links.ItemLinks.Part;
import works.repka.ryscore.links.ItemLinks.State;

/**
 * The book's cards and mentions for the recipe viewer (RYSCore {@code links.ItemLinks}, plan _audit/ryscope-quests, mockups 1 to
 * 3): a shop offer and a quest that gives an item as the viewer draws them, in the shop's and the book's own words. Built on the main
 * thread for the few rows the viewer asks about, from the client's copies of the book, the progress and the shop; nothing here draws.
 * Which offers and quests come in is the index's business (client.integration.ItemLinkIndex): this only words them.
 */
public final class LinkCards {
    private LinkCards() {
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /** A line of the book that starts a sentence on its own: "победи: Игнис" becomes "Победи: Игнис". */
    static String capital(String line) {
        return line.isEmpty() ? line : line.substring(0, line.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT) + line.substring(line.offsetByCodePoints(0, 1));
    }

    // --- states ---

    /** A quest's state for the viewer: done once its tasks are, the reward waiting or not. */
    public static State state(Quest quest) {
        return switch (QuestLogic.status(quest, ClientProgress::get)) {
            case COMPLETED, CLAIMED -> State.DONE;
            case IN_PROGRESS -> State.ACTIVE;
            case AVAILABLE -> State.OPEN;
            case LOCKED -> State.LOCKED;
        };
    }

    /** An offer's state: closed, bought for good by the team, a delivery under way, or open. */
    public static State state(ShopOffer offer) {
        if (ShopView.locked(offer)) {
            return State.LOCKED;
        }
        if (ShopView.boughtByTeam(offer)) {
            return State.DONE;
        }
        return offer.delivery() && !ClientShop.delivered(offer.id()).isEmpty() ? State.ACTIVE : State.OPEN;
    }

    // --- actions ---

    public static Action openQuest(Quest quest) {
        return new Action(tr("rysquests.links.open_book"), () -> BookScreen.openAt(quest.id()));
    }

    public static Action openOffer(ShopOffer offer) {
        return new Action(tr("rysquests.links.open_shop"), () -> BookScreen.openOffer(offer.id()));
    }

    /** "Квест «Первый Осколок Врат»" under a lock: opens the book on the quest. */
    private static Action questLink(Quest quest) {
        return new Action(tr("rysquests.links.quest_link", tr(quest.title())), () -> BookScreen.openAt(quest.id()));
    }

    // --- the shop ---

    /**
     * A shop offer as a card (mockups 1 and 2): what it gives, what it takes (a delivery with what the team has brought already, once
     * open), the lock in the shop's own words with the quest that opens it, and "Открыть в магазине".
     *
     * @param visible whether a quest is shown in the book, for the link under the lock
     */
    public static Card offer(Group group, ShopOffer offer, Predicate<Quest> visible) {
        State state = state(offer);
        boolean locked = state == State.LOCKED;
        Map<String, Long> delivered = ClientShop.delivered(offer.id());
        Card.Builder card = Card.builder(group, offer.id(), Component.literal(ShopView.name(offer))).state(state);
        // A sale shows what it takes as its icon; a picture of its own (a crate for a stack) stands for the goods; else the goods.
        if (offer.isSale() || ShopView.picture(offer)) {
            card.icon(ShopView.icon(offer));
        }
        card.gives(tr(offer.isSale() ? "rysquests.links.you_get_coins" : "rysquests.links.you_get"), parts(offer.gives(), null, false, delivered));
        String takes = offer.delivery() ? "rysquests.links.to_deliver" : offer.isSale() ? "rysquests.links.to_hand_in" : "rysquests.links.price";
        card.takes(tr(takes), parts(offer.cost(), offer.delivery() ? tr("rysquests.links.coins_on_take") : null, offer.delivery() && !locked, delivered));
        card.subtitle(offerSubtitle(offer, state, delivered));
        card.note(offerNote(offer, state, delivered));
        if (locked) {
            Quest opens = offer.quest().map(id -> BookData.book().quests().get(id)).filter(visible).orElse(null);
            card.lock(Component.literal(capital(ShopView.lockedLine(offer))), opens == null ? null : questLink(opens));
        }
        card.open(openOffer(offer));
        return card.build();
    }

    /** Rows of one side of a trade: items first in the order of the file, money after them. */
    private static List<Part> parts(List<ShopOffer.Entry> entries, @Nullable Component coinsDetail, boolean progress, Map<String, Long> delivered) {
        List<Part> items = new ArrayList<>();
        List<Part> coins = new ArrayList<>();
        for (ShopOffer.Entry entry : entries) {
            if (entry.isCurrency()) {
                coins.add(Part.coins(Component.literal(ShopView.money(entry.id(), entry.amount())), coinsDetail, entry.amount()));
            } else {
                ItemStack stack = entry.stack();
                if (!stack.isEmpty()) {
                    items.add(progress ? Part.item(stack, entry.amount(), delivered.getOrDefault(entry.id(), 0L)) : Part.item(stack, entry.amount()));
                }
            }
        }
        items.addAll(coins);
        return items;
    }

    /** Under the title: "закрыто", "поставка команды · 45 %", "куплено командой", else the offer's section. */
    @Nullable
    private static Component offerSubtitle(ShopOffer offer, State state, Map<String, Long> delivered) {
        if (state == State.LOCKED) {
            return tr("rysquests.shop.closed");
        }
        if (state == State.DONE) {
            return tr(ClientShop.inTeam() ? "rysquests.shop.bought_by_team" : "rysquests.shop.bought_alone");
        }
        if (offer.delivery()) {
            if (offer.deliveryComplete(delivered)) {
                return tr("rysquests.shop.delivery.ready");
            }
            return tr(ClientShop.inTeam() ? "rysquests.links.delivery_team" : "rysquests.links.delivery_own", ShopView.percent(offer));
        }
        String section = offer.category().map(key -> tr(key).getString()).orElse("");
        if (offer.isSale()) {
            return section.isEmpty() ? tr("rysquests.links.sale") : tr("rysquests.links.sale_in", section);
        }
        return section.isEmpty() ? null : Component.literal(section);
    }

    /** The line with the state's dot: how much of a delivery the team has brought, or what is left of a limit. */
    @Nullable
    private static Component offerNote(ShopOffer offer, State state, Map<String, Long> delivered) {
        if (state == State.LOCKED || state == State.DONE) {
            return null;
        }
        if (offer.delivery()) {
            Map<String, Long> rows = offer.deliveryRows();
            long full = rows.entrySet().stream().filter(row -> delivered.getOrDefault(row.getKey(), 0L) >= row.getValue()).count();
            return tr(ClientShop.inTeam() ? "rysquests.links.delivered_team" : "rysquests.links.delivered_own", Sidebar.full(full),
                    Plural.full("rysquests.links.plural.of_rows", rows.size()));
        }
        String limit = ShopView.limitShort(offer);
        if (limit != null && ShopView.left(offer) <= 0) {
            return tr("rysquests.links.limit_used", limit);
        }
        return limit == null ? null : tr("rysquests.links.limit", limit);
    }

    /** A row of the shop in the list of mentions: "Поставка команды · 45 %", the lock, or the price. */
    static Mention offerMention(ShopOffer offer) {
        State state = state(offer);
        Map<String, Long> delivered = ClientShop.delivered(offer.id());
        String detail;
        if (state == State.LOCKED) {
            detail = capital(ShopView.lockedLine(offer));
        } else if (offer.delivery() && state != State.DONE) {
            detail = capital(Objects.requireNonNull(offerSubtitle(offer, state, delivered)).getString());
        } else {
            ShopOffer.Entry price = ShopView.price(offer);
            String money = price == null ? "" : ShopView.money(price.id(), price.amount());
            detail = offer.isSale() ? Component.translatable("rysquests.links.sells_for", money).getString() : money;
        }
        return new Mention(tr("rysquests.links.section.shop"), ShopView.icon(offer), Component.literal(ShopView.name(offer)),
                detail.isEmpty() ? null : Component.literal(detail), state, openOffer(offer));
    }

    // --- quests ---

    /** "Творение · эпоха IX · побочный": the quest's chapter, its epoch and whether it is a side quest. */
    static String questDetail(Quest quest) {
        List<String> parts = new ArrayList<>();
        Chapter chapter = QuestFacts.chapter(quest);
        if (chapter != null) {
            String title = QuestFacts.chapterTitle(quest);
            if (!title.isEmpty()) {
                parts.add(title);
            }
            chapter.epoch().filter(epoch -> epoch > 0).ifPresent(epoch -> parts.add(tr("rysquests.links.epoch", Ui.roman(epoch)).getString()));
        }
        if (quest.options().optional()) {
            parts.add(tr("rysquests.search.state.side").getString());
        }
        return String.join(" · ", parts);
    }

    /**
     * A quest that gives the item as a reward, as a card: its rewards that are items (one of a choice marked so), where it stands,
     * what it waits for while closed with the quest to do first, and "Открыть в книге".
     *
     * @param visible whether a quest is shown in the book, for the link under the lock
     */
    public static Card reward(Group group, Quest quest, Predicate<Quest> visible) {
        State state = state(quest);
        Card.Builder card = Card.builder(group, quest.id(), tr(quest.title())).state(state);
        quest.icon().map(TaskText::stackOf).filter(stack -> !stack.isEmpty()).ifPresent(card::icon);
        List<Part> gives = new ArrayList<>();
        for (RewardDef reward : quest.rewards()) {
            rewardParts(reward, false, gives);
        }
        card.gives(tr("rysquests.links.reward"), gives);
        String detail = questDetail(quest);
        card.subtitle(detail.isEmpty() ? null : Component.literal(detail));
        QuestStatus status = QuestLogic.status(quest, ClientProgress::get);
        switch (status) {
            case CLAIMED -> card.note(tr("rysquests.links.quest_done"));
            case COMPLETED -> card.note(tr("rysquests.links.reward_waits"));
            case IN_PROGRESS -> card.note(tr("rysquests.links.tasks_done", QuestFacts.tasksDone(quest, ClientProgress.get(quest.id())), quest.tasks().size()));
            case AVAILABLE -> card.note(tr("rysquests.links.quest_open"));
            case LOCKED -> {
                Quest first = quest.requires().stream().filter(id -> !ClientProgress.get(id).everDone())
                        .map(id -> BookData.book().quests().get(id)).filter(Objects::nonNull).filter(visible).findFirst().orElse(null);
                card.lock(Component.literal(capital(BookData.waitingFor(quest).getString())), first == null ? null : questLink(first));
            }
        }
        card.open(openQuest(quest));
        return card.build();
    }

    private static void rewardParts(RewardDef reward, boolean choice, List<Part> out) {
        if (reward instanceof ItemReward item) {
            ItemStack stack = QuestFacts.stack(item).copyWithCount(1);
            if (!stack.isEmpty()) {
                out.add(new Part(stack, false, null, choice ? tr("rysquests.links.one_of") : null, item.count(), -1));
            }
        } else if (reward instanceof ChoiceReward options) {
            options.of().forEach(option -> rewardParts(option, true, out));
        } else if (reward instanceof works.repka.rysquests.reward.CurrencyReward coins && !choice) {
            out.add(Part.coins(Component.literal(ShopView.money(coins.id(), coins.amount())), null, coins.amount()));
        }
    }

    /** A quest in the list of mentions: its icon, title, chapter and epoch, state; a click opens the book on it. */
    static Mention questMention(Component section, Quest quest) {
        String detail = questDetail(quest);
        return new Mention(section, quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), tr(quest.title()),
                detail.isEmpty() ? null : Component.literal(detail), state(quest), openQuest(quest));
    }

    /**
     * The chip "2 квеста" and its list (mockup 3): quests that ask for the item in a task, quests that give it, then the shop. The chip
     * counts quests only, each once; an item only the shop knows says "в магазине", the one reward of one quest "награда квеста".
     * Null when the book has nothing to say about it.
     */
    @Nullable
    public static Mentions mentions(List<Quest> asking, List<Quest> rewarding, List<ShopOffer> offers) {
        if (asking.isEmpty() && rewarding.isEmpty() && offers.isEmpty()) {
            return null;
        }
        List<Mention> rows = new ArrayList<>();
        Component tasks = tr("rysquests.links.section.tasks");
        Component rewards = tr("rysquests.links.section.rewards");
        asking.forEach(quest -> rows.add(questMention(tasks, quest)));
        rewarding.forEach(quest -> rows.add(questMention(rewards, quest)));
        offers.forEach(offer -> rows.add(offerMention(offer)));
        Set<ResourceLocation> quests = new LinkedHashSet<>();
        asking.forEach(quest -> quests.add(quest.id()));
        rewarding.forEach(quest -> quests.add(quest.id()));
        Component label;
        if (quests.isEmpty()) {
            label = tr("rysquests.links.chip.shop");
        } else if (asking.isEmpty() && rewarding.size() == 1) {
            label = tr("rysquests.links.chip.reward");
        } else {
            label = Component.literal(Plural.full("rysquests.plural.quest", quests.size()));
        }
        return new Mentions(label, tr("rysquests.links.title"), rows);
    }

    /** "3 сделки", "2 квеста": the count of a category in the book's words. */
    public static Component count(boolean shop, int count) {
        return Component.literal(Plural.full(shop ? "rysquests.links.plural.offer" : "rysquests.plural.quest", count));
    }
}
