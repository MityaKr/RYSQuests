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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.BookReport.Issue;
import works.repka.rysquests.data.BookReport.Severity;
import works.repka.rysquests.editor.EditorFolder;
import works.repka.rysquests.network.BookSyncPayload;
import works.repka.rysquests.progress.ProgressService;

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
        RawBook raw = parsed.withIssues(issues);
        BookValidator.Result result = BookValidator.validate(raw, BuiltInRegistries.ITEM::containsKey, ops);
        QuestBook book = result.book().withAssets(BookAssets.read(EditorFolder.root(), result.book()));
        BookSyncPayload payload = new BookSyncPayload(book.hash(), BookSerializer.encode(book, registries));
        QuestBook shown = book.playerView();
        BookSyncPayload playerPayload = shown == book ? payload : new BookSyncPayload(book.hash(), BookSerializer.encode(shown, registries));
        return new BookHolder.Loaded(book, result.report(), payload, playerPayload, raw.migrations());
    }

    /** Reads only the book again and sends it to everyone, which is far quicker than reloading all the data of a large pack. */
    public static void reloadNow(MinecraftServer server, Consumer<QuestBook> done) {
        BookLoader loader = new BookLoader(server.registryAccess());
        CompletableFuture.supplyAsync(() -> loader.prepare(server.getResourceManager(), InactiveProfiler.INSTANCE), Util.backgroundExecutor())
                .thenAcceptAsync(loaded -> {
                    loader.apply(loaded, server.getResourceManager(), InactiveProfiler.INSTANCE);
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        PacketDistributor.sendToPlayer(player, loaded.payloadFor(player));
                        ProgressService.sendAll(player);
                    }
                    done.accept(loaded.book());
                }, server);
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
        LOGGER.info("Loaded book: {} chapters, {} quests, {} guides, {} sections, {} errors, {} warnings, {} bytes to sync",
                loaded.book().chapters().size(), loaded.book().quests().size(), loaded.book().guides().size(), loaded.book().sections().size(),
                loaded.report().errors(), loaded.report().warnings(), loaded.payload().data().length);
    }
}
