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

    @Test
    void insertionAloneOnALineIsABlock() {
        assertEquals("image", Markup.block(Markup.parse("[[image:a:b.png|caption]]").getFirst()).kind());
        assertEquals("recipe", Markup.block(Markup.parse("  [[recipe:minecraft:stone]] ").getFirst()).kind());
    }

    @Test
    void insertionInsideTextIsNotABlock() {
        assertEquals(null, Markup.block(Markup.parse("see [[recipe:minecraft:stone]]").getFirst()));
        assertEquals(null, Markup.block(Markup.parse("[[item:minecraft:stone]]").getFirst()));
        assertEquals(null, Markup.block(Markup.parse("").getFirst()));
    }

    @Test
    void guideLinkKeepsPageInTarget() {
        Span link = Markup.parse("[[guide:a:b#c|read]]").getFirst().spans().getFirst();
        assertEquals("guide", link.kind());
        assertEquals("a:b#c", link.target());
    }
    @Test
    void theDiscordPageOfRepysIsALinkToTheWeb() {
        // guide.repys.book.discord.text, as the book of RepYS writes it
        List<Span> spans = Markup.parse("[[url:https://discord.gg/Tqgy7acmyd|discord.gg/Tqgy7acmyd]]").getFirst().spans();
        assertEquals(1, spans.size());
        assertEquals(Markup.URL, spans.getFirst().kind());
        assertEquals("https://discord.gg/Tqgy7acmyd", spans.getFirst().target());
        assertEquals("discord.gg/Tqgy7acmyd", spans.getFirst().text());
    }

    @Test
    void aMarkdownLinkKeepsItsLabel() {
        List<Span> spans = Markup.parse("Join [our Discord](https://discord.gg/x) today").getFirst().spans();
        assertEquals(3, spans.size());
        assertEquals("Join ", spans.get(0).text());
        assertEquals(Markup.URL, spans.get(1).kind());
        assertEquals("our Discord", spans.get(1).text());
        assertEquals("https://discord.gg/x", spans.get(1).target());
        assertEquals(" today", spans.get(2).text());
    }

    @Test
    void aBareAddressIsALinkWithoutTheDotThatEndsTheSentence() {
        List<Span> spans = Markup.parse("See https://example.com/a?b=1. Or (http://x.org)!").getFirst().spans();
        assertEquals(Markup.URL, spans.get(1).kind());
        assertEquals("https://example.com/a?b=1", spans.get(1).target());
        assertEquals("", spans.get(1).text());
        assertEquals(". Or (", spans.get(2).text());
        assertEquals("http://x.org", spans.get(3).target());
        assertEquals(")!", spans.get(4).text());
    }

    @Test
    void aBoldAddressStaysBold() {
        Span link = Markup.parse("**https://discord.gg/x**").getFirst().spans().getFirst();
        assertEquals(Markup.URL, link.kind());
        assertTrue(link.bold());
    }

    @Test
    void onlyWebAddressesOpen() {
        assertTrue(Markup.isWebUrl("https://discord.gg/x"));
        assertTrue(Markup.isWebUrl("HTTP://example.com"));
        assertFalse(Markup.isWebUrl("file:///C:/Windows"));
        assertFalse(Markup.isWebUrl("javascript:alert(1)"));
        assertFalse(Markup.isWebUrl("https://"));
        assertFalse(Markup.isWebUrl("minecraft:stone"));
        // a [[url:]] that is not a web address stays a link that does nothing; text that only looks like one stays text
        assertEquals("", Markup.parse("ftp://x.org").getFirst().spans().getFirst().kind());
    }

    @Test
    void anAddressWithoutALabelReadsWithoutItsScheme() {
        assertEquals("discord.gg/x", Markup.shortUrl("https://discord.gg/x"));
        assertEquals("example.com", Markup.shortUrl("http://example.com/"));
        assertEquals("example.com/a/", Markup.shortUrl("https://example.com/a/"));
    }
}
