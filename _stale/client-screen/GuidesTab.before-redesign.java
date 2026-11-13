package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.SectionDef;
import works.repka.ryscore.ui.Icons;

/**
 * The "Guides" tab as the frame holds it until the guides screen takes over (plan step 8): the old library and page, and the author's
 * own tabs (cards, pages, and matrices for operators) as a group inside it. Until the new sidebar of guides shows that group, the old
 * rail of sections on the left switches between the library and the author's tabs, as before.
 */
final class GuidesTab implements BookView {
    private static final int SECTIONS_W = 84;
    private static final int SECTION_ITEM_W = 70;
    private static final int SECTION_ITEM_H = 60;
    private static final int SECTION_STEP = 66;

    private final BookScreen screen;
    final Guides guides = new Guides();
    final GuideLibraryView library;
    final GuidePageView guidePage;
    final GuidePageView pagesSection;
    private final MatrixView matrix;
    private final CardsView cards = new CardsView();

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
                return Component.translatable("rysquests.section.guides").getString();
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
                return null;
            }

            @Override
            public void back() {
            }

            @Override
            public void edit(Guide guide, int page) {
                if (BookState.customSection() instanceof PagesSection pages) {
                    screen.editors.openGuide(GuideDraft.of(pages), false);
                }
            }
        });
    }

    /** What the rail lists: the library when the book has guides, then the author's tabs. */
    private static List<String> entries() {
        List<String> out = new ArrayList<>();
        if (BookTabs.allowed(BookData.book(), BookMeta.GUIDES) && !BookData.book().guides().isEmpty()) {
            out.add(BookMeta.GUIDES);
        }
        BookTabs.authorSections(BookData.book()).forEach(section -> out.add(section.id().toString()));
        return out;
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

    private int railWidth() {
        return entries().size() >= 2 ? SECTIONS_W : 0;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        List<String> entries = entries();
        int railW = railWidth();
        if (railW > 0) {
            renderSections(ui, entries, x, y, h);
        }
        content().render(ui, x + railW, y, w - railW, h);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
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

    // --- the old rail of sections ---

    private void renderSections(Ui ui, List<String> sections, int x, int y, int h) {
        ui.rect(x, y, SECTIONS_W, h, ui.railBg());
        ui.rect(x + SECTIONS_W - 1, y, 1, h, ui.c("panel.border"));
        float center = x + (SECTIONS_W - 1) / 2f;
        String current = BookState.currentSection();
        for (int i = 0; i < sections.size(); i++) {
            renderSectionItem(ui, sections.get(i), sections.get(i).equals(current), center - SECTION_ITEM_W / 2f, y + 14 + i * SECTION_STEP);
        }
    }

    private void renderSectionItem(Ui ui, String section, boolean open, float x, float y) {
        boolean over = ui.over(x, y, SECTION_ITEM_W, SECTION_ITEM_H);
        if (open) {
            ui.box(x, y, SECTION_ITEM_W, SECTION_ITEM_H, 10, ui.c("button.bg"), ui.c("button.border"));
        } else if (over) {
            ui.fill(x, y, SECTION_ITEM_W, SECTION_ITEM_H, 10, ui.c("slot.hover"));
        }
        int fg = ui.c(open ? "text.primary" : "text.muted");
        float center = x + SECTION_ITEM_W / 2f;
        SectionDef custom = ResourceLocation.tryParse(section) == null ? null : BookData.book().sections().get(ResourceLocation.parse(section));
        if (custom != null) {
            ui.item(custom.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), center - 10, y + 11.5f, 20);
        } else {
            ui.sectionIcon(section, center - 10, y + 11.5f, 20, fg);
        }
        String name = Component.translatable(custom != null ? custom.shortTitle().orElse(custom.title()) : "rysquests.section." + section).getString();
        ui.labelCentered(ui.fit(name, SECTION_ITEM_W - 4, 10.5f, Ui.REGULAR), center, y + 35.5f + 6.5f, 10.5f, Ui.REGULAR, fg);
        int badge = section.equals(BookMeta.GUIDES) ? guides.neededCount() : 0;
        if (badge > 0) {
            String count = String.valueOf(badge);
            float badgeW = Math.max(14, ui.width(count, 9.5f, Ui.SEMIBOLD) + 6);
            float badgeX = x + SECTION_ITEM_W - 6 - badgeW;
            ui.fill(badgeX, y + 6, badgeW, 14, 7, ui.c("text.primary"));
            ui.labelCentered(count, badgeX + badgeW / 2f, y + 13, 9.5f, Ui.SEMIBOLD, ui.c("accent.text"));
        }
        ui.hit(x, y, SECTION_ITEM_W, SECTION_ITEM_H, () -> BookState.openSection(section));
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
