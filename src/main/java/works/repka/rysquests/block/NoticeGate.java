package works.repka.rysquests.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * When the owner of a shop block hears that it stopped (decision on question 17 of the shop plan), with no world in it, so that the
 * tests can drive it. A block tells the gate every change of its state; the gate keeps one notice per block and lets them out at most
 * once a minute per owner: one notice for one block, one notice with "and N more" for several.
 *
 * <ul>
 *   <li>Once per change: the same state told twice is one notice, a state that is gone before its notice went out is no notice.</li>
 *   <li>Once a minute per owner: whatever the number of blocks, the owner gets at most one notice a minute.</li>
 *   <li>Only yellow and red states make a notice ({@link ReceiverStatus#notifies}), only from a block whose switch is on.</li>
 *   <li>An owner who is away gets the notice when they come back, if the block is still stopped by then.</li>
 * </ul>
 */
public final class NoticeGate {
    /** Ticks between two notices to one owner: a minute. */
    public static final long OWNER_PERIOD = 1200;

    /** A block that stopped. @param kind "receiver" or "dispenser" @param dimension with the position, the block's key */
    public record Stop(String kind, String dimension, BlockPos pos, ReceiverStatus status, Optional<ResourceLocation> offer) {
        public String key() {
            return dimension + "@" + pos.asLong();
        }
    }

    /** A notice to send. @param more how many other blocks of the same owner stopped too */
    public record Due(UUID owner, Stop stop, int more) {
    }

    private final Map<String, ReceiverStatus> last = new HashMap<>();
    private final Map<UUID, Map<String, Stop>> pending = new LinkedHashMap<>();
    private final Map<UUID, Long> sentAt = new HashMap<>();

    /**
     * A block's state, told whenever it may have changed. @param enabled the block's switch "tell me when it stops" @return whether a
     * notice is now waiting for it
     */
    public boolean status(UUID owner, Stop stop, boolean enabled) {
        String key = stop.key();
        ReceiverStatus before = last.put(key, stop.status());
        if (before == stop.status() && enabled) {
            return pending.getOrDefault(owner, Map.of()).containsKey(key);
        }
        if (enabled && stop.status().notifies()) {
            pending.computeIfAbsent(owner, any -> new LinkedHashMap<>()).put(key, stop);
            return true;
        }
        forget(owner, key);
        return false;
    }

    /** The block is gone or changed its owner: nothing more about it. */
    public void removed(UUID owner, String key) {
        forget(owner, key);
        last.remove(key);
    }

    private void forget(UUID owner, String key) {
        Map<String, Stop> waiting = pending.get(owner);
        if (waiting != null) {
            waiting.remove(key);
            if (waiting.isEmpty()) {
                pending.remove(owner);
            }
        }
    }

    /** How many notices wait for an owner. */
    public int waiting(UUID owner) {
        return pending.getOrDefault(owner, Map.of()).size();
    }

    /**
     * The notices to send now: one per owner who is in the world and heard nothing in the last minute. @param now the server's tick
     * @param online whether an owner is in the world
     */
    public List<Due> due(long now, Predicate<UUID> online) {
        if (pending.isEmpty()) {
            return List.of();
        }
        List<Due> out = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, Map<String, Stop>>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<String, Stop>> entry = it.next();
            UUID owner = entry.getKey();
            Long at = sentAt.get(owner);
            if (at != null && now - at < OWNER_PERIOD || !online.test(owner)) {
                continue;
            }
            Map<String, Stop> stops = entry.getValue();
            // The latest stop leads: it is the one the owner is most likely to want to hear about.
            Stop newest = null;
            for (Stop stop : stops.values()) {
                newest = stop;
            }
            out.add(new Due(owner, newest, stops.size() - 1));
            sentAt.put(owner, now);
            it.remove();
        }
        sentAt.values().removeIf(at -> now - at >= OWNER_PERIOD * 2);
        return out;
    }
}
