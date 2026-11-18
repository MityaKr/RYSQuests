package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.editor.BookDraft;
import works.repka.rysquests.editor.QuestEdits;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.XpReward;

/**
 * The form of a quest on the right of the editor's map (pictures 33, 34, 33c; compact 46e): name and icon, the type as one switch
 * "Основной | Побочный | Веха", "+ Следующий", "+ Побочный", the description, tasks, rewards, what it opens after, and "Ещё" for the
 * rare settings. There is no "Готово": every change is in the draft already, the form closes with its cross or a click on the canvas.
 * At its foot "В архив" instead of deleting, and "Дублировать".
 */
final class QuestForm {
    static final int WIDTH = 360;
    static final int COMPACT_WIDTH = 300;
    private static final int PAD_X = 18;
    private static final int GAP = 11;

    /** What the form asks of the page around it. */
    interface Host {
        void select(@Nullable ResourceLocation quest);

        TaskPicker picker();
    }

    private final EditorPage page;
    private final Host host;
    private final Scroll scroll = new Scroll();
    private boolean more;
    private float[] area = new float[4];

    QuestForm(EditorPage page, Host host) {
        this.page = page;
        this.host = host;
    }

    boolean contains(double x, double y) {
        return x >= area[0] && x < area[0] + area[2] && y >= area[1] && y < area[1] + area[3];
    }

    void scroll(double amount) {
        scroll.wheel(amount, 48);
    }

    void reset() {
        scroll.reset();
        more = false;
    }

