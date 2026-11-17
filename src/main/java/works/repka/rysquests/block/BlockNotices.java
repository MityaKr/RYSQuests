package works.repka.rysquests.block;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.network.BlockNoticePayload;

/**
 * The server's side of "your block stopped" (decision on question 17 of the shop plan): the blocks tell it every change of their
 * state, it lets the notices out through a {@link NoticeGate} once a second, to owners who are in the world.
 */
public final class BlockNotices {
    private static NoticeGate gate = new NoticeGate();

    private BlockNotices() {
    }

    /** A block's state may have changed. */
    static void status(ServerLevel level, BlockPos pos, String kind, @Nullable UUID owner, ReceiverStatus status, Optional<ResourceLocation> offer, boolean enabled) {
        if (owner != null) {
            gate.status(owner, new NoticeGate.Stop(kind, level.dimension().location().toString(), pos, status, offer), enabled);
        }
    }

    /** A block is gone: nothing more about it. */
    static void removed(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        if (owner != null) {
            gate.removed(owner, new NoticeGate.Stop("", level.dimension().location().toString(), pos, ReceiverStatus.NO_OWNER, Optional.empty()).key());
        }
    }

    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 20 != 0) {
            return;
        }
        for (NoticeGate.Due due : gate.due(now, owner -> server.getPlayerList().getPlayer(owner) != null)) {
            ServerPlayer player = server.getPlayerList().getPlayer(due.owner());
            if (player != null) {
                NoticeGate.Stop stop = due.stop();
                PacketDistributor.sendToPlayer(player, new BlockNoticePayload(stop.kind(), stop.pos(), stop.status(), stop.offer(), due.more()));
            }
        }
    }

    static void onServerStopped(ServerStoppedEvent event) {
        gate = new NoticeGate();
    }
}
