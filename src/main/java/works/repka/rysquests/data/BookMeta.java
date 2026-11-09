package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/** Book-wide settings, from {@code book.json}. */
public record BookMeta(
        ProgressMode progressMode,
        ViewMode defaultView,
        ResourceLocation defaultTheme,
        Optional<String> title,
        Optional<ResourceLocation> icon,
        List<ResourceLocation> chapterOrder,
        Hud hud) {

    public record Hud(int maxPinned) {
        static final Hud DEFAULT = new Hud(3);
        static final Codec<Hud> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_pinned", DEFAULT.maxPinned).forGetter(Hud::maxPinned)
        ).apply(instance, Hud::new));
    }

    private static final ResourceLocation SLATE = ResourceLocation.fromNamespaceAndPath("ryscore", "slate");

    public static final BookMeta DEFAULT = new BookMeta(ProgressMode.SOLO, ViewMode.FLOW, SLATE,
            Optional.empty(), Optional.empty(), List.of(), Hud.DEFAULT);

    public static final Codec<BookMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ProgressMode.CODEC.optionalFieldOf("progress_mode", ProgressMode.SOLO).forGetter(BookMeta::progressMode),
            ViewMode.CODEC.optionalFieldOf("default_view", ViewMode.FLOW).forGetter(BookMeta::defaultView),
            ResourceLocation.CODEC.optionalFieldOf("default_theme", SLATE).forGetter(BookMeta::defaultTheme),
            Codec.STRING.optionalFieldOf("title").forGetter(BookMeta::title),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(BookMeta::icon),
            ResourceLocation.CODEC.listOf().optionalFieldOf("chapter_order", List.of()).forGetter(BookMeta::chapterOrder),
            Hud.CODEC.optionalFieldOf("hud", Hud.DEFAULT).forGetter(BookMeta::hud)
    ).apply(instance, BookMeta::new));
}
