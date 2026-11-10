package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/** The "matrix" template: rows by columns, with ways of doing something in each cell. Nothing about it is kept on the server. */
public record MatrixSection(
        ResourceLocation id,
        String title,
        Optional<String> shortTitle,
        Optional<String> subtitle,
        Optional<ResourceLocation> icon,
        Spoilers spoilers,
        boolean compact,
        Optional<String> search,
        Optional<String> rowTitle,
        List<String> groups,
        List<Column> columns,
        List<Row> rows) implements SectionDef {
    public static final String TEMPLATE = "matrix";
    private static final String PIN_SEPARATOR = "#";

    /** What a player sees of the columns that are still closed. */
    public enum Spoilers implements StringRepresentable {
        SHOW("show"), DIM("dim"), HIDE("hide");

        static final Codec<Spoilers> CODEC = StringRepresentable.fromValues(Spoilers::values);
        private final String name;

        Spoilers(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum LinkType implements StringRepresentable {
        QUEST("quest"), RECIPE("recipe"), GUIDE("guide");

        static final Codec<LinkType> CODEC = StringRepresentable.fromValues(LinkType::values);
        private final String name;

        LinkType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Link(LinkType type, String id) {
        static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LinkType.CODEC.fieldOf("type").forGetter(Link::type),
                Codec.STRING.fieldOf("id").forGetter(Link::id)
        ).apply(instance, Link::new));
    }

    /** @param stage when set, the column is closed until the player has it */
    public record Column(String id, String title, Optional<String> hint, Optional<String> stage) {
        static final Codec<Column> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Column::id),
                Codec.STRING.fieldOf("title").forGetter(Column::title),
                Codec.STRING.optionalFieldOf("hint").forGetter(Column::hint),
                Codec.STRING.optionalFieldOf("stage").forGetter(Column::stage)
        ).apply(instance, Column::new));
    }

    /** One entry of a cell. Its texts are lang keys built from the ids unless written out. */
    public record Way(String id, Optional<String> name, Optional<String> text, Optional<ResourceLocation> icon, Optional<String> mark,
            boolean best, List<ResourceLocation> needs, List<Link> links) {
        static final Codec<Way> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Way::id),
                Codec.STRING.optionalFieldOf("name").forGetter(Way::name),
                Codec.STRING.optionalFieldOf("text").forGetter(Way::text),
                ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(Way::icon),
                Codec.STRING.optionalFieldOf("mark").forGetter(Way::mark),
                Codec.BOOL.optionalFieldOf("best", false).forGetter(Way::best),
                ResourceLocation.CODEC.listOf().optionalFieldOf("needs", List.of()).forGetter(Way::needs),
                Link.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(Way::links)
        ).apply(instance, Way::new));
    }

    public record Row(String id, Optional<String> title, Optional<ResourceLocation> icon, Optional<String> group, Map<String, List<Way>> cells) {
        static final Codec<Row> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Row::id),
                Codec.STRING.optionalFieldOf("title").forGetter(Row::title),
                ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(Row::icon),
                Codec.STRING.optionalFieldOf("group").forGetter(Row::group),
                Codec.unboundedMap(Codec.STRING, Way.CODEC.listOf()).optionalFieldOf("cells", Map.of()).forGetter(Row::cells)
        ).apply(instance, Row::new));

        public List<Way> ways(Column column) {
            return cells.getOrDefault(column.id(), List.of());
        }
    }

    public static final MapCodec<MatrixSection> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(MatrixSection::id),
            Codec.STRING.fieldOf("title").forGetter(MatrixSection::title),
            Codec.STRING.optionalFieldOf("short_title").forGetter(MatrixSection::shortTitle),
            Codec.STRING.optionalFieldOf("subtitle").forGetter(MatrixSection::subtitle),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(MatrixSection::icon),
            Spoilers.CODEC.optionalFieldOf("spoilers", Spoilers.DIM).forGetter(MatrixSection::spoilers),
            Codec.BOOL.optionalFieldOf("compact", false).forGetter(MatrixSection::compact),
            Codec.STRING.optionalFieldOf("search").forGetter(MatrixSection::search),
            Codec.STRING.optionalFieldOf("row_title").forGetter(MatrixSection::rowTitle),
            Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(MatrixSection::groups),
            Column.CODEC.listOf().fieldOf("columns").forGetter(MatrixSection::columns),
            Row.CODEC.listOf().fieldOf("rows").forGetter(MatrixSection::rows)
    ).apply(instance, MatrixSection::new));

    @Override
    public String template() {
        return TEMPLATE;
    }

    @Override
    public Set<String> textKeys() {
        Set<String> keys = commonKeys();
        search.ifPresent(keys::add);
        rowTitle.ifPresent(keys::add);
        groups.forEach(group -> keys.add(groupTitle(group)));
        for (Column column : columns) {
            keys.add(column.title());
            column.hint().ifPresent(keys::add);
        }
        for (Row row : rows) {
            keys.add(rowTitle(row));
            for (Column column : columns) {
                for (Way way : row.ways(column)) {
                    keys.add(wayName(row, column, way));
                    keys.add(wayText(row, column, way));
                }
            }
        }
        return keys;
    }

    public String rowTitle(Row row) {
        return row.title().orElse(title + "." + row.id() + ".name");
    }

    public String groupTitle(String group) {
        return title + ".group." + group;
    }

    public String wayName(Row row, Column column, Way way) {
        return way.name().orElse(wayKey(row, column, way) + ".name");
    }

    public String wayText(Row row, Column column, Way way) {
        return way.text().orElse(wayKey(row, column, way) + ".text");
    }

    private String wayKey(Row row, Column column, Way way) {
        return title + "." + row.id() + "." + column.id() + "." + way.id();
    }

    /** How a way pinned to the screen is remembered. */
    public String pinKey(Row row, Column column, Way way) {
        return String.join(PIN_SEPARATOR, id.toString(), row.id(), column.id(), way.id());
    }

    /** The parts of a pinned way. */
    public record Pinned(MatrixSection section, Row row, Column column, Way way) {
    }

    /** @return the way a pin key names, if the book still has it */
    public static Optional<Pinned> findPinned(QuestBook book, String key) {
        String[] parts = key.split(PIN_SEPARATOR, -1);
        ResourceLocation sectionId = parts.length == 4 ? ResourceLocation.tryParse(parts[0]) : null;
        if (sectionId == null || !(book.sections().get(sectionId) instanceof MatrixSection section)) {
            return Optional.empty();
        }
        for (Row row : section.rows) {
            for (Column column : section.columns) {
                if (row.id().equals(parts[1]) && column.id().equals(parts[2])) {
                    for (Way way : row.ways(column)) {
                        if (way.id().equals(parts[3])) {
                            return Optional.of(new Pinned(section, row, column, way));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
