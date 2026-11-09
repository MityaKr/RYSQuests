package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Draw;

/** "What next": three recommendations, rewards waiting, and overall progress. */
final class HomeView implements BookView {
    private static final int CARD = 34;
    private static final int GAP = 4;
    private static final int WHEEL = 24;
    private static final long REFRESH_MS = 500;

    private record Pick(Quest quest, Component reason) {
    }

    private List<Pick> picks = List.of();
    private int claimable;
    private int done;
    private int total;
    private long refreshedAt;
    private int scroll;

    /** Walks the whole book, so it runs twice a second rather than every frame. */
    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - refreshedAt < REFRESH_MS) {
            return;
        }
        refreshedAt = now;
        List<Quest> open = new ArrayList<>();
        claimable = 0;
        done = 0;
        total = ClientBook.current().quests().size();
        for (Quest quest : ClientBook.current().quests().values()) {
            QuestStatus status = BookState.status(quest);
            if (status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS) {
                open.add(quest);
            }
            if (status == QuestStatus.COMPLETED) {
                claimable++;
            }
            if (status == QuestStatus.COMPLETED || status == QuestStatus.CLAIMED) {
                done++;
            }
        }
        picks = choose(open);
    }

    private static List<Pick> choose(List<Quest> open) {
        List<Pick> chosen = new ArrayList<>();
        open.stream().filter(quest -> ratio(quest) > 0).max(Comparator.comparingDouble(HomeView::ratio))
                .ifPresent(quest -> chosen.add(new Pick(quest, Component.translatable("rysquests.home.closest", Math.round(ratio(quest) * 100)))));
        open.stream().filter(quest -> chosen.stream().noneMatch(pick -> pick.quest == quest))
                .max(Comparator.comparingInt(HomeView::unlocks))
                .filter(quest -> unlocks(quest) > 0)
                .ifPresent(quest -> chosen.add(new Pick(quest, Component.translatable("rysquests.home.opens", unlocks(quest)))));
        open.stream().filter(quest -> chosen.stream().noneMatch(pick -> pick.quest == quest))
                .findFirst()
                .ifPresent(quest -> chosen.add(new Pick(quest, Component.translatable("rysquests.home.available"))));
        return chosen;
    }

    private static int unlocks(Quest quest) {
        return ClientBook.current().unlocks().getOrDefault(quest.id(), List.of()).size();
    }

    private static double ratio(Quest quest) {
        QuestProgress own = ClientProgress.get(quest.id());
        double sum = 0;
        for (int i = 0; i < quest.tasks().size(); i++) {
            sum += Math.min(1.0, (double) own.task(i) / quest.tasks().get(i).target());
        }
        return quest.tasks().isEmpty() ? 0 : sum / quest.tasks().size();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        refresh();
        ui.clip(x, y, w, h);
        int cursor = y - scroll;
        ui.text(Component.translatable("rysquests.home.title"), x, cursor, "text.primary");
        cursor += Ui.LINE + 3;
        for (Pick pick : picks) {
            cursor = renderPick(ui, pick, x, cursor, w) + GAP;
        }
        if (claimable > 0) {
            ui.theme.card(ui.g, x, cursor, w, 22);
            ui.text(Component.translatable("rysquests.home.rewards", claimable), x + 6, cursor + 7, "text.body");
            Component label = Component.translatable("rysquests.home.claim_all");
            ui.button(label, x + w - ui.font.width(label) - 18, cursor + 3, 16, ui.accentOnce(), () -> QuestKeys.sendAll(QuestActionPayload.CLAIM_ALL));
            cursor += 22 + GAP;
        }
        ui.text(Component.translatable("rysquests.home.progress"), x, cursor + 3, "text.primary");
        cursor += Ui.LINE + 6;
        ui.bar(x, cursor, w, 5, total == 0 ? 0 : (float) done / total, "state.ok");
        ui.small(done + " / " + total, x, cursor + 8, "text.muted");
        cursor += 20;
        ui.unclip();
        scroll = Mth.clamp(scroll, 0, Math.max(0, cursor + scroll - y - h));
    }

    private static int renderPick(Ui ui, Pick pick, int x, int y, int w) {
        ui.theme.card(ui.g, x, y, w, CARD);
        pick.quest.icon().map(TaskText::stackOf).ifPresent(stack -> ui.slot(stack, x + 6, y + 8, false));
        Component open = Component.translatable("rysquests.home.open");
        int buttonW = ui.font.width(open) + 14;
        int textW = w - Ui.SLOT - 16 - buttonW;
        ui.text(Draw.ellipsize(Component.translatable(pick.quest.title()).getString(), textW), x + 28, y + 7, "text.primary");
        ui.small(Draw.ellipsize(pick.reason.getString(), (int) (textW / 0.75f)), x + 28, y + 19, "text.muted");
        ui.button(open, x + w - buttonW - 5, y + 9, 16, false, () -> {
            BookState.select(pick.quest);
            BookState.view = ViewMode.FLOW;
        });
        return y + CARD;
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll -= (int) (amount * WHEEL);
    }
}
