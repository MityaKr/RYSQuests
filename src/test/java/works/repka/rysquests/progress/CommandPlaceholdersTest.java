package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandPlaceholdersTest {
    @Test
    void knownNamesAreFilledAndTheRestIsLeftAlone() {
        Map<String, String> values = Map.of("p", "Lena", "x", "12");

        assertEquals("give Lena minecraft:diamond 2", CommandPlaceholders.fill("give {p} minecraft:diamond 2", values::get));
        assertEquals("tp Lena 12 {unknown}", CommandPlaceholders.fill("tp {p} {x} {unknown}", values::get));
        assertEquals("tellraw @a {\"text\":\"hi\"}", CommandPlaceholders.fill("tellraw @a {\"text\":\"hi\"}", values::get));
        assertEquals("say $1 \\ {p}", CommandPlaceholders.fill("say $1 \\ {p}", name -> null));
        // A value with characters the replacement syntax treats specially goes in as it is.
        assertEquals("say a$1\\b", CommandPlaceholders.fill("say {p}", name -> "a$1\\b"));
    }
}
