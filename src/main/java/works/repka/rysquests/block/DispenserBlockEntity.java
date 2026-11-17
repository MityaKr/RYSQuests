package works.repka.rysquests.block;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.shop.ShopService;

/**
 * The dispenser in the world: holds a {@link Dispenser}, buys once a second on its own offset, finds the inventory on its output side,
 * gives pipes its own slot to take from, keeps the lamp, the comparator and the owner's notices in step with the status and opens the
 * window for the owner and the owner's team.
 */
public final class DispenserBlockEntity extends BlockEntity implements MenuProvider {
    private final Dispenser dispenser = new Dispenser();
    private @Nullable ReceiverMarket market;
    /** The status the lamp, the comparator and the notices last showed. */
    private @Nullable ReceiverStatus shown;
    /** The tick of the second this block buys in: blocks next to each other count in different ticks. */
    private final int offset;

    public DispenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISPENSER_ENTITY.get(), pos, state);
        this.offset = Math.floorMod(Long.hashCode(pos.asLong()) * 31, Dispenser.CYCLE_TICKS);
        dispenser.onSlotChanged(this::setChanged);
    }

    public Dispenser dispenser() {
        return dispenser;
    }

    /** Pipes take from the own slot, from any side; nothing goes in. */
    public IItemHandler handler() {
        return dispenser.pipes();
    }

    private @Nullable ReceiverMarket market() {
        if (market == null && level instanceof ServerLevel server) {
            market = ReceiverMarket.of(server.getServer());
        }
        return market;
    }

    // --- once a second ---

    void tick() {
        if (level != null && (level.getGameTime() + offset) % Dispenser.CYCLE_TICKS == 0) {
            cycle();
        }
    }

    private @Nullable IItemHandler target() {
        Optional<Direction> side = dispenser.settings().output();
        if (level == null || side.isEmpty()) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(side.get()), side.get().getOpposite());
    }

    void cycle() {
        ReceiverMarket shop = market();
        if (shop == null || level == null) {
            return;
        }
        long lots = dispenser.lots();
        List<ItemStack> spill = dispenser.cycle(shop, target(), level.hasNeighborSignal(worldPosition));
        for (ItemStack stack : spill) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
        }
        if (lots != dispenser.lots()) {
            setChanged();
        }
        show();
    }

    /** Shows the status: the lamp, the comparator and, on a change, the owner's notice (decision on question 17 of the plan). */
    private void show() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockState state = getBlockState();
        ReceiverStatus status = dispenser.status();
        if (state.hasProperty(ReceiverBlock.LAMP) && state.getValue(ReceiverBlock.LAMP) != status.lamp()) {
            level.setBlock(worldPosition, state.setValue(ReceiverBlock.LAMP, status.lamp()), Block.UPDATE_CLIENTS);
        }
        if (status != shown) {
            shown = status;
            setChanged();
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
            BlockNotices.status(server, worldPosition, "dispenser", dispenser.owner(), status, dispenser.settings().offer(), dispenser.settings().notices());
        }
    }

    // --- owner and access ---

    void claim(ServerPlayer player) {
        dispenser.setOwner(player.getUUID(), player.getGameProfile().getName());
        setChanged();
    }

    boolean mayUse(ServerPlayer player) {
        ReceiverMarket shop = market();
        return shop != null && ReceiverAccess.mayUse(dispenser.owner(), player.getUUID(), shop::team);
    }

    boolean mayBreak(ServerPlayer player) {
        ReceiverMarket shop = market();
        return shop == null || ReceiverAccess.mayBreak(dispenser.owner(), player.getUUID(), shop::team, player.hasPermissions(BookHolder.OPERATOR));
    }

    /** What a stranger is told: whose block it is. */
    Component foreignMessage() {
        return Component.translatable("rysquests.dispenser.foreign", dispenser.ownerName());
    }

    /** Opens the window, or tells a stranger whose block it is. A block nobody owns becomes the opener's. */
    void open(ServerPlayer player) {
        if (!mayUse(player)) {
            player.displayClientMessage(foreignMessage(), true);
            return;
        }
        if (dispenser.owner() == null) {
            claim(player);
        }
        DispenserView view = view(player);
        player.openMenu(this, buf -> {
            buf.writeBlockPos(worldPosition);
            DispenserView.STREAM_CODEC.encode(buf, view);
        });
    }

    // --- the window ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.rysquests.shop_dispenser");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DispenserMenu(id, inventory, this);
    }

    /** What the window shows now to a player. */
    DispenserView view(ServerPlayer player) {
        ReceiverMarket shop = market();
        ShopOffer offer = shop == null ? null : dispenser.offer(shop);
        String currency = offer == null ? "coin" : Dispenser.currency(offer);
        boolean inTeam = shop != null && ReceiverAccess.mayChooseTreasury(dispenser.owner(), shop::team);
        long wallet = shop == null || dispenser.owner() == null ? 0 : shop.balance(dispenser.owner(), false, currency);
        long treasury = inTeam ? shop.balance(dispenser.owner(), true, currency) : 0;
        int sides = 0;
        if (level != null) {
            for (Direction side : Direction.values()) {
                if (level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(side), side.getOpposite()) != null) {
                    sides |= 1 << side.get3DDataValue();
                }
            }
        }
        long held = 0;
        for (int i = 0; i < dispenser.slot().getSlots(); i++) {
            held += dispenser.slot().getStackInSlot(i).getCount();
        }
        boolean mayTreasury = inTeam && (dispenser.settings().treasury() || ShopService.maySpendTreasury(player.getUUID()));
        return new DispenserView(dispenser.owner() == null ? ReceiverView.NOBODY : dispenser.owner(), dispenser.ownerName(), inTeam, mayTreasury, dispenser.settings(),
                currency, wallet, treasury, sides, held, dispenser.lots(), dispenser.spent(), dispenser.status());
    }

    /**
     * New settings from the window. Nothing the client says is believed: the trade must exist and be one a dispenser can buy, the
     * treasury needs an owner in a team and, to be switched on, a player with the right to spend it; the numbers are bounded by the
     * settings themselves.
     */
    void configure(ServerPlayer player, DispenserSettings asked, boolean reset) {
        ReceiverMarket shop = market();
        if (shop == null || !mayUse(player)) {
            return;
        }
        DispenserSettings before = dispenser.settings();
        Optional<ResourceLocation> offer = asked.offer().filter(id -> {
            ShopOffer found = shop.offer(id);
            return found != null && Dispenser.fits(found);
        });
        boolean treasury = asked.treasury()
                && ReceiverAccess.mayPayFromTreasury(dispenser.owner(), shop::team, before.treasury(), ShopService.maySpendTreasury(player.getUUID()));
        DispenserSettings next = asked.withOffer(offer).withTreasury(treasury);
        dispenser.configure(next);
        if (reset) {
            dispenser.resetCounters();
        }
        if (next.notices() != before.notices() && level instanceof ServerLevel server) {
            // The switch is part of what the notices know: turned off, a waiting notice is dropped.
            BlockNotices.status(server, worldPosition, "dispenser", dispenser.owner(), dispenser.status(), next.offer(), next.notices());
        }
        setChanged();
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    /** The block breaks: what lies in its own slot drops as items. */
    void broken() {
        if (level == null) {
            return;
        }
        for (ItemStack stack : dispenser.takeSlot()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
        }
        if (level instanceof ServerLevel server) {
            BlockNotices.removed(server, worldPosition, dispenser.owner());
        }
    }

    // --- saving ---

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        dispenser.load(tag.getCompound("dispenser"), registries);
        // A loaded block shows what it saved without telling the owner again: only a change after this is news.
        shown = dispenser.status();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("dispenser", dispenser.save(registries));
    }
}
