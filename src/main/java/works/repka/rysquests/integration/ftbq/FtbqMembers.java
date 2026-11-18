package works.repka.rysquests.integration.ftbq;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.migrate.Snbt;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.migrate.MigrationEngine;
import works.repka.rysquests.progress.QuestProgress;

/**
 * Who saw which FTB progress, and where that progress belongs now (ADR-65, review of 18.11). FTB Quests keeps progress by FTB team;
 * a player may sit in another team here (they were in a team of the team mod before the move, or there is no team mod and they
 * play alone). Their FTB team's progress then also goes to the team they actually play in, and the report says so.
 *
 * <p>The FTB team files ({@code world/ftbteams/party/*.snbt}, ranks {@code owner}, {@code officer}, {@code member}) are read with
 * the family's reader. A player's own FTB file counts for them only while they were in no party: FTB hid it from them otherwise.
 */
final class FtbqMembers {
    private FtbqMembers() {
    }

    /** One player whose FTB team's progress also went to the team they play in now. */
    record Moved(UUID source, UUID player, UUID target) {
    }

    /**
     * FTB parties and their players.
     *
     * @param broken given the name and the reason of a party file that could not be read
     */
    static Map<UUID, Set<UUID>> parties(@Nullable Path world, BiConsumer<String, String> broken) {
        Map<UUID, Set<UUID>> out = new LinkedHashMap<>();
        if (world == null) {
            return out;
        }
        Path dir = world.resolve("ftbteams").resolve("party");
        if (!Files.isDirectory(dir)) {
            return out;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(p -> p.getFileName().toString().endsWith(".snbt")).sorted().toList();
        } catch (IOException e) {
            broken.accept(dir.getFileName().toString(), String.valueOf(e.getMessage()));
            return out;
        }
        for (Path file : files) {
            try {
                JsonObject root = Snbt.readObject(file);
                UUID id = Snbt.uuid(root.get("id"));
                if (id == null || !(root.get("ranks") instanceof JsonObject ranks)) {
                    continue;
                }
                Set<UUID> members = new LinkedHashSet<>();
                for (Map.Entry<String, JsonElement> e : ranks.entrySet()) {
                    String rank = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : "";
                    UUID player = Snbt.uuid(new JsonPrimitive(e.getKey()));
                    if (player != null && (rank.equals("owner") || rank.equals("officer") || rank.equals("member"))) {
                        members.add(player);
                    }
                }
                out.put(id, members);
            } catch (IOException | RuntimeException e) {
                broken.accept(file.getFileName().toString(), String.valueOf(e.getMessage()));
            }
        }
        return out;
    }

    /** The player's name from their FTB Teams file, else the start of the id. */
    static String name(@Nullable Path world, UUID player) {
        if (world != null) {
            Path file = world.resolve("ftbteams").resolve("player").resolve(player + ".snbt");
            if (Files.isRegularFile(file)) {
                try {
                    String name = Snbt.string(Snbt.readObject(file), "player_name", "");
                    if (!name.isBlank()) {
                        return name;
                    }
                } catch (IOException | RuntimeException ignored) {
                    // the start of the id
                }
            }
        }
        return player.toString().substring(0, 8);
    }

    /**
     * The FTB progress with each team's records also folded into the teams its players play in now. A party's records go as they
     * are; a lone player's own records go without the rewards they took for themselves (those stay in their own record, as here).
     * Where two sources give the same quest to a team, the better record wins ({@link MigrationEngine#best}). The FTB team keeps
     * its own records too, so nothing is lost if its team comes later.
     *
     * @param teamOf where a player's progress lives now
     * @param moved  filled with the players whose FTB team's progress also went elsewhere
     */
    static Map<UUID, Map<ResourceLocation, QuestProgress>> redirect(Map<UUID, Map<ResourceLocation, QuestProgress>> ftb, Map<UUID, Set<UUID>> parties,
            Function<UUID, UUID> teamOf, QuestBook book, List<Moved> moved) {
        Map<UUID, Map<ResourceLocation, QuestProgress>> out = new HashMap<>();
        ftb.forEach((team, quests) -> out.put(team, new HashMap<>(quests)));
        if (parties.isEmpty()) {
            return out;
        }
        Set<UUID> inParty = new HashSet<>();
        parties.values().forEach(inParty::addAll);
        for (Map.Entry<UUID, Map<ResourceLocation, QuestProgress>> source : new TreeMap<>(ftb).entrySet()) {
            UUID key = source.getKey();
            boolean party = parties.containsKey(key);
            Set<UUID> viewers = party ? parties.get(key) : inParty.contains(key) ? Set.of() : Set.of(key);
            Set<UUID> targets = new LinkedHashSet<>();
            for (UUID player : viewers) {
                UUID target = teamOf.apply(player);
                if (target == null || target.equals(key)) {
                    continue;
                }
                moved.add(new Moved(key, player, target));
                targets.add(target);
            }
            for (UUID target : targets) {
                Map<ResourceLocation, QuestProgress> into = out.computeIfAbsent(target, id -> new HashMap<>());
                source.getValue().forEach((quest, progress) -> {
                    QuestProgress shared = party ? progress : teamPart(book, quest, progress);
                    into.merge(quest, shared, MigrationEngine::best);
                });
            }
        }
        return out;
    }

    /** A lone player's record without the rewards that were only theirs. */
    private static QuestProgress teamPart(QuestBook book, ResourceLocation quest, QuestProgress progress) {
        List<Integer> claimed = new ArrayList<>();
        for (int reward : progress.claimed()) {
            if (FtbqProgress.sharedByTeam(book, quest, reward)) {
                claimed.add(reward);
            }
        }
        return claimed.size() == progress.claimed().size() ? progress : progress.withClaimed(List.copyOf(claimed));
    }
}
