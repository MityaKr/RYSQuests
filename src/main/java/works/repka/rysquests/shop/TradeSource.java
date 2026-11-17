package works.repka.rysquests.shop;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Where a trade takes the items it is paid with (shop plan, step M5): a player's inventory today, the buffer of an intake block or a
 * neighbouring inventory later. Only plain stacks pay, so that a worn, damaged, enchanted or renamed piece is never sold as an ordinary
 * one; a source counts and takes only those.
 */
public interface TradeSource {
    /** A source that holds nothing: a trade paid only with money, or a delivery's goods taken from the team's count. */
    TradeSource NONE = new TradeSource() {
        @Override
        public long count(Item item) {
            return 0;
        }

        @Override
        public long take(Item item, long amount) {
            return 0;
        }
    };

    /** How many of an item can be taken, as plain stacks. */
    long count(Item item);

    /** Takes up to that many plain stacks of an item. @return how many were taken */
    long take(Item item, long amount);

    /** Whether a stack can pay: plain, so that nothing with components is ever paid as an ordinary item. */
    static boolean pays(ItemStack stack, Item item) {
        return stack.is(item) && stack.getComponentsPatch().isEmpty();
    }

    /** Stacks held in a list, such as the main inventory of a player (never armour or the offhand). */
    static TradeSource of(List<ItemStack> items, Runnable changed) {
        return new TradeSource() {
            @Override
            public long count(Item item) {
                long have = 0;
                for (ItemStack stack : items) {
                    if (pays(stack, item)) {
                        have += stack.getCount();
                    }
                }
                return have;
            }

            @Override
            public long take(Item item, long amount) {
                long left = amount;
                for (int slot = 0; slot < items.size() && left > 0; slot++) {
                    ItemStack stack = items.get(slot);
                    if (pays(stack, item)) {
                        int taken = (int) Math.min(left, stack.getCount());
                        stack.shrink(taken);
                        left -= taken;
                    }
                }
                changed.run();
                return amount - left;
            }
        };
    }

    /**
     * An item handler of a block. It counts what it would really give out, by simulated extraction, so that a handler which refuses to
     * give something is never counted on.
     */
    static TradeSource of(IItemHandler handler) {
        return new TradeSource() {
            @Override
            public long count(Item item) {
                long have = 0;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (pays(stack, item)) {
                        have += handler.extractItem(slot, stack.getCount(), true).getCount();
                    }
                }
                return have;
            }

            @Override
            public long take(Item item, long amount) {
                long left = amount;
                for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (pays(stack, item)) {
                        left -= handler.extractItem(slot, (int) Math.min(left, stack.getCount()), false).getCount();
                    }
                }
                return amount - left;
            }
        };
    }
}
