package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Layout;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.integration.ftbq.FtbqProgressAccess;

class QuestLogicTest {
    private static final ResourceLocation A = ResourceLocation.parse("t:a");
    private static final ResourceLocation B = ResourceLocation.parse("t:b");
    private static final QuestProgress DONE = new QuestProgress(List.of(1), 5, List.of());

    private static Quest quest(RequiresMode mode, List<RewardDef> rewards, ResourceLocation... requires) {
        return new Quest(ResourceLocation.parse("t:q"), Optional.empty(), Optional.empty(), "t", Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(requires), mode, Optional.empty(), List.of(), rewards,
                new QuestOptions(Layout.DEFAULT, 1, false, false, Optional.empty(), false, List.of()));
    }

    private static Function<ResourceLocation, QuestProgress> progress(Map<ResourceLocation, QuestProgress> map) {
        return id -> map.getOrDefault(id, QuestProgress.EMPTY);
    }

    @Test
    void allModeNeedsEveryDependency() {
        Quest quest = quest(RequiresMode.ALL, List.of(), A, B);

        assertFalse(QuestLogic.isUnlocked(quest, progress(Map.of(A, DONE))));
        assertTrue(QuestLogic.isUnlocked(quest, progress(Map.of(A, DONE, B, DONE))));
    }

    @Test
    void anyAndAtLeastModes() {
        assertTrue(QuestLogic.isUnlocked(quest(RequiresMode.ANY, List.of(), A, B), progress(Map.of(B, DONE))));
        assertFalse(QuestLogic.isUnlocked(quest(new RequiresMode.AtLeast(2), List.of(), A, B), progress(Map.of(B, DONE))));
    }

    @Test
    void statusFollowsProgress() {
        Quest quest = quest(RequiresMode.ALL, List.of(new works.repka.rysquests.reward.XpReward(Optional.of(1), Optional.empty())));
        Map<ResourceLocation, QuestProgress> map = new HashMap<>();

        assertEquals(QuestStatus.AVAILABLE, QuestLogic.status(quest, progress(map)));
        map.put(quest.id(), new QuestProgress(List.of(1), 0, List.of()));
        assertEquals(QuestStatus.IN_PROGRESS, QuestLogic.status(quest, progress(map)));
        map.put(quest.id(), new QuestProgress(List.of(1), 9, List.of()));
        assertEquals(QuestStatus.COMPLETED, QuestLogic.status(quest, progress(map)));
        map.put(quest.id(), new QuestProgress(List.of(1), 9, List.of(0)));
        assertEquals(QuestStatus.CLAIMED, QuestLogic.status(quest, progress(map)));
    }

    @Test
    void progressRoundTrips() {
        QuestProgress original = new QuestProgress(List.of(3, 1), 77, List.of(0));

        assertEquals(original, QuestProgress.CODEC.parse(JsonOps.INSTANCE, QuestProgress.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow()).getOrThrow());
    }

    @Test
    void ftbqUuidHasNoDashes() {
        assertEquals("216ea99a-d6eb-45be-8a5b-3da7852588a5", FtbqProgressAccess.uuid("216ea99ad6eb45be8a5b3da7852588a5").toString());
        assertTrue(new JsonObject().isEmpty());
    }
}
