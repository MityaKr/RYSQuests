package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Achievement;

class AchievementTest {
    private static final UUID PLAYER = new UUID(1, 2);
    private static final Achievement SAMPLE = Achievement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
            {"id":"t:a/b","hidden":true,"tiers":[{"tier":"bronze","amount":10},{"tier":"gold","amount":100}]}""")).getOrThrow();

    @Test
    void anAchievementNeedsATier() {
        assertTrue(Achievement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"t:a","tiers":[]}""")).isError());
    }

    @Test
    void textsComeFromTheIdUnlessGiven() {
        assertEquals("achievement.t.a.b.name", SAMPLE.titleKey());
        assertEquals("achievement.t.a.b.hint", SAMPLE.hintKey());
    }

    @Test
    void aTierIsAQuestOutOfTheStory() {
        ResourceLocation quest = SAMPLE.questId(SAMPLE.tiers().getFirst());

        assertEquals(ResourceLocation.parse("t:_achievements/a/b/bronze"), quest);
        assertTrue(Achievement.isTierQuest(quest));
        assertFalse(Achievement.isTierQuest(ResourceLocation.parse("t:start/first")));
    }

    @Test
    void titlesSurviveSaving() {
        ProgressStore store = new ProgressStore();
        store.addTitle(PLAYER, "title.a");
        store.setActiveTitle(PLAYER, "title.a");

        ProgressStore loaded = ProgressStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(Set.of("title.a"), loaded.titles(PLAYER));
        assertEquals("title.a", loaded.activeTitle(PLAYER));
    }

    @Test
    void rarityIgnoresRecordsWithNothingCounted() {
        works.repka.rysquests.data.QuestBook book = works.repka.rysquests.data.QuestBook.EMPTY.withAchievements(List.of(SAMPLE));
        ResourceLocation bronze = SAMPLE.questId(SAMPLE.tiers().getFirst());
        Map<UUID, Map<ResourceLocation, QuestProgress>> records = Map.of(
                new UUID(0, 1), Map.of(bronze, new QuestProgress(List.of(10), 5, List.of())),
                new UUID(0, 2), Map.of(bronze, new QuestProgress(List.of(3), 0, List.of())),
                new UUID(0, 3), Map.of(bronze, QuestProgress.EMPTY.withClaimed(List.of(0))));

        Map<String, Integer> rarity = AchievementService.computeRarity(book, records);

        assertEquals(50, rarity.get(bronze.toString()));
        assertEquals(Optional.of(0), Optional.ofNullable(rarity.get(SAMPLE.questId(SAMPLE.tiers().getLast()).toString())));
    }
}
