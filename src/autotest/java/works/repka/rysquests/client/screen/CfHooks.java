package works.repka.rysquests.client.screen;

import net.minecraft.client.Minecraft;

/** What the {@code cfshots} scenario needs from package-private parts of the book: the shop's filter. */
public final class CfHooks {
    private CfHooks() {
    }

    /**
     * "Side links" of the map's legend (dashed roads between chapters, roads of side quests), for this game only: the
     * setting is changed in memory and not saved, and the open map follows it at once.
     */
    public static void mapSideLinks(boolean show) {
        works.repka.rysquests.client.ClientConfig.MAP_SIDE_LINKS.set(show);
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.mapTab().map.canvas.setShowSideLinks(show);
        }
    }

    /** Shows one section of the shop ({@code category} of the offers, e.g. "shop.repys.trophies"). */
    public static void shopSection(String section) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.shopView().stateForTest(section, 1, false, false);
        }
    }
}
