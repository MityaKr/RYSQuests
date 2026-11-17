package works.repka.rysquests.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The rule of the automatic map form (plan, section 5), in the order the author set:
 * <ol>
 *     <li>most chapters on the map have an epoch: serpentine;</li>
 *     <li>few links between chapters and at least {@value #GALAXY_MIN_CHAPTERS} chapters: galaxy;</li>
 *     <li>otherwise the generator.</li>
 * </ol>
 * An epoch counts only when the author set it: epochs the FTB Quests import made from chapter groups do not, so a carried over
 * book like ATM10 comes to rule 2. Pure, so that it can be tested on real books and run on either side.
 */
public final class MapForms {
    public static final int GALAXY_MIN_CHAPTERS = 8;
    /** At most this share of the links between quests may cross from one chapter to another for "few links". */
    public static final double FEW_LINKS = 0.05;

    private MapForms() {
    }

    /** Why the form was chosen, for the one line of reason the form preview shows. */
    public enum Reason {
        /** The book's settings name the form. */
        SET_BY_AUTHOR,
        /** Rule 1: most chapters have the author's epoch. */
        EPOCHS,
        /** Rule 2: chapters hardly link to each other, and there are enough of them. */
        FEW_LINKS,
        /** Rule 3: neither of the above. */
        OTHER
    }

    /**
     * The form and what it was chosen by.
     *
     * @param chapters chapters on the map
     * @param chaptersWithEpoch of them, those with an epoch the author set
     * @param links dependencies between live quests on the map
     * @param crossLinks of them, those from one chapter to another: "18 of 5 277" in the reason of a galaxy
     */
    public record Choice(MapForm form, Reason reason, int chapters, int chaptersWithEpoch, int links, int crossLinks) {
    }

    /** The form the book is shown in: the one its settings name, or the automatic one. */
    public static Choice choose(QuestBook book) {
        return choose(book.meta(), List.copyOf(book.chapters().values()), List.copyOf(book.quests().values()));
    }

    public static Choice choose(BookMeta meta, List<Chapter> chapters, List<Quest> quests) {
        Choice auto = auto(meta, chapters, quests);
        return meta.mapForm() == MapForm.AUTO ? auto
                : new Choice(meta.mapForm(), Reason.SET_BY_AUTHOR, auto.chapters(), auto.chaptersWithEpoch(), auto.links(), auto.crossLinks());
    }

    /** The automatic form, whatever the settings say: the form preview recommends it. */
    public static Choice auto(BookMeta meta, List<Chapter> chapters, List<Quest> quests) {
        Map<ResourceLocation, Chapter> onMap = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> chapterOf = new HashMap<>();
        Map<ResourceLocation, Chapter> byId = new HashMap<>();
        chapters.forEach(chapter -> byId.put(chapter.id(), chapter));
        for (Quest quest : quests) {
            Chapter chapter = quest.chapter().map(byId::get).orElse(null);
            if (quest.options().live() && chapter != null && onMap(chapter)) {
                onMap.put(chapter.id(), chapter);
                chapterOf.put(quest.id(), chapter.id());
            }
        }
        int withEpoch = (int) onMap.values().stream().filter(chapter -> meta.authorEpoch(chapter.epoch())).count();
        int links = 0;
        int cross = 0;
        for (Quest quest : quests) {
            ResourceLocation own = chapterOf.get(quest.id());
            if (own == null) {
                continue;
            }
            for (ResourceLocation dependency : quest.requires()) {
                ResourceLocation other = chapterOf.get(dependency);
                if (other != null) {
                    links++;
                    if (!other.equals(own)) {
                        cross++;
                    }
                }
            }
        }
        int count = onMap.size();
        if (count > 0 && withEpoch * 2 > count) {
            return new Choice(MapForm.SERPENTINE, Reason.EPOCHS, count, withEpoch, links, cross);
        }
        if (count >= GALAXY_MIN_CHAPTERS && cross <= links * FEW_LINKS) {
            return new Choice(MapForm.GALAXY, Reason.FEW_LINKS, count, withEpoch, links, cross);
        }
        return new Choice(MapForm.GENERATOR, Reason.OTHER, count, withEpoch, links, cross);
    }

    /** A chapter of the story that is neither hidden nor set off the map. */
    public static boolean onMap(Chapter chapter) {
        return chapter.isStory() && !chapter.hidden() && !chapter.offMap();
    }
}
