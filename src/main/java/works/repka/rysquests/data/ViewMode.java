package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Which view of the book a chapter opens in. */
public enum ViewMode implements StringRepresentable {
    HOME("home"),
    FLOW("flow"),
    MAP("map"),
    LIST("list");

    public static final Codec<ViewMode> CODEC = StringRepresentable.fromValues(ViewMode::values);

    private final String name;

    ViewMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
