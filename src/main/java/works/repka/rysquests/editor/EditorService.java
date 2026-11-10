package works.repka.rysquests.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
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
                server.execute(() -> reloadBook(player, saved, payload.rail(), firstSave));
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

    /** A brand new pack, or a folder served by another mod, may need the full reload to be seen at all. */
    private static void reloadBook(ServerPlayer player, @Nullable ResourceLocation saved, List<String> rail, boolean firstSave) {
        if (firstSave) {
            fullReload(player, saved, rail);
            return;
        }
        BookLoader.reloadNow(player.server, book -> {
            if (contains(book, saved, rail)) {
                reply(player, true, "");
            } else {
                fullReload(player, saved, rail);
            }
        });
    }

    private static boolean contains(QuestBook book, @Nullable ResourceLocation saved, List<String> rail) {
        boolean found = saved == null || book.guides().containsKey(saved) || book.sections().containsKey(saved);
        return found && (rail.isEmpty() || rail.equals(book.meta().sections()));
    }

    /** Looks for new packs first, as the /reload command does: the editor's own pack may have only just appeared. */
    private static void fullReload(ServerPlayer player, @Nullable ResourceLocation saved, List<String> rail) {
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
            boolean found = failure == null && contains(BookHolder.current().book(), saved, rail);
            reply(player, found, found ? "" : FAILED);
        }));
    }

    private static void reply(ServerPlayer player, boolean ok, String key) {
        PacketDistributor.sendToPlayer(player, new EditorResultPayload(ok, key));
    }
}
