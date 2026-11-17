package works.repka.rysquests.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.ShopOffer.Entry;
import works.repka.rysquests.task.KillTask;

/**
 * What the editor's "Магазин" (picture 39, ADR-58) changes in a trade, with no screen in it, so that the tests can check that every
 * edit keeps what it does not touch: the line of the closed card ({@code lock_text}), the delivery, the stage, the icon, the
 * components of what is given, the currency's name and the items a delivery asks for.
 *
 * <p>A trade has two sides as the editor shows them: the goods (what the player gets in a purchase, what the player gives in a sale)
 * and the price (the coins, with the items a delivery or a barter asks for besides).
 */
public final class OfferEdits {
    public static final String COIN = "coin";

    private OfferEdits() {
    }

    /** A boss a trade can wait for: the first tier of an achievement that counts kills of one entity (shop plan, step M3). */
    public record Boss(ResourceLocation quest, ResourceLocation entity) {
    }

    // --- reading ---

    /** The goods: the items the player gets in a purchase, the items the player gives in a sale. */
    public static List<Entry> goods(ShopOffer offer) {
        return items(offer.isSale() ? offer.cost() : offer.gives());
    }

    /** The items a purchase asks for besides its coins: what a delivery brings in parts, or what a barter takes at once. None in a sale. */
    public static List<Entry> payItems(ShopOffer offer) {
        return offer.isSale() ? List.of() : items(offer.cost());
    }

    /** The price: the first currency of the price's side, 0 when it has none. */
    public static int price(ShopOffer offer) {
        return priceSide(offer).stream().filter(Entry::isCurrency).mapToInt(Entry::amount).findFirst().orElse(0);
    }

    private static List<Entry> priceSide(ShopOffer offer) {
        return offer.isSale() ? offer.gives() : offer.cost();
    }

    private static List<Entry> items(List<Entry> side) {
        return side.stream().filter(entry -> !entry.isCurrency()).toList();
    }

    private static List<Entry> currencies(List<Entry> side) {
        return side.stream().filter(Entry::isCurrency).toList();
    }

    /** Items first, then the currencies of the side, as the files of a delivery show them. */
    private static List<Entry> join(List<Entry> items, List<Entry> currencies) {
        List<Entry> out = new ArrayList<>(items);
        out.addAll(currencies);
        return List.copyOf(out);
    }

    // --- the fields one by one ---

    private static ShopOffer copy(ShopOffer o, Optional<String> title, Optional<String> category, List<Entry> cost, List<Entry> gives,
            Optional<ShopOffer.Limit> limit, Optional<ResourceLocation> quest, Optional<String> lockText, boolean delivery) {
        return new ShopOffer(o.id(), title, o.icon(), category, cost, gives, limit, o.stage(), o.stageTitle(), quest, lockText, delivery);
    }

    private static ShopOffer sides(ShopOffer o, List<Entry> cost, List<Entry> gives) {
        return copy(o, o.title(), o.category(), cost, gives, o.limit(), o.quest(), o.lockText(), o.delivery());
    }

    public static ShopOffer withTitle(ShopOffer o, Optional<String> title) {
        return copy(o, title, o.category(), o.cost(), o.gives(), o.limit(), o.quest(), o.lockText(), o.delivery());
    }

    public static ShopOffer withCategory(ShopOffer o, Optional<String> category) {
        return copy(o, o.title(), category, o.cost(), o.gives(), o.limit(), o.quest(), o.lockText(), o.delivery());
    }

    public static ShopOffer withQuest(ShopOffer o, Optional<ResourceLocation> quest) {
        return copy(o, o.title(), o.category(), o.cost(), o.gives(), o.limit(), quest, o.lockText(), o.delivery());
    }

    public static ShopOffer withLimit(ShopOffer o, Optional<ShopOffer.Limit> limit) {
        return copy(o, o.title(), o.category(), o.cost(), o.gives(), limit, o.quest(), o.lockText(), o.delivery());
    }

    /** The line of the closed card: a lang key or the text itself; empty gives the card its own words back. */
    public static ShopOffer withLockText(ShopOffer o, Optional<String> lockText) {
        return copy(o, o.title(), o.category(), o.cost(), o.gives(), o.limit(), o.quest(), lockText.filter(text -> !text.isBlank()), o.delivery());
    }

    /**
     * The key the editor types the line of the closed card into: the trade's own key when it has one, else a new one beside its name.
     *
     * @param isKey whether a string is a lang key rather than the text itself (the game's language decides)
     */
    public static String lockKey(ShopOffer o, Predicate<String> isKey) {
        return o.lockText().filter(isKey).orElse("shop." + o.id().getNamespace() + "." + o.id().getPath().replace('/', '.') + ".lock");
    }

    /** The price in the currency it already has (a token stays a token), or in coins when the side has none yet. */
    public static ShopOffer withPrice(ShopOffer o, int amount) {
        if (amount <= 0) {
            return o;
        }
        List<Entry> side = new ArrayList<>(priceSide(o));
        int at = -1;
        for (int i = 0; i < side.size() && at < 0; i++) {
            if (side.get(i).isCurrency()) {
                at = i;
            }
        }
        if (at < 0) {
            side.add(new Entry(ShopOffer.Kind.CURRENCY, COIN, amount));
        } else {
            side.set(at, new Entry(ShopOffer.Kind.CURRENCY, side.get(at).id(), amount));
        }
        return o.isSale() ? sides(o, o.cost(), List.copyOf(side)) : sides(o, List.copyOf(side), o.gives());
    }

