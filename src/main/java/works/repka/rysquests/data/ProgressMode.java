package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Who shares quest progress. */
public enum ProgressMode implements StringRepresentable {
    SOLO("solo"),
    TEAM("team"),
    SERVER("server");

    public static final Codec<ProgressMode> CODEC = StringRepresentable.fromValues(ProgressMode::values);

    private final String name;

    ProgressMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
