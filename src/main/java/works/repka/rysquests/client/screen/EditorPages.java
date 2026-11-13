package works.repka.rysquests.client.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Kit;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.QuestBook;

/**
 * The editor inside the book (plan, section 6): one mode with the author's tabs Карта · Гайды · Магазин · Достижения · Вкладки · Проверка,
 * entered and left by the one "Редактор" toggle of the header. Every page of it is an {@link EditorPage}, so the header shows
 * "Сохранить · N", undo and "Открыть игрокам" by itself. Empty tabs are grey with "+" (picture 10).
 */
final class EditorPages {
    static final String MAP = "map";
    static final String GUIDES = "guides";
    static final String SHOP = "shop";
    static final String ACHIEVEMENTS = "achievements";
    static final String TABS = "tabs";
    static final String CHECK = "check";

    /** The open tab of the editor, kept between openings of the book. */
    static String current = MAP;

    private final BookScreen screen;
    private final Map<String, EditorPage> pages = new HashMap<>();
    private final List<BookTabs.Tab> tabs;

    EditorPages(BookScreen screen) {
        this.screen = screen;
        tabs = List.of(
                tab(MAP, book -> true),
                tab(GUIDES, book -> !book.guides().isEmpty()),
                tab(SHOP, book -> !book.offers().isEmpty()),
                tab(ACHIEVEMENTS, book -> !book.achievements().isEmpty()),
                tab(TABS, book -> true),
                tab(CHECK, book -> true));
    }

    private BookTabs.Tab tab(String id, java.util.function.Predicate<QuestBook> content) {
        return new BookTabs.Tab(id, book -> content.test(EditorBook.get().data()), () -> 0, s -> page(id), () -> current.equals(id), () -> current = id);
    }

    /** Whether the book shows the editor. Only an operator ever does. */
    boolean on() {
        return EditorBook.active && BookRole.operator();
    }

    List<BookTabs.Tab> tabs() {
        return tabs;
    }

    BookTabs.Tab activeTab() {
        return tabs.stream().filter(tab -> tab.id().equals(current)).findFirst().orElse(tabs.getFirst());
    }

    EditorPage page(String id) {
        return pages.computeIfAbsent(id, key -> switch (key) {
            case GUIDES -> new GuideEditorView(screen);
            case SHOP -> new ShopEditorView(screen);
            case ACHIEVEMENTS -> new AchievementEditorView(screen);
            case TABS -> new TabsEditorView(screen);
            case CHECK -> new CheckView(screen);
            default -> new EditorMapView(screen);
        });
    }

    EditorPage view() {
        return page(current);
    }

    EditorMapView map() {
        return (EditorMapView) page(MAP);
    }

    void closed() {
        pages.values().forEach(EditorPage::closed);
    }

    /** "Редактор": in from the tab the player was on, out to the same place. */
    void toggle(@Nullable BookTabs.Tab playerTab) {
        if (EditorBook.active) {
            EditorBook.active = false;
            EditorSidebar.lookedAsPlayer = true;
            BookTabs.byId(switch (current) {
                case GUIDES, TABS -> BookTabs.GUIDES;
                case SHOP -> BookTabs.SHOP;
                case ACHIEVEMENTS -> BookTabs.ACHIEVEMENTS;
                default -> BookTabs.MAP;
            }).open().run();
            return;
        }
        EditorBook.active = true;
        String from = playerTab == null ? BookTabs.MAP : playerTab.id();
        current = switch (from) {
            case BookTabs.GUIDES -> GUIDES;
            case BookTabs.SHOP -> SHOP;
            case BookTabs.ACHIEVEMENTS -> ACHIEVEMENTS;
            default -> MAP;
        };
        askRestore();
    }

    /** A draft from disk that the author has not seen since the game closed: offer it back (plan, section 6). */
    void askRestore() {
        EditorBook book = EditorBook.get();
        if (!book.restoredFromDisk()) {
            return;
        }
        int n = book.unsaved();
        screen.ask(new Kit.Dialog(Ui.counted("rysquests.ed.restore.title", n))
                .text(EditorBook.tr("rysquests.ed.restore.text"))
                .body(Math.min(5, n) * 9 + 2, (g, x, y, w, mx, my) -> EditorPage.drawLines(g, book.lines(), x, y, w))
                .no(EditorBook.tr("rysquests.ed.restore.drop"), screen.answer(book::discard))
                .yes(EditorBook.tr("rysquests.ed.restore.keep"), screen.answer(book::keepRestored))
                .width(230));
    }

