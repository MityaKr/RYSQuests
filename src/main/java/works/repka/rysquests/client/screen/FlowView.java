package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.ItemTask;
import works.repka.ryscore.ui.Draw;

/**
 * The chapter as a ribbon, from {@code design/Comfort.dc.html}: finished quests folded into one line, the current quest
 * opened in full, then what comes next.
 */
final class FlowView implements BookView {
    private static final int PAD = 24;
    private static final int BAR_H = 65;
    private static final int NEXT_CARDS = 2;
    private static final int DONE_NAMES = 4;
    private static final int SPINE_X = 26;
    private static final int WHEEL = 48;
    private static final int MAX_LINES = 4;

    private final BookScreen screen;
    private final Scroll scroll = new Scroll();

    FlowView(BookScreen screen) {
        this.screen = screen;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        ui.rect(x + w - 1, y, 1, h, ui.flowBorder());
        int left = x + PAD;
        int inner = w - 1 - PAD * 2;
        float headH = renderHead(ui, left, y + 18, inner);
        int bodyTop = y + Math.round(18 + headH + 10);
        int barY = y + h - BAR_H;
        List<Quest> quests = screen.visibleQuests();
        Quest current = current(quests);
        ui.clip(x, bodyTop, w - 1, barY - bodyTop);
        int offset = scroll.offset();
        float cursor = bodyTop + 6 - offset;
        cursor = renderRibbon(ui, quests, current, left, cursor, inner);
        ui.unclip();
        scroll.content(cursor + offset - bodyTop + 16, barY - bodyTop);
        renderBar(ui, x, barY, w - 1);
    }

    /** The selected quest if it is in this chapter, otherwise the first one the player can work on. */
    @Nullable
    private static Quest current(List<Quest> quests) {
        for (Quest quest : quests) {
            if (quest.id().equals(BookState.quest)) {
                return quest;
            }
        }
        Quest first = quests.stream().filter(quest -> {
            QuestStatus status = BookState.status(quest);
            return status != QuestStatus.CLAIMED && status != QuestStatus.LOCKED;
        }).findFirst().orElse(quests.stream().filter(quest -> BookState.status(quest) != QuestStatus.CLAIMED).findFirst().orElse(null));
        if (first != null) {
            BookState.select(first);
        }
        return first;
    }

    private float renderHead(Ui ui, float x, float y, float w) {
        Chapter chapter = BookState.chapter == null ? null : ClientBook.current().chapters().get(BookState.chapter);
        if (chapter == null) {
            return 30;
        }
        String flow = Component.translatable("rysquests.view.flow").getString();
        String map = Component.translatable("rysquests.view.map").getString();
        float flowW = ui.width(flow, 13, Ui.MEDIUM) + 24;
        float mapW = ui.width(map, 13, Ui.REGULAR) + 24;
        float tabsW = flowW + 4 + mapW;

        Quest quest = BookState.quest == null ? null : ClientBook.current().quests().get(BookState.quest);
        List<String> parts = new ArrayList<>();
        parts.add(Component.translatable("rysquests.flow.chapter", Ui.roman(BookScreen.chapterNumber(chapter.id()))).getString());
        parts.add(Component.translatable(chapter.title()).getString());
        if (quest != null) {
            parts.add(Component.translatable(quest.title()).getString());
        }
        float crumbRoom = w - tabsW - 10;
        while (parts.size() > 1 && minCrumbs(ui, parts) > crumbRoom) {
            parts.removeFirst();
        }
        int count = parts.size() * 2 - 1;
        float[] base = new float[count];
        float[] min = new float[count];
        for (int i = 0; i < count; i++) {
            if (i % 2 == 1) {
                base[i] = 5;
                min[i] = 5;
            } else {
                String part = parts.get(i / 2);
                var weight = i == count - 1 ? Ui.MEDIUM : Ui.REGULAR;
                base[i] = ui.width(part, 13, weight);
                min[i] = count == 1 ? 0 : ui.minWidth(part, 13, weight);
            }
        }
        float[] widths = Ui.shrink(crumbRoom, 6, base, min);
        float rowH = 30;
        List<List<String>> wrapped = new ArrayList<>();
        for (int i = 0; i < count; i += 2) {
            var weight = i == count - 1 ? Ui.MEDIUM : Ui.REGULAR;
            List<String> lines = ui.wrap(parts.get(i / 2), widths[i] + 1, 13, weight, 3);
            wrapped.add(lines);
            rowH = Math.max(rowH, lines.size() * 16);
        }
        float cy = y + rowH / 2f;
        float pen = x;
        for (int i = 0; i < count; i++) {
            if (i % 2 == 1) {
                ui.label("›", pen, cy, 13, Ui.REGULAR, ui.c("text.muted"));
            } else {
                boolean last = i == count - 1;
                ui.lines(wrapped.get(i / 2), pen, cy, 16, 13, last ? Ui.MEDIUM : Ui.REGULAR, ui.c(last ? "text.primary" : "text.muted"));
            }
            pen += widths[i] + 6;
        }
        float tabX = x + w - tabsW;
        ui.tab(flow, tabX, cy, 30, 13, true, () -> BookState.view = ViewMode.FLOW);
        ui.tab(map, tabX + flowW + 4, cy, 30, 13, false, () -> BookState.view = ViewMode.MAP);
        return rowH;
    }

