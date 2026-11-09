package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.rysquests.reward.XpReward;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.ItemTask;
import works.repka.ryscore.ui.Draw;

/** A quest's header, tasks and rewards. Expanded in place in the flow, and beside the map. */
final class QuestCard {
    private static final int TASK_ROW = 20;
    private static final int BUTTON = 14;
    private static final int PAD = 6;

    private QuestCard() {
    }

    /** @return the height used */
    static int render(Ui ui, Quest quest, int x, int y, int w) {
        QuestProgress own = ClientProgress.get(quest.id());
        QuestStatus status = BookState.status(quest);
        boolean open = status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
        int top = y;
        int inner = w - PAD * 2;
        int left = x + PAD;
        y += PAD;

        quest.icon().map(TaskText::stackOf).ifPresent(stack -> ui.slot(stack, left, top + PAD, false));
        int textX = left + Ui.SLOT + 4;
        int titleWidth = inner - Ui.SLOT - 4 - BUTTON - 2;
        ui.text(Draw.ellipsize(Component.translatable(quest.title()).getString(), titleWidth), textX, y, "text.primary");
        ui.small(summary(quest, own), textX, y + 10, "text.muted");
        pinButton(ui, quest, x + w - PAD - BUTTON, top + PAD);
        y += Ui.SLOT + 3;

        if (quest.subtitle().isPresent()) {
            y = ui.wrapped(Component.translatable(quest.subtitle().get()), left, y, inner, "text.muted") + 3;
        }
        for (int i = 0; i < quest.tasks().size(); i++) {
            y = taskRow(ui, quest, i, own, open, left, y, inner);
        }
        y += 2;
        for (int i = 0; i < quest.rewards().size(); i++) {
            y = reward(ui, quest.rewards().get(i), i, own.claimed().contains(i), left, y, inner);
        }
        if (status == QuestStatus.COMPLETED) {
            y += 2;
            ui.button(Component.translatable("rysquests.book.claim"), left, y, BUTTON + 2, true, () -> claim(quest));
            y += BUTTON + 4;
        }
        return y + PAD - top;
    }

