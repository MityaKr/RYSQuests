package works.repka.rysquests.shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * When the money of a team changed by a shop block is sent to its members (shop plan, section 4.6): at most once a second per team,
 * whatever the number of its blocks and their trades. A trade at the book is still sent at once; this is only for trades without a
 * player.
 */
final class ShopBroadcast {
    /** Ticks between two states sent to one team. */
    static final int PERIOD = 20;

    private final Set<UUID> changed = new LinkedHashSet<>();
    private final Map<UUID, Long> sentAt = new HashMap<>();

    /** The money or the counts of a team (or of a player alone) changed. */
    void mark(UUID team) {
        changed.add(team);
    }

    /** @return the teams whose state is to be sent at this tick: changed, and not sent within the last second */
    List<UUID> due(long tick) {
        if (changed.isEmpty()) {
            if (!sentAt.isEmpty()) {
                sentAt.values().removeIf(at -> tick - at >= PERIOD);
            }
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (Iterator<UUID> it = changed.iterator(); it.hasNext(); ) {
            UUID team = it.next();
            Long at = sentAt.get(team);
            if (at == null || tick - at >= PERIOD) {
                out.add(team);
                sentAt.put(team, tick);
                it.remove();
            }
        }
        return out;
    }
}
