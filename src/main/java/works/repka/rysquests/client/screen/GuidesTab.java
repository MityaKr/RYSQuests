package works.repka.rysquests.client.screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.ryscore.ui.Icons;

/**
 * The "Guides" tab (plan, section 3, pictures 23, 24): on the left the topics of the library and, as a group of their own, the
 * author's tabs (cards and pages; matrices only for operators); beside them the library or the author's tab. A page of a guide draws
 * its own column, the list of its pages, with the way back to the library on top.
 */
final class GuidesTab implements BookView {
    private static final int WHEEL = 48;

    private final BookScreen screen;
    final Guides guides = new Guides();
    final GuideLibraryView library;
    final GuidePageView guidePage;
    final GuidePageView pagesSection;
    private final MatrixView matrix;
    private final CardsView cards = new CardsView();
    private final Scroll topicScroll = new Scroll();

    GuidesTab(BookScreen screen) {
        this.screen = screen;
        this.library = new GuideLibraryView(screen, guides);
        this.matrix = new MatrixView(screen);
        this.guidePage = new GuidePageView(guides, new GuidePageView.Source() {
            @Nullable
            @Override
            public Guide guide() {
                return BookState.guide == null ? null : BookData.book().guides().get(BookState.guide);
            }

            @Override
            public int page() {
                return BookState.guidePage;
            }

            @Override
            public void open(Guide guide, int page) {
                BookState.openGuide(guide, page);
            }

            @Override
            public boolean tracksReading() {
                return true;
            }

            @Override
            public String crumb() {
                return Component.translatable("rysquests.guides.all_guides").getString();
            }

            @Override
            public void back() {
                BookState.openLibrary();
            }

            @Override
            public void edit(Guide guide, int page) {
                screen.editors.openGuide(GuideDraft.of(guide), false);
            }
        });
        this.pagesSection = new GuidePageView(guides, new GuidePageView.Source() {
            @Nullable
            @Override
            public Guide guide() {
                return BookState.customSection() instanceof PagesSection pages ? pages.asGuide() : null;
            }

            @Override
            public int page() {
                return BookState.customSection() instanceof PagesSection pages ? BookState.SECTION_PAGES.getOrDefault(pages.id(), 0) : 0;
            }

            @Override
            public void open(Guide guide, int page) {
                BookState.SECTION_PAGES.put(guide.id(), page);
            }

            @Override
            public boolean tracksReading() {
                return false;
            }

            @Nullable
            @Override
            public String crumb() {
                return BookData.book().guides().isEmpty() ? null : Component.translatable("rysquests.guides.all_guides").getString();
            }

            @Override
            public void back() {
                if (!BookData.book().guides().isEmpty()) {
                    BookState.openLibrary();
                }
            }

            @Override
            public void edit(Guide guide, int page) {
                if (BookState.customSection() instanceof PagesSection pages) {
                    screen.editors.openGuide(GuideDraft.of(pages), false);
                }
            }
        });
    }

    /** The view of what is open in the tab: the library, a guide's page, or an author's tab. */
    BookView content() {
        SectionDef custom = BookState.customSection();
        if (custom == null) {
            return BookState.guidePage >= 0 ? guidePage : library;
        }
        return switch (custom) {
            case MatrixSection table -> matrix;
            case CardsSection catalog -> cards;
            case PagesSection pages -> pagesSection;
        };
    }

    /** A page of a guide or of the author's pages draws its own column (the list of pages); everything else stands beside the topics. */
    private boolean ownColumn() {
        BookView shown = content();
        return shown == guidePage || shown == pagesSection;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (ownColumn() || screen.compact()) {
            content().render(ui, x, y, w, h);
            return;
        }
        renderTopics(ui, x, y, h);
        content().render(ui, x + SideList.WIDTH, y, w - SideList.WIDTH, h);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (!ownColumn() && !screen.compact() && mouseX < topicsRight) {
            topicScroll.wheel(amount, WHEEL);
            return;
        }
        content().scroll(mouseX, mouseY, amount);
    }

