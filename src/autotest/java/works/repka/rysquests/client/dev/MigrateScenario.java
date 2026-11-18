package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.fail;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.ryscore.migrate.Migrations;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.integration.ftbq.QuestsMigration;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;

/**
 * {@code migrate} (run with {@code runRepysAutotest -Pautotest=migrate}): the {@code quests} step of the move from FTB on the copy of
 * the RepYS server's files ({@code rysquests.autotest.fixtures}, default {@code _shared/_saved/fixtures/ftb-2026-11-18}) and the
 * RepYS book. The FTB files and the id table go into the test world and the copy's config, the step runs by the core's command twice:
 * the first time VENIK's progress comes over (or, in a world kept from an earlier run, nothing of its own is changed), the second time
 * the core says it is done and nothing changes. A mark of an earlier run is moved aside first, not deleted.
 */
final class MigrateScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrateScenario.class);
    private static final UUID VENIK = UUID.fromString("0c383da8-5fb5-41ef-96be-7cae45e6e640");
    private static final AtomicReference<Migrations.Outcome> OUTCOME = new AtomicReference<>();
    private static final AtomicReference<Map<ResourceLocation, QuestProgress>> BEFORE = new AtomicReference<>();
    private static final AtomicReference<Boolean> SAME = new AtomicReference<>();

    private MigrateScenario() {
    }

    static void check() {
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        step("put the FTB files of the RepYS server in place", MigrateScenario::prepare, 5);
        step("run the quests step", () -> server(() -> OUTCOME.set(Migrations.runManual(QuestsMigration.ID, mc().getSingleplayerServer()))), 5);
        expect("the step ran", () -> OUTCOME.get() != null);
        step("check the first run", () -> {
            Migrations.Outcome o = OUTCOME.get();
            LOGGER.info("[autotest] migrate first: {}", o);
            if (o.status() != Migrations.Status.DONE && o.status() != Migrations.Status.PARTIAL) {
                fail("the quests step did not run: " + o);
            }
            OUTCOME.set(null);
            server(() -> BEFORE.set(new HashMap<>(ProgressStore.get(mc().getSingleplayerServer()).team(VENIK))));
        }, 5);
        expect("VENIK's progress is here", () -> BEFORE.get() != null);
        step("check VENIK", () -> {
            LOGGER.info("[autotest] migrate VENIK records: {}", BEFORE.get().size());
            if (BEFORE.get().size() < 100) {
                fail("VENIK has only " + BEFORE.get().size() + " records");
            }
        }, 1);
        step("run it again", () -> server(() -> {
            OUTCOME.set(Migrations.runManual(QuestsMigration.ID, mc().getSingleplayerServer()));
            SAME.set(BEFORE.get().equals(ProgressStore.get(mc().getSingleplayerServer()).team(VENIK)));
        }), 5);
        expect("the second run answered", () -> OUTCOME.get() != null && SAME.get() != null);
        step("check the second run", () -> {
            Migrations.Outcome o = OUTCOME.get();
            LOGGER.info("[autotest] migrate again: {}", o);
            if (o.status() != Migrations.Status.ALREADY_DONE) {
                fail("the second run was not refused as done: " + o);
            }
            if (!SAME.get()) {
                fail("the second run changed VENIK's progress");
            }
        }, 1);
    }

    private static void server(Runnable r) {
        mc().getSingleplayerServer().execute(r);
    }

    private static void prepare() {
        Path fixtures = Path.of(System.getProperty("rysquests.autotest.fixtures", ""));
        Path world = mc().getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize();
        try {
            copy(fixtures.resolve("world").resolve("ftbquests"), world.resolve("ftbquests"));
            copy(fixtures.resolve("config").resolve("rysquests").resolve("import"), mc().gameDirectory.toPath().resolve("config").resolve("rysquests").resolve("import"));
            Path mark = world.resolve(Migrations.STATE_DIR).resolve("migrate.json");
            if (Files.isRegularFile(mark)) {
                Files.move(mark, mark.resolveSibling("migrate.json.prev-" + System.currentTimeMillis()));
            }
        } catch (IOException e) {
            fail("could not put the FTB files in place: " + e);
        }
    }

    private static void copy(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            throw new IOException("no folder " + from);
        }
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
