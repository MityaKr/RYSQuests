package works.repka.rysquests.progress;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.migrate.MigrationEngine;

/** Quest progress of all teams, saved with the world. */
public final class ProgressStore extends SavedData {
    private static final String NAME = "rysquests_progress";
    private static final String TEAMS = "teams";
    private static final String ARCHIVE = "archive";
    private static final String PINNED = "pinned";
    private static final String MEMBERS = "members";
    private static final String APPLIED = "applied_migrations";
    private static final String GUIDE_PAGES = "guide_pages";
    private static final UUID SERVER_TEAM = new UUID(0, 0);

    private static final Codec<Map<UUID, Map<ResourceLocation, QuestProgress>>> CODEC = Codec.unboundedMap(
            UUIDUtil.STRING_CODEC, Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC));
    private static final Codec<Map<UUID, List<ResourceLocation>>> PINNED_CODEC = Codec.unboundedMap(
            UUIDUtil.STRING_CODEC, ResourceLocation.CODEC.listOf());
    private static final Codec<Map<UUID, UUID>> MEMBERS_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC);
    private static final Codec<Map<UUID, List<String>>> GUIDE_PAGES_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf());
    private static final Factory<ProgressStore> FACTORY = new Factory<>(ProgressStore::new, ProgressStore::load);

    private final Map<UUID, Map<ResourceLocation, QuestProgress>> teams = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, QuestProgress>> archive = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> pinned = new HashMap<>();
    private final Map<UUID, UUID> members = new HashMap<>();
    private final List<String> applied = new ArrayList<>();
    private final Map<UUID, Set<String>> guidePages = new HashMap<>();

    public static ProgressStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    static ProgressStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressStore store = new ProgressStore();
        CODEC.parse(NbtOps.INSTANCE, tag.get(TEAMS)).result().ifPresent(saved ->
                saved.forEach((team, quests) -> store.teams.put(team, new HashMap<>(quests))));
        if (tag.contains(ARCHIVE)) {
            CODEC.parse(NbtOps.INSTANCE, tag.get(ARCHIVE)).result().ifPresent(saved ->
                    saved.forEach((team, quests) -> store.archive.put(team, new HashMap<>(quests))));
        }
        if (tag.contains(PINNED)) {
            PINNED_CODEC.parse(NbtOps.INSTANCE, tag.get(PINNED)).result().ifPresent(store.pinned::putAll);
        }
        if (tag.contains(MEMBERS)) {
            MEMBERS_CODEC.parse(NbtOps.INSTANCE, tag.get(MEMBERS)).result().ifPresent(store.members::putAll);
        }
        if (tag.contains(APPLIED)) {
            Codec.STRING.listOf().parse(NbtOps.INSTANCE, tag.get(APPLIED)).result().ifPresent(store.applied::addAll);
        }
        if (tag.contains(GUIDE_PAGES)) {
            GUIDE_PAGES_CODEC.parse(NbtOps.INSTANCE, tag.get(GUIDE_PAGES)).result().ifPresent(saved ->
                    saved.forEach((player, pages) -> store.guidePages.put(player, new HashSet<>(pages))));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TEAMS, CODEC.encodeStart(NbtOps.INSTANCE, teams).getOrThrow());
        tag.put(ARCHIVE, CODEC.encodeStart(NbtOps.INSTANCE, archive).getOrThrow());
        tag.put(PINNED, PINNED_CODEC.encodeStart(NbtOps.INSTANCE, pinned).getOrThrow());
        tag.put(MEMBERS, MEMBERS_CODEC.encodeStart(NbtOps.INSTANCE, members).getOrThrow());
        tag.put(APPLIED, Codec.STRING.listOf().encodeStart(NbtOps.INSTANCE, applied).getOrThrow());
        Map<UUID, List<String>> pages = new HashMap<>();
        guidePages.forEach((player, read) -> pages.put(player, read.stream().sorted().toList()));
        tag.put(GUIDE_PAGES, GUIDE_PAGES_CODEC.encodeStart(NbtOps.INSTANCE, pages).getOrThrow());
        return tag;
    }

    public QuestProgress get(UUID team, ResourceLocation quest) {
        return team(team).getOrDefault(quest, QuestProgress.EMPTY);
    }

    public Map<ResourceLocation, QuestProgress> team(UUID team) {
        return teams.computeIfAbsent(team, key -> new HashMap<>());
    }

    public void put(UUID team, Map<ResourceLocation, QuestProgress> changes) {
        team(team).putAll(changes);
        setDirty();
    }

    public void replaceTeam(UUID team, Map<ResourceLocation, QuestProgress> quests) {
        teams.put(team, new HashMap<>(quests));
        setDirty();
    }

    public List<ResourceLocation> pinned(UUID player) {
        return pinned.getOrDefault(player, List.of());
    }

    public void setPinned(UUID player, List<ResourceLocation> quests) {
        pinned.put(player, List.copyOf(quests));
        setDirty();
    }

    /** The guide pages a player has read. Reading is personal, also in a team. */
    public Set<String> guidePages(UUID player) {
        return guidePages.getOrDefault(player, Set.of());
    }

    /** @return whether the page was not read before */
    public boolean markGuidePage(UUID player, String key) {
        boolean added = guidePages.computeIfAbsent(player, id -> new HashSet<>()).add(key);
        if (added) {
            setDirty();
        }
        return added;
    }

    /** Whose progress a player works on, under the book's progress mode. */
    public UUID teamOf(UUID player, ProgressMode mode) {
        return switch (mode) {
            case SOLO -> player;
            case SERVER -> SERVER_TEAM;
            case TEAM -> {
                // A team mod, when installed, decides. Our own commands only apply without one.
                UUID shared = TeamsApi.get().teamOf(player);
                yield shared.equals(player) ? members.getOrDefault(player, player) : shared;
            }
        };
    }

    /** Folds one set of progress into another, keeping the better record of each quest. */
    public void mergeInto(UUID from, UUID into) {
        Map<ResourceLocation, QuestProgress> target = team(into);
        team(from).forEach((quest, progress) -> target.merge(quest, progress, MigrationEngine::best));
        setDirty();
    }

    public void copyTeam(UUID from, UUID to) {
        teams.put(to, new HashMap<>(team(from)));
        setDirty();
    }

    /** Players in a team made with our own commands. */
    public int memberCount(UUID team) {
        return (int) members.values().stream().filter(team::equals).count();
    }

    public void setTeam(UUID player, UUID team) {
        members.put(player, team);
        setDirty();
    }

    public void leaveTeam(UUID player) {
        members.remove(player);
        setDirty();
    }

    public MigrationSnapshot snapshot() {
        return new MigrationSnapshot(teams, archive, List.copyOf(applied));
    }

    /** The parts of the store that migrations read and replace. */
    public record MigrationSnapshot(Map<UUID, Map<ResourceLocation, QuestProgress>> teams,
            Map<UUID, Map<ResourceLocation, QuestProgress>> archive, List<String> applied) {
    }

    public void restore(Map<UUID, Map<ResourceLocation, QuestProgress>> newTeams,
            Map<UUID, Map<ResourceLocation, QuestProgress>> newArchive, java.util.Collection<String> newApplied) {
        teams.clear();
        teams.putAll(newTeams);
        archive.clear();
        archive.putAll(newArchive);
        applied.clear();
        applied.addAll(newApplied);
        setDirty();
    }
}
