package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/**
 * Experience, in levels or in points, as FTB Quests' and Heracles' experience tasks. With {@code consume} the player
 * hands it in with the submit button, otherwise having it is enough.
 */
public record XpTask(Optional<Integer> levels, Optional<Integer> pointsAmount, boolean consume, Optional<String> title) implements TaskDef {
    public static final MapCodec<XpTask> CODEC = RecordCodecBuilder.<XpTask>mapCodec(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("levels").forGetter(XpTask::levels),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("points").forGetter(XpTask::pointsAmount),
            Codec.BOOL.optionalFieldOf("consume", false).forGetter(XpTask::consume),
            Codec.STRING.optionalFieldOf("title").forGetter(XpTask::title)
    ).apply(instance, XpTask::new)).validate(XpTask::checkAmount);

    private static DataResult<XpTask> checkAmount(XpTask task) {
        return task.levels.isPresent() == task.pointsAmount.isPresent()
                ? DataResult.error(() -> "an experience task needs either levels or points")
                : DataResult.success(task);
    }

    /** Whether the amount is in points rather than levels. */
    public boolean points() {
        return pointsAmount.isPresent();
    }

    public int amount() {
        return levels.orElseGet(() -> pointsAmount.orElse(1));
    }

    /** What the player has in this task's unit. Points are counted from the level and the bar, as spending levels lowers them. */
    public int have(net.minecraft.world.entity.player.Player player) {
        if (!points()) {
            return player.experienceLevel;
        }
        return pointsAtLevel(player.experienceLevel) + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    /** The points it takes to reach a level from zero, by the game's own formula. */
    static int pointsAtLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    @Override
    public int target() {
        return amount();
    }

    @Override
    public TaskType<?> type() {
        return TaskTypes.XP.get();
    }
}
