package works.repka.rysquests.integration.ftbq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.editor.EditorService;
import works.repka.rysquests.network.FtbqImportErrorPayload;
import works.repka.rysquests.network.FtbqImportProgressPayload;
import works.repka.rysquests.network.FtbqImportReportPayload;
import works.repka.rysquests.network.FtbqImportStartPayload;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;

/**
 * Runs the FTB Quests import for the book's import wizard and for the command: read, convert, write, reload, and the progress
 * of FTB Quests teams if asked. A file that cannot be read stops it before anything is written, so a book is never made by half.
 */
public final class FtbqImportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FtbqImportService.class);
    private static final String DEFAULT_NAMESPACE = "ftbq";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    /** Who hears how the import goes. Called on the main thread. */
    public interface Listener {
        void progress(String phase, int done, int total);

        /** Nothing was written. */
        void failed(FtbqFiles.Failure failure, int chaptersRead, int chaptersTotal);

        /** The new book is loaded. */
        void done(ImportReport report);
    }

    private FtbqImportService() {
    }

    /** The folder of the FTB Quests book. */
    public static Path questsFolder() {
        return FMLPaths.CONFIGDIR.get().resolve("ftbquests").resolve("quests");
    }

    /** The FTB Quests folder as the game folder names it, which the files of a failure are relative to. */
    public static String folderName() {
        return relativeToGame(questsFolder());
    }

    private static String relativeToGame(Path path) {
        Path game = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        Path absolute = path.toAbsolutePath().normalize();
        return (absolute.startsWith(game) ? game.relativize(absolute) : absolute).toString().replace('\\', '/');
    }

    public static void handle(ServerPlayer player, FtbqImportStartPayload payload) {
        if (!player.hasPermissions(BookHolder.OPERATOR)) {
            return;
        }
        String namespace = payload.namespace().isBlank() ? DEFAULT_NAMESPACE : payload.namespace();
        if (!namespace.matches("[a-z0-9_.-]+")) {
            PacketDistributor.sendToPlayer(player, new FtbqImportErrorPayload(new FtbqFiles.Failure("", 0, "bad namespace", false), folderName(), 0, 0));
            return;
        }
        run(player.server, namespace, new LinkedHashSet<>(payload.skip()), payload.withProgress(), new Listener() {
            @Override
            public void progress(String phase, int done, int total) {
                PacketDistributor.sendToPlayer(player, new FtbqImportProgressPayload(phase, done, total));
            }

            @Override
            public void failed(FtbqFiles.Failure failure, int chaptersRead, int chaptersTotal) {
                PacketDistributor.sendToPlayer(player, new FtbqImportErrorPayload(failure, folderName(), chaptersRead, chaptersTotal));
            }

            @Override
            public void done(ImportReport report) {
                PacketDistributor.sendToPlayer(player, new FtbqImportReportPayload(report));
            }
        });
    }

    /** @param skip files of the FTB book to leave out, after the author agreed to go on without them */
    public static void run(MinecraftServer server, String namespace, Set<String> skip, boolean withProgress, Listener listener) {
        if (!RUNNING.compareAndSet(false, true)) {
            listener.failed(new FtbqFiles.Failure("", 0, "an import is already running", false), 0, 0);
            return;
        }
        Path quests = questsFolder();
        Path root = ImportedPack.root();
        listener.progress(FtbqImportProgressPayload.READ, 0, 0);
        Util.backgroundExecutor().execute(() -> {
            try {
                if (!Files.isDirectory(quests)) {
                    finishFailed(server, listener, new FtbqFiles.Failure("", 0, "no FTB Quests book", false), 0, 0);
                    return;
                }
                FtbqFiles.Read read = FtbqFiles.read(quests, skip);
                if (!read.failures().isEmpty()) {
                    finishFailed(server, listener, read.failures().getFirst(), read.chaptersRead(), read.chaptersTotal());
                    return;
                }
                server.execute(() -> listener.progress(FtbqImportProgressPayload.CONVERT, read.chaptersRead(), read.chaptersTotal()));
                FtbqImporter.Result result;
                try {
                    result = FtbqImporter.convert(read.source(), namespace, FtbqFiles.readIds(root));
                } catch (FtbqImporter.ChapterError e) {
                    finishFailed(server, listener, new FtbqFiles.Failure(e.file(), 0, String.valueOf(e.getCause().getMessage()), true), read.chaptersRead(), read.chaptersTotal());
                    return;
                }
                server.execute(() -> listener.progress(FtbqImportProgressPayload.WRITE, 0, result.files().size()));
                FtbqFiles.write(root, result);
                ImportReport report = result.report().withFiles(List.copyOf(skip), relativeToGame(root.resolve(FtbqFiles.reportFileName())));
                LOGGER.info("Imported {} chapters and {} quests from FTB Quests, {} hidden until checked", result.chapters(), result.quests(), report.hiddenQuests());
                server.execute(() -> reload(server, withProgress, report, listener));
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("FTB Quests import failed", e);
                finishFailed(server, listener, new FtbqFiles.Failure("", 0, String.valueOf(e.getMessage()), false), 0, 0);
            }
        });
    }

    private static void finishFailed(MinecraftServer server, Listener listener, FtbqFiles.Failure failure, int read, int total) {
        server.execute(() -> {
            RUNNING.set(false);
            listener.failed(failure, read, total);
        });
    }

    /** The imported pack may be new, so the reload looks for new packs first. */
    private static void reload(MinecraftServer server, boolean withProgress, ImportReport report, Listener listener) {
        listener.progress(FtbqImportProgressPayload.RELOAD, 0, 0);
        EditorService.reloadWithNewPacks(server).whenComplete((done, failure) -> server.execute(() -> {
            if (failure != null) {
                LOGGER.error("Could not reload after the FTB Quests import", failure);
                RUNNING.set(false);
                listener.failed(new FtbqFiles.Failure("", 0, String.valueOf(failure.getMessage()), false), report.chapters(), report.chapters());
            } else if (withProgress) {
                importProgress(server, report, listener);
            } else {
                RUNNING.set(false);
                listener.done(report);
            }
        }));
    }

    private static void importProgress(MinecraftServer server, ImportReport report, Listener listener) {
        listener.progress(FtbqImportProgressPayload.PROGRESS, 0, 0);
        var book = BookHolder.current().book();
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("ftbquests");
        Util.backgroundExecutor().execute(() -> {
            try {
                Map<UUID, Map<ResourceLocation, QuestProgress>> teams = FtbqProgress.read(dir, FtbqFiles.readIds(ImportedPack.root()), book);
                server.execute(() -> {
                    // ADR-65: only quests without a record here come over, so importing the book again loses no progress
                    ProgressStore store = ProgressStore.get(server);
                    FtbqMerge.Plan plan = FtbqMerge.plan(teams, store.allProgress());
                    if (!plan.empty()) {
                        works.repka.rysquests.migrate.MigrationRunner.backup(server);
                        plan.additions().forEach(store::put);
                    }
                    ProgressService.invalidateAll();
                    server.getPlayerList().getPlayers().forEach(ProgressService::sendAll);
                    RUNNING.set(false);
                    listener.done(report.withTeams(teams.size()));
                });
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("FTB Quests progress import failed", e);
                // The book itself came across; only the progress did not.
                server.execute(() -> {
                    RUNNING.set(false);
                    listener.done(report);
                });
            }
        });
    }
}
