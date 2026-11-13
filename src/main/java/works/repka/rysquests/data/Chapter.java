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
        List<ResourceLocation> links,
        boolean offMap) {
    /** The section of the book that holds the progression chapters, and the only one shown so far. */
    public static final String STORY = "story";

    public Chapter {
        // Epochs count from 1. Books written before off_map put their intro and chronicle into epoch 0 (RepYS up to 0.1.0): that is
        // no epoch, so in a book with epochs those chapters stay off the map, as off_map puts them now.
        epoch = epoch.filter(number -> number > 0);
    }

    /** A chapter on the map, without quests of other chapters shown on it. */
    public Chapter(ResourceLocation id, String title, Optional<String> subtitle, Optional<ResourceLocation> icon, Optional<Integer> epoch,
            Optional<ViewMode> defaultView, List<Group> groups, List<ResourceLocation> requires, boolean hidden, String section) {
        this(id, title, subtitle, icon, epoch, defaultView, groups, requires, hidden, section, List.of(), false);
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
            ResourceLocation.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(Chapter::links),
            // Off the map: listed under "Off the map" and opened as a list, as the book's intro and chronicle are.
            Codec.BOOL.optionalFieldOf("off_map", false).forGetter(Chapter::offMap)
    ).apply(instance, Chapter::new));

    public boolean isStory() {
        return STORY.equals(section);
    }

    public Chapter withEpoch(Optional<Integer> newEpoch) {
        return new Chapter(id, title, subtitle, icon, newEpoch, defaultView, groups, requires, hidden, section, links, offMap);
    }

    public Chapter withGroups(List<Group> newGroups) {
        return new Chapter(id, title, subtitle, icon, epoch, defaultView, List.copyOf(newGroups), requires, hidden, section, links, offMap);
    }

    public Chapter withTitle(String newTitle) {
        return new Chapter(id, newTitle, subtitle, icon, epoch, defaultView, groups, requires, hidden, section, links, offMap);
    }

    public Chapter withOffMap(boolean off) {
        return new Chapter(id, title, subtitle, icon, epoch, defaultView, groups, requires, hidden, section, links, off);
    }
}
