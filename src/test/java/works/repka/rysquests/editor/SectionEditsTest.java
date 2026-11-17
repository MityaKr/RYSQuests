package works.repka.rysquests.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.MatrixSection.Column;
import works.repka.rysquests.data.MatrixSection.Way;
import works.repka.rysquests.data.SectionDef;

class SectionEditsTest {
    private static final ResourceLocation ID = ResourceLocation.parse("t:atlas");
    private static final String TITLE = "section.t.atlas";

    @TempDir
    Path root;

    private static MatrixSection matrix() {
        return SectionEdits.newMatrix(ID, TITLE);
    }

    private static Column middle(MatrixSection section) {
        return section.columns().get(1);
    }

    @Test
    void newMatrixStartsWithThreeStagesAndOneRow() {
        assertEquals(3, matrix().columns().size());
        assertEquals(1, matrix().rows().size());
    }

    @Test
    void newRowsAndColumnsGetFreshIds() {
        MatrixSection section = SectionEdits.addRow(SectionEdits.addRow(matrix()));
        section = SectionEdits.addColumn(section);

        assertEquals(List.of("row_1", "row_2", "row_3"), section.rows().stream().map(MatrixSection.Row::id).toList());
        assertEquals("column_4", section.columns().get(3).id());
    }

    @Test
    void waysAreAddedRemovedAndMovedWithinTheirCell() {
        MatrixSection section = matrix();
        section = SectionEdits.addWay(section, 0, middle(section));
        section = SectionEdits.addWay(section, 0, middle(section));
        assertEquals(List.of("way_1", "way_2"), section.rows().getFirst().ways(middle(section)).stream().map(Way::id).toList());

        section = SectionEdits.moveWay(section, 0, middle(section), 1, -1);
        assertEquals("way_2", section.rows().getFirst().ways(middle(section)).getFirst().id());

        section = SectionEdits.removeWay(section, 0, middle(section), 0);
        section = SectionEdits.removeWay(section, 0, middle(section), 0);
        assertFalse(section.rows().getFirst().cells().containsKey("mid"));
    }

    @Test
    void onlyOneWayOfACellIsTheBest() {
        MatrixSection section = matrix();
        section = SectionEdits.addWay(section, 0, middle(section));
        section = SectionEdits.addWay(section, 0, middle(section));
        section = SectionEdits.markBest(section, 0, middle(section), 0);
        section = SectionEdits.markBest(section, 0, middle(section), 1);

        List<Way> ways = section.rows().getFirst().ways(middle(section));
        assertFalse(ways.get(0).best());
        assertTrue(ways.get(1).best());
    }

    @Test
    void removingAColumnTakesItsCellsAlong() {
        MatrixSection section = matrix();
        section = SectionEdits.addWay(section, 0, middle(section));

        section = SectionEdits.removeColumn(section, 1);

        assertEquals(List.of("early", "late"), section.columns().stream().map(Column::id).toList());
        assertTrue(section.rows().getFirst().cells().isEmpty());
    }

    @Test
    void theLastRowColumnAndCardStay() {
        MatrixSection section = matrix();
        assertSame(section, SectionEdits.removeRow(section, 0));
        CardsSection cards = SectionEdits.newCards(ID, TITLE);
        assertSame(cards, SectionEdits.removeCard(cards, 0));
    }

    @Test
    void movingPastTheEndChangesNothing() {
        List<String> list = List.of("a", "b");
        assertEquals(list, SectionEdits.moved(list, 0, -1));
        assertEquals(list, SectionEdits.moved(list, 1, 1));
        assertEquals(List.of("b", "a"), SectionEdits.moved(list, 1, -1));
    }

    @Test
    void savedMatrixReadsBackWithItsIdFromThePath() throws Exception {
        MatrixSection section = SectionEdits.addWay(matrix(), 0, middle(matrix()));
        Way way = section.rows().getFirst().ways(middle(section)).getFirst();
        String name = section.wayName(section.rows().getFirst(), middle(section), way);

        EditorFiles.save(root, Optional.empty(), Optional.of(section), "ru_ru", Map.of(name, "Шахта"), Map.of(), true);

        JsonObject json = JsonParser.parseString(Files.readString(root.resolve("data/t/rysquests/sections/atlas.json"))).getAsJsonObject();
        assertEquals("template", json.keySet().iterator().next());
        json.addProperty("id", ID.toString());
        assertEquals(section, SectionDef.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals("Шахта", JsonParser.parseString(Files.readString(root.resolve("assets/t/lang/ru_ru.json"))).getAsJsonObject().get(name).getAsString());
    }

    @Test
    void railOrderKeepsTheRestOfTheBookSettings() throws Exception {
        BookMeta meta = new BookMeta(BookMeta.DEFAULT.progressMode(), BookMeta.DEFAULT.defaultView(), BookMeta.DEFAULT.defaultTheme(), Optional.of("book.title"), Optional.empty(), List.of(), BookMeta.DEFAULT.hud(), List.of("story"));

        EditorFiles.saveBook(root, "t", meta, List.of("guides", "story", "t:atlas"), false);

        JsonObject json = JsonParser.parseString(Files.readString(root.resolve("data/t/rysquests/book.json"))).getAsJsonObject();
        BookMeta read = BookMeta.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of("guides", "story", "t:atlas"), read.sections());
        assertEquals(Optional.of("book.title"), read.title());
    }

    @Test
    void aWayMovesToAnotherStageWithItsTexts() {
        MatrixSection section = SectionEdits.addWay(matrix(), 0, middle(matrix()));
        Column from = middle(section);
        Column to = section.columns().getFirst();
        String name = section.wayName(section.rows().getFirst(), from, section.rows().getFirst().ways(from).getFirst());
        MatrixSection moved = SectionEdits.moveWayTo(section, 0, from, 0, to);
        assertTrue(moved.rows().getFirst().ways(from).isEmpty(), "the old cell is empty");
        assertEquals(1, moved.rows().getFirst().ways(to).size(), "the way is in the new cell");
        Way way = moved.rows().getFirst().ways(to).getFirst();
        assertEquals(name, moved.wayName(moved.rows().getFirst(), to, way), "its name keeps its key");
        assertSame(section, SectionEdits.moveWayTo(section, 0, from, 0, from), "the same column changes nothing");
    }
}
