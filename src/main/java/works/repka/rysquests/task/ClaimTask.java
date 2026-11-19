package works.repka.rysquests.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/**
 * Completed when the player's team holds a number of chunks (RYSClaims, RYSCore {@code claims.ClaimsApi.claimed}; plan of the map,
 * section 8, RYSQuests). The value only grows: chunks released later do not take the task back. Without RYSClaims no team holds land,
 * and the task waits.
 */
public record ClaimTask(int count, Optional<String> title) implements TaskDef {
    public static final MapCodec<ClaimTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ClaimTask::count),
            Codec.STRING.optionalFieldOf("title").forGetter(ClaimTask::title)
    ).apply(instance, ClaimTask::new));

    @Override
    public int target() {
        return count;
    }

    @Override
    public TaskType<?> type() {
        return TaskTypes.CLAIMS.get();
    }
}
