package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Who gets a reward in a team: every member, or only the first to claim it. */
public enum TeamMode implements StringRepresentable {
    EACH, ONCE;

    public static final Codec<TeamMode> CODEC = StringRepresentable.fromEnum(TeamMode::values);
    public static final MapCodec<TeamMode> FIELD = CODEC.optionalFieldOf("team_mode", EACH);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
