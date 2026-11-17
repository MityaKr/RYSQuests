package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.editor.BookDraft;
import works.repka.rysquests.editor.QuestEdits;

/**
 * A chapter as a table (pictures 35b and 09): a row per quest, columns "Квест, Тип, Что сделать, Награда, После", side quests right under
 * their main one. A click edits a cell, Tab always goes to the next cell, Ctrl+→ makes the quest a side one of the row above, and the
 * last row is the quick entry: Enter adds the quest and stays there for the next. Ticked rows get actions for all of them at once.
 *
 * <p>The same component serves every chapter of the editor and the first chapter of a new book; the page around it draws the tools
 * above it and decides what stands beside it.
 */
final class QuestTable {
    static final int HEAD_H = 32;
    static final int ROW_H = 38;
    static final int BAR_H = 28;
    private static final int PAD = 14;
    private static final int COL_GAP = 12;
    private static final int WHEEL = 38;

    /** A column that can be edited, in the order Tab walks them. */
    enum Column {
        TITLE, TYPE, TASK, REWARD, AFTER
    }

    /** What the table asks of its page. */
    interface Host {
        /** Opens the list of tasks to add one to the quest (picture 34). */
        void pickTask(ResourceLocation quest);

        /** The quests chosen changed: the small map beside lights them. */
        default void selected(Set<ResourceLocation> quests) {
        }
    }

    private final EditorPage page;
    private final Host host;
    private final Scroll scroll = new Scroll();
    private final Set<ResourceLocation> chosen = new LinkedHashSet<>();
    @Nullable
    private ResourceLocation chapter;
    @Nullable
    private ResourceLocation editRow;
    @Nullable
    private Column editColumn;
    private int filter;
    private boolean compact;
    private float[] area = new float[4];
    private String entry = "";

    QuestTable(EditorPage page, Host host) {
        this.page = page;
        this.host = host;
    }

    void show(@Nullable ResourceLocation newChapter) {
        if (newChapter == null || !newChapter.equals(chapter)) {
            chapter = newChapter;
            chosen.clear();
            editRow = null;
            scroll.reset();
            filter = 0;
        }
    }

    @Nullable
    ResourceLocation chapter() {
        return chapter;
    }

    Set<ResourceLocation> chosen() {
        return chosen;
    }

    void setCompact(boolean value) {
        compact = value;
    }

    boolean contains(double x, double y) {
        return x >= area[0] && x < area[0] + area[2] && y >= area[1] && y < area[1] + area[3];
    }

    void scroll(double amount) {
        scroll.wheel(amount, WHEEL);
    }

    // --- rows ---

    /** A row: the quest and whether it hangs under the main one above. */
    record Row(Quest quest, boolean child) {
    }

