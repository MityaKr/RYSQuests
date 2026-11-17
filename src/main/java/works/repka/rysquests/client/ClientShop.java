package works.repka.rysquests.client;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.network.ShopStatePayload;
import works.repka.rysquests.progress.ProgressStore;

/** The client's copy of its money. Changed only by packets. */
public final class ClientShop {
    private static ShopStatePayload state = new ShopStatePayload(Map.of(), Map.of(), false, Map.of(), List.of());

    private ClientShop() {
    }

    static void receive(ShopStatePayload payload) {
        state = payload;
    }

    public static long balance(String currency) {
        return state.wallet().getOrDefault(currency, 0L);
    }

    public static long treasury(String currency) {
        return state.treasury().getOrDefault(currency, 0L);
    }

    public static boolean inTeam() {
        return state.inTeam();
    }

    /** Whether the player's role in the team lets them pay from its treasury. */
    public static boolean spendTreasury() {
        return state.inTeam() && state.spendTreasury();
    }

    public static int used(ResourceLocation offer) {
        return state.used().getOrDefault(offer.toString(), 0);
    }

    public static List<ProgressStore.ShopLine> history() {
        return state.history();
    }

    /** What the player's team has delivered towards a delivery trade, by item id. */
    public static Map<String, Long> delivered(ResourceLocation offer) {
        return state.delivered().getOrDefault(offer.toString(), Map.of());
    }

    /** For the in-game autotest: shows a shop state the server did not send (pictures 25 to 27b). */
    public static void showForTest(ShopStatePayload payload) {
        state = payload;
    }

    static void clear() {
        state = new ShopStatePayload(Map.of(), Map.of(), false, Map.of(), List.of());
    }
}
