package works.repka.rysquests.data;

import java.util.List;
import java.util.Optional;
import works.repka.rysquests.data.BookReport.Issue;
import works.repka.rysquests.migrate.Migration;

/** A book as parsed from files, before linking. Quests have their chapter filled in. */
public record RawBook(BookMeta meta, List<Chapter> chapters, List<Quest> quests, List<Guide> guides, List<SectionDef> sections, List<ShopOffer> offers, List<Achievement> achievements, List<StructureDef> structures, List<Issue> issues, List<Migration> migrations,
        Optional<BookLayout> layout) {

    public RawBook withIssues(List<Issue> newIssues) {
        return new RawBook(meta, chapters, quests, guides, sections, offers, achievements, structures, List.copyOf(newIssues), migrations, layout);
    }
}
