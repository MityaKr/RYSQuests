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
 * The "Map" tab as the frame holds it until the new map takes over (plan step 7): the {@link Sidebar} on the left, "Карта | Список"
 * above, and the old {@link MapView} or {@link ListView} with the old quest card on the right. The map screen replaces this class
 * (its line in {@link BookTabs}); the sidebar's listener below is where a chapter click reaches the map.
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
                BookState.openChapter(id);
                Chapter chapter = BookData.book().chapters().get(id);
                if (chapter != null && Sidebar.isOffMap(chapter)) {
                    BookState.view = ViewMode.LIST;
                }
            }

            @Override
            public void epoch(int number) {
                Sidebar.model().groups().stream().filter(group -> group.number() == number).findFirst()
                        .flatMap(group -> group.chapters().stream().findFirst())
                        .ifPresent(entry -> BookState.openChapter(entry.chapter().id()));
            }
        }, () -> BookState.chapter);
    }

    private BookView active() {
        return BookState.currentView() == ViewMode.LIST ? list : map;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        boolean compact = screen.compact();
        int sideW = sidebar.width(compact);
        sidebar.render(ui, x, y, h, compact);
        int viewX = x + sideW;
        int viewW = w - sideW;
        boolean listMode = BookState.currentView() == ViewMode.LIST;
        ui.rect(viewX, y, viewW, TOOLBAR_H, listMode ? ui.c("panel.bg") : ui.mapBg());
        Chapter open = BookState.chapter == null ? null : BookData.book().chapters().get(BookState.chapter);
        boolean offMap = open != null && Sidebar.isOffMap(open);
        Segments.Item mapItem = offMap
                ? new Segments.Item(Component.translatable("rysquests.tab.map").getString(), Icons.Shape.MAP, true, Component.translatable("rysquests.map.off_map"))
                : Segments.item(Component.translatable("rysquests.tab.map").getString(), Icons.Shape.MAP);
        Segments.render(ui, viewX + 12, y + 12, List.of(mapItem, Segments.item(Component.translatable("rysquests.view.list").getString(), Icons.Shape.LIST)),
                listMode ? 1 : 0, index -> BookState.view = index == 0 ? ViewMode.MAP : ViewMode.LIST);
        int bodyY = y + TOOLBAR_H;
        int bodyH = h - TOOLBAR_H;
        cardLeft = Float.MAX_VALUE;
        if (!listMode && screen.windowWidth() >= CARD_MIN_WINDOW && !compact) {
            viewW -= CARD_W;
            cardLeft = viewX + viewW;
            renderCard(ui, Math.round(cardLeft), y, CARD_W, h);
        }
        active().render(ui, viewX, bodyY, viewW, bodyH);
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
        } else if (mouseX >= cardLeft) {
            cardScroll.wheel(amount, WHEEL);
        } else {
            active().scroll(mouseX, mouseY, amount);
        }
    }

    @Override
    public boolean drag(double deltaX, double deltaY) {
        return active().drag(deltaX, deltaY);
    }

    @Override
    public boolean key(int key) {
        return active().key(key);
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
