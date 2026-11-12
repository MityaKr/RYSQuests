package works.repka.rysquests.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class XpTaskTest {
    private static com.mojang.serialization.DataResult<XpTask> parse(String json) {
        return XpTask.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void levelsOrPointsButNotBoth() {
        assertEquals(5, parse("{\"levels\": 5}").getOrThrow().amount());
        assertTrue(parse("{\"points\": 300, \"consume\": true}").getOrThrow().points());
        assertTrue(parse("{\"levels\": 5, \"points\": 300}").error().isPresent());
        assertTrue(parse("{}").error().isPresent());
    }

    /** The game's own totals: 7 at level 1, 352 at level 16, 1395 at level 30, 2920 at level 40. */
    @Test
    void pointsFollowTheGamesFormula() {
        assertEquals(0, XpTask.pointsAtLevel(0));
        assertEquals(7, XpTask.pointsAtLevel(1));
        assertEquals(352, XpTask.pointsAtLevel(16));
        assertEquals(1395, XpTask.pointsAtLevel(30));
        assertEquals(2920, XpTask.pointsAtLevel(40));
    }
}
