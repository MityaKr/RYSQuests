package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.ryscore.ui.Draw;

/** The whole book by epochs: each chapter as a card with its progress. */
final class TimelineView implements BookView {
    private static final int CARD_W = 104;
    private static final int CARD_H = 32;
    private static final int GAP = 4;
    private static final int HEADING = 14;
    private static final int WHEEL = 24;
    private static final long REFRESH_MS = 500;

    private final Map<net.minecraft.resources.ResourceLocation, int[]> counts = new java.util.HashMap<>();
    private long refreshedAt;
    private int scroll;

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - refreshedAt < REFRESH_MS) {
            return;
        }
        refreshedAt = now;
        counts.clear();
        for (Quest quest : ClientBook.current().quests().values()) {
            quest.chapter().ifPresent(chapter -> {
                int[] count = counts.computeIfAbsent(chapter, key -> new int[2]);
                count[1]++;
                if (ClientProgress.get(quest.id()).completed()) {
                    count[0]++;
                }
            });
        }
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        refresh();
        Map<Integer, List<Chapter>> epochs = new TreeMap<>();
        for (Chapter chapter : ClientBook.current().chapters().values()) {
            epochs.computeIfAbsent(chapter.epoch().orElse(0), key -> new ArrayList<>()).add(chapter);
        }
        int perRow = Math.max(1, (w + GAP) / (CARD_W + GAP));
        ui.clip(x, y, w, h);
        int cursor = y - scroll;
        for (Map.Entry<Integer, List<Chapter>> epoch : epochs.entrySet()) {
            String title = epoch.getKey() == 0
                    ? Component.translatable("rysquests.timeline.other").getString()
                    : Component.translatable("rysquests.timeline.epoch", epoch.getKey()).getString();
            ui.small(title, x + 2, cursor + 3, "text.dim");
            cursor += HEADING;
            List<Chapter> chapters = epoch.getValue();
            for (int i = 0; i < chapters.size(); i++) {
                renderCard(ui, chapters.get(i), x + (i % perRow) * (CARD_W + GAP), cursor + (i / perRow) * (CARD_H + GAP));
            }
            cursor += ((chapters.size() + perRow - 1) / perRow) * (CARD_H + GAP) + 3;
        }
        ui.unclip();
        scroll = Mth.clamp(scroll, 0, Math.max(0, cursor + scroll - y - h));
    }

    private void renderCard(Ui ui, Chapter chapter, int x, int y) {
        ui.theme.card(ui.g, x, y, CARD_W, CARD_H);
        chapter.icon().map(TaskText::stackOf).ifPresent(stack -> ui.plated(stack, x + 4, y + 8));
        ui.text(Draw.ellipsize(Component.translatable(chapter.title()).getString(), CARD_W - 26), x + 23, y + 6, "text.primary");
        int[] count = counts.getOrDefault(chapter.id(), new int[2]);
        ui.bar(x + 23, y + 18, CARD_W - 28, 3, count[1] == 0 ? 0 : (float) count[0] / count[1], "state.ok");
        ui.small(count[0] + " / " + count[1], x + 23, y + 23, "text.muted");
        ui.hit(x, y, CARD_W, CARD_H, () -> {
            BookState.openChapter(chapter.id());
            BookState.view = ViewMode.FLOW;
        });
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll -= (int) (amount * WHEEL);
    }
}