    void render(Ui ui, float x, float y, float w, float h, ResourceLocation id, boolean compact) {
        EditorBook editor = EditorBook.get();
        Quest quest = editor.view().quests().get(id);
        Quest data = editor.data().quests().get(id);
        if (quest == null || data == null) {
            host.select(null);
            return;
        }
        area = new float[] {x, y, w, h};
        if (compact) {
            ui.shadow(x, y, w, h, 0, 0.8f);
        }
        ui.rect(x, y, w, h, ui.c("panel.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float footY = y + h - EdUi.FOOT_H;
        float bodyTop = y + (compact ? 12 : 16);
        ui.clip(Math.round(x + 1), Math.round(y), Math.round(w - 1), Math.round(footY - y));
        float bx = x + 1 + (compact ? 14 : PAD_X);
        float bw = w - 1 - (compact ? 28 : PAD_X * 2);
        int offset = scroll.offset();
        float pen = bodyTop - offset;
        pen += head(ui, quest, bx, pen, bw) + GAP;
        QuestEdits.Kind kind = QuestEdits.kind(data);
        pen += EdUi.wide(ui, bx, pen, bw, List.of(EdText.tr("rysquests.ed.quest.main"), EdText.tr("rysquests.ed.quest.side"), EdText.tr("rysquests.ed.quest.milestone")),
                java.util.Arrays.asList(null, null, Icons.Shape.FLAG), kind.ordinal(), index -> setKind(data, QuestEdits.Kind.values()[index])) + GAP;
        // + Следующий, + Побочный, Значок
        float row = bx;
        row += EdUi.button(ui, EdText.tr("rysquests.ed.quest.next"), Icons.Shape.PLUS, row, pen, true, false, false, () -> addAfter(data, QuestEdits.Kind.MAIN)) + 6;
        EdUi.button(ui, EdText.tr("rysquests.ed.quest.add_side"), Icons.Shape.PLUS, row, pen, true, false, false, () -> addAfter(data, QuestEdits.Kind.SIDE));
        String iconLabel = compact ? "" : EdText.tr("rysquests.ed.quest.icon");
        float sideEnd = row + EdUi.buttonWidth(ui, EdText.tr("rysquests.ed.quest.add_side"), Icons.Shape.PLUS, true) + 6;
        if (sideEnd + EdUi.buttonWidth(ui, iconLabel, Icons.Shape.HAND, true) > bx + bw) {
            // a wider font (vanilla's) leaves no room for the word: the hand alone, its name in the tooltip
            iconLabel = "";
        }
        float iw = EdUi.buttonWidth(ui, iconLabel, Icons.Shape.HAND, true);
        EdUi.button(ui, iconLabel, Icons.Shape.HAND, bx + bw - iw, pen, true, false, false, () -> iconFromHand(data));
        if (iconLabel.isEmpty()) {
            ui.tooltipIfOver(bx + bw - iw, pen, iw, EdUi.SM_H, net.minecraft.network.chat.Component.literal(EdText.tr("rysquests.ed.quest.icon")));
        }
        pen += EdUi.SM_H + GAP;
        if (!compact) {
            pen += EdUi.field(ui, page.fields, "quest-text:" + id, EdText.tr("rysquests.ed.quest.description"), bx, pen, bw, 72, quest.text().orElse(""),
                    EdText.tr("rysquests.ed.quest.description_hint"), value -> editor.typeQuestText(id, "text", value)) + GAP;
        }
        pen += tasks(ui, data, bx, pen, bw) + GAP;
        pen += rewards(ui, data, bx, pen, bw) + GAP;
        if (!compact) {
            pen += after(ui, data, bx, pen, bw) + GAP;
            pen += moreBlock(ui, data, bx, pen, bw);
        }
        ui.unclip();
        scroll.content(pen + offset - bodyTop + GAP, footY - bodyTop);
        float by = EdUi.foot(ui, x + 1, footY, w - 1);
        EdUi.ghost(ui, EdText.tr("rysquests.ed.quest.archive"), Icons.Shape.ARCHIVE, bx - 14, by, () -> archive(data));
        String dup = EdText.tr("rysquests.ed.quest.duplicate");
        float dw = EdUi.buttonWidth(ui, dup, null, false);
        EdUi.button(ui, dup, null, bx + bw - dw, by, false, false, false, () -> duplicate(data));
    }

    private float head(Ui ui, Quest quest, float x, float y, float w) {
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElseGet(() -> quest.tasks().isEmpty() ? ItemStack.EMPTY : TaskText.icon(quest.tasks().getFirst()));
        EdUi.plate(ui, icon, Icons.Shape.FLAG, x, y, 46);
        float fx = x + 46 + 10;
        float fw = w - 46 - 10 - 28 - 10;
        EdUi.input(ui, page.fields, "quest-title:" + quest.id(), fx, y + 6, fw, EdUi.INPUT_H, 13, Ui.SEMIBOLD, quest.title(), EdText.tr("rysquests.ed.quest.title_hint"),
                value -> EditorBook.get().typeQuestText(quest.id(), "title", value));
        EdUi.close(ui, x + w - 28, y + 9, () -> host.select(null));
        return 46;
    }

    private float tasks(Ui ui, Quest quest, float x, float y, float w) {
        float pen = y;
        pen += DetailPanel.rubric(ui, x, pen, w, EdText.tr("rysquests.ed.quest.tasks"), String.valueOf(quest.tasks().size())) + 6;
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            int index = i;
            ItemStack stack = EdText.taskIcon(task);
            pen += EdUi.row(ui, x, pen, w, stack.isEmpty() ? null : stack, stack.isEmpty() ? EdText.taskGlyph(task) == null ? Icons.Shape.CHECK : EdText.taskGlyph(task) : null,
                    EdText.task(task), EdText.taskSub(task), null, () -> removeTask(quest, index)) + 6;
        }
        boolean lit = host.picker().isOpen();
        pen += EdUi.add(ui, EdText.tr("rysquests.ed.quest.add_task"), x, pen, w, lit, () -> host.picker().openTask(task -> addTask(quest.id(), task)));
        return pen - y;
    }

    private float rewards(Ui ui, Quest quest, float x, float y, float w) {
        float pen = y;
        boolean once = quest.rewards().stream().anyMatch(r -> r.teamMode() == TeamMode.ONCE);
        String right = quest.rewards().isEmpty() ? null : quest.rewards().size() + " · " + EdText.tr(once ? "rysquests.ed.quest.reward_once" : "rysquests.ed.quest.reward_each");
        pen += DetailPanel.rubric(ui, x, pen, w, EdText.tr("rysquests.ed.quest.rewards"), right) + 6;
        float cw = (w - 6) / 2f;
        for (int i = 0; i < quest.rewards().size(); i++) {
            RewardDef reward = quest.rewards().get(i);
            int index = i;
            float cx = x + (i % 2) * (cw + 6);
            rewardChip(ui, reward, cx, pen, cw, () -> removeReward(quest, index));
            if (i % 2 == 1 || i == quest.rewards().size() - 1) {
                pen += 34 + 6;
            }
        }
        pen += EdUi.add(ui, EdText.tr("rysquests.ed.quest.add_reward"), x, pen, w, false, () -> host.picker().openReward(reward -> addReward(quest.id(), reward)));
        return pen - y;
    }

    /** One reward as a chip of the form: "25 опыта" with the number green, "1 монета" with its coin, an item with its icon. */
    private static void rewardChip(Ui ui, RewardDef reward, float x, float y, float w, Runnable remove) {
        ui.box(x, y, w, 34, 9, ui.c("card.bg"), ui.c("panel.border"));
        float pen = x + 10;
        float cy = y + 17;
        switch (reward) {
            case XpReward xp -> {
                String number = String.valueOf(xp.levels().orElse(xp.points().orElse(0)));
                ui.label(number, pen, cy, 12.5f, Ui.SEMIBOLD, ui.c("state.ok"));
                pen += ui.width(number, 12.5f, Ui.SEMIBOLD) + 7;
                ui.labelFit(EdText.tr(xp.levels().isPresent() ? "rysquests.ed.reward.word.levels" : "rysquests.ed.reward.word.xp"), pen, cy, x + w - 30 - pen, 12.5f, Ui.REGULAR,
                        ui.c("text.primary"));
            }
            case CurrencyReward coins -> {
                ui.circle(pen, cy - 4.5f, 9, ui.c("coin"));
                pen += 9 + 7;
                ui.labelFit(Ui.counted("rysquests.ed.reward.coins", coins.amount()), pen, cy, x + w - 30 - pen, 12.5f, Ui.REGULAR, ui.c("text.primary"));
            }
            case ItemReward item -> {
                ui.item(TaskText.stackOf(item.item()), pen - 2, cy - 8, 16);
                pen += 16 + 6;
                ui.labelFitTip(EdText.item(item.item()) + (item.count() > 1 ? " ×" + item.count() : ""), pen, cy, x + w - 30 - pen, 12.5f, Ui.REGULAR, ui.c("text.primary"));
            }
            default -> ui.labelFitTip(EdText.rewardShort(reward), pen, cy, x + w - 30 - pen, 12.5f, Ui.REGULAR, ui.c("text.primary"));
        }
        boolean over = ui.over(x + w - 24, y + 9, 16, 16);
        ui.icon(Icons.Shape.CROSS, x + w - 10 - 13, y + 10.5f, 13, ui.c(over ? "text.primary" : "text.dim"));
        ui.hit(x + w - 26, y + 7, 20, 20, remove);
    }

    private float after(Ui ui, Quest quest, float x, float y, float w) {
        float pen = y;
        boolean any = quest.requiresMode() == RequiresMode.ANY;
        String mode = EdText.tr(any ? "rysquests.ed.quest.needs_any" : "rysquests.ed.quest.needs_all");
        pen += DetailPanel.rubric(ui, x, pen, w, EdText.tr("rysquests.ed.quest.after"), quest.requires().isEmpty() ? null : mode) + 6;
        if (!quest.requires().isEmpty()) {
            float mw = ui.width(mode, 11.5f, Ui.REGULAR);
            ui.hit(x + w - mw, y, mw, 16, () -> EditorBook.get().editQuest(quest.id(), EdText.tr("rysquests.ed.log.mode", EditorBook.get().text(quest.title())),
                    q -> QuestEdits.withRequires(q, q.requires(), any ? RequiresMode.ALL : RequiresMode.ANY)));
        }
        QuestBook book = EditorBook.get().view();
        float row = x;
        for (ResourceLocation dependency : quest.requires()) {
            Quest other = book.quests().get(dependency);
            String name = other == null ? dependency.toString() : other.title();
            float tw = 7 + ui.width(name, 12, Ui.MEDIUM) + 4 + 11 + 7;
            if (row + tw > x + w && row > x) {
                row = x;
                pen += 24 + 6;
            }
            row += EdUi.tag(ui, name, row, pen, () -> QuestTable.link(quest.id(), dependency)) + 6;
        }
        String add = EdText.tr("rysquests.ed.quest.add_link");
        float aw = 7 + 11 + 4 + ui.width(add, 12, Ui.MEDIUM) + 7;
        if (row + aw > x + w && row > x) {
            row = x;
            pen += 24 + 6;
        }
        float ax = row;
        float ay = pen;
        EdUi.addTag(ui, add, row, pen, () -> {
            List<Quest> options = book.quests().values().stream().filter(q -> q.chapter().equals(quest.chapter()) && !q.id().equals(quest.id())
                    && !quest.requires().contains(q.id()) && !q.options().archived()).toList();
            page.choices.toggle("link:" + quest.id(), ax, ay + 28, 260, options.stream().map(Quest::title).toList(), -1,
                    index -> QuestTable.link(quest.id(), options.get(index).id()));
        });
        pen += 24;
        return pen - y;
    }

    private float moreBlock(Ui ui, Quest quest, float x, float y, float w) {
        float pen = y;
        boolean over = ui.over(x, pen, w, 20);
        ui.chevron(more, x, pen + 10, 13, ui.c("text.muted"));
        ui.labelFitTip(EdText.tr("rysquests.ed.quest.more"), x + 13 + 8, pen + 10, w - 13 - 8, 12.5f, Ui.REGULAR, ui.c(over ? "text.primary" : "text.muted"));
        ui.hit(x, pen, w, 20, () -> more = !more);
        pen += 20;
        if (!more) {
            return pen - y;
        }
        EditorBook editor = EditorBook.get();
        String title = editor.text(quest.title());
        pen += 6;
        pen += EdUi.setting(ui, x, pen, w, EdText.tr("rysquests.ed.quest.hidden"), EdText.tr("rysquests.ed.quest.hidden_sub"), quest.options().hidden(),
                () -> editor.editQuest(quest.id(), EdText.tr("rysquests.ed.log.hidden", title), q -> QuestEdits.withHidden(q, !q.options().hidden()))) + 4;
        pen += EdUi.setting(ui, x, pen, w, EdText.tr("rysquests.ed.quest.repeatable"), EdText.tr("rysquests.ed.quest.repeatable_sub"), quest.options().repeatable().isPresent(),
                () -> editor.editQuest(quest.id(), EdText.tr("rysquests.ed.log.repeatable", title), q -> QuestEdits.withRepeatable(q, q.options().repeatable().isEmpty()))) + 4;
        // Whether the tasks count while the quest is locked (ADR-66): its own word, or the chapter's.
        works.repka.rysquests.data.QuestBook draft = editor.data();
        boolean chapterSays = works.repka.rysquests.progress.QuestLogic.preComplete(java.util.Optional.empty(),
                quest.chapter().map(id -> draft.chapters().get(id)).flatMap(works.repka.rysquests.data.Chapter::preComplete), draft.meta().preComplete());
        pen += EdUi.preComplete(ui, page.choices, "pre-complete:" + quest.id(), x, pen, w, "rysquests.ed.pre_complete.as_chapter", chapterSays,
                quest.options().preComplete(), value -> editor.editQuest(quest.id(), EdText.tr("rysquests.ed.log.pre_complete", title),
                        q -> QuestEdits.withOptions(q, q.options().withPreComplete(value)))) + 6;
        pen += EdUi.field(ui, page.fields, "quest-stage:" + quest.id(), EdText.tr("rysquests.ed.quest.stage"), x, pen, w, EdUi.INPUT_H, quest.stageRequired().orElse(""),
                EdText.tr("rysquests.ed.quest.stage_hint"), value -> editor.type("quest-stage:" + quest.id(), EditorBook.chapterScope(quest.chapter().get()),
                        EdText.tr("rysquests.ed.log.stage", title), d -> {
                            BookDraft.Work work = d.chapters().containsKey(quest.chapter().get()) ? d.chapters().get(quest.chapter().get()) : editor.work(quest.chapter().get());
                            Quest current = work.questMap().get(quest.id());
                            return d.withWork(work.withQuest(new Quest(current.id(), current.chapter(), current.group(), current.title(), current.subtitle(), current.text(), current.icon(),
                                    current.requires(), current.requiresMode(), value.isBlank() ? Optional.empty() : Optional.of(value.trim()), current.tasks(), current.rewards(),
                                    current.guide(), current.options())));
                        })) + 6;
        String guide = quest.guide().map(ref -> ref.toString()).orElse(EdText.tr("rysquests.ed.quest.no_guide"));
        ui.label(EdText.tr("rysquests.ed.quest.guide", guide), x, pen + 9, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        pen += 18;
        return pen - y;
    }

    // --- changes ---

    private void setKind(Quest quest, QuestEdits.Kind kind) {
        EditorBook editor = EditorBook.get();
        editor.editQuest(quest.id(), EdText.tr("rysquests.ed.log.kind", editor.text(quest.title()), EdText.kind(kind)), q -> QuestEdits.withKind(q, kind));
    }

    private void addAfter(Quest quest, QuestEdits.Kind kind) {
        EditorBook editor = EditorBook.get();
        String title = EdText.tr(kind == QuestEdits.Kind.SIDE ? "rysquests.ed.quest.new_side" : "rysquests.ed.quest.new_next");
        ResourceLocation id = editor.addQuest(quest.chapter().orElseThrow(), title, kind, List.of(quest.id()), quest.id());
        host.select(id);
        page.fields.focus("quest-title:" + id);
    }

    private void iconFromHand(Quest quest) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            works.repka.rysquests.client.ClientMessages.show("rysquests.ed.hand_empty", List.of(), false);
            return;
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
        EditorBook editor = EditorBook.get();
        editor.editQuest(quest.id(), EdText.tr("rysquests.ed.log.icon", editor.text(quest.title())), q -> QuestEdits.withIcon(q, item));
    }

    static void addTask(ResourceLocation id, TaskDef task) {
        EditorBook editor = EditorBook.get();
        Quest quest = editor.quest(id);
        if (quest == null) {
            return;
        }
        editor.editQuest(id, EdText.tr("rysquests.ed.log.task", editor.text(quest.title()), EdText.task(task)), q -> {
            List<TaskDef> tasks = new ArrayList<>(q.tasks());
            tasks.add(task);
            return QuestEdits.withTasks(q, tasks);
        });
    }

    static void addReward(ResourceLocation id, RewardDef reward) {
        EditorBook editor = EditorBook.get();
        Quest quest = editor.quest(id);
        if (quest == null) {
            return;
        }
        editor.editQuest(id, EdText.tr("rysquests.ed.log.reward", editor.text(quest.title()), EdText.rewardShort(reward)), q -> {
            List<RewardDef> rewards = new ArrayList<>(q.rewards());
            rewards.add(reward);
            return QuestEdits.withRewards(q, rewards);
        });
    }

    private void removeTask(Quest quest, int index) {
        EditorBook editor = EditorBook.get();
        editor.editQuest(quest.id(), EdText.tr("rysquests.ed.log.task_removed", editor.text(quest.title())), q -> {
            List<TaskDef> tasks = new ArrayList<>(q.tasks());
            if (index < tasks.size()) {
                tasks.remove(index);
            }
            return QuestEdits.withTasks(q, tasks);
        });
    }

    private void removeReward(Quest quest, int index) {
        EditorBook editor = EditorBook.get();
        editor.editQuest(quest.id(), EdText.tr("rysquests.ed.log.reward_removed", editor.text(quest.title())), q -> {
            List<RewardDef> rewards = new ArrayList<>(q.rewards());
            if (index < rewards.size()) {
                rewards.remove(index);
            }
            return QuestEdits.withRewards(q, rewards);
        });
    }

    private void archive(Quest quest) {
        EditorBook editor = EditorBook.get();
        editor.editQuest(quest.id(), EdText.tr("rysquests.ed.log.archive", editor.text(quest.title())), q -> QuestEdits.withArchived(q, true));
        host.select(null);
    }

    private void duplicate(Quest quest) {
        EditorBook editor = EditorBook.get();
        ResourceLocation chapter = quest.chapter().orElseThrow();
        ResourceLocation id = QuestEdits.freshQuest(chapter, new java.util.HashSet<>(editor.data().quests().keySet()));
        Quest copy = QuestEdits.withOptions(QuestEdits.duplicate(quest, id, 0, 0), quest.options());
        copy = QuestEdits.withRequires(copy, quest.requires(), quest.requiresMode());
        Quest made = copy;
        String title = EdText.tr("rysquests.ed.quest.copy_of", editor.text(quest.title()));
        editor.edit(EditorBook.chapterScope(chapter), EdText.tr("rysquests.ed.log.new_quest", title), d -> {
            BookDraft.Work work = d.chapters().containsKey(chapter) ? d.chapters().get(chapter) : editor.work(chapter);
            BookDraft out = d.withWork(work.withQuestAfter(made, quest.id())).withText(made.title(), title);
            if (made.text().isPresent() && quest.text().isPresent()) {
                out = out.withText(made.text().get(), editor.text(quest.text().get()));
            }
            return out;
        });
        host.select(id);
    }

    static String coins(long amount) {
        return Numbers.compact(amount);
    }
}
