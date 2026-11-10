package works.repka.rysquests.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.migrate.MigrationEngine;
import works.repka.rysquests.shop.ShopLimits;

/** Quest progress of all teams, saved with the world. */
public final class ProgressStore extends SavedData {
    private static final String NAME = "rysquests_progress";
    private static final String TEAMS = "teams";
    private static final String ARCHIVE = "archive";
    private static final String PINNED = "pinned";
    private static final String MEMBERS = "members";
    private static final String APPLIED = "applied_migrations";
    private static final String GUIDE_PAGES = "guide_pages";
    private static final String PINNED_WAYS = "pinned_ways";
    private static final String WALLETS = "wallets";
    private static final String SHOP_USAGE = "shop_usage";
    private static final String SHOP_HISTORY = "shop_history";
    private static final String TITLES = "titles";
    private static final String ACTIVE_TITLE = "active_title";
    private static final int HISTORY_LINES = 30;
    private static final UUID SERVER_TEAM = new UUID(0, 0);

    private static final Codec<Map<UUID, Map<ResourceLocation, QuestProgress>>> CODEC = Codec.unboundedMap(
            UUIDUtil.STRING_CODEC, Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC));
    private static final Codec<Map<UUID, List<ResourceLocation>>> PINNED_CODEC = Codec.unboundedMap(
            UUIDUtil.STRING_CODEC, ResourceLocation.CODEC.listOf());
    private static final Codec<Map<UUID, UUID>> MEMBERS_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC);
    private static final Codec<Map<UUID, List<String>>> GUIDE_PAGES_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf());
    private static final Codec<Map<UUID, Map<String, Long>>> WALLETS_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, Codec.LONG));
    private static final Codec<Map<UUID, Map<String, ShopLimits.Usage>>> USAGE_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, ShopLimits.Usage.CODEC));
    private static final Codec<Map<UUID, List<ShopLine>>> HISTORY_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, ShopLine.CODEC.listOf());
    private static final Factory<ProgressStore> FACTORY = new Factory<>(ProgressStore::new, ProgressStore::load);

    private final Map<UUID, Map<ResourceLocation, QuestProgress>> teams = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, QuestProgress>> archive = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> pinned = new HashMap<>();
    private final Map<UUID, UUID> members = new HashMap<>();
    private final List<String> applied = new ArrayList<>();
    private final Map<UUID, Set<String>> guidePages = new HashMap<>();
    private final Map<UUID, List<String>> pinnedWays = new HashMap<>();
    private final Map<UUID, Map<String, Long>> wallets = new HashMap<>();
    private final Map<UUID, Map<String, ShopLimits.Usage>> shopUsage = new HashMap<>();
    private final Map<UUID, List<ShopLine>> shopHistory = new HashMap<>();
    private final Map<UUID, Set<String>> titles = new HashMap<>();
    private final Map<UUID, String> activeTitles = new HashMap<>();

    /** One operation with money. @param ref the offer or the quest behind it @param times how many trades, 0 for a reward */
    public record ShopLine(long time, long delta, String currency, String ref, int times) {
        public static final String TRANSFER = "rysquests:transfer";
        public static final String EXCHANGE = "rysquests:exchange";

        public static final Codec<ShopLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("time").forGetter(ShopLine::time),
                Codec.LONG.fieldOf("delta").forGetter(ShopLine::delta),
                Codec.STRING.fieldOf("currency").forGetter(ShopLine::currency),
                Codec.STRING.fieldOf("ref").forGetter(ShopLine::ref),
                Codec.INT.fieldOf("times").forGetter(ShopLine::times)
        ).apply(instance, ShopLine::new));
    }

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
        if (tag.contains(PINNED_WAYS)) {
            GUIDE_PAGES_CODEC.parse(NbtOps.INSTANCE, tag.get(PINNED_WAYS)).result().ifPresent(store.pinnedWays::putAll);
        }
        if (tag.contains(TITLES)) {
            GUIDE_PAGES_CODEC.parse(NbtOps.INSTANCE, tag.get(TITLES)).result().ifPresent(saved -> saved.forEach((player, owned) -> store.titles.put(player, new HashSet<>(owned))));
        }
        if (tag.contains(ACTIVE_TITLE)) {
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).parse(NbtOps.INSTANCE, tag.get(ACTIVE_TITLE)).result().ifPresent(store.activeTitles::putAll);
        }
        if (tag.contains(WALLETS)) {
            WALLETS_CODEC.parse(NbtOps.INSTANCE, tag.get(WALLETS)).result().ifPresent(saved -> saved.forEach((owner, money) -> store.wallets.put(owner, new HashMap<>(money))));
        }
        if (tag.contains(SHOP_USAGE)) {
            USAGE_CODEC.parse(NbtOps.INSTANCE, tag.get(SHOP_USAGE)).result().ifPresent(saved -> saved.forEach((owner, usage) -> store.shopUsage.put(owner, new HashMap<>(usage))));
        }
        if (tag.contains(SHOP_HISTORY)) {
            HISTORY_CODEC.parse(NbtOps.INSTANCE, tag.get(SHOP_HISTORY)).result().ifPresent(saved -> saved.forEach((owner, lines) -> store.shopHistory.put(owner, new ArrayList<>(lines))));
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
        tag.put(PINNED_WAYS, GUIDE_PAGES_CODEC.encodeStart(NbtOps.INSTANCE, pinnedWays).getOrThrow());
        Map<UUID, List<String>> owned = new HashMap<>();
        titles.forEach((player, names) -> owned.put(player, names.stream().sorted().toList()));
        tag.put(TITLES, GUIDE_PAGES_CODEC.encodeStart(NbtOps.INSTANCE, owned).getOrThrow());
        tag.put(ACTIVE_TITLE, Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).encodeStart(NbtOps.INSTANCE, activeTitles).getOrThrow());
        tag.put(WALLETS, WALLETS_CODEC.encodeStart(NbtOps.INSTANCE, wallets).getOrThrow());
        tag.put(SHOP_USAGE, USAGE_CODEC.encodeStart(NbtOps.INSTANCE, shopUsage).getOrThrow());
        tag.put(SHOP_HISTORY, HISTORY_CODEC.encodeStart(NbtOps.INSTANCE, shopHistory).getOrThrow());
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

    public Set<String> titles(UUID player) {
        return titles.getOrDefault(player, Set.of());
    }

    public void addTitle(UUID player, String title) {
        titles.computeIfAbsent(player, id -> new HashSet<>()).add(title);
        setDirty();
    }

    /** A title taken away stops being worn. */
    public void removeTitle(UUID player, String title) {
        Set<String> owned = titles.get(player);
        if (owned != null && owned.remove(title)) {
            if (title.equals(activeTitles.get(player))) {
                activeTitles.remove(player);
            }
            setDirty();
        }
    }

    public String activeTitle(UUID player) {
        return activeTitles.getOrDefault(player, "");
    }

    public void setActiveTitle(UUID player, String title) {
        if (title.isEmpty()) {
            activeTitles.remove(player);
        } else {
            activeTitles.put(player, title);
        }
        setDirty();
    }

    /** Every saved record of progress, for counting how rare something is. */
    public Map<UUID, Map<ResourceLocation, QuestProgress>> allProgress() {
        return teams;
    }

    public Map<String, Long> balances(UUID owner) {
        return wallets.getOrDefault(owner, Map.of());
    }

    public long balance(UUID owner, String currency) {
        return balances(owner).getOrDefault(currency, 0L);
    }

    /** A balance never goes below zero. */
    public void addBalance(UUID owner, String currency, long delta) {
        wallets.computeIfAbsent(owner, id -> new HashMap<>()).merge(currency, delta, (old, change) -> Math.max(0, old + change));
        setDirty();
    }

    public Map<String, ShopLimits.Usage> shopUsage(UUID owner) {
        return shopUsage.getOrDefault(owner, Map.of());
    }

    /** @param offer one trade, or null for all of them */
    public void clearShopUsage(UUID owner, @Nullable String offer) {
        Map<String, ShopLimits.Usage> used = shopUsage.get(owner);
        if (used != null) {
            if (offer == null) {
                used.clear();
            } else {
                used.remove(offer);
            }
            setDirty();
        }
    }

    public void setShopUsage(UUID owner, String offer, ShopLimits.Usage usage) {
        shopUsage.computeIfAbsent(owner, id -> new HashMap<>()).put(offer, usage);
        setDirty();
    }

    /** The latest first. */
    public List<ShopLine> shopHistory(UUID player) {
        return shopHistory.getOrDefault(player, List.of());
    }

    public void addShopHistory(UUID player, ShopLine line) {
        List<ShopLine> lines = shopHistory.computeIfAbsent(player, id -> new ArrayList<>());
        lines.addFirst(line);
        if (lines.size() > HISTORY_LINES) {
            lines.removeLast();
        }
        setDirty();
    }

    /** The ways of matrix sections a player keeps on the screen. */
    public List<String> pinnedWays(UUID player) {
        return pinnedWays.getOrDefault(player, List.of());
    }

    public void setPinnedWays(UUID player, List<String> ways) {
        pinnedWays.put(player, List.copyOf(ways));
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
