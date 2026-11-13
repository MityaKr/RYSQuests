package works.repka.rysquests.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * Book-wide settings, from {@code book.json}.
 *
 * @param epochs names of the epochs, the first for {@code epoch: 1}; a chapter's epoch past the end of the list has no name
 * @param mapForm the shape of the map, {@link MapForm#AUTO} by default
 * @param published whether players see the book; a book without the field is open, so that existing books stay open
 */
public record BookMeta(
        ProgressMode progressMode,
        ViewMode defaultView,
        ResourceLocation defaultTheme,
        Optional<String> title,
        Optional<ResourceLocation> icon,
        List<ResourceLocation> chapterOrder,
        Hud hud,
        List<String> sections,
        List<Epoch> epochs,
        MapForm mapForm,
        boolean published) {

    public record Hud(int maxPinned) {
        static final Hud DEFAULT = new Hud(3);
        static final Codec<Hud> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_pinned", DEFAULT.maxPinned).forGetter(Hud::maxPinned)
        ).apply(instance, Hud::new));
    }

    /**
     * One epoch of the book. Written as a plain string (its title) unless it was made by an import.
     *
     * @param imported made by the FTB Quests import from a chapter group rather than by the author: the automatic map form does
     *                 not count such epochs (plan, section 5)
     */
    public record Epoch(String title, boolean imported) {
        private static final Codec<Epoch> FULL = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("title").forGetter(Epoch::title),
                Codec.BOOL.optionalFieldOf("imported", false).forGetter(Epoch::imported)
        ).apply(instance, Epoch::new));
        public static final Codec<Epoch> CODEC = Codec.either(Codec.STRING, FULL).xmap(
                either -> either.map(title -> new Epoch(title, false), epoch -> epoch),
                epoch -> epoch.imported() ? Either.right(epoch) : Either.left(epoch.title()));
    }

    public static final String GUIDES = "guides";
    public static final String SHOP = "shop";
    public static final String ACHIEVEMENTS = "achievements";

    private static final ResourceLocation SLATE = ResourceLocation.fromNamespaceAndPath("ryscore", "slate");

    public static final BookMeta DEFAULT = new BookMeta(ProgressMode.SOLO, ViewMode.FLOW, SLATE,
            Optional.empty(), Optional.empty(), List.of(), Hud.DEFAULT, List.of(), List.of(), MapForm.AUTO, true);

    /** Settings as they were before epochs, map forms and the publication lock. */
    public BookMeta(ProgressMode progressMode, ViewMode defaultView, ResourceLocation defaultTheme, Optional<String> title, Optional<ResourceLocation> icon,
            List<ResourceLocation> chapterOrder, Hud hud, List<String> sections) {
        this(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, List.of(), MapForm.AUTO, true);
    }

    public static final Codec<BookMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ProgressMode.CODEC.optionalFieldOf("progress_mode", ProgressMode.SOLO).forGetter(BookMeta::progressMode),
            ViewMode.CODEC.optionalFieldOf("default_view", ViewMode.FLOW).forGetter(BookMeta::defaultView),
            ResourceLocation.CODEC.optionalFieldOf("default_theme", SLATE).forGetter(BookMeta::defaultTheme),
            Codec.STRING.optionalFieldOf("title").forGetter(BookMeta::title),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(BookMeta::icon),
            ResourceLocation.CODEC.listOf().optionalFieldOf("chapter_order", List.of()).forGetter(BookMeta::chapterOrder),
            Hud.CODEC.optionalFieldOf("hud", Hud.DEFAULT).forGetter(BookMeta::hud),
            Codec.STRING.listOf().optionalFieldOf("sections", List.of()).forGetter(BookMeta::sections),
            Epoch.CODEC.listOf().optionalFieldOf("epochs", List.of()).forGetter(BookMeta::epochs),
            MapForm.CODEC.optionalFieldOf("map_form", MapForm.AUTO).forGetter(BookMeta::mapForm),
            Codec.BOOL.optionalFieldOf("published", true).forGetter(BookMeta::published)
    ).apply(instance, BookMeta::new));

    /** The epoch of a chapter by its number, if the book names it. */
    public Optional<Epoch> epoch(int number) {
        return number >= 1 && number <= epochs.size() ? Optional.of(epochs.get(number - 1)) : Optional.empty();
    }

    /** Whether a chapter's epoch was set by the author rather than made by an import. Epoch 0 and below means none. */
    public boolean authorEpoch(Optional<Integer> number) {
        return number.filter(n -> n >= 1).isPresent() && epoch(number.get()).map(epoch -> !epoch.imported()).orElse(true);
    }

    public BookMeta withSections(List<String> newSections) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, List.copyOf(newSections), epochs, mapForm, published);
    }

    public BookMeta withPublished(boolean open) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, epochs, mapForm, open);
    }

    public BookMeta withMapForm(MapForm form) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, epochs, form, published);
    }

    public BookMeta withEpochs(List<Epoch> newEpochs) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, List.copyOf(newEpochs), mapForm, published);
    }

    /** What a player is sent while the book is closed: its name and icon, nothing in it. */
    public BookMeta closedView() {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, List.of(), hud, List.of(), List.of(), mapForm, false);
    }
}
