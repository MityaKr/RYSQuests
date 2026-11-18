package works.repka.rysquests.progress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.MinecraftServer;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.api.QuestCompletedEvent;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.dev.DevReports;
import works.repka.rysquests.network.DevReportPayload;
import works.repka.rysquests.network.PinnedPayload;
import works.repka.rysquests.api.ScriptHooks;
import works.repka.rysquests.network.TeamInfoPayload;
import works.repka.rysquests.network.GuidePagesPayload;
import works.repka.rysquests.network.ProgressPayload;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.network.StageLocksPayload;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.CommandReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.LootTableReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.StageReward;
import works.repka.rysquests.reward.TitleReward;
import works.repka.rysquests.shop.ShopService;
import works.repka.rysquests.reward.XpReward;
import works.repka.rysquests.stage.Stages;
import works.repka.rysquests.task.AdvancementTask;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.FluidTask;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.CollectionTask;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.KillTask;
import works.repka.rysquests.task.StatTask;
import works.repka.rysquests.task.XpTask;
import works.repka.rysquests.task.VisitTask;

/** Server-side quest rules. Runs on the main thread only. */
public final class ProgressService {
    private static final int POLL_INTERVAL = 20;
    private static final int STAGE_INTERVAL = 100;
    private static final long MINUTE_MS = 60_000;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ProgressService.class);
    private static final ResourceLocation FEED_SOURCE = ResourceLocation.fromNamespaceAndPath("rysquests", "quests");

    /** Open, unlocked quests per team, so that polling does not walk the whole book. */
    /** The open quests of each team, and when the first cooldown among the rest runs out and the list is due again. */
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private record Active(List<Quest> quests, long expires) {
    }
    private static final Map<UUID, Integer> INVENTORY_HASH = new HashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> STAGE_LOCKS = new HashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> GUIDE_LOCKS = new HashMap<>();
    private static final Map<UUID, Set<String>> OPEN_STAGES = new HashMap<>();
    private static QuestBook activeBook;

    private ProgressService() {
    }

    private static UUID team(ServerPlayer player) {
        return ProgressStore.get(player.server).teamOf(player.getUUID(), book().meta().progressMode());
    }

    private static QuestBook book() {
        return BookHolder.current().book();
    }

    /**
     * A team shares task progress, while each member claims rewards for themselves. A finished round of a repeatable quest whose
     * rewards still wait for the player shows first, while the team's next round goes on.
     */
    private static QuestProgress viewFor(ServerPlayer player, ResourceLocation quest, QuestProgress teamProgress) {
        ProgressStore store = ProgressStore.get(player.server);
        boolean alone = team(player).equals(player.getUUID());
        List<Integer> own = alone ? List.of() : store.get(player.getUUID(), quest).claimed();
        return TeamRewards.memberView(book().quests().get(quest), teamProgress, own, store.owed(player.getUUID(), quest), alone);
    }

    private static Function<ResourceLocation, QuestProgress> lookup(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        UUID team = team(player);
        return id -> viewFor(player, id, store.get(team, id));
    }

    /** The team's own records, the same for every member: what is open, done or stuck, rewards aside. */
    private static Function<ResourceLocation, QuestProgress> teamLookup(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        UUID team = team(player);
        return id -> store.get(team, id);
    }

    private static boolean stageAllows(ServerPlayer player, Quest quest) {
        return quest.stageRequired().map(stage -> Stages.has(player, stage)).orElse(true);
    }

    // --- polling ---

    /**
     * Whether the book counts for this player: a closed book is not sent to players, so it neither counts their progress nor takes
     * their actions until an operator opens it. Operators try it out meanwhile.
     */
    private static boolean counts(ServerPlayer player) {
        return book().meta().published() || player.hasPermissions(works.repka.rysquests.data.BookHolder.OPERATOR);
    }

    public static void tick(ServerPlayer player) {
        if (!counts(player)) {
            return;
        }
        int phase = player.getId() + player.server.getTickCount();
        if (phase % STAGE_INTERVAL == 0) {
            refreshStageLocks(player);
        }
        if (phase % POLL_INTERVAL != 0) {
            return;
        }
        List<Quest> active = openFor(player);
        int hash = inventoryHash(player.getInventory());
        boolean inventoryChanged = !Objects.equals(INVENTORY_HASH.put(player.getUUID(), hash), hash);
        Map<ResourceLocation, QuestProgress> changes = new HashMap<>();
        // Who shares the progress is asked once a poll, and only when a statistic is counted.
        Supplier<List<UUID>> sharers = Suppliers.memoize(() -> sharers(player));
        ProgressStore store = ProgressStore.get(player.server);
        UUID team = team(player);
        // A collection's level reads the marks this same poll may have just raised (ADR-62).
        Function<ResourceLocation, QuestProgress> seen = id -> changes.containsKey(id) ? changes.get(id) : store.get(team, id);
        for (Quest quest : active) {
            poll(player, quest, inventoryChanged, sharers, seen).ifPresent(updated -> changes.put(quest.id(), updated));
        }
        apply(player, changes);
    }

    private static void refreshStageLocks(ServerPlayer player) {
        Set<ResourceLocation> locks = new HashSet<>();
        for (Quest quest : book().quests().values()) {
            if (!stageAllows(player, quest)) {
                locks.add(quest.id());
            }
        }
        Set<ResourceLocation> guideLocks = new HashSet<>();
        for (Guide guide : book().guides().values()) {
            if (guide.stage().filter(stage -> !Stages.has(player, stage)).isPresent()) {
                guideLocks.add(guide.id());
            }
        }
        Set<String> open = new HashSet<>();
        for (String stage : sectionStages()) {
            if (Stages.has(player, stage)) {
                open.add(stage);
            }
        }
        boolean questsChanged = !locks.equals(STAGE_LOCKS.put(player.getUUID(), locks));
        boolean guidesChanged = !guideLocks.equals(GUIDE_LOCKS.put(player.getUUID(), guideLocks));
        boolean stagesChanged = !open.equals(OPEN_STAGES.put(player.getUUID(), open));
        if (questsChanged) {
            ACTIVE.remove(team(player));
        }
        if (questsChanged || guidesChanged || stagesChanged) {
            PacketDistributor.sendToPlayer(player, new StageLocksPayload(List.copyOf(locks), List.copyOf(guideLocks), List.copyOf(open)));
        }
    }

    /** The stages custom sections look at. */
    private static Set<String> sectionStages() {
        Set<String> stages = new HashSet<>();
        for (SectionDef section : book().sections().values()) {
            if (section instanceof MatrixSection matrix) {
                matrix.columns().forEach(column -> column.stage().ifPresent(stages::add));
            } else if (section instanceof CardsSection cards) {
                cards.cards().forEach(card -> card.stage().ifPresent(stages::add));
            }
        }
        book().offers().values().forEach(offer -> offer.stage().ifPresent(stages::add));
        return stages;
    }

    /** The team's open quests that this player's own stages allow. */
    private static List<Quest> openFor(ServerPlayer player) {
        List<Quest> open = active(player);
        List<Quest> allowed = new ArrayList<>(open.size());
        for (Quest quest : open) {
            if (stageAllows(player, quest)) {
                allowed.add(quest);
            }
        }
        return allowed;
    }

    private static List<Quest> active(ServerPlayer player) {
        if (activeBook != book()) {
            activeBook = book();
            invalidateAll();
        }
        UUID team = team(player);
        long now = System.currentTimeMillis();
        Active cached = ACTIVE.get(team);
        if (cached != null && now < cached.expires()) {
            return cached.quests();
        }
        // What is open is the same for every member, whatever rewards one of them still has to take of an earlier round.
        Function<ResourceLocation, QuestProgress> progress = teamLookup(player);
        List<Quest> open = new ArrayList<>();
        long expires = Long.MAX_VALUE;
        for (Quest quest : activeBook.quests().values()) {
            // Drafts and archived quests are not counted: players are never sent them.
            if (!QuestLogic.isLive(quest)) {
                continue;
            }
            QuestProgress own = progress.apply(quest.id());
            // The marks of a collection the author made longer are asked again for the new items (ADR-62).
            if (own.completed() && !(Achievement.isCollectionQuest(quest.id()) && !QuestLogic.allTasksDone(quest, own.tasks()))) {
                continue;
            }
            if (own.coolingDown()) {
                // A repeatable quest comes back by itself: the list is rebuilt when its cooldown runs out.
                expires = Math.min(expires, own.nextAt());
            } else if (QuestLogic.isUnlocked(quest, progress)) {
                // Stages are each player's own: the shared list leaves them out, openFor applies the asking player's.
                open.add(quest);
            }
        }
        List<Quest> quests = List.copyOf(open);
        ACTIVE.put(team, new Active(quests, expires));
        // A quest that has just opened or come back reads the inventory at once, not after the next change in it.
        player.server.getPlayerList().getPlayers().stream().filter(member -> team(member).equals(team)).forEach(member -> INVENTORY_HASH.remove(member.getUUID()));
        return quests;
    }

    private static Optional<QuestProgress> poll(ServerPlayer player, Quest quest, boolean inventoryChanged, Supplier<List<UUID>> sharers,
            Function<ResourceLocation, QuestProgress> seen) {
        QuestProgress before = seen.apply(quest.id());
        List<Integer> values = before.taskValues(quest.tasks().size());
        boolean changed = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            int current = values.get(i);
            if (current < task.target()) {
                int next = task instanceof CollectionTask
                        ? collectionLevel(quest, current, task.target(), seen)
                        : Math.min(task.target(), pollTask(player, task, current, inventoryChanged, sharers));
                // A streak starts again from zero, and its counter with it (ADR-61); every other value only grows.
                if (next > current || next < current && task instanceof StatTask stat && StatCount.isStreak(stat)) {
                    values.set(i, next);
                    changed = true;
                }
            }
        }
        return QuestLogic.afterPoll(quest, before, values, changed, System.currentTimeMillis());
    }

    /** A level of a collection counts the marks of its achievement (ADR-62). */
    private static int collectionLevel(Quest level, int current, int target, Function<ResourceLocation, QuestProgress> seen) {
        ResourceLocation marksId = Achievement.collectionQuestOf(level.id());
        Quest marks = book().quests().get(marksId);
        return marks == null ? current : CollectionMarks.levelValue(current, CollectionMarks.count(marks, seen.apply(marksId)), target);
    }

    private static int pollTask(ServerPlayer player, TaskDef task, int current, boolean inventoryChanged, Supplier<List<UUID>> sharers) {
        return switch (task) {
            case ItemTask item when item.mode() != ItemTask.Mode.CONSUME && item.mode() != ItemTask.Mode.CRAFT && inventoryChanged -> Math.max(current, countIn(player.getInventory(), item));
            case FluidTask fluid when fluid.mode() != ItemTask.Mode.CONSUME && fluid.mode() != ItemTask.Mode.CRAFT && inventoryChanged -> Math.max(current, fluidIn(player.getInventory(), fluid));
            case XpTask xp when !xp.consume() -> Math.max(current, xp.have(player));
            case StatTask stat -> statCount(player, stat, current, sharers.get());
            case AdvancementTask advancement -> isEarned(player, advancement) ? 1 : current;
            case VisitTask visit -> isVisiting(player, visit) ? 1 : current;
            case works.repka.rysquests.task.ScriptTask script -> ScriptHooks.get().taskProgress(player, script.script(), current);
            default -> current;
        };
    }

    private static QuestProgress finish(Quest quest, QuestProgress before, List<Integer> values) {
        return QuestLogic.finish(quest, before, values, System.currentTimeMillis());
    }

    private static void apply(ServerPlayer player, Map<ResourceLocation, QuestProgress> changes) {
        apply(player, changes, true);
    }

    /** @param byPlayer the change comes from what the player did, not from an operator's command */
    private static void apply(ServerPlayer player, Map<ResourceLocation, QuestProgress> changes, boolean byPlayer) {
        if (changes.isEmpty()) {
            return;
        }
        UUID team = team(player);
        ProgressStore store = ProgressStore.get(player.server);
        List<ResourceLocation> newlyCompleted = changes.entrySet().stream()
                .filter(entry -> entry.getValue().completed() && !store.get(team, entry.getKey()).completed())
                // the marks of a collection fill up in silence: its level tells that it is done (ADR-62)
                .filter(entry -> !Achievement.isCollectionQuest(entry.getKey()))
                .map(Map.Entry::getKey)
                .toList();
        Optional<ResourceLocation> lastChapter = byPlayer ? lastChapterOf(newlyCompleted).filter(chapter -> store.setLastChapter(player.getUUID(), chapter)) : Optional.empty();
        // Task values move every poll; only a quest that finishes or starts a new round changes which quests are open.
        boolean reopens = changes.entrySet().stream().anyMatch(entry -> {
            QuestProgress old = store.get(team, entry.getKey());
            return old.completed() != entry.getValue().completed() || old.nextAt() != entry.getValue().nextAt();
        });
        store.put(team, changes);
        if (reopens) {
            ACTIVE.remove(team);
        }
        newlyCompleted.forEach(id -> {
            NeoForge.EVENT_BUS.post(new QuestCompletedEvent(team, id, player));
            postToTeam(team, id, player);
        });
        boolean tierReached = newlyCompleted.stream().anyMatch(Achievement::isTierQuest);
        if (tierReached) {
            AchievementService.invalidateRarity();
        }
        for (ServerPlayer member : player.server.getPlayerList().getPlayers()) {
            if (team(member).equals(team)) {
                Map<ResourceLocation, QuestProgress> view = new HashMap<>();
                changes.forEach((id, progress) -> view.put(id, viewFor(member, id, progress)));
                Map<ResourceLocation, ProgressPayload.Done> done = new HashMap<>();
                for (ResourceLocation id : newlyCompleted) {
                    done.put(id, new ProgressPayload.Done(byPlayer ? Optional.of(player.getUUID()) : Optional.empty(),
                            player.getGameProfile().getName(), rewardState(id, view.get(id))));
                }
                // The chapter of the last completion belongs to whoever did it: teammates keep their own.
                PacketDistributor.sendToPlayer(member, new ProgressPayload(false, view, member == player ? lastChapter : Optional.empty(), done));
                if (tierReached) {
                    claimAchievements(member);
                    AchievementService.send(member);
                }
            }
        }
    }

    /**
     * What became of a just completed quest's reward for a member, for the message about it: achievement tiers are given at once, a
     * reward only one of the team could take may already be taken, otherwise it waits in the book.
     */
    private static int rewardState(ResourceLocation id, QuestProgress view) {
        Quest quest = book().quests().get(id);
        if (quest == null || quest.rewards().isEmpty()) {
            return ProgressPayload.Done.NONE;
        }
        if (Achievement.isTierQuest(id)) {
            return ProgressPayload.Done.RECEIVED;
        }
        boolean allTaken = view != null && view.claimed().size() >= quest.rewards().size();
        return allTaken ? ProgressPayload.Done.TEAM_TAKEN : ProgressPayload.Done.WAITING;
    }

    /** The story chapter of the last of these quests, for the next step on "Now"; achievement tiers and card conditions have none. */
    private static Optional<ResourceLocation> lastChapterOf(List<ResourceLocation> completed) {
        Optional<ResourceLocation> found = Optional.empty();
        for (ResourceLocation id : completed) {
            Quest quest = book().quests().get(id);
            Optional<ResourceLocation> chapter = quest == null ? Optional.empty() : quest.chapter();
            if (chapter.map(book().chapters()::get).filter(works.repka.rysquests.data.Chapter::isStory).isPresent()) {
                found = chapter;
            }
        }
        return found;
    }

    /** Achievements have nothing to press: what is reached is given at once, and on the next login to those who were away. */
    private static void claimAchievements(ServerPlayer player) {
        boolean given = false;
        for (Achievement achievement : book().achievements().values()) {
            for (Achievement.Tier tier : achievement.tiers()) {
                Quest quest = book().quests().get(achievement.questId(tier));
                if (quest != null && QuestLogic.isLive(quest) && QuestLogic.status(quest, lookup(player)) == QuestStatus.COMPLETED) {
                    // Nothing to press here, so a reward that does not fit falls at the player's feet, as before.
                    given |= claim(player, quest, List.of(), true).given();
                }
            }
        }
        if (given) {
            Feedback.reward(player);
        }
    }

    private static void postToTeam(UUID team, ResourceLocation id, ServerPlayer player) {
        Quest quest = book().quests().get(id);
        if (quest != null && !TeamsApi.get().isSolo(team)) {
            TeamsApi.get().post(team, new TeamsApi.Post(FEED_SOURCE,
                    Component.translatable("rysquests.team.feed_completed", player.getDisplayName(), Component.translatable(quest.title())),
                    quest.icon().orElse(null), false));
        }
    }

    // --- task checks ---

    private static int inventoryHash(Inventory inventory) {
        int hash = 1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            hash = 31 * hash + (stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount());
        }
        return hash;
    }

    private static boolean matches(ItemTask task, ItemStack stack) {
        return task.matches(stack);
    }

    private static int countIn(Inventory inventory, ItemTask task) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (matches(task, stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int fluidIn(Inventory inventory, FluidTask task) {
        return task.amountIn(inventory);
    }

    private static int takeFluid(Inventory inventory, FluidTask task, int wanted) {
        Fluid fluid = BuiltInRegistries.FLUID.get(task.fluid());
        int taken = 0;
        for (int i = 0; i < inventory.getContainerSize() && taken < wanted; i++) {
            ItemStack stack = inventory.getItem(i);
            IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler != null) {
                taken += handler.drain(new FluidStack(fluid, wanted - taken), IFluidHandler.FluidAction.EXECUTE).getAmount();
                inventory.setItem(i, handler.getContainer());
            }
        }
        return taken;
    }

    /**
     * A statistic task's value from the statistics of everyone who shares the progress (ADR-61): their sum, or the longest current
     * streak. Each member has their own statistic, so the polling player's alone would leave the team's counter at its best member's.
     */
    private static int statCount(ServerPlayer player, StatTask task, int current, List<UUID> sharers) {
        Stat<?> stat = statOf(task);
        if (stat == null) {
            return current;
        }
        int[] values = new int[sharers.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = MemberStats.value(player.server, sharers.get(i), stat);
        }
        boolean streak = StatCount.isStreak(task);
        return StatCount.next(streak, current, StatCount.combine(streak, values), task.target());
    }

    /** The players whose statistics make a shared counter: the polling player and everyone sharing their progress, offline too. */
    private static List<UUID> sharers(ServerPlayer player) {
        Set<UUID> found = new java.util.LinkedHashSet<>();
        found.add(player.getUUID());
        works.repka.rysquests.data.ProgressMode mode = book().meta().progressMode();
        switch (mode) {
            case SOLO -> {
            }
            case TEAM -> found.addAll(ProgressStore.get(player.server).teamMembers(team(player), mode));
            case SERVER -> found.addAll(MemberStats.everyone(player.server));
        }
        return List.copyOf(found);
    }

    @Nullable
    private static Stat<?> statOf(StatTask task) {
        StatType<?> type = BuiltInRegistries.STAT_TYPE.get(task.stat());
        return type == null ? null : statOf(type, task.entry());
    }

    @Nullable
    private static <T> Stat<T> statOf(StatType<T> type, ResourceLocation target) {
        T value = type.getRegistry().get(target);
        return value == null ? null : type.get(value);
    }

    private static boolean isEarned(ServerPlayer player, AdvancementTask task) {
        var advancement = player.server.getAdvancements().get(task.advancement());
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static boolean isVisiting(ServerPlayer player, VisitTask task) {
        if (task.dimension().isPresent() && !task.dimension().get().equals(player.level().dimension().location())) {
            return false;
        }
        if (task.pos().isPresent() && task.size().isPresent()) {
            List<Integer> pos = task.pos().get();
            List<Integer> size = task.size().get();
            long[] at = {player.getBlockX(), player.getBlockY(), player.getBlockZ()};
            for (int i = 0; i < 3; i++) {
                // Half open, in longs: a box FTB Quests stretches over the whole world does not overflow.
                if (at[i] < pos.get(i) || at[i] >= (long) pos.get(i) + size.get(i)) {
                    return false;
                }
            }
        } else if (task.pos().isPresent()) {
            List<Integer> pos = task.pos().get();
            double dx = player.getX() - pos.get(0);
            double dy = player.getY() - pos.get(1);
            double dz = player.getZ() - pos.get(2);
            if (dx * dx + dy * dy + dz * dz > (double) task.radius() * task.radius()) {
                return false;
            }
        }
        return task.biome().map(biome -> isInBiome(player, biome)).orElse(true)
                && task.structure().map(structure -> isInside(player, structure)).orElse(true);
    }

    private static boolean isInBiome(ServerPlayer player, ItemRef biome) {
        var holder = player.level().getBiome(player.blockPosition());
        return biome.tag()
                ? holder.is(TagKey.create(Registries.BIOME, biome.id()))
                : holder.is(ResourceKey.create(Registries.BIOME, biome.id()));
    }

    private static boolean isInside(ServerPlayer player, ItemRef structure) {
        ServerLevel level = player.serverLevel();
        if (structure.tag()) {
            return level.structureManager().getStructureWithPieceAt(player.blockPosition(), TagKey.create(Registries.STRUCTURE, structure.id())).isValid();
        }
        Structure target = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structure.id());
        return target != null && level.structureManager().getStructureWithPieceAt(player.blockPosition(), target).isValid();
    }

    /** Crafted or smelted items count towards open tasks in craft mode. */
    public static void onCrafted(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !counts(player)) {
            return;
        }
        ProgressStore store = ProgressStore.get(player.server);
        Map<ResourceLocation, QuestProgress> changes = new HashMap<>();
        for (Quest quest : openFor(player)) {
            QuestProgress before = store.get(team(player), quest.id());
            List<Integer> values = before.taskValues(quest.tasks().size());
            boolean changed = false;
            for (int i = 0; i < quest.tasks().size(); i++) {
                if (quest.tasks().get(i) instanceof ItemTask task && task.mode() == ItemTask.Mode.CRAFT && values.get(i) < task.count() && matches(task, stack)) {
                    values.set(i, Math.min(task.count(), values.get(i) + stack.getCount()));
                    changed = true;
                }
            }
            if (changed) {
                changes.put(quest.id(), finish(quest, before, values));
            }
        }
        apply(player, changes);
    }

    public static void onKill(ServerPlayer player, ResourceLocation entity) {
        if (!counts(player)) {
            return;
        }
        ProgressStore store = ProgressStore.get(player.server);
        Map<ResourceLocation, QuestProgress> changes = new HashMap<>();
        for (Quest quest : openFor(player)) {
            QuestProgress before = store.get(team(player), quest.id());
            List<Integer> values = before.taskValues(quest.tasks().size());
            boolean changed = false;
            for (int i = 0; i < quest.tasks().size(); i++) {
                if (quest.tasks().get(i) instanceof KillTask kill && kill.entity().equals(entity) && values.get(i) < kill.count()) {
                    values.set(i, values.get(i) + 1);
                    changed = true;
                }
            }
            if (changed) {
                changes.put(quest.id(), finish(quest, before, values));
            }
        }
        apply(player, changes);
    }

    // --- player actions ---

    public static void handle(ServerPlayer player, QuestActionPayload payload) {
        if (!counts(player)) {
            return;
        }
        if (payload.action() == QuestActionPayload.SUBMIT_ALL) {
            if (!TeamsApi.get().can(player.getUUID(), "submit_tasks")) {
                Feedback.refuse(player, "rysquests.submit.no_right");
                return;
            }
            // Each quest is checked right before it is paid, against what the ones before it left: none is paid by half.
            int handed = 0;
            for (Quest open : openFor(player)) {
                if (canSubmitFully(player, open) && submit(player, open, QuestActionPayload.ALL_TASKS)) {
                    handed++;
                }
            }
            // It says what it did, or that there was nothing to hand in.
            if (handed == 0) {
                Feedback.refuse(player, "rysquests.submit.nothing");
            } else {
                Feedback.inform(player, "rysquests.submit.done", handed);
            }
            if (handed > 0) {
                Feedback.submit(player);
            }
            return;
        }
        if (payload.action() == QuestActionPayload.CLAIM_ALL) {
            claimAll(player);
            return;
        }
        if (payload.action() == QuestActionPayload.DEV_REPORT) {
            if (player.hasPermissions(2)) {
                DevReports.Standing standing = DevReports.standing(player.server);
                Util.backgroundExecutor().execute(() -> {
                    DevReports.Report report = DevReports.build(player.server, standing);
                    DevReports.write(player.server, report);
                    player.server.execute(() -> PacketDistributor.sendToPlayer(player, new DevReportPayload(report)));
                });
            }
            return;
        }
        if (payload.action() == QuestActionPayload.READ_GUIDE_PAGE) {
            readGuidePage(player, payload.quest(), payload.value());
            return;
        }
        Quest quest = book().quests().get(payload.quest());
        if (quest == null || !QuestLogic.isLive(quest)) {
            return;
        }
        switch (payload.action()) {
            // The value names the one task whose "Hand in" was pressed, or ALL_TASKS for the whole quest (review U10).
            case QuestActionPayload.SUBMIT -> submit(player, quest, payload.value());
            case QuestActionPayload.CHECK -> check(player, quest, payload.value());
            case QuestActionPayload.CLAIM -> {
                Claim result = claim(player, quest, payload.choices(), false);
                if (result.given()) {
                    Feedback.reward(player);
                }
                if (result.refusal() != null) {
                    Feedback.refuse(player, result.refusal());
                }
            }
            case QuestActionPayload.CLAIM_EPOCH -> {
                if (book().isEpochReward(quest.id())) {
                    tickEpoch(player, quest);
                    Claim result = claim(player, quest, payload.choices(), false);
                    if (result.given()) {
                        Feedback.reward(player);
                    }
                    if (result.refusal() != null) {
                        Feedback.refuse(player, result.refusal());
                    }
                }
            }
            case QuestActionPayload.PIN -> togglePin(player, quest);
            case QuestActionPayload.RESET -> {
                // checked here, whatever the client shows: only an operator resets a quest, so that rewards cannot be taken twice
                if (!player.hasPermissions(2)) {
                    Feedback.refuse(player, "rysquests.qmenu.no_right");
                } else {
                    setCompleted(player, quest, false);
                    Feedback.inform(player, "rysquests.qmenu.reset_done");
                }
            }
            case QuestActionPayload.COMPLETE -> {
                // checked here, whatever the client shows: only an operator completes a quest without its tasks
                if (!player.hasPermissions(2)) {
                    Feedback.refuse(player, "rysquests.qmenu.no_right");
                } else if (!ProgressStore.get(player.server).get(team(player), quest.id()).completed()) {
                    setCompleted(player, quest, true);
                    Feedback.inform(player, "rysquests.qmenu.complete_done");
                }
            }
            default -> {
            }
        }
    }

    private static void readGuidePage(ServerPlayer player, ResourceLocation guideId, int index) {
        // the book's own pages "О книге" are read as a guide of their own (ADR-60)
        Guide guide = book().guideOrAbout(guideId).orElse(null);
        if (guide == null || index < 0 || index >= guide.pages().size()) {
            return;
        }
        String key = guide.readKey(guide.pages().get(index));
        if (ProgressStore.get(player.server).markGuidePage(player.getUUID(), key)) {
            PacketDistributor.sendToPlayer(player, new GuidePagesPayload(false, List.of(key)));
        }
    }

    /** "Ready" means every consumed item is at hand, so that a bulk hand-in never leaves a quest half paid. */
    private static boolean canSubmitFully(ServerPlayer player, Quest quest) {
        QuestProgress progress = ProgressStore.get(player.server).get(team(player), quest.id());
        boolean any = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            int missing = quest.tasks().get(i).target() - progress.task(i);
            if (missing <= 0) {
                continue;
            }
            if (quest.tasks().get(i) instanceof ItemTask task && task.mode() == ItemTask.Mode.CONSUME) {
                if (countIn(player.getInventory(), task) < missing) {
                    return false;
                }
                any = true;
            } else if (quest.tasks().get(i) instanceof FluidTask task && task.mode() == ItemTask.Mode.CONSUME) {
                if (fluidIn(player.getInventory(), task) < missing) {
                    return false;
                }
                any = true;
            } else if (quest.tasks().get(i) instanceof XpTask xp && xp.consume()) {
                if (xp.have(player) < missing) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    /** Gives everything that needs no decision: a choice stays unclaimed until the player opens its quest and picks. */
    private static void claimAll(ServerPlayer player) {
        // An epoch passed gives its reward with the rest: its ticks are made first (ADR-60).
        for (ResourceLocation id : book().meta().epochRewards().keySet()) {
            Quest epoch = book().quests().get(id);
            if (epoch != null && QuestLogic.isLive(epoch)) {
                tickEpoch(player, epoch);
            }
        }
        Function<ResourceLocation, QuestProgress> progress = lookup(player);
        List<Quest> ready = book().quests().values().stream()
                .filter(quest -> QuestLogic.isLive(quest) && QuestLogic.status(quest, progress) == QuestStatus.COMPLETED)
                .toList();
        boolean given = false;
        boolean full = false;
        for (Quest quest : ready) {
            Claim result = claim(player, quest, List.of(), false);
            given |= result.given();
            full |= INVENTORY_FULL.equals(result.refusal());
        }
        if (given) {
            Feedback.reward(player);
        }
        // A choice left for the player to pick is expected here; a full inventory is not.
        if (full) {
            Feedback.refuse(player, INVENTORY_FULL);
        }
    }

    private static void togglePin(ServerPlayer player, Quest quest) {
        ProgressStore store = ProgressStore.get(player.server);
        List<ResourceLocation> pinned = new ArrayList<>(store.pinned(player.getUUID()));
        // A finished quest that is not repeatable never shows on the board again: it gives its place back for good.
        pinned.removeIf(id -> !id.equals(quest.id()) && (book().quests().get(id) == null
                || store.get(team(player), id).completed() && book().quests().get(id).options().repeatable().isEmpty()));
        if (!pinned.remove(quest.id())) {
            if (openPinned(player, pinned) + store.pinnedWays(player.getUUID()).size() < book().meta().hud().maxPinned()) {
                pinned.add(quest.id());
            } else {
                Feedback.refuse(player, "rysquests.pin.limit", book().meta().hud().maxPinned());
            }
        }
        store.setPinned(player.getUUID(), pinned);
        PacketDistributor.sendToPlayer(player, new PinnedPayload(List.copyOf(pinned), store.pinnedWays(player.getUUID())));
    }

    /** Pinned quests that still show on the board: a finished one leaves the board and frees its place. */
    private static int openPinned(ServerPlayer player, List<ResourceLocation> pinned) {
        ProgressStore store = ProgressStore.get(player.server);
        UUID team = team(player);
        return PinLimit.taken(pinned, book().quests()::get, id -> store.get(team, id));
    }

    /** Quests and ways share the player's limit of pinned cards. */
    public static void togglePinnedWay(ServerPlayer player, String key) {
        if (MatrixSection.findPinned(book(), key).isEmpty()) {
            return;
        }
        ProgressStore store = ProgressStore.get(player.server);
        List<String> ways = new ArrayList<>(store.pinnedWays(player.getUUID()));
        if (!ways.remove(key)) {
            if (ways.size() + openPinned(player, store.pinned(player.getUUID())) < book().meta().hud().maxPinned()) {
                ways.add(key);
            } else {
                Feedback.refuse(player, "rysquests.pin.limit", book().meta().hud().maxPinned());
            }
        }
        store.setPinnedWays(player.getUUID(), ways);
        PacketDistributor.sendToPlayer(player, new PinnedPayload(store.pinned(player.getUUID()), List.copyOf(ways)));
    }

    private static boolean isOpen(ServerPlayer player, Quest quest) {
        // A member still owed an earlier round helps with the team's current one all the same.
        QuestStatus status = QuestLogic.status(quest, teamLookup(player));
        return (status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS) && stageAllows(player, quest);
    }

    /**
     * @param only the one task to hand in, or {@link QuestActionPayload#ALL_TASKS} for all of them
     * @return whether anything was handed in
     */
    private static boolean submit(ServerPlayer player, Quest quest, int only) {
        if (!isOpen(player, quest) || !TeamsApi.get().can(player.getUUID(), "submit_tasks")) {
            return false;
        }
        if (only != QuestActionPayload.ALL_TASKS && (only < 0 || only >= quest.tasks().size())) {
            return false;
        }
        QuestProgress before = ProgressStore.get(player.server).get(team(player), quest.id());
        List<Integer> values = before.taskValues(quest.tasks().size());
        boolean changed = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (only != QuestActionPayload.ALL_TASKS && i != only) {
                continue;
            }
            int missing = quest.tasks().get(i).target() - values.get(i);
            int taken = 0;
            if (missing > 0 && quest.tasks().get(i) instanceof ItemTask task && task.mode() == ItemTask.Mode.CONSUME) {
                taken = take(player.getInventory(), task, missing);
            } else if (missing > 0 && quest.tasks().get(i) instanceof FluidTask task && task.mode() == ItemTask.Mode.CONSUME) {
                taken = takeFluid(player.getInventory(), task, missing);
            } else if (missing > 0 && quest.tasks().get(i) instanceof XpTask xp && xp.consume()) {
                int wanted = Math.min(missing, xp.have(player));
                int had = xp.have(player);
                if (wanted > 0 && xp.points()) {
                    player.giveExperiencePoints(-wanted);
                } else if (wanted > 0) {
                    player.giveExperienceLevels(-wanted);
                }
                // Other mods may cancel or scale the change: the task gets what actually left the player.
                taken = Math.max(0, Math.min(wanted, had - xp.have(player)));
            }
            if (taken > 0) {
                values.set(i, values.get(i) + taken);
                changed = true;
            }
        }
        if (changed) {
            apply(player, Map.of(quest.id(), finish(quest, before, values)));
        }
        return changed;
    }

    private static int take(Inventory inventory, ItemTask task, int wanted) {
        int taken = 0;
        for (int i = 0; i < inventory.getContainerSize() && taken < wanted; i++) {
            ItemStack stack = inventory.getItem(i);
            if (matches(task, stack)) {
                int part = Math.min(stack.getCount(), wanted - taken);
                stack.shrink(part);
                taken += part;
            }
        }
        if (taken > 0) {
            inventory.setChanged();
        }
        return taken;
    }

    private static void check(ServerPlayer player, Quest quest, int index) {
        if (!isOpen(player, quest) || index < 0 || index >= quest.tasks().size() || !(quest.tasks().get(index) instanceof CheckboxTask)) {
            return;
        }
        QuestProgress before = ProgressStore.get(player.server).get(team(player), quest.id());
        List<Integer> values = before.taskValues(quest.tasks().size());
        values.set(index, 1);
        apply(player, Map.of(quest.id(), finish(quest, before, values)));
    }

    /** Ticks what an epoch's quest still lacks, when ticks are all it lacks and the epoch is passed (ADR-60). */
    private static void tickEpoch(ServerPlayer player, Quest quest) {
        QuestProgress before = ProgressStore.get(player.server).get(team(player), quest.id());
        if (before.completed() || !isOpen(player, quest)) {
            return;
        }
        EpochRewards.ticked(quest, before.taskValues(quest.tasks().size()))
                .ifPresent(values -> apply(player, Map.of(quest.id(), finish(quest, before, values))));
    }

    private static final String INVENTORY_FULL = "rysquests.reward.error.inventory_full";

    /** @param given whether any reward was given now @param refusal the lang key of why something was not, or null */
    private record Claim(boolean given, @Nullable String refusal) {
    }

    /** @param dropExtra give what does not fit at the player's feet rather than keep it for a later claim */
    private static Claim claim(ServerPlayer player, Quest quest, List<Integer> choices, boolean dropExtra) {
        UUID team = team(player);
        ProgressStore store = ProgressStore.get(player.server);
        QuestProgress owed = store.owed(player.getUUID(), quest.id());
        QuestProgress before = viewFor(player, quest.id(), store.get(team, quest.id()));
        if (!before.completed()) {
            return new Claim(false, "rysquests.reward.error.not_done");
        }
        if (!TeamsApi.get().can(player.getUUID(), "claim_rewards")) {
            return new Claim(false, "rysquests.reward.error.no_right");
        }
        List<Integer> claimed = new ArrayList<>(before.claimed());
        String refusal = null;
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (claimed.contains(i)) {
                continue;
            }
            String problem = grant(player, quest, quest.rewards().get(i), i < choices.size() ? choices.get(i) : -1, dropExtra);
            if (problem == null) {
                claimed.add(i);
            } else if (refusal == null || INVENTORY_FULL.equals(problem)) {
                refusal = problem;
            }
        }
        boolean given = claimed.size() > before.claimed().size();
        if (owed != null) {
            // A round the team has already moved on from: it is the player's alone, and taking it starts nothing.
            if (given) {
                store.setOwed(player.getUUID(), quest.id(), TeamRewards.owedAfterClaim(quest, owed, claimed));
                PacketDistributor.sendToPlayer(player, new ProgressPayload(false, Map.of(quest.id(), viewFor(player, quest.id(), store.get(team, quest.id())))));
            }
            return new Claim(given, refusal);
        }
        boolean allClaimed = claimed.size() >= quest.rewards().size();
        if (allClaimed && quest.options().repeatable().isPresent()) {
            long cooldown = quest.options().repeatable().get().cooldownMinutes() * MINUTE_MS;
            QuestProgress round = store.get(team, quest.id());
            int times = round.times() + 1;
            // What the others have not taken of this round stays theirs until they take it, the next round going on meanwhile.
            for (UUID member : store.roundMembers(team, book().meta().progressMode())) {
                if (!member.equals(player.getUUID())) {
                    List<Integer> own = member.equals(team) ? round.claimed() : store.get(member, quest.id()).claimed();
                    store.setOwed(member, quest.id(), TeamRewards.owedAtNewRound(quest, round, own, store.owed(member, quest.id())));
                }
            }
            // A new round starts clean for everyone in the team: what was owed of this one is kept above, apart from the rounds.
            // The rows are the store's own, so this holds for a team mod, our own teams (whose id is the owner's) and
            // server-wide progress alike, whoever of them claims.
            for (UUID member : TeamRewards.rowsToReset(store.allProgress().keySet(), team, row -> store.teamOf(row, book().meta().progressMode()))) {
                store.put(member, Map.of(quest.id(), QuestProgress.EMPTY));
            }
            if (!team.equals(player.getUUID())) {
                store.put(player.getUUID(), Map.of(quest.id(), QuestProgress.EMPTY));
            }
            apply(player, Map.of(quest.id(), new QuestProgress(List.of(), 0, List.of(), cooldown > 0 ? System.currentTimeMillis() + cooldown : 0, times)));
            return new Claim(given, refusal);
        }
        if (!given) {
            return new Claim(false, refusal);
        }
        if (team.equals(player.getUUID())) {
            apply(player, Map.of(quest.id(), before.withClaimed(List.copyOf(claimed))));
        } else {
            store.put(player.getUUID(), Map.of(quest.id(), QuestProgress.EMPTY.withClaimed(List.copyOf(claimed))));
            QuestProgress teamRow = store.get(team, quest.id());
            List<Integer> shared = TeamRewards.sharedAfterClaim(quest, teamRow.claimed(), claimed);
            if (shared.size() > teamRow.claimed().size()) {
                apply(player, Map.of(quest.id(), teamRow.withClaimed(shared)));
            } else {
                PacketDistributor.sendToPlayer(player, new ProgressPayload(false, Map.of(quest.id(), before.withClaimed(List.copyOf(claimed)))));
            }
        }
        return new Claim(given, refusal);
    }

    /**
     * @return null when the reward was given, or the lang key of why it stays unclaimed: a choice with no valid pick, or items
     *         that do not fit, which wait for the next claim instead of falling to the ground
     */
    @Nullable
    private static String grant(ServerPlayer player, Quest quest, RewardDef reward, int choice, boolean dropExtra) {
        switch (reward) {
            case ItemReward item -> {
                if (!dropExtra && !fits(player.getInventory(), item)) {
                    return INVENTORY_FULL;
                }
                giveItem(player, item);
            }
            case XpReward xp -> {
                xp.levels().ifPresent(player::giveExperienceLevels);
                xp.points().ifPresent(player::giveExperiencePoints);
            }
            case CommandReward command -> runCommand(player, quest, command);
            case StageReward stage -> Stages.grant(player, stage.stage());
            case TitleReward title -> AchievementService.grantTitle(player, title.id());
            case CurrencyReward currency -> ShopService.deposit(player, currency.id(), currency.amount(), currency.teamMode() == TeamMode.ONCE, ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "reward"));
            case LootTableReward loot -> rollLoot(player, loot);
            case works.repka.rysquests.reward.ScriptReward script -> ScriptHooks.get().reward(player, script.script());
            case ChoiceReward options -> {
                if (choice < 0 || choice >= options.of().size()) {
                    return "rysquests.reward.error.pick";
                }
                return grant(player, quest, options.of().get(choice), -1, dropExtra);
            }
            // A roll cannot be known before it is made: what it gives may still fall at the player's feet.
            case RandomReward random -> grant(player, quest, pick(player, random), -1, true);
            default -> {
            }
        }
        return null;
    }

    /** Whether the whole reward fits into the main inventory, in free slots and on top of the same items. */
    private static boolean fits(Inventory inventory, ItemReward reward) {
        ItemStack template = new ItemStack(BuiltInRegistries.ITEM.get(reward.item()));
        reward.components().ifPresent(template::applyComponents);
        long room = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.isEmpty()) {
                room += template.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(stack, template)) {
                room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
            if (room >= reward.count()) {
                return true;
            }
        }
        return room >= reward.count();
    }

    private static void rollLoot(ServerPlayer player, LootTableReward reward) {
        var table = player.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, reward.table()));
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
        for (int i = 0; i < reward.rolls(); i++) {
            table.getRandomItems(params).forEach(stack -> ItemHandlerHelper.giveItemToPlayer(player, stack));
        }
    }

    private static RewardDef pick(ServerPlayer player, RandomReward random) {
        if (random.weights().isEmpty()) {
            return random.of().get(player.getRandom().nextInt(random.of().size()));
        }
        int total = random.weights().stream().mapToInt(Integer::intValue).sum();
        int roll = player.getRandom().nextInt(Math.max(1, total));
        for (int i = 0; i < random.of().size(); i++) {
            roll -= random.weights().get(i);
            if (roll < 0) {
                return random.of().get(i);
            }
        }
        return random.of().getLast();
    }

    private static void giveItem(ServerPlayer player, ItemReward reward) {
        ItemStack template = new ItemStack(BuiltInRegistries.ITEM.get(reward.item()));
        reward.components().ifPresent(template::applyComponents);
        int left = reward.count();
        while (left > 0) {
            int part = Math.min(left, template.getMaxStackSize());
            ItemHandlerHelper.giveItemToPlayer(player, template.copyWithCount(part));
            left -= part;
        }
    }

    private static void runCommand(ServerPlayer player, Quest quest, CommandReward reward) {
        CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput();
        String command = CommandPlaceholders.fill(reward.command(), name -> placeholder(player, quest, name));
        player.server.getCommands().performPrefixedCommand(reward.asServer() ? source.withPermission(reward.level()) : source, command);
    }

    /** Players sharing the progress: a team mod's members, our own team's, everyone known in server mode, or one. */
    private static int memberCount(ServerPlayer player, UUID team) {
        ProgressStore store = ProgressStore.get(player.server);
        if (book().meta().progressMode() == works.repka.rysquests.data.ProgressMode.SERVER) {
            return Math.max(1, player.server.getPlayerList().getPlayerCount());
        }
        // A team mod's members, or our own team's members map, which holds the owner too.
        return Math.max(1, Math.max(TeamsApi.get().members(team).size(), store.memberCount(team)));
    }

    /** The values of FTB Quests' command placeholders, so that imported commands keep working; null leaves a name as it is. */
    @Nullable
    private static String placeholder(ServerPlayer player, Quest quest, String name) {
        UUID team = team(player);
        return switch (name) {
            case "p" -> player.getGameProfile().getName();
            case "x" -> String.valueOf(player.getBlockX());
            case "y" -> String.valueOf(player.getBlockY());
            case "z" -> String.valueOf(player.getBlockZ());
            case "quest" -> quest.id().toString();
            case "chapter" -> quest.chapter().map(ResourceLocation::toString).orElse("");
            case "team", "team_id", "long_team_id" -> team.toString();
            case "member_count" -> String.valueOf(memberCount(player, team));
            case "online_member_count" -> String.valueOf(player.server.getPlayerList().getPlayers().stream().filter(member -> team(member).equals(team)).count());
            default -> null;
        };
    }

    // --- sync and admin ---

    public static void sendAll(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        Map<ResourceLocation, QuestProgress> view = new HashMap<>();
        store.team(team(player)).forEach((id, progress) -> view.put(id, viewFor(player, id, progress)));
        PacketDistributor.sendToPlayer(player, new ProgressPayload(true, view, store.lastChapter(player.getUUID())));
        PacketDistributor.sendToPlayer(player, new PinnedPayload(store.pinned(player.getUUID()), store.pinnedWays(player.getUUID())));
        ShopService.send(player);
        claimAchievements(player);
        AchievementService.send(player);
        PacketDistributor.sendToPlayer(player, new GuidePagesPayload(true, List.copyOf(store.guidePages(player.getUUID()))));
        TeamsApi teams = TeamsApi.get();
        int members = Math.max(teams.members(teams.teamOf(player.getUUID())).size(), store.memberCount(team(player)));
        PacketDistributor.sendToPlayer(player, new TeamInfoPayload(members));
        STAGE_LOCKS.remove(player.getUUID());
        GUIDE_LOCKS.remove(player.getUUID());
        OPEN_STAGES.remove(player.getUUID());
        refreshStageLocks(player);
        mendStuck(player);
    }

    /**
     * Completes the team's quests that are open with every task done but were never completed, at login and after a reload: records
     * from before {@link QuestLogic#afterPoll} completed them (taken over from FTB Quests, merged by a migration). The poll would
     * get to them within a second as well; this does it at once and for quests the poll's list may not hold yet.
     */
    private static void mendStuck(ServerPlayer player) {
        if (!counts(player)) {
            return;
        }
        UUID team = team(player);
        ProgressStore store = ProgressStore.get(player.server);
        Function<ResourceLocation, QuestProgress> progress = teamLookup(player);
        Map<ResourceLocation, QuestProgress> mended = new HashMap<>();
        long now = System.currentTimeMillis();
        for (Quest quest : book().quests().values()) {
            if (QuestLogic.stuck(quest, progress) && stageAllows(player, quest)) {
                QuestProgress before = store.get(team, quest.id());
                mended.put(quest.id(), QuestLogic.finish(quest, before, before.taskValues(quest.tasks().size()), now));
            }
        }
        if (!mended.isEmpty()) {
            LOGGER.info("Completed {} quests whose tasks were all done for {}: {}", mended.size(), player.getGameProfile().getName(), mended.keySet());
            apply(player, mended, false);
        }
    }

    public static void forget(ServerPlayer player) {
        MemberStats.left(player);
        ACTIVE.remove(team(player));
        INVENTORY_HASH.remove(player.getUUID());
        STAGE_LOCKS.remove(player.getUUID());
        GUIDE_LOCKS.remove(player.getUUID());
        OPEN_STAGES.remove(player.getUUID());
    }

    /**
     * Completes or resets a quest for the player's team: the operator's command and the quest's menu in the book. A reset starts the
     * tasks and the rewards again for the whole team, the rewards each member claimed for themselves too, and mends a record stuck
     * with its tasks done; the quests it opened stay as they are.
     */
    public static void setCompleted(ServerPlayer player, Quest quest, boolean completed) {
        UUID team = team(player);
        ProgressStore store = ProgressStore.get(player.server);
        QuestProgress progress = QuestProgress.EMPTY;
        if (completed) {
            List<Integer> values = quest.tasks().stream().map(TaskDef::target).toList();
            progress = new QuestProgress(values, System.currentTimeMillis(), List.of());
        } else {
            for (UUID member : TeamRewards.rowsToReset(store.allProgress().keySet(), team, row -> store.teamOf(row, book().meta().progressMode()))) {
                store.put(member, Map.of(quest.id(), QuestProgress.EMPTY));
            }
            if (!team.equals(player.getUUID())) {
                store.put(player.getUUID(), Map.of(quest.id(), QuestProgress.EMPTY));
            }
        }
        // An operator's command is not the player's own doing: their chapter of the last completion stays.
        apply(player, Map.of(quest.id(), progress), false);
        // polled again from scratch: what lies in the inventory counts at once
        ACTIVE.remove(team);
        player.server.getPlayerList().getPlayers().stream().filter(member -> team(member).equals(team)).forEach(member -> INVENTORY_HASH.remove(member.getUUID()));
    }

    /** Who owns the progress this player counts towards: their team, themselves alone, or the whole server, by the book's mode. */
    public static UUID owner(ServerPlayer player) {
        return team(player);
    }

    /**
     * Marks an entry of an achievement's collection as collected for an owner of progress (ADR-62), online or not: what a script calls
     * to carry over marks it kept itself before collections came to the book. Marking twice changes nothing.
     *
     * @param owner a team, a player alone or the server, as {@link #owner} gives it
     * @param item an item id, or a tag written with {@code #}, as the collection names it
     * @return whether the entry is collected now; false when there is no such collection or entry
     */
    public static boolean collect(MinecraftServer server, UUID owner, Achievement achievement, String item) {
        Quest marks = book().quests().get(achievement.collectionQuestId());
        int index = marks == null ? -1 : CollectionMarks.indexOf(marks, item);
        if (index < 0) {
            return false;
        }
        ProgressStore store = ProgressStore.get(server);
        CollectionMarks.mark(marks, store.get(owner, marks.id()), index, System.currentTimeMillis()).ifPresent(updated -> {
            store.put(owner, Map.of(marks.id(), updated));
            ACTIVE.remove(owner);
            for (ServerPlayer member : server.getPlayerList().getPlayers()) {
                if (team(member).equals(owner)) {
                    PacketDistributor.sendToPlayer(member, new ProgressPayload(false, Map.of(marks.id(), viewFor(member, marks.id(), updated)), Optional.empty(), Map.of()));
                }
            }
        });
        // The levels catch up at the next poll of a member, and an offline team's at its next login.
        return true;
    }

    /** The server stopped: the statistics kept of offline players belong to its world. */
    public static void onServerStopped() {
        MemberStats.clear();
    }

    /** Forgets caches after progress was replaced from outside, such as an import or a migration. */
    public static void invalidateAll() {
        ACTIVE.clear();
        INVENTORY_HASH.clear();
    }
}
