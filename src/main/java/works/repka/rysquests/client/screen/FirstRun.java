package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.integration.ftbq.ImportReport;

/**
 * What the first-run pages say, worked out without drawing (plan, sections 4 and 5): the rows of the import report (picture 06),
 * the reason of each map form and of the advice (07), the answer of the new book (09a) and the facts of the publication question
 * (10). Texts come out as language keys with their arguments, so that tests can read them without the game.
 */
final class FirstRun {
    static final String P = "rysquests.first.";

    private FirstRun() {
    }

    // --- texts ---

    /** A text to translate: arguments may be numbers, strings, other texts or {@link Plural}s. */
    record Text(String key, List<Object> args, @Nullable Object fallback) {
        Text(String key, List<Object> args) {
            this(key, args, null);
        }

        static Text of(String key, Object... args) {
            return new Text(key, List.of(args));
        }

        /** Shown when the language has no such key: types of FTB Quests the book does not know by name. */
        Text or(Object other) {
            return new Text(key, args, other);
        }
    }

    /** A number with the word that agrees with it, "18 квестов": the key gets {@code .one}, {@code .few} or {@code .many}. */
    record Plural(String key, int count) {
    }

    /** Words joined as a list: "«наблюдение» и «энергия»". */
    record Joined(List<Text> parts) {
    }

    // --- the import report (picture 06) ---

    /** What stands at the left of a row of the report. */
    enum Badge {
        /** Carried over: a green check. */
        DONE,
        /** Where it went: the icon of the map. */
        MAP,
        /** Off the map: a flag. */
        OFF_MAP,
        /** To fix by hand: how many, or "!" when nothing was hidden. */
        COUNT
    }

    /**
     * One row of the report.
     *
     * @param count the number on a {@link Badge#COUNT} badge, 0 for "!"
     * @param value the number at the right, null for a chevron (rows to fix)
     * @param quests the quests concerned, for the row's tooltip
     */
    record Row(Badge badge, int count, Text title, @Nullable Text sub, @Nullable Text value, List<String> quests) {
    }

    /** The three columns of the report: what came across, where it went, what needs a hand; and how many quests are hidden. */
    record Report(List<Row> carried, List<Row> placed, List<Row> manual, int hidden) {
    }

    static Report report(ImportReport report) {
        List<Row> carried = new ArrayList<>();
        carried.add(new Row(Badge.DONE, 0, Text.of(P + "report.chapters"),
                report.optionalQuests() > 0 ? Text.of(P + "report.optional", new Plural(P + "report.optional_n", report.optionalQuests())) : null,
                Text.of(P + "report.pair", report.chapters(), report.quests()), List.of()));
        carried.add(new Row(Badge.DONE, 0, Text.of(P + "report.tasks"), null, Text.of(P + "report.pair", report.tasks(), report.rewards()), List.of()));
        if (!report.languages().isEmpty()) {
            carried.add(new Row(Badge.DONE, 0, Text.of(P + "report.texts"), Text.of(P + "report.texts_sub"),
                    new Text(P + "report.languages", List.of(languages(report.languages()))),
                    List.of()));
        }
        if (report.dependencies() > 0) {
            carried.add(new Row(Badge.DONE, 0, Text.of(P + "report.links"), null, Text.of(P + "report.number", report.dependencies()), List.of()));
        }
        if (report.teams() > 0) {
            carried.add(new Row(Badge.DONE, 0, Text.of(P + "report.progress"), Text.of(P + "report.progress_sub"),
                    Text.of(P + "report.number", new Plural(P + "report.teams", report.teams())), List.of()));
        }
        List<Row> placed = new ArrayList<>();
        if (report.chaptersOnMap() > 0) {
            placed.add(new Row(Badge.MAP, 0, Text.of(P + "report.on_map"),
                    report.sections() > 0 ? Text.of(P + "report.number", new Plural(P + "report.sections", report.sections())) : null,
                    Text.of(P + "report.number", new Plural(P + "report.chapters_n", report.chaptersOnMap())), List.of()));
        }
        int offMap = report.chapters() - report.chaptersOnMap();
        if (offMap > 0) {
            placed.add(new Row(Badge.OFF_MAP, 0, Text.of(P + "report.off_map"), Text.of(P + "report.off_map_sub"),
                    Text.of(P + "report.number", new Plural(P + "report.chapters_n", offMap)), List.of()));
        }
        List<Row> manual = new ArrayList<>();
        List<String> order = List.of(ImportReport.UNCONVERTED_TASK, ImportReport.DROPPED_REWARD, ImportReport.LOOT_EMPTY_WEIGHT);
        List<ImportReport.Manual> entries = new ArrayList<>(report.manual());
        entries.sort(java.util.Comparator.comparingInt(entry -> order.contains(entry.kind()) ? order.indexOf(entry.kind()) : order.size()));
        for (ImportReport.Manual entry : entries) {
            if (entry.count() > 0) {
                manual.add(manualRow(entry));
            }
        }
        return new Report(carried, placed, manual, report.hiddenQuests());
    }

