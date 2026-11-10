package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** A custom section of the book: one file in {@code rysquests/sections/}, built from a template. Its id comes from the file's path. */
public sealed interface SectionDef permits MatrixSection, CardsSection, PagesSection {
    Codec<SectionDef> CODEC = Codec.STRING.partialDispatch("template", section -> DataResult.success(section.template()), SectionDef::codecOf);

    ResourceLocation id();

    String template();

    String title();

    /** The name on the rail, where a full title may not fit. */
    Optional<String> shortTitle();

    Optional<String> subtitle();

    Optional<ResourceLocation> icon();

    /** Every lang key this section can show: what travels with the book, and all that the editor may write. */
    Set<String> textKeys();

    /** The optional strings all sections share. */
    default Set<String> commonKeys() {
        Set<String> keys = new HashSet<>();
        keys.add(title());
        keys.add(title() + ".hint");
        shortTitle().ifPresent(keys::add);
        subtitle().ifPresent(keys::add);
        return keys;
    }

    private static DataResult<MapCodec<? extends SectionDef>> codecOf(String template) {
        return switch (template) {
            case MatrixSection.TEMPLATE -> DataResult.success(MatrixSection.CODEC);
            case CardsSection.TEMPLATE -> DataResult.success(CardsSection.CODEC);
            case PagesSection.TEMPLATE -> DataResult.success(PagesSection.CODEC);
            default -> DataResult.error(() -> "Unknown section template: " + template);
        };
    }
}
