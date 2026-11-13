package works.repka.rysquests.client.screen;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.data.ViewMode;

/**
 * The tabs of the book, in the order of the header: Now, Map, Guides, Shop, Achievements (plan, section 2). This is the one place
 * where a tab gets its view: to put a new screen into a tab, change the {@code view} of its line and nothing else.
 *
 * <p>Which tab is open is not stored here: it follows from {@link BookState} (the open section and view), so the old views that
 * set {@code BookState.section} or {@code BookState.view} keep switching tabs as before.
 */
final class BookTabs {
    static final String NOW = "now";
    static final String MAP = "map";
    static final String GUIDES = "guides";
    static final String SHOP = "shop";
    static final String ACHIEVEMENTS = "achievements";

    /**
     * One tab.
     *
     * @param id         stable name, also the end of its lang key {@code rysquests.tab.<id>}
     * @param hasContent whether the book has anything to show there: a player never sees a tab without it
     * @param badge      the number on the tab, 0 for none; only "Now" has one (rewards waiting)
     * @param view       makes the tab's view, once per open book
     * @param isOpen     whether the book state says this tab is the open one
     * @param open       switches the book state to this tab
     */
    record Tab(String id, Predicate<QuestBook> hasContent, IntSupplier badge, Function<BookScreen, BookView> view, BooleanSupplier isOpen, Runnable open) {
        String label() {
            return Component.translatable("rysquests.tab." + id).getString();
        }
    }

    static final List<Tab> ALL = List.of(
            new Tab(NOW, BookTabs::hasStory, BookTallies::readyToClaim,
                    NowView::new,
                    () -> BookState.inStory() && !BookTabs.mapView(BookState.currentView()), () -> BookTabs.openStory(ViewMode.HOME)),
            new Tab(MAP, BookTabs::hasStory, () -> 0,
                    MapTab::new,
                    () -> BookState.inStory() && BookTabs.mapView(BookState.currentView()), () -> BookTabs.openStory(BookState.mapMode)),
            new Tab(GUIDES, BookTabs::hasGuides, () -> 0,
                    GuidesTab::new,
                    () -> BookState.inGuides() || BookState.customSection() != null, BookTabs::openGuides),
            new Tab(SHOP, book -> allowed(book, BookMeta.SHOP) && !book.offers().isEmpty(), () -> 0,
                    screen -> new ShopView(),
                    BookState::inShop, () -> BookState.openSection(BookMeta.SHOP)),
            new Tab(ACHIEVEMENTS, book -> allowed(book, BookMeta.ACHIEVEMENTS) && !book.achievements().isEmpty(), () -> 0,
                    screen -> new AchievementsView(),
                    BookState::inAchievements, () -> BookState.openSection(BookMeta.ACHIEVEMENTS)));

    private BookTabs() {
    }

    /** The tabs a player sees: those with something in them. */
    static List<Tab> visible(QuestBook book) {
        return ALL.stream().filter(tab -> tab.hasContent().test(book)).toList();
    }

    /** The open tab; when the state points at a tab that is not there, the first one there is opened. Null for an empty book. */
    static Tab current(QuestBook book) {
        List<Tab> shown = visible(book);
        for (Tab tab : shown) {
            if (tab.isOpen().getAsBoolean()) {
                return tab;
            }
        }
        if (shown.isEmpty()) {
            return null;
        }
        shown.getFirst().open().run();
        return shown.getFirst();
    }

    static Tab byId(String id) {
        return ALL.stream().filter(tab -> tab.id().equals(id)).findFirst().orElseThrow();
    }

    /** The views of the story that live in the "Map" tab. */
    static boolean mapView(ViewMode view) {
        return view == ViewMode.MAP || view == ViewMode.LIST;
    }

    private static void openStory(ViewMode view) {
        BookState.section = Chapter.STORY;
        BookState.view = view;
    }

    private static void openGuides() {
        QuestBook book = BookData.book();
        if (!book.guides().isEmpty()) {
            BookState.openLibrary();
        } else {
            authorSections(book).stream().findFirst().ifPresent(section -> BookState.openSection(section.id().toString()));
        }
    }

    /** Whether the book's list of sections lets this one in. A book that lists none lets everything in. */
    static boolean allowed(QuestBook book, String section) {
        List<String> listed = book.meta().sections();
        return listed.isEmpty() || listed.contains(section);
    }

    private static boolean hasStory(QuestBook book) {
        return allowed(book, Chapter.STORY) && book.chapters().values().stream().anyMatch(chapter -> chapter.isStory() && !chapter.hidden());
    }

    private static boolean hasGuides(QuestBook book) {
        return allowed(book, BookMeta.GUIDES) && !book.guides().isEmpty() || !authorSections(book).isEmpty();
    }

    /**
     * The author's own tabs (cards, pages, matrices), which live inside "Guides" as a group of their own (plan, section 2). Matrices
     * are for the author: a player does not see them (plan, section 8).
     */
    static List<SectionDef> authorSections(QuestBook book) {
        boolean operator = BookRole.operator();
        return book.sections().values().stream()
                .filter(section -> allowed(book, section.id().toString()))
                .filter(section -> operator || !(section instanceof MatrixSection))
                .toList();
    }
}
