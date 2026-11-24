package works.repka.rysquests.integration.ftbq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Layout;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Ids;
import works.repka.rysquests.integration.ftbq.FtbqImporter.Ref;
import works.repka.rysquests.migrate.Migration;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.reward.ItemReward;

/** FTB keeps a party's claims as {@code "<player>:<reward>"}: one player taking a reward says nothing about another. */
class FtbqProgressTest {
    private static final String PARTY_HEX = "0c383da85fb541ef96be7cae45e6e640";
    private static final String FIRST_HEX = "216ea99ad6eb45be8a5b3da7852588a5";
    private static final String SECOND_HEX = "633d6f3ff3184004a3fbb5131b22c28b";
    private static final UUID PARTY = FtbqProgress.parseUuid(PARTY_HEX);
    private static final UUID FIRST = FtbqProgress.parseUuid(FIRST_HEX);
    private static final UUID SECOND = FtbqProgress.parseUuid(SECOND_HEX);
    private static final ResourceLocation QUEST = ResourceLocation.parse("t:q");
    private static final String QUEST_HEX = "AAAAAAAAAAAAAAAA";
    private static final String EACH_HEX = "BBBBBBBBBBBBBBBB";
    private static final String ONCE_HEX = "CCCCCCCCCCCCCCCC";
    private static final Ids IDS = new Ids(Map.of(QUEST_HEX, QUEST.toString()), Map.of(), Map.of(EACH_HEX, new Ref(QUEST.toString(), 0), ONCE_HEX, new Ref(QUEST.toString(), 1)));

    private static RewardDef reward(TeamMode mode) {
        return new ItemReward(ResourceLocation.parse("minecraft:bread"), 1, Optional.empty(), mode);
    }

    private static QuestBook book() {
        Quest quest = new Quest(QUEST, Optional.empty(), Optional.empty(), "t", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), RequiresMode.ALL, Optional.empty(),
                List.of(), List.of(reward(TeamMode.EACH), reward(TeamMode.ONCE)), Optional.empty(), new QuestOptions(Layout.DEFAULT, 1, false, false, Optional.empty(), false, List.of()));
        return QuestBook.create(BookMeta.DEFAULT, List.of(), List.of(quest), List.of(), List.of(), "");
    }

    private static void file(Path dir, String owner, String claims) throws IOException {
        Files.writeString(dir.resolve(owner + ".snbt"), """
                {
                \tversion: 1
                \tuuid: "%s"
                \tcompleted: {
                \t\t%s: 1793285774000L
                \t}
                \tclaimed_rewards: {
                %s
                \t}
                }
                """.formatted(owner, QUEST_HEX, claims));
    }

    private static String claim(String player, String reward) {
        return "\t\t\"" + player + ":" + reward + "\": 1793285774481L";
    }

    @Test
    void aMemberKeepsTheirOwnClaim(@TempDir Path dir) throws IOException {
        file(dir, PARTY_HEX, claim(FIRST_HEX, EACH_HEX));

        Map<UUID, Map<ResourceLocation, QuestProgress>> read = FtbqProgress.read(dir, IDS, book());

        assertEquals(List.of(0), read.get(FIRST).get(QUEST).claimed());
        assertFalse(read.containsKey(SECOND), "The other member has taken nothing");
        assertEquals(List.of(), read.get(PARTY).get(QUEST).claimed());
        assertTrue(read.get(PARTY).get(QUEST).completed());
    }

    @Test
    void aRewardForTheWholeTeamStaysWithTheTeam(@TempDir Path dir) throws IOException {
        file(dir, PARTY_HEX, claim(SECOND_HEX, ONCE_HEX) + "\n\t\t" + EACH_HEX + ": 1793285774481L");

        Map<UUID, Map<ResourceLocation, QuestProgress>> read = FtbqProgress.read(dir, IDS, book());

        assertEquals(List.of(1, 0), read.get(PARTY).get(QUEST).claimed());
        assertFalse(read.containsKey(SECOND));
    }

    @Test
    void aPlayerAloneClaimsInTheirOwnRecord(@TempDir Path dir) throws IOException {
        file(dir, FIRST_HEX, claim(FIRST_HEX, EACH_HEX));

        assertEquals(List.of(0), FtbqProgress.read(dir, IDS, book()).get(FIRST).get(QUEST).claimed());
    }

    /** The id table names the quest as the book was; the book renamed it since (RepYS achievements, upgrade simulation 24.11). */
    @Test
    void aQuestTheBookRenamedComesUnderItsNewName(@TempDir Path dir) throws IOException {
        ResourceLocation old = ResourceLocation.parse("t:old");
        Ids table = new Ids(Map.of(QUEST_HEX, old.toString()), Map.of(), Map.of(EACH_HEX, new Ref(old.toString(), 0), ONCE_HEX, new Ref(old.toString(), 1)));
        file(dir, PARTY_HEX, claim(FIRST_HEX, EACH_HEX));

        assertTrue(FtbqProgress.read(dir, table, book()).getOrDefault(PARTY, Map.of()).isEmpty(), "Without the moves the old name is not in the book");

        Ids moved = FtbqProgress.followMoves(table, List.of(new Migration.Rename("r", old, QUEST)));
        Map<UUID, Map<ResourceLocation, QuestProgress>> read = FtbqProgress.read(dir, moved, book());
        assertTrue(read.get(PARTY).get(QUEST).completed());
        assertEquals(List.of(0), read.get(FIRST).get(QUEST).claimed());
    }

    @Test
    void movesAreFollowedToTheirEndAndALoopStops() {
        Ids table = new Ids(Map.of(QUEST_HEX, "t:a", EACH_HEX, "t:x"), Map.of(), Map.of());
        List<Migration> moves = List.of(
                new Migration.Rename("1", ResourceLocation.parse("t:a"), ResourceLocation.parse("t:b")),
                new Migration.Merge("2", List.of(ResourceLocation.parse("t:b"), ResourceLocation.parse("t:c")), ResourceLocation.parse("t:d")),
                new Migration.Remove("3", ResourceLocation.parse("t:d")),
                new Migration.Rename("4", ResourceLocation.parse("t:x"), ResourceLocation.parse("t:y")),
                new Migration.Rename("5", ResourceLocation.parse("t:y"), ResourceLocation.parse("t:x")));

        Ids moved = FtbqProgress.followMoves(table, moves);

        assertEquals("t:d", moved.quests().get(QUEST_HEX), "rename, then merge; a remove is left to the book");
        assertTrue(Set.of("t:x", "t:y").contains(moved.quests().get(EACH_HEX)), "a loop ends without hanging");
        assertEquals(table, FtbqProgress.followMoves(table, List.of()));
    }

    @Test
    void aClaimJoinsWhatThePlayersOwnFileSays(@TempDir Path dir) throws IOException {
        file(dir, PARTY_HEX, claim(SECOND_HEX, EACH_HEX));
        file(dir, SECOND_HEX, "");

        QuestProgress own = FtbqProgress.read(dir, IDS, book()).get(SECOND).get(QUEST);

        assertTrue(own.completed(), "The personal record stays");
        assertEquals(List.of(0), own.claimed());
    }
}
