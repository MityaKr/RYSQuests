package works.repka.rysquests.client.screen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import net.neoforged.neoforge.network.PacketDistributor;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.GuideRef;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestStatus;

/** What the book shows. Static, so that it survives closing the book and coming back from the recipe window. */
final class BookState {
    @Nullable
    static ResourceLocation chapter;
    @Nullable
    static ResourceLocation quest;
    @Nullable
    static ViewMode view;
    static String search = "";
    /** The open section of the book, or null for the first one. */
    @Nullable
    static String section;
    /** The guide selected in the library, or open. */
    @Nullable
    static ResourceLocation guide;
    /** The open page of {@link #guide}, or -1 for the library. */
    static int guidePage = -1;
    /** The search, the filter chip and the order of the section on screen. */
    static String sectionSearch = "";
    static String sectionCategory = "";
    static boolean sectionByName;
    /** The open page of each section on the pages template. */
    static final Map<ResourceLocation, Integer> SECTION_PAGES = new HashMap<>();
    static final Map<ResourceLocation, MatrixPick> MATRIX_PICKS = new HashMap<>();
    static final Map<ResourceLocation, String> CARD_PICKS = new HashMap<>();

    /** The selected cell of a matrix and the way chosen in it. */
    record MatrixPick(String row, String column, int way) {
    }
    static boolean showDone;
    static boolean showRest;
    /** Picks of choice rewards by reward index, for the selected quest. */
    static final Map<Integer, Integer> PICKS = new HashMap<>();
    static final Set<String> REVEALED = new HashSet<>();

    private BookState() {
    }

    static void select(Quest selected) {
        if (!selected.id().equals(quest)) {
            PICKS.clear();
        }
        quest = selected.id();
        section = Chapter.STORY;
        selected.chapter().ifPresent(id -> chapter = id);
    }

    /** The sections on the rail: those the book names and has something to show in. */
    static List<String> sections() {
        QuestBook book = ClientBook.current();
        return book.meta().sections().stream().distinct().filter(id -> hasContent(book, id)).toList();
    }

    private static boolean hasContent(QuestBook book, String id) {
        return switch (id) {
            case Chapter.STORY -> book.chapters().values().stream().anyMatch(chapter -> chapter.isStory() && !chapter.hidden());
            case BookMeta.GUIDES -> !book.guides().isEmpty();
            case BookMeta.SHOP -> !book.offers().isEmpty();
            case BookMeta.ACHIEVEMENTS -> !book.achievements().isEmpty();
            default -> ResourceLocation.tryParse(id) != null && book.sections().containsKey(ResourceLocation.parse(id));
        };
    }

    static String currentSection() {
        QuestBook book = ClientBook.current();
        if (book.meta().sections().isEmpty()) {
            return Chapter.STORY;
        }
        // A section that is not on the rail can still be open: a card's page leads to one.
        if (section != null && hasContent(book, section)) {
            return section;
        }
        List<String> sections = sections();
        return sections.isEmpty() ? Chapter.STORY : sections.getFirst();
    }

    /** The unsaved section the editor shows through the player's eyes. */
    @Nullable
    static SectionDef previewSection;

    /** The custom section on screen, or null in the story and the guides. */
    @Nullable
    static SectionDef customSection() {
        if (previewSection != null) {
            return previewSection;
        }
        String current = currentSection();
        ResourceLocation id = current.equals(Chapter.STORY) || current.equals(BookMeta.GUIDES) || current.equals(BookMeta.SHOP) || current.equals(BookMeta.ACHIEVEMENTS) ? null : ResourceLocation.tryParse(current);
        return id == null ? null : ClientBook.current().sections().get(id);
    }

    static boolean inShop() {
        return currentSection().equals(BookMeta.SHOP);
    }

    static boolean inAchievements() {
        return currentSection().equals(BookMeta.ACHIEVEMENTS);
    }

    static boolean inStory() {
        return currentSection().equals(Chapter.STORY);
    }

    static boolean inGuides() {
        return currentSection().equals(BookMeta.GUIDES);
    }

    static void openSection(String id) {
        section = id;
        guidePage = -1;
        sectionSearch = "";
        sectionCategory = "";
    }

    static void openLibrary() {
        openSection(BookMeta.GUIDES);
    }

    /** Opens a page. Opening it is what counts as reading it. */
    static void openGuide(Guide target, int page) {
        section = BookMeta.GUIDES;
        guide = target.id();
        guidePage = page;
        if (page >= 0 && page < target.pages().size() && !ClientProgress.isRead(target, target.pages().get(page))) {
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.READ_GUIDE_PAGE, target.id(), page, List.of()));
        }
    }

    /** Follows a link to a guide. A closed guide only gets selected in the library, which says why it is closed. */
    static void openRef(GuideRef ref) {
        Guide target = ClientBook.current().guides().get(ref.guide());
        if (target == null) {
            if (ClientBook.current().sections().get(ref.guide()) instanceof PagesSection pages) {
                openSection(pages.id().toString());
                SECTION_PAGES.put(pages.id(), Math.max(0, ref.page().map(pages.asGuide()::pageIndex).orElse(0)));
            }
            return;
        }
        if (ClientProgress.isGuideLocked(target.id())) {
            guide = target.id();
            openLibrary();
            return;
        }
        openGuide(target, ref.page().map(target::pageIndex).filter(index -> index >= 0).orElse(firstUnread(target)));
    }

    static int firstUnread(Guide target) {
        for (int i = 0; i < target.pages().size(); i++) {
            if (!ClientProgress.isRead(target, target.pages().get(i))) {
                return i;
            }
        }
        return 0;
    }

    static void openChapter(ResourceLocation id) {
        chapter = id;
        showDone = false;
        showRest = false;
    }

    static ViewMode currentView() {
        if (view != null) {
            return view;
        }
        var open = chapter == null ? null : ClientBook.current().chapters().get(chapter);
        return open == null ? ClientBook.current().meta().defaultView() : open.defaultView().orElse(ClientBook.current().meta().defaultView());
    }

    static QuestStatus status(Quest target) {
        QuestStatus status = QuestLogic.status(target, ClientProgress::get);
        boolean open = status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
        return open && ClientProgress.isStageLocked(target.id()) ? QuestStatus.LOCKED : status;
    }
}
