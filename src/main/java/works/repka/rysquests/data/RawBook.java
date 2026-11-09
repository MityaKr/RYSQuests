package works.repka.rysquests.data;

import java.util.List;
import works.repka.rysquests.data.BookReport.Issue;

/** A book as parsed from files, before linking. Quests have their chapter filled in. */
public record RawBook(BookMeta meta, List<Chapter> chapters, List<Quest> quests, List<Issue> issues) {
}
