package works.repka.rysquests.progress;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The statistics of the players who share progress, for {@link StatCount}: an online player's live ones, an offline player's as they
 * were when the player left. The statistics of an offline player do not change, so each is read from the world's {@code stats} folder
 * at most once a run and kept; a player who leaves leaves their final ones here, replacing what was read.
 */
final class MemberStats {
    private static final Map<UUID, ServerStatsCounter> OFFLINE = new HashMap<>();
    /** Everyone with a statistics file, listed once a run, for progress shared by the whole server. */
    private static final Set<UUID> KNOWN = new LinkedHashSet<>();
    private static boolean listed;

    private MemberStats() {
    }

    static int value(MinecraftServer server, UUID player, Stat<?> stat) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getStats().getValue(stat);
        }
        return OFFLINE.computeIfAbsent(player, id -> read(server, id)).getValue(stat);
    }

    private static ServerStatsCounter read(MinecraftServer server, UUID player) {
        // The same file PlayerList reads at login; a player who never played has none, and counts nothing.
        File folder = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
        return new ServerStatsCounter(server, new File(folder, player + ".json"));
    }

    /** Every player who ever played in this world, for progress the whole server shares. */
    static Set<UUID> everyone(MinecraftServer server) {
        if (!listed) {
            listed = true;
            File[] files = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile().listFiles((dir, name) -> name.endsWith(".json"));
            for (File file : files == null ? new File[0] : files) {
                try {
                    KNOWN.add(UUID.fromString(file.getName().substring(0, file.getName().length() - ".json".length())));
                } catch (IllegalArgumentException notAPlayer) {
                    // a file by a player's name from very old versions: PlayerList itself no longer reads those
                }
            }
        }
        Set<UUID> all = new LinkedHashSet<>(KNOWN);
        server.getPlayerList().getPlayers().forEach(player -> all.add(player.getUUID()));
        return all;
    }

    /** A player left: their statistics stay as they are now until they come back. */
    static void left(ServerPlayer player) {
        OFFLINE.put(player.getUUID(), player.getStats());
        KNOWN.add(player.getUUID());
    }

    /** The server stopped: another world may be opened next, in single player. */
    static void clear() {
        OFFLINE.clear();
        KNOWN.clear();
        listed = false;
    }
}