    /** "ru и en", or "ru, en и ещё 13" for a book in many languages: short codes, Russian and English first. */
    static Joined languages(List<String> locales) {
        List<String> codes = new ArrayList<>(locales.stream().map(locale -> locale.contains("_") ? locale.substring(0, locale.indexOf('_')) : locale).distinct().toList());
        codes.sort(java.util.Comparator.comparingInt(code -> code.equals("ru") ? 0 : code.equals("en") ? 1 : 2));
        List<Text> parts = new ArrayList<>();
        int shown = codes.size() <= 3 ? codes.size() : 2;
        for (int i = 0; i < shown; i++) {
            parts.add(Text.of(P + "report.lang", codes.get(i)));
        }
        if (shown < codes.size()) {
            parts.add(Text.of(P + "report.more_short", codes.size() - shown));
        }
        return new Joined(parts);
    }

    private static Row manualRow(ImportReport.Manual entry) {
        Joined types = new Joined(entry.types().stream().distinct().limit(3).map(FirstRun::ftbType).toList());
        int count = entry.hidden() ? entry.count() : 0;
        return switch (entry.kind()) {
            case ImportReport.UNCONVERTED_TASK -> new Row(Badge.COUNT, count, Text.of(P + "report.task_missing"),
                    new Text(P + "report.task_missing_sub", List.of(types)), null, entry.quests());
            case ImportReport.DROPPED_REWARD -> entry.types().stream().distinct().count() == 1
                    ? new Row(Badge.COUNT, count, ftbReward(entry.types().getFirst()), Text.of(P + "report.reward_dropped_one"), null, entry.quests())
                    : new Row(Badge.COUNT, count, Text.of(P + "report.reward_dropped"), new Text(P + "report.reward_dropped_sub", List.of(types)), null, entry.quests());
            case ImportReport.LOOT_EMPTY_WEIGHT -> new Row(Badge.COUNT, count, Text.of(P + "report.loot"),
                    Text.of(P + "report.loot_sub", new Plural(P + "report.rewards_n", entry.count())), null, entry.quests());
            default -> new Row(Badge.COUNT, count, Text.of(P + "report.other", entry.kind()), types.parts().isEmpty() ? null : new Text(P + "report.number", List.of(types)),
                    null, entry.quests());
        };
    }

    /** The name of an FTB Quests task type in quotes: "«наблюдение»". */
    static Text ftbType(String type) {
        return Text.of(P + "ftb.task", Text.of(P + "ftb.type." + path(type)).or(path(type)));
    }

    /** A reward type FTB Quests has and we do not, as a row title: "Награда-уведомление". */
    static Text ftbReward(String type) {
        return Text.of(P + "ftb.reward." + path(type)).or(Text.of(P + "report.reward_named", path(type)));
    }

    private static String path(String type) {
        int colon = type.indexOf(':');
        return (colon >= 0 ? type.substring(colon + 1) : type).toLowerCase(java.util.Locale.ROOT);
    }

    // --- the map form (pictures 07, 07b) ---

    /** The forms in the order of the preview's grid. */
    static final List<MapForm> FORMS = List.of(MapForm.SERPENTINE, MapForm.SPIRAL, MapForm.GENERATOR, MapForm.GALAXY);

    /**
     * What the advice knows about the book.
     *
     * @param auto the automatic choice, which the preview recommends
     * @param epochs the epochs the book names
     * @param imported whether all of them were made by the FTB Quests import
     * @param chapters chapters on the map
     * @param core chapters a galaxy puts in its core
     * @param arms arms of a galaxy
     */
    record Book(MapForms.Choice auto, int epochs, boolean imported, int chapters, int core, int arms) {
    }

