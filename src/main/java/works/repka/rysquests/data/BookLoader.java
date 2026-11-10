package works.repka.rysquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.BookReport.Issue;
import works.repka.rysquests.data.BookReport.Severity;
import works.repka.rysquests.network.BookSyncPayload;

/** Loads the book from datapacks. All the work is done off the main thread; applying the result is a single swap. */
public final class BookLoader extends SimplePreparableReloadListener<BookHolder.Loaded> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookLoader.class);
    private static final FileToIdConverter FILES = FileToIdConverter.json("rysquests");

    private final RegistryAccess registries;

    public BookLoader(RegistryAccess registries) {
        this.registries = registries;
    }

    @Override
    protected BookHolder.Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<Issue> readErrors = new ArrayList<>();
        Map<ResourceLocation, JsonElement> files = read(manager, readErrors);
        DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        RawBook parsed = BookParser.parse(files, ops);
        List<Issue> issues = new ArrayList<>(readErrors);
        issues.addAll(parsed.issues());
        RawBook raw = new RawBook(parsed.meta(), parsed.chapters(), parsed.quests(), parsed.guides(), issues, parsed.migrations());
        BookValidator.Result result = BookValidator.validate(raw, BuiltInRegistries.ITEM::containsKey, ops);
        byte[] data = BookSerializer.encode(result.book(), registries);
        return new BookHolder.Loaded(result.book(), result.report(), new BookSyncPayload(result.book().hash(), data), raw.migrations());
    }

    private static Map<ResourceLocation, JsonElement> read(ResourceManager manager, List<Issue> errors) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
            ResourceLocation file = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                files.put(FILES.fileToId(file), JsonParser.parseReader(reader));
            } catch (IOException | RuntimeException e) {
                errors.add(new Issue(Severity.ERROR, "parse_error", file.toString(), String.valueOf(e.getMessage())));
            }
        }
        return files;
    }

    @Override
    protected void apply(BookHolder.Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
        BookHolder.set(loaded);
        LOGGER.info("Loaded book: {} chapters, {} quests, {} guides, {} errors, {} warnings, {} bytes to sync",
                loaded.book().chapters().size(), loaded.book().quests().size(), loaded.book().guides().size(),
                loaded.report().errors(), loaded.report().warnings(), loaded.payload().data().length);
    }
}
