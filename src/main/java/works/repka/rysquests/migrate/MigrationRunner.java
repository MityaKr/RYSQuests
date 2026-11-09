package works.repka.rysquests.migrate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.migrate.MigrationEngine.Result;
import works.repka.rysquests.migrate.MigrationEngine.State;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;

/** Runs migrations against the live store: a backup first, then replacing the store's contents. */
public final class MigrationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationRunner.class);
    private static final String REPORT = "rysquests-migrate.txt";

    private MigrationRunner() {
    }

    /** A copy that is safe to hand to a background thread. Call on the server thread. */
    public static State snapshot(MinecraftServer server) {
        ProgressStore.MigrationSnapshot snapshot = ProgressStore.get(server).snapshot();
        return new State(copy(snapshot.teams()), copy(snapshot.archive()), new LinkedHashSet<>(snapshot.applied()));
    }

    private static Map<UUID, Map<ResourceLocation, QuestProgress>> copy(Map<UUID, Map<ResourceLocation, QuestProgress>> source) {
        Map<UUID, Map<ResourceLocation, QuestProgress>> copy = new HashMap<>();
        source.forEach((team, quests) -> copy.put(team, new HashMap<>(quests)));
        return copy;
    }

    public static Result run(State state) {
        return MigrationEngine.run(BookHolder.current().migrations(), state);
    }

    /** Applies whatever is new. Runs when the world starts and after each reload. */
    public static void applyNew(MinecraftServer server) {
        Result result = run(snapshot(server));
        if (result.changed()) {
            commit(server, result);
            LOGGER.info("Applied {} progress migrations", result.report().size());
        }
    }

    public static void commit(MinecraftServer server, Result result) {
        backup(server);
        ProgressStore.get(server).restore(result.state().teams(), result.state().archive(), result.state().applied());
        ProgressService.invalidateAll();
        writeReport(server, result, "applied");
    }

    private static void backup(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("rysquests_progress.backup-" + System.currentTimeMillis() + ".dat");
        try {
            CompoundTag tag = ProgressStore.get(server).save(new CompoundTag(), server.registryAccess());
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(tag, file);
        } catch (IOException e) {
            LOGGER.warn("Could not back up quest progress to {}", file, e);
        }
    }

    public static void writeReport(MinecraftServer server, Result result, String mode) {
        Path file = server.getServerDirectory().resolve("logs").resolve(REPORT);
        List<String> lines = new ArrayList<>();
        lines.add(mode + ": " + result.report().size() + " migrations");
        lines.addAll(result.report());
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }
}
