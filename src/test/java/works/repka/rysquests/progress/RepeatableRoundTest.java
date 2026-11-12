package works.repka.rysquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.rysquests.data.Layout;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;

/** Repeatable quests between rounds (ADR-46): what they opened stays open, and a new round clears every member. */
class RepeatableRoundTest {
    private static final ResourceLocation REPEATABLE = ResourceLocation.parse("t:repeatable");

    private static Quest dependent() {
        return new Quest(ResourceLocation.parse("t:after"), Optional.empty(), Optional.empty(), "t", Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(REPEATABLE), RequiresMode.ALL, Optional.empty(), List.of(), List.of(), Optional.empty(),
                new QuestOptions(Layout.DEFAULT, 1, false, false, Optional.empty(), false, List.of()));
    }

    @Test
    void aDependentStaysOpenBetweenRounds() {
        QuestProgress cooling = new QuestProgress(List.of(), 0, List.of(), System.currentTimeMillis() + 60_000, 1);
        QuestProgress nextRound = new QuestProgress(List.of(), 0, List.of(), 0, 3);

        assertTrue(QuestLogic.isUnlocked(dependent(), id -> cooling));
        assertTrue(QuestLogic.isUnlocked(dependent(), id -> nextRound));
        assertFalse(QuestLogic.isUnlocked(dependent(), id -> QuestProgress.EMPTY));
    }

    @Test
    void savesFromBeforeTheCountStillOpenWhenTheyHadACooldown() {
        QuestProgress legacy = new QuestProgress(List.of(), 0, List.of(), 1L);

        assertTrue(legacy.everDone());
        assertTrue(QuestLogic.isUnlocked(dependent(), id -> legacy));
    }

    @Test
    void theRoundCountIsSavedOnlyWhenThereIsOne() {
        JsonObject plain = QuestProgress.CODEC.encodeStart(JsonOps.INSTANCE, new QuestProgress(List.of(1), 5, List.of())).getOrThrow().getAsJsonObject();
        JsonObject counted = QuestProgress.CODEC.encodeStart(JsonOps.INSTANCE, new QuestProgress(List.of(), 0, List.of(), 0, 2)).getOrThrow().getAsJsonObject();

        assertFalse(plain.has("times"));
        assertEquals(2, QuestProgress.CODEC.parse(JsonOps.INSTANCE, counted).getOrThrow().times());
        JsonObject old = new JsonObject();
        old.addProperty("completed", 5L);
        assertEquals(0, QuestProgress.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow().times());
    }

    @Test
    void aNewRoundClearsEveryMemberButNeverTheTeamRow() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID server = new UUID(0, 0);
        // Our own teams use the owner's id as the team's.
        Map<UUID, UUID> ownTeams = Map.of(owner, owner, member, owner, stranger, stranger);
        assertEquals(List.of(member), TeamRewards.rowsToReset(List.of(owner, member, stranger), owner, ownTeams::get));
        // Server-wide progress: every player row belongs to the one team.
        assertEquals(2, TeamRewards.rowsToReset(List.of(server, owner, member), server, row -> server).size());
        // Solo: no row belongs to someone else.
        assertEquals(List.of(), TeamRewards.rowsToReset(List.of(owner, member), owner, row -> row));
    }
}
