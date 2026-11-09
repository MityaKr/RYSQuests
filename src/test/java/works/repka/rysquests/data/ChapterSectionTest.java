package works.repka.rysquests.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class ChapterSectionTest {
    private static Chapter parse(String json) {
        return Chapter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void chapterWithoutSectionBelongsToTheStory() {
        Chapter chapter = parse("{\"id\": \"test:a\", \"title\": \"A\"}");
        assertEquals(Chapter.STORY, chapter.section());
        assertTrue(chapter.isStory());
    }

    @Test
    void otherSectionIsReadAndIsNotTheStory() {
        Chapter chapter = parse("{\"id\": \"test:a\", \"title\": \"A\", \"section\": \"guides\"}");
        assertEquals("guides", chapter.section());
        assertFalse(chapter.isStory());
    }

    @Test
    void sectionSurvivesEncoding() {
        Chapter chapter = parse("{\"id\": \"test:a\", \"title\": \"A\", \"section\": \"guides\"}");
        JsonElement encoded = Chapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter).getOrThrow();
        assertEquals(chapter, Chapter.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
