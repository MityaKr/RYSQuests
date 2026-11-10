package works.repka.rysquests.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.CardsSection.Card;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.MatrixSection.Column;
import works.repka.rysquests.data.MatrixSection.Row;
import works.repka.rysquests.data.MatrixSection.Spoilers;
import works.repka.rysquests.data.MatrixSection.Way;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.SectionDef;

/** Changes to a section as new immutable values. No game state here, so that each step can be tested. */
public final class SectionEdits {
    private SectionEdits() {
    }

    private static String freshId(String prefix, List<String> taken) {
        int number = taken.size() + 1;
        while (taken.contains(prefix + number)) {
            number++;
        }
        return prefix + number;
    }

    private static <T> List<T> replaced(List<T> list, int index, T value) {
        List<T> out = new ArrayList<>(list);
        out.set(index, value);
        return out;
    }

    private static <T> List<T> without(List<T> list, int index) {
        List<T> out = new ArrayList<>(list);
        out.remove(index);
        return out;
    }

    private static <T> List<T> plus(List<T> list, T value) {
        List<T> out = new ArrayList<>(list);
        out.add(value);
        return out;
    }

    /** Moves an entry within a list. Moving past either end changes nothing. */
    public static <T> List<T> moved(List<T> list, int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= list.size()) {
            return list;
        }
        List<T> out = new ArrayList<>(list);
        out.add(target, out.remove(index));
        return out;
    }

    /** Replaces what every template has at the top of its file. */
    public static SectionDef withHeader(SectionDef section, String title, Optional<String> shortTitle, Optional<String> subtitle, Optional<ResourceLocation> icon) {
        return switch (section) {
            case MatrixSection s -> new MatrixSection(s.id(), title, shortTitle, subtitle, icon, s.spoilers(), s.compact(), s.search(), s.rowTitle(), s.groups(), s.columns(), s.rows());
            case CardsSection s -> new CardsSection(s.id(), title, shortTitle, subtitle, icon, s.groups(), s.cards());
            case PagesSection s -> new PagesSection(s.id(), title, shortTitle, subtitle, icon, s.pages());
        };
    }

    public static SectionDef withGroups(SectionDef section, List<String> groups) {
        return switch (section) {
            case MatrixSection s -> new MatrixSection(s.id(), s.title(), s.shortTitle(), s.subtitle(), s.icon(), s.spoilers(), s.compact(), s.search(), s.rowTitle(), groups, s.columns(), s.rows());
            case CardsSection s -> new CardsSection(s.id(), s.title(), s.shortTitle(), s.subtitle(), s.icon(), groups, s.cards());
            case PagesSection s -> s;
        };
    }

    public static MatrixSection matrixOptions(MatrixSection s, Spoilers spoilers, boolean compact) {
        return new MatrixSection(s.id(), s.title(), s.shortTitle(), s.subtitle(), s.icon(), spoilers, compact, s.search(), s.rowTitle(), s.groups(), s.columns(), s.rows());
    }

    // --- matrix ---

    /** A new matrix has the three stages of the template and one row to start from. */
    public static MatrixSection newMatrix(ResourceLocation id, String title) {
        List<Column> columns = List.of(new Column("early", title + ".early", Optional.empty(), Optional.empty()),
                new Column("mid", title + ".mid", Optional.empty(), Optional.empty()),
                new Column("late", title + ".late", Optional.empty(), Optional.empty()));
        return new MatrixSection(id, title, Optional.empty(), Optional.empty(), Optional.empty(), Spoilers.DIM, false, Optional.empty(), Optional.empty(), List.of(), columns,
                List.of(new Row("row_1", Optional.empty(), Optional.empty(), Optional.empty(), Map.of())));
    }

    private static MatrixSection withRows(MatrixSection s, List<Row> rows) {
        return new MatrixSection(s.id(), s.title(), s.shortTitle(), s.subtitle(), s.icon(), s.spoilers(), s.compact(), s.search(), s.rowTitle(), s.groups(), s.columns(), rows);
    }

    private static MatrixSection withColumns(MatrixSection s, List<Column> columns, List<Row> rows) {
        return new MatrixSection(s.id(), s.title(), s.shortTitle(), s.subtitle(), s.icon(), s.spoilers(), s.compact(), s.search(), s.rowTitle(), s.groups(), columns, rows);
    }

    public static MatrixSection addRow(MatrixSection s) {
        String id = freshId("row_", s.rows().stream().map(Row::id).toList());
        return withRows(s, plus(s.rows(), new Row(id, Optional.empty(), Optional.empty(), Optional.empty(), Map.of())));
    }

    public static MatrixSection removeRow(MatrixSection s, int row) {
        return s.rows().size() < 2 ? s : withRows(s, without(s.rows(), row));
    }

    public static MatrixSection moveRow(MatrixSection s, int row, int delta) {
        return withRows(s, moved(s.rows(), row, delta));
    }

    public static MatrixSection updateRow(MatrixSection s, int row, UnaryOperator<Row> change) {
        return withRows(s, replaced(s.rows(), row, change.apply(s.rows().get(row))));
    }

    public static MatrixSection addColumn(MatrixSection s) {
        String id = freshId("column_", s.columns().stream().map(Column::id).toList());
        return withColumns(s, plus(s.columns(), new Column(id, s.title() + "." + id, Optional.empty(), Optional.empty())), s.rows());
    }

    /** The ways of a removed column go with it. */
    public static MatrixSection removeColumn(MatrixSection s, int column) {
        if (s.columns().size() < 2) {
            return s;
        }
        String id = s.columns().get(column).id();
        List<Row> rows = new ArrayList<>();
        for (Row row : s.rows()) {
            Map<String, List<Way>> cells = new HashMap<>(row.cells());
            cells.remove(id);
            rows.add(new Row(row.id(), row.title(), row.icon(), row.group(), cells));
        }
        return withColumns(s, without(s.columns(), column), rows);
    }

    public static MatrixSection moveColumn(MatrixSection s, int column, int delta) {
        return withColumns(s, moved(s.columns(), column, delta), s.rows());
    }

    public static MatrixSection updateColumn(MatrixSection s, int column, UnaryOperator<Column> change) {
        return withColumns(s, replaced(s.columns(), column, change.apply(s.columns().get(column))), s.rows());
    }

    private static Row withWays(Row row, String column, List<Way> ways) {
        Map<String, List<Way>> cells = new HashMap<>(row.cells());
        if (ways.isEmpty()) {
            cells.remove(column);
        } else {
            cells.put(column, ways);
        }
        return new Row(row.id(), row.title(), row.icon(), row.group(), cells);
    }

    public static MatrixSection addWay(MatrixSection s, int row, Column column) {
        return updateRow(s, row, target -> {
            List<Way> ways = target.ways(column);
            String id = freshId("way_", ways.stream().map(Way::id).toList());
            return withWays(target, column.id(), plus(ways, new Way(id, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false, List.of(), List.of())));
        });
    }

    public static MatrixSection removeWay(MatrixSection s, int row, Column column, int way) {
        return updateRow(s, row, target -> withWays(target, column.id(), without(target.ways(column), way)));
    }

    public static MatrixSection moveWay(MatrixSection s, int row, Column column, int way, int delta) {
        return updateRow(s, row, target -> withWays(target, column.id(), moved(target.ways(column), way, delta)));
    }

    public static MatrixSection updateWay(MatrixSection s, int row, Column column, int way, UnaryOperator<Way> change) {
        return updateRow(s, row, target -> withWays(target, column.id(), replaced(target.ways(column), way, change.apply(target.ways(column).get(way)))));
    }

    /** Only one way of a cell is the best one. */
    public static MatrixSection markBest(MatrixSection s, int row, Column column, int way) {
        return updateRow(s, row, target -> {
            List<Way> ways = new ArrayList<>();
            List<Way> current = target.ways(column);
            for (int i = 0; i < current.size(); i++) {
                Way each = current.get(i);
                boolean best = i == way && !each.best();
                ways.add(new Way(each.id(), each.name(), each.text(), each.icon(), each.mark(), best, each.needs(), each.links()));
            }
            return withWays(target, column.id(), ways);
        });
    }

    // --- cards ---

    public static CardsSection newCards(ResourceLocation id, String title) {
        return addCard(new CardsSection(id, title, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    private static CardsSection withCards(CardsSection s, List<Card> cards) {
        return new CardsSection(s.id(), s.title(), s.shortTitle(), s.subtitle(), s.icon(), s.groups(), cards);
    }

    public static CardsSection addCard(CardsSection s) {
        String id = freshId("card_", s.cards().stream().map(Card::id).toList());
        return withCards(s, plus(s.cards(), new Card(id, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of())));
    }

    public static CardsSection removeCard(CardsSection s, int card) {
        return s.cards().size() < 2 ? s : withCards(s, without(s.cards(), card));
    }

    public static CardsSection moveCard(CardsSection s, int card, int delta) {
        return withCards(s, moved(s.cards(), card, delta));
    }

    public static CardsSection updateCard(CardsSection s, int card, UnaryOperator<Card> change) {
        return withCards(s, replaced(s.cards(), card, change.apply(s.cards().get(card))));
    }
}
