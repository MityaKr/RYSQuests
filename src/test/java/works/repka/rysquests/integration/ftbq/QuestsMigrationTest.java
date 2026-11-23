package works.repka.rysquests.integration.ftbq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.ryscore.migrate.Migrations;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.TestBooks;
import works.repka.rysquests.progress.ProgressStoreAccess;
import works.repka.rysquests.progress.QuestProgress;

/**
 * The {@code quests} step on the copy of the RepYS server's FTB files ({@code _shared/_saved/fixtures/ftb-2026-11-18}) with the RepYS
 * book: a first run, a second, a failed write that the next start finishes, and the live case where the progress was moved by hand.
 */
class QuestsMigrationTest {
    private static final UUID VENIK = UUID.fromString("0c383da8-5fb5-41ef-96be-7cae45e6e640");
    private static final Map<String, QuestBook> BOOKS = new HashMap<>();

    @TempDir
    Path game;
    Path world;

    /** The records "in the server's memory" and "on disk"; a restart reads the disk again. */
    Map<UUID, Map<ResourceLocation, QuestProgress>> own = new HashMap<>();
    Map<UUID, Map<ResourceLocation, QuestProgress>> disk = new HashMap<>();
    QuestBook book;
    /** Where a player plays now; absent means alone. */
    Map<UUID, UUID> teams = new HashMap<>();
    boolean failWrite;
    int writes;

    private static Path property(String name, String fallback) {
        return Path.of(System.getProperty(name, fallback));
    }

    @BeforeEach
    void setUp() throws IOException {
        Path fixtures = property("rysquests.ftbFixtures", "../../_shared/_saved/fixtures/ftb-2026-11-18");
        assumeTrue(Files.isDirectory(fixtures.resolve("world/ftbquests")), "no copy of the FTB files at " + fixtures);
        book = book(property("rysquests.repysBook", "../../_shared/_fresh/book"));
        world = game.resolve("world");
        copy(fixtures.resolve("world/ftbquests"), world.resolve("ftbquests"));
        copy(fixtures.resolve("config/rysquests/import"), game.resolve("config/rysquests/import"));
        flag();
        register();
    }

    @AfterEach
    void tearDown() {
        Migrations.loader(id -> false, id -> "");
    }

    private void register() {
        Migrations.register(new QuestsMigration(new QuestsMigration.Backend() {
            @Override
            public QuestBook book(Migrations.Context c) {
                return book;
            }

            @Override
            public Map<UUID, Map<ResourceLocation, QuestProgress>> progress(Migrations.Context c) {
                return own;
            }

            @Override
            public void add(Migrations.Context c, Map<UUID, Map<ResourceLocation, QuestProgress>> additions) throws IOException {
                additions.forEach((team, quests) -> own.computeIfAbsent(team, id -> new HashMap<>()).putAll(quests));
                if (failWrite) {
                    throw new IOException("disk full");
                }
                writes++;
                disk = deepCopy(own);
            }

            @Override
            public UUID teamOf(Migrations.Context c, UUID player) {
                return teams.getOrDefault(player, player);
            }
        }));
    }

    private static QuestBook book(Path folder) throws IOException {
        assumeTrue(Files.isDirectory(folder), "no RepYS book at " + folder);
        QuestBook cached = BOOKS.get(folder.toString());
        if (cached == null) {
            Map<ResourceLocation, JsonElement> files = TestBooks.folder(folder, "repys");
            files.values().forEach(QuestsMigrationTest::stripComponents);
            cached = TestBooks.book(files);
            BOOKS.put(folder.toString(), cached);
        }
        return cached;
    }

    /** Component types of absent mods cannot be parsed here; in the real game they resolve. */
    private static void stripComponents(JsonElement element) {
        if (element.isJsonObject()) {
            element.getAsJsonObject().remove("components");
            element.getAsJsonObject().remove("nbt");
            element.getAsJsonObject().entrySet().forEach(entry -> stripComponents(entry.getValue()));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(QuestsMigrationTest::stripComponents);
        }
    }

