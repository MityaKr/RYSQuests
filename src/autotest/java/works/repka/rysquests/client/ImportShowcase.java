package works.repka.rysquests.client;

import java.util.List;
import works.repka.rysquests.integration.ftbq.FtbqFiles;
import works.repka.rysquests.integration.ftbq.ImportReport;
import works.repka.rysquests.network.FtbqImportErrorPayload;
import works.repka.rysquests.network.FtbqImportReportPayload;

/**
 * What the server says at the end of an FTB import, without importing anything: the report and the failure of the ATM10 run of the
 * "first" scenario, for the screenshots of the wizard in every theme. Part of the autotest only, never of the mod.
 */
public final class ImportShowcase {
    private ImportShowcase() {
    }

    public static void report() {
        List<String> unconverted = List.of("ftbq:auroral/shimmer_soil", "ftbq:bumblezone/daycares", "ftbq:bumblezone/floaters",
                "ftbq:deeper_and_darker/gloomy_geyser", "ftbq:extreme_reactors/the_turbine", "ftbq:forbidden__arcanus/tier_1_forge");
        ImportReport report = new ImportReport(67, 4853, 605, 6104, 7312, List.of("en_us", "ru_ru", "de_de", "zh_cn"), 5277, 10, 67,
                List.of(new ImportReport.Manual(ImportReport.DROPPED_REWARD, 1, List.of("toast"), true, List.of("ftbq:apothic_enchanting/unbreakable_charms")),
                        new ImportReport.Manual(ImportReport.UNCONVERTED_TASK, 17, List.of("forge_energy", "observation"), true, unconverted)),
                18, 0, List.of("chapters/zz_autotest_broken.snbt"), "config/rysquests/import/import-report.md");
        ClientImport.receiveReport(new FtbqImportReportPayload(report));
    }

    public static void error() {
        ClientImport.receiveError(new FtbqImportErrorPayload(new FtbqFiles.Failure("chapters/zz_autotest_broken.snbt", 7, "unclosed object", true),
                "config/ftbquests/quests", 67, 68));
    }

    public static void clear() {
        ClientImport.clear();
    }
}
