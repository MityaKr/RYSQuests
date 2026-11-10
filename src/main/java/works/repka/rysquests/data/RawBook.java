package works.repka.rysquests.data;

import java.util.List;
import works.repka.rysquests.data.BookReport.Issue;
import works.repka.rysquests.migrate.Migration;

/** A book as parsed from files, before linking. Quests have their chapter filled in. */
public record RawBook(BookMeta meta, List<Chapter> chapters, List<Quest> quests, List<Guide> guides, List<SectionDef> sections, List<ShopOffer> offers, List<Achievement> achievements, List<Issue> issues, List<Migration> migrations) {
}
