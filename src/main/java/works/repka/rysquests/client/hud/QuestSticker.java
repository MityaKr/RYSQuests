package works.repka.rysquests.client.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.screen.LevelSegments;
import works.repka.rysquests.client.screen.TaskText;
import works.repka.rysquests.client.screen.Ui;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;

/**
 * A pinned quest (mockup {@code .hud}, picture 31): its icon and title, which wraps to a second line rather than being cut (nothing can
 * be hovered over the world), and a row per task with its count. Done, it keeps its place and says "награда ждёт" with the book's key.
 * A pinned level of an achievement is titled by the achievement: "Шахтёр · до уровня 2", and has a thin bar under its count up to that
 * level's amount, in that level's medal color, as in the book; only that level, not all of them. It draws the count the server already
 * sends, nothing more is asked for it.
 */
final class QuestSticker implements Sticker {
    private static final int PAD_X = 12;
    private static final int PAD_Y = 10;
    private static final int ICON = 24;
    private static final int GAP = 7;
    private static final int ROWS = 3;
    private static final float BAR_H = 3;
    private static final float BAR_GAP = 5;

    private final Quest quest;

    QuestSticker(Quest quest) {
        this.quest = quest;
    }

    @Override
    public String id() {
        return quest.id().toString();
    }

    private boolean waiting() {
        return QuestLogic.status(quest, ClientProgress::get) == QuestStatus.COMPLETED;
    }

    /** The token of the medal color of the level this card counts to, or null when it is not a level of an achievement. */
    @Nullable
    private String medalToken() {
        if (!Achievement.isTierQuest(quest.id())) {
            return null;
        }
        for (Achievement achievement : ClientBook.current().achievements().values()) {
            for (int i = 0; i < achievement.tiers().size(); i++) {
                if (achievement.questId(achievement.tiers().get(i)).equals(quest.id())) {
                    return LevelSegments.token(i, achievement.tiers().size());
                }
            }
        }
        return null;
    }

    /** Whether the card shows the bar of its level: a level of an achievement, unfolded and not yet reached. */
    private boolean hasBar(boolean folded) {
        return !folded && !quest.tasks().isEmpty() && !waiting() && medalToken() != null;
    }

    /** The title: the quest's own, or for a level of an achievement its name and which level comes. */
    private String title() {
        if (Achievement.isTierQuest(quest.id())) {
            for (Achievement achievement : ClientBook.current().achievements().values()) {
                for (int i = 0; i < achievement.tiers().size(); i++) {
                    if (achievement.questId(achievement.tiers().get(i)).equals(quest.id())) {
                        return Component.translatable("rysquests.hud.achievement_title", Component.translatable(achievement.titleKey()), i + 1).getString();
                    }
                }
            }
        }
        return Component.translatable(quest.title()).getString();
    }

    private ItemStack icon() {
        if (quest.icon().isPresent()) {
            return TaskText.stackOf(quest.icon().get());
        }
        if (Achievement.isTierQuest(quest.id())) {
            for (Achievement achievement : ClientBook.current().achievements().values()) {
                if (achievement.tiers().stream().anyMatch(tier -> achievement.questId(tier).equals(quest.id()))) {
                    return achievement.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
                }
            }
        }
        return quest.tasks().isEmpty() ? ItemStack.EMPTY : TaskText.icon(quest.tasks().getFirst());
    }

    /** The tasks shown: open ones first, at most three. */
    private List<Integer> rows() {
        QuestProgress own = ClientProgress.get(quest.id());
        List<Integer> open = new ArrayList<>();
        List<Integer> done = new ArrayList<>();
        for (int i = 0; i < quest.tasks().size(); i++) {
            (own.task(i) >= quest.tasks().get(i).target() ? done : open).add(i);
        }
        open.addAll(done);
        return open.size() > ROWS ? open.subList(0, ROWS) : open;
    }

    private List<String> titleLines(Ui ui, int width) {
        float textW = width - PAD_X * 2 - ICON - 8 - 13 - 8;
        return ui.wrap(title(), textW, HudLook.TITLE_PX, Ui.SEMIBOLD, 2);
    }

