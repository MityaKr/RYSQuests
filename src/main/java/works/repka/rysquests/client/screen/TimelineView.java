package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Draw;

/** The whole book by eras, from {@code design/Timeline.dc.html}: five at a time, the wheel moves through the rest. */
final class TimelineView implements BookView {
    private static final int PAD_X = 24;
    private static final int PAD_Y = 22;
    private static final int COLUMNS = 5;
    private static final int RAIL_DOTS = 6;
    private static final int GAP = 14;
    private static final int NUDGE = 48;

    private final Scroll nudge = new Scroll();
    private int offset;
    private int lastOffset;
    private boolean positioned;

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        List<Epochs.Epoch> epochs = Epochs.list();
        int current = Epochs.currentIndex(epochs);
        if (!positioned && !epochs.isEmpty()) {
            offset = Math.max(0, current - 1);
            positioned = true;
        }
        lastOffset = Math.max(0, epochs.size() - COLUMNS);
        offset = Mth.clamp(offset, 0, lastOffset);
        int push = nudge.offset();
        float left = x + PAD_X;
        float inner = w - PAD_X * 2;
        float columnW = (inner - GAP * (COLUMNS - 1f)) / COLUMNS;
        int visible = Math.min(COLUMNS, epochs.size() - offset);
        float railY = y + PAD_Y;
        renderRail(ui, current, left + 20, railY, inner - 40, Math.min(RAIL_DOTS, epochs.size() - offset));
        float cardY = y + PAD_Y + 34;
        float cardH = h - PAD_Y * 2 - 34 - 16 - 14;
        for (int i = 0; i < visible; i++) {
            renderColumn(ui, epochs.get(offset + i), offset + i, current, left + i * (columnW + GAP) - push, cardY, columnW, cardH);
        }
        float hintCy = y + h - PAD_Y - 7;
        ui.label(Component.translatable("rysquests.timeline.hint_left").getString(), left, hintCy, 11, Ui.REGULAR, ui.c("text.dim"));
        ui.labelRight(Component.translatable("rysquests.timeline.hint_right").getString(), left + inner, hintCy, 11, Ui.REGULAR, ui.c("text.dim"));
    }

    private void renderRail(Ui ui, int current, float x, float y, float w, int visible) {
        if (visible == 0) {
            return;
        }
        float dots = 0;
        for (int i = 0; i < visible; i++) {
            dots += offset + i == current ? 18 : 14;
        }
        float link = visible > 1 ? (w - dots) / (visible - 1) : 0;
        float cy = y + 9;
        float pen = x;
        for (int i = 0; i < visible; i++) {
            int index = offset + i;
            float size = index == current ? 18 : 14;
            boolean done = index < current;
            if (index == current) {
                ui.circle(pen - 4, cy - 13, 26, Draw.alpha(ui.c("accent"), 0.2f));
            }
            ui.circle(pen, cy - size / 2f, size, ui.c(index == current ? "accent" : done ? "state.ok" : "card.border"));
            pen += size;
            if (i + 1 < visible) {
                ui.rect(pen, cy - 1, link, 2, ui.c(done ? "state.ok" : "card.border"));
                pen += link;
            }
        }
    }

    private void renderColumn(Ui ui, Epochs.Epoch epoch, int index, int current, float x, float y, float w, float h) {
        boolean now = index == current;
        boolean done = index < current;
        ui.box(x, y, w, h, 12, now ? ui.mix("card.bg", "card.border", 0.2f) : ui.c("card.bg"), now ? ui.c("accent") : ui.c("panel.border"));
        float left = x + 15;
        float inner = w - 30;
        ui.clip(Math.round(left), Math.round(y), Math.round(inner), Math.round(h));
        String numeral = Ui.roman(epoch.number());
        ui.label(numeral, left, y + 29, 22, Ui.DISPLAY, ui.c(now ? "accent" : done ? "state.ok" : "text.muted"));
        ui.labelRight(epoch.done() + " / " + epoch.total(), left + inner, y + 33, 11, Ui.REGULAR, ui.c("text.muted"));
        ui.labelFit(epoch.title(), left, y + 61, inner, 13, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.bar(left, y + 79, inner, 5, 3, epoch.ratio(), ui.c(now ? "accent" : "state.ok"));
        ui.caps(Component.translatable("rysquests.timeline.milestones").getString(), left, y + 105, ui.c("text.dim"));
        float cursor = y + 122;
        float keyTop = y + h - 15 - 66;
        for (Quest quest : epoch.milestones()) {
            if (cursor + 34 > keyTop - 10) {
                break;
            }
            renderMilestone(ui, quest, left, cursor, inner);
            cursor += 44;
        }
        if (epoch.keyQuest() != null) {
            renderKey(ui, epoch, index, current, left, keyTop, inner);
        }
        ui.unclip();
        ui.hit(x, y, w, 96, () -> {
            BookState.openChapter(epoch.chapters().getFirst().id());
            BookState.view = ViewMode.FLOW;
        });
    }

    private static void renderMilestone(Ui ui, Quest quest, float x, float y, float w) {
        QuestStatus status = BookState.status(quest);
        boolean done = status == QuestStatus.CLAIMED || status == QuestStatus.COMPLETED;
        ui.box(x, y, w, 34, 8, ui.c("slot.bg"), done ? ui.doneBorder() : ui.c("slot.border"));
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.item(icon, x + 9, y + 8, 18);
        ui.interactive(icon, x + 9, y + 8, 18, 18);
        float cy = y + 17;
        float right = x + w - 9;
        float stateW;
        QuestProgress own = ClientProgress.get(quest.id());
        if (done) {
            stateW = 10;
            ui.check(right - 10, cy - 5, 10, ui.c("state.ok"));
        } else if (status == QuestStatus.LOCKED) {
            stateW = 8;
            ui.lock(right - 8, cy - 5, 10, ui.mix("accent", "text.dim", 0.35f));
        } else if (status == QuestStatus.IN_PROGRESS) {
            String text = QuestCard.tasksDone(quest, own) + "/" + quest.tasks().size();
            stateW = ui.width(text, 11, Ui.REGULAR);
            ui.labelRight(text, right, cy, 11, Ui.REGULAR, ui.c("accent"));
        } else {
            String text = Component.translatable("rysquests.timeline.soon").getString();
            stateW = ui.width(text, 11, Ui.REGULAR);
            ui.labelRight(text, right, cy, 11, Ui.REGULAR, ui.c("chip.mod.fg"));
        }
        ui.labelFit(Component.translatable(quest.title()).getString(), x + 35, cy, w - 35 - 9 - stateW - 6, 11.5f, Ui.REGULAR, ui.c("text.body"));
        ui.hit(x, y, w, 34, () -> {
            BookState.select(quest);
            BookState.view = ViewMode.MAP;
        });
    }

    private static void renderKey(Ui ui, Epochs.Epoch epoch, int index, int current, float x, float y, float w) {
        ui.caps(Component.translatable("rysquests.timeline.key_item").getString(), x, y + 7, ui.c("text.dim"));
        Quest quest = epoch.keyQuest();
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.cell(icon, x, y + 18, 30, 30, 7, 16, ui.c("slot.bg"), ui.c("card.border"));
        ui.labelFit(icon.isEmpty() ? Component.translatable(quest.title()).getString() : icon.getHoverName().getString(), x + 38, y + 33, w - 38, 11.5f, Ui.REGULAR, ui.c("button.fg"));
        String state = index < current ? Component.translatable("rysquests.timeline.finished").getString()
                : index == current ? Component.translatable("rysquests.timeline.in_progress", Ui.counted("rysquests.plural.quest", epoch.total() - epoch.done())).getString()
                : Ui.counted("rysquests.plural.quest", epoch.total());
        ui.labelFit(state, x, y + 59, w, 11, Ui.REGULAR, ui.c("text.muted"));
    }

    /** The line in the top bar where the other views have their search. */
    void renderSummary(Ui ui, float right, float cy, float room) {
        List<Epochs.Epoch> epochs = Epochs.list();
        String number = epochs.isEmpty() ? "" : Ui.roman(epochs.get(Epochs.currentIndex(epochs)).number());
        float numberW = ui.width(number, 12, Ui.SEMIBOLD);
        String text = Component.translatable("rysquests.timeline.summary", epochs.size(), ClientBook.current().quests().size()).getString();
        if (ui.width(text, 12, Ui.REGULAR) + numberW + 4 > room) {
            return;
        }
        ui.label(number, right - numberW, cy, 12, Ui.SEMIBOLD, ui.c("accent"));
        ui.labelRight(text, right - numberW - 4, cy, 12, Ui.REGULAR, ui.c("text.muted"));
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        // The wheel never wraps around: at either end the columns only give a small push.
        int next = Mth.clamp(offset - (int) Math.signum(amount), 0, lastOffset);
        if (next == offset) {
            nudge.wheel(amount, NUDGE);
        }
        offset = next;
    }
}
