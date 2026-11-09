package works.repka.rysquests.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.data.Quest;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;

/** "Quest complete", shown when a quest's last task is done. */
public final class QuestToast implements Toast {
    private static final long DURATION = 5000;

    private final Quest quest;

    public QuestToast(Quest quest) {
        this.quest = quest;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceVisible) {
        Theme theme = ThemeManager.get();
        theme.card(g, 0, 0, width(), height());
        quest.icon().ifPresent(icon -> g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(icon)), 8, 8));
        Draw.text(g, Component.translatable("rysquests.toast.complete"), 30, 7, theme.c("state.ok"));
        Draw.text(g, Draw.ellipsize(Component.translatable(quest.title()).getString(), width() - 36), 30, 18, theme.c("text.primary"));
        return timeSinceVisible >= DURATION ? Visibility.HIDE : Visibility.SHOW;
    }
}
