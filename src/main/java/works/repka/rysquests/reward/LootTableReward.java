package works.repka.rysquests.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import works.repka.rysquests.api.RewardType;
import works.repka.rysquests.data.RewardDef;

/** Rolls a loot table for the player. */
public record LootTableReward(ResourceLocation table, int rolls, boolean showChances) implements RewardDef {
    public static final MapCodec<LootTableReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("table").forGetter(LootTableReward::table),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("rolls", 1).forGetter(LootTableReward::rolls),
            Codec.BOOL.optionalFieldOf("show_chances", false).forGetter(LootTableReward::showChances)
    ).apply(instance, LootTableReward::new));

    @Override
    public RewardType<?> type() {
        return RewardTypes.LOOT_TABLE.get();
    }
}