    private void flag() throws IOException {
        Path file = game.resolve(Migrations.FLAG_FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"v\": 1, \"from\": \"ftb\", \"steps\": [\"teams\", \"quests\", \"claims\", \"map\"]}", StandardCharsets.UTF_8);
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.copy(p, t);
                }
            }
        }
    }

    private static Map<UUID, Map<ResourceLocation, QuestProgress>> deepCopy(Map<UUID, Map<ResourceLocation, QuestProgress>> source) {
        Map<UUID, Map<ResourceLocation, QuestProgress>> copy = new HashMap<>();
        source.forEach((team, quests) -> copy.put(team, new HashMap<>(quests)));
        return copy;
    }

    private Migrations.Outcome start() {
        return Migrations.runServer(game, world, null).outcome(QuestsMigration.ID);
    }

    private void restart() {
        own = deepCopy(disk);
    }

    private static int quests(Map<UUID, Map<ResourceLocation, QuestProgress>> progress) {
        return progress.values().stream().mapToInt(Map::size).sum();
    }

    @Test
    void firstRunBringsTheProgressIntoAnEmptyStore() throws IOException {
        Migrations.Outcome o = start();
        assertEquals(Migrations.Status.DONE, o.status(), o.toString());
        assertTrue(own.containsKey(VENIK), "the party's progress");
        assertTrue(own.get(VENIK).size() > 100, "VENIK has done much: " + own.get(VENIK).size());
        assertEquals(quests(FtbqProgress.read(world.resolve("ftbquests"), FtbqFiles.readIds(game.resolve("config/rysquests/import")), book)), quests(own));
        assertEquals((long) quests(own), o.report().counts().get("quests"));
        assertEquals(0L, o.report().counts().get("kept"));
        assertTrue(Files.isRegularFile(world.resolve("rys/migrate.json")), "the core's mark");
        assertTrue(Files.isRegularFile(world.resolve("ftbquests/" + VENIK + ".snbt")), "FTB's files stay");
    }

    @Test
    void secondStartDoesNothing() {
        start();
        Map<UUID, Map<ResourceLocation, QuestProgress>> before = deepCopy(own);
        int writesBefore = writes;
        restart();
        assertEquals(Migrations.Status.ALREADY_DONE, start().status());
        assertEquals(before, own);
        assertEquals(writesBefore, writes);
    }

    @Test
    void lostMarkIsSafeToRepeat() throws IOException {
        start();
        Map<UUID, Map<ResourceLocation, QuestProgress>> before = deepCopy(own);
        Files.writeString(world.resolve("rys/migrate.json"), "{ not json", StandardCharsets.UTF_8);
        restart();
        Migrations.Outcome again = start();
        assertEquals(Migrations.Status.DONE, again.status(), again.toString());
        assertEquals(0L, again.report().counts().get("quests"));
        assertEquals(before, own);
        assertTrue(again.report().skipped().stream().anyMatch(s -> s.endsWith("already exists")), again.toString());
    }

    @Test
    void failedWriteLeavesNoMarkAndNextStartFinishes() {
        failWrite = true;
        Migrations.Outcome failed = start();
        assertEquals(Migrations.Status.FAILED, failed.status());
        failWrite = false;
        restart();
        assertTrue(own.isEmpty(), "nothing reached the disk");
        Migrations.Outcome next = start();
        assertEquals(Migrations.Status.DONE, next.status(), next.toString());
        assertTrue(own.get(VENIK).size() > 100);
        restart();
        assertTrue(own.get(VENIK).size() > 100, "written to disk");
    }

    @Test
    void ownProgressIsNeverOverwritten() throws IOException {
        start();
        // the team played on after the move: a quest reset by an operator, a reward taken, a quest of our own, a record lost
        Map<ResourceLocation, QuestProgress> venik = own.get(VENIK);
        List<ResourceLocation> ids = venik.keySet().stream().sorted().toList();
        ResourceLocation reset = ids.get(0), claimed = ids.get(1), lost = ids.get(2);
        ResourceLocation ours = ResourceLocation.parse("repys:test/only_here");
        venik.put(reset, QuestProgress.EMPTY);
        venik.put(claimed, venik.get(claimed).withClaimed(List.of(0, 1, 2)));
        venik.put(ours, new QuestProgress(List.of(3), 0, List.of()));
        venik.remove(lost);
        Map<UUID, Map<ResourceLocation, QuestProgress>> before = deepCopy(own);
        // no mark, as on the RepYS server where the progress was moved by a command
        Files.delete(world.resolve("rys/migrate.json"));
        Migrations.Outcome o = start();
        assertEquals(Migrations.Status.DONE, o.status(), o.toString());
        assertEquals(1L, o.report().counts().get("quests"), "only the lost record comes back");
        assertEquals(QuestProgress.EMPTY, own.get(VENIK).get(reset), "the reset stays");
        assertEquals(List.of(0, 1, 2), own.get(VENIK).get(claimed).claimed());
        assertTrue(own.get(VENIK).containsKey(ours));
        assertTrue(own.get(VENIK).containsKey(lost));
        before.get(VENIK).forEach((quest, progress) -> assertEquals(progress, own.get(VENIK).get(quest), quest.toString()));
    }

    @Test
    void withoutTheIdTableNothingIsTouched() throws IOException {
        Files.move(game.resolve("config/rysquests/import/ftbq-ids.json"), game.resolve("ids.off"));
        Migrations.Outcome o = start();
        assertEquals(Migrations.Status.NOT_NEEDED, o.status());
        assertTrue(own.isEmpty());
        assertFalse(Files.exists(world.resolve("rys/migrate.json")) && Files.readString(world.resolve("rys/migrate.json")).contains("\"quests\""));
        // the table arrives with the next build: the step goes then
        Files.move(game.resolve("ids.off"), game.resolve("config/rysquests/import/ftbq-ids.json"));
        assertEquals(Migrations.Status.DONE, start().status());
    }

    @Test
    void waitsWhileFtbQuestsIsLoaded() {
        Migrations.loader(id -> id.equals("ftbquests"), id -> "");
        assertEquals(Migrations.Status.NOT_NEEDED, start().status());
        assertTrue(own.isEmpty());
    }

    @Test
    void brokenFileMakesItPartialAndTheNextStartFinishes() throws IOException {
        Path broken = world.resolve("ftbquests/ffffffff-0000-0000-0000-000000000000.snbt");
        Files.writeString(broken, "{ uuid: \"x\" completed: {", StandardCharsets.UTF_8);
        Migrations.Outcome o = start();
        assertEquals(Migrations.Status.PARTIAL, o.status(), o.toString());
        assertTrue(own.containsKey(VENIK), "the other files came over");
        // no "done" mark (RYSCore 1.13.1): the next start runs again and adds nothing twice
        Map<UUID, Map<ResourceLocation, QuestProgress>> before = deepCopy(own);
        restart();
        Migrations.Outcome again = start();
        assertEquals(Migrations.Status.PARTIAL, again.status(), again.toString());
        assertEquals(0L, again.report().counts().get("quests"));
        assertEquals(before, own);
        // the broken file is taken away: the step finishes and sets the mark
        Files.move(broken, game.resolve("broken.off"));
        restart();
        assertEquals(Migrations.Status.DONE, start().status());
        restart();
        assertEquals(Migrations.Status.ALREADY_DONE, start().status());
    }

    private static final UUID MITYA = UUID.fromString("216ea99a-d6eb-45be-8a5b-3da7852588a5");
    private static final UUID KIFA = UUID.fromString("633d6f3f-f318-4004-a3fb-b5131b22c28b");

    private void withFtbTeams() throws IOException {
        Path fixtures = property("rysquests.ftbFixtures", "../../_shared/_saved/fixtures/ftb-2026-11-18");
        copy(fixtures.resolve("world/ftbteams"), world.resolve("ftbteams"));
    }

    @Test
    void playerInAnotherTeamGetsTheProgressThere() throws IOException {
        withFtbTeams();
        UUID other = UUID.randomUUID();
        teams.put(MITYA, VENIK);
        teams.put(KIFA, other);
        Migrations.Outcome o = start();
        assertEquals(Migrations.Status.DONE, o.status(), o.toString());
        assertTrue(own.get(VENIK).size() > 100, "the party keeps its progress");
        assertTrue(own.containsKey(other), "Kifa65 plays in another team now: " + o);
        assertTrue(own.get(other).keySet().containsAll(own.get(VENIK).keySet()), "the team gets every quest of VENIK");
        assertEquals(1L, o.report().counts().get("moved"), "MityaYS is in VENIK; his own FTB file was hidden by the party");
        assertTrue(o.report().lines().stream().anyMatch(l -> l.contains("to team") && l.contains("Kifa65")), o.toString());
        // run again (mark lost): nothing more
        Map<UUID, Map<ResourceLocation, QuestProgress>> before = deepCopy(own);
        Files.writeString(world.resolve("rys/migrate.json"), "{ not json", StandardCharsets.UTF_8);
        restart();
        Migrations.Outcome again = start();
        assertEquals(0L, again.report().counts().get("quests"));
        assertEquals(before, own);
    }

    @Test
    void withoutTeamsEachPlayerOfThePartyGetsItAlone() throws IOException {
        withFtbTeams();
        Migrations.Outcome o = start();
        assertEquals(Migrations.Status.DONE, o.status(), o.toString());
        assertTrue(own.get(VENIK).size() > 100);
        assertTrue(own.get(KIFA).keySet().containsAll(own.get(VENIK).keySet()), "Kifa65 plays alone and has the party's quests");
        assertTrue(own.get(MITYA).keySet().containsAll(own.get(VENIK).keySet()));
        assertEquals(2L, o.report().counts().get("moved"));
        assertTrue(o.report().lines().stream().anyMatch(l -> l.contains("personal progress")), o.toString());
    }

    @Test
    void brokenTeamFileMakesItPartial() throws IOException {
        withFtbTeams();
        teams.put(MITYA, VENIK);
        teams.put(KIFA, VENIK);
        Files.writeString(world.resolve("ftbteams/party/ffffffff-0000-0000-0000-000000000000.snbt"), "{ id: \"x\" ranks: {", StandardCharsets.UTF_8);
        Migrations.Outcome o = start();
        assertEquals(Migrations.Status.PARTIAL, o.status(), o.toString());
        assertEquals(0L, o.report().counts().get("moved"));
        assertTrue(own.get(VENIK).size() > 100);
    }

    @Test
    void foldAddsOnlyMissingQuests() {
        ResourceLocation a = ResourceLocation.parse("t:a"), b = ResourceLocation.parse("t:b");
        UUID team = UUID.randomUUID(), fresh = UUID.randomUUID();
        QuestProgress done = new QuestProgress(List.of(1), 5, List.of(0));
        Map<UUID, Map<ResourceLocation, QuestProgress>> ftb = Map.of(team, Map.of(a, done, b, done), fresh, Map.of(a, done));
        Map<UUID, Map<ResourceLocation, QuestProgress>> mine = Map.of(team, Map.of(a, QuestProgress.EMPTY));
        FtbqMerge.Plan plan = FtbqMerge.plan(ftb, mine);
        assertEquals(Map.of(team, Map.of(b, done), fresh, Map.of(a, done)), plan.additions());
        assertEquals(1, plan.newTeams());
        assertEquals(1, plan.mergedTeams());
        assertEquals(1, plan.kept());
        assertTrue(FtbqMerge.plan(ftb, Map.of(team, Map.of(a, done, b, done), fresh, Map.of(a, done))).empty());
    }

    /**
     * The copy of the RepYS test server: progress moved by a command on 17.11 and played on since. The step must keep every record
     * there; what it would add is printed for the report.
     */
    @Test
    void liveCopyOfRepysServer() throws IOException {
        Path live = property("rysquests.liveServer", "C:/Projects/RepYS-ServerTest");
        Path dat = live.resolve("world/data/rysquests_progress.dat");
        assumeTrue(Files.isRegularFile(dat) && Files.isDirectory(live.resolve("world/ftbquests")), "no copy of the server at " + live);
        book = book(live.resolve("kubejs/data/repys/rysquests"));
        CompoundTag root = NbtIo.readCompressed(dat, NbtAccounter.unlimitedHeap());
        own = deepCopy(ProgressStoreAccess.load(root.getCompound("data")).allProgress());
        Map<UUID, Map<ResourceLocation, QuestProgress>> before = deepCopy(own);
        // the server's own id table and FTB files, in place of the fixture's
        Files.copy(live.resolve("config/rysquests/import/ftbq-ids.json"), game.resolve("config/rysquests/import/ftbq-ids.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (Stream<Path> files = Files.list(live.resolve("world/ftbquests"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".snbt")).toList()) {
                Files.copy(f, world.resolve("ftbquests").resolve(f.getFileName().toString()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Migrations.Outcome o = start();
        assertEquals(Migrations.Status.DONE, o.status(), o.toString());
        before.forEach((team, quests) -> quests.forEach((quest, progress) -> assertEquals(progress, own.get(team).get(quest), team + " " + quest)));
        System.out.println("[rys-migrate] RepYS server copy: " + o);
    }
}
