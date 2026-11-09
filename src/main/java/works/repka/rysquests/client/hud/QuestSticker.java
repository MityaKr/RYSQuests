package works.repka.rysquests.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.TaskText;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;

/** A pinned quest: title, its tasks and a progress bar. */
final class QuestSticker implements Sticker {
    private static final int HEADER = 20;
    private static final int LINE = 9;
    private static final int FOOTER = 6;

    private final Quest quest;

    QuestSticker(Quest quest) {
        this.quest = quest;
    }

    @Override
    public String id() {
        return quest.id().toString();
    }

    @Override
    public int height(int width) {
        return HEADER + quest.tasks().size() * LINE + FOOTER;
    }

    @Override
    public void render(GuiGraphics g, int x, int y, int width) {
        Theme theme = ThemeManager.get();
        int h = height(width);
        Draw.roundBox(g, x, y, width, h, theme.beveled() ? 0 : 5f, Draw.alpha(theme.c("hud.bg"), 0.86f), theme.c("hud.border"));
        quest.icon().ifPresent(icon -> g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(icon)), x + 3, y + 2));
        int textColor = theme.beveled() ? theme.c("tooltip.fg") : theme.c("text.primary");
        int mutedColor = theme.beveled() ? theme.c("tooltip.fg") : theme.c("text.muted");
        Draw.text(g, Draw.ellipsize(Component.translatable(quest.title()).getString(), width - 26), x + 22, y + 6, textColor);

        QuestProgress progress = ClientProgress.get(quest.id());
        int total = 0;
        int done = 0;
        int lineY = y + HEADER;
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            int value = Math.min(progress.task(i), task.target());
            total += task.target();
            done += value;
            int color = value >= task.target() ? theme.c("state.ok") : mutedColor;
            String label = TaskText.describe(task).getString();
            String count = value + "/" + task.target();
            Draw.textScaled(g, Draw.ellipsize(label, (int) ((width - 34) / 0.75f)), x + 5, lineY, 0.75f, color);
            Draw.textScaled(g, count, x + width - 5 - (int) (g.guiWidth() > 0 ? net.minecraft.client.Minecraft.getInstance().font.width(count) * 0.75f : 0), lineY, 0.75f, color);
            lineY += LINE;
        }
        int barX = x + 5;
        int barW = width - 10;
        int barY = y + h - 4;
        Draw.rect(g, barX, barY, barW, 2, theme.c("slot.bg"));
        if (total > 0) {
            Draw.rect(g, barX, barY, barW * done / total, 2, theme.c("state.ok"));
        }
    }
}
