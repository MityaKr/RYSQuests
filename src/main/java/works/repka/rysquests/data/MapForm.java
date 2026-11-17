package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The shape of the book's map. {@link #AUTO} leaves the choice to {@link MapForms#choose}. */
public enum MapForm implements StringRepresentable {
    AUTO("auto"),
    /** Epochs one after another along a winding trunk. */
    SERPENTINE("serpentine"),
    /** Everything leads in towards one finale in the middle. */
    SPIRAL("spiral"),
    /** Chapters placed by the generic graph layout. */
    GENERATOR("generator"),
    /** A main line in the middle and the sections of the book around it, for books with few links between chapters. */
    GALAXY("galaxy");

    public static final Codec<MapForm> CODEC = StringRepresentable.fromValues(MapForm::values);

    private final String name;

    MapForm(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
