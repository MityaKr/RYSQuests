package works.repka.rysquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

/** Books for tests: from JSON written in the test, or from a folder laid out like {@code data/<ns>/rysquests}. */
public final class TestBooks {
    private TestBooks() {
    }

    public static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    /** Every JSON file of a {@code rysquests} folder, by id relative to it. */
    public static Map<ResourceLocation, JsonElement> folder(Path rysquests, String namespace) throws IOException {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        try (Stream<Path> paths = Files.walk(rysquests)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                String path = rysquests.relativize(file).toString().replace('\\', '/');
                files.put(ResourceLocation.fromNamespaceAndPath(namespace, path.substring(0, path.length() - ".json".length())), JsonParser.parseString(Files.readString(file)));
            }
        }
        return files;
    }

    /** Links the files as the server does; items are taken to exist, since modded ones are absent here. */
    public static BookValidator.Result validate(Map<ResourceLocation, JsonElement> files) {
        return BookValidator.validate(BookParser.parse(files, JsonOps.INSTANCE), id -> true, JsonOps.INSTANCE);
    }

    public static QuestBook book(Map<ResourceLocation, JsonElement> files) {
        return validate(files).book();
    }
}
