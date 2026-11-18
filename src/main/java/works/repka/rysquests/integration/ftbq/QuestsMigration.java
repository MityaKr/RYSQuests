package works.repka.rysquests.integration.ftbq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.migrate.Migrations;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.migrate.MigrationRunner;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;

/**
 * The {@code quests} step (order 20, after {@code teams}) of the family's move from FTB (RYSCore {@code migrate.Migrations}, ADR-65).
 * It runs once by itself when the modpack's flag {@code config/ryscore/migrate.json} names it, or by {@code /rys migrate run quests};
 * the core makes the copy, the mark and the {@code [rys-migrate]} report.
 *
 * <p>It folds FTB Quests progress ({@code world/ftbquests/*.snbt}) into ours with {@link FtbqMerge}: what a team has here stays, only
 * quests without a record come over. Run again it adds nothing; on a world whose progress was moved by hand (the RepYS server) it only
 * sets the mark, adding at most quests that have no record here at all.
 *
 * <p>A player of an FTB team who plays in another team now (they were in a team of the team mod already, or there is no team mod)
 * also gets that FTB team's progress in the team they play in, with a line in the report ({@link FtbqMembers}). A file that could
 * not be read makes the step partial: with RYSCore 1.13.1 it runs again at the next start and adds what is missing.
 *
 * <p>It waits, without a mark, while FTB Quests is loaded, while the id table {@code config/rysquests/import/ftbq-ids.json} is missing
 * and while no book is loaded: each of these is said once in the log at start.
 */
public final class QuestsMigration implements Migrations.Step {
    public static final String ID = "quests";

    /** Where the step reads the book and the progress and how it writes; tests replace it. */
    public interface Backend {
        @Nullable
        QuestBook book(Migrations.Context c);

        /** The records here, read only. */
        Map<UUID, Map<ResourceLocation, QuestProgress>> progress(Migrations.Context c);

        /** Adds the records and writes the store to disk before returning. */
        void add(Migrations.Context c, Map<UUID, Map<ResourceLocation, QuestProgress>> additions) throws IOException;

        /** Whose progress a player works on now (their team, or themselves when alone). */
        default UUID teamOf(Migrations.Context c, UUID player) {
            return TeamsApi.get().teamOf(player);
        }
    }

    private final Backend backend;

    public QuestsMigration() {
        this(new ServerBackend());
    }

    public QuestsMigration(Backend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Migrations.Side side() {
        return Migrations.Side.SERVER;
    }

    @Override
    public String modId() {
        return RYSQuests.MODID;
    }

    static Path idsFile(Migrations.Context c) {
        return c.configDir().resolve("rysquests").resolve("import").resolve(FtbqFiles.IDS_FILE);
    }

    static boolean hasData(@Nullable Path world) {
        if (world == null || !Files.isDirectory(world.resolve("ftbquests"))) {
            return false;
        }
        try (Stream<Path> list = Files.list(world.resolve("ftbquests"))) {
            return list.anyMatch(p -> p.getFileName().toString().endsWith(".snbt"));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean needed(Migrations.Context c) {
        if (!hasData(c.worldDir())) {
            return false;
        }
        if (c.isLoaded("ftbquests")) {
            c.log("FTB Quests ещё стоит: прогресс ведёт он, шаг ждёт");
            return false;
        }
        if (!Files.isRegularFile(idsFile(c))) {
            c.log("нет таблицы id квестов " + idsFile(c) + ": шаг ждёт, ничего не тронуто");
            return false;
        }
        QuestBook book = backend.book(c);
        if (book == null || book.quests().isEmpty()) {
            c.log("книга не загружена: шаг ждёт");
            return false;
        }
        return true;
    }

    @Override
    public Migrations.Report run(Migrations.Context c) throws Exception {
        Path world = Objects.requireNonNull(c.worldDir(), "no world folder");
        QuestBook book = Objects.requireNonNull(backend.book(c), "no book");
        FtbqImporter.Ids ids = FtbqFiles.readIds(idsFile(c).getParent());
        Migrations.Report report = Migrations.Report.create();
        List<String> broken = new ArrayList<>();
        Map<UUID, Map<ResourceLocation, QuestProgress>> ftb = FtbqProgress.read(world.resolve("ftbquests"), ids, book, (file, why) -> broken.add(file + ": " + why));
        broken.forEach(line -> report.partial("файл " + line));
        // a player who plays in another team now also gets their FTB team's progress there (ADR-65, review of 18.11)
        Map<UUID, Set<UUID>> parties = FtbqMembers.parties(world, (file, why) ->
                report.partial("файл команды FTB " + file + " не прочитан, её игроки не сверены: " + why));
        List<FtbqMembers.Moved> moved = new ArrayList<>();
        ftb = FtbqMembers.redirect(ftb, parties, player -> backend.teamOf(c, player), book, moved);
        for (FtbqMembers.Moved m : moved) {
            String player = FtbqMembers.name(world, m.player());
            report.line(label(m.source()) + ": прогресс FTB добавлен и " + (m.target().equals(m.player())
                    ? "в личный прогресс " + player + " (играет без команды)" : "в команду " + label(m.target()) + ", где сейчас " + player));
        }
        report.count("moved", moved.size());
        FtbqMerge.Plan plan = FtbqMerge.plan(ftb, backend.progress(c));
        if (!plan.empty()) {
            backend.add(c, plan.additions());
        }
        report.count("teams", plan.newTeams()).count("merged", plan.mergedTeams()).count("quests", plan.added()).count("kept", plan.kept());
        plan.merged().forEach((team, n) -> report.line(label(team) + ": добавлено недостающих квестов " + n));
        plan.same().forEach(team -> report.skipped(label(team), "уже есть"));
        return report;
    }

    /** The team's name where the team mod knows it, with the start of its id. */
    static String label(UUID team) {
        String id = team.toString().substring(0, 8);
        try {
            String name = TeamsApi.get().name(team).getString();
            return name.isBlank() || name.equals(id) ? id : name + " (" + id + ")";
        } catch (RuntimeException e) {
            return id;
        }
    }

    /** The world's store: a backup of its own first, then the records, then the file on disk. */
    static final class ServerBackend implements Backend {
        @Override
        public @Nullable QuestBook book(Migrations.Context c) {
            return BookHolder.current().book();
        }

        @Override
        public Map<UUID, Map<ResourceLocation, QuestProgress>> progress(Migrations.Context c) {
            return ProgressStore.get(server(c)).allProgress();
        }

        @Override
        public void add(Migrations.Context c, Map<UUID, Map<ResourceLocation, QuestProgress>> additions) throws IOException {
            MinecraftServer server = server(c);
            MigrationRunner.backup(server);
            ProgressStore store = ProgressStore.get(server);
            additions.forEach(store::put);
            store.writeNow(server);
            ProgressService.invalidateAll();
        }

        @Override
        public UUID teamOf(Migrations.Context c, UUID player) {
            QuestBook book = BookHolder.current().book();
            return ProgressStore.get(server(c)).teamOf(player, book == null ? ProgressMode.TEAM : book.meta().progressMode());
        }

        private static MinecraftServer server(Migrations.Context c) {
            return Objects.requireNonNull(c.server(), "the quests step runs on a server only");
        }
    }
}
