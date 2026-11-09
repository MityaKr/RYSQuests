package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import works.repka.rysquests.reward.StageReward;
import works.repka.rysquests.reward.XpReward;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.ItemTask;
import works.repka.ryscore.ui.Icons;

/** The quest card beside the map, from {@code design/Main.dc.html}, and what the flow shares with it. */
final class QuestCard {
    private static final int PAD = 18;
    private static final int MAX_SLOTS = 4;

    private QuestCard() {
    }

    // --- shared with the flow ---

    static String taskName(TaskDef task) {
        String name = TaskText.describe(task).getString();
        return task instanceof ItemTask && task.target() > 1 ? name + " ×" + task.target() : name;
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

    static int opens(Quest quest) {
        return ClientBook.current().unlocks().getOrDefault(quest.id(), List.of()).size();
    }

    static String flowSummary(Quest quest, QuestProgress own, QuestStatus status) {
        if (status == QuestStatus.LOCKED) {
            return BookScreen.waitingFor(quest).getString();
        }
        String key = status == QuestStatus.CLAIMED ? "rysquests.flow.summary_done" : "rysquests.flow.summary";
        return Component.translatable(key, tasksDone(quest, own), quest.tasks().size(), Ui.counted("rysquests.plural.quest", opens(quest))).getString();
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
        return quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
    }

    static void pinButton(Ui ui, Quest quest, float x, float y, float size, float radius) {
        boolean pinned = ClientProgress.pinned().contains(quest.id());
        boolean over = ui.over(x, y, size, size);
        ui.box(x, y, size, size, radius, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        int color = ui.c(pinned ? "accent" : "button.fg");
        float glyph = 11;
        float gx = x + (size - glyph) / 2f;
        float gy = y + (size - glyph) / 2f;
        ui.stroke(gx, gy, glyph, glyph, 1, color);
        ui.rect(gx + 3, gy + 3, glyph - 6, glyph - 6, color);
        ui.hit(x, y, size, size, () -> send(QuestActionPayload.PIN, quest, 0, List.of()));
        ui.tooltipIfOver(x, y, size, size, Component.translatable(pinned ? "rysquests.book.unpin" : "rysquests.book.pin"));
    }

    private sealed interface Part {
    }

    private record Slot(ItemStack stack, int count, int pickReward, int pickOption) implements Part {
    }

    private record Words(String text, String token) implements Part {
    }

    /** The rewards as the flow's single line reads them. */
    private static List<Part> parts(Quest quest) {
        List<Part> parts = new ArrayList<>();
        int stacks = 0;
        boolean named = quest.rewards().size() <= 2;
        for (int index = 0; index < quest.rewards().size(); index++) {
            RewardDef reward = quest.rewards().get(index);
            switch (reward) {
                case ItemReward item -> {
                    if (stacks++ < MAX_SLOTS) {
                        ItemStack stack = stack(item);
                        parts.add(new Slot(stack, item.count(), -1, -1));
                        if (named) {
                            parts.add(new Words(item.count() + " " + stack.getHoverName().getString(), "text.muted"));
                        }
                    }
                }
                case XpReward xp -> parts.add(new Words(xpText(xp), "text.muted"));
                case ChoiceReward choice -> {
                    int option = 0;
                    for (RewardDef candidate : choice.of()) {
                        if (candidate instanceof ItemReward item && stacks++ < MAX_SLOTS) {
                            if (option > 0) {
                                parts.add(new Words(Component.translatable("rysquests.flow.or").getString(), "text.dim"));
                            }
                            ItemStack stack = stack(item);
                            parts.add(new Slot(stack, item.count(), index, option));
                            parts.add(new Words(item.count() + " " + stack.getHoverName().getString(), "text.muted"));
                        }
                        option++;
                    }
                    parts.add(new Words(Component.translatable("rysquests.flow.pick_later").getString(), "text.dim"));
                }
                case RandomReward random -> {
                    for (RewardDef candidate : random.of()) {
                        if (candidate instanceof ItemReward item && stacks++ < MAX_SLOTS) {
                            parts.add(new Slot(stack(item), item.count(), -1, -1));
                        }
                    }
                    parts.add(new Words(Component.translatable("rysquests.flow.random_later").getString(), "text.dim"));
                }
                case StageReward stage -> parts.add(new Words(Component.translatable("rysquests.reward.stage", stage.stage()).getString(), "text.muted"));
                default -> {
                }
            }
        }
        return parts;
    }

    private static String xpText(XpReward xp) {
        return xp.levels().isPresent()
                ? Ui.counted("rysquests.plural.level", xp.levels().get())
                : Component.translatable("rysquests.reward.points", xp.points().orElse(0)).getString();
    }

    static float flowRewards(Ui ui, Quest quest, QuestProgress own, QuestStatus status, float x, float y, float w) {
        List<Part> parts = new ArrayList<>(parts(quest));
        if (parts.isEmpty() && status != QuestStatus.COMPLETED) {
            return y - 14;
        }
        parts.addFirst(new Words(Component.translatable("rysquests.flow.reward").getString(), "text.muted"));
        String claim = status == QuestStatus.COMPLETED ? Component.translatable("rysquests.book.claim_short").getString() : "";
        float claimW = claim.isEmpty() ? 0 : ui.buttonWidth(claim, 13, Ui.SEMIBOLD, 14);
        float room = w - (claim.isEmpty() ? 0 : claimW + 10);
        while (parts.size() > 1 && minRow(ui, parts) > room) {
            parts.removeLast();
        }
        float[] base = new float[parts.size()];
        float[] min = new float[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i) instanceof Words words) {
                base[i] = ui.width(words.text, 13, Ui.REGULAR);
                min[i] = ui.minWidth(words.text, 13, Ui.REGULAR);
            } else {
                base[i] = 32;
                min[i] = 32;
            }
        }
        float[] widths = Ui.shrink(room, 10, base, min);
        float height = 32;
        List<List<String>> wrapped = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            List<String> lines = parts.get(i) instanceof Words words ? ui.wrap(words.text, widths[i] + 1, 13, Ui.REGULAR, 3) : List.of();
            wrapped.add(lines);
            height = Math.max(height, lines.size() * 16);
        }
        float cy = y + height / 2f;
        float pen = x;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part instanceof Words words) {
                ui.lines(wrapped.get(i), pen, cy, 16, 13, Ui.REGULAR, ui.c(words.token));
            } else if (part instanceof Slot slot) {
                boolean picked = slot.pickReward >= 0 && BookState.PICKS.getOrDefault(slot.pickReward, -1) == slot.pickOption;
                ui.cell(slot.stack, pen, cy - 16, 32, 32, 7, 18, ui.c("slot.bg"), picked ? ui.c("accent") : ui.c("card.border"));
                ui.count(slot.count, pen + 29, cy + 15);
                if (slot.pickReward >= 0) {
                    ui.hit(pen, cy - 16, 32, 32, () -> BookState.PICKS.put(slot.pickReward, slot.pickOption));
                }
            }
            pen += widths[i] + 10;
        }
        if (!claim.isEmpty()) {
            ui.button(claim, x + w - claimW, cy - 17, claimW, 34, 9, 13, Ui.SEMIBOLD, 14, Ui.Style.ACCENT, () -> claim(quest));
        }
        return y + height;
    }

    /** The narrowest the reward line can get: what still does not fit is left out. */
    private static float minRow(Ui ui, List<Part> parts) {
        float total = (parts.size() - 1) * 10f;
        for (Part part : parts) {
            total += part instanceof Words words ? ui.minWidth(words.text, 13, Ui.REGULAR) : 32;
        }
        return total;
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

    static void claim(Quest quest) {
        List<Integer> picks = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            picks.add(BookState.PICKS.getOrDefault(i, -1));
        }
        send(QuestActionPayload.CLAIM, quest, 0, picks);
    }

    static void send(int action, Quest quest, int value, List<Integer> picks) {
        PacketDistributor.sendToServer(new QuestActionPayload(action, quest.id(), value, picks));
    }

    // --- the card beside the map ---

    /** @return the height used */
    static int render(Ui ui, Quest quest, int x, int y, int w) {
        QuestProgress own = ClientProgress.get(quest.id());
        QuestStatus status = BookState.status(quest);
        boolean open = status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
        float left = x + PAD;
        float inner = w - PAD * 2;
        float cursor = header(ui, quest, status, left, y + 16, inner) + 12 + 4;

        String tasks = Component.translatable("rysquests.card.tasks").getString();
        if (!quest.tasks().isEmpty()) {
            ui.caps(tasks, left, cursor + 7, ui.c("text.dim"));
            ui.capsRight(tasksDone(quest, own) + " / " + quest.tasks().size(), left + inner, cursor + 7, ui.c("text.dim"));
            cursor += 14 + 6;
            for (int i = 0; i < quest.tasks().size(); i++) {
                cursor = taskRow(ui, quest, i, own, open, left, cursor, inner) + (i + 1 < quest.tasks().size() ? 6 : 0);
            }
            cursor += 14;
        }
        cursor = rewards(ui, quest, status, left, cursor, inner);
        cursor = textBlock(ui, quest, left, cursor, inner);
        actions(ui, quest, left, cursor);
        cursor += 34 + 18;
        return Math.round(cursor - y);
    }

    private static float header(Ui ui, Quest quest, QuestStatus status, float x, float y, float w) {
        float textW = w - 46 - 12 - 12 - 34;
        String sub = subtitle(quest, status);
        List<String> subLines = ui.wrap(sub, textW, 11.5f, Ui.REGULAR, 3);
        float blockH = 23 + 2 + subLines.size() * 14;
        float h = Math.max(46, blockH);
        float cy = y + h / 2f;
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.cell(icon, x, cy - 23, 46, 46, 10, 28, ui.c("slot.bg"), ui.c("card.border"));
        float textX = x + 46 + 12;
        ui.labelFit(Component.translatable(quest.title()).getString(), textX, y + 11.5f, textW, 18, Ui.DISPLAY, ui.c("text.primary"));
        for (int i = 0; i < subLines.size(); i++) {
            ui.label(subLines.get(i), textX, y + 25 + 7 + i * 14, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        pinButton(ui, quest, x + w - 34, cy - 17, 34, 9);
        return y + h;
    }

    private static String subtitle(Quest quest, QuestStatus status) {
        List<String> parts = new ArrayList<>();
        parts.add(Component.translatable("rysquests.card.chapter", Ui.roman(BookScreen.chapterNumber(quest.chapter().orElse(null)))).getString());
        List<String> needs = new ArrayList<>();
        for (ResourceLocation id : quest.requires()) {
            Quest required = ClientBook.current().quests().get(id);
            if (required != null) {
                needs.add(Component.translatable(required.title()).getString());
            }
        }
        if (!needs.isEmpty()) {
            parts.add(Component.translatable("rysquests.card.requires", String.join(", ", needs)).getString());
        }
        if (opens(quest) > 0) {
            parts.add(Component.translatable("rysquests.card.opens", opens(quest)).getString());
        }
        return String.join(" · ", parts);
    }

    private static float taskRow(Ui ui, Quest quest, int index, QuestProgress own, boolean open, float x, float y, float w) {
        TaskDef task = quest.tasks().get(index);
        int value = Math.min(own.task(index), task.target());
        boolean done = own.completed() || value >= task.target();
        float h = 47;
        float cy = y + h / 2f;
        boolean active = !done && (index == 0 || own.task(index - 1) >= quest.tasks().get(index - 1).target());
        ui.box(x, y, w, h, 9, ui.c("status.bg"), active ? ui.warmBorder(0.19f) : ui.c("status.border"));
        ui.box(x + 11, cy - 10, 20, 20, 5, done ? ui.c("state.ok") : ui.c("slot.bg"), ui.c("card.border"));
        if (done) {
            ui.check(x + 15, cy - 6, 12, ui.c("accent.text"));
        }
        ItemStack icon = TaskText.icon(task);
        float textX = x + 41;
        if (!icon.isEmpty()) {
            ui.item(icon, x + 41, cy - 10, 20);
            ui.interactive(icon, x + 41, cy - 10, 20, 20);
            textX += 30;
        }
        boolean submit = open && !done && task instanceof ItemTask item && item.mode() == ItemTask.Mode.CONSUME && BookScreen.held(item) > 0;
        String submitText = Component.translatable("rysquests.book.submit").getString();
        float buttonW = submit ? ui.buttonWidth(submitText, 11.5f, Ui.SEMIBOLD, 10) : 0;
        float textW = x + w - 11 - (submit ? buttonW + 8 : 0) - textX;
        ui.labelFit(taskName(task), textX, y + 16.5f, textW, 12.5f, Ui.REGULAR, ui.nameFg());
        ui.labelFit(taskLine(task, value, done), textX, y + 31, textW, 11, Ui.REGULAR, ui.c(done ? "state.ok" : "accent"));
        if (submit) {
            ui.button(submitText, x + w - 11 - buttonW, cy - 14, buttonW, 28, 7, 11.5f, Ui.SEMIBOLD, 10, Ui.Style.ACCENT, () -> send(QuestActionPayload.SUBMIT, quest, 0, List.of()));
        }
        if (task instanceof CheckboxTask && open && !done) {
            ui.hit(x, y, w, h, () -> send(QuestActionPayload.CHECK, quest, index, List.of()));
        }
        return y + h;
    }

    private static String taskLine(TaskDef task, int value, boolean done) {
        if (!(task instanceof ItemTask item)) {
            return done ? Component.translatable("rysquests.flow.received").getString() : value + " / " + task.target();
        }
        String count = value + " / " + task.target();
        if (done) {
            return item.mode() == ItemTask.Mode.CONSUME ? count + " · " + Component.translatable("rysquests.flow.submitted").getString() : count;
        }
        int short_ = task.target() - value - BookScreen.held(item);
        return short_ > 0 ? Component.translatable("rysquests.card.short", count, short_).getString() : Component.translatable("rysquests.card.ready", count).getString();
    }

    private static float rewards(Ui ui, Quest quest, QuestStatus status, float x, float y, float w) {
        List<Slot> slots = new ArrayList<>();
        List<String> words = new ArrayList<>();
        int choice = -1;
        int options = 0;
        for (int index = 0; index < quest.rewards().size(); index++) {
            RewardDef reward = quest.rewards().get(index);
            switch (reward) {
                case ItemReward item -> slots.add(new Slot(stack(item), item.count(), -1, -1));
                case XpReward xp -> words.add("+ " + xpText(xp));
                case StageReward stage -> words.add("+ " + Component.translatable("rysquests.reward.stage", stage.stage()).getString());
                case ChoiceReward picks -> {
                    choice = index;
                    options = picks.of().size();
                    int option = 0;
                    for (RewardDef candidate : picks.of()) {
                        if (candidate instanceof ItemReward item) {
                            slots.add(new Slot(stack(item), item.count(), index, option));
                        }
                        option++;
                    }
                }
                case RandomReward random -> random.of().forEach(candidate -> {
                    if (candidate instanceof ItemReward item) {
                        slots.add(new Slot(stack(item), item.count(), -1, -1));
                    }
                });
                default -> {
                }
            }
        }
        if (slots.isEmpty() && words.isEmpty()) {
            return y;
        }
        ui.caps(Component.translatable("rysquests.card.rewards").getString(), x, y + 7, ui.c("text.dim"));
        if (choice >= 0) {
            ui.capsRight(Component.translatable("rysquests.card.pick", 1, options).getString(), x + w, y + 7, ui.c("text.dim"));
        }
        float rowY = y + 14 + 6;
        float h = 48;
        float cy = rowY + h / 2f;
        float pen = x;
        int perRow = Math.max(1, Math.min(slots.size(), (int) ((w + 8) / 54f)));
        String claimText = status == QuestStatus.COMPLETED ? Component.translatable("rysquests.book.claim_short").getString() : "";
        float claimW = claimText.isEmpty() ? 0 : ui.buttonWidth(claimText, 11.5f, Ui.SEMIBOLD, 10);
        int limit = Math.min(MAX_SLOTS + 1, perRow);
        for (int i = 0; i < Math.min(limit, slots.size()); i++) {
            Slot slot = slots.get(i);
            boolean picked = slot.pickReward >= 0 && BookState.PICKS.getOrDefault(slot.pickReward, -1) == slot.pickOption;
            float size = picked ? 48 : 46;
            float sy = cy - size / 2f;
            ui.cell(slot.stack, pen, sy, size, size, 9, 24, ui.c("slot.bg"), picked ? 0 : ui.c("card.border"));
            if (picked) {
                ui.strokeBold(pen, sy, size, size, 9, ui.c("accent"));
            }
            ui.count(slot.count, pen + size - 5, sy + size - 3);
            if (slot.pickReward >= 0) {
                ui.hit(pen, sy, size, size, () -> BookState.PICKS.put(slot.pickReward, slot.pickOption));
            }
            pen += size + 8;
        }
        if (!claimText.isEmpty()) {
            ui.button(claimText, x + w - claimW, cy - 14, claimW, 28, 7, 11.5f, Ui.SEMIBOLD, 10, Ui.Style.ACCENT, () -> claim(quest));
        }
        if (!words.isEmpty()) {
            float textX = pen + 4;
            float textW = x + w - (claimText.isEmpty() ? 0 : claimW + 8) - textX;
            ui.lines(ui.wrap(String.join(" · ", words), textW, 11.5f, Ui.REGULAR, 3), textX, cy, 14, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        return rowY + h + 14;
    }

    private static float textBlock(Ui ui, Quest quest, float x, float y, float w) {
        boolean hasText = quest.text().isPresent();
        ItemStack target = recipeTarget(quest);
        RecipePreview.Shown recipe = RecipePreview.of(target);
        if (!hasText && recipe == null) {
            return y;
        }
        float h = ui.measure(() -> Math.round(textBlockBody(ui, quest, recipe, x, 0, w)));
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("status.border"));
        textBlockBody(ui, quest, recipe, x, y, w);
        return y + h + 14;
    }

    private static float textBlockBody(Ui ui, Quest quest, RecipePreview.Shown recipe, float x, float y, float w) {
        float left = x + 13;
        float inner = w - 26;
        float cursor = y + 13;
        if (quest.text().isPresent()) {
            cursor = TextBlock.render(ui, Component.translatable(quest.text().get()).getString(), left, cursor, inner,
                    new TextBlock.Look(12.5f, 18.75f, ui.c("button.fg")), Recipes::showRecipes);
        }
        if (recipe != null) {
            if (quest.text().isPresent()) {
                cursor += 8;
            }
            ui.box(left, cursor, inner, 54, 8, ui.c("slot.bg"), ui.c("slot.border"));
            float cy = cursor + 27;
            String viewer = RecipePreview.viewerName();
            ui.label(viewer, left + 9, cy, 10.5f, Ui.REGULAR, ui.c("text.dim"));
            float pen = left + 9 + ui.width(viewer, 10.5f, Ui.REGULAR) + 10;
            String graph = Component.translatable("rysquests.card.graph").getString();
            float graphW = ui.buttonWidth(graph, 11, Ui.REGULAR, 9) + 2;
            float room = left + inner - 9 - graphW - 8 - 16 - 11 - 36 - pen;
            int cells = Math.max(1, (int) ((room + 6) / 36f));
            for (int i = 0; i < Math.min(cells, recipe.inputs().size()); i++) {
                ItemStack input = recipe.inputs().get(i);
                ui.cell(input, pen, cy - 15, 30, 30, 6, 18, ui.c("panel.bg"), ui.c("panel.bg"));
                pen += 36;
            }
            ui.icon(Icons.Shape.ARROW, pen, cy - 6, 12, ui.c("text.dim"));
            pen += 16.8f;
            ui.cell(recipe.output(), pen, cy - 18, 36, 36, 7, 20, ui.c("panel.bg"), ui.c("card.border"));
            ui.button(graph, left + inner - 9 - graphW, cy - 13, graphW, 26, 7, 11, Ui.REGULAR, 9, Ui.Style.PLAIN, () -> Recipes.showRecipes(recipe.output()));
            cursor += 54;
        }
        return cursor + 13 - y;
    }

    private static void actions(Ui ui, Quest quest, float x, float y) {
        String deps = Component.translatable("rysquests.card.dependencies").getString();
        String chat = Component.translatable("rysquests.card.share").getString();
        float depsW = ui.buttonWidth(deps, 12, Ui.REGULAR, 14) + 2;
        float chatW = ui.buttonWidth(chat, 12, Ui.REGULAR, 14) + 2;
        ui.button(deps, x, y, depsW, 34, 9, 12, Ui.REGULAR, 14, Ui.Style.PLAIN, () -> {
            if (Minecraft.getInstance().screen instanceof BookScreen screen) {
                screen.focusOnMap();
            }
        });
        ui.button(chat, x + depsW + 8, y, chatW, 34, 9, 12, Ui.REGULAR, 14, Ui.Style.PLAIN, () -> Minecraft.getInstance().setScreen(
                new ChatScreen("[[quest:" + quest.id() + "]]")));
    }
}
