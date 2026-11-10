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
import net.minecraft.stats.StatType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.api.QuestCompletedEvent;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
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
import works.repka.rysquests.reward.StageReward;
import works.repka.rysquests.reward.XpReward;
import works.repka.rysquests.stage.Stages;
import works.repka.rysquests.task.AdvancementTask;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.FluidTask;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.KillTask;
import works.repka.rysquests.task.StatTask;
import works.repka.rysquests.task.VisitTask;

/** Server-side quest rules. Runs on the main thread only. */
public final class ProgressService {
    private static final int POLL_INTERVAL = 20;
    private static final int STAGE_INTERVAL = 100;
    private static final long MINUTE_MS = 60_000;
    private static final ResourceLocation FEED_SOURCE = ResourceLocation.fromNamespaceAndPath("rysquests", "quests");

    /** Open, unlocked quests per team, so that polling does not walk the whole book. */
    private static final Map<UUID, List<Quest>> ACTIVE = new HashMap<>();
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

    /** A team shares task progress, while each member claims rewards for themselves. */
    private static QuestProgress viewFor(ServerPlayer player, ResourceLocation quest, QuestProgress teamProgress) {
        UUID team = team(player);
        if (team.equals(player.getUUID())) {
            return teamProgress;
        }
        List<Integer> own = ProgressStore.get(player.server).get(player.getUUID(), quest).claimed();
        Quest definition = book().quests().get(quest);
        return teamProgress.withClaimed(definition == null ? own : TeamRewards.claimedBy(definition, teamProgress.claimed(), own));
    }

    private static Function<ResourceLocation, QuestProgress> lookup(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        UUID team = team(player);
        return id -> viewFor(player, id, store.get(team, id));
    }

    private static boolean stageAllows(ServerPlayer player, Quest quest) {
        return quest.stageRequired().map(stage -> Stages.has(player, stage)).orElse(true);
    }

    // --- polling ---

    public static void tick(ServerPlayer player) {
        int phase = player.getId() + player.server.getTickCount();
        if (phase % STAGE_INTERVAL == 0) {
            refreshStageLocks(player);
        }
        if (phase % POLL_INTERVAL != 0) {
            return;
        }
        List<Quest> active = active(player);
        int hash = inventoryHash(player.getInventory());
        boolean inventoryChanged = !Objects.equals(INVENTORY_HASH.put(player.getUUID(), hash), hash);
        Map<ResourceLocation, QuestProgress> changes = new HashMap<>();
        for (Quest quest : active) {
            poll(player, quest, inventoryChanged).ifPresent(updated -> changes.put(quest.id(), updated));
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
        return stages;
    }

    private static List<Quest> active(ServerPlayer player) {
        if (activeBook != book()) {
            activeBook = book();
            invalidateAll();
        }
        return ACTIVE.computeIfAbsent(team(player), key -> {
            Function<ResourceLocation, QuestProgress> progress = lookup(player);
            return activeBook.quests().values().stream()
                    .filter(quest -> {
                        QuestProgress own = progress.apply(quest.id());
                        return !own.completed() && !own.coolingDown() && QuestLogic.isUnlocked(quest, progress) && stageAllows(player, quest);
                    })
                    .toList();
        });
    }

    private static Optional<QuestProgress> poll(ServerPlayer player, Quest quest, boolean inventoryChanged) {
        QuestProgress before = ProgressStore.get(player.server).get(team(player), quest.id());
        List<Integer> values = before.taskValues(quest.tasks().size());
        boolean changed = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            int current = values.get(i);
            if (current < task.target()) {
                int next = Math.min(task.target(), pollTask(player, task, current, inventoryChanged));
                if (next > current) {
                    values.set(i, next);
                    changed = true;
                }
            }
        }
        return changed || quest.tasks().isEmpty() ? Optional.of(finish(quest, before, values)) : Optional.empty();
    }

