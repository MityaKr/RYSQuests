package works.repka.rysquests.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.network.ReceiverConfigPayload;
import works.repka.rysquests.network.ReceiverStatePayload;

/**
 * The window of an intake block: no slots, only what the block shows ({@link ReceiverView}) and the settings the player changes. While
 * it is open the server looks a few times a second and sends the view only when it changed (shop plan, section 4.6).
 */
public final class ReceiverMenu extends AbstractContainerMenu {
    /** Ticks between two looks at the block while the window is open. */
    private static final int LOOK_EVERY = 5;

    private final BlockPos pos;
    private final @Nullable ReceiverBlockEntity entity;
    private final Player player;
    private ReceiverView view;
    private int sinceLook;

    /** On the server, over the block. */
    ReceiverMenu(int id, Inventory inventory, ReceiverBlockEntity entity) {
        super(ModBlocks.RECEIVER_MENU.get(), id);
        this.pos = entity.getBlockPos();
        this.entity = entity;
        this.player = inventory.player;
        this.view = entity.view();
    }

    /** On the client, from what the server wrote when it opened the window. */
    public ReceiverMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModBlocks.RECEIVER_MENU.get(), id);
        this.pos = buf.readBlockPos();
        this.view = ReceiverView.STREAM_CODEC.decode(buf);
        this.entity = null;
        this.player = inventory.player;
    }

    public BlockPos pos() {
        return pos;
    }

    public ReceiverView view() {
        return view;
    }

    /** A newer view from the server. */
    public void setView(ReceiverView view) {
        this.view = view;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (entity == null || !(player instanceof ServerPlayer server) || ++sinceLook < LOOK_EVERY) {
            return;
        }
        sinceLook = 0;
        ReceiverView now = entity.view();
        if (!now.equals(view)) {
            view = now;
            PacketDistributor.sendToPlayer(server, new ReceiverStatePayload(containerId, now));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return entity == null || !entity.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    /** New settings from the window: only for the window that is open now, and only when the player may still use the block. */
    public static void handleConfig(ServerPlayer player, ReceiverConfigPayload payload) {
        if (player.containerMenu instanceof ReceiverMenu menu && menu.containerId == payload.containerId() && menu.entity != null && menu.stillValid(player)) {
            menu.entity.configure(player, payload.program(), payload.offer(), payload.treasury(), payload.reset());
            // The answer goes at once: the window waits for it to show the new mode.
            menu.sinceLook = LOOK_EVERY;
            menu.broadcastChanges();
        }
    }

    /** A view for the window that is open now on this client. */
    public static void receive(@Nullable Player player, ReceiverStatePayload payload) {
        if (player != null && player.containerMenu instanceof ReceiverMenu menu && menu.containerId == payload.containerId()) {
            menu.setView(payload.view());
        }
    }
}
