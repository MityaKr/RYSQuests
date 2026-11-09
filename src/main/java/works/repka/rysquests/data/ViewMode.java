package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Which view of the book is open. */
public enum ViewMode implements StringRepresentable {
    HOME("home"),
    FLOW("flow"),
    MAP("map"),
    LIST("list"),
    TIMELINE("timeline"),
    DEV("dev");

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
