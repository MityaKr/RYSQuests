package works.repka.rysquests.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.integration.ftbq.ImportReport;

/** What the first-run pages say (plan, sections 4 and 5), worked out without the game. */
class FirstRunTest {
    private static final String P = FirstRun.P;

    private static ImportReport report(int teams, int sections, int onMap, List<ImportReport.Manual> manual, int hidden) {
        return new ImportReport(67, 4853, 605, 5909, 7279, List.of("ru", "en"), 5277, sections, onMap, manual, hidden, teams, List.of(), "");
    }

    @Test
    void theReportShowsOnlyWhatIsThere() {
        FirstRun.Report shown = FirstRun.report(report(3, 11, 67, List.of(
                new ImportReport.Manual(ImportReport.UNCONVERTED_TASK, 17, List.of("observation", "ftbquests:energy"), true, List.of("ftbq:a")),
                new ImportReport.Manual(ImportReport.DROPPED_REWARD, 1, List.of("toast"), true, List.of("ftbq:b")),
                new ImportReport.Manual(ImportReport.LOOT_EMPTY_WEIGHT, 262, List.of("loot"), false, List.of()),
                new ImportReport.Manual("nothing_here", 0, List.of(), false, List.of())), 18));
        assertEquals(5, shown.carried().size(), "chapters, tasks, texts, links, progress");
        assertEquals(new FirstRun.Text(P + "report.pair", List.of(67, 4853)), shown.carried().getFirst().value());
        assertEquals(1, shown.placed().size(), "everything is on the map: no row for off the map");
        assertEquals(new FirstRun.Plural(P + "report.sections", 11), shown.placed().getFirst().sub().args().getFirst());
        assertEquals(3, shown.manual().size(), "a kind with nothing in it is absent");
        assertEquals(17, shown.manual().get(0).count());
        assertEquals(0, shown.manual().get(2).count(), "loot hides no quest: \"!\" instead of a number");
        assertNull(shown.manual().get(0).value(), "rows to fix end in a chevron");
        assertEquals(18, shown.hidden());
        FirstRun.Joined types = (FirstRun.Joined) shown.manual().get(0).sub().args().getFirst();
        assertEquals(List.of("observation", "energy"), types.parts().stream().map(t -> ((FirstRun.Text) t.args().getFirst()).fallback()).toList(),
                "FTB types lose their namespace and fall back to their own name");
        assertEquals(new FirstRun.Text(P + "ftb.reward.toast", List.of()).key(), shown.manual().get(1).title().key(), "one dropped reward type names the row");
    }

    @Test
    void manyLanguagesAreCut() {
        assertEquals(List.of("ru", "en"), FirstRun.languages(List.of("en_us", "ru_ru")).parts().stream().map(t -> (String) t.args().getFirst()).toList());
        FirstRun.Joined many = FirstRun.languages(List.of("de_de", "en_us", "fr_fr", "ru_ru", "zh_cn"));
        assertEquals(3, many.parts().size());
        assertEquals("ru", many.parts().getFirst().args().getFirst());
        assertEquals(new FirstRun.Text(P + "report.more_short", List.of(3)), many.parts().get(2));
    }

    @Test
    void noProgressNoLinksNoRows() {
        ImportReport bare = new ImportReport(3, 10, 0, 10, 0, List.of(), 0, 0, 2, List.of(), 0, 0, List.of(), "");
        FirstRun.Report shown = FirstRun.report(bare);
        assertEquals(2, shown.carried().size(), "chapters and tasks only");
        assertNull(shown.carried().getFirst().sub(), "no optional quests, no second line");
        assertEquals(2, shown.placed().size(), "one chapter off the map gets its row");
        assertEquals(FirstRun.Badge.OFF_MAP, shown.placed().get(1).badge());
        assertNull(shown.placed().getFirst().sub(), "no FTB groups, no line about sections");
    }

    private static FirstRun.Book book(MapForm advised, int epochs, boolean imported, int chapters) {
        MapForms.Reason reason = advised == MapForm.SERPENTINE ? MapForms.Reason.EPOCHS : advised == MapForm.GALAXY ? MapForms.Reason.FEW_LINKS : MapForms.Reason.OTHER;
        return new FirstRun.Book(new MapForms.Choice(advised, reason, chapters, imported ? 0 : chapters, 5277, 18), epochs, imported, chapters, 4, 10);
    }

