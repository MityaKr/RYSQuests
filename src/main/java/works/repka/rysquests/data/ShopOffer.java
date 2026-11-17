package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.task.KillTask;

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
        Optional<ResourceLocation> quest,
        Optional<String> lockText,
        boolean delivery) {

    /** A trade as it was before {@code lock_text}. */
    public ShopOffer(ResourceLocation id, Optional<String> title, Optional<ResourceLocation> icon, Optional<String> category, List<Entry> cost,
            List<Entry> gives, Optional<Limit> limit, Optional<String> stage, Optional<String> stageTitle, Optional<ResourceLocation> quest) {
        this(id, title, icon, category, cost, gives, limit, stage, stageTitle, quest, Optional.empty(), false);
    }

    /** A trade paid at once, as it was before {@code delivery}. */
    public ShopOffer(ResourceLocation id, Optional<String> title, Optional<ResourceLocation> icon, Optional<String> category, List<Entry> cost,
            List<Entry> gives, Optional<Limit> limit, Optional<String> stage, Optional<String> stageTitle, Optional<ResourceLocation> quest, Optional<String> lockText) {
        this(id, title, icon, category, cost, gives, limit, stage, stageTitle, quest, lockText, false);
    }

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

    /**
     * One side of a trade. @param id a currency name, or an item id
     * @param components data components of the item given, as in an item reward (a Create schematic with its file); only in
     *        {@code gives}: what a player pays with is always a plain item
     */
    public record Entry(Kind type, String id, int amount, Optional<DataComponentPatch> components) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Kind.CODEC.fieldOf("type").forGetter(Entry::type),
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("amount").forGetter(entry -> entry.type == Kind.CURRENCY ? Optional.of(entry.amount) : Optional.empty()),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(entry -> entry.type == Kind.ITEM ? Optional.of(entry.amount) : Optional.empty()),
                DataComponentPatch.CODEC.optionalFieldOf("components").forGetter(Entry::components)
        ).apply(instance, (type, id, amount, count, components) -> new Entry(type, id, amount.or(() -> count).orElse(1), components.filter(patch -> !patch.isEmpty()))));

        public Entry(Kind type, String id, int amount) {
            this(type, id, amount, Optional.empty());
        }

        public boolean isCurrency() {
            return type == Kind.CURRENCY;
        }

        public boolean hasComponents() {
            return components.isPresent();
        }

        public Optional<ResourceLocation> item() {
            return type == Kind.ITEM ? Optional.ofNullable(ResourceLocation.tryParse(id)) : Optional.empty();
        }

        /** One of the item as it is given, its components applied; empty for currency or an unknown item. */
        public ItemStack stack() {
            Optional<ResourceLocation> item = item();
            if (item.isEmpty() || !BuiltInRegistries.ITEM.containsKey(item.get())) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item.get()));
            components.ifPresent(stack::applyComponents);
            return stack;
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

        /** A team buys it a set number of times for good, such as a schematic once per team. */
        public boolean onceForTeam() {
            return per == Per.TEAM && reset == Reset.NEVER;
        }
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
            ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(ShopOffer::quest),
            Codec.STRING.optionalFieldOf("lock_text").forGetter(ShopOffer::lockText),
            Codec.BOOL.optionalFieldOf("delivery", false).forGetter(ShopOffer::delivery)
    ).apply(instance, ShopOffer::new)).validate(offer -> offer.cost.isEmpty() || offer.gives.isEmpty()
            ? DataResult.error(() -> "A trade needs both 'cost' and 'gives'")
            : DataResult.success(offer));

    /**
     * What a delivery trade asks to be delivered, by item id, in the order of {@code cost}: its items, which the team brings in parts.
     * Its currency is paid only when the goods are taken. Empty for a trade paid at once.
     */
    public Map<String, Long> deliveryRows() {
        Map<String, Long> rows = new java.util.LinkedHashMap<>();
        if (delivery) {
            for (Entry entry : cost) {
                if (!entry.isCurrency()) {
                    rows.merge(entry.id(), (long) entry.amount(), Long::sum);
                }
            }
        }
        return rows;
    }

    /**
     * How far a delivery has come, from 0 to 1: the mean of its rows, each counted up to what it needs, so that one finished row of
     * three is a third whatever the numbers. @param delivered what was delivered, by item
     */
    public float deliveryProgress(Map<String, Long> delivered) {
        Map<String, Long> rows = deliveryRows();
        if (rows.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Map.Entry<String, Long> row : rows.entrySet()) {
            sum += Math.min(1.0, (double) delivered.getOrDefault(row.getKey(), 0L) / row.getValue());
        }
        return (float) (sum / rows.size());
    }

    /**
     * How much of what is held goes to a delivery: for each row what it still lacks, up to what is held, so that nothing is taken
     * beyond the need. @param held what the inventory holds, by item @return by item, only the rows that take something
     */
    public Map<String, Long> toDeliver(Map<String, Long> delivered, Map<String, Long> held) {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        deliveryRows().forEach((item, need) -> {
            long amount = Math.min(Math.max(0, need - delivered.getOrDefault(item, 0L)), held.getOrDefault(item, 0L));
            if (amount > 0) {
                out.put(item, amount);
            }
        });
        return out;
    }

    /** Whether every row of a delivery is full, so the goods can be taken once the coins are paid. */
    public boolean deliveryComplete(Map<String, Long> delivered) {
        Map<String, Long> rows = deliveryRows();
        return !rows.isEmpty() && rows.entrySet().stream().allMatch(row -> delivered.getOrDefault(row.getKey(), 0L) >= row.getValue());
    }

    /**
     * The boss the team has to defeat before the trade opens: its {@code quest} is the tier of an achievement that counts kills of
     * one entity, such as {@code repys:_achievements/slayer_ignis/gold}. Such a tier is kept for the team, so the first kill by any
     * member opens the trade for all of them, for good.
     */
    public Optional<ResourceLocation> bossToDefeat(Map<ResourceLocation, Quest> quests) {
        return quest.filter(Achievement::isTierQuest).map(quests::get)
                .filter(tier -> tier.tasks().size() == 1 && tier.tasks().getFirst() instanceof KillTask)
                .map(tier -> ((KillTask) tier.tasks().getFirst()).entity());
    }

    /**
     * The strings of the trade that may be lang keys, which the editor writes into the lang file on save: its name, its section, the
     * line of its closed card and the name of its stage.
     */
    public List<String> textKeys() {
        List<String> keys = new java.util.ArrayList<>();
        title.ifPresent(keys::add);
        category.ifPresent(keys::add);
        lockText.ifPresent(keys::add);
        stageTitle.ifPresent(keys::add);
        return keys;
    }

    /** Whether the player gets only currency out of it, which the shop lists under selling. */
    public boolean isSale() {
        return gives.stream().allMatch(Entry::isCurrency);
    }
}
