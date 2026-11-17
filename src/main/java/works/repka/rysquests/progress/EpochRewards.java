package works.repka.rysquests.progress;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.task.CheckboxTask;

/**
 * The reward of an epoch (ADR-60): a quest the epoch names in {@code book.json}, shown and claimed at the epoch on the map rather
 * than as a card of its chapter. It stays an ordinary quest underneath, with its id, its progress and the rewards already taken, so
 * that nothing needs a migration and a reward is never given twice: the claim is the quest's own claim.
 *
 * <p>The epoch's quest of RepYS waits for the key quest of the epoch before it and has one task, the checkbox "Эпоха открыта".
 * Such ticks are made for the player when the reward is taken: the epoch is passed once the quest's dependencies are done.
 */
public final class EpochRewards {
    private EpochRewards() {
    }

    /** Where the reward of an epoch stands for the player. */
    public enum State {
        /** The epoch is not passed yet: the quest's dependencies, or a task that is not a tick, are still to do. */
        LOCKED,
        /** It can be taken now, or a part of it still waits (a choice, or items that did not fit). */
        READY,
        /** Everything is taken. */
        CLAIMED
    }

    /**
     * @param progress what the book shows the player of each quest: the team's record with the player's own claims
     */
    public static State state(Quest quest, Function<ResourceLocation, QuestProgress> progress) {
        QuestProgress own = progress.apply(quest.id());
        if (own.completed()) {
            return QuestLogic.allClaimed(quest, own) ? State.CLAIMED : State.READY;
        }
        if (own.coolingDown() || !QuestLogic.isUnlocked(quest, progress)) {
            return State.LOCKED;
        }
        return ticked(quest, own.tasks()).isPresent() ? State.READY : State.LOCKED;
    }

    /**
     * The task values with every unticked checkbox ticked, when that is all the quest still lacks; empty when another task is left
     * to do. A quest without tasks needs nothing.
     */
    public static Optional<List<Integer>> ticked(Quest quest, List<Integer> values) {
        List<Integer> out = new ArrayList<>(quest.tasks().size());
        for (int i = 0; i < quest.tasks().size(); i++) {
            int value = i < values.size() ? values.get(i) : 0;
            if (value >= quest.tasks().get(i).target()) {
                out.add(value);
            } else if (quest.tasks().get(i) instanceof CheckboxTask) {
                out.add(1);
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(out);
    }

    /** The rewards still to take, by index; nothing while the epoch is not passed. */
    public static List<Integer> waiting(Quest quest, Function<ResourceLocation, QuestProgress> progress) {
        if (state(quest, progress) != State.READY) {
            return List.of();
        }
        List<Integer> claimed = progress.apply(quest.id()).claimed();
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (!claimed.contains(i)) {
                left.add(i);
            }
        }
        return left;
    }
}
