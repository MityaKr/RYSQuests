package works.repka.rysquests.progress;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Quest progress of all teams, saved with the world. */
public final class ProgressStore extends SavedData {
    private static final String NAME = "rysquests_progress";
    private static final String TEAMS = "teams";
    private static final Codec<Map<UUID, Map<ResourceLocation, QuestProgress>>> CODEC = Codec.unboundedMap(
            UUIDUtil.STRING_CODEC, Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC));
    private static final String PINNED = "pinned";
    private static final Codec<Map<UUID, List<ResourceLocation>>> PINNED_CODEC = Codec.unboundedMap(
            UUIDUtil.STRING_CODEC, ResourceLocation.CODEC.listOf());
    private static final Factory<ProgressStore> FACTORY = new Factory<>(ProgressStore::new, ProgressStore::load);

    private final Map<UUID, Map<ResourceLocation, QuestProgress>> teams = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> pinned = new HashMap<>();

    public List<ResourceLocation> pinned(UUID player) {
        return pinned.getOrDefault(player, List.of());
    }

    public void setPinned(UUID player, List<ResourceLocation> quests) {
        pinned.put(player, List.copyOf(quests));
        setDirty();
    }

    public static ProgressStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static ProgressStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressStore store = new ProgressStore();
        CODEC.parse(NbtOps.INSTANCE, tag.get(TEAMS)).result().ifPresent(saved ->
                saved.forEach((team, quests) -> store.teams.put(team, new HashMap<>(quests))));
        if (tag.contains(PINNED)) {
            PINNED_CODEC.parse(NbtOps.INSTANCE, tag.get(PINNED)).result().ifPresent(store.pinned::putAll);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TEAMS, CODEC.encodeStart(NbtOps.INSTANCE, teams).getOrThrow());
        tag.put(PINNED, PINNED_CODEC.encodeStart(NbtOps.INSTANCE, pinned).getOrThrow());
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
}