    @Override
    public int height(Ui ui, int width, boolean folded) {
        float head = Math.max(ICON, titleLines(ui, width).size() * HudLook.TITLE_LINE);
        int rows = folded ? 0 : waiting() ? 1 : rows().size();
        float bar = hasBar(folded) ? BAR_GAP + BAR_H : 0;
        return Math.round(PAD_Y * 2 + head + rows * (GAP + Ui.lineHeight(HudLook.ROW_PX)) + bar);
    }

    @Override
    public void render(Ui ui, int x, int y, int width, boolean folded) {
        int h = height(ui, width, folded);
        HudLook.plate(ui, x, y, width, h, 0.92f, false);
        float left = x + PAD_X;
        float right = x + width - PAD_X;
        List<String> lines = titleLines(ui, width);
        float head = Math.max(ICON, lines.size() * HudLook.TITLE_LINE);
        float headTop = y + PAD_Y;
        ui.item(icon(), left, headTop + (head - ICON) / 2f, ICON);
        float textX = left + ICON + 8;
        float textTop = headTop + (head - lines.size() * HudLook.TITLE_LINE) / 2f;
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), textX, textTop + i * HudLook.TITLE_LINE + HudLook.TITLE_LINE / 2f, HudLook.TITLE_PX, Ui.SEMIBOLD, HudLook.primary(ui));
        }
        ui.icon(Icons.Shape.PIN, right - 13, headTop + HudLook.TITLE_LINE / 2f - 6.5f, 13, HudLook.muted(ui));
        if (folded) {
            return;
        }
        float rowH = Ui.lineHeight(HudLook.ROW_PX);
        float pen = headTop + head + GAP;
        if (waiting()) {
            float cy = pen + rowH / 2f;
            int ok = ui.c("state.ok");
            ui.icon(Icons.Shape.CHECK, left, cy - 6, 12, ok);
            ui.label(Component.translatable("rysquests.hud.reward_waiting").getString(), left + 12 + 8, cy, HudLook.ROW_PX, Ui.REGULAR, ok);
            HudLook.key(ui, QuestKeys.OPEN_BOOK, right, cy);
            return;
        }
        QuestProgress own = ClientProgress.get(quest.id());
        for (int index : rows()) {
            TaskDef task = quest.tasks().get(index);
            int value = Math.min(own.task(index), task.target());
            boolean done = value >= task.target();
            float cy = pen + rowH / 2f;
            int color = done ? ui.c("state.ok") : HudLook.secondary(ui);
            if (done) {
                ui.icon(Icons.Shape.CHECK, left, cy - 6, 12, color);
            } else {
                ui.circle(left + 5, cy - 1, 2, HudLook.muted(ui));
            }
            String count = task.target() > 1 ? Component.translatable("rysquests.count.of", Numbers.compact(value), Numbers.compact(task.target())).getString() : "";
            float countW = count.isEmpty() ? 0 : ui.width(count, HudLook.ROW_PX, Ui.MEDIUM) + 8;
            ui.labelFit(TaskText.name(task), left + 12 + 8, cy, right - countW - left - 20, HudLook.ROW_PX, Ui.REGULAR, color);
            if (!count.isEmpty()) {
                ui.labelRight(count, right, cy, HudLook.ROW_PX, Ui.MEDIUM, done ? color : HudLook.muted(ui));
            }
            pen += rowH + GAP;
        }
        String medal = medalToken();
        if (medal != null && !quest.tasks().isEmpty()) {
            // A level of an achievement has one task, its counter: the bar runs from zero to this level's amount.
            TaskDef task = quest.tasks().getFirst();
            float ratio = task.target() <= 0 ? 0 : Math.min(1f, (float) own.task(0) / task.target());
            ui.bar(left, pen - GAP + BAR_GAP, right - left, BAR_H, BAR_H / 2f, ratio, ui.c(medal));
        }
    }
}
