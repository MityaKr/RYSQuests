package works.repka.rysquests.client.markup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.client.markup.Markup.Paragraph;
import works.repka.rysquests.client.markup.Markup.Span;

class MarkupTest {
    @Test
    void plainTextStaysOneSpan() {
        List<Paragraph> parsed = Markup.parse("hello world");

        assertEquals(1, parsed.size());
        assertEquals("hello world", parsed.getFirst().spans().getFirst().text());
        assertFalse(parsed.getFirst().warning());
    }

    @Test
    void linksKeepKindTargetAndLabel() {
        List<Span> spans = Markup.parse("take [[item:minecraft:stone]] or [[quest:a:b|the quest]]").getFirst().spans();

        assertEquals("item", spans.get(1).kind());
        assertEquals("minecraft:stone", spans.get(1).target());
        assertEquals("", spans.get(1).text());
        assertEquals("the quest", spans.get(3).text());
    }

    @Test
    void stylesAndSpoilers() {
        List<Span> spans = Markup.parse("a **b** __c__ >>d<<").getFirst().spans();

        assertTrue(spans.get(1).bold());
        assertTrue(spans.get(3).italic());
        assertTrue(spans.get(5).spoiler());
    }

    @Test
    void linkInsideBoldIsBoldLink() {
        Span link = Markup.parse("**[[item:minecraft:stone|x]]**").getFirst().spans().getFirst();

        assertTrue(link.isLink());
        assertTrue(link.bold());
    }

    @Test
    void warningLineAndNewlines() {
        List<Paragraph> parsed = Markup.parse("one\n!!careful!!\n\nthree");

        assertEquals(4, parsed.size());
        assertTrue(parsed.get(1).warning());
        assertEquals("careful", parsed.get(1).spans().getFirst().text());
        assertTrue(parsed.get(2).spans().isEmpty());
    }

    @Test
    void unfinishedMarkersAreLeftAsText() {
        assertEquals("**a [[b", Markup.parse("**a [[b").getFirst().spans().getFirst().text());
    }
}
