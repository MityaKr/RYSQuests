package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** The "pages" template: a book of pages in the format of guides, without a library. */
public record PagesSection(
        ResourceLocation id,
        String title,
        Optional<String> shortTitle,
        Optional<String> subtitle,
        Optional<ResourceLocation> icon,
        List<Guide.Page> pages) implements SectionDef {
    public static final String TEMPLATE = "pages";

    public static final MapCodec<PagesSection> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(PagesSection::id),
            Codec.STRING.fieldOf("title").forGetter(PagesSection::title),
            Codec.STRING.optionalFieldOf("short_title").forGetter(PagesSection::shortTitle),
            Codec.STRING.optionalFieldOf("subtitle").forGetter(PagesSection::subtitle),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(PagesSection::icon),
            Guide.Page.CODEC.listOf().fieldOf("pages").forGetter(PagesSection::pages)
    ).apply(instance, PagesSection::new));

    @Override
    public String template() {
        return TEMPLATE;
    }

    @Override
    public Set<String> textKeys() {
        Set<String> keys = commonKeys();
        pages.forEach(page -> {
            keys.add(page.title());
            keys.add(page.text());
        });
        return keys;
    }

    /** The same pages as a guide, so that both are read with one view. */
    public Guide asGuide() {
        return new Guide(id, title, icon, Optional.empty(), Optional.empty(), Optional.empty(), pages);
    }
}
