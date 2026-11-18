package works.repka.rysquests.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.LocalDate;
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
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.migrate.MigrationEngine;
import works.repka.rysquests.shop.ShopLimits;

/** Quest progress of all teams, saved with the world. */
public final class ProgressStore extends SavedData {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ProgressStore.class);
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
    private static final String COUNTED_USAGE = "counted_usage";
    private static final String SHOP_HISTORY = "shop_history";
    private static final String TITLES = "titles";
    private static final String ACTIVE_TITLE = "active_title";
    private static final String LAST_CHAPTER = "last_chapter";
    private static final String OWED_ROUNDS = "owed_rounds";
    private static final String DELIVERIES = "deliveries";
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
    private static final Codec<Map<UUID, ResourceLocation>> LAST_CHAPTER_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, ResourceLocation.CODEC);
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
    /**
     * Per player: how much of their own use of team limits a team already counts. What they used beyond it, alone between two teams,
     * is added to the team they join; see {@link #foldTeamLimits}.
     */
    private final Map<UUID, Map<String, ShopLimits.Usage>> countedUsage = new HashMap<>();
    private final Map<UUID, List<ShopLine>> shopHistory = new HashMap<>();
    /**
     * Per player, not saved: the lines of the history that still sum up what their shop blocks do, by what they sum up (shop plan,
     * section 4.6). Any other operation closes them; a restart starts new ones.
     */
    private final Map<UUID, Map<String, ShopLine>> openSummaries = new HashMap<>();
    private final Map<UUID, Set<String>> titles = new HashMap<>();
    private final Map<UUID, String> activeTitles = new HashMap<>();
    /** Per player, not per team: the chapter of the quest this player last completed by their own doing. */
    private final Map<UUID, ResourceLocation> lastChapters = new HashMap<>();
    /**
     * Per player: the finished rounds of repeatable quests whose rewards still wait for them, because a teammate started the next
     * round first. See {@link TeamRewards#owedAtNewRound}.
     */
    private final Map<UUID, Map<ResourceLocation, QuestProgress>> owedRounds = new HashMap<>();
    /** What teams, and players alone, have delivered towards trades paid in parts (shop plan, step M4). */
    private final DeliveryLedger deliveries = new DeliveryLedger(this::setDirty);

    /**
     * One operation with money.
     *
     * @param ref the offer or the quest behind it
     * @param times how many trades, 0 for a reward
     * @param treasury the money came from or went to the team's treasury rather than the player's own wallet
     */
    public record ShopLine(long time, long delta, String currency, String ref, int times, boolean treasury) {
        public static final String TRANSFER = "rysquests:transfer";
        public static final String EXCHANGE = "rysquests:exchange";

        public static final Codec<ShopLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("time").forGetter(ShopLine::time),
                Codec.LONG.fieldOf("delta").forGetter(ShopLine::delta),
                Codec.STRING.fieldOf("currency").forGetter(ShopLine::currency),
                Codec.STRING.fieldOf("ref").forGetter(ShopLine::ref),
                Codec.INT.fieldOf("times").forGetter(ShopLine::times),
                // Lines saved before the field are the player's own wallet.
                Codec.BOOL.optionalFieldOf("treasury", false).forGetter(ShopLine::treasury)
        ).apply(instance, ShopLine::new));

        /** A line of the player's own wallet. */
        public ShopLine(long time, long delta, String currency, String ref, int times) {
            this(time, delta, currency, ref, times, false);
        }
    }

    public static ProgressStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    static ProgressStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressStore store = new ProgressStore();
        CODEC.parse(NbtOps.INSTANCE, tag.get(TEAMS)).resultOrPartial(LOGGER::error).ifPresent(saved ->
                saved.forEach((team, quests) -> store.teams.put(team, new HashMap<>(quests))));
        if (tag.contains(ARCHIVE)) {
            CODEC.parse(NbtOps.INSTANCE, tag.get(ARCHIVE)).resultOrPartial(LOGGER::error).ifPresent(saved ->
                    saved.forEach((team, quests) -> store.archive.put(team, new HashMap<>(quests))));
        }
        if (tag.contains(PINNED)) {
            PINNED_CODEC.parse(NbtOps.INSTANCE, tag.get(PINNED)).resultOrPartial(LOGGER::error).ifPresent(store.pinned::putAll);
        }
        if (tag.contains(MEMBERS)) {
            MEMBERS_CODEC.parse(NbtOps.INSTANCE, tag.get(MEMBERS)).resultOrPartial(LOGGER::error).ifPresent(store.members::putAll);
        }
        if (tag.contains(APPLIED)) {
            Codec.STRING.listOf().parse(NbtOps.INSTANCE, tag.get(APPLIED)).resultOrPartial(LOGGER::error).ifPresent(store.applied::addAll);
        }
        if (tag.contains(GUIDE_PAGES)) {
            GUIDE_PAGES_CODEC.parse(NbtOps.INSTANCE, tag.get(GUIDE_PAGES)).resultOrPartial(LOGGER::error).ifPresent(saved ->
                    saved.forEach((player, pages) -> store.guidePages.put(player, new HashSet<>(pages))));
        }
        if (tag.contains(PINNED_WAYS)) {
            GUIDE_PAGES_CODEC.parse(NbtOps.INSTANCE, tag.get(PINNED_WAYS)).resultOrPartial(LOGGER::error).ifPresent(store.pinnedWays::putAll);
        }
        if (tag.contains(TITLES)) {
            GUIDE_PAGES_CODEC.parse(NbtOps.INSTANCE, tag.get(TITLES)).resultOrPartial(LOGGER::error).ifPresent(saved -> saved.forEach((player, owned) -> store.titles.put(player, new HashSet<>(owned))));
        }
        if (tag.contains(ACTIVE_TITLE)) {
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).parse(NbtOps.INSTANCE, tag.get(ACTIVE_TITLE)).resultOrPartial(LOGGER::error).ifPresent(store.activeTitles::putAll);
        }
        if (tag.contains(WALLETS)) {
            WALLETS_CODEC.parse(NbtOps.INSTANCE, tag.get(WALLETS)).resultOrPartial(LOGGER::error).ifPresent(saved -> saved.forEach((owner, money) -> store.wallets.put(owner, new HashMap<>(money))));
        }
        if (tag.contains(SHOP_USAGE)) {
            USAGE_CODEC.parse(NbtOps.INSTANCE, tag.get(SHOP_USAGE)).resultOrPartial(LOGGER::error).ifPresent(saved -> saved.forEach((owner, usage) -> store.shopUsage.put(owner, new HashMap<>(usage))));
        }
        if (tag.contains(COUNTED_USAGE)) {
            USAGE_CODEC.parse(NbtOps.INSTANCE, tag.get(COUNTED_USAGE)).resultOrPartial(LOGGER::error).ifPresent(saved -> saved.forEach((owner, usage) -> store.countedUsage.put(owner, new HashMap<>(usage))));
        }
        if (tag.contains(SHOP_HISTORY)) {
            HISTORY_CODEC.parse(NbtOps.INSTANCE, tag.get(SHOP_HISTORY)).resultOrPartial(LOGGER::error).ifPresent(saved -> saved.forEach((owner, lines) -> store.shopHistory.put(owner, new ArrayList<>(lines))));
        }
        if (tag.contains(LAST_CHAPTER)) {
            LAST_CHAPTER_CODEC.parse(NbtOps.INSTANCE, tag.get(LAST_CHAPTER)).resultOrPartial(LOGGER::error).ifPresent(store.lastChapters::putAll);
        }
        if (tag.contains(OWED_ROUNDS)) {
            CODEC.parse(NbtOps.INSTANCE, tag.get(OWED_ROUNDS)).resultOrPartial(LOGGER::error).ifPresent(saved ->
                    saved.forEach((player, quests) -> store.owedRounds.put(player, new HashMap<>(quests))));
        }
        if (tag.contains(DELIVERIES)) {
            DeliveryLedger.CODEC.parse(NbtOps.INSTANCE, tag.get(DELIVERIES)).resultOrPartial(LOGGER::error).ifPresent(store.deliveries::load);
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
        tag.put(COUNTED_USAGE, USAGE_CODEC.encodeStart(NbtOps.INSTANCE, countedUsage).getOrThrow());
        tag.put(SHOP_HISTORY, HISTORY_CODEC.encodeStart(NbtOps.INSTANCE, shopHistory).getOrThrow());
        tag.put(LAST_CHAPTER, LAST_CHAPTER_CODEC.encodeStart(NbtOps.INSTANCE, lastChapters).getOrThrow());
        tag.put(OWED_ROUNDS, CODEC.encodeStart(NbtOps.INSTANCE, owedRounds).getOrThrow());
        tag.put(DELIVERIES, DeliveryLedger.CODEC.encodeStart(NbtOps.INSTANCE, deliveries.saved()).getOrThrow());
        return tag;
    }

    /**
     * Writes the store to {@code data/rysquests_progress.dat} now, on this thread, and fails loudly: {@link SavedData#save(java.io.File,
     * HolderLookup.Provider)} hands the write to a worker and only logs a failure. The FTB step needs to know the data is on disk before
     * the family's migration marks it done. The store stays dirty, so the world's own save writes it again.
     */
    public void writeNow(MinecraftServer server) throws java.io.IOException {
        java.nio.file.Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(NAME + ".dat");
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag(), server.registryAccess()));
        net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root);
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        net.minecraft.nbt.NbtIo.writeCompressed(root, tmp);
        java.nio.file.Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        setDirty();
    }

    public java.util.Optional<ResourceLocation> lastChapter(UUID player) {
        return java.util.Optional.ofNullable(lastChapters.get(player));
    }

    /** @return whether it changed */
    public boolean setLastChapter(UUID player, ResourceLocation chapter) {
        if (chapter.equals(lastChapters.put(player, chapter))) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Rewrites one quest's progress in every record that has it: teams, and the players' own records of claims. */
    public void remapQuest(ResourceLocation quest, java.util.function.UnaryOperator<QuestProgress> change) {
        boolean changed = false;
        for (Map<ResourceLocation, QuestProgress> row : teams.values()) {
            QuestProgress before = row.get(quest);
            if (before != null) {
                row.put(quest, change.apply(before));
                changed = true;
            }
        }
        // An owed round is the same kind of record, kept apart only until its rewards are taken.
        for (Map<ResourceLocation, QuestProgress> row : owedRounds.values()) {
            QuestProgress before = row.get(quest);
            if (before != null) {
                row.put(quest, change.apply(before));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /** The finished round of a repeatable quest whose rewards still wait for this player, or null. */
    @Nullable
    public QuestProgress owed(UUID player, ResourceLocation quest) {
        Map<ResourceLocation, QuestProgress> rounds = owedRounds.get(player);
        return rounds == null ? null : rounds.get(quest);
    }

    /** @param round what the player is owed now, or null when nothing is left */
    public void setOwed(UUID player, ResourceLocation quest, @Nullable QuestProgress round) {
        if (round == null) {
            Map<ResourceLocation, QuestProgress> rounds = owedRounds.get(player);
            if (rounds != null && rounds.remove(quest) != null) {
                if (rounds.isEmpty()) {
                    owedRounds.remove(player);
                }
                setDirty();
            }
        } else {
            owedRounds.computeIfAbsent(player, key -> new HashMap<>()).put(quest, round);
            setDirty();
        }
    }

    /** The players of a team who can be owed a round of a repeatable quest. */
    public List<UUID> roundMembers(UUID team, ProgressMode mode) {
        List<UUID> candidates = new ArrayList<>(teams.keySet());
        candidates.addAll(TeamsApi.get().members(team));
        candidates.addAll(members.keySet());
        candidates.remove(SERVER_TEAM);
        return TeamRewards.roundMembers(candidates, team, player -> teamOf(player, mode), team.equals(members.get(team)));
    }

    /**
     * The players who share a team's progress, offline ones too: a team mod's members and those of a team made with our own commands
     * (its id is its owner's), each only while they still work on this team's progress.
     */
    public List<UUID> teamMembers(UUID team, ProgressMode mode) {
        Set<UUID> candidates = new java.util.LinkedHashSet<>(TeamsApi.get().members(team));
        members.forEach((player, own) -> {
            if (own.equals(team)) {
                candidates.add(player);
            }
        });
        candidates.remove(SERVER_TEAM);
        candidates.removeIf(player -> !teamOf(player, mode).equals(team));
        return List.copyOf(candidates);
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

    /** A copy of what an owner used of the limits, to put back with {@link #replaceShopUsage}. */
    public Map<String, ShopLimits.Usage> copyShopUsage(UUID owner) {
        return new HashMap<>(shopUsage(owner));
    }

    public void replaceShopUsage(UUID owner, Map<String, ShopLimits.Usage> usage) {
        shopUsage.put(owner, new HashMap<>(usage));
        setDirty();
    }

    public Map<String, ShopLimits.Usage> countedUsage(UUID player) {
        return countedUsage.getOrDefault(player, Map.of());
    }

    /** Forgets what a team counted of a player's own purchases, when their limits are reset. @param offer one trade, or null for all */
    public void clearCountedUsage(UUID player, @Nullable String offer) {
        Map<String, ShopLimits.Usage> counted = countedUsage.get(player);
        if (counted != null) {
            if (offer == null) {
                counted.clear();
            } else {
                counted.remove(offer);
            }
            setDirty();
        }
    }

    /**
     * A player leaves a team: what the team used of its team limits goes with them, as if they had bought it themselves. Otherwise
     * leaving, buying alone and coming back would buy a trade "once per team" twice. All of it is counted already, so coming back
     * adds nothing.
     *
     * @param limits the trades limited per team, by id
     */
    public void carryTeamLimits(UUID team, UUID player, Map<String, ShopOffer.Limit> limits) {
        Map<String, ShopLimits.Usage> shared = copyShopUsage(team);
        Map<String, ShopLimits.Usage> own = shopUsage.computeIfAbsent(player, id -> new HashMap<>());
        Map<String, ShopLimits.Usage> counted = countedUsage.computeIfAbsent(player, id -> new HashMap<>());
        for (String offer : limits.keySet()) {
            ShopLimits.Usage usage = shared.get(offer);
            if (usage == null) {
                own.remove(offer);
                counted.remove(offer);
            } else {
                own.put(offer, usage);
                counted.put(offer, usage);
            }
        }
        setDirty();
    }

    /**
     * A player joins a team: what they used of team limits on their own, beyond what a team already counts, is added to the team's.
     * Joining twice in a row adds it once.
     */
    public void foldTeamLimits(UUID player, UUID team, Map<String, ShopOffer.Limit> limits, LocalDate today) {
        Map<String, ShopLimits.Usage> own = shopUsage(player);
        Map<String, ShopLimits.Usage> counted = countedUsage.computeIfAbsent(player, id -> new HashMap<>());
        for (Map.Entry<String, ShopOffer.Limit> entry : limits.entrySet()) {
            String offer = entry.getKey();
            ShopLimits.Usage mine = own.get(offer);
            int extra = team.equals(player) ? 0 : ShopLimits.unCounted(entry.getValue(), mine, counted.get(offer), today);
            if (extra > 0) {
                setShopUsage(team, offer, ShopLimits.after(entry.getValue(), shopUsage(team).get(offer), extra, today));
            }
            if (mine == null) {
                counted.remove(offer);
            } else {
                counted.put(offer, mine);
            }
        }
        setDirty();
    }

    /** Two teams become one: each bought on its own, so what both used of a team limit adds up. */
    public void addTeamLimits(UUID from, UUID into, Map<String, ShopOffer.Limit> limits, LocalDate today) {
        if (from.equals(into)) {
            return;
        }
        Map<String, ShopLimits.Usage> gone = shopUsage(from);
        for (Map.Entry<String, ShopOffer.Limit> entry : limits.entrySet()) {
            int used = ShopLimits.used(entry.getValue(), gone.get(entry.getKey()), today);
            if (used > 0) {
                setShopUsage(into, entry.getKey(), ShopLimits.after(entry.getValue(), shopUsage(into).get(entry.getKey()), used, today));
            }
        }
    }

    /** What has been delivered towards trades paid in parts, by owner, goal and item. */
    public DeliveryLedger deliveries() {
        return deliveries;
    }

    /**
     * A player joins a team: what they delivered alone moves into the team's count and leaves theirs, so joining twice adds it once.
     * Leaving takes nothing along, whatever the team's rule on progress: the count stays with the team.
     */
    public void foldDeliveries(UUID player, UUID team) {
        deliveries.move(player, team);
    }

    /** The latest first. */
    public List<ShopLine> shopHistory(UUID player) {
        return shopHistory.getOrDefault(player, List.of());
    }

    public void addShopHistory(UUID player, ShopLine line) {
        openSummaries.remove(player);
        pushShopLine(player, line);
    }

    /**
     * A trade a shop block made on its owner's behalf (shop plan, section 4.6). The history keeps 30 lines, which a block trading every
     * second would fill in half a minute, so its trades add up in one line per trade, currency, wallet and direction: the line is
     * updated and moved to the top for as long as no other operation comes in between.
     */
    public void addShopSummary(UUID player, ShopLine line) {
        Map<String, ShopLine> open = openSummaries.computeIfAbsent(player, id -> new HashMap<>());
        String key = line.ref() + '|' + line.currency() + '|' + line.treasury() + '|' + Long.signum(line.delta());
        List<ShopLine> lines = shopHistory.computeIfAbsent(player, id -> new ArrayList<>());
        ShopLine before = open.get(key);
        // By identity: an equal line written by hand is not the block's.
        int at = -1;
        for (int i = 0; before != null && i < lines.size(); i++) {
            if (lines.get(i) == before) {
                at = i;
                break;
            }
        }
        ShopLine written = line;
        if (at >= 0) {
            lines.remove(at);
            written = new ShopLine(line.time(), before.delta() + line.delta(), line.currency(), line.ref(),
                    (int) Math.min(Integer.MAX_VALUE, (long) before.times() + line.times()), line.treasury());
        }
        pushShopLine(player, written);
        open.put(key, written);
    }

    private void pushShopLine(UUID player, ShopLine line) {
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

    /**
     * A copy of a team's progress for a player who leaves it. The rewards the player took for themselves stay taken: in a team they
     * live in the player's own record, and copying the team's over it alone would let leaving and joining again pay them twice.
     */
    public void copyTeam(UUID from, UUID to) {
        Map<ResourceLocation, QuestProgress> copy = new HashMap<>(team(from));
        Map<ResourceLocation, QuestProgress> own = teams.get(to);
        if (own != null) {
            own.forEach((quest, mine) -> {
                if (!mine.claimed().isEmpty()) {
                    QuestProgress shared = copy.getOrDefault(quest, QuestProgress.EMPTY);
                    Set<Integer> claimed = new java.util.TreeSet<>(shared.claimed());
                    claimed.addAll(mine.claimed());
                    copy.put(quest, shared.withClaimed(List.copyOf(claimed)));
                }
            });
        }
        teams.put(to, copy);
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
