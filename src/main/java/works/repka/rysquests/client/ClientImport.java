package works.repka.rysquests.client;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.integration.ftbq.ImportReport;
import works.repka.rysquests.network.FtbqImportErrorPayload;
import works.repka.rysquests.network.FtbqImportProgressPayload;
import works.repka.rysquests.network.FtbqImportReportPayload;
import works.repka.rysquests.network.FtbqImportStartPayload;

/**
 * The client's side of the FTB Quests import wizard (plan, pictures 06, 07, 50): it starts the import and keeps what the server
 * said last, for the wizard to draw. "Retry" is {@link #retry()}, "Carry over without this chapter" is {@link #skipFailedFile()}.
 */
public final class ClientImport {
    public enum Stage {
        IDLE, RUNNING, DONE, FAILED
    }

    private static Stage stage = Stage.IDLE;
    @Nullable
    private static FtbqImportProgressPayload progress;
    @Nullable
    private static ImportReport report;
    @Nullable
    private static FtbqImportErrorPayload error;
    private static String namespace = "";
    private static final List<String> SKIP = new ArrayList<>();
    private static boolean withProgress;

    private ClientImport() {
    }

    /** Starts afresh. @param bookNamespace the namespace of the new book, empty for {@code ftbq} */
    public static void start(String bookNamespace, boolean carryProgress) {
        namespace = bookNamespace;
        withProgress = carryProgress;
        SKIP.clear();
        send();
    }

    /** "Retry": the same import again, after the file was fixed. */
    public static void retry() {
        send();
    }

    /** "Carry over without this chapter": the file that stopped the import is left out. */
    public static void skipFailedFile() {
        if (error != null && error.failure().skippable() && !SKIP.contains(error.failure().file())) {
            SKIP.add(error.failure().file());
        }
        send();
    }

    private static void send() {
        stage = Stage.RUNNING;
        progress = null;
        report = null;
        error = null;
        PacketDistributor.sendToServer(new FtbqImportStartPayload(namespace, List.copyOf(SKIP), withProgress));
    }

    public static Stage stage() {
        return stage;
    }

    /** The phase the running import is in, or null before the first word from the server. */
    @Nullable
    public static FtbqImportProgressPayload progress() {
        return progress;
    }

    @Nullable
    public static ImportReport report() {
        return report;
    }

    @Nullable
    public static FtbqImportErrorPayload error() {
        return error;
    }

    static void receiveProgress(FtbqImportProgressPayload payload) {
        progress = payload;
    }

    static void receiveReport(FtbqImportReportPayload payload) {
        report = payload.report();
        stage = Stage.DONE;
    }

    static void receiveError(FtbqImportErrorPayload payload) {
        error = payload;
        stage = Stage.FAILED;
    }

    static void clear() {
        stage = Stage.IDLE;
        progress = null;
        report = null;
        error = null;
        SKIP.clear();
    }
}
