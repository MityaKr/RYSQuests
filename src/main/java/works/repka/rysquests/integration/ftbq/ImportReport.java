package works.repka.rysquests.integration.ftbq;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * What an FTB Quests import did, by kind, for the report page of the import (plan, picture 06): what came across, where it went,
 * and what waits for the author's hand. Only what is there is shown, so a kind with nothing in it is simply absent.
 *
 * @param optionalQuests how many of the quests are optional
 * @param languages the locales the texts came in
 * @param dependencies links between quests
 * @param sections FTB chapter groups that became sections of the book; they are not the author's epochs (plan, section 5)
 * @param chaptersOnMap chapters placed on the map
 * @param manual what needs the author's hand
 * @param hiddenQuests quests kept from players until the author looks at them: drafts
 * @param teams FTB Quests teams whose progress came across, 0 when progress was not carried over
 * @param skippedFiles files left out on the author's word, after they could not be read
 * @param reportFile where the full report lies, relative to the game folder
 */
public record ImportReport(int chapters, int quests, int optionalQuests, int tasks, int rewards, List<String> languages, int dependencies,
        int sections, int chaptersOnMap, List<Manual> manual, int hiddenQuests, int teams, List<String> skippedFiles, String reportFile) {

    /** A task FTB Quests has and the book does not: it stands in as a checkbox, and its quest is a draft. */
    public static final String UNCONVERTED_TASK = "unconverted_task";
    /** A reward the book cannot give: the quest comes without it, as a draft. */
    public static final String DROPPED_REWARD = "dropped_reward";
    /** A loot roll that could give nothing in FTB Quests always gives something here; the quests stay visible. */
    public static final String LOOT_EMPTY_WEIGHT = "loot_empty_weight";

    /**
     * One kind of thing to fix by hand.
     *
     * @param count how many things of this kind
     * @param types the FTB Quests types behind them, such as {@code observation} or {@code energy}
     * @param hidden whether their quests were made drafts
     * @param quests the quests concerned, ours
     */
    public record Manual(String kind, int count, List<String> types, boolean hidden, List<String> quests) {
        public static final Codec<Manual> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("kind").forGetter(Manual::kind),
                Codec.INT.fieldOf("count").forGetter(Manual::count),
                Codec.STRING.listOf().fieldOf("types").forGetter(Manual::types),
                Codec.BOOL.fieldOf("hidden").forGetter(Manual::hidden),
                Codec.STRING.listOf().fieldOf("quests").forGetter(Manual::quests)
        ).apply(instance, Manual::new));
    }

    private record Counts(int chapters, int quests, int optionalQuests, int tasks, int rewards, List<String> languages, int dependencies) {
        static final Codec<Counts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("chapters").forGetter(Counts::chapters),
                Codec.INT.fieldOf("quests").forGetter(Counts::quests),
                Codec.INT.fieldOf("optional_quests").forGetter(Counts::optionalQuests),
                Codec.INT.fieldOf("tasks").forGetter(Counts::tasks),
                Codec.INT.fieldOf("rewards").forGetter(Counts::rewards),
                Codec.STRING.listOf().fieldOf("languages").forGetter(Counts::languages),
                Codec.INT.fieldOf("dependencies").forGetter(Counts::dependencies)
        ).apply(instance, Counts::new));
    }

    public static final Codec<ImportReport> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Counts.CODEC.fieldOf("carried").forGetter(report -> new Counts(report.chapters, report.quests, report.optionalQuests, report.tasks, report.rewards, report.languages, report.dependencies)),
            Codec.INT.fieldOf("sections").forGetter(ImportReport::sections),
            Codec.INT.fieldOf("chapters_on_map").forGetter(ImportReport::chaptersOnMap),
            Manual.CODEC.listOf().fieldOf("manual").forGetter(ImportReport::manual),
            Codec.INT.fieldOf("hidden_quests").forGetter(ImportReport::hiddenQuests),
            Codec.INT.fieldOf("teams").forGetter(ImportReport::teams),
            Codec.STRING.listOf().fieldOf("skipped_files").forGetter(ImportReport::skippedFiles),
            Codec.STRING.fieldOf("report_file").forGetter(ImportReport::reportFile)
    ).apply(instance, (counts, sections, onMap, manual, hidden, teams, skipped, file) -> new ImportReport(counts.chapters, counts.quests, counts.optionalQuests, counts.tasks,
            counts.rewards, counts.languages, counts.dependencies, sections, onMap, manual, hidden, teams, skipped, file)));

    public ImportReport withTeams(int count) {
        return new ImportReport(chapters, quests, optionalQuests, tasks, rewards, languages, dependencies, sections, chaptersOnMap, manual, hiddenQuests, count, skippedFiles, reportFile);
    }

    public ImportReport withFiles(List<String> skipped, String report) {
        return new ImportReport(chapters, quests, optionalQuests, tasks, rewards, languages, dependencies, sections, chaptersOnMap, manual, hiddenQuests, teams, List.copyOf(skipped), report);
    }

    public Manual manual(String kind) {
        return manual.stream().filter(entry -> entry.kind().equals(kind)).findFirst().orElse(new Manual(kind, 0, List.of(), false, List.of()));
    }
}