    /** New goods; the currencies beside them stay. A trade keeps at least one item of goods. */
    public static ShopOffer withGoods(ShopOffer o, List<Entry> goods) {
        if (goods.isEmpty()) {
            return o;
        }
        return o.isSale() ? sides(o, join(goods, currencies(o.cost())), o.gives()) : sides(o, o.cost(), join(goods, currencies(o.gives())));
    }

    /** New items besides the price of a purchase; a delivery keeps at least one. A sale has none. */
    public static ShopOffer withPayItems(ShopOffer o, List<Entry> items) {
        if (o.isSale() || o.delivery() && items.isEmpty()) {
            return o;
        }
        return sides(o, join(items, currencies(o.cost())), o.gives());
    }

    /** The count of one item: of the goods, or of the items besides the price. A count that is not positive changes nothing. */
    public static ShopOffer withCount(ShopOffer o, boolean pay, int index, int count) {
        List<Entry> list = new ArrayList<>(pay ? payItems(o) : goods(o));
        if (count <= 0 || index < 0 || index >= list.size()) {
            return o;
        }
        Entry old = list.get(index);
        list.set(index, new Entry(old.type(), old.id(), count, old.components()));
        return pay ? withPayItems(o, list) : withGoods(o, list);
    }

    /** The components of one item the player gets; only a purchase gives items, so a sale is left as it is. */
    public static ShopOffer withComponents(ShopOffer o, int index, Optional<DataComponentPatch> components) {
        List<Entry> list = new ArrayList<>(goods(o));
        if (o.isSale() || index < 0 || index >= list.size()) {
            return o;
        }
        Entry old = list.get(index);
        list.set(index, new Entry(old.type(), old.id(), old.amount(), components.filter(patch -> !patch.isEmpty())));
        return withGoods(o, list);
    }

    /** A purchase becomes a sale and back. A delivery is always a purchase, so it stays one: its delivery is switched off first. */
    public static ShopOffer flipped(ShopOffer o) {
        if (o.delivery()) {
            return o;
        }
        return sides(o, o.gives(), o.cost());
    }

    /**
     * Pays in parts or at once. A delivery needs an item to bring: the purchase's own items besides the price, else the held item
     * (plain: a player brings plain items), else it stays as it is. Switched off, the items stay and are paid at once.
     */
    public static ShopOffer withDelivery(ShopOffer o, boolean on, Optional<Entry> held) {
        if (on == o.delivery() || on && o.isSale()) {
            return o;
        }
        if (!on) {
            return copy(o, o.title(), o.category(), o.cost(), o.gives(), o.limit(), o.quest(), o.lockText(), false);
        }
        List<Entry> cost = o.cost();
        if (payItems(o).isEmpty()) {
            if (held.isEmpty() || held.get().isCurrency()) {
                return o;
            }
            Entry plain = new Entry(ShopOffer.Kind.ITEM, held.get().id(), held.get().amount());
            cost = join(List.of(plain), currencies(o.cost()));
        }
        return copy(o, o.title(), o.category(), cost, o.gives(), o.limit(), o.quest(), o.lockText(), true);
    }

    // --- checks ---

    /**
     * What is wrong or unseen in a trade, as keys under {@code rysquests.ed.shop.problem.}: a delivery with nothing to bring or that
     * gives only coins (the book would leave it out), a line of the closed card for a trade that is never closed, an item of the
     * price with components (a player pays with plain items).
     */
    public static List<String> problems(ShopOffer o) {
        List<String> out = new ArrayList<>();
        if (o.delivery() && o.isSale()) {
            out.add("delivery_sale");
        } else if (o.delivery() && payItems(o).isEmpty()) {
            out.add("delivery_items");
        }
        if (o.cost().stream().anyMatch(Entry::hasComponents)) {
            out.add("cost_components");
        }
        if (o.lockText().isPresent() && o.quest().isEmpty() && o.stage().isEmpty()) {
            out.add("lock_unseen");
        }
        return out;
    }

    /** A count or a price as typed: a whole number above zero, else nothing. */
    public static OptionalInt positive(String typed) {
        try {
            int value = Integer.parseInt(typed.trim());
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /** The bosses a trade can wait for, in the book's order of achievements: each the first tier of a kill achievement. */
    public static List<Boss> bosses(QuestBook book) {
        List<Boss> out = new ArrayList<>();
        for (Achievement achievement : book.achievements().values()) {
            if (achievement.archived() || achievement.tiers().isEmpty()) {
                continue;
            }
            ResourceLocation id = achievement.questId(achievement.tiers().getFirst());
            Quest tier = book.quests().get(id);
            if (tier != null && tier.tasks().size() == 1 && tier.tasks().getFirst() instanceof KillTask kill) {
                out.add(new Boss(id, kill.entity()));
            }
        }
        return out;
    }
}
