package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;

/**
 * Optional quest fields, kept apart because a record codec takes at most 16 fields.
 *
 * @param milestone a milestone of the book, drawn larger; the old {@code size: 2} reads as one
 * @param draft hidden from players until the author has looked at it, as the FTB Quests import leaves a quest it could not carry
 *              over in full; the server neither sends it to players nor counts it
 * @param archived taken out of the book without deleting the file: players never see it and quests that needed it no longer wait
 * @param preComplete whether the tasks count while the quest is still locked (ADR-66); empty takes the chapter's, then the book's
 */
public record QuestOptions(
        Layout layout,
        boolean milestone,
        boolean optional,
        boolean hidden,
        Optional<Repeatable> repeatable,
        boolean page,
        List<String> tags,
        boolean draft,
        boolean archived,
        Optional<Boolean> preComplete) {

    public static final QuestOptions DEFAULT = new QuestOptions(Layout.DEFAULT, false, false, false, Optional.empty(), false, List.of(), false, false);

    /** The fields as they were before the early count (ADR-66): the quest takes it from its chapter and book. */
    public QuestOptions(Layout layout, boolean milestone, boolean optional, boolean hidden, Optional<Repeatable> repeatable, boolean page,
            List<String> tags, boolean draft, boolean archived) {
        this(layout, milestone, optional, hidden, repeatable, page, tags, draft, archived, Optional.empty());
    }

    public record Repeatable(int cooldownMinutes) {
        static final Codec<Repeatable> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("cooldown_minutes").forGetter(Repeatable::cooldownMinutes)
        ).apply(instance, Repeatable::new));
    }

    /** The fields as they were before milestones, drafts and the archive: {@code size} 2 is a milestone. */
    public QuestOptions(Layout layout, int size, boolean optional, boolean hidden, Optional<Repeatable> repeatable, boolean page, List<String> tags) {
        this(layout, size >= 2, optional, hidden, repeatable, page, tags, false, false);
    }

    public static final MapCodec<QuestOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Layout.CODEC.optionalFieldOf("layout", Layout.DEFAULT).forGetter(QuestOptions::layout),
            // Read only: size 2 was the milestone before the field had its name. It is never written again.
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("size", 1).forGetter(options -> 1),
            Codec.BOOL.optionalFieldOf("milestone", false).forGetter(QuestOptions::milestone),
            Codec.BOOL.optionalFieldOf("optional", false).forGetter(QuestOptions::optional),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(QuestOptions::hidden),
            Repeatable.CODEC.optionalFieldOf("repeatable").forGetter(QuestOptions::repeatable),
            Codec.BOOL.optionalFieldOf("page", false).forGetter(QuestOptions::page),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(QuestOptions::tags),
            Codec.BOOL.optionalFieldOf("draft", false).forGetter(QuestOptions::draft),
            Codec.BOOL.optionalFieldOf("archived", false).forGetter(QuestOptions::archived),
            // Absent: as the chapter says, and the chapter as the book says (ADR-66).
            Codec.BOOL.optionalFieldOf("pre_complete").forGetter(QuestOptions::preComplete)
    ).apply(instance, (layout, size, milestone, optional, hidden, repeatable, page, tags, draft, archived, preComplete) ->
            new QuestOptions(layout, milestone || size >= 2, optional, hidden, repeatable, page, tags, draft, archived, preComplete)));

    /** Players see the quest and the server counts it: neither a draft nor in the archive. */
    public boolean live() {
        return !draft && !archived;
    }

    public QuestOptions withLayout(Layout newLayout) {
        return new QuestOptions(newLayout, milestone, optional, hidden, repeatable, page, tags, draft, archived, preComplete);
    }

    public QuestOptions withMilestone(boolean value) {
        return new QuestOptions(layout, value, optional, hidden, repeatable, page, tags, draft, archived, preComplete);
    }

    public QuestOptions withOptional(boolean value) {
        return new QuestOptions(layout, milestone, value, hidden, repeatable, page, tags, draft, archived, preComplete);
    }

    public QuestOptions withDraft(boolean value) {
        return new QuestOptions(layout, milestone, optional, hidden, repeatable, page, tags, value, archived, preComplete);
    }

    public QuestOptions withPreComplete(Optional<Boolean> value) {
        return new QuestOptions(layout, milestone, optional, hidden, repeatable, page, tags, draft, archived, value);
    }

    public QuestOptions withArchived(boolean value) {
        return new QuestOptions(layout, milestone, optional, hidden, repeatable, page, tags, draft, value, preComplete);
    }
}
