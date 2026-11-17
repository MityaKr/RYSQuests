package works.repka.rysquests.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Achievement;

/** The frame's small rules that need no game: numbers, the bold sentence of a message, the color of a level. */
class FramePartsTest {
    @Test
    void wholeNumbersGetASpaceBetweenThousands() {
        assertEquals("0", Sidebar.full(0));
        assertEquals("329", Sidebar.full(329));
        assertEquals("4 248", Sidebar.full(4248));
        assertEquals("10 000", Sidebar.full(10_000));
        assertEquals("1 234 567", Sidebar.full(1_234_567));
        assertEquals("-4 400", Sidebar.full(-4400));
    }

    @Test
    void theFirstSentenceOfAMessageIsBold() {
        String text = "Покупка не прошла. «Жетон инженера» сегодня уже куплен 3 раза из 3.";
        assertEquals("Покупка не прошла.", text.substring(0, BookMessageLine.boldEnd(text)));
        assertEquals(0, BookMessageLine.boldEnd("В инвентаре нет места: награда подождёт"));
    }

    @Test
    void aLevelIsColoredByItsPlace() {
        assertEquals("medal.gold", LevelSegments.token(0, 1));
        assertEquals("medal.silver", LevelSegments.token(0, 2));
        assertEquals("medal.bronze", LevelSegments.token(1, 4));
        assertEquals("medal.silver", LevelSegments.token(2, 4));
        assertEquals("medal.gold", LevelSegments.token(3, 4));
        assertEquals(Achievement.Medal.GOLD, Achievement.medal(2, 3));
    }
}
