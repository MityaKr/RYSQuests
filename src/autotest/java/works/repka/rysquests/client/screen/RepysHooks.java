package works.repka.rysquests.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;

/**
 * Hooks of the "repys" autotest into the open book on the real RepYS book: the shop, the achievements, the guides, the search and the
 * quest panel's main button, as a player reaches them. Part of the autotest only, never of the mod.
 */
public final class RepysHooks {
    private RepysHooks() {
    }

    @Nullable
    private static BookScreen screen() {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen : null;
    }

    public static void shop(@Nullable ResourceLocation offer) {
        BookScreen screen = screen();
        if (screen == null) {
            return;
        }
        screen.closePage();
        screen.openTab(BookTabs.byId(BookTabs.SHOP));
        if (offer != null) {
            screen.shopView().selectForTest(offer);
        }
    }

    public static void shopHistory() {
        BookScreen screen = screen();
        if (screen != null) {
            screen.shopView().showHistoryForTest();
        }
    }

    public static void achievements(@Nullable ResourceLocation selected) {
        BookScreen screen = screen();
        if (screen == null) {
            return;
        }
        screen.closePage();
        screen.openTab(BookTabs.byId(BookTabs.ACHIEVEMENTS));
        AchievementsView view = screen.achievementsView();
        view.showForTest(0, "");
        if (selected != null) {
            view.selectForTest(selected);
        } else {
            view.closePanelForTest();
        }
    }

    public static void library() {
        BookScreen screen = screen();
        if (screen == null) {
            return;
        }
        screen.closePage();
        screen.openTab(BookTabs.byId(BookTabs.GUIDES));
        BookState.openLibrary();
        screen.guidesTab().library.showForTest(0);
    }

    public static boolean guide(ResourceLocation id, int page) {
        Guide guide = ClientBook.current().guides().get(id);
        if (guide == null) {
            return false;
        }
        BookState.openGuide(guide, page);
        return true;
    }

    public static void search(String query) {
        BookScreen screen = screen();
        if (screen != null) {
            screen.setSearch(query);
            screen.focusSearch();
        }
    }

    /** The label of the quest panel's main button ("Как сделать: X", "Забрать: ..."), or null. */
    @Nullable
    public static String mainAction(ResourceLocation quest) {
        Quest found = ClientBook.current().quests().get(quest);
        DetailPanel.Action action = found == null ? null : new QuestPanel().actionFor(found);
        return action == null ? null : action.label();
    }

    /** Presses the quest panel's main button. */
    public static boolean pressMainAction(ResourceLocation quest) {
        Quest found = ClientBook.current().quests().get(quest);
        DetailPanel.Action action = found == null ? null : new QuestPanel().actionFor(found);
        if (action == null || action.action() == null) {
            return false;
        }
        action.action().run();
        return true;
    }
}
