package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

/** A trade of the shop: one file in {@code rysquests/shop/}. A sale is simply a trade that gives only currency. */
public record ShopOffer(
        ResourceLocation id,
        Optional<String> title,
        Optional<ResourceLocation> icon,
        Optional<String> category,
        List<Entry> cost,
        List<Entry> gives,
        Optional<Limit> limit,
        Optional<String> stage,
        Optional<String> stageTitle,
        Optional<ResourceLocation> quest) {

    public enum Kind implements StringRepresentable {
        CURRENCY("currency"), ITEM("item");

        static final Codec<Kind> CODEC = StringRepresentable.fromValues(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** One side of a trade. @param id a currency name, or an item id */
    public record Entry(Kind type, String id, int amount) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Kind.CODEC.fieldOf("type").forGetter(Entry::type),
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("amount").forGetter(entry -> entry.type == Kind.CURRENCY ? Optional.of(entry.amount) : Optional.empty()),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(entry -> entry.type == Kind.ITEM ? Optional.of(entry.amount) : Optional.empty())
        ).apply(instance, (type, id, amount, count) -> new Entry(type, id, amount.or(() -> count).orElse(1))));

        public boolean isCurrency() {
            return type == Kind.CURRENCY;
        }

        public Optional<ResourceLocation> item() {
            return type == Kind.ITEM ? Optional.ofNullable(ResourceLocation.tryParse(id)) : Optional.empty();
        }
    }

    public enum Per implements StringRepresentable {
        PLAYER("player"), TEAM("team");

        static final Codec<Per> CODEC = StringRepresentable.fromValues(Per::values);
        private final String name;

        Per(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Reset implements StringRepresentable {
        NEVER("never"), DAILY("daily"), WEEKLY("weekly");

        static final Codec<Reset> CODEC = StringRepresentable.fromValues(Reset::values);
        private final String name;

        Reset(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Limit(Per per, int count, Reset reset) {
        static final Codec<Limit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Per.CODEC.optionalFieldOf("per", Per.PLAYER).forGetter(Limit::per),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Limit::count),
                Reset.CODEC.optionalFieldOf("reset", Reset.NEVER).forGetter(Limit::reset)
        ).apply(instance, Limit::new));
    }

    public static final Codec<ShopOffer> CODEC = RecordCodecBuilder.<ShopOffer>create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ShopOffer::id),
            Codec.STRING.optionalFieldOf("title").forGetter(ShopOffer::title),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(ShopOffer::icon),
            Codec.STRING.optionalFieldOf("category").forGetter(ShopOffer::category),
            Entry.CODEC.listOf().fieldOf("cost").forGetter(ShopOffer::cost),
            Entry.CODEC.listOf().fieldOf("gives").forGetter(ShopOffer::gives),
            Limit.CODEC.optionalFieldOf("limit").forGetter(ShopOffer::limit),
            Codec.STRING.optionalFieldOf("stage").forGetter(ShopOffer::stage),
            Codec.STRING.optionalFieldOf("stage_title").forGetter(ShopOffer::stageTitle),
            ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(ShopOffer::quest)
    ).apply(instance, ShopOffer::new)).validate(offer -> offer.cost.isEmpty() || offer.gives.isEmpty()
            ? DataResult.error(() -> "A trade needs both 'cost' and 'gives'")
            : DataResult.success(offer));

    /** Whether the player gets only currency out of it, which the shop lists under selling. */
    public boolean isSale() {
        return gives.stream().allMatch(Entry::isCurrency);
    }
}
