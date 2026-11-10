package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** A guide: a title, an icon and pages of text. One file in {@code rysquests/guides/}. Texts are lang keys. */
public record Guide(
        ResourceLocation id,
        String title,
        Optional<ResourceLocation> icon,
        Optional<String> category,
        Optional<String> stage,
        Optional<String> stageTitle,
        List<Page> pages) {

    /** @param id stable within the guide: reading progress is kept by it, so pages can be reordered */
    public record Page(String id, String title, String text) {
        public static final Codec<Page> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Page::id),
                Codec.STRING.fieldOf("title").forGetter(Page::title),
                Codec.STRING.fieldOf("text").forGetter(Page::text)
        ).apply(instance, Page::new));
    }

    public static final Codec<Guide> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Guide::id),
            Codec.STRING.fieldOf("title").forGetter(Guide::title),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(Guide::icon),
            Codec.STRING.optionalFieldOf("category").forGetter(Guide::category),
            Codec.STRING.optionalFieldOf("stage").forGetter(Guide::stage),
            Codec.STRING.optionalFieldOf("stage_title").forGetter(Guide::stageTitle),
            Page.CODEC.listOf().fieldOf("pages").forGetter(Guide::pages)
    ).apply(instance, Guide::new));

    /** @return the page's position, or -1 */
    public int pageIndex(String pageId) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).id().equals(pageId)) {
                return i;
            }
        }
        return -1;
    }

    /** How a read page is remembered. */
    public String readKey(Page page) {
        return id + "#" + page.id();
    }
}
