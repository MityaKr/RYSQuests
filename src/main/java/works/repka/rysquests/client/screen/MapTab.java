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
 * above, and the old {@link MapView} with the {@link QuestPanel} on the right, or the {@link QuestListView} with its own. The map
 * screen replaces this class (its line in {@link BookTabs}); the sidebar's listener below is where a chapter click reaches the map.
 */
final class MapTab implements BookView {
    private static final int TOOLBAR_H = 46;
    private static final int CARD_W = DetailPanel.WIDTH;
    private static final int CARD_MIN_WINDOW = 1000;

    private final BookScreen screen;
    final MapView map;
    final QuestListView list;
    final Sidebar sidebar;
    private final QuestPanel card = new QuestPanel(new QuestPanel.Events() {
        @Override
        public void close() {
            BookState.quest = null;
        }
    });
    private float cardLeft = Float.MAX_VALUE;

    MapTab(BookScreen screen) {
        this.screen = screen;
        this.map = new MapView(screen);
        this.list = new QuestListView(screen);
        this.sidebar = new Sidebar(new Sidebar.Listener() {
            @Override
            public void chapter(ResourceLocation id) {
                BookState.openChapter(id);
                QuestListView.showChapter();
                Chapter chapter = BookData.book().chapters().get(id);
                if (chapter != null && Sidebar.isOffMap(chapter)) {
                    BookState.view = ViewMode.LIST;
                }
            }

            @Override
            public void epoch(int number) {
                if (BookState.currentView() == ViewMode.LIST) {
                    QuestListView.showEpoch(number);
                    return;
                }
                Sidebar.model().groups().stream().filter(group -> group.number() == number).findFirst()
                        .flatMap(group -> group.chapters().stream().findFirst())
                        .ifPresent(entry -> BookState.openChapter(entry.chapter().id()));
            }

            @Override
            public void wholeBook() {
                QuestListView.showBook();
            }
        }, () -> BookState.currentView() == ViewMode.LIST ? QuestListView.chosenChapter() : BookState.chapter);
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
        cardLeft = Float.MAX_VALUE;
        if (listMode) {
            card.clear();
            list.render(ui, viewX, y, viewW, h);
            sidebar.renderOverlay(ui);
            return;
        }
        ui.rect(viewX, y, viewW, TOOLBAR_H, ui.mapBg());
        Chapter open = BookState.chapter == null ? null : BookData.book().chapters().get(BookState.chapter);
        modeSwitch(ui, viewX + 12, y + 12, false, open != null && Sidebar.isOffMap(open));
        int bodyY = y + TOOLBAR_H;
        int bodyH = h - TOOLBAR_H;
        if (screen.windowWidth() >= CARD_MIN_WINDOW && !compact) {
            viewW -= CARD_W;
            cardLeft = viewX + viewW;
            renderCard(ui, Math.round(cardLeft), y, CARD_W, h);
        }
        active().render(ui, viewX, bodyY, viewW, bodyH);
        sidebar.renderOverlay(ui);
    }

    /**
     * "Карта | Список" (mockup {@code .seg}); "Карта" is grey for a chapter off the map, with the reason on hover (19b).
     *
     * @return its width
     */
    static float modeSwitch(Ui ui, float x, float y, boolean listMode, boolean offMap) {
        Segments.Item mapItem = offMap
                ? new Segments.Item(Component.translatable("rysquests.tab.map").getString(), Icons.Shape.MAP, true, Component.translatable("rysquests.qlist.off_map_tip"))
                : Segments.item(Component.translatable("rysquests.tab.map").getString(), Icons.Shape.MAP);
        return Segments.render(ui, x, y, List.of(mapItem, Segments.item(Component.translatable("rysquests.view.list").getString(), Icons.Shape.LIST)),
                listMode ? 1 : 0, index -> BookState.view = index == 0 ? ViewMode.MAP : ViewMode.LIST);
    }

    /** The quest panel beside the map: the selected quest of the open chapter, or nothing. */
    private void renderCard(Ui ui, int x, int y, int w, int h) {
        Quest quest = BookState.quest == null ? null : BookData.book().quests().get(BookState.quest);
        if (quest == null || !quest.chapter().equals(java.util.Optional.ofNullable(BookState.chapter))) {
            ui.rect(x, y, w, h, ui.c("panel.bg"));
            ui.rect(x, y, 1, h, ui.c("panel.border"));
            card.clear();
            return;
        }
        card.render(ui, x, y, w, h, quest, QuestPanel.Look.MAP);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (sidebar.contains(mouseX, mouseY)) {
            sidebar.scroll(mouseX, mouseY, amount);
        } else if (mouseX >= cardLeft) {
            card.scroll(amount);
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
        if (BookState.currentView() == ViewMode.LIST) {
            return list.messageArea(x + sideW, y, w - sideW, h);
        }
        int right = cardLeft == Float.MAX_VALUE ? x + w : Math.round(cardLeft);
        return new int[] {x + sideW, y, right - x - sideW, h};
    }

    int sidebarScrollForTest() {
        return sidebar.scrollForTest();
    }
}
