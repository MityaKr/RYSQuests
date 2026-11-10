package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** One quest. The chapter may be omitted in JSON, then it comes from the quest's folder. */
public record Quest(
        ResourceLocation id,
        Optional<ResourceLocation> chapter,
        Optional<String> group,
        String title,
        Optional<String> subtitle,
        Optional<String> text,
        Optional<ResourceLocation> icon,
        List<ResourceLocation> requires,
        RequiresMode requiresMode,
        Optional<String> stageRequired,
        List<TaskDef> tasks,
        List<RewardDef> rewards,
        Optional<GuideRef> guide,
        QuestOptions options) {

    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Quest::id),
            ResourceLocation.CODEC.optionalFieldOf("chapter").forGetter(Quest::chapter),
            Codec.STRING.optionalFieldOf("group").forGetter(Quest::group),
            Codec.STRING.fieldOf("title").forGetter(Quest::title),
            Codec.STRING.optionalFieldOf("subtitle").forGetter(Quest::subtitle),
            Codec.STRING.optionalFieldOf("text").forGetter(Quest::text),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(Quest::icon),
            ResourceLocation.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Quest::requires),
            RequiresMode.CODEC.optionalFieldOf("requires_mode", RequiresMode.ALL).forGetter(Quest::requiresMode),
            Codec.STRING.optionalFieldOf("stage_required").forGetter(Quest::stageRequired),
            TaskDef.CODEC.listOf().optionalFieldOf("tasks", List.of()).forGetter(Quest::tasks),
            RewardDef.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Quest::rewards),
            GuideRef.CODEC.optionalFieldOf("guide").forGetter(Quest::guide),
            QuestOptions.CODEC.forGetter(Quest::options)
    ).apply(instance, Quest::new));

    public Quest withChapter(ResourceLocation chapterId) {
        return new Quest(id, Optional.of(chapterId), group, title, subtitle, text, icon, requires, requiresMode,
                stageRequired, tasks, rewards, guide, options);
    }
}