    /** The narrowest the breadcrumbs can get: below this they drop their first part. */
    private static float minCrumbs(Ui ui, List<String> parts) {
        float total = (parts.size() - 1) * 17f;
        for (int i = 0; i < parts.size(); i++) {
            total += ui.minWidth(parts.get(i), 13, i == parts.size() - 1 ? Ui.MEDIUM : Ui.REGULAR);
        }
        return total;
    }

    private float renderRibbon(Ui ui, List<Quest> quests, @Nullable Quest current, float x, float y, float w) {
        List<Quest> done = new ArrayList<>();
        List<Quest> open = new ArrayList<>();
        List<Quest> locked = new ArrayList<>();
        for (Quest quest : quests) {
            if (quest == current) {
                continue;
            }
            switch (BookState.status(quest)) {
                case CLAIMED -> done.add(quest);
                case LOCKED -> locked.add(quest);
                default -> open.add(quest);
            }
        }
        int doneTotal = done.size();
        if (doneTotal > 0) {
            y = renderDoneLine(ui, done, quests.size(), x, y, w);
            if (BookState.showDone) {
                for (Quest quest : done) {
                    y = renderDoneRow(ui, quest, x, y + 6, w);
                }
            }
            if (current != null) {
                ui.rect(x + SPINE_X, y, 2, 18, ui.c("slot.have.border"));
                y += 18;
            }
        }
        if (current != null) {
            y = renderCurrent(ui, current, x, y, w);
        }
        List<Quest> next = next(current, open, locked);
        if (!next.isEmpty()) {
            ui.rect(x + SPINE_X, y, 2, 18, ui.idleLine());
            y += 18;
            ui.label(Component.translatable("rysquests.flow.next").getString(), x + 2, y + 7.5f, 12, Ui.REGULAR, ui.c("text.muted"));
            y += 15 + 8;
            y = renderCards(ui, next, x, y, w);
        }
        List<Quest> rest = new ArrayList<>(open);
        rest.addAll(locked);
        rest.removeAll(next);
        if (!rest.isEmpty()) {
            ui.rect(x + SPINE_X, y, 2, 14, ui.idleLine());
            y += 14;
            if (BookState.showRest) {
                y = renderCards(ui, rest, x, y, w);
            } else {
                y = renderRestLine(ui, rest.size(), x, y, w);
            }
        }
        return y;
    }

    private static List<Quest> next(@Nullable Quest current, List<Quest> open, List<Quest> locked) {
        List<Quest> next = new ArrayList<>();
        for (Quest quest : open) {
            if (next.size() < NEXT_CARDS) {
                next.add(quest);
            }
        }
        if (current != null) {
            for (ResourceLocation id : ClientBook.current().unlocks().getOrDefault(current.id(), List.of())) {
                locked.stream().filter(quest -> quest.id().equals(id) && next.size() < NEXT_CARDS && !next.contains(quest)).findFirst().ifPresent(next::add);
            }
        }
        for (Quest quest : locked) {
            if (next.size() < NEXT_CARDS && !next.contains(quest)) {
                next.add(quest);
            }
        }
        return next;
    }

