package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A build shown in 3D on a page: one file in {@code rysquests/structures/}, its id taken from the path. {@code mapping}
 * and {@code pattern} are written as Patchouli writes them, so a multiblock can be copied over as it is.
 *
 * @param pattern layers from the top down, each a list of rows
 */
public record StructureDef(ResourceLocation id, Optional<String> title, Map<String, String> mapping, List<List<String>> pattern) {
    private static final char AIR = ' ';
    private static final char ANY = '_';
    private static final char CENTER = '0';

    public static final Codec<StructureDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(StructureDef::id),
            Codec.STRING.optionalFieldOf("title").forGetter(StructureDef::title),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("mapping").forGetter(StructureDef::mapping),
            Codec.STRING.listOf().listOf().fieldOf("pattern").forGetter(StructureDef::pattern)
    ).apply(instance, StructureDef::new));

    /** What goes in one position. */
    public interface Cell {
        void accept(int x, int y, int z, String block);
    }

    public void forEachBlock(Cell cell) {
        for (int layer = 0; layer < pattern.size(); layer++) {
            List<String> rows = pattern.get(layer);
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z);
                for (int x = 0; x < row.length(); x++) {
                    char symbol = row.charAt(x);
                    String block = mapping.get(String.valueOf(symbol));
                    if (symbol != AIR && symbol != ANY && block != null) {
                        cell.accept(x, pattern.size() - 1 - layer, z, block);
                    }
                }
            }
        }
    }

    /** @return whether every layer has the same shape */
    public boolean isRectangular() {
        if (pattern.isEmpty() || pattern.getFirst().isEmpty() || pattern.getFirst().getFirst().isEmpty()) {
            return false;
        }
        int rows = pattern.getFirst().size();
        int columns = pattern.getFirst().getFirst().length();
        return pattern.stream().allMatch(layer -> layer.size() == rows && layer.stream().allMatch(row -> row.length() == columns));
    }

    /** @return the first symbol the mapping does not explain, or empty */
    public Optional<String> unknownSymbol() {
        return pattern.stream().flatMap(List::stream).flatMap(row -> row.chars().mapToObj(symbol -> String.valueOf((char) symbol)))
                .filter(symbol -> symbol.charAt(0) != AIR && symbol.charAt(0) != ANY && !mapping.containsKey(symbol))
                .findFirst();
    }

    /** @return whether there is one anchor, which a hologram in the world needs */
    public boolean hasCenter() {
        return pattern.stream().flatMap(List::stream).flatMapToInt(String::chars).filter(symbol -> symbol == CENTER).count() == 1;
    }
}
