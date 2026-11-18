package works.repka.rysquests.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * Book-wide settings, from {@code book.json}.
 *
 * @param epochs names of the epochs, the first for {@code epoch: 1}; a chapter's epoch past the end of the list has no name
 * @param mapForm the shape of the map, {@link MapForm#AUTO} by default
 * @param published whether players see the book; a book without the field is open, so that existing books stay open
 * @param about the pages "О книге" that the book's name in the header opens: a guide of the book, or pages of their own
 * @param preComplete whether the tasks of a locked quest count already, for chapters and quests that do not say (ADR-66)
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
        boolean published,
        Optional<About> about,
        boolean preComplete) {

    public record Hud(int maxPinned) {
        static final Hud DEFAULT = new Hud(3);
        static final Codec<Hud> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_pinned", DEFAULT.maxPinned).forGetter(Hud::maxPinned)
        ).apply(instance, Hud::new));
    }

    /**
     * One epoch of the book. Written as a plain string (its title) unless it was made by an import or names its reward.
     *
     * @param imported made by the FTB Quests import from a chapter group rather than by the author: the automatic map form does
     *                 not count such epochs (plan, section 5)
     * @param quest    the quest whose rewards are the epoch's reward: shown and claimed on the map at the epoch, never as a card of
     *                 its own chapter. Its id, progress and claimed rewards stay what they were (ADR-60)
     */
    public record Epoch(String title, boolean imported, Optional<ResourceLocation> quest) {
        private static final Codec<Epoch> FULL = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("title").forGetter(Epoch::title),
                Codec.BOOL.optionalFieldOf("imported", false).forGetter(Epoch::imported),
                ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(Epoch::quest)
        ).apply(instance, Epoch::new));
        public static final Codec<Epoch> CODEC = Codec.either(Codec.STRING, FULL).xmap(
                either -> either.map(title -> new Epoch(title, false), epoch -> epoch),
                epoch -> epoch.imported() || epoch.quest().isPresent() ? Either.right(epoch) : Either.left(epoch.title()));

        public Epoch(String title, boolean imported) {
            this(title, imported, Optional.empty());
        }

        public Epoch withTitle(String newTitle) {
            return new Epoch(newTitle, imported, quest);
        }
    }

    /**
     * The pages "О книге" (ADR-60): written as the id of a guide of the book ({@code "about": "repys:book"}), whose reading
     * progress and links stay the guide's, or as a list of pages in the format of a guide's pages.
     *
     * @param guide the guide shown, when the field names one
     * @param pages the book's own pages, when the field lists them
     */
    public record About(Optional<ResourceLocation> guide, List<Guide.Page> pages) {
        /** The id the book's own pages are read and remembered by, as if they were a guide. */
        public static final ResourceLocation OWN = ResourceLocation.fromNamespaceAndPath("rysquests", "about");
        /** The title of the book's own pages. */
        public static final String TITLE = "rysquests.about.title";

        public static final Codec<About> CODEC = Codec.either(ResourceLocation.CODEC, Guide.Page.CODEC.listOf()).xmap(
                either -> either.map(About::guide, About::pages),
                about -> about.guide().<Either<ResourceLocation, List<Guide.Page>>>map(Either::left).orElseGet(() -> Either.right(about.pages())));

        public static About guide(ResourceLocation id) {
            return new About(Optional.of(id), List.of());
        }

        public static About pages(List<Guide.Page> pages) {
            return new About(Optional.empty(), List.copyOf(pages));
        }

        /** The book's own pages as a guide, so that they are read with the guides' view. */
        public Guide ownGuide() {
            return new Guide(OWN, TITLE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), pages);
        }
    }

    public static final String GUIDES = "guides";
    public static final String SHOP = "shop";
    public static final String ACHIEVEMENTS = "achievements";

    private static final ResourceLocation SLATE = ResourceLocation.fromNamespaceAndPath("ryscore", "slate");

    public static final BookMeta DEFAULT = new BookMeta(ProgressMode.SOLO, ViewMode.FLOW, SLATE,
            Optional.empty(), Optional.empty(), List.of(), Hud.DEFAULT, List.of(), List.of(), MapForm.AUTO, true, Optional.empty(), false);

    /** Settings as they were before the early count (ADR-66): off. */
    public BookMeta(ProgressMode progressMode, ViewMode defaultView, ResourceLocation defaultTheme, Optional<String> title, Optional<ResourceLocation> icon,
            List<ResourceLocation> chapterOrder, Hud hud, List<String> sections, List<Epoch> epochs, MapForm mapForm, boolean published, Optional<About> about) {
        this(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, epochs, mapForm, published, about, false);
    }

    /** Settings as they were before epochs, map forms and the publication lock. */
    public BookMeta(ProgressMode progressMode, ViewMode defaultView, ResourceLocation defaultTheme, Optional<String> title, Optional<ResourceLocation> icon,
            List<ResourceLocation> chapterOrder, Hud hud, List<String> sections) {
        this(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, List.of(), MapForm.AUTO, true, Optional.empty());
    }

    /** Settings as they were before the pages "О книге". */
    public BookMeta(ProgressMode progressMode, ViewMode defaultView, ResourceLocation defaultTheme, Optional<String> title, Optional<ResourceLocation> icon,
            List<ResourceLocation> chapterOrder, Hud hud, List<String> sections, List<Epoch> epochs, MapForm mapForm, boolean published) {
        this(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, epochs, mapForm, published, Optional.empty());
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
            Codec.BOOL.optionalFieldOf("published", true).forGetter(BookMeta::published),
            About.CODEC.optionalFieldOf("about").forGetter(BookMeta::about),
            Codec.BOOL.optionalFieldOf("pre_complete", false).forGetter(BookMeta::preComplete)
    ).apply(instance, BookMeta::new));

    /** The epoch of a chapter by its number, if the book names it. */
    public Optional<Epoch> epoch(int number) {
        return number >= 1 && number <= epochs.size() ? Optional.of(epochs.get(number - 1)) : Optional.empty();
    }

    /** Whether a chapter's epoch was set by the author rather than made by an import. Epoch 0 and below means none. */
    public boolean authorEpoch(Optional<Integer> number) {
        return number.filter(n -> n >= 1).isPresent() && epoch(number.get()).map(epoch -> !epoch.imported()).orElse(true);
    }

    /** The quests that are rewards of epochs, each with its epoch's number from 1, in epoch order. The first epoch naming a quest wins. */
    public Map<ResourceLocation, Integer> epochRewards() {
        Map<ResourceLocation, Integer> rewards = new LinkedHashMap<>();
        for (int i = 0; i < epochs.size(); i++) {
            int number = i + 1;
            epochs.get(i).quest().ifPresent(quest -> rewards.putIfAbsent(quest, number));
        }
        return rewards;
    }

    /** The reward quest of an epoch by its number, if the epoch names one. */
    public Optional<ResourceLocation> epochReward(int number) {
        return epoch(number).flatMap(Epoch::quest);
    }

    public BookMeta withSections(List<String> newSections) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, List.copyOf(newSections), epochs, mapForm, published, about, preComplete);
    }

    public BookMeta withPublished(boolean open) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, epochs, mapForm, open, about, preComplete);
    }

    public BookMeta withMapForm(MapForm form) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, epochs, form, published, about, preComplete);
    }

    public BookMeta withEpochs(List<Epoch> newEpochs) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, List.copyOf(newEpochs), mapForm, published, about, preComplete);
    }

    public BookMeta withAbout(Optional<About> newAbout) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, epochs, mapForm, published, newAbout, preComplete);
    }

    /** The same settings under another name and icon: the editor's "Новая книга" and the name typed in the draft. */
    public BookMeta withTitleAndIcon(Optional<String> newTitle, Optional<ResourceLocation> newIcon) {
        return new BookMeta(progressMode, defaultView, defaultTheme, newTitle, newIcon, chapterOrder, hud, sections, epochs, mapForm, published, about, preComplete);
    }

    /** What a player is sent while the book is closed: its name and icon, nothing in it. */
    public BookMeta closedView() {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, List.of(), hud, List.of(), List.of(), mapForm, false, Optional.empty(), preComplete);
    }

    public BookMeta withPreComplete(boolean value) {
        return new BookMeta(progressMode, defaultView, defaultTheme, title, icon, chapterOrder, hud, sections, epochs, mapForm, published, about, value);
    }
}