    private float renderDoneLine(Ui ui, List<Quest> done, int total, float x, float y, float w) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(DONE_NAMES, done.size()); i++) {
            names.append(i == 0 ? "" : ", ").append(Component.translatable(done.get(done.size() - 1 - i).title()).getString());
        }
        if (done.size() > DONE_NAMES) {
            names.append("…");
        }
        String text = Component.translatable("rysquests.flow.done", done.size(), total, names.toString()).getString();
        String toggle = Component.translatable(BookState.showDone ? "rysquests.flow.collapse" : "rysquests.flow.expand").getString();
        float[] widths = Ui.shrink(w - 34 - 34, 12, new float[] {ui.width(text, 14, Ui.REGULAR), ui.width(toggle, 13, Ui.REGULAR)},
                new float[] {0, ui.width(toggle, 13, Ui.REGULAR)});
        List<String> lines = ui.wrap(text, widths[0] + 1, 14, Ui.REGULAR, MAX_LINES);
        float h = Math.max(46, lines.size() * 17 + 26);
        float cy = y + h / 2f;
        ui.box(x, y, w, h, 12, ui.c("status.bg"), ui.flowBorder());
        ui.circle(x + 17, cy - 11, 22, ui.c("state.ok"));
        ui.check(x + 22, cy - 6, 12, ui.c("accent.text"));
        ui.lines(lines, x + 51, cy, 17, 14, Ui.REGULAR, ui.c("button.fg"));
        ui.link(toggle, x + w - 17 - ui.width(toggle, 13, Ui.REGULAR), cy, 13, ui.c("chip.mod.fg"));
        ui.hit(x, y, w, h, () -> BookState.showDone = !BookState.showDone);
        return y + h;
    }

    private static float renderDoneRow(Ui ui, Quest quest, float x, float y, float w) {
        float h = 46;
        float cy = y + h / 2f;
        ui.box(x, y, w, h, 12, ui.c("status.bg"), ui.flowBorder());
        quest.icon().map(TaskText::stackOf).ifPresent(stack -> ui.item(stack, x + 17, cy - 11, 22));
        ui.labelFit(Component.translatable(quest.title()).getString(), x + 51, cy, w - 68, 14, Ui.REGULAR, ui.c("text.muted"));
        ui.hit(x, y, w, h, () -> BookState.select(quest));
        return y + h;
    }

    private float renderRestLine(Ui ui, int count, float x, float y, float w) {
        float h = 38;
        float cy = y + h / 2f;
        ui.dashed(x, y, w, h, ui.flowBorder());
        ui.label(Component.translatable("rysquests.flow.rest", Ui.counted("rysquests.plural.quest", count)).getString(), x + 17, cy, 13, Ui.REGULAR, ui.c("text.muted"));
        String show = Component.translatable("rysquests.flow.show").getString();
        ui.link(show, x + w - 17 - ui.width(show, 13, Ui.REGULAR), cy, 13, ui.c("chip.mod.fg"));
        ui.hit(x, y, w, h, () -> BookState.showRest = true);
        return y + h;
    }

    private float renderCards(Ui ui, List<Quest> quests, float x, float y, float w) {
        float gap = 10;
        float cardW = (w - gap) / 2f;
        for (int i = 0; i < quests.size(); i += 2) {
            Quest a = quests.get(i);
            Quest b = i + 1 < quests.size() ? quests.get(i + 1) : null;
            float h = Math.max(cardHeight(ui, a, cardW), b == null ? 0 : cardHeight(ui, b, cardW));
            renderCard(ui, a, x, y, cardW, h);
            if (b != null) {
                renderCard(ui, b, x + cardW + gap, y, cardW, h);
            }
            y += h + (i + 2 < quests.size() ? gap : 0);
        }
        return y;
    }

    private static float cardTextW(float w) {
        return w - 2 - 28 - 36 - 12;
    }

    private static float cardHeight(Ui ui, Quest quest, float w) {
        String sub = subline(quest);
        int subLines = ui.wrap(sub, cardTextW(w), 12, Ui.REGULAR, 3).size();
        return Math.max(56, 24 + 2 + 17 + 2 + subLines * 15);
    }

    private static String subline(Quest quest) {
        return BookState.status(quest) == QuestStatus.LOCKED
                ? BookScreen.waitingFor(quest).getString()
                : Component.translatable("rysquests.home.available").getString();
    }

    private void renderCard(Ui ui, Quest quest, float x, float y, float w, float h) {
        boolean over = ui.over(x, y, w, h);
        ui.box(x, y, w, h, 12, over ? ui.flowBg() : ui.c("status.bg"), ui.flowBorder());
        float cy = y + h / 2f;
        ui.fill(x + 15, cy - 18, 36, 36, 9, Draw.mix(ui.c("status.bg"), ui.c("slot.bg"), 0.7f));
        quest.icon().map(TaskText::stackOf).ifPresent(stack -> {
            ui.item(stack, x + 22, cy - 11, 22);
            ui.veil(x + 15, cy - 18, 36, 36, 9, ui.c("status.bg"), 0.3f);
            ui.interactive(stack, x + 15, cy - 18, 36, 36);
        });
        float textX = x + 63;
        float textW = cardTextW(w);
        String sub = subline(quest);
        List<String> subLines = ui.wrap(sub, textW, 12, Ui.REGULAR, 3);
        float blockH = 17 + 2 + subLines.size() * 15;
        float top = cy - blockH / 2f;
        ui.labelFit(Component.translatable(quest.title()).getString(), textX, top + 8.5f, textW, 14, Ui.REGULAR, ui.c("text.body"));
        for (int i = 0; i < subLines.size(); i++) {
            ui.label(subLines.get(i), textX, top + 19 + 7.5f + i * 15, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        ui.hit(x, y, w, h, () -> BookState.select(quest));
    }

    private float renderCurrent(Ui ui, Quest quest, float x, float y, float w) {
        float height = ui.measure(() -> Math.round(layoutCurrent(ui, quest, x, 0, w)));
        ui.shadow(x, y, w, height, 14, 0.4f);
        ui.box(x, y, w, height, 14, ui.currentBg(), ui.c("accent"));
        layoutCurrent(ui, quest, x, y, w);
        return y + height;
    }

    private float layoutCurrent(Ui ui, Quest quest, float x, float y, float w) {
        QuestProgress own = ClientProgress.get(quest.id());
        QuestStatus status = BookState.status(quest);
        boolean open = status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
        float left = x + 19;
        float inner = w - 38;
        float top = y + 17;
        float headH = 50;
        float cy = top + headH / 2f;
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.cell(icon, left, top, 50, 50, 12, 30, ui.c("slot.bg"), ui.c("card.border"));
        float textX = left + 64;
        float textW = inner - 64 - 14 - 44;
        ui.labelFit(Component.translatable(quest.title()).getString(), textX, cy - 22 + 12.5f, textW, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelFit(QuestCard.flowSummary(quest, own, status), textX, cy - 22 + 28 + 8, textW, 13, Ui.REGULAR, ui.c("text.muted"));
        QuestCard.pinButton(ui, quest, left + inner - 44, cy - 22, 44, 11);
        float cursor = top + headH + 14;

        int firstOpen = -1;
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (firstOpen < 0 && own.task(i) < quest.tasks().get(i).target() && !own.completed()) {
                firstOpen = i;
            }
        }
        for (int i = 0; i < quest.tasks().size(); i++) {
            cursor = taskRow(ui, quest, i, own, open, i == firstOpen, left, cursor, inner) + (i + 1 < quest.tasks().size() ? 8 : 0);
        }
        if (!quest.tasks().isEmpty()) {
            cursor += 14;
        }
        cursor = QuestCard.flowRewards(ui, quest, own, status, left, cursor, inner);
        return cursor + 17 - y;
    }

    private float taskRow(Ui ui, Quest quest, int index, QuestProgress own, boolean open, boolean active, float x, float y, float w) {
        TaskDef task = quest.tasks().get(index);
        int value = Math.min(own.task(index), task.target());
        boolean done = own.completed() || value >= task.target();
        ItemStack icon = TaskText.icon(task);
        String name = QuestCard.taskName(task);
        String right = done ? Component.translatable(task instanceof ItemTask item && item.mode() == ItemTask.Mode.CONSUME ? "rysquests.flow.submitted" : "rysquests.flow.received").getString()
                : value + " / " + task.target();
        int submit = open && !done && task instanceof ItemTask item && item.mode() == ItemTask.Mode.CONSUME ? Math.min(BookScreen.held(item), task.target() - value) : 0;
        String buttonText = submit > 0 ? Component.translatable("rysquests.flow.submit_n", submit).getString() : "";

        float room = w - 26 - 24 - 12 - (icon.isEmpty() ? 0 : 34);
        float rightBase = ui.width(right, 13, Ui.REGULAR);
        float[] base = submit > 0 ? new float[] {ui.width(name, 14, Ui.REGULAR), rightBase, ui.buttonWidth(buttonText, 13, Ui.SEMIBOLD, 14)}
                : new float[] {ui.width(name, 14, Ui.REGULAR), rightBase};
        float[] min = submit > 0 ? new float[] {0, ui.minWidth(right, 13, Ui.REGULAR), ui.buttonMinWidth(buttonText, 13, Ui.SEMIBOLD, 14)}
                : new float[] {0, ui.minWidth(right, 13, Ui.REGULAR)};
        float[] widths = Ui.shrink(room, 12, base, min);
        List<String> nameLines = ui.wrap(name, widths[0] + 1, 14, Ui.REGULAR, MAX_LINES);
        List<String> rightLines = ui.wrap(right, widths[1] + 1, 13, Ui.REGULAR, 3);
        float content = Math.max(24, Math.max(nameLines.size() * 17, Math.max(rightLines.size() * 16, submit > 0 ? 34 : 0)));
        float h = content + 22;
        float cy = y + h / 2f;

        ui.box(x, y, w, h, 10, ui.flowBg(), active ? ui.warmBorder(0.27f) : ui.flowBorder());
        ui.box(x + 13, cy - 12, 24, 24, 6, done ? ui.c("state.ok") : ui.c("slot.bg"), ui.c("card.border"));
        if (done) {
            ui.check(x + 18, cy - 7, 14, ui.c("accent.text"));
        }
        float cursor = x + 13 + 24 + 12;
        if (!icon.isEmpty()) {
            ui.item(icon, cursor, cy - 11, 22);
            ui.interactive(icon, cursor, cy - 11, 22, 22);
            cursor += 34;
        }
        ui.lines(nameLines, cursor, cy, 17, 14, Ui.REGULAR, ui.c(done ? "text.muted" : "text.primary"));
        float rightEdge = x + w - 13;
        if (submit > 0) {
            float bw = widths[2];
            ui.button(buttonText, rightEdge - bw, cy - 17, bw, 34, 9, 13, Ui.SEMIBOLD, 14, Ui.Style.ACCENT, () -> QuestCard.send(QuestActionPayload.SUBMIT, quest, 0, List.of()));
            rightEdge -= bw + 12;
        }
        float lineRight = rightEdge;
        float firstCy = cy - (rightLines.size() - 1) * 8f;
        for (int i = 0; i < rightLines.size(); i++) {
            ui.labelRight(rightLines.get(i), lineRight, firstCy + i * 16, 13, Ui.REGULAR, ui.c(done ? "state.ok" : "accent"));
        }
        if (task instanceof CheckboxTask && open && !done) {
            ui.hit(x, y, w, h, () -> QuestCard.send(QuestActionPayload.CHECK, quest, index, List.of()));
        }
        return y + h;
    }

    private void renderBar(Ui ui, int x, int y, int w) {
        ui.rect(x, y, w, BAR_H, ui.railBg());
        ui.rect(x, y, w, 1, ui.flowBorder());
        int submit = screen.readyToSubmit();
        int claim = screen.readyToClaim();
        String submitText = Component.translatable("rysquests.flow.submit_all", submit).getString();
        String claimText = Component.translatable("rysquests.flow.claim_all", claim).getString();
        String hint = Component.translatable("rysquests.flow.hint", QuestKeys.label(QuestKeys.OPEN_BOOK)).getString();
        float[] base = {submit > 0 ? ui.buttonWidth(submitText, 13, Ui.SEMIBOLD, 16) : 0, claim > 0 ? ui.buttonWidth(claimText, 13, Ui.REGULAR, 16) + 2 : 0, ui.width(hint, 12, Ui.REGULAR)};
        float[] min = {submit > 0 ? ui.buttonMinWidth(submitText, 13, Ui.SEMIBOLD, 16) : 0, claim > 0 ? ui.buttonMinWidth(claimText, 13, Ui.REGULAR, 16) + 2 : 0, ui.minWidth(hint, 12, Ui.REGULAR)};
        float[] widths = Ui.shrink(w - PAD * 2 - 16 + (submit > 0 ? 0 : 8) + (claim > 0 ? 0 : 8), 0, base, min);
        float cursor = x + PAD;
        int top = y + 13;
        if (submit > 0) {
            ui.button(submitText, cursor, top, widths[0], 40, 10, 13, Ui.SEMIBOLD, 16, Ui.Style.ACCENT, () -> QuestKeys.sendAll(QuestActionPayload.SUBMIT_ALL));
            cursor += widths[0] + 8;
        }
        if (claim > 0) {
            ui.button(claimText, cursor, top, widths[1], 40, 10, 13, Ui.REGULAR, 16, Ui.Style.PLAIN, () -> QuestKeys.sendAll(QuestActionPayload.CLAIM_ALL));
            cursor += widths[1] + 8;
        }
        float hintX = x + w - PAD - widths[2];
        ui.lines(ui.wrap(hint, widths[2] + 1, 12, Ui.REGULAR, 3), Math.max(cursor, hintX), y + 33, 15, 12, Ui.REGULAR, ui.c("text.dim"));
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll.wheel(amount, WHEEL);
    }
}
