package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.task.ItemTask;

/** "What next", from {@code design/Home.dc.html}: recommendations, rewards waiting, the latest news and overall progress. */
final class HomeView implements BookView {
    private static final int PAD_X = 24;
    private static final int PAD_Y = 22;
    private static final int GAP = 18;
    private static final int CARD_H = 78;
    private static final int CARD_GAP = 12;
    private static final int PICKS = 3;
    private static final int FEED = 3;
    private static final int PROGRESS_ROWS = 6;
    private static final int EPOCH_DOTS = 5;
    private static final int WHEEL = 48;
    private static final long REFRESH_MS = 500;

    private enum Kind {
        ALMOST, READY, PAGE, OPEN
    }

    private record Pick(Quest quest, Kind kind, String reason) {
    }

    private record News(Quest quest, long at) {
    }

    private List<Pick> picks = List.of();
    private List<News> news = List.of();
    private List<Quest> claimable = List.of();
    private long refreshedAt;
    private final Scroll scroll = new Scroll();

    /** Walks the whole book, so it runs twice a second rather than every frame. */
    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - refreshedAt < REFRESH_MS) {
            return;
        }
        refreshedAt = now;
        List<Quest> open = new ArrayList<>();
        List<Quest> claim = new ArrayList<>();
        List<News> finished = new ArrayList<>();
        for (Quest quest : ClientBook.current().quests().values()) {
            if (!BookScreen.inStory(quest)) {
                continue;
            }
            QuestStatus status = BookState.status(quest);
            if (status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS) {
                open.add(quest);
            }
            if (status == QuestStatus.COMPLETED) {
                claim.add(quest);
            }
            long at = ClientProgress.get(quest.id()).completedAt();
            if (at > 0) {
                finished.add(new News(quest, at));
            }
        }
        claimable = claim;
        finished.sort(Comparator.comparingLong(News::at).reversed());
        news = finished.subList(0, Math.min(FEED, finished.size()));
        picks = choose(open);
    }

    private static List<Pick> choose(List<Quest> open) {
        List<Pick> chosen = new ArrayList<>();
        open.stream().filter(quest -> ratio(quest) > 0).max(Comparator.comparingDouble(HomeView::ratio)).ifPresent(quest -> {
            int missing = missingItems(quest);
            String reason = missing > 0 ? Component.translatable("rysquests.home.short", missing).getString() : Component.translatable("rysquests.home.can_finish").getString();
            chosen.add(new Pick(quest, Kind.ALMOST, reason));
        });
        open.stream().filter(quest -> chosen.stream().noneMatch(pick -> pick.quest == quest) && !quest.tasks().isEmpty() && missingItems(quest) == 0 && hasItemTask(quest))
                .findFirst().ifPresent(quest -> chosen.add(new Pick(quest, Kind.READY, Component.translatable("rysquests.home.in_inventory").getString())));
        for (Quest quest : open) {
            if (chosen.size() >= PICKS) {
                break;
            }
            if (chosen.stream().noneMatch(pick -> pick.quest == quest)) {
                chosen.add(quest.tasks().isEmpty()
                        ? new Pick(quest, Kind.PAGE, Component.translatable("rysquests.home.read").getString())
                        : new Pick(quest, Kind.OPEN, Component.translatable("rysquests.home.available").getString()));
            }
        }
        return chosen;
    }

    private static boolean hasItemTask(Quest quest) {
        return quest.tasks().stream().anyMatch(task -> task instanceof ItemTask);
    }

    private static int missingItems(Quest quest) {
        QuestProgress own = ClientProgress.get(quest.id());
        int missing = 0;
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (quest.tasks().get(i) instanceof ItemTask item) {
                missing += Math.max(0, item.count() - own.task(i) - BookScreen.held(item));
            }
        }
        return missing;
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
        float content = w - PAD_X * 2;
        float column = (content - GAP * 2) / 3f;
        float left = x + PAD_X;
        float leftW = column * 2 + GAP;
        float rightX = left + leftW + GAP;
        ui.clip(x, y, w, h);
        int offset = scroll.offset();
        float top = y + PAD_Y - offset;
        float leftBottom = renderLeft(ui, left, top, leftW);
        float rightBottom = renderRight(ui, rightX, top, column);
        ui.unclip();
        scroll.content(Math.max(leftBottom, rightBottom) + offset - y + PAD_Y, h);
    }

    private float renderLeft(Ui ui, float x, float y, float w) {
        float cy = y + 13;
        ui.label(Component.translatable("rysquests.home.title").getString(), x, cy, 20, Ui.DISPLAY, ui.c("text.primary"));
        Chapter chapter = BookState.chapter == null ? null : ClientBook.current().chapters().get(BookState.chapter);
        if (chapter != null) {
            int done = 0;
            int total = 0;
            for (Quest quest : ClientBook.current().quests().values()) {
                if (quest.chapter().filter(chapter.id()::equals).isPresent()) {
                    total++;
                    done += ClientProgress.get(quest.id()).completed() ? 1 : 0;
                }
            }
            String status = Component.translatable("rysquests.home.chapter_status", Ui.roman(BookScreen.chapterNumber(chapter.id())), done, total).getString();
            ui.labelRight(status, x + w, y + 15.5f, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        float cursor = y + 26 + 12;
        for (Pick pick : picks) {
            renderPick(ui, pick, x, cursor, w);
            cursor += CARD_H + CARD_GAP;
        }
        cursor += 4;
        float half = (w - CARD_GAP) / 2f;
        float boxH = 118;
        renderRewards(ui, x, cursor, half, boxH);
        renderNews(ui, x + half + CARD_GAP, cursor, half, boxH);
        return cursor + boxH;
    }

    private static void renderPick(Ui ui, Pick pick, float x, float y, float w) {
        Quest quest = pick.quest;
        ui.box(x, y, w, CARD_H, 12, ui.c("card.bg"), pick.kind == Kind.ALMOST ? ui.warmBorder(0.19f) : ui.c("panel.border"));
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.cell(icon, x + 17, y + 15, 48, 48, 10, 28, ui.c("slot.bg"), ui.c("card.border"));
        String open = Component.translatable("rysquests.home.open").getString();
        float buttonW = ui.buttonWidth(open, 12.5f, Ui.SEMIBOLD, 14);
        float buttonX = x + w - 17 - buttonW;
        ui.button(open, buttonX, y + 21, buttonW, 36, 9, 12.5f, Ui.SEMIBOLD, 14, Ui.Style.ACCENT, () -> {
            BookState.select(quest);
            BookState.view = ViewMode.FLOW;
        });
        List<ItemStack> rewards = QuestCard.rewardStacks(quest, 3);
        float slotX = buttonX - 14 - 32 * rewards.size() - 6 * Math.max(0, rewards.size() - 1);
        for (int i = 0; i < rewards.size(); i++) {
            ui.cell(rewards.get(i), slotX + i * 38, y + 23, 32, 32, 7, 18, ui.c("slot.bg"), ui.c("slot.border"));
        }
        float textX = x + 79;
        float textW = slotX - 14 - textX;
        String title = Component.translatable(quest.title()).getString();
        float titleW = Math.min(textW - 90, ui.width(title, 14, Ui.SEMIBOLD));
        ui.labelFit(title, textX, y + 25.5f, titleW, 14, Ui.SEMIBOLD, ui.c("text.primary"));
        String chip = Component.translatable("rysquests.home.kind." + pick.kind.name().toLowerCase(java.util.Locale.ROOT)).getString();
        int chipBg = switch (pick.kind) {
            case ALMOST -> ui.warmBorder(0.17f);
            case READY -> ui.c("chip.tag.bg");
            default -> ui.c("chip.mod.bg");
        };
        int chipFg = switch (pick.kind) {
            case ALMOST -> ui.c("accent");
            case READY -> ui.c("chip.tag.fg");
            default -> ui.c("chip.mod.fg");
        };
        ui.pill(chip, textX + titleW + 8, y + 25.5f, 10.5f, Ui.MEDIUM, 7, 15, 7.5f, chipBg, chipFg);
        QuestProgress own = ClientProgress.get(quest.id());
        String counts = quest.tasks().isEmpty() ? "" : Component.translatable("rysquests.map.tasks", QuestCard.tasksDone(quest, own), quest.tasks().size()).getString() + " · ";
        ui.labelFit(counts + pick.reason, textX, y + 45.5f, textW, 12, Ui.REGULAR, ui.c("text.muted"));
        ui.bar(textX, y + 57, Math.min(260, textW), 4, 2, (float) ratio(quest), ui.c("accent"));
    }

    private void renderRewards(Ui ui, float x, float y, float w, float h) {
        ui.box(x, y, w, h, 12, ui.c("card.bg"), ui.c("panel.border"));
        ui.caps(Component.translatable("rysquests.home.rewards").getString(), x + 17, y + 23, ui.c("text.dim"));
        float badgeW = ui.width(String.valueOf(claimable.size()), 11, Ui.SEMIBOLD) + 14;
        ui.fill(x + w - 17 - badgeW, y + 15, badgeW, 16, 8, ui.warmBorder(0.17f));
        ui.labelCentered(String.valueOf(claimable.size()), x + w - 17 - badgeW / 2f, y + 23, 11, Ui.SEMIBOLD, ui.c("accent"));
        if (claimable.isEmpty()) {
            ui.label(Component.translatable("rysquests.home.rewards_none").getString(), x + 17, y + 62, 12, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Quest quest : claimable) {
            if (stacks.size() < 2) {
                stacks.addAll(QuestCard.rewardStacks(quest, 2 - stacks.size()));
            }
        }
        float slotY = y + 41;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            ui.cell(stack, x + 17 + i * 48, slotY, 42, 42, 9, 22, ui.c("slot.bg"), ui.c("card.border"));
            ui.count(stack.getCount(), x + 17 + i * 48 + 37, slotY + 40);
        }
        BookScreen screen = net.minecraft.client.Minecraft.getInstance().screen instanceof BookScreen open ? open : null;
        String own = Component.translatable("rysquests.home.claim_all").getString();
        String claim = screen == null ? own : screen.claimAllLabel(own);
        float textX = x + 17 + stacks.size() * 48 + 2;
        float room = w - 34 - stacks.size() * 48 - 2;
        String names = claimNames();
        float[] widths = Ui.shrink(room, 12, new float[] {ui.width(names, 12, Ui.REGULAR), ui.buttonWidth(claim, 12, Ui.MEDIUM, 12) + 2},
                new float[] {ui.minWidth(names, 12, Ui.REGULAR), ui.buttonMinWidth(claim, 12, Ui.MEDIUM, 12) + 2});
        ui.lines(ui.wrap(names, widths[0] + 1, 12, Ui.REGULAR, 2), textX, slotY + 21, 15, 12, Ui.REGULAR, ui.c("text.muted"));
        ui.button(claim, x + w - 17 - widths[1], slotY + 5, widths[1], 32, 8, 12, Ui.MEDIUM, 12, Ui.Style.PLAIN, () -> {
            if (screen != null) {
                screen.claimAll();
            } else {
                QuestKeys.sendAll(QuestActionPayload.CLAIM_ALL);
            }
        });
    }

    private String claimNames() {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(2, claimable.size()); i++) {
            names.append(i == 0 ? "«" : " и «").append(Component.translatable(claimable.get(i).title()).getString()).append("»");
        }
        return Component.translatable("rysquests.home.for_quests", names.toString()).getString();
    }

    private void renderNews(Ui ui, float x, float y, float w, float h) {
        ui.box(x, y, w, h, 12, ui.c("card.bg"), ui.c("panel.border"));
        ui.caps(Component.translatable(ClientProgress.teamSize() > 1 ? "rysquests.home.team" : "rysquests.home.recent").getString(), x + 17, y + 22, ui.c("text.dim"));
        float cursor = y + 37;
        for (News item : news) {
            ItemStack icon = item.quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
            ui.item(icon, x + 17, cursor, 18);
            float cy = cursor + 9;
            String ago = ago(item.at);
            float agoW = ui.width(ago, 12, Ui.REGULAR);
            ui.labelFit(Component.translatable("rysquests.home.done_news", Component.translatable(item.quest.title())).getString(), x + 43, cy, w - 60 - agoW - 8, 12, Ui.REGULAR, ui.c("button.fg"));
            ui.labelRight(ago, x + w - 17, cy, 12, Ui.REGULAR, ui.c("text.dim"));
            cursor += 24;
        }
    }

    static String ago(long at) {
        long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60000);
        if (minutes < 60) {
            return Component.translatable("rysquests.time.minutes", Math.max(1, minutes)).getString();
        }
        if (minutes < 60 * 24) {
            return Component.translatable("rysquests.time.hours", minutes / 60).getString();
        }
        return minutes < 60 * 48 ? Component.translatable("rysquests.time.yesterday").getString()
                : Component.translatable("rysquests.time.days", minutes / (60 * 24)).getString();
    }

    private float renderRight(Ui ui, float x, float y, float w) {
        ui.label(Component.translatable("rysquests.home.progress").getString(), x, y + 13, 20, Ui.DISPLAY, ui.c("text.primary"));
        float boxY = y + 38;
        List<Chapter> chapters = BookScreen.chapters();
        int firstRow = Math.max(0, Math.min(chapters.size() - PROGRESS_ROWS, BookScreen.chapterNumber(BookState.chapter) - 3));
        int rows = Math.min(PROGRESS_ROWS, chapters.size());
        float boxH = 30 + rows * 46 - 8;
        ui.box(x, boxY, w, boxH, 12, ui.c("card.bg"), ui.c("panel.border"));
        float cursor = boxY + 15;
        int[] counts = new int[2];
        for (int i = firstRow; i < firstRow + rows; i++) {
            Chapter chapter = chapters.get(i);
            counts[0] = 0;
            counts[1] = 0;
            for (Quest quest : ClientBook.current().quests().values()) {
                if (quest.chapter().filter(chapter.id()::equals).isPresent()) {
                    counts[1]++;
                    counts[0] += ClientProgress.get(quest.id()).completed() ? 1 : 0;
                }
            }
            String count = counts[0] + " / " + counts[1];
            float cy = cursor + 13.5f;
            ui.labelFit(Ui.roman(i + 1) + " · " + Component.translatable(chapter.title()).getString(), x + 17, cy, w - 34 - ui.width(count, 12.5f, Ui.REGULAR) - 8, 12.5f, Ui.REGULAR, ui.nameFg());
            ui.labelRight(count, x + w - 17, cy, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            float ratio = counts[1] == 0 ? 0 : (float) counts[0] / counts[1];
            ui.bar(x + 17, cursor + 26, w - 34, 6, 3, ratio, ui.c(chapter.id().equals(BookState.chapter) && ratio < 1 ? "accent" : "state.ok"));
            cursor += 46;
        }
        float epochY = boxY + boxH + 12;
        renderEpochs(ui, x, epochY, w);
        return epochY + 120;
    }

    private static void renderEpochs(Ui ui, float x, float y, float w) {
        ui.box(x, y, w, 120, 12, ui.c("card.bg"), ui.c("panel.border"));
        ui.caps(Component.translatable("rysquests.home.epochs").getString(), x + 17, y + 22, ui.c("text.dim"));
        List<Epochs.Epoch> epochs = Epochs.list();
        int current = Epochs.currentIndex(epochs);
        int from = Math.max(0, Math.min(epochs.size() - EPOCH_DOTS, current - 1));
        int to = Math.min(epochs.size(), from + EPOCH_DOTS);
        int n = to - from;
        float inner = w - 34;
        float dots = 0;
        for (int i = from; i < to; i++) {
            dots += i == current ? 18 : 14;
        }
        float link = n > 1 ? (inner - dots) / (n - 1) : 0;
        float cy = y + 50;
        float pen = x + 17;
        for (int i = from; i < to; i++) {
            float size = i == current ? 18 : 14;
            boolean done = i < current;
            int color = i == current ? ui.c("accent") : done ? ui.c("state.ok") : ui.c("card.border");
            if (i == current) {
                ui.circle(pen - 4, cy - 13, 26, works.repka.ryscore.ui.Draw.alpha(ui.c("accent"), 0.2f));
            }
            ui.circle(pen, cy - size / 2f, size, color);
            String numeral = Ui.roman(epochs.get(i).number());
            float labelX = n == 1 ? pen : pen + (size - ui.width(numeral, 10.5f, Ui.REGULAR)) * (i - from) / (float) (n - 1);
            ui.label(numeral, labelX, y + 73.5f, 10.5f, Ui.REGULAR, ui.c(i == current ? "accent" : "text.muted"));
            pen += size;
            if (i + 1 < to) {
                ui.rect(pen, cy - 1, link, 2, ui.c(done ? "state.ok" : "card.border"));
                pen += link;
            }
        }
        if (!epochs.isEmpty() && current + 1 < epochs.size()) {
            Epochs.Epoch now = epochs.get(current);
            String text = Component.translatable("rysquests.home.to_epoch", Ui.roman(current + 2), Ui.counted("rysquests.plural.quest", Math.max(0, now.total() - now.done()))).getString();
            ui.labelFit(text, x + 17, y + 97.5f, inner, 12, Ui.REGULAR, ui.c("text.muted"));
        }
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll.wheel(amount, WHEEL);
    }
}