    @Override
    public boolean drag(double deltaX, double deltaY) {
        return guidePage.dragStructure(deltaX / 2, deltaY / 2) || pagesSection.dragStructure(deltaX / 2, deltaY / 2) || content().drag(deltaX, deltaY);
    }

    @Override
    public boolean release(double x, double y, int button) {
        guidePage.releaseStructure();
        pagesSection.releaseStructure();
        return false;
    }

    @Override
    public boolean key(int key) {
        return content().key(key);
    }

    @Override
    public void closed() {
        guidePage.closeStructures();
        pagesSection.closeStructures();
    }

    // --- the topics on the left (picture 23) ---

    private int topicsRight;

    private void renderTopics(Ui ui, int x, int y, int h) {
        topicsRight = x + SideList.WIDTH;
        SideList.plate(ui, x, y, h);
        QuestBook book = BookData.book();
        float pen = y;
        boolean library = BookTabs.allowed(book, BookMeta.GUIDES) && !book.guides().isEmpty();
        pen += SideList.head(ui, x, pen, Component.translatable("rysquests.guides.side_title").getString());
        int offset = topicScroll.offset();
        ui.clip(x, Math.round(pen), SideList.WIDTH - 1, Math.round(y + h - pen));
        float top = pen;
        pen -= offset;
        String current = BookState.currentSection();
        boolean inLibrary = BookState.customSection() == null;
        if (library) {
            pen += SideList.row(ui, x, pen, Component.translatable("rysquests.guides.all_guides").getString(), String.valueOf(book.guides().size()),
                    inLibrary && BookState.sectionCategory.isEmpty(), false, Icons.Shape.BOOK, BookState::openLibrary);
            Map<String, Integer> topics = new LinkedHashMap<>();
            for (Guide guide : book.guides().values()) {
                guide.category().ifPresent(category -> topics.merge(category, 1, Integer::sum));
            }
            if (!topics.isEmpty()) {
                pen += SideList.group(ui, x, pen, Component.translatable("rysquests.guides.by_topic").getString());
                for (Map.Entry<String, Integer> topic : topics.entrySet()) {
                    String key = topic.getKey();
                    pen += SideList.row(ui, x, pen, Component.translatable(key).getString(), String.valueOf(topic.getValue()),
                            inLibrary && key.equals(BookState.sectionCategory), true, null, () -> {
                                BookState.openLibrary();
                                BookState.sectionCategory = key;
                            });
                }
            }
        }
        List<SectionDef> author = BookTabs.authorSections(book);
        if (!author.isEmpty()) {
            pen += SideList.group(ui, x, pen, Component.translatable("rysquests.guides.from_author").getString());
            for (SectionDef section : author) {
                String count = switch (section) {
                    case CardsSection catalog -> String.valueOf(catalog.cards().size());
                    case PagesSection pages -> null;
                    case MatrixSection table -> null;
                };
                Icons.Shape glyph = switch (section) {
                    case CardsSection catalog -> Icons.Shape.LIST;
                    case PagesSection pages -> Icons.Shape.FLAG;
                    case MatrixSection table -> Icons.Shape.CHART;
                };
                pen += SideList.row(ui, x, pen, Component.translatable(section.shortTitle().orElse(section.title())).getString(), count,
                        section.id().toString().equals(current), true, glyph, () -> BookState.openSection(section.id().toString()));
            }
        }
        ui.unclip();
        topicScroll.content(pen + offset - top, y + h - top);
    }

    /** The pencil beside a section's title, for operators. */
    static void editButton(Ui ui, float x, float cy, SectionDef section) {
        if (BookRole.operator()) {
            boolean over = ui.over(x, cy - 15, 30, 30);
            ui.box(x, cy - 15, 30, 30, 8, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
            ui.icon(Icons.Shape.PENCIL, x + 9, cy - 6, 12, ui.c("button.fg"));
            ui.tooltipIfOver(x, cy - 15, 30, 30, Component.translatable("rysquests.editor.edit"));
            ui.hit(x, cy - 15, 30, 30, () -> {
                if (net.minecraft.client.Minecraft.getInstance().screen instanceof BookScreen open) {
                    open.editors.openSection(section);
                }
            });
        }
    }
}
