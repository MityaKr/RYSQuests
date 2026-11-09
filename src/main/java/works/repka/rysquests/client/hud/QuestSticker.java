package works.repka.rysquests.client.hud;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.TaskText;
import works.repka.rysquests.client.screen.Ui;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.ryscore.ui.Draw;

/** A pinned quest, from {@code design/Hud.dc.html}: its tasks and a progress bar. */
final class QuestSticker implements Sticker {
    private static final int PAD_TOP = 13;
    private static final int PAD_LEFT = 17;
    private static final int PAD_RIGHT = 15;
    private static final int HEAD = 22;
    private static final int GAP = 8;
    private static final int ROW_GAP = 4;
    private static final int ROW_DONE = 15;
    private static final int ROW_OPEN = 16;
    private static final int BAR = 4;
    private static final int PAD_BOTTOM = 13;

    private final Quest quest;

    QuestSticker(Quest quest) {
        this.quest = quest;
    }

    @Override
    public String id() {
        return quest.id().toString();
    }

    private static boolean done(TaskDef task, int index, QuestProgress own) {
        return own.task(index) >= task.target();
    }

    @Override
    public int height(Ui ui, int width) {
        QuestProgress own = ClientProgress.get(quest.id());
        List<TaskDef> tasks = quest.tasks();
        int rows = 0;
        for (int i = 0; i < tasks.size(); i++) {
            rows += (done(tasks.get(i), i, own) ? ROW_DONE : ROW_OPEN) + (i + 1 < tasks.size() ? ROW_GAP : 0);
        }
        return PAD_TOP + HEAD + GAP + rows + GAP + BAR + PAD_BOTTOM;
    }

    @Override
    public void render(Ui ui, int x, int y, int width) {
        int h = height(ui, width);
        ui.shadow(x, y, width, h, 12, 0.4f);
        ui.box(x, y, width, h, 12, Draw.alpha(ui.c("hud.bg"), 0.92f), ui.c("hud.border"));
        ui.fill(x, y + 5, 3, h - 10, 1.5f, ui.c("accent"));
        float left = x + PAD_LEFT;
        float inner = width - PAD_LEFT - PAD_RIGHT;
        float headCy = y + PAD_TOP + HEAD / 2f;
        quest.icon().ifPresent(icon -> ui.item(new ItemStack(BuiltInRegistries.ITEM.get(icon)), left, headCy - 11, 22));
        QuestProgress own = ClientProgress.get(quest.id());
        int total = quest.tasks().size();
        int finished = 0;
        for (int i = 0; i < total; i++) {
            finished += done(quest.tasks().get(i), i, own) ? 1 : 0;
        }
        String count = finished + " / " + total;
        float countW = ui.width(count, 11, Ui.REGULAR);
        ui.labelRight(count, left + inner, headCy, 11, Ui.REGULAR, muted(ui));
        ui.labelFit(Component.translatable(quest.title()).getString(), left + 30, headCy, inner - 30 - countW - 6, 13, Ui.SEMIBOLD, primary(ui));
        float cursor = y + PAD_TOP + HEAD + GAP;
        for (int i = 0; i < total; i++) {
            TaskDef task = quest.tasks().get(i);
            int value = Math.min(own.task(i), task.target());
            boolean done = value >= task.target();
            float rowH = done ? ROW_DONE : ROW_OPEN;
            float cy = cursor + rowH / 2f;
            String name = TaskText.describe(task).getString();
            if (done) {
                ui.fill(left, cy - 7, 14, 14, 4, ui.c("state.ok"));
                ui.check(left + 2, cy - 5, 10, ui.c("accent.text"));
                ui.labelFit(task.target() > 1 ? name + " " + value + " / " + task.target() : name, left + 22, cy, inner - 22, 12, Ui.REGULAR, muted(ui));
            } else {
                ui.box(left, cy - 8, 16, 16, 4, ui.c("slot.bg"), ui.c("card.border"));
                String progress = value + " / " + task.target();
                float progressW = ui.width(progress, 12, Ui.SEMIBOLD);
                ui.labelRight(progress, left + inner, cy, 12, Ui.SEMIBOLD, ui.c("accent"));
                ui.labelFit(name, left + 24, cy, inner - 24 - progressW - 6, 12, Ui.REGULAR, ui.nameFg());
            }
            cursor += rowH + ROW_GAP;
        }
        float barY = y + h - PAD_BOTTOM - BAR;
        ui.bar(left, barY, inner, BAR, 2, total == 0 ? 0 : (float) finished / total, ui.c("accent"));
    }

    private static int primary(Ui ui) {
        return ui.c(ui.theme.beveled() ? "tooltip.fg" : "text.primary");
    }

    private static int muted(Ui ui) {
        return ui.c(ui.theme.beveled() ? "tooltip.fg" : "text.muted");
    }
}
