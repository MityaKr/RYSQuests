package works.repka.rysquests.client;

import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.dev.DevReports;
import works.repka.rysquests.network.DevReportPayload;

/** The last book report sent by the server to this operator. */
public final class ClientDev {
    @Nullable
    private static DevReports.Report report;

    private ClientDev() {
    }

    @Nullable
    public static DevReports.Report report() {
        return report;
    }

    static void receive(DevReportPayload payload) {
        report = payload.report();
    }
}
