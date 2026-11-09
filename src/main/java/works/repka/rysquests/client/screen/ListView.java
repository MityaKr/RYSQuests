package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Draw;

/** The open chapter as a plain list grouped by status; rows outside the view are not drawn. */
final class ListView implements BookView {
    private static final int ROW = 20;
    private static final int HEADING = 14;
    private static final int TOOLBAR = 20;
    private static final int WHEEL = 24;
    private static final QuestStatus[] ORDER = {QuestStatus.COMPLETED, QuestStatus.IN_PROGRESS, QuestStatus.AVAILABLE, QuestStatus.LOCKED, QuestStatus.CLAIMED};

    private final BookScreen screen;
    private boolean hideFinished;
    private int scroll;

    ListView(BookScreen screen) {
        this.screen = screen;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        Component toggle = Component.translatable(hideFinished ? "rysquests.list.show_finished" : "rysquests.list.hide_finished");
        ui.button(toggle, x, y, 16, false, () -> hideFinished = !hideFinished);
        Map<QuestStatus, List<Quest>> groups = new EnumMap<>(QuestStatus.class);
        for (Quest quest : screen.visibleQuests()) {
            groups.computeIfAbsent(BookState.status(quest), key -> new ArrayList<>()).add(quest);
        }
        int listY = y + TOOLBAR;
        int listH = h - TOOLBAR;
        ui.clip(x, listY, w, listH);
        int cursor = listY - scroll;
        for (QuestStatus status : ORDER) {
            List<Quest> quests = groups.getOrDefault(status, List.of());
            if (quests.isEmpty() || hideFinished && status == QuestStatus.CLAIMED) {
                continue;
            }
            if (cursor + HEADING > listY && cursor < listY + listH) {
                ui.small(Component.translatable("rysquests.status." + status.name().toLowerCase(java.util.Locale.ROOT)).getString() + " · " + quests.size(), x + 2, cursor + 3, "text.dim");
            }
            cursor += HEADING;
            for (Quest quest : quests) {
                if (cursor + ROW > listY && cursor < listY + listH) {
                    renderRow(ui, quest, status, x, cursor, w);
                }
                cursor += ROW;
            }
        }
        ui.unclip();
        scroll = Mth.clamp(scroll, 0, Math.max(0, cursor + scroll - listY - listH));
    }

    private static void renderRow(Ui ui, Quest quest, QuestStatus status, int x, int y, int w) {
        int rowH = ROW - 2;
        if (ui.over(x, y, w, rowH) || quest.id().equals(BookState.quest)) {
            ui.theme.card(ui.g, x, y, w, rowH);
        }
        quest.icon().map(TaskText::stackOf).ifPresent(stack -> ui.plated(stack, x + 2, y + 1));
        String token = switch (status) {
            case LOCKED -> "text.dim";
            case CLAIMED -> "state.ok";
            default -> "text.primary";
        };
        ui.text(Draw.ellipsize(Component.translatable(quest.title()).getString(), w - 26), x + 21, y + 5, token);
        ui.hit(x, y, w, rowH, () -> BookState.select(quest));
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll -= (int) (amount * WHEEL);
    }
}
