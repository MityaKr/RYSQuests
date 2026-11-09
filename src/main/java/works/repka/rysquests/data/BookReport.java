package works.repka.rysquests.data;

import java.util.List;

/** What validation found. An issue code doubles as the suffix of its translation key. */
public record BookReport(List<Issue> issues) {
    public static final BookReport EMPTY = new BookReport(List.of());

    public enum Severity {
        ERROR,
        WARNING
    }

    public record Issue(Severity severity, String code, String subject, String detail) {
        @Override
        public String toString() {
            return severity + " " + code + " " + subject + (detail.isEmpty() ? "" : " : " + detail);
        }
    }

    public long errors() {
        return issues.stream().filter(issue -> issue.severity == Severity.ERROR).count();
    }

    public long warnings() {
        return issues.size() - errors();
    }
}
