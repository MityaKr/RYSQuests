package works.repka.rysquests.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.ShopOffer;

/**
 * The intake block in the world: holds a {@link Receiver}, gives pipes an inventory that only takes, schedules one tick a second after
 * items came in, keeps the lamp in step with the status and opens the window for the owner and the owner's team.
 */
public final class ReceiverBlockEntity extends BlockEntity implements MenuProvider {
    private final Receiver receiver = new Receiver();
    private @Nullable ReceiverMarket market;
    /** The status the lamp, the comparator and the notices last showed; null until the block first looked after loading. */
    private @Nullable ReceiverStatus shown;

    /** No slots: every insertion goes straight to the receiver, an honest simulation included. Nothing ever comes out. */
    private final IItemHandler handler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return insert(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && insert(stack.copyWithCount(1), true).isEmpty();
        }
    };

    public ReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RECEIVER_ENTITY.get(), pos, state);
    }

    public IItemHandler handler() {
        return handler;
    }

    public Receiver receiver() {
        return receiver;
    }

    private @Nullable ReceiverMarket market() {
        if (market == null && level instanceof ServerLevel server) {
            market = ReceiverMarket.of(server.getServer());
        }
        return market;
    }

    private long now() {
        return level == null ? 0 : level.getGameTime();
    }

    // --- pipes ---

    ItemStack insert(ItemStack stack, boolean simulate) {
        ReceiverMarket shop = market();
        if (shop == null || stack.isEmpty()) {
            return stack;
        }
        ReceiverStatus before = receiver.status();
        ItemStack rest = receiver.insert(shop, stack, simulate, now());
        if (!simulate && (receiver.pending() || receiver.status() != before)) {
            setChanged();
            schedule();
        }
        if (receiver.status() != before) {
            showLamp();
        }
        return rest;
    }

    /** One tick a second after items came in, however many insertions there were. */
    private void schedule() {
        if (level != null && !level.getBlockTicks().hasScheduledTick(worldPosition, getBlockState().getBlock())) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), Receiver.SETTLE_TICKS);
        }
    }

    /** The scheduled tick: sells the whole lots and shows how the block stands. */
    void settle() {
        ReceiverMarket shop = market();
        if (shop == null) {
            return;
        }
        receiver.settle(shop, now());
        setChanged();
        showLamp();
    }

    /** Looks at the trade again without changing anything: when the window opens or the settings change. */
    void refresh() {
        ReceiverMarket shop = market();
        if (shop != null) {
            receiver.refresh(shop, now());
            showLamp();
        }
    }

    /** Shows the status: the lamp, the comparator and, on a change, the owner's notice (decision on question 17 of the plan). */
    private void showLamp() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockState state = getBlockState();
        ReceiverStatus status = receiver.status();
        ReceiverLamp lamp = status.lamp();
        if (state.hasProperty(ReceiverBlock.LAMP) && state.getValue(ReceiverBlock.LAMP) != lamp) {
            level.setBlock(worldPosition, state.setValue(ReceiverBlock.LAMP, lamp), Block.UPDATE_CLIENTS);
        }
        if (status != shown) {
            shown = status;
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
            BlockNotices.status(server, worldPosition, "receiver", receiver.owner(), status, Optional.ofNullable(receiver.offerId()), receiver.notifies());
        }
    }

    /** What a comparator next to the block reads. */
    int signal() {
        ReceiverMarket shop = market();
        return shop == null ? 0 : receiver.signal(shop);
    }

    // --- owner and access ---

    void claim(ServerPlayer player) {
        receiver.setOwner(player.getUUID(), player.getGameProfile().getName());
        setChanged();
        refresh();
    }

    boolean mayUse(ServerPlayer player) {
        ReceiverMarket shop = market();
        return shop != null && ReceiverAccess.mayUse(receiver.owner(), player.getUUID(), shop::team);
    }

    boolean mayBreak(ServerPlayer player) {
        ReceiverMarket shop = market();
        return shop == null || ReceiverAccess.mayBreak(receiver.owner(), player.getUUID(), shop::team, player.hasPermissions(BookHolder.OPERATOR));
    }

    /** What a stranger is told: whose block it is. */
    Component foreignMessage() {
        return Component.translatable("rysquests.receiver.foreign", receiver.ownerName());
    }

    /** Opens the window, or tells a stranger whose block it is. A block nobody owns becomes the opener's. */
    void open(ServerPlayer player) {
        if (!mayUse(player)) {
            player.displayClientMessage(foreignMessage(), true);
            return;
        }
        if (receiver.owner() == null) {
            claim(player);
        }
        refresh();
        ReceiverView view = view();
        player.openMenu(this, buf -> {
            buf.writeBlockPos(worldPosition);
            ReceiverView.STREAM_CODEC.encode(buf, view);
        });
    }

    // --- the window ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.rysquests.shop_receiver");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ReceiverMenu(id, inventory, this);
    }

    /** What the window shows now. */
    ReceiverView view() {
        ReceiverMarket shop = market();
        ShopOffer offer = shop == null ? null : receiver.offer(shop);
        String currency = offer != null && offer.isSale() && offer.gives().getFirst().isCurrency() ? offer.gives().getFirst().id() : "coin";
        boolean inTeam = shop != null && ReceiverAccess.mayChooseTreasury(receiver.owner(), shop::team);
        long wallet = shop == null || receiver.owner() == null ? 0 : shop.balance(receiver.owner(), false, currency);
        long treasury = inTeam ? shop.balance(receiver.owner(), true, currency) : 0;
        Map<String, Long> buffer = new LinkedHashMap<>();
        receiver.buffer().forEach((item, count) -> buffer.put(BuiltInRegistries.ITEM.getKey(item).toString(), count));
        return new ReceiverView(receiver.owner() == null ? ReceiverView.NOBODY : receiver.owner(), receiver.ownerName(), inTeam, receiver.programId(),
                Optional.ofNullable(receiver.offerId()), receiver.treasury(), currency, wallet, treasury, buffer, receiver.accepted(), receiver.lots(), receiver.earned(),
                receiver.status(), receiver.comparator(), receiver.notifies());
    }

    /**
     * New settings from the window. Nothing the client says is believed: the program must be registered, the trade must exist and fit
     * it, the treasury needs an owner in a team. What the buffer holds of a trade that is no longer chosen is sold if it makes whole
     * lots, and the rest drops on top of the block.
     */
    void configure(ServerPlayer player, ResourceLocation program, Optional<ResourceLocation> offer, boolean treasury, boolean reset, ComparatorMode comparator,
            boolean notify) {
        ReceiverMarket shop = market();
        ReceiverProgram mode = ReceiverProgram.Registry.get(program);
        if (shop == null || mode == null || !mayUse(player)) {
            return;
        }
        ResourceLocation chosen = offer.filter(id -> {
            ShopOffer found = shop.offer(id);
            return found != null && mode.fits(found);
        }).orElse(null);
        boolean toTreasury = treasury && ReceiverAccess.mayChooseTreasury(receiver.owner(), shop::team);
        if (!program.equals(receiver.programId()) || !java.util.Objects.equals(chosen, receiver.offerId())) {
            receiver.settle(shop, now());
            dropBuffer();
        }
        receiver.configure(program, chosen, toTreasury);
        receiver.setComparator(comparator);
        if (notify != receiver.notifies()) {
            receiver.setNotify(notify);
            // The switch is part of what the notices know: turned off, a waiting notice is dropped.
            if (level instanceof ServerLevel server) {
                BlockNotices.status(server, worldPosition, "receiver", receiver.owner(), receiver.status(), Optional.ofNullable(receiver.offerId()), notify);
            }
        }
        if (reset) {
            receiver.resetCounters();
        }
        setChanged();
        refresh();
    }

    /** The block breaks: whole lots are sold first, the rest of the buffer drops as items. */
    void broken() {
        ReceiverMarket shop = market();
        if (shop != null && !receiver.bufferEmpty()) {
            receiver.settle(shop, now());
        }
        dropBuffer();
        if (level instanceof ServerLevel server) {
            BlockNotices.removed(server, worldPosition, receiver.owner());
        }
    }

    private void dropBuffer() {
        if (level != null && !receiver.bufferEmpty()) {
            for (ItemStack stack : receiver.takeBuffer()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
            }
            setChanged();
        }
    }

    // --- saving ---

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        receiver.load(tag.getCompound("receiver"));
        // A loaded block shows what it saved without telling the owner again: only a change after this is news.
        shown = receiver.status();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("receiver", receiver.save());
    }
}