    private static String summary(Quest quest, QuestProgress own) {
        int done = 0;
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (own.completed() || own.task(i) >= quest.tasks().get(i).target()) {
                done++;
            }
        }
        int opens = ClientBook.current().unlocks().getOrDefault(quest.id(), List.of()).size();
        return Component.translatable("rysquests.book.summary", done, quest.tasks().size(), opens).getString();
    }

    private static void pinButton(Ui ui, Quest quest, int x, int y) {
        boolean pinned = ClientProgress.pinned().contains(quest.id());
        ui.theme.button(ui.g, x, y, BUTTON, BUTTON, ui.over(x, y, BUTTON, BUTTON), pinned);
        Draw.round(ui.g, x + 4, y + 4, 6, 6, 1f, ui.color(pinned ? "accent.text" : "text.muted"));
        ui.hit(x, y, BUTTON, BUTTON, () -> send(QuestActionPayload.PIN, quest, 0, List.of()));
        ui.tooltipIfOver(x, y, BUTTON, BUTTON, Component.translatable(pinned ? "rysquests.book.unpin" : "rysquests.book.pin"));
    }

    private static int taskRow(Ui ui, Quest quest, int index, QuestProgress own, boolean open, int x, int y, int w) {
        TaskDef task = quest.tasks().get(index);
        int value = Math.min(own.task(index), task.target());
        boolean done = own.completed() || value >= task.target();
        int rowH = TASK_ROW - 2;
        ui.theme.card(ui.g, x, y, w, rowH);

        int boxY = y + (rowH - 10) / 2;
        Draw.roundBox(ui.g, x + 3, boxY, 10, 10, 2f, ui.color(done ? "state.ok" : "slot.bg"), ui.color("card.border"));
        ItemStack icon = TaskText.icon(task);
        ui.item(icon, x + 15, y + 1);
        if (!icon.isEmpty() && ui.over(x + 15, y + 1, 16, 16)) {
            ui.hoveredStack = icon;
        }
        if (!icon.isEmpty()) {
            ui.hit(x + 15, y + 1, 16, 16, () -> Recipes.showRecipes(icon));
        }
        int nameX = x + (icon.isEmpty() ? 16 : 33);
        boolean submit = open && !done && task instanceof ItemTask item && item.mode() == ItemTask.Mode.CONSUME;
        String count = value + " / " + task.target();
        int countW = ui.font.width(count);
        Component submitLabel = Component.translatable("rysquests.book.submit");
        int submitW = submit ? ui.font.width(submitLabel) + 14 : 0;
        int nameW = w - (nameX - x) - countW - submitW - 8;
        ui.text(Draw.ellipsize(TaskText.describe(task).getString(), nameW), nameX, y + 5, done ? "text.muted" : "text.primary");
        ui.text(count, x + w - countW - submitW - 4, y + 5, done ? "state.ok" : "accent");
        if (submit) {
            ui.button(submitLabel, x + w - submitW - 2, y + 2, BUTTON, true, () -> send(QuestActionPayload.SUBMIT, quest, 0, List.of()));
        }
        if (task instanceof CheckboxTask && open && !done) {
            ui.hit(x, y, w, rowH, () -> send(QuestActionPayload.CHECK, quest, index, List.of()));
        }
        return y + TASK_ROW;
    }

    private static int reward(Ui ui, RewardDef reward, int index, boolean claimed, int x, int y, int w) {
        String token = claimed ? "state.ok" : "text.muted";
        switch (reward) {
            case ItemReward item -> {
                ItemStack stack = stack(item);
                ui.slot(stack, x, y, false);
                ui.text(Draw.ellipsize(stack.getHoverName().getString(), w - Ui.SLOT - 4), x + Ui.SLOT + 4, y + 5, token);
                return y + Ui.SLOT + 2;
            }
            case XpReward xp -> {
                ui.text(xp.levels().isPresent()
                        ? Component.translatable("rysquests.reward.levels", xp.levels().get())
                        : Component.translatable("rysquests.reward.points", xp.points().orElse(0)), x, y + 1, token);
                return y + Ui.LINE + 2;
            }
            case ChoiceReward choice -> {
                return options(ui, choice.of(), index, "rysquests.reward.choose", true, x, y, w);
            }
            case RandomReward random -> {
                return options(ui, random.of(), index, "rysquests.reward.random", false, x, y, w);
            }
            default -> {
                return y;
            }
        }
    }

    private static int options(Ui ui, List<RewardDef> options, int index, String label, boolean pickable, int x, int y, int w) {
        ui.text(Component.translatable(label), x, y, "text.muted");
        y += Ui.LINE;
        int cell = Ui.SLOT + 2;
        int perRow = Math.max(1, w / cell);
        for (int i = 0; i < options.size(); i++) {
            int slotX = x + (i % perRow) * cell;
            int slotY = y + (i / perRow) * cell;
            if (options.get(i) instanceof ItemReward item) {
                ui.slot(stack(item), slotX, slotY, pickable && BookState.PICKS.getOrDefault(index, -1) == i);
            }
            if (pickable) {
                int option = i;
                ui.hit(slotX, slotY, Ui.SLOT, Ui.SLOT, () -> BookState.PICKS.put(index, option));
            }
        }
        return y + ((options.size() + perRow - 1) / perRow) * cell + 3;
    }

    static ItemStack stack(ItemReward reward) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(reward.item()), reward.count());
        reward.components().ifPresent(stack::applyComponents);
        return stack;
    }

    private static void claim(Quest quest) {
        List<Integer> picks = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            picks.add(BookState.PICKS.getOrDefault(i, -1));
        }
        send(QuestActionPayload.CLAIM, quest, 0, picks);
    }

    private static void send(int action, Quest quest, int value, List<Integer> picks) {
        PacketDistributor.sendToServer(new QuestActionPayload(action, quest.id(), value, picks));
    }
}
