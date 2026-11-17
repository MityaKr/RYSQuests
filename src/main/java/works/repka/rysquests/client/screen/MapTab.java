package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.ViewMode;
import works.repka.ryscore.ui.Icons;

/**
 * The "Map" tab: the {@link Sidebar} on the left and, right of it, the map ({@link MapView}, the canvas with its tools and the
 * {@link QuestPanel}) or the list ({@link QuestListView} with its own panel). On the map the sidebar flies: a chapter to its area, an
 * epoch to its part, "Вся книга" to the whole book; the list shows the same level. A chapter off the map opens as a list.
 */
final class MapTab implements BookView {
    private final BookScreen screen;
    final MapView map;
    final QuestListView list;
    final Sidebar sidebar;

    MapTab(BookScreen screen) {
        this.screen = screen;
        this.map = new MapView(screen);
        this.list = new QuestListView(screen);
        this.sidebar = new Sidebar(new Sidebar.Listener() {
            @Override
            public void chapter(ResourceLocation id) {
                QuestListView.showChapter();
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
                QuestListView.showEpoch(number);
                if (!listMode()) {
                    map.flyToEpoch(number);
                    // an epoch whose reward waits opens its card on the way (ADR-60)
                    if (EpochCards.waiting(number)) {
                        map.openEpochCard(number);
                    }
                }
            }

            @Override
            public void wholeBook() {
                QuestListView.showBook();
                if (!listMode()) {
                    map.flyToBook();
                }
            }
        }, () -> listMode() ? QuestListView.chosenChapter() : map.selectedChapter());
    }

    private static boolean listMode() {
        return BookState.currentView() == ViewMode.LIST;
    }

    private BookView active() {
        return listMode() ? list : map;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        boolean compact = screen.compact();
        int sideW = sidebar.width(compact);
        sidebar.render(ui, x, y, h, compact);
        active().render(ui, x + sideW, y, w - sideW, h);
        sidebar.renderOverlay(ui);
    }

    /**
     * "Карта | Список" (mockup {@code .seg}) of the list; "Карта" is grey for a chapter off the map, with the reason on hover (19b).
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

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (sidebar.contains(mouseX, mouseY)) {
            sidebar.scroll(mouseX, mouseY, amount);
        } else {
            active().scroll(mouseX, mouseY, amount);
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
        return active().messageArea(x + sideW, y, w - sideW, h);
    }

    int sidebarScrollForTest() {
        return sidebar.scrollForTest();
    }
}
