package works.repka.rysquests.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.BookHash;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.BookLayout;
import works.repka.rysquests.data.BookLoader;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.network.EditorResultPayload;
import works.repka.rysquests.network.EditorSavePayload;
import works.repka.rysquests.network.LayoutSavePayload;
import works.repka.rysquests.network.MapFormPayload;
import works.repka.rysquests.network.NewBookPayload;
import works.repka.rysquests.network.PublishPayload;
import works.repka.rysquests.network.QuestEditorSavePayload;
import works.repka.rysquests.progress.ProgressRemap;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;

/** Saves what an author made in the book. Only operators may, and the server checks everything again. */
public final class EditorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditorService.class);
    private static final int PERMISSION = BookHolder.OPERATOR;
    private static final String FAILED = "rysquests.editor.error.failed";
    /** The chapter changed since the editing began, by hand or by an operator whose name is not known. */
    public static final String CONFLICT_FILES = EditorResultPayload.CONFLICT + "_files";
    /** Who saved each chapter last since the server started, to name them when a save is refused. */
    private static final Map<ResourceLocation, String> LAST_SAVED_BY = new java.util.concurrent.ConcurrentHashMap<>();

    private EditorService() {
    }

    public static void handle(ServerPlayer player, EditorSavePayload payload) {
        if (!player.hasPermissions(PERMISSION)) {
            reply(player, false, "rysquests.editor.error.denied");
            return;
        }
        MinecraftServer server = player.server;
        ResourceLocation saved = payload.guide().map(Guide::id).orElseGet(() -> payload.section().map(SectionDef::id).orElse(null));
        Path root = EditorFolder.root();
        boolean createPack = !EditorFolder.servedByKubeJs();
        boolean firstSave = createPack && !Files.isRegularFile(root.resolve("pack.mcmeta"));
        String bookNamespace = bookNamespace(server, saved);
        BookMeta meta = BookHolder.current().book().meta();
        CompletableFuture.runAsync(() -> {
            try {
                if (saved != null) {
                    EditorFiles.save(root, payload.guide(), payload.section(), payload.language(), payload.texts(), payload.images(), createPack);
                }
                if (!payload.rail().isEmpty()) {
                    EditorFiles.saveBook(root, bookNamespace, meta, payload.rail(), createPack);
                }
                server.execute(() -> reloadBook(player, book -> contains(book, saved, payload.rail()), firstSave));
            } catch (EditorFiles.Rejected e) {
                server.execute(() -> reply(player, false, e.key()));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not save {}", saved, e);
                server.execute(() -> reply(player, false, FAILED));
            }
        }, Util.ioPool());
    }

    /** The namespace of the book's settings, so that a new file replaces them rather than competing with them. */
    private static String bookNamespace(MinecraftServer server, @Nullable ResourceLocation fallback) {
        return server.getResourceManager().listResources("rysquests", id -> id.getPath().equals("rysquests/book.json")).keySet().stream()
                .map(ResourceLocation::getNamespace).sorted().findFirst()
                .orElse(fallback != null ? fallback.getNamespace() : RYSQuests.MODID);
    }

    public static void handleQuests(ServerPlayer player, QuestEditorSavePayload payload) {
        if (!player.hasPermissions(PERMISSION)) {
            reply(player, false, "rysquests.editor.error.denied");
            return;
        }
        MinecraftServer server = player.server;
        Path root = EditorFolder.root();
        boolean createPack = !EditorFolder.servedByKubeJs();
        boolean firstSave = createPack && !Files.isRegularFile(root.resolve("pack.mcmeta"));
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        ResourceManager resources = server.getResourceManager();
        QuestBook before = BookHolder.current().book();
        ResourceLocation chapterId = payload.chapter().id();
        // Two operators must not overwrite each other: the chapter has to be as it was when this editing began.
        if (!payload.force() && !BookHash.chapterVersion(before, chapterId, ops).equals(payload.baseVersion())) {
            String other = LAST_SAVED_BY.get(chapterId);
            if (other != null && !other.equals(player.getGameProfile().getName())) {
                PacketDistributor.sendToPlayer(player, new EditorResultPayload(false, EditorResultPayload.CONFLICT, List.of(other)));
            } else {
                PacketDistributor.sendToPlayer(player, new EditorResultPayload(false, CONFLICT_FILES, List.of()));
            }
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                EditorFiles.saveChapter(root, payload.chapter(), payload.chapterChanged(), payload.quests(), questFiles(resources), payload.language(), payload.texts(), ops, createPack);
                server.execute(() -> {
                    LAST_SAVED_BY.put(chapterId, player.getGameProfile().getName());
                    followTasks(server, before, payload.quests());
                    reloadBook(player, book -> book.chapters().containsKey(chapterId) && payload.quests().stream().allMatch(quest -> book.quests().containsKey(quest.id())), firstSave);
                });
            } catch (EditorFiles.Rejected e) {
                server.execute(() -> reply(player, false, e.key()));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not save chapter {}", payload.chapter().id(), e);
                server.execute(() -> reply(player, false, FAILED));
            }
        }, Util.ioPool());
    }

    /** Progress follows its own tasks and rewards when the editor moved, added or removed some (review C1). */
    private static void followTasks(MinecraftServer server, QuestBook before, List<Quest> saved) {
        ProgressStore store = ProgressStore.get(server);
        boolean moved = false;
        for (Quest quest : saved) {
            Quest old = before.quests().get(quest.id());
            if (old == null) {
                continue;
            }
            ProgressRemap.Mapping mapping = ProgressRemap.between(old, quest);
            if (!mapping.identity()) {
                store.remapQuest(quest.id(), progress -> ProgressRemap.apply(progress, mapping));
                LOGGER.debug("Moved progress of {} with {}", quest.id(), mapping);
                moved = true;
            }
        }
        if (moved) {
            ProgressService.invalidateAll();
        }
    }

    /** Opens the book to players or closes it again. Operators keep seeing it either way. */
    public static void handlePublish(ServerPlayer player, PublishPayload payload) {
        if (!player.hasPermissions(PERMISSION)) {
            reply(player, false, "rysquests.editor.error.denied");
            return;
        }
        MinecraftServer server = player.server;
        Path root = EditorFolder.root();
        boolean createPack = !EditorFolder.servedByKubeJs();
        boolean firstSave = createPack && !Files.isRegularFile(root.resolve("pack.mcmeta"));
        String namespace = bookNamespace(server, null);
        BookMeta meta = BookHolder.current().book().meta().withPublished(payload.open());
        CompletableFuture.runAsync(() -> {
            try {
                EditorFiles.saveMeta(root, namespace, meta, createPack);
                server.execute(() -> {
                    LOGGER.info("{} {} the book to players", player.getGameProfile().getName(), payload.open() ? "opened" : "closed");
                    reloadBook(player, book -> book.meta().published() == payload.open(), firstSave);
                });
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not change the publication of the book", e);
                server.execute(() -> reply(player, false, FAILED));
            }
        }, Util.ioPool());
    }

    /** Sets the form of the map, or leaves it to the rule with {@code auto}: the form choice after the FTB Quests import. */
    public static void handleMapForm(ServerPlayer player, MapFormPayload payload) {
        if (!player.hasPermissions(PERMISSION)) {
            reply(player, false, "rysquests.editor.error.denied");
            return;
        }
        saveMetaAndReload(player, BookHolder.current().book().meta().withMapForm(payload.form()), book -> book.meta().mapForm() == payload.form());
    }

    /**
     * Makes a book from scratch: its name, icon and map form, closed to players (picture 09a). A book that already has quests is
     * never overwritten this way.
     */
    public static void handleNewBook(ServerPlayer player, NewBookPayload payload) {
        if (!player.hasPermissions(PERMISSION)) {
            reply(player, false, "rysquests.editor.error.denied");
            return;
        }
        String title = payload.title().strip();
        if (title.isEmpty() || title.length() > NewBookPayload.MAX_TITLE || !BookHolder.current().book().quests().isEmpty()) {
            reply(player, false, "rysquests.editor.error.invalid");
            return;
        }
        BookMeta base = BookMeta.DEFAULT;
        BookMeta meta = new BookMeta(base.progressMode(), base.defaultView(), base.defaultTheme(), java.util.Optional.of(title), payload.icon(),
                List.of(), base.hud(), List.of(), List.of(), payload.form(), false);
        saveMetaAndReload(player, meta, book -> book.meta().title().filter(title::equals).isPresent());
    }

    private static void saveMetaAndReload(ServerPlayer player, BookMeta meta, Predicate<QuestBook> found) {
        MinecraftServer server = player.server;
        Path root = EditorFolder.root();
        boolean createPack = !EditorFolder.servedByKubeJs();
        boolean firstSave = createPack && !Files.isRegularFile(root.resolve("pack.mcmeta"));
        String namespace = bookNamespace(server, null);
        CompletableFuture.runAsync(() -> {
            try {
                EditorFiles.saveMeta(root, namespace, meta, createPack);
                server.execute(() -> reloadBook(player, found, firstSave));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not save the book's settings", e);
                server.execute(() -> reply(player, false, FAILED));
            }
        }, Util.ioPool());
    }

    /** Writes the map laid out by the client of an operator as layout.json beside the book, and sends it to everyone. */
    public static void handleLayout(ServerPlayer player, LayoutSavePayload payload) {
        if (!player.hasPermissions(PERMISSION)) {
            reply(player, false, "rysquests.editor.error.denied");
            return;
        }
        BookLayout layout = payload.layout();
        if (layout.form() == MapForm.AUTO) {
            reply(player, false, "rysquests.editor.error.invalid");
            return;
        }
        // Laid out for a book that has changed since: writing it would only make a stale file.
        if (!layout.hash().equals(BookHolder.current().book().layoutHash())) {
            reply(player, false, "rysquests.layout.error.stale");
            return;
        }
        MinecraftServer server = player.server;
        Path root = EditorFolder.root();
        boolean createPack = !EditorFolder.servedByKubeJs();
        boolean firstSave = createPack && !Files.isRegularFile(root.resolve("pack.mcmeta"));
        String namespace = bookNamespace(server, null);
        CompletableFuture.runAsync(() -> {
            try {
                EditorFiles.saveLayout(root, namespace, layout, createPack);
                server.execute(() -> reloadBook(player, book -> book.freshLayout().isPresent(), firstSave));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not save the map layout", e);
                server.execute(() -> reply(player, false, FAILED));
            }
        }, Util.ioPool());
    }

    private static EditorFiles.QuestFiles questFiles(ResourceManager resources) {
        Map<ResourceLocation, ResourceLocation> quests = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> chapters = new HashMap<>();
        Set<ResourceLocation> shared = new HashSet<>();
        resources.listResources("rysquests/chapters", file -> file.getPath().endsWith(".json")).forEach((file, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (json.isJsonArray()) {
                    json.getAsJsonArray().forEach(item -> shared.add(ResourceLocation.parse(item.getAsJsonObject().get("id").getAsString())));
                } else {
                    ResourceLocation id = ResourceLocation.parse(json.getAsJsonObject().get("id").getAsString());
                    (file.getPath().endsWith("/_chapter.json") ? chapters : quests).put(id, file);
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("Skipped {} while looking for quest files", file, e);
            }
        });
        return new EditorFiles.QuestFiles(quests, chapters, shared);
    }

    /** A brand new pack, or a folder served by another mod, may need the full reload to be seen at all. */
    private static void reloadBook(ServerPlayer player, Predicate<QuestBook> found, boolean firstSave) {
        if (firstSave) {
            fullReload(player, found);
            return;
        }
        BookLoader.reloadNow(player.server, book -> {
            if (found.test(book)) {
                reply(player, true, "");
            } else {
                fullReload(player, found);
            }
        });
    }

    private static boolean contains(QuestBook book, @Nullable ResourceLocation saved, List<String> rail) {
        boolean found = saved == null || book.guides().containsKey(saved) || book.sections().containsKey(saved);
        return found && (rail.isEmpty() || rail.equals(book.meta().sections()));
    }

    /** Looks for new packs first, as the /reload command does: the editor's own pack may have only just appeared. */
    private static void fullReload(ServerPlayer player, Predicate<QuestBook> wanted) {
        MinecraftServer server = player.server;
        reloadWithNewPacks(server).whenComplete((done, failure) -> server.execute(() -> {
            if (failure != null) {
                LOGGER.error("Could not reload after saving", failure);
            }
            boolean found = failure == null && wanted.test(BookHolder.current().book());
            reply(player, found, found ? "" : FAILED);
        }));
    }

    /**
     * Reloads all data with packs that appeared since, as {@code /reload} does ({@code ReloadCommand.discoverNewPacks}). Runs on the
     * main thread. A pack switched off stays off: {@code /datapack disable} reloads, and every reload writes the packs left out into
     * the world's disabled list ({@code MinecraftServer.reloadResources}), which is what is skipped here. Of the packs that appeared
     * since (a fresh import), only those the game would switch on itself when a world loads are switched on: their source adds them
     * automatically and the world has their features ({@code MinecraftServer.configurePackRepository}). {@code /reload} switches on
     * every new pack, also one meant to stay off until asked for (a {@code FEATURE} pack such as the dev example's broken book).
     */
    public static CompletableFuture<Void> reloadWithNewPacks(MinecraftServer server) {
        PackRepository packs = server.getPackRepository();
        List<String> before = List.copyOf(packs.getSelectedIds());
        List<String> selected = new ArrayList<>(before);
        packs.reload();
        Collection<String> disabled = server.getWorldData().getDataConfiguration().dataPacks().getDisabled();
        net.minecraft.world.flag.FeatureFlagSet features = server.getWorldData().enabledFeatures();
        for (net.minecraft.server.packs.repository.Pack pack : packs.getAvailablePacks()) {
            String id = pack.getId();
            if (!disabled.contains(id) && !selected.contains(id) && pack.getPackSource().shouldAddAutomatically()
                    && pack.getRequestedFeatures().isSubsetOf(features)) {
                selected.add(id);
            }
        }
        net.neoforged.neoforge.resource.ResourcePackLoader.reorderNewlyDiscoveredPacks(selected, before, packs);
        return server.reloadResources(selected);
    }

    private static void reply(ServerPlayer player, boolean ok, String key) {
        PacketDistributor.sendToPlayer(player, new EditorResultPayload(ok, key));
    }
}