    /**
     * The question of picture 33b: the book closes with edits in the draft. "Сохранить" saves and then closes, "Не сохранять" throws the
     * draft away, "Остаться" keeps the book open.
     *
     * @return true when the question was asked and the book stays open for now
     */
    boolean askBeforeClose(Runnable close) {
        EditorBook book = EditorBook.get();
        if (!on() || book.unsaved() == 0) {
            return false;
        }
        int n = book.unsaved();
        List<String> lines = book.lines();
        List<String> last = lines.subList(Math.max(0, lines.size() - 5), lines.size());
        screen.ask(new Kit.Dialog(Ui.counted("rysquests.ed.close.title", n))
                .text(EditorBook.tr("rysquests.ed.close.text"))
                .body(last.size() * 9 + 2, (g, x, y, w, mx, my) -> EditorPage.drawLines(g, last, x, y, w))
                .no(EditorBook.tr("rysquests.ed.close.stay"), screen.answer(() -> { }))
                .button(EditorBook.tr("rysquests.ed.close.drop"), screen.answer(() -> {
                    book.discard();
                    close.run();
                }))
                .yes(EditorBook.tr("rysquests.ed.close.save"), screen.answer(() -> book.save(screen, close)))
                .width(230));
        return true;
    }

    /**
     * A new book from scratch, after the question of picture 09a (the first-run screens ask it): the title and the form go into the
     * draft, one chapter without an epoch is made (with epoch I only for "по порядку"), and the editor opens on its table (picture 09).
     *
     * @param title  the book's name as typed
     * @param form   the form the answer chose, {@link MapForm#AUTO} for "пока не знаю"
     * @param epochs whether the answer was "по порядку: этап за этапом", the only one that makes an epoch
     */
    void startNewBook(String title, MapForm form, boolean epochs) {
        EditorBook book = EditorBook.get();
        screen.closePage();
        EditorBook.active = true;
        current = MAP;
        String namespace = EditorBook.namespace(book.data());
        String titleKey = "book." + namespace + ".title";
        works.repka.rysquests.data.BookMeta base = book.draft().meta().orElse(book.base().meta());
        BookMeta named = new BookMeta(base.progressMode(), base.defaultView(), base.defaultTheme(), Optional.of(titleKey), base.icon(), base.chapterOrder(),
                base.hud(), base.sections(), epochs ? List.of(new BookMeta.Epoch(EditorBook.tr("rysquests.ed.new_book.epoch"), false)) : base.epochs(), form, false);
        book.edit("meta", EditorBook.tr("rysquests.ed.log.book_title", title), d -> d.withMeta(named).withText(titleKey, title));
        var chapter = book.addChapter(EditorBook.tr("rysquests.ed.new_book.chapter"), epochs ? Optional.of(1) : Optional.empty());
        map().openTable(chapter);
    }

    // --- the pencils of the player's pages ---

    /** A guide, or a tab of pages, opens on the editor's "Гайды" or "Вкладки". */
    void openGuide(GuideDraft draft) {
        EditorBook.active = true;
        screen.closePage();
        if (draft.section) {
            current = TABS;
            ((TabsEditorView) page(TABS)).select(draft.id);
        } else {
            current = GUIDES;
            ((GuideEditorView) page(GUIDES)).select(draft.id, 0);
        }
    }

    /** A tab of the author: "Вкладки" with it chosen. */
    void openSection(@org.jetbrains.annotations.Nullable works.repka.rysquests.data.SectionDef def) {
        if (def instanceof works.repka.rysquests.data.PagesSection pages) {
            openGuide(GuideDraft.of(pages));
            return;
        }
        EditorBook.active = true;
        screen.closePage();
        current = TABS;
        if (def != null) {
            ((TabsEditorView) page(TABS)).select(def.id());
        }
    }

    static Component label(String id) {
        return Component.translatable("rysquests.tab." + id);
    }
}
