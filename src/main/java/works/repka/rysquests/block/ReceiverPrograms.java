package works.repka.rysquests.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.shop.TradeCore;
import works.repka.rysquests.shop.TradeSource;

/** The two modes of the intake block today (shop plan, section 4.1): "Продажа" and "Поставка". */
public final class ReceiverPrograms {
    public static final Sell SELL = ReceiverProgram.Registry.register(new Sell());
    public static final Delivery DELIVERY = ReceiverProgram.Registry.register(new Delivery());

    private ReceiverPrograms() {
    }

    /** A source that has as much of every item as anyone could ask: a check of the trade that does not depend on what is held. */
    private static final TradeSource PLENTY = new TradeSource() {
        @Override
        public long count(Item item) {
            return Long.MAX_VALUE / 4;
        }

        @Override
        public long take(Item item, long amount) {
            return 0;
        }
    };

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /**
     * "Продажа": takes the items of a sale, plain stacks only, into the buffer, at most {@link Receiver#BUFFER_LOTS} lots of each, and
     * sells every whole lot once a second. The coins go to the owner's wallet or the team's treasury; putting coins into a treasury
     * needs no right (ADR-52).
     */
    public static final class Sell implements ReceiverProgram {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "sell");

        private Sell() {
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public boolean fits(ShopOffer offer) {
            return offer.isSale() && !offer.delivery() && offer.cost().stream().anyMatch(entry -> !entry.isCurrency());
        }

        /** How many of an item one lot takes, 0 for an item the sale does not take. */
        static long lot(ShopOffer offer, Item item) {
            String id = itemId(item);
            long out = 0;
            for (ShopOffer.Entry entry : offer.cost()) {
                if (!entry.isCurrency() && entry.id().equals(id)) {
                    out += entry.amount();
                }
            }
            return out;
        }

        @Override
        public ItemStack insert(Receiver receiver, ReceiverMarket market, ShopOffer offer, ItemStack stack, boolean simulate) {
            Item item = stack.getItem();
            long lot = lot(offer, item);
            if (lot <= 0 || !TradeSource.pays(stack, item)) {
                return stack;
            }
            long room = lot * Receiver.BUFFER_LOTS - receiver.buffered(item);
            int taken = (int) Math.max(0, Math.min(stack.getCount(), room));
            if (taken == 0) {
                return stack;
            }
            if (!simulate) {
                receiver.buffer(item, taken);
            }
            return stack.copyWithCount(stack.getCount() - taken);
        }

        /** How many whole lots the buffer holds. */
        static long wholeLots(Receiver receiver, ShopOffer offer) {
            long out = Long.MAX_VALUE;
            for (ShopOffer.Entry entry : offer.cost()) {
                if (!entry.isCurrency()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.id()));
                    out = Math.min(out, receiver.buffered(item) / lot(offer, item));
                }
            }
            return out == Long.MAX_VALUE ? 0 : out;
        }

        @Override
        public @Nullable String settle(Receiver receiver, ReceiverMarket market, ShopOffer offer) {
            long whole = wholeLots(receiver, offer);
            if (whole == 0) {
                return check(receiver, market, offer);
            }
            TradeCore.Outcome outcome = market.sell(receiver.owner(), offer, (int) Math.min(Integer.MAX_VALUE, whole), receiver.treasury(), receiver.bufferSource());
            if (!outcome.ok()) {
                return outcome.problem();
            }
            receiver.sold(outcome.times(), coins(offer) * outcome.times());
            // Whole lots left over mean the coins or the limit ran out midway: the lamp says which, not "works".
            return outcome.times() < whole ? check(receiver, market, offer) : null;
        }

        @Override
        public @Nullable String check(Receiver receiver, ReceiverMarket market, ShopOffer offer) {
            return market.check(receiver.owner(), offer, receiver.treasury(), PLENTY);
        }

        /** The coins one lot brings, in the sale's first currency. */
        public static long coins(ShopOffer offer) {
            ShopOffer.Entry first = offer.gives().getFirst();
            return first.isCurrency() ? first.amount() : 0;
        }
    }

    /**
     * "Поставка": gives what comes in straight to the delivery count of the owner's team, only what a row still lacks, so a full row
     * takes nothing more and its pipe stands. Once every row is full the block waits until the goods are taken in the shop.
     */
    public static final class Delivery implements ReceiverProgram {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "delivery");

        private Delivery() {
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public boolean fits(ShopOffer offer) {
            return offer.delivery() && !offer.deliveryRows().isEmpty();
        }

        @Override
        public ItemStack insert(Receiver receiver, ReceiverMarket market, ShopOffer offer, ItemStack stack, boolean simulate) {
            Item item = stack.getItem();
            if (!offer.deliveryRows().containsKey(itemId(item)) || !TradeSource.pays(stack, item)) {
                return stack;
            }
            long[] left = {stack.getCount()};
            TradeSource one = new TradeSource() {
                @Override
                public long count(Item asked) {
                    return asked == item ? left[0] : 0;
                }

                @Override
                public long take(Item asked, long amount) {
                    long taken = asked == item ? Math.min(left[0], amount) : 0;
                    left[0] -= taken;
                    return taken;
                }
            };
            TradeCore.Delivered delivered = market.deliver(receiver.owner(), offer, one, simulate);
            if (!delivered.ok()) {
                // "Nothing to deliver" is only this row being full: the others may still take theirs.
                if (!simulate && !"rysquests.shop.error.nothing_to_deliver".equals(delivered.problem())) {
                    receiver.refused(ReceiverStatus.of(delivered.problem()));
                }
                return stack;
            }
            long taken = delivered.taken().getOrDefault(itemId(item), 0L);
            if (taken <= 0) {
                return stack;
            }
            if (!simulate) {
                receiver.counted(taken);
            }
            return stack.copyWithCount((int) (stack.getCount() - taken));
        }

        @Override
        public @Nullable String settle(Receiver receiver, ReceiverMarket market, ShopOffer offer) {
            return check(receiver, market, offer);
        }

        @Override
        public @Nullable String check(Receiver receiver, ReceiverMarket market, ShopOffer offer) {
            TradeCore.Delivered delivered = market.deliver(receiver.owner(), offer, PLENTY, true);
            return delivered.ok() ? null : delivered.problem();
        }
    }
}
