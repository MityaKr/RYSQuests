package works.repka.rysquests.shop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Where a trade puts the items it gives (shop plan, step M5): a player's inventory today, the inventory next to a dispenser block later.
 * The trade asks first whether everything fits, takes the payment only then, and puts the goods last; what still does not fit comes
 * back to the caller, so that nothing paid for is lost.
 */
public interface TradeSink {
    /** Items given by a trade: one stack as it is given, components and all, and how many of it, which may be more than a stack. */
    record Goods(ItemStack template, long count) {
        public Goods {
            template = template.copyWithCount(1);
        }
    }

    /** A sink that takes nothing: a sale gives only money. */
    TradeSink NONE = new TradeSink() {
        @Override
        public boolean fits(List<Goods> goods) {
            return goods.isEmpty();
        }

        @Override
        public List<Goods> put(List<Goods> goods) {
            return goods;
        }
    };

    /** Whether all of these goods fit together. */
    boolean fits(List<Goods> goods);

    /** Puts the goods. @return what did not fit, nothing when all did */
    List<Goods> put(List<Goods> goods);

    /**
     * Whether goods fit into a list of stacks, in the free slots and on top of stacks already held. A stack counts only when it is the
     * same item with the same components: a plain schematic does not take a schematic with its file.
     */
    static boolean roomIn(List<ItemStack> items, List<Goods> goods) {
        long free = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        for (Goods good : goods) {
            ItemStack template = good.template();
            int maxStack = template.getMaxStackSize();
            long left = good.count();
            for (ItemStack stack : items) {
                if (ItemStack.isSameItemSameComponents(stack, template)) {
                    left -= Math.max(0, stack.getMaxStackSize() - stack.getCount());
                }
            }
            if (left > 0) {
                free -= (left + maxStack - 1) / maxStack;
            }
        }
        return free >= 0;
    }

    /**
     * A player's inventory: room is counted in the main inventory, and the goods are handed over the way a reward is, so that what does
     * not fit after all drops at the player's feet and nothing comes back.
     *
     * @param give hands one stack, no larger than the item allows, to the player
     */
    static TradeSink player(List<ItemStack> items, Consumer<ItemStack> give) {
        return new TradeSink() {
            @Override
            public boolean fits(List<Goods> goods) {
                return roomIn(items, goods);
            }

            @Override
            public List<Goods> put(List<Goods> goods) {
                for (Goods good : goods) {
                    int maxStack = good.template().getMaxStackSize();
                    long left = good.count();
                    while (left > 0) {
                        int part = (int) Math.min(left, maxStack);
                        give.accept(good.template().copyWithCount(part));
                        left -= part;
                    }
                }
                return List.of();
            }
        };
    }

    /**
     * An item handler of a block. Room is found by simulated insertion slot by slot, so that the handler's own rules (slot limits, what a
     * slot accepts) are kept. Several goods never count on the same slot: a slot one of them would go into is closed to the others, which
     * may refuse a trade that would just fit but never lets one through that does not.
     */
    static TradeSink of(IItemHandler handler) {
        return new TradeSink() {
            @Override
            public boolean fits(List<Goods> goods) {
                Set<Integer> used = new HashSet<>();
                for (Goods good : goods) {
                    if (insert(good, used, true) > 0) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public List<Goods> put(List<Goods> goods) {
                List<Goods> left = new ArrayList<>();
                for (Goods good : goods) {
                    long rest = insert(good, null, false);
                    if (rest > 0) {
                        left.add(new Goods(good.template(), rest));
                    }
                }
                return left;
            }

            /** @param used slots taken by goods simulated before, or null for a real insertion @return how many did not go in */
            private long insert(Goods good, Set<Integer> used, boolean simulate) {
                long left = good.count();
                int maxStack = good.template().getMaxStackSize();
                // Stacks of the same item first, then empty slots, as a hopper or a player would fill a chest.
                for (int pass = 0; pass < 2 && left > 0; pass++) {
                    for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                        if (used != null && used.contains(slot)) {
                            continue;
                        }
                        ItemStack there = handler.getStackInSlot(slot);
                        boolean fitsPass = pass == 0 ? ItemStack.isSameItemSameComponents(there, good.template()) : there.isEmpty();
                        if (!fitsPass) {
                            continue;
                        }
                        // A real insertion goes stack by stack; a simulation can offer one slot everything it might hold.
                        long offered = simulate ? Math.min(left, Math.max(maxStack, handler.getSlotLimit(slot))) : Math.min(left, maxStack);
                        while (offered > 0) {
                            ItemStack rest = handler.insertItem(slot, good.template().copyWithCount((int) offered), simulate);
                            long went = offered - rest.getCount();
                            if (went <= 0) {
                                break;
                            }
                            left -= went;
                            if (used != null) {
                                used.add(slot);
                            }
                            offered = simulate ? 0 : Math.min(left, maxStack);
                        }
                    }
                }
                return left;
            }
        };
    }
}
