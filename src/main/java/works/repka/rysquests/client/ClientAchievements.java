package works.repka.rysquests.client;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.network.AchievementStatePayload;

/** What the server says about achievements beyond their own progress. */
public final class ClientAchievements {
    private static AchievementStatePayload state = new AchievementStatePayload(Map.of(), List.of(), "");

    private ClientAchievements() {
    }

    static void receive(AchievementStatePayload payload) {
        state = payload;
    }

    /** @return the percent of this server who reached a tier */
    public static int rarity(ResourceLocation tierQuest) {
        return state.rarity().getOrDefault(tierQuest.toString(), 0);
    }

    public static boolean owns(String title) {
        return state.titles().contains(title);
    }

    public static String activeTitle() {
        return state.activeTitle();
    }

    /** For the in-game autotest: owned titles and the worn one the server did not send (picture 28). */
    public static void showForTest(AchievementStatePayload payload) {
        state = payload;
    }

    static void clear() {
        state = new AchievementStatePayload(Map.of(), List.of(), "");
    }
}