    private static int pollTask(ServerPlayer player, TaskDef task, int current, boolean inventoryChanged) {
        return switch (task) {
            case ItemTask item when item.mode() != ItemTask.Mode.CONSUME && inventoryChanged -> Math.max(current, countIn(player.getInventory(), item));
            case FluidTask fluid when fluid.mode() != ItemTask.Mode.CONSUME && inventoryChanged -> Math.max(current, fluidIn(player.getInventory(), fluid));
            case StatTask stat -> Math.max(current, readStat(player, stat));
            case AdvancementTask advancement -> isEarned(player, advancement) ? 1 : current;
            case VisitTask visit -> isVisiting(player, visit) ? 1 : current;
            case works.repka.rysquests.task.ScriptTask script -> ScriptHooks.get().taskProgress(player, script.script(), current);
            default -> current;
        };
    }

    private static QuestProgress finish(Quest quest, QuestProgress before, List<Integer> values) {
        boolean done = true;
        for (int i = 0; i < quest.tasks().size(); i++) {
            done &= values.get(i) >= quest.tasks().get(i).target();
        }
        long completedAt = done ? System.currentTimeMillis() : before.completedAt();
        return new QuestProgress(List.copyOf(values), completedAt, before.claimed(), before.nextAt());
    }

    private static void apply(ServerPlayer player, Map<ResourceLocation, QuestProgress> changes) {
        if (changes.isEmpty()) {
            return;
        }
        UUID team = team(player);
        ProgressStore store = ProgressStore.get(player.server);
        List<ResourceLocation> newlyCompleted = changes.entrySet().stream()
                .filter(entry -> entry.getValue().completed() && !store.get(team, entry.getKey()).completed())
                .map(Map.Entry::getKey)
                .toList();
        store.put(team, changes);
        ACTIVE.remove(team);
        newlyCompleted.forEach(id -> {
            NeoForge.EVENT_BUS.post(new QuestCompletedEvent(team, id, player));
            postToTeam(team, id, player);
        });
        for (ServerPlayer member : player.server.getPlayerList().getPlayers()) {
            if (team(member).equals(team)) {
                Map<ResourceLocation, QuestProgress> view = new HashMap<>();
                changes.forEach((id, progress) -> view.put(id, viewFor(member, id, progress)));
                PacketDistributor.sendToPlayer(member, new ProgressPayload(false, view));
            }
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
        if (stack.isEmpty() || task.itemRefs().stream().noneMatch(ref -> matchesRef(ref, stack))) {
            return false;
        }
        return task.nbt().flatMap(ItemTask.ComponentMatch::components).map(patch -> hasComponents(stack, patch)).orElse(true);
    }

    private static boolean matchesRef(ItemRef ref, ItemStack stack) {
        return ref.tag()
                ? stack.is(TagKey.create(Registries.ITEM, ref.id()))
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ref.id());
    }

