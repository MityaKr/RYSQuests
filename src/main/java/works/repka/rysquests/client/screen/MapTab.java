package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.ryscore.ui.Icons;

/**
 * The "Map" tab: the {@link Sidebar} on the left and, right of it, the map ({@link MapView}, the canvas with its tools and panel) or
 * the list. On the map the sidebar flies: a chapter to its area, an epoch to its part, "Вся книга" to the whole book. A chapter off
 * the map opens as a list. The list keeps the frame's toolbar and old quest card until its own screen takes over.
 */
final class MapTab implements BookView {
    private static final int TOOLBAR_H = 46;
    private static final int CARD_W = 392;
    private static final int CARD_MIN_WINDOW = 1000;
    private static final int WHEEL = 48;

    private final BookScreen screen;
    final MapView map;
    final ListView list;
    final Sidebar sidebar;
    private final Scroll cardScroll = new Scroll();
    private float cardLeft = Float.MAX_VALUE;

    MapTab(BookScreen screen) {
        this.screen = screen;
        this.map = new MapView(screen);
        this.list = new ListView(screen);
        this.sidebar = new Sidebar(new Sidebar.Listener() {
            @Override
            public void chapter(ResourceLocation id) {
                Chapter chapter = BookData.book().chapters().get(id);
                if (chapter != null && Sidebar.isOffMap(chapter)) {
                    BookState.openChapter(id);
                    BookState.view = ViewMode.LIST;
                } else if (listMode()) {
                    BookState.openChapter(id);
                } else {
                    map.flyToChapter(id);
                }
            }

            @Override
            public void epoch(int number) {
                if (listMode()) {
                    Sidebar.model().groups().stream().filter(group -> group.number() == number).findFirst()
                            .flatMap(group -> group.chapters().stream().findFirst())
                            .ifPresent(entry -> BookState.openChapter(entry.chapter().id()));
                } else {
                    map.flyToEpoch(number);
                }
            }

            @Override
            public void wholeBook() {
                if (!listMode()) {
                    map.flyToBook();
                }
            }
        }, () -> listMode() ? BookState.chapter : map.selectedChapter());
    }

    private static boolean listMode() {
        return BookState.currentView() == ViewMode.LIST;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        boolean compact = screen.compact();
        int sideW = sidebar.width(compact);
        sidebar.render(ui, x, y, h, compact);
        int viewX = x + sideW;
        int viewW = w - sideW;
        cardLeft = Float.MAX_VALUE;
        if (!listMode()) {
            map.render(ui, viewX, y, viewW, h);
            sidebar.renderOverlay(ui);
            return;
        }
        ui.rect(viewX, y, viewW, TOOLBAR_H, ui.c("panel.bg"));
        Segments.render(ui, viewX + 12, y + 12, List.of(Segments.item(Component.translatable("rysquests.tab.map").getString(), Icons.Shape.MAP),
                Segments.item(Component.translatable("rysquests.view.list").getString(), Icons.Shape.LIST)), 1, index -> BookState.view = index == 0 ? ViewMode.MAP : ViewMode.LIST);
        int bodyY = y + TOOLBAR_H;
        int bodyH = h - TOOLBAR_H;
        if (screen.windowWidth() >= CARD_MIN_WINDOW && !compact) {
            viewW -= CARD_W;
            cardLeft = viewX + viewW;
            renderCard(ui, Math.round(cardLeft), y, CARD_W, h);
        }
        list.render(ui, viewX, bodyY, viewW, bodyH);
        sidebar.renderOverlay(ui);
    }

    private void renderCard(Ui ui, int x, int y, int w, int h) {
        ui.rect(x, y, w, h, ui.c("card.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        Quest quest = BookState.quest == null ? null : BookData.book().quests().get(BookState.quest);
        if (quest == null || !quest.chapter().equals(java.util.Optional.ofNullable(BookState.chapter))) {
            return;
        }
        ui.clip(x, y, w, h);
        int content = QuestCard.render(ui, quest, x + 1, y - cardScroll.offset(), w - 1);
        ui.unclip();
        cardScroll.content(content, h);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (sidebar.contains(mouseX, mouseY)) {
            sidebar.scroll(mouseX, mouseY, amount);
        } else if (!listMode()) {
            map.scroll(mouseX, mouseY, amount);
        } else if (mouseX >= cardLeft) {
            cardScroll.wheel(amount, WHEEL);
        } else {
            list.scroll(mouseX, mouseY, amount);
        }
    }

    @Override
    public void press(double x, double y, int button) {
        if (!listMode() && !sidebar.contains(x, y)) {
            map.press(x, y, button);
        }
    }

    @Override
    public boolean release(double x, double y, int button) {
        return !listMode() && map.release(x, y, button);
    }

    @Override
    public boolean drag(double deltaX, double deltaY) {
        return listMode() ? list.drag(deltaX, deltaY) : map.drag(deltaX, deltaY);
    }

    @Override
    public boolean key(int key) {
        return listMode() ? list.key(key) : map.key(key);
    }

    @Nullable
    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        int sideW = sidebar.width(screen.compact());
        int right = cardLeft == Float.MAX_VALUE ? x + w : Math.round(cardLeft);
        return new int[] {x + sideW, y, right - x - sideW, h};
    }

    int sidebarScrollForTest() {
        return sidebar.scrollForTest();
    }
}
