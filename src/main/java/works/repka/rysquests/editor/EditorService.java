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
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.BookLoader;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.network.EditorResultPayload;
import works.repka.rysquests.network.EditorSavePayload;
import works.repka.rysquests.network.QuestEditorSavePayload;

/** Saves what an author made in the book. Only operators may, and the server checks everything again. */
public final class EditorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditorService.class);
    private static final int PERMISSION = 2;
    private static final String FAILED = "rysquests.editor.error.failed";

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
        CompletableFuture.runAsync(() -> {
            try {
                EditorFiles.saveChapter(root, payload.chapter(), payload.chapterChanged(), payload.quests(), questFiles(resources), payload.language(), payload.texts(), ops, createPack);
                server.execute(() -> reloadBook(player, book -> book.chapters().containsKey(payload.chapter().id()) && payload.quests().stream().allMatch(quest -> book.quests().containsKey(quest.id())), firstSave));
            } catch (EditorFiles.Rejected e) {
                server.execute(() -> reply(player, false, e.key()));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not save chapter {}", payload.chapter().id(), e);
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
        PackRepository packs = server.getPackRepository();
        List<String> selected = new ArrayList<>(packs.getSelectedIds());
        packs.reload();
        Collection<String> disabled = server.getWorldData().getDataConfiguration().dataPacks().getDisabled();
        for (String id : packs.getAvailableIds()) {
            if (!disabled.contains(id) && !selected.contains(id)) {
                selected.add(id);
            }
        }
        server.reloadResources(selected).whenComplete((done, failure) -> server.execute(() -> {
            if (failure != null) {
                LOGGER.error("Could not reload after saving", failure);
            }
            boolean found = failure == null && wanted.test(BookHolder.current().book());
            reply(player, found, found ? "" : FAILED);
        }));
    }

    private static void reply(ServerPlayer player, boolean ok, String key) {
        PacketDistributor.sendToPlayer(player, new EditorResultPayload(ok, key));
    }
}
