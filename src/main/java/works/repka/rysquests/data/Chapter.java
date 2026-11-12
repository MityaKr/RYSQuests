package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Metadata of a chapter, from its {@code _chapter.json}. */
public record Chapter(
        ResourceLocation id,
        String title,
        Optional<String> subtitle,
        Optional<ResourceLocation> icon,
        Optional<Integer> epoch,
        Optional<ViewMode> defaultView,
        List<Group> groups,
        List<ResourceLocation> requires,
        boolean hidden,
        String section,
        List<ResourceLocation> links) {
    /** The section of the book that holds the progression chapters, and the only one shown so far. */
    public static final String STORY = "story";

    /** A chapter without quests of other chapters shown on its map. */
    public Chapter(ResourceLocation id, String title, Optional<String> subtitle, Optional<ResourceLocation> icon, Optional<Integer> epoch,
            Optional<ViewMode> defaultView, List<Group> groups, List<ResourceLocation> requires, boolean hidden, String section) {
        this(id, title, subtitle, icon, epoch, defaultView, groups, requires, hidden, section, List.of());
    }

    public record Group(String id, String title) {
        static final Codec<Group> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Group::id),
                Codec.STRING.fieldOf("title").forGetter(Group::title)
        ).apply(instance, Group::new));
    }

    public static final Codec<Chapter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Chapter::id),
            Codec.STRING.fieldOf("title").forGetter(Chapter::title),
            Codec.STRING.optionalFieldOf("subtitle").forGetter(Chapter::subtitle),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(Chapter::icon),
            Codec.INT.optionalFieldOf("epoch").forGetter(Chapter::epoch),
            ViewMode.CODEC.optionalFieldOf("default_view").forGetter(Chapter::defaultView),
            Group.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(Chapter::groups),
            ResourceLocation.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Chapter::requires),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Chapter::hidden),
            Codec.STRING.optionalFieldOf("section", STORY).forGetter(Chapter::section),
            // Quests of other chapters shown on this chapter's map, as FTB Quests' quest links (ADR-46).
            ResourceLocation.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(Chapter::links)
    ).apply(instance, Chapter::new));

    public boolean isStory() {
        return STORY.equals(section);
    }
}