    /** The quests of the chapter in the table's order: main quests in the order of the story, each followed by its side quests. */
    List<Row> rows(QuestBook book) {
        if (chapter == null) {
            return List.of();
        }
        List<Quest> ordered = ChapterOrder.of(book, chapter).stream().filter(q -> !q.options().archived()).toList();
        // the order of the draft wins where it has one: a quest typed into the quick entry stays where it was typed
        BookDraft.Work work = EditorBook.get().draft().chapters().get(chapter);
        if (work != null) {
            Map<ResourceLocation, Integer> drafted = new LinkedHashMap<>();
            for (int i = 0; i < work.quests().size(); i++) {
                drafted.put(work.quests().get(i).id(), i);
            }
            List<Quest> sorted = new ArrayList<>(ordered);
            sorted.sort(java.util.Comparator.comparingInt(q -> drafted.getOrDefault(q.id(), Integer.MAX_VALUE)));
            if (isTopological(sorted)) {
                ordered = sorted;
            }
        }
        Map<ResourceLocation, List<Quest>> children = new LinkedHashMap<>();
        List<Quest> mains = new ArrayList<>();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        ordered.forEach(q -> ids.add(q.id()));
        for (Quest quest : ordered) {
            ResourceLocation parent = quest.options().optional() ? quest.requires().stream().filter(ids::contains)
                    .filter(id -> !book.quests().get(id).options().optional()).findFirst().orElse(null) : null;
            if (parent != null) {
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(quest);
            } else {
                mains.add(quest);
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Quest main : mains) {
            rows.add(new Row(main, false));
            children.getOrDefault(main.id(), List.of()).forEach(child -> rows.add(new Row(child, true)));
        }
        return switch (filter) {
            case 1 -> rows.stream().filter(row -> !row.quest().options().optional()).toList();
            case 2 -> rows.stream().filter(row -> row.quest().options().optional()).toList();
            case 3 -> rows.stream().filter(row -> row.quest().rewards().isEmpty()).toList();
            default -> rows;
        };
    }

    private static boolean isTopological(List<Quest> quests) {
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        Set<ResourceLocation> all = new LinkedHashSet<>();
        quests.forEach(q -> all.add(q.id()));
        for (Quest quest : quests) {
            for (ResourceLocation dependency : quest.requires()) {
                if (all.contains(dependency) && !seen.contains(dependency)) {
                    return false;
                }
            }
            seen.add(quest.id());
        }
        return true;
    }

    /** The filter chips of the tools row: "Все · 43", "Основные · 15", "Побочные · 28", "Без награды · 3". */
    List<String> chipLabels(QuestBook book) {
        int old = filter;
        filter = 0;
        List<Row> all = rows(book);
        filter = old;
        long main = all.stream().filter(row -> !row.quest().options().optional()).count();
        long noReward = all.stream().filter(row -> row.quest().rewards().isEmpty()).count();
        return List.of(EditorBook.tr("rysquests.ed.table.all", all.size()), EditorBook.tr("rysquests.ed.table.main", main),
                EditorBook.tr("rysquests.ed.table.side", all.size() - main), EditorBook.tr("rysquests.ed.table.no_reward", noReward));
    }

    int filter() {
        return filter;
    }

    void setFilter(int value) {
        filter = value;
        scroll.reset();
    }

    // --- drawing ---

    /** The columns' x and widths for a width: {@code [x, w]} per column of the mockup's grid. */
    private float[][] columns(float x, float w) {
        float inner = w - PAD * 2;
        float fixed = 22 + 28 + 96 + COL_GAP * 6;
        float[] fr = compact ? new float[] {1.6f, 0, 1.6f, 0, 0} : new float[] {1.5f, 0, 1.5f, 1f, 1.3f};
        float total = fr[0] + fr[2] + fr[3] + fr[4];
        float free = inner - fixed + (compact ? COL_GAP * 2 : 0);
        float[][] out = new float[7][2];
        float pen = x + PAD;
        float[] widths = {22, 28, free * fr[0] / total, 96, free * fr[2] / total, free * fr[3] / total, free * fr[4] / total};
        for (int i = 0; i < 7; i++) {
            out[i][0] = pen;
            out[i][1] = widths[i];
            if (widths[i] > 0) {
                pen += widths[i] + COL_GAP;
            }
        }
        return out;
    }

    /** The bar of actions for the ticked rows ({@code Выбрано 2 · Сделать побочными …}). @return its height */
    float renderBar(Ui ui, float x, float y, float w) {
        QuestBook book = EditorBook.get().view();
        List<Quest> picked = chosen.stream().map(book.quests()::get).filter(java.util.Objects::nonNull).toList();
        if (picked.isEmpty()) {
            ui.label(EditorBook.tr("rysquests.ed.table.hint"), x, y + BAR_H / 2f, 12.5f, Ui.REGULAR, ui.c("text.dim"));
            return BAR_H;
        }
        String head = picked.size() == 1 ? EditorBook.tr("rysquests.ed.table.chosen_one", EditorBook.get().text(picked.getFirst().title()))
                : EditorBook.tr("rysquests.ed.table.chosen", picked.size());
        ui.label(head, x, y + BAR_H / 2f, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        float pen = x + ui.width(head, 12.5f, Ui.SEMIBOLD) + 8;
        boolean many = picked.size() > 1;
        pen += EdUi.button(ui, EditorBook.tr(many ? "rysquests.ed.table.make_side_many" : "rysquests.ed.table.make_side"), null, pen, y, true, false, false,
                () -> setKind(picked, QuestEdits.Kind.SIDE)) + 8;
        if (many) {
            pen += EdUi.button(ui, EditorBook.tr("rysquests.ed.table.reward_all"), null, pen, y, true, false, false, () -> rewardAll(picked)) + 8;
        }
        pen += EdUi.button(ui, EditorBook.tr(many ? "rysquests.ed.table.make_milestones" : "rysquests.ed.table.make_milestone"), Icons.Shape.FLAG, pen, y, true, false, false,
                () -> setKind(picked, QuestEdits.Kind.MILESTONE)) + 8;
        if (many) {
            pen += EdUi.button(ui, EditorBook.tr("rysquests.ed.table.other_chapter"), null, pen, y, true, false, false, () -> moveTo(ui, picked)) + 8;
        }
        EdUi.button(ui, EditorBook.tr("rysquests.ed.table.archive"), Icons.Shape.ARCHIVE, pen, y, true, false, false, () -> archive(picked));
        return BAR_H;
    }

    /** Draws the table from y down, within h. @return the height it took */
    float render(Ui ui, float x, float y, float w, float h) {
        EditorBook editor = EditorBook.get();
        QuestBook book = editor.view();
        QuestBook data = editor.data();
        List<Row> rows = rows(book);
        float content = HEAD_H + (rows.size() + 1) * ROW_H;
        float shown = Math.min(h, content + 2);
        area = new float[] {x, y, w, shown};
        ui.box(x, y, w, shown, 12, ui.c("card.bg"), ui.c("panel.border"));
        float[][] col = columns(x, w);
        // head
        ui.fillCorners(x + 1, y + 1, w - 2, HEAD_H - 1, 11, ui.c("sidebar.bg"), true, true, false, false);
        float hy = y + HEAD_H / 2f;
        String[] heads = {"quest", "type", "task", "reward", "after"};
        int[] at = {2, 3, 4, 5, 6};
        for (int i = 0; i < heads.length; i++) {
            if (col[at[i]][1] > 0) {
                ui.caps(EditorBook.tr("rysquests.ed.table.col." + heads[i]), col[at[i]][0], hy, ui.c("text.dim"));
            }
        }
        float bodyTop = y + HEAD_H;
        float bodyH = shown - HEAD_H - 1;
        ui.clip(Math.round(x + 1), Math.round(bodyTop), Math.round(w - 2), Math.round(bodyH));
        int offset = scroll.offset();
        float pen = bodyTop - offset;
        for (Row row : rows) {
            if (ui.rowVisible(pen, ROW_H)) {
                renderRow(ui, row, data.quests().getOrDefault(row.quest().id(), row.quest()), book, x, pen, w, col);
            }
            pen += ROW_H;
        }
        renderEntry(ui, x, pen, w, col, rows);
        pen += ROW_H;
        ui.unclip();
        scroll.content(pen + offset - bodyTop, bodyH);
        return shown;
    }

    private void renderRow(Ui ui, Row row, Quest data, QuestBook book, float x, float y, float w, float[][] col) {
        Quest quest = row.quest();
        ResourceLocation id = quest.id();
        boolean on = chosen.contains(id);
        ui.rect(x + 1, y, w - 2, 1, ui.c("panel.border"));
        if (on) {
            ui.rect(x + 1, y, w - 2, ROW_H, ui.c("tab.active.bg"));
            ui.stroke(x + 1, y, w - 2, ROW_H, 0, ui.c("text.secondary"));
        }
        float cy = y + ROW_H / 2f;
        int fg = ui.c("text.secondary");
        // tick
        float cbx = col[0][0];
        ui.box(cbx, cy - 7.5f, 15, 15, 4, ui.c(on ? "state.ok" : "card.bg"), ui.c(on ? "state.ok" : "text.dim"));
        if (on) {
            ui.check(cbx + 2.5f, cy - 5, 10, ui.c("sidebar.bg"));
        }
        ui.hit(cbx - 4, y, 22 + 8, ROW_H, () -> toggle(id));
        // icon
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElseGet(() -> quest.tasks().isEmpty() ? ItemStack.EMPTY : TaskText.icon(quest.tasks().getFirst()));
        ui.box(col[1][0], cy - 12, 24, 24, 5, ui.c("slot.bg"), ui.c("slot.border"));
        ui.item(icon, col[1][0] + 4, cy - 8, 16);
        ui.hit(col[1][0], y, 28, ROW_H, () -> only(id));
        // title
        float tx = col[2][0] + (row.child() ? 14 : 0);
        float tw = col[2][1] - (row.child() ? 14 : 0);
        if (isEditing(id, Column.TITLE)) {
            cell(ui, tx - 7, cy, tw + 7);
            EdUi.Fields f = page.fields;
            TextArea area = f.area(fieldId(id, Column.TITLE), false, 12.5f, Ui.MEDIUM, quest.title(), value -> EditorBook.get().typeQuestText(id, "title", value));
            area.render(ui, tx, cy - 9, tw - 6, 18, ui.c("text.primary"), "");
        } else {
            ui.labelFitTip(quest.title(), tx, cy, tw, 12.5f, Ui.MEDIUM, ui.c("text.primary"));
        }
        ui.hit(tx, y, tw, ROW_H, () -> edit(id, Column.TITLE));
        // type
        QuestEdits.Kind kind = QuestEdits.kind(quest);
        if (isEditing(id, Column.TYPE)) {
            cell(ui, col[3][0] - 7, cy, col[3][1]);
            ui.label(EdText.kind(kind), col[3][0], cy, 12.5f, Ui.MEDIUM, ui.c("text.primary"));
            ui.icon(Icons.Shape.CHEVRON_DOWN, col[3][0] + col[3][1] - 7 - 11 - 4, cy - 5.5f, 11, ui.c("text.muted"));
        } else {
            ui.label(EdText.kind(kind), col[3][0], cy, 12.5f, Ui.REGULAR, ui.c(kind == QuestEdits.Kind.SIDE ? "text.muted" : "text.secondary"));
        }
        float typeX = col[3][0] - 7;
        ui.hit(col[3][0], y, col[3][1], ROW_H, () -> {
            edit(id, Column.TYPE);
            openType(id, typeX, y + ROW_H - 4, kind);
        });
        // task
        String tasks = EdText.tasks(quest);
        boolean noTask = tasks.isEmpty();
        String taskText = noTask ? EditorBook.tr(quest.options().page() ? "rysquests.ed.table.page" : "rysquests.ed.table.no_task") : tasks;
        if (isEditing(id, Column.TASK)) {
            cell(ui, col[4][0] - 7, cy, col[4][1] + 7);
        }
        ui.labelFitTip(taskText, col[4][0], cy, col[4][1], 12.5f, Ui.REGULAR, noTask ? ui.c("text.dim") : fg);
        ui.hit(col[4][0], y, col[4][1], ROW_H, () -> {
            edit(id, Column.TASK);
            host.pickTask(id);
        });
        if (col[5][1] > 0) {
            String rewards = EdText.rewards(quest);
            if (isEditing(id, Column.REWARD)) {
                cell(ui, col[5][0] - 7, cy, col[5][1] + 7);
                TextArea area = page.fields.area(fieldId(id, Column.REWARD), false, 12.5f, Ui.REGULAR, entryReward(), value -> entry = value);
                if (area.text().isEmpty()) {
                    ui.icon(Icons.Shape.PLUS, col[5][0], cy - 5.5f, 11, ui.c("text.muted"));
                }
                area.render(ui, col[5][0] + (area.text().isEmpty() ? 15 : 0), cy - 9, col[5][1] - 15, 18, ui.c("text.primary"),
                        EditorBook.tr("rysquests.ed.table.reward_hint"));
            } else {
                ui.labelFitTip(rewards.isEmpty() ? EditorBook.tr("rysquests.ed.table.none") : rewards, col[5][0], cy, col[5][1], 12.5f, Ui.REGULAR,
                        rewards.isEmpty() ? ui.c("text.dim") : fg);
            }
            ui.hit(col[5][0], y, col[5][1], ROW_H, () -> edit(id, Column.REWARD));
        }
        if (col[6][1] > 0) {
            List<String> names = quest.requires().stream().map(book.quests()::get).filter(java.util.Objects::nonNull).map(Quest::title).toList();
            String after = names.isEmpty() ? EditorBook.tr("rysquests.ed.table.open_at_once") : String.join(", ", names);
            if (isEditing(id, Column.AFTER)) {
                cell(ui, col[6][0] - 7, cy, col[6][1] + 7);
            }
            ui.labelFitTip(after, col[6][0], cy, col[6][1], 12.5f, Ui.REGULAR, names.isEmpty() ? ui.c("text.dim") : fg);
            float afterX = col[6][0] - 7;
            float afterW = col[6][1] + 7;
            ui.hit(col[6][0], y, col[6][1], ROW_H, () -> {
                edit(id, Column.AFTER);
                openAfter(id, afterX, y + ROW_H - 4, afterW);
            });
        }
    }

    private static void cell(Ui ui, float x, float cy, float w) {
        ui.box(x, cy - 13, w, 26, 6, ui.c("input.bg"), ui.c("text.secondary"));
    }

    private void renderEntry(Ui ui, float x, float y, float w, float[][] col, List<Row> rows) {
        ui.rect(x + 1, y, w - 2, 1, ui.c("panel.border"));
        float cy = y + ROW_H / 2f;
        ui.icon(Icons.Shape.PLUS, col[0][0] + 2, cy - 6, 12, ui.c("text.muted"));
        TextArea area = page.fields.area("table-entry", false, 12.5f, Ui.REGULAR, "", value -> { });
        area.render(ui, col[2][0], cy - 9, w - (col[2][0] - x) - PAD, 18, ui.c("text.primary"), EditorBook.tr("rysquests.ed.table.entry_hint"));
        ui.hit(x, y, w, ROW_H, () -> {
            editRow = null;
            page.fields.click(area);
        });
    }

    // --- editing ---

    private static String fieldId(ResourceLocation id, Column column) {
        return "table:" + column + ":" + id;
    }

    private boolean isEditing(ResourceLocation id, Column column) {
        return id.equals(editRow) && column == editColumn;
    }

    private String entryReward() {
        return entry;
    }

    void edit(ResourceLocation id, Column column) {
        commitReward();
        editRow = id;
        editColumn = column;
        entry = "";
        if (column == Column.TITLE || column == Column.REWARD) {
            page.fields.focus(fieldId(id, column));
        } else {
            page.fields.blur();
        }
    }

    private void toggle(ResourceLocation id) {
        if (!chosen.remove(id)) {
            chosen.add(id);
        }
        host.selected(chosen);
    }

    private void only(ResourceLocation id) {
        chosen.clear();
        chosen.add(id);
        host.selected(chosen);
    }

    private void openType(ResourceLocation id, float x, float y, QuestEdits.Kind kind) {
        List<String> labels = List.of(EdText.kind(QuestEdits.Kind.MAIN), EdText.kind(QuestEdits.Kind.SIDE), EdText.kind(QuestEdits.Kind.MILESTONE));
        page.choices.toggle("type:" + id, x, y, 140, labels, kind.ordinal(), index -> {
            Quest quest = EditorBook.get().quest(id);
            if (quest != null) {
                setKind(List.of(quest), QuestEdits.Kind.values()[index]);
            }
        });
    }

    private void openAfter(ResourceLocation id, float x, float y, float w) {
        QuestBook book = EditorBook.get().view();
        List<Quest> options = rows(book).stream().map(Row::quest).filter(q -> !q.id().equals(id)).toList();
        Quest self = book.quests().get(id);
        List<String> labels = options.stream().map(q -> self != null && self.requires().contains(q.id()) ? EditorBook.tr("rysquests.ed.table.unlink", q.title()) : q.title()).toList();
        page.choices.toggle("after:" + id, x, y, Math.max(w, 220), labels, -1, index -> link(id, options.get(index).id()));
    }

    /** Adds or takes away "after": a link that would close a circle is refused with the reason (picture 33c). */
    static void link(ResourceLocation quest, ResourceLocation after) {
        EditorBook editor = EditorBook.get();
        QuestBook data = editor.data();
        Quest target = data.quests().get(quest);
        if (target == null) {
            return;
        }
        boolean adding = !target.requires().contains(after);
        if (adding && QuestEdits.wouldCycle(data.quests(), quest, after)) {
            works.repka.rysquests.client.ClientMessages.show("rysquests.ed.link.cycle_message", List.of(editor.text(target.title()), editor.text(data.quests().get(after).title())), false);
            return;
        }
        String line = EditorBook.tr(adding ? "rysquests.ed.log.link" : "rysquests.ed.log.unlink", editor.text(data.quests().get(after).title()), editor.text(target.title()));
        editor.editQuest(quest, line, q -> QuestEdits.toggled(q, after));
    }

    private void setKind(List<Quest> quests, QuestEdits.Kind kind) {
        EditorBook editor = EditorBook.get();
        if (quests.isEmpty()) {
            return;
        }
        ResourceLocation chapterId = quests.getFirst().chapter().orElse(chapter);
        String line = quests.size() == 1 ? EditorBook.tr("rysquests.ed.log.kind", editor.text(quests.getFirst().title()), EdText.kind(kind))
                : EditorBook.tr("rysquests.ed.log.kind_many", Plural.full("rysquests.plural.quest", quests.size()), EdText.kind(kind));
        QuestBook view = editor.view();
        List<Row> rows = rows(view);
        editor.editChapter(chapterId, line, work -> {
            BookDraft.Work out = work;
            for (Quest quest : quests) {
                Quest current = out.questMap().get(quest.id());
                if (current == null) {
                    continue;
                }
                Quest changed = QuestEdits.withKind(current, kind);
                // a side quest hangs on the main one above it, as Ctrl+→ does
                if (kind == QuestEdits.Kind.SIDE && current.requires().isEmpty()) {
                    ResourceLocation above = mainAbove(rows, quest.id());
                    if (above != null) {
                        changed = QuestEdits.withRequires(changed, List.of(above), changed.requiresMode());
                    }
                }
                out = out.withQuest(changed);
            }
            return out;
        });
    }

    @Nullable
    private static ResourceLocation mainAbove(List<Row> rows, ResourceLocation id) {
        ResourceLocation last = null;
        for (Row row : rows) {
            if (row.quest().id().equals(id)) {
                return last;
            }
            if (!row.quest().options().optional()) {
                last = row.quest().id();
            }
        }
        return null;
    }

    /** "Награда всем: …": the reward typed into the cell of the first ticked row goes to all of them. */
    private void rewardAll(List<Quest> picked) {
        if (!picked.isEmpty()) {
            edit(picked.getFirst().id(), Column.REWARD);
            bulkReward = true;
        }
    }

    private boolean bulkReward;

    private void moveTo(Ui ui, List<Quest> picked) {
        EditorBook editor = EditorBook.get();
        QuestBook book = editor.view();
        List<works.repka.rysquests.data.Chapter> targets = EditorSidebar.ordered(book).stream().filter(c -> c.isStory() && !c.id().equals(chapter)).toList();
        page.choices.toggle("move", area[0] + 300, area[1], 260, targets.stream().map(c -> editor.text(c.title())).toList(), -1, index -> {
            ResourceLocation to = targets.get(index).id();
            String line = EditorBook.tr("rysquests.ed.log.move", Plural.full("rysquests.plural.quest", picked.size()), book.chapters().get(to).title());
            editor.edit(EditorBook.chapterScope(to), line, d -> {
                BookDraft out = d;
                BookDraft.Work from = editor.work(chapter);
                BookDraft.Work target = out.chapters().containsKey(to) ? out.chapters().get(to) : editor.work(to);
                if (from == null || target == null) {
                    return d;
                }
                for (Quest quest : picked) {
                    Quest current = from.questMap().getOrDefault(quest.id(), quest);
                    from = from.withoutQuest(quest.id());
                    target = target.withQuest(current.withChapter(to));
                }
                return out.withWork(from).withWork(target);
            });
            chosen.clear();
        });
    }

    private void archive(List<Quest> picked) {
        EditorBook editor = EditorBook.get();
        String line = picked.size() == 1 ? EditorBook.tr("rysquests.ed.log.archive", editor.text(picked.getFirst().title()))
                : EditorBook.tr("rysquests.ed.log.archive_many", Plural.full("rysquests.plural.quest", picked.size()));
        editor.editChapter(chapter, line, work -> {
            BookDraft.Work out = work;
            for (Quest quest : picked) {
                Quest current = out.questMap().get(quest.id());
                if (current != null) {
                    out = out.withQuest(QuestEdits.withArchived(current, true));
                }
            }
            return out;
        });
        chosen.clear();
    }

    /** A reward typed into its cell lands when the cell is left: Enter, Tab or a click elsewhere. */
    private void commitReward() {
        if (editRow == null || editColumn != Column.REWARD || entry.isBlank()) {
            return;
        }
        Optional<RewardDef> reward = EdText.parseReward(entry);
        String typed = entry;
        entry = "";
        if (reward.isEmpty()) {
            works.repka.rysquests.client.ClientMessages.show("rysquests.ed.table.reward_unknown", List.of(typed), false);
            return;
        }
        EditorBook editor = EditorBook.get();
        List<ResourceLocation> targets = bulkReward ? List.copyOf(chosen) : List.of(editRow);
        bulkReward = false;
        String line = targets.size() == 1 ? EditorBook.tr("rysquests.ed.log.reward", editor.text(editor.quest(targets.getFirst()).title()), EdText.rewardShort(reward.get()))
                : EditorBook.tr("rysquests.ed.log.reward_many", Plural.full("rysquests.ed.plural.quest_dat", targets.size()), EdText.rewardShort(reward.get()));
        editor.editChapter(chapter, line, work -> {
            BookDraft.Work out = work;
            for (ResourceLocation id : targets) {
                Quest current = out.questMap().get(id);
                if (current != null) {
                    List<RewardDef> rewards = new ArrayList<>(current.rewards());
                    rewards.add(reward.get());
                    out = out.withQuest(QuestEdits.withRewards(current, rewards));
                }
            }
            return out;
        });
    }

    /** Enter in the quick entry adds a quest after the last row, and the entry stays for the next one. */
    private void addFromEntry(String title) {
        if (chapter == null || title.isBlank()) {
            return;
        }
        EditorBook editor = EditorBook.get();
        List<Row> rows = rows(editor.view());
        ResourceLocation lastMain = null;
        ResourceLocation last = null;
        for (Row row : rows) {
            last = row.quest().id();
            if (!row.quest().options().optional()) {
                lastMain = row.quest().id();
            }
        }
        editor.addQuest(chapter, title.trim(), QuestEdits.Kind.MAIN, lastMain == null ? List.of() : List.of(lastMain), last);
        scroll.wheel(-100, ROW_H);
    }

    /** Keys a focused field of the table did not take: Enter and Tab walk the cells. @return true when used */
    boolean fieldKey(int key) {
        String focused = page.fields.focusedId();
        if (focused == null) {
            return false;
        }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (focused.equals("table-entry")) {
            if (enter) {
                TextArea area = page.fields.area("table-entry", false, 12.5f, Ui.REGULAR, "", v -> { });
                addFromEntry(area.text());
                area.setText("");
                return true;
            }
            return false;
        }
        if (enter || key == GLFW.GLFW_KEY_TAB) {
            next();
            return true;
        }
        return false;
    }

    /** Keys while no field has the keyboard: Tab goes to the next cell, Ctrl+→ makes the row a side quest of the one above. */
    boolean key(int key) {
        if (key == GLFW.GLFW_KEY_TAB && editRow != null) {
            next();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT && Screen.hasControlDown()) {
            ResourceLocation id = editRow != null ? editRow : chosen.stream().reduce((a, b) -> b).orElse(null);
            Quest quest = id == null ? null : EditorBook.get().quest(id);
            if (quest != null) {
                setKind(List.of(quest), QuestEdits.Kind.SIDE);
            }
            return true;
        }
        return false;
    }

    /** Tab: the next cell to the right, then the first of the next row. */
    private void next() {
        commitReward();
        if (editRow == null || editColumn == null) {
            return;
        }
        List<Row> rows = rows(EditorBook.get().view());
        Column[] order = compact ? new Column[] {Column.TITLE, Column.TYPE, Column.TASK} : Column.values();
        int column = java.util.Arrays.asList(order).indexOf(editColumn);
        ResourceLocation row = editRow;
        if (column + 1 < order.length) {
            edit(row, order[column + 1]);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).quest().id().equals(row)) {
                if (i + 1 < rows.size()) {
                    edit(rows.get(i + 1).quest().id(), order[0]);
                } else {
                    editRow = null;
                    page.fields.focus("table-entry");
                }
                return;
            }
        }
    }

    /** A click elsewhere: a typed reward lands, the cell closes. */
    void leave() {
        commitReward();
    }

    // --- tests ---

    void typeEntryForTest(String title) {
        addFromEntry(title);
    }

    void tickForTest(ResourceLocation id) {
        toggle(id);
    }

    Component describeForTest() {
        return Component.literal(chosen.toString());
    }
}
