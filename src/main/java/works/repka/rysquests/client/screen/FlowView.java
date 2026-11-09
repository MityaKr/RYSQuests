package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Draw;

/**
 * The chapter as a vertical ribbon: finished quests folded into one line, open quests in order with the selected one
 * expanded in place, then what opens next and the rest behind a link.
 */
final class FlowView implements BookView {
    private static final int ROW = 20;
    private static final int GAP = 3;
    private static final int ACTIONS = 22;
    private static final int NEXT_CARDS = 2;
    private static final int WHEEL = 24;
    private static final int MEASURE_Y = -10000;

    private final BookScreen screen;
    private int scroll;
    /** The row the player clicked keeps its place on screen when the card opens above or below it. */
    @Nullable
    private ResourceLocation anchor;
    private int anchorY;

    FlowView(BookScreen screen) {
        this.screen = screen;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        List<Quest> quests = screen.visibleQuests();
        int listH = h - ACTIONS;
        ui.clip(x, y, w, listH);
        int cursor = y - scroll;
        if (!BookState.search.isBlank()) {
            cursor = renderSearch(ui, quests, x, cursor, w);
        } else {
            cursor = renderRibbon(ui, quests, x, cursor, w);
        }
        ui.unclip();
        int content = cursor + scroll - y;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - listH));
        renderActions(ui, x, y + h - ACTIONS + 3, w);
    }

    private int renderSearch(Ui ui, List<Quest> quests, int x, int y, int w) {
        String query = BookState.search.toLowerCase(Locale.ROOT);
        for (Quest quest : quests) {
            if (Component.translatable(quest.title()).getString().toLowerCase(Locale.ROOT).contains(query)) {
                y = renderQuest(ui, quest, x, y, w);
            }
        }
        return y;
    }

    private int renderRibbon(Ui ui, List<Quest> quests, int x, int y, int w) {
        List<Quest> done = new ArrayList<>();
        List<Quest> open = new ArrayList<>();
        List<Quest> locked = new ArrayList<>();
        for (Quest quest : quests) {
            switch (BookState.status(quest)) {
                case CLAIMED -> done.add(quest);
                case LOCKED -> locked.add(quest);
                default -> open.add(quest);
            }
        }
        if (!done.isEmpty()) {
            y = renderDoneLine(ui, done.size(), quests.size(), x, y, w);
            if (BookState.showDone) {
                for (Quest quest : done) {
                    y = renderQuest(ui, quest, x, y, w);
                }
            }
        }
        for (Quest quest : open) {
            y = renderQuest(ui, quest, x, y, w);
        }
        return renderUpcoming(ui, locked, x, y, w);
    }

    private int renderDoneLine(Ui ui, int done, int total, int x, int y, int w) {
        int rowH = ROW - 2;
        ui.theme.card(ui.g, x, y, w, rowH);
        Draw.round(ui.g, x + 4, y + 4, 10, 10, 5f, ui.color("state.ok"));
        ui.text(Component.translatable("rysquests.flow.done", done, total), x + 18, y + 5, "text.body");
        Component toggle = Component.translatable(BookState.showDone ? "rysquests.flow.collapse" : "rysquests.flow.expand");
        ui.text(toggle, x + w - ui.font.width(toggle) - 5, y + 5, "chip.mod.fg");
        ui.hit(x, y, w, rowH, () -> BookState.showDone = !BookState.showDone);
        return y + ROW + GAP;
    }

    private int renderQuest(Ui ui, Quest quest, int x, int y, int w) {
        if (quest.id().equals(anchor)) {
            scroll += y - anchorY;
            anchor = null;
        }
        if (quest.id().equals(BookState.quest)) {
            int height = ui.measure(() -> QuestCard.render(ui, quest, 0, MEASURE_Y, w));
            ui.theme.card(ui.g, x, y, w, height);
            Draw.roundBorder(ui.g, x, y, w, height, 6f, ui.color("accent"));
            QuestCard.render(ui, quest, x, y, w);
            return y + height + GAP;
        }
        return renderRow(ui, quest, x, y, w, false);
    }

    private int renderRow(Ui ui, Quest quest, int x, int y, int w, boolean dim) {
        int rowH = ROW - 2;
        boolean over = ui.over(x, y, w, rowH);
        if (over) {
            ui.theme.card(ui.g, x, y, w, rowH);
        }
        QuestStatus status = BookState.status(quest);
        quest.icon().map(TaskText::stackOf).ifPresent(stack -> ui.item(stack, x + 2, y + 1));
        String token = switch (status) {
            case LOCKED -> "text.dim";
            case CLAIMED -> "state.ok";
            case COMPLETED -> "accent";
            default -> dim ? "text.muted" : "text.primary";
        };
        ui.text(Draw.ellipsize(Component.translatable(quest.title()).getString(), w - 36), x + 21, y + 5, token);
        if (status == QuestStatus.LOCKED) {
            Draw.lock(ui.g, x + w - 11, y + 5, ui.color("text.dim"));
        } else if (status == QuestStatus.COMPLETED) {
            Draw.round(ui.g, x + w - 11, y + 5, 7, 7, 3.5f, ui.color("accent"));
        }
        int rowY = y;
        ui.hit(x, y, w, rowH, () -> {
            anchor = quest.id();
            anchorY = rowY;
            BookState.select(quest);
        });
        return y + ROW;
    }

    private int renderUpcoming(Ui ui, List<Quest> locked, int x, int y, int w) {
        if (locked.isEmpty()) {
            return y;
        }
        List<Quest> next = nextAfterSelection(locked);
        if (!next.isEmpty()) {
            ui.text(Component.translatable("rysquests.flow.next"), x + 2, y + 2, "text.muted");
            y += Ui.LINE + 3;
            int cardW = (w - GAP) / 2;
            for (int i = 0; i < next.size(); i++) {
                renderNextCard(ui, next.get(i), x + i * (cardW + GAP), y, cardW);
            }
            y += NEXT_H + GAP;
        }
        List<Quest> rest = locked.stream().filter(quest -> !next.contains(quest)).toList();
        if (rest.isEmpty()) {
            return y;
        }
        if (BookState.showRest) {
            for (Quest quest : rest) {
                y = renderQuest(ui, quest, x, y, w);
            }
            return y;
        }
        int rowH = ROW - 2;
        Draw.roundBorder(ui.g, x, y, w, rowH, 5f, ui.color("panel.border"));
        ui.text(Component.translatable("rysquests.flow.rest", rest.size()), x + 6, y + 5, "text.muted");
        Component show = Component.translatable("rysquests.flow.show");
        ui.text(show, x + w - ui.font.width(show) - 5, y + 5, "chip.mod.fg");
        ui.hit(x, y, w, rowH, () -> BookState.showRest = true);
        return y + ROW;
    }

    private static final int NEXT_H = 30;

    private static List<Quest> nextAfterSelection(List<Quest> locked) {
        List<Quest> next = new ArrayList<>();
        if (BookState.quest != null) {
            for (ResourceLocation id : ClientBook.current().unlocks().getOrDefault(BookState.quest, List.of())) {
                locked.stream().filter(quest -> quest.id().equals(id)).findFirst().ifPresent(next::add);
            }
        }
        for (Quest quest : locked) {
            if (next.size() >= NEXT_CARDS) {
                break;
            }
            if (!next.contains(quest)) {
                next.add(quest);
            }
        }
        return next.subList(0, Math.min(NEXT_CARDS, next.size()));
    }

    private void renderNextCard(Ui ui, Quest quest, int x, int y, int w) {
        ui.theme.card(ui.g, x, y, w, NEXT_H);
        quest.icon().map(TaskText::stackOf).ifPresent(stack -> ui.item(stack, x + 4, y + 7));
        ui.text(Draw.ellipsize(Component.translatable(quest.title()).getString(), w - 26), x + 23, y + 6, "text.body");
        ui.small(Draw.ellipsize(BookScreen.waitingFor(quest).getString(), (int) ((w - 26) / 0.75f)), x + 23, y + 17, "text.muted");
        ui.hit(x, y, w, NEXT_H, () -> BookState.select(quest));
    }

    private void renderActions(Ui ui, int x, int y, int w) {
        int submit = screen.readyToSubmit();
        int claim = screen.readyToClaim();
        int cursor = x;
        if (submit > 0) {
            cursor += ui.button(Component.translatable("rysquests.flow.submit_all", submit), cursor, y, 16, true,
                    () -> QuestKeys.sendAll(QuestActionPayload.SUBMIT_ALL)) + GAP;
        }
        if (claim > 0) {
            ui.button(Component.translatable("rysquests.flow.claim_all", claim), cursor, y, 16, submit == 0,
                    () -> QuestKeys.sendAll(QuestActionPayload.CLAIM_ALL));
        }
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll -= (int) (amount * WHEEL);
    }
}