    @Test
    void everyFormSaysWhyForThisBook() {
        FirstRun.Book atm = book(MapForm.GALAXY, 11, true, 67);
        assertEquals(P + "form.serpentine.imported", FirstRun.formReason(MapForm.SERPENTINE, atm).key());
        assertEquals(P + "form.spiral.core", FirstRun.formReason(MapForm.SPIRAL, atm).key());
        assertEquals(P + "form.generator.scattered", FirstRun.formReason(MapForm.GENERATOR, atm).key());
        assertEquals(P + "form.galaxy.advised", FirstRun.formReason(MapForm.GALAXY, atm).key());
        FirstRun.Text why = FirstRun.why(atm);
        assertEquals(P + "why.galaxy_ftb", why.key());
        assertEquals(List.of(new FirstRun.Plural(P + "why.core", 4), new FirstRun.Plural(P + "why.rest", 63), 18, 5277, new FirstRun.Plural(P + "why.arms", 10)), why.args());

        FirstRun.Book repys = book(MapForm.SERPENTINE, 8, false, 40);
        assertEquals(P + "form.serpentine.advised", FirstRun.formReason(MapForm.SERPENTINE, repys).key());
        assertEquals(P + "form.generator.epochs", FirstRun.formReason(MapForm.GENERATOR, repys).key());
        assertEquals(P + "form.galaxy.linked", FirstRun.formReason(MapForm.GALAXY, repys).key());
        assertEquals(P + "why.serpentine", FirstRun.why(repys).key());

        FirstRun.Book small = book(MapForm.GENERATOR, 0, false, 5);
        assertEquals(P + "form.galaxy.few", FirstRun.formReason(MapForm.GALAXY, small).key());
        assertEquals(P + "form.serpentine.no_epochs", FirstRun.formReason(MapForm.SERPENTINE, small).key());
        assertEquals(P + "why.generator_few", FirstRun.why(small).key());
    }

    @Test
    void theAdvisedFormStaysAuto() {
        assertEquals(MapForm.AUTO, FirstRun.formToSave(MapForm.GALAXY, MapForm.GALAXY));
        assertEquals(MapForm.SPIRAL, FirstRun.formToSave(MapForm.SPIRAL, MapForm.GALAXY));
    }

    @Test
    void theAnswerPicksTheForm() {
        assertEquals(MapForm.SERPENTINE, FirstRun.Answer.ORDER.form);
        assertEquals(MapForm.SPIRAL, FirstRun.Answer.FINALE.form);
        assertEquals(MapForm.GALAXY, FirstRun.Answer.CORE.form);
        assertEquals(MapForm.AUTO, FirstRun.Answer.UNKNOWN.form);
        assertEquals("Моя сборка", FirstRun.bookTitle("  Моя сборка ", 64));
        assertNull(FirstRun.bookTitle("   ", 64));
        assertEquals("abc", FirstRun.bookTitle("abcdef", 3));
    }

    private static Quest quest(String id, boolean draft, String... requires) {
        QuestOptions options = new QuestOptions(QuestOptions.DEFAULT.layout(), false, false, false, Optional.empty(), false, List.of(), draft, false);
        return new Quest(ResourceLocation.parse(id), Optional.of(ResourceLocation.parse("t:" + id.substring(2, 3))), Optional.empty(), id, Optional.empty(),
                Optional.empty(), Optional.empty(), Arrays.stream(requires).map(ResourceLocation::parse).toList(), RequiresMode.ALL, Optional.empty(), List.of(),
                List.of(), Optional.empty(), options);
    }

    @Test
    void thePublicationQuestionCountsWhatPlayersGet() {
        Chapter a = new Chapter(ResourceLocation.parse("t:a"), "a", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), false, Chapter.STORY);
        Chapter b = new Chapter(ResourceLocation.parse("t:b"), "b", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), false, Chapter.STORY);
        Chapter c = new Chapter(ResourceLocation.parse("t:c"), "c", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), false, Chapter.STORY);
        QuestBook clean = QuestBook.create(BookMeta.DEFAULT, List.of(a, b, c), List.of(quest("t:a1", false), quest("t:b1", false, "t:a1"), quest("t:c1", true)), List.of(), List.of(), "h");
        FirstRun.Publish facts = FirstRun.publish(clean);
        assertEquals(2, facts.chapters(), "a chapter with only a draft is not seen");
        assertEquals(2, facts.quests());
        assertEquals(1, facts.drafts());
        assertTrue(facts.clean());
        QuestBook stuck = QuestBook.create(BookMeta.DEFAULT, List.of(a, b, c), List.of(quest("t:a1", false, "t:x9"), quest("t:b1", false, "t:c1"), quest("t:c1", true)), List.of(),
                List.of(), "h");
        FirstRun.Publish issues = FirstRun.publish(stuck);
        assertEquals(1, issues.broken());
        assertEquals(1, issues.stuck(), "a quest waiting for a draft never opens");
        assertFalse(issues.clean());
    }
}
