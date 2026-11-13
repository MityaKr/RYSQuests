package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.rysquests.task.FluidTask;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.XpTask;
import works.repka.ryscore.ui.Numbers;

/**
 * What the pages of the book need to know about a quest and do with it, without drawing anything: tasks done, what it takes and what
 * the player holds of it, its rewards as items, claiming and handing in. The old quest card held these; the quest panel, "Now", the
 * list, the map, the guides and the search share them now.
 */
final class QuestFacts {
    private QuestFacts() {
    }

    static String taskName(TaskDef task) {
        String name = TaskText.name(task);
        return task instanceof ItemTask && task.target() > 1 ? name + " ×" + Numbers.compact(task.target()) : name;
    }

    static int tasksDone(Quest quest, QuestProgress own) {
        int done = 0;
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (own.completed() || own.task(i) >= quest.tasks().get(i).target()) {
                done++;
            }
        }
        return done;
    }

    /** How many quests wait for this one. */
    static int opens(Quest quest) {
        return ClientBook.current().unlocks().getOrDefault(quest.id(), List.of()).size();
    }

    /** The item a quest is about, for the recipe preview: the first task still open, or the quest's own icon. */
    static ItemStack recipeTarget(Quest quest) {
        QuestProgress own = ClientProgress.get(quest.id());
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (quest.tasks().get(i) instanceof ItemTask && own.task(i) < quest.tasks().get(i).target()) {
                ItemStack icon = TaskText.icon(quest.tasks().get(i));
                if (!icon.isEmpty()) {
                    return icon;
                }
            }
        }
        return icon(quest);
    }

    /** The quest's icon: its own, else the item of its first task. */
    static ItemStack icon(Quest quest) {
        if (quest.icon().isPresent()) {
            return TaskText.stackOf(quest.icon().get());
        }
        for (TaskDef task : quest.tasks()) {
            ItemStack stack = TaskText.icon(task);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    static List<ItemStack> rewardStacks(Quest quest, int limit) {
        List<ItemStack> stacks = new ArrayList<>();
        for (RewardDef reward : quest.rewards()) {
            collect(reward, stacks);
        }
        return Ui.firstStacks(stacks, limit);
    }

    private static void collect(RewardDef reward, List<ItemStack> stacks) {
        switch (reward) {
            case ItemReward item -> stacks.add(stack(item));
            case ChoiceReward choice -> choice.of().forEach(option -> collect(option, stacks));
            case RandomReward random -> random.of().forEach(option -> collect(option, stacks));
            default -> {
            }
        }
    }

    static ItemStack stack(ItemReward reward) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(reward.item()), reward.count());
        reward.components().ifPresent(stack::applyComponents);
        return stack;
    }

    /** Whether the task takes something with the submit button: items, fluid or experience. */
    static boolean takes(TaskDef task) {
        return task instanceof ItemTask item && item.mode() == ItemTask.Mode.CONSUME
                || task instanceof FluidTask fluid && fluid.mode() == ItemTask.Mode.CONSUME
                || task instanceof XpTask xp && xp.consume();
    }

    /** How much of what the task takes the player has now; 0 for a task that takes nothing. */
    static int canGive(TaskDef task) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        return switch (task) {
            case ItemTask item when item.mode() == ItemTask.Mode.CONSUME -> BookData.held(item);
            case FluidTask fluid when fluid.mode() == ItemTask.Mode.CONSUME -> fluid.amountIn(player.getInventory());
            case XpTask xp when xp.consume() -> xp.have(player);
            default -> 0;
        };
    }

    /** How much of what a task asks for the player holds, whether the task takes it or only looks: items, fluid, experience. */
    static int held(TaskDef task) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        return switch (task) {
            case ItemTask item -> BookData.held(item);
            case FluidTask fluid -> fluid.amountIn(player.getInventory());
            case XpTask xp -> xp.have(player);
            default -> 0;
        };
    }

    /** Everything the quest takes is at hand in full: the server's own rule for handing in all that is ready. */
    static boolean readyToSubmit(Quest quest, QuestProgress own) {
        boolean any = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            int missing = task.target() - own.task(i);
            if (!takes(task) || missing <= 0) {
                continue;
            }
            if (canGive(task) < missing) {
                return false;
            }
            any = true;
        }
        return any;
    }

    static void claim(Quest quest) {
        List<Integer> picks = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            picks.add(BookState.PICKS.getOrDefault(i, -1));
        }
        send(QuestActionPayload.CLAIM, quest, 0, picks);
        BookTallies.invalidate();
    }

    /** A choice reward still waits for a decision: the quest cannot be claimed in one press until it is made. */
    static boolean hasUnclaimedChoice(Quest quest) {
        List<Integer> claimed = ClientProgress.get(quest.id()).claimed();
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (quest.rewards().get(i) instanceof ChoiceReward && !claimed.contains(i)) {
                return true;
            }
        }
        return false;
    }

    /** Something a bulk claim can give without asking: claiming everything leaves only the choices. */
    static boolean hasUnclaimedPlain(Quest quest) {
        List<Integer> claimed = ClientProgress.get(quest.id()).claimed();
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (!(quest.rewards().get(i) instanceof ChoiceReward) && !claimed.contains(i)) {
                return true;
            }
        }
        return false;
    }

    /** A choice of the quest still has nothing picked. */
    static boolean awaitsPick(Quest quest) {
        List<Integer> claimed = ClientProgress.get(quest.id()).claimed();
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (quest.rewards().get(i) instanceof ChoiceReward choice && !claimed.contains(i)) {
                int pick = BookState.PICKS.getOrDefault(i, -1);
                if (pick < 0 || pick >= choice.of().size()) {
                    return true;
                }
            }
        }
        return false;
    }

    static void send(int action, Quest quest, int value, List<Integer> picks) {
        PacketDistributor.sendToServer(new QuestActionPayload(action, quest.id(), value, picks));
    }

    // --- where a quest stands in its chapter ---

    /** The quest's chapter, or null. */
    static Chapter chapter(Quest quest) {
        return quest.chapter().map(id -> BookData.book().chapters().get(id)).orElse(null);
    }

    /** Text without the formatting codes an imported book may carry ("§aГлава 3"): the book draws its own colors. */
    static String plain(String text) {
        String stripped = net.minecraft.ChatFormatting.stripFormatting(text);
        return stripped == null ? text : stripped;
    }

    /** The chapter's title as the book shows it. */
    static String chapterTitle(Quest quest) {
        Chapter chapter = chapter(quest);
        return chapter == null ? "" : QuestFacts.plain(Component.translatable(chapter.title()).getString());
    }

    /** The main quests of the quest's chapter in chapter order: what "шаг 7 из 15" counts. */
    static List<Quest> mains(ResourceLocation chapter) {
        return BookTallies.questsOf(chapter).stream().filter(QuestFacts::isMain).toList();
    }

    /** A quest that counts: not optional, neither a draft nor in the archive. */
    static boolean isMain(Quest quest) {
        return !quest.options().optional() && QuestLogic.isLive(quest);
    }

    /** The quest's step among the main quests of its chapter, from 1, and their count; {0, 0} for an optional quest. */
    static int[] step(Quest quest) {
        if (!isMain(quest) || quest.chapter().isEmpty()) {
            return new int[] {0, 0};
        }
        List<Quest> mains = mains(quest.chapter().get());
        return new int[] {mains.indexOf(quest) + 1, mains.size()};
    }
}
