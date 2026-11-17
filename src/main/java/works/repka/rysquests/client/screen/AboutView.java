package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.network.QuestActionPayload;

/**
 * The pages "О книге" (mockup _audit/chronicle/01_about_brand, ADR-60): opened by the book's name in the header as a page over the
 * open tab, the way the settings open. The tabs go dark meanwhile and "Назад: Сейчас" returns. The pages are drawn by the guides'
 * own view: a guide of the book named in {@code book.json}, or the book's own pages from the same field.
 */
final class AboutView implements BookView {
    private final GuidePageView view;
    private int page;

    /** @param back the name of the place "Назад" returns to */
    AboutView(BookScreen screen, String back) {
        Guide shown = guide();
        page = shown == null ? 0 : BookState.firstUnread(shown);
        markRead(shown, page);
        view = new GuidePageView(new Guides(), new GuidePageView.Source() {
            @Nullable
            @Override
            public Guide guide() {
                return AboutView.guide();
            }

            @Override
            public int page() {
                return page;
            }

            @Override
            public void open(Guide guide, int index) {
                page = index;
                markRead(guide, index);
            }

            @Override
            public boolean tracksReading() {
                return true;
            }

            @Override
            public String crumb() {
                return Component.translatable("rysquests.page.back", back).getString();
            }

            @Override
            public void back() {
                screen.closePage();
            }

            @Override
            public void edit(Guide guide, int index) {
                screen.closePage();
                screen.editor.openGuide(GuideDraft.of(guide));
            }

            @Override
            public boolean editable() {
                // the book's own pages live in book.json, which the guides' editor does not write
                Guide guide = AboutView.guide();
                return guide != null && !guide.id().equals(BookMeta.About.OWN);
            }
        });
    }

    @Nullable
    static Guide guide() {
        return BookData.book().aboutGuide().orElse(null);
    }

    /** Whether no page of "О книге" is read yet: the dot on the book's name. */
    static boolean unread() {
        Guide guide = guide();
        return guide != null && ClientProgress.readPages(guide) == 0;
    }

    /** Opening a page is what counts as reading it, as in the library. */
    private static void markRead(@Nullable Guide guide, int index) {
        if (guide != null && index >= 0 && index < guide.pages().size() && !ClientProgress.isRead(guide, guide.pages().get(index))) {
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.READ_GUIDE_PAGE, guide.id(), index, List.of()));
        }
    }

    int pageForTest() {
        return page;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        view.render(ui, x, y, w, h);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        view.scroll(mouseX, mouseY, amount);
    }

    @Override
    public boolean drag(double deltaX, double deltaY) {
        return view.drag(deltaX, deltaY);
    }

    @Override
    public boolean key(int key) {
        return view.key(key);
    }

    @Override
    public void press(double x, double y, int button) {
        view.press(x, y, button);
    }

    @Override
    public boolean release(double x, double y, int button) {
        return view.release(x, y, button);
    }

    @Override
    public void closed() {
        view.closed();
    }
}
