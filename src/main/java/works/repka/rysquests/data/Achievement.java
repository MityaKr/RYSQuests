package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * An achievement: one file in {@code rysquests/achievements/}. Its {@code task} is read by the parser, which makes one
 * hidden quest per tier from it, so counting works exactly as it does for quests.
 */
public record Achievement(
        ResourceLocation id,
        Optional<String> title,
        Optional<String> text,
        Optional<String> hint,
        Optional<ResourceLocation> icon,
        Optional<String> category,
        boolean hidden,
        List<Tier> tiers,
        boolean archived) {

    /** The colour of a tier, set by its place among the tiers rather than by its name (plan, section 3, "Achievements"). */
    public enum Medal {
        BRONZE, SILVER, GOLD
    }

    /** An achievement as it was before the archive. */
    public Achievement(ResourceLocation id, Optional<String> title, Optional<String> text, Optional<String> hint, Optional<ResourceLocation> icon,
            Optional<String> category, boolean hidden, List<Tier> tiers) {
        this(id, title, text, hint, icon, category, hidden, tiers, false);
    }

    /** @param tier its name, such as {@code bronze}; only an id now, its colour comes from {@link #medal(int)} */
    public record Tier(String tier, int amount, List<RewardDef> rewards) {
        static final Codec<Tier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("tier").forGetter(Tier::tier),
                ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(Tier::amount),
                RewardDef.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Tier::rewards)
        ).apply(instance, Tier::new));
    }

    public static final Codec<Achievement> CODEC = RecordCodecBuilder.<Achievement>create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Achievement::id),
            Codec.STRING.optionalFieldOf("title").forGetter(Achievement::title),
            Codec.STRING.optionalFieldOf("text").forGetter(Achievement::text),
            Codec.STRING.optionalFieldOf("hint").forGetter(Achievement::hint),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(Achievement::icon),
            Codec.STRING.optionalFieldOf("category").forGetter(Achievement::category),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Achievement::hidden),
            Tier.CODEC.listOf().fieldOf("tiers").forGetter(Achievement::tiers),
            Codec.BOOL.optionalFieldOf("archived", false).forGetter(Achievement::archived)
    ).apply(instance, Achievement::new)).validate(achievement -> achievement.tiers.isEmpty()
            ? DataResult.error(() -> "An achievement needs at least one tier")
            : DataResult.success(achievement));

    private static final String CHAPTER_PATH = "_achievements";

    /**
     * The medal of the tier at this place: the last tier is gold, the one before it silver, every earlier one bronze. A single
     * tier is gold; four are bronze, bronze, silver, gold.
     */
    public static Medal medal(int index, int tierCount) {
        int fromTop = tierCount - 1 - index;
        return fromTop <= 0 ? Medal.GOLD : fromTop == 1 ? Medal.SILVER : Medal.BRONZE;
    }

    public Medal medal(int index) {
        return medal(index, tiers.size());
    }

    /** @return the medal of this tier, bronze for a tier that is not one of this achievement's */
    public Medal medal(Tier tier) {
        int index = tiers.indexOf(tier);
        return index < 0 ? Medal.BRONZE : medal(index);
    }

    public String titleKey() {
        return title.orElse(prefix() + ".name");
    }

    public String textKey() {
        return text.orElse(prefix() + ".text");
    }

    public String hintKey() {
        return hint.orElse(prefix() + ".hint");
    }

    private String prefix() {
        return "achievement." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** The chapter that holds the tiers, out of sight of the story. */
    public ResourceLocation holderChapter() {
        return id.withPath(CHAPTER_PATH);
    }

    /** The hidden quest that counts a tier. */
    public ResourceLocation questId(Tier tier) {
        return id.withPath(CHAPTER_PATH + "/" + id.getPath() + "/" + tier.tier());
    }

    public static boolean isTierQuest(ResourceLocation quest) {
        return quest.getPath().startsWith(CHAPTER_PATH + "/");
    }
}