    /** The one line of reason under a form in the grid: what the form is for and how this book fits it. */
    static Text formReason(MapForm form, Book book) {
        MapForm advised = book.auto().form();
        String base = P + "form." + form.getSerializedName() + ".";
        return switch (form) {
            case SERPENTINE -> Text.of(base + (advised == MapForm.SERPENTINE ? "advised" : book.epochs() > 0 && book.imported() ? "imported" : "no_epochs"));
            case SPIRAL -> Text.of(base + (advised == MapForm.GALAXY ? "core" : "other"));
            case GENERATOR -> Text.of(base + (advised == MapForm.GENERATOR ? "advised" : advised == MapForm.SERPENTINE ? "epochs" : "scattered"));
            case GALAXY -> advised == MapForm.GALAXY ? Text.of(base + "advised")
                    : book.chapters() < MapForms.GALAXY_MIN_CHAPTERS ? Text.of(base + "few", MapForms.GALAXY_MIN_CHAPTERS)
                    : Text.of(base + "linked", book.auto().crossLinks(), book.auto().links());
            case AUTO -> Text.of(base + "advised");
        };
    }

    /** "Почему галактика.": what the advice went by, in words (picture 07). */
    static Text why(Book book) {
        MapForms.Choice auto = book.auto();
        return switch (auto.form()) {
            case SERPENTINE -> Text.of(P + "why.serpentine", new Plural(P + "why.chapters", auto.chaptersWithEpoch()), auto.chapters());
            case GALAXY -> book.imported() && book.epochs() > 0
                    ? Text.of(P + "why.galaxy_ftb", new Plural(P + "why.core", book.core()), new Plural(P + "why.rest", Math.max(0, book.chapters() - book.core())),
                            auto.crossLinks(), auto.links(), new Plural(P + "why.arms", book.arms()))
                    : Text.of(P + "why.galaxy", new Plural(P + "why.chapters", book.chapters()), auto.crossLinks(), auto.links());
            default -> book.chapters() < MapForms.GALAXY_MIN_CHAPTERS
                    ? Text.of(P + "why.generator_few", new Plural(P + "why.chapters", book.chapters()), MapForms.GALAXY_MIN_CHAPTERS)
                    : Text.of(P + "why.generator", auto.crossLinks(), auto.links());
        };
    }

    /**
     * The form to write when the author picks one: the advised form stays {@link MapForm#AUTO}, so that the book keeps following
     * the rule as it grows; any other is written as it is.
     */
    static MapForm formToSave(MapForm picked, MapForm advised) {
        return picked == advised ? MapForm.AUTO : picked;
    }

    // --- the new book (picture 09a) ---

    /** The answers to "Как устроена сборка?" and the form each picks; "Пока не знаю" leaves it to the rule. */
    enum Answer {
        ORDER(MapForm.SERPENTINE),
        FINALE(MapForm.SPIRAL),
        CORE(MapForm.GALAXY),
        UNKNOWN(MapForm.AUTO);

        final MapForm form;

        Answer(MapForm form) {
            this.form = form;
        }

        String key() {
            return P + "new.answer." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The name as it will be saved: trimmed, at most {@code max} characters, null when nothing is left. */
    @Nullable
    static String bookTitle(String typed, int max) {
        String title = typed.strip();
        if (title.isEmpty()) {
            return null;
        }
        return title.length() > max ? title.substring(0, max).strip() : title;
    }

    // --- the publication question (picture 10) ---

    /**
     * What players will get when the book opens.
     *
     * @param chapters chapters with at least one quest players see
     * @param quests quests players see: neither drafts nor archived
     * @param drafts quests that stay hidden until the author looks at them
     * @param broken links to quests that do not exist
     * @param stuck quests players see that wait for a hidden one, so they can never open
     */
    record Publish(int chapters, int quests, int drafts, int broken, int stuck) {
        boolean clean() {
            return broken == 0 && stuck == 0;
        }
    }

    static Publish publish(QuestBook book) {
        Map<ResourceLocation, Quest> quests = book.quests();
        Set<ResourceLocation> chapters = new HashSet<>();
        int live = 0;
        int drafts = 0;
        int broken = 0;
        int stuck = 0;
        for (Quest quest : quests.values()) {
            if (quest.options().archived()) {
                continue;
            }
            if (quest.options().draft()) {
                drafts++;
                continue;
            }
            live++;
            quest.chapter().ifPresent(chapters::add);
            boolean waits = false;
            for (ResourceLocation dependency : quest.requires()) {
                Quest other = quests.get(dependency);
                if (other == null) {
                    broken++;
                } else if (!other.options().live()) {
                    waits = true;
                }
            }
            if (waits) {
                stuck++;
            }
        }
        return new Publish(chapters.size(), live, drafts, broken, stuck);
    }
}