    private static boolean hasComponents(ItemStack stack, DataComponentPatch patch) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (entry.getValue().isPresent() && !entry.getValue().get().equals(stack.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
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
        Fluid fluid = BuiltInRegistries.FLUID.get(task.fluid());
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            IFluidHandlerItem handler = inventory.getItem(i).getCapability(Capabilities.FluidHandler.ITEM);
            if (handler != null) {
                FluidStack contained = handler.drain(new FluidStack(fluid, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
                total += contained.getAmount();
            }
        }
        return total;
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

    private static int readStat(ServerPlayer player, StatTask task) {
        StatType<?> type = BuiltInRegistries.STAT_TYPE.get(task.stat());
        return type == null ? 0 : readStat(player, type, task.entry());
    }

    private static <T> int readStat(ServerPlayer player, StatType<T> type, ResourceLocation target) {
        T value = type.getRegistry().get(target);
        return value == null ? 0 : player.getStats().getValue(type.get(value));
    }

    private static boolean isEarned(ServerPlayer player, AdvancementTask task) {
        var advancement = player.server.getAdvancements().get(task.advancement());
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static boolean isVisiting(ServerPlayer player, VisitTask task) {
        if (task.dimension().isPresent() && !task.dimension().get().equals(player.level().dimension().location())) {
            return false;
        }
        if (task.pos().isPresent()) {
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

    public static void onKill(ServerPlayer player, ResourceLocation entity) {
        ProgressStore store = ProgressStore.get(player.server);
        Map<ResourceLocation, QuestProgress> changes = new HashMap<>();
        for (Quest quest : active(player)) {
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
        if (payload.action() == QuestActionPayload.SUBMIT_ALL) {
            List.copyOf(active(player)).stream().filter(open -> canSubmitFully(player, open)).forEach(open -> submit(player, open));
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
        if (quest == null) {
            return;
        }
        switch (payload.action()) {
            case QuestActionPayload.SUBMIT -> submit(player, quest);
            case QuestActionPayload.CHECK -> check(player, quest, payload.value());
            case QuestActionPayload.CLAIM -> claim(player, quest, payload.choices());
            case QuestActionPayload.PIN -> togglePin(player, quest);
            default -> {
            }
        }
    }

    private static void readGuidePage(ServerPlayer player, ResourceLocation guideId, int index) {
        Guide guide = book().guides().get(guideId);
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
            }
        }
        return any;
    }

    /** Choice rewards need a decision, so quests with one are left for the player to open. */
    private static void claimAll(ServerPlayer player) {
        Function<ResourceLocation, QuestProgress> progress = lookup(player);
        book().quests().values().stream()
                .filter(quest -> QuestLogic.status(quest, progress) == QuestStatus.COMPLETED)
                .filter(quest -> quest.rewards().stream().noneMatch(ChoiceReward.class::isInstance))
                .toList()
                .forEach(quest -> claim(player, quest, List.of()));
    }

    private static void togglePin(ServerPlayer player, Quest quest) {
        ProgressStore store = ProgressStore.get(player.server);
        List<ResourceLocation> pinned = new ArrayList<>(store.pinned(player.getUUID()));
        if (!pinned.remove(quest.id()) && pinned.size() + store.pinnedWays(player.getUUID()).size() < book().meta().hud().maxPinned()) {
            pinned.add(quest.id());
        }
        store.setPinned(player.getUUID(), pinned);
        PacketDistributor.sendToPlayer(player, new PinnedPayload(List.copyOf(pinned), store.pinnedWays(player.getUUID())));
    }

    /** Quests and ways share the player's limit of pinned cards. */
    public static void togglePinnedWay(ServerPlayer player, String key) {
        if (MatrixSection.findPinned(book(), key).isEmpty()) {
            return;
        }
        ProgressStore store = ProgressStore.get(player.server);
        List<String> ways = new ArrayList<>(store.pinnedWays(player.getUUID()));
        if (!ways.remove(key) && ways.size() + store.pinned(player.getUUID()).size() < book().meta().hud().maxPinned()) {
            ways.add(key);
        }
        store.setPinnedWays(player.getUUID(), ways);
        PacketDistributor.sendToPlayer(player, new PinnedPayload(store.pinned(player.getUUID()), List.copyOf(ways)));
    }

    private static boolean isOpen(ServerPlayer player, Quest quest) {
        QuestStatus status = QuestLogic.status(quest, lookup(player));
        return (status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS) && stageAllows(player, quest);
    }

    private static void submit(ServerPlayer player, Quest quest) {
        if (!isOpen(player, quest) || !TeamsApi.get().can(player.getUUID(), "submit_tasks")) {
            return;
        }
        QuestProgress before = ProgressStore.get(player.server).get(team(player), quest.id());
        List<Integer> values = before.taskValues(quest.tasks().size());
        boolean changed = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            int missing = quest.tasks().get(i).target() - values.get(i);
            int taken = 0;
            if (missing > 0 && quest.tasks().get(i) instanceof ItemTask task && task.mode() == ItemTask.Mode.CONSUME) {
                taken = take(player.getInventory(), task, missing);
            } else if (missing > 0 && quest.tasks().get(i) instanceof FluidTask task && task.mode() == ItemTask.Mode.CONSUME) {
                taken = takeFluid(player.getInventory(), task, missing);
            }
            if (taken > 0) {
                values.set(i, values.get(i) + taken);
                changed = true;
            }
        }
        if (changed) {
            apply(player, Map.of(quest.id(), finish(quest, before, values)));
        }
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

    private static void claim(ServerPlayer player, Quest quest, List<Integer> choices) {
        UUID team = team(player);
        ProgressStore store = ProgressStore.get(player.server);
        QuestProgress before = viewFor(player, quest.id(), store.get(team, quest.id()));
        if (!before.completed() || !TeamsApi.get().can(player.getUUID(), "claim_rewards")) {
            return;
        }
        List<Integer> claimed = new ArrayList<>(before.claimed());
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (!claimed.contains(i) && grant(player, quest.rewards().get(i), i < choices.size() ? choices.get(i) : -1)) {
                claimed.add(i);
            }
        }
        boolean allClaimed = claimed.size() >= quest.rewards().size();
        if (allClaimed && quest.options().repeatable().isPresent()) {
            long cooldown = quest.options().repeatable().get().cooldownMinutes() * MINUTE_MS;
            store.put(player.getUUID(), team.equals(player.getUUID()) ? Map.of() : Map.of(quest.id(), QuestProgress.EMPTY));
            apply(player, Map.of(quest.id(), new QuestProgress(List.of(), 0, List.of(), cooldown > 0 ? System.currentTimeMillis() + cooldown : 0)));
            return;
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
    }

    /** @return false when the reward stays unclaimed, which only happens to a choice with no valid pick */
    private static boolean grant(ServerPlayer player, RewardDef reward, int choice) {
        switch (reward) {
            case ItemReward item -> giveItem(player, item);
            case XpReward xp -> {
                xp.levels().ifPresent(player::giveExperienceLevels);
                xp.points().ifPresent(player::giveExperiencePoints);
            }
            case CommandReward command -> runCommand(player, command);
            case StageReward stage -> Stages.grant(player, stage.stage());
            case LootTableReward loot -> rollLoot(player, loot);
            case works.repka.rysquests.reward.ScriptReward script -> ScriptHooks.get().reward(player, script.script());
            case ChoiceReward options -> {
                if (choice < 0 || choice >= options.of().size()) {
                    return false;
                }
                grant(player, options.of().get(choice), -1);
            }
            case RandomReward random -> grant(player, pick(player, random), -1);
            default -> {
            }
        }
        return true;
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

    private static void runCommand(ServerPlayer player, CommandReward reward) {
        CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput();
        player.server.getCommands().performPrefixedCommand(reward.asServer() ? source.withPermission(4) : source, reward.command());
    }

    // --- sync and admin ---

    public static void sendAll(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        Map<ResourceLocation, QuestProgress> view = new HashMap<>();
        store.team(team(player)).forEach((id, progress) -> view.put(id, viewFor(player, id, progress)));
        PacketDistributor.sendToPlayer(player, new ProgressPayload(true, view));
        PacketDistributor.sendToPlayer(player, new PinnedPayload(store.pinned(player.getUUID()), store.pinnedWays(player.getUUID())));
        PacketDistributor.sendToPlayer(player, new GuidePagesPayload(true, List.copyOf(store.guidePages(player.getUUID()))));
        TeamsApi teams = TeamsApi.get();
        int members = Math.max(teams.members(teams.teamOf(player.getUUID())).size(), store.memberCount(team(player)));
        PacketDistributor.sendToPlayer(player, new TeamInfoPayload(members));
        STAGE_LOCKS.remove(player.getUUID());
        GUIDE_LOCKS.remove(player.getUUID());
        OPEN_STAGES.remove(player.getUUID());
        refreshStageLocks(player);
    }

    public static void forget(ServerPlayer player) {
        ACTIVE.remove(team(player));
        INVENTORY_HASH.remove(player.getUUID());
        STAGE_LOCKS.remove(player.getUUID());
        GUIDE_LOCKS.remove(player.getUUID());
        OPEN_STAGES.remove(player.getUUID());
    }

    public static void setCompleted(ServerPlayer player, Quest quest, boolean completed) {
        QuestProgress progress = QuestProgress.EMPTY;
        if (completed) {
            List<Integer> values = quest.tasks().stream().map(TaskDef::target).toList();
            progress = new QuestProgress(values, System.currentTimeMillis(), List.of());
        }
        apply(player, Map.of(quest.id(), progress));
    }

    /** Forgets caches after progress was replaced from outside, such as an import or a migration. */
    public static void invalidateAll() {
        ACTIVE.clear();
        INVENTORY_HASH.clear();
    }
}
