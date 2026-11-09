package works.repka.rysquests.progress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.network.PinnedPayload;
import works.repka.rysquests.network.ProgressPayload;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.CommandReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.rysquests.reward.XpReward;
import works.repka.rysquests.task.AdvancementTask;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.KillTask;
import works.repka.rysquests.task.StatTask;
import works.repka.rysquests.task.VisitTask;

/** Server-side quest rules. Runs on the main thread only. */
public final class ProgressService {
    private static final int POLL_INTERVAL = 20;

    /** Open, unlocked quests per team, so that polling does not walk the whole book. */
    private static final Map<UUID, List<Quest>> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> INVENTORY_HASH = new HashMap<>();
    private static QuestBook activeBook;

    private ProgressService() {
    }

    /** In solo mode a team is the player. */
    private static UUID team(ServerPlayer player) {
        return player.getUUID();
    }

    private static QuestBook book() {
        return BookHolder.current().book();
    }

    private static Function<ResourceLocation, QuestProgress> lookup(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        UUID team = team(player);
        return id -> store.get(team, id);
    }

    // --- polling ---

    public static void tick(ServerPlayer player) {
        if ((player.getId() + player.server.getTickCount()) % POLL_INTERVAL != 0) {
            return;
        }
        UUID team = team(player);
        List<Quest> active = active(player);
        int hash = inventoryHash(player.getInventory());
        boolean inventoryChanged = !Objects.equals(INVENTORY_HASH.put(team, hash), hash);
        Map<ResourceLocation, QuestProgress> changes = new HashMap<>();
        for (Quest quest : active) {
            poll(player, quest, inventoryChanged).ifPresent(updated -> changes.put(quest.id(), updated));
        }
        apply(player, changes);
    }

    private static List<Quest> active(ServerPlayer player) {
        if (activeBook != book()) {
            activeBook = book();
            ACTIVE.clear();
            INVENTORY_HASH.clear();
        }
        return ACTIVE.computeIfAbsent(team(player), key -> {
            Function<ResourceLocation, QuestProgress> progress = lookup(player);
            return activeBook.quests().values().stream()
                    .filter(quest -> !progress.apply(quest.id()).completed() && QuestLogic.isUnlocked(quest, progress))
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
            case StatTask stat -> Math.max(current, readStat(player, stat));
            case AdvancementTask advancement -> isEarned(player, advancement) ? 1 : current;
            case VisitTask visit -> isVisiting(player, visit) ? 1 : current;
            default -> current;
        };
    }

    private static QuestProgress finish(Quest quest, QuestProgress before, List<Integer> values) {
        boolean done = true;
        for (int i = 0; i < quest.tasks().size(); i++) {
            done &= values.get(i) >= quest.tasks().get(i).target();
        }
        long completedAt = done ? System.currentTimeMillis() : before.completedAt();
        return new QuestProgress(List.copyOf(values), completedAt, before.claimed());
    }

    private static void apply(ServerPlayer player, Map<ResourceLocation, QuestProgress> changes) {
        if (changes.isEmpty()) {
            return;
        }
        ProgressStore.get(player.server).put(team(player), changes);
        ACTIVE.remove(team(player));
        PacketDistributor.sendToPlayer(player, new ProgressPayload(false, Map.copyOf(changes)));
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
        return task.structure().map(structure -> isInside(player, structure)).orElse(true);
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

    /** "Ready" means every consumed item is at hand, so that a bulk hand-in never leaves a quest half paid. */
    private static boolean canSubmitFully(ServerPlayer player, Quest quest) {
        QuestProgress progress = ProgressStore.get(player.server).get(team(player), quest.id());
        boolean any = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (quest.tasks().get(i) instanceof ItemTask task && task.mode() == ItemTask.Mode.CONSUME && progress.task(i) < task.count()) {
                if (countIn(player.getInventory(), task) < task.count() - progress.task(i)) {
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
        if (!pinned.remove(quest.id()) && pinned.size() < book().meta().hud().maxPinned()) {
            pinned.add(quest.id());
        }
        store.setPinned(player.getUUID(), pinned);
        PacketDistributor.sendToPlayer(player, new PinnedPayload(List.copyOf(pinned)));
    }

    private static boolean isOpen(ServerPlayer player, Quest quest) {
        QuestStatus status = QuestLogic.status(quest, lookup(player));
        return status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
    }

    private static void submit(ServerPlayer player, Quest quest) {
        if (!isOpen(player, quest)) {
            return;
        }
        QuestProgress before = ProgressStore.get(player.server).get(team(player), quest.id());
        List<Integer> values = before.taskValues(quest.tasks().size());
        boolean changed = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (quest.tasks().get(i) instanceof ItemTask task && task.mode() == ItemTask.Mode.CONSUME && values.get(i) < task.count()) {
                int taken = take(player.getInventory(), task, task.count() - values.get(i));
                if (taken > 0) {
                    values.set(i, values.get(i) + taken);
                    changed = true;
                }
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
        if (!isOpen(player, quest) || index < 0 || index >= quest.tasks().size()
                || !(quest.tasks().get(index) instanceof works.repka.rysquests.task.CheckboxTask)) {
            return;
        }
        QuestProgress before = ProgressStore.get(player.server).get(team(player), quest.id());
        List<Integer> values = before.taskValues(quest.tasks().size());
        values.set(index, 1);
        apply(player, Map.of(quest.id(), finish(quest, before, values)));
    }

    private static void claim(ServerPlayer player, Quest quest, List<Integer> choices) {
        QuestProgress before = ProgressStore.get(player.server).get(team(player), quest.id());
        if (!before.completed()) {
            return;
        }
        List<Integer> claimed = new ArrayList<>(before.claimed());
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (!claimed.contains(i) && grant(player, quest.rewards().get(i), i < choices.size() ? choices.get(i) : -1)) {
                claimed.add(i);
            }
        }
        boolean repeat = quest.options().repeatable().isPresent() && claimed.size() >= quest.rewards().size();
        apply(player, Map.of(quest.id(), repeat ? QuestProgress.EMPTY : new QuestProgress(before.tasks(), before.completedAt(), List.copyOf(claimed))));
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
        PacketDistributor.sendToPlayer(player, new ProgressPayload(true, Map.copyOf(ProgressStore.get(player.server).team(team(player)))));
        PacketDistributor.sendToPlayer(player, new PinnedPayload(ProgressStore.get(player.server).pinned(player.getUUID())));
    }

    public static void forget(ServerPlayer player) {
        ACTIVE.remove(team(player));
        INVENTORY_HASH.remove(team(player));
    }

    public static void setCompleted(ServerPlayer player, Quest quest, boolean completed) {
        QuestProgress progress = QuestProgress.EMPTY;
        if (completed) {
            List<Integer> values = quest.tasks().stream().map(TaskDef::target).toList();
            progress = new QuestProgress(values, System.currentTimeMillis(), List.of());
        }
        apply(player, Map.of(quest.id(), progress));
    }

    /** Replaces a team's progress, for imports. Online members are resynced by the caller. */
    public static void invalidateAll() {
        ACTIVE.clear();
        INVENTORY_HASH.clear();
    }
}
