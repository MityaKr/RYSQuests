package works.repka.rysquests.client.screen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The logic under the achievements' segments and the search's "может быть". */
class ExtrasLogicTest {
    @Test
    void levelsFillFromTheLevelBefore() {
        float[] fills = AchievementsView.fills(4400, new int[] {1000, 10000, 50000}, new boolean[3]);
        assertArrayEquals(new float[] {1f, 3400f / 9000f, 0f}, fills, 1e-6f);
    }

    @Test
    void aReachedLevelStaysFull() {
        float[] fills = AchievementsView.fills(0, new int[] {5, 12}, new boolean[] {true, false});
        assertArrayEquals(new float[] {1f, 0f}, fills, 1e-6f);
    }

    @Test
    void oneLevelIsOneSegment() {
        assertArrayEquals(new float[] {1f}, AchievementsView.fills(1, new int[] {1}, new boolean[1]), 1e-6f);
        assertArrayEquals(new float[] {0f}, AchievementsView.fills(0, new int[] {1}, new boolean[1]), 1e-6f);
    }

    @Test
    void aLineOfTheShopStartsASentenceInTheViewer() {
        assertEquals("Победи: Игнис", LinkCards.capital("победи: Игнис"));
        assertEquals("После квеста «Первый»", LinkCards.capital("после квеста «Первый»"));
        assertEquals("", LinkCards.capital(""));
    }

    @Test
    void typosAreCloseWords() {
        assertEquals(1, SearchIndex.distance("катшка", "катушка"));
        assertEquals(1, SearchIndex.distance("кирак", "кирка"));
        assertEquals(0, SearchIndex.distance("пресс", "пресс"));
        assertEquals(4, SearchIndex.distance("сталь", "медь"));
    }
}
