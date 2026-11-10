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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.BookLoader;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.QuestBook;
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
        ResourceLocation saved = payload.guide().map(Guide::id).orElseGet(() -> payload.section().map(PagesSection::id).orElse(null));
        Path root = EditorFolder.root();
        boolean firstSave = !Files.isRegularFile(root.resolve("pack.mcmeta")) && !EditorFolder.servedByKubeJs();
        CompletableFuture.runAsync(() -> {
            try {
                EditorFiles.save(root, payload.guide(), payload.section(), payload.language(), payload.texts(), payload.images(), !EditorFolder.servedByKubeJs());
                server.execute(() -> reloadBook(player, saved, firstSave));
            } catch (EditorFiles.Rejected e) {
                server.execute(() -> reply(player, false, e.key()));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not save {}", saved, e);
                server.execute(() -> reply(player, false, FAILED));
            }
        }, Util.ioPool());
    }

    /** A brand new pack, or a folder served by another mod, may need the full reload to be seen at all. */
    private static void reloadBook(ServerPlayer player, ResourceLocation saved, boolean firstSave) {
        if (firstSave) {
            fullReload(player, saved);
            return;
        }
        BookLoader.reloadNow(player.server, book -> {
            if (contains(book, saved)) {
                reply(player, true, "");
            } else {
                fullReload(player, saved);
            }
        });
    }

    private static boolean contains(QuestBook book, ResourceLocation saved) {
        return book.guides().containsKey(saved) || book.sections().containsKey(saved);
    }

    /** Looks for new packs first, as the /reload command does: the editor's own pack may have only just appeared. */
    private static void fullReload(ServerPlayer player, ResourceLocation saved) {
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
            boolean found = failure == null && contains(BookHolder.current().book(), saved);
            reply(player, found, found ? "" : FAILED);
        }));
    }

    private static void reply(ServerPlayer player, boolean ok, String key) {
        PacketDistributor.sendToPlayer(player, new EditorResultPayload(ok, key));
    }
}
