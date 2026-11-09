package works.repka.rysquests.integration.ftbq;

import java.util.UUID;

/** Exposes package-private importer helpers to tests in other packages. */
public final class FtbqProgressAccess {
    private FtbqProgressAccess() {
    }

    public static UUID uuid(String text) {
        return FtbqProgress.parseUuid(text);
    }
}
