package works.repka.rysquests.progress;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.Nullable;

/**
 * What has been delivered towards a goal that is paid in parts (shop plan, step M4): per owner (a team, or a player alone), per goal
 * (a delivery trade of the shop, later perhaps a trial), per item, how many. The goal does not have to be a shop trade.
 *
 * <p>Items only ever move between owners, they are never copied: a player joining a team brings what they delivered alone, a player
 * leaving takes nothing, a merge moves one team's into the other's and its undo gives back no more than is still there. So no order
 * of leaving, delivering and coming back delivers one item twice (the kind of hole the reward duplication of 0.2.4 was).
 */
public final class DeliveryLedger {
    static final Codec<Map<UUID, Map<String, Map<String, Long>>>> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC,
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.LONG)));

    private final Map<UUID, Map<String, Map<String, Long>>> owners = new HashMap<>();
    private final Runnable changed;

    DeliveryLedger(Runnable changed) {
        this.changed = changed;
    }

    void load(Map<UUID, Map<String, Map<String, Long>>> saved) {
        owners.clear();
        saved.forEach((owner, goals) -> goals.forEach((goal, items) -> items.forEach((item, count) -> {
            if (count > 0) {
                row(owner, goal).put(item, count);
            }
        })));
    }

    Map<UUID, Map<String, Map<String, Long>>> saved() {
        return owners;
    }

    private Map<String, Long> row(UUID owner, String goal) {
        return owners.computeIfAbsent(owner, key -> new HashMap<>()).computeIfAbsent(goal, key -> new HashMap<>());
    }

    /** What an owner has delivered towards a goal, by item. */
    public Map<String, Long> get(UUID owner, String goal) {
        Map<String, Map<String, Long>> goals = owners.get(owner);
        Map<String, Long> items = goals == null ? null : goals.get(goal);
        return items == null ? Map.of() : Map.copyOf(items);
    }

    /** Every goal an owner has something towards. */
    public Map<String, Map<String, Long>> all(UUID owner) {
        Map<String, Map<String, Long>> goals = owners.get(owner);
        if (goals == null) {
            return Map.of();
        }
        Map<String, Map<String, Long>> out = new HashMap<>();
        goals.forEach((goal, items) -> {
            if (!items.isEmpty()) {
                out.put(goal, Map.copyOf(items));
            }
        });
        return out;
    }

    public long count(UUID owner, String goal, String item) {
        return get(owner, goal).getOrDefault(item, 0L);
    }

    public void add(UUID owner, String goal, String item, long amount) {
        if (amount <= 0) {
            return;
        }
        row(owner, goal).merge(item, amount, Long::sum);
        changed.run();
    }

    /**
     * Takes one round of a goal off what was delivered: what was given beyond it (brought by a merge or a joining player) stays for
     * the next round. @return false, changing nothing, when something is short
     */
    public boolean take(UUID owner, String goal, Map<String, Long> round) {
        Map<String, Long> have = get(owner, goal);
        for (Map.Entry<String, Long> need : round.entrySet()) {
            if (have.getOrDefault(need.getKey(), 0L) < need.getValue()) {
                return false;
            }
        }
        Map<String, Long> row = row(owner, goal);
        round.forEach((item, amount) -> subtract(row, item, amount));
        tidy(owner);
        changed.run();
        return true;
    }

    private static void subtract(Map<String, Long> row, String item, long amount) {
        long left = row.getOrDefault(item, 0L) - amount;
        if (left > 0) {
            row.put(item, left);
        } else {
            row.remove(item);
        }
    }

    private void tidy(UUID owner) {
        Map<String, Map<String, Long>> goals = owners.get(owner);
        if (goals != null) {
            goals.values().removeIf(Map::isEmpty);
            if (goals.isEmpty()) {
                owners.remove(owner);
            }
        }
    }

    /** Moves everything one owner has into another's, adding up: a player joining a team, or a team merged into another. */
    public void move(UUID from, UUID into) {
        if (from.equals(into)) {
            return;
        }
        Map<String, Map<String, Long>> goals = owners.remove(from);
        if (goals == null) {
            return;
        }
        goals.forEach((goal, items) -> items.forEach((item, count) -> row(into, goal).merge(item, count, Long::sum)));
        changed.run();
    }

    /** A copy of all an owner has, to hand to {@link #giveBack} if a merge is undone. */
    public Map<String, Map<String, Long>> copy(UUID owner) {
        return all(owner);
    }

    /**
     * A merge is undone: the team that was merged gets back what it had before, but never more than the other still holds of it. What
     * was used up meanwhile (a trophy taken) is gone for both, so nothing comes out twice.
     */
    public void giveBack(UUID into, UUID from, Map<String, Map<String, Long>> before) {
        if (from.equals(into)) {
            return;
        }
        boolean any = false;
        for (Map.Entry<String, Map<String, Long>> goal : before.entrySet()) {
            for (Map.Entry<String, Long> item : goal.getValue().entrySet()) {
                long back = Math.min(item.getValue(), count(into, goal.getKey(), item.getKey()));
                if (back > 0) {
                    subtract(row(into, goal.getKey()), item.getKey(), back);
                    row(from, goal.getKey()).merge(item.getKey(), back, Long::sum);
                    any = true;
                }
            }
        }
        tidy(into);
        tidy(from);
        if (any) {
            changed.run();
        }
    }

    /** Forgets what an owner delivered. @param goal one goal, or null for all of them */
    public void clear(UUID owner, @Nullable String goal) {
        Map<String, Map<String, Long>> goals = owners.get(owner);
        if (goals == null) {
            return;
        }
        if (goal == null) {
            owners.remove(owner);
        } else if (goals.remove(goal) == null) {
            return;
        }
        tidy(owner);
        changed.run();
    }
}
