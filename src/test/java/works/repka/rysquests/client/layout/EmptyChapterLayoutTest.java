package works.repka.rysquests.client.layout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;

/** A book from nothing starts with one empty chapter: in every form it has a chapter's shape and room for its label, not a sliver. */
class EmptyChapterLayoutTest {
    @Test
    void anEmptyChapterHasAChapterShape() {
        Chapter c = new Chapter(ResourceLocation.parse("t:c"), "c", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), false, Chapter.STORY);
        MapBook book = MapBook.of(BookMeta.DEFAULT, List.of(c), List.of(), "empty");
        for (MapForm form : List.of(MapForm.SERPENTINE, MapForm.SPIRAL, MapForm.GENERATOR, MapForm.GALAXY)) {
            CanvasLayout layout = new LayoutEngine().lay(book, form);
            CanvasLayout.Region region = layout.regions[0];
            float w = region.maxX() - region.minX();
            float h = region.maxY() - region.minY();
            assertTrue(Math.min(w, h) >= 40, form + ": the empty chapter is " + w + " by " + h);
            assertTrue(Math.max(w, h) / Math.min(w, h) <= 4, form + ": the empty chapter is " + w + " by " + h);
            assertTrue(layout.labels[0].maxWidth() >= 40, form + ": its label has " + layout.labels[0].maxWidth());
        }
    }
}
