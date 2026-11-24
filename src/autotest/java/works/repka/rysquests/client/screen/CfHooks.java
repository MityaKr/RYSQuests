package works.repka.rysquests.client.screen;

import net.minecraft.client.Minecraft;

/** What the {@code cfshots} scenario needs from package-private parts of the book: the shop's filter. */
public final class CfHooks {
    private CfHooks() {
    }

    /** Shows one section of the shop ({@code category} of the offers, e.g. "shop.repys.trophies"). */
    public static void shopSection(String section) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.shopView().stateForTest(section, 1, false, false);
        }
    }
}
