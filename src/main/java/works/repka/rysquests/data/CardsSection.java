package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/** The "cards" template: a catalog with filters. A card can be marked done by the same conditions as a quest task. */
public record CardsSection(
        ResourceLocation id,
        String title,
        Optional<String> shortTitle,
        Optional<String> subtitle,
        Optional<ResourceLocation> icon,
        List<String> groups,
        List<Card> cards) implements SectionDef {
    public static final String TEMPLATE = "cards";

    public record Need(ResourceLocation item, int count) {
        static final Codec<Need> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Need::item),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Need::count)
        ).apply(instance, Need::new));
    }

    /** @param art the background of the picture area as {@code #rrggbb}, the pack author's own color */
    public record Card(String id, Optional<ResourceLocation> icon, Optional<String> group, Optional<String> art, Optional<String> stage,
            Optional<String> stageTitle, Optional<TaskDef> done, Optional<GuideRef> page, Optional<ResourceLocation> quest,
            List<Need> needs, List<ResourceLocation> gives) {
        static final Codec<Card> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Card::id),
                ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(Card::icon),
                Codec.STRING.optionalFieldOf("group").forGetter(Card::group),
                Codec.STRING.optionalFieldOf("art").forGetter(Card::art),
                Codec.STRING.optionalFieldOf("stage").forGetter(Card::stage),
                Codec.STRING.optionalFieldOf("stage_title").forGetter(Card::stageTitle),
                TaskDef.CODEC.optionalFieldOf("done").forGetter(Card::done),
                GuideRef.CODEC.optionalFieldOf("page").forGetter(Card::page),
                ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(Card::quest),
                Need.CODEC.listOf().optionalFieldOf("needs", List.of()).forGetter(Card::needs),
                ResourceLocation.CODEC.listOf().optionalFieldOf("gives", List.of()).forGetter(Card::gives)
        ).apply(instance, Card::new));
    }

    public static final MapCodec<CardsSection> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(CardsSection::id),
            Codec.STRING.fieldOf("title").forGetter(CardsSection::title),
            Codec.STRING.optionalFieldOf("short_title").forGetter(CardsSection::shortTitle),
            Codec.STRING.optionalFieldOf("subtitle").forGetter(CardsSection::subtitle),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(CardsSection::icon),
            Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(CardsSection::groups),
            Card.CODEC.listOf().fieldOf("cards").forGetter(CardsSection::cards)
    ).apply(instance, CardsSection::new));

    @Override
    public String template() {
        return TEMPLATE;
    }

    @Override
    public Set<String> textKeys() {
        Set<String> keys = commonKeys();
        for (String part : List.of(".done", ".open", ".locked", ".done_count")) {
            keys.add(title + part);
        }
        groups.forEach(group -> keys.add(groupTitle(group)));
        for (Card card : cards) {
            for (String part : List.of("name", "desc", "meta", "text", "tip", "needs_title", "gives_title")) {
                keys.add(cardKey(card, part));
            }
            card.stageTitle().ifPresent(keys::add);
        }
        return keys;
    }

    public String groupTitle(String group) {
        return title + ".group." + group;
    }

    /** A lang key of a card, such as {@code name} or {@code text}. */
    public String cardKey(Card card, String part) {
        return title + "." + card.id() + "." + part;
    }

    /** The chapter that holds the conditions of the cards. It is not part of the story, so the book does not show it. */
    public ResourceLocation holderChapter() {
        return id.withPath("_cards/" + id.getPath());
    }

    /** A card's condition runs as a hidden quest, which gives it events, polling and team progress for free. */
    public ResourceLocation questId(Card card) {
        return id.withPath("_cards/" + id.getPath() + "/" + card.id());
    }
}
