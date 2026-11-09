package works.repka.rysquests.api;

import com.mojang.serialization.MapCodec;
import works.repka.rysquests.data.RewardDef;

/** A kind of quest reward, selected by the {@code type} field of its JSON. */
public interface RewardType<R extends RewardDef> {
    MapCodec<R> codec();
}
