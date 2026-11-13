package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientDev;
import works.repka.rysquests.client.ClientLayout;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForms;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.dev.DevReports;
import works.repka.rysquests.editor.QuestEdits;
import works.repka.rysquests.network.QuestActionPayload;

/**
 * "Проверка" (pictures 37, 37b): on the left the pages "Что исправить", "Где команды застревают", "Архив" and the book in numbers,
 * "Раскладка карты устарела" with "Сохранить раскладку" and "Закрыть для игроков…"; in the middle the issues in words or the funnel of
 * the chapters (from {@link DevReports}); on the right the chosen issue with its ways to fix it, or the quest where teams stand.
 */
final class CheckView extends EditorPage {
    private static final int SIDE_W = 244;
    private static final int PANEL_W = 360;

    private enum Page {
        ISSUES, STUCK, ARCHIVE
    }

    private record Funnel(ResourceLocation chapter, int percent, @Nullable ResourceLocation stuck, String note) {
    }

    private Page shown = Page.ISSUES;
    private int filter;
    private int chosen;
    @Nullable
    private ResourceLocation stuckQuest;
    private boolean requested;
    private final Scroll scroll = new Scroll();
    private List<EditorChecks.Issue> lastVisible = List.of();

    CheckView(BookScreen screen) {
        super(screen);
    }

    void showArchive() {
        shown = Page.ARCHIVE;
    }

    void showStuckForTest() {
        shown = Page.STUCK;
    }

    void showIssuesForTest() {
        shown = Page.ISSUES;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (!requested) {
            requested = true;
            QuestKeys.sendAll(QuestActionPayload.DEV_REPORT);
        }
        QuestBook book = book().view();
        List<EditorChecks.Issue> issues = EditorChecks.find(book);
        renderSide(ui, book, issues, x, y, h);
        int mx = x + SIDE_W;
        int mw = w - SIDE_W - PANEL_W;
        switch (shown) {
            case STUCK -> renderStuck(ui, book, mx, y, mw, h);
            case ARCHIVE -> renderArchive(ui, book, mx, y, w - SIDE_W, h);
            default -> renderIssues(ui, issues, mx, y, mw, h);
        }
        if (shown != Page.ARCHIVE) {
            renderPanel(ui, book, issues, x + w - PANEL_W, y, PANEL_W, h);
        }
        choices.render(ui, y + h);
    }

    private static int teams() {
        DevReports.Report report = ClientDev.report();
        return report == null ? 0 : report.sections().stream().filter(s -> s.id().equals(DevReports.TEAMS)).flatMap(s -> s.entries().stream()).findFirst()
                .map(e -> Integer.parseInt(e.detail())).orElse(0);
    }

    private void renderSide(Ui ui, QuestBook book, List<EditorChecks.Issue> issues, int x, int y, int h) {
        ui.rect(x, y, SIDE_W, h, ui.c("sidebar.bg"));
        ui.rect(x + SIDE_W - 1, y, 1, h, ui.c("panel.border"));
        ui.caps(tr("rysquests.ed.check.head"), x + 14, y + 20, ui.c("text.dim"));
        float pen = y + 40;
        long archived = EditorSidebar.archived(book);
        pen += sideRow(ui, Icons.Shape.WARN, tr("rysquests.ed.check.issues"), String.valueOf(issues.size()), x + 8, pen, shown == Page.ISSUES, () -> shown = Page.ISSUES) + 3;
        pen += sideRow(ui, Icons.Shape.CHART, tr("rysquests.ed.check.stuck"), "", x + 8, pen, shown == Page.STUCK, () -> shown = Page.STUCK) + 3;
        pen += sideRow(ui, Icons.Shape.ARCHIVE, tr("rysquests.ed.check.archive"), archived > 0 ? String.valueOf(archived) : "", x + 8, pen, shown == Page.ARCHIVE,
                () -> shown = Page.ARCHIVE) + 16;
        ui.caps(tr("rysquests.ed.check.whole"), x + 14, pen + 7, ui.c("text.dim"));
        pen += 20;
        long chapters = book.chapters().values().stream().filter(Chapter::isStory).count();
        long onMap = book.chapters().values().stream().filter(MapForms::onMap).count();
        List<Quest> story = book.quests().values().stream().filter(q -> !q.options().archived() && q.chapter().map(id -> book.chapters().containsKey(id) && book.chapters().get(id).isStory()).orElse(false)).toList();
        long questsOnMap = story.stream().filter(q -> MapForms.onMap(book.chapters().get(q.chapter().get()))).count();
        long errors = issues.stream().filter(i -> i.level() == EditorChecks.Level.ERROR).count();
        long warns = issues.size() - errors;
        pen += stat(ui, tr("rysquests.ed.check.chapters"), Sidebar.full(chapters), tr("rysquests.ed.check.on_map", Sidebar.full(onMap)), null, x + 14, pen, SIDE_W - 28);
        pen += stat(ui, tr("rysquests.ed.check.quests"), Sidebar.full(story.size()), tr("rysquests.ed.check.on_map", Sidebar.full(questsOnMap)), null, x + 14, pen, SIDE_W - 28);
        pen += stat(ui, tr("rysquests.ed.check.errors"), String.valueOf(errors), "", errors > 0 ? "state.bad" : "text.primary", x + 14, pen, SIDE_W - 28);
        pen += stat(ui, tr("rysquests.ed.check.remarks"), String.valueOf(warns), "", warns > 0 ? "state.warn" : "text.primary", x + 14, pen, SIDE_W - 28);
        boolean open = ClientBook.current().meta().published();
        stat(ui, tr("rysquests.ed.check.players"), tr(open ? "rysquests.ed.check.open" : "rysquests.ed.check.closed"), "", open ? "state.ok" : "state.warn", x + 14, pen, SIDE_W - 28);
        // the foot: the map's layout and the lock
        float foot = y + h - (1 + 12 + 18 + 8 + 32 + 6 + 32 + 12);
        ui.rect(x, foot, SIDE_W - 1, 1, ui.c("panel.border"));
        float fy = foot + 1 + 12;
        boolean stale = ClientBook.current().freshLayout().isEmpty();
        ui.icon(Icons.Shape.MAP, x + 14, fy + 2, 14, ui.c("text.secondary"));
        ui.label(tr("rysquests.ed.check.layout"), x + 14 + 20, fy + 9, 12.5f, Ui.REGULAR, ui.c("text.body"));
        ui.labelRight(tr(stale ? "rysquests.ed.check.layout_stale" : "rysquests.ed.check.layout_fresh"), x + SIDE_W - 14, fy + 9, 12.5f, Ui.REGULAR,
                ui.c(stale ? "state.warn" : "state.ok"));
        fy += 18 + 8;
        wideButton(ui, tr("rysquests.ed.check.save_layout"), Icons.Shape.SAVE, x + 12, fy, SIDE_W - 24, stale ? this::bake : null);
        fy += 32 + 6;
        if (open) {
            wideButton(ui, tr("rysquests.ed.check.close_book"), null, x + 12, fy, SIDE_W - 24, this::askClose);
        } else {
            wideButton(ui, tr("rysquests.header.publish"), Icons.Shape.EYE, x + 12, fy, SIDE_W - 24, this::publish);
        }
    }

    private static float sideRow(Ui ui, Icons.Shape icon, String label, String count, float x, float y, boolean on, Runnable action) {
        float w = SIDE_W - 17;
        boolean over = ui.over(x, y, w, 30);
        if (on || over) {
            ui.fill(x, y, w, 30, 7, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        ui.icon(icon, x + 10, y + 8, 14, ui.c(on ? "text.primary" : "text.secondary"));
        ui.label(label, x + 10 + 14 + 10, y + 15, 12.5f, on ? Ui.MEDIUM : Ui.REGULAR, ui.c(on || over ? "text.primary" : "text.secondary"));
        if (!count.isEmpty()) {
            ui.labelRight(count, x + w - 10, y + 15, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        ui.hit(x, y, w, 30, action);
        return 30;
    }

    private static float stat(Ui ui, String label, String value, String note, @Nullable String color, float x, float y, float w) {
        ui.label(label, x, y + 9, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        float right = x + w;
        if (!note.isEmpty()) {
            String text = " · " + note;
            ui.labelRight(text, right, y + 9, 12, Ui.REGULAR, ui.c("text.muted"));
            right -= ui.width(text, 12, Ui.REGULAR);
        }
        ui.labelRight(value, right, y + 9, 12.5f, Ui.SEMIBOLD, ui.c(color == null ? "text.primary" : color));
        return 23;
    }

    private static void wideButton(Ui ui, String label, @Nullable Icons.Shape icon, float x, float y, float w, @Nullable Runnable action) {
        boolean over = action != null && ui.over(x, y, w, 32);
        ui.box(x, y, w, 32, 9, ui.c(over ? "slot.hover" : "button.bg"), ui.c("button.border"));
        int fg = ui.c(action == null ? "text.dim" : over ? "text.primary" : "text.secondary");
        float tw = ui.width(label, 12.5f, Ui.MEDIUM) + (icon == null ? 0 : 20);
        float tx = x + (w - tw) / 2f;
        if (icon != null) {
            ui.icon(icon, tx, y + 9, 14, fg);
            tx += 20;
        }
        ui.label(label, tx, y + 16, 12.5f, Ui.MEDIUM, fg);
        if (action != null) {
            ui.hit(x, y, w, 32, action);
        }
    }

    private void bake() {
        boolean sent = ClientLayout.bakeAndSave();
        works.repka.rysquests.client.ClientMessages.show(sent ? "rysquests.ed.check.layout_sent" : "rysquests.layout.no_baker", List.of(), sent);
    }

    private void askClose() {
        screen.ask(new works.repka.ryscore.ui.Kit.Dialog(tr("rysquests.ed.check.close_title"))
                .text(tr("rysquests.ed.check.close_text"))
                .no(tr("rysquests.ed.check.close_no"), screen.answer(() -> { }))
                .yes(tr("rysquests.ed.check.close_yes"), screen.answer(() -> ClientBook.requestPublish(false))));
    }

    // --- "Что исправить" ---

    private void renderIssues(Ui ui, List<EditorChecks.Issue> issues, int x, int y, int w, int h) {
        float px = x + 18;
        float pw = w - 36;
        float pen = y + 14;
        ui.label(tr("rysquests.ed.check.issues_title"), px, pen + 10, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.label(tr("rysquests.ed.check.issues_sub"), px, pen + 32, 12, Ui.REGULAR, ui.c("text.muted"));
        long errors = issues.stream().filter(i -> i.level() == EditorChecks.Level.ERROR).count();
        List<String> chips = List.of(tr("rysquests.ed.check.all", issues.size()), tr("rysquests.ed.check.only_errors", errors), tr("rysquests.ed.check.only_remarks", issues.size() - errors));
        float cw = EdUi.chipsWidth(ui, chips);
        EdUi.chips(ui, px + pw - cw, pen + 4, chips, filter, i -> {
            filter = i;
            chosen = 0;
        });
        pen += 50;
        List<EditorChecks.Issue> shownErrors = filter == 2 ? List.of() : issues.stream().filter(i -> i.level() == EditorChecks.Level.ERROR).toList();
        List<EditorChecks.Issue> shownWarns = filter == 1 ? List.of() : issues.stream().filter(i -> i.level() != EditorChecks.Level.ERROR).toList();
        if (issues.isEmpty()) {
            ui.label(tr("rysquests.ed.check.clean"), px, pen + 10, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            lastVisible = List.of();
            return;
        }
        ui.clip(x, Math.round(pen), w, Math.round(y + h - pen));
        int offset = scroll.offset();
        float top = pen;
        pen -= offset;
        int index = 0;
        List<EditorChecks.Issue> visible = new ArrayList<>();
        if (!shownErrors.isEmpty()) {
            DetailPanel.rubric(ui, px, pen, pw, tr("rysquests.ed.check.errors_head"), String.valueOf(shownErrors.size()));
            pen += 20;
            for (EditorChecks.Issue issue : shownErrors) {
                pen += issueRow(ui, issue, px, pen, pw, index++) + 7;
                visible.add(issue);
            }
            pen += 4;
        }
        if (!shownWarns.isEmpty()) {
            DetailPanel.rubric(ui, px, pen, pw, tr("rysquests.ed.check.remarks_head"), String.valueOf(shownWarns.size()));
            pen += 20;
            for (EditorChecks.Issue issue : shownWarns) {
                pen += issueRow(ui, issue, px, pen, pw, index++) + 7;
                visible.add(issue);
            }
        }
        ui.unclip();
        scroll.content(pen + offset - top, y + h - top);
        lastVisible = visible;
    }

    private float issueRow(Ui ui, EditorChecks.Issue issue, float x, float y, float w, int index) {
        boolean on = index == chosen;
        float h = 42;
        ui.box(x, y, w, h, 10, ui.c(on ? "tab.active.bg" : "card.bg"), ui.c(on ? "text.secondary" : "panel.border"));
        ui.box(x + 8, y + 7, 28, 28, 6, ui.c("slot.bg"), ui.c("slot.border"));
        String color = issue.level() == EditorChecks.Level.ERROR ? "state.bad" : issue.level() == EditorChecks.Level.WARN ? "state.warn" : "link";
        ui.icon(issue.level() == EditorChecks.Level.INFO ? Icons.Shape.SPARK : Icons.Shape.WARN, x + 8 + 7, y + 14, 14, ui.c(color));
        String show = tr("rysquests.ed.check.show");
        float bw = EdUi.buttonWidth(ui, show, null, true);
        ui.labelFitTip(issue.title(), x + 46, y + 14, w - 46 - bw - 20, 13, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.labelFitTip(issue.sub(), x + 46, y + 30, w - 46 - bw - 20, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.hit(x, y, w, h, () -> chosen = index);
        EdUi.button(ui, show, null, x + w - 12 - bw, y + 7, true, false, false, () -> {
            chosen = index;
            if (issue.quest() != null) {
                EditorChecks.showOnMap(issue.quest());
            }
        });
        return h;
    }

    // --- "Где команды застревают" ---

    private List<Funnel> funnels(QuestBook book) {
        DevReports.Report report = ClientDev.report();
        List<Funnel> out = new ArrayList<>();
        if (report == null) {
            return out;
        }
        int teams = teams();
        for (Chapter chapter : EditorSidebar.ordered(book)) {
            if (!MapForms.onMap(chapter)) {
                continue;
            }
            DevReports.Section section = report.sections().stream().filter(s -> s.id().equals(DevReports.FUNNEL_PREFIX + chapter.id())).findFirst().orElse(null);
            if (section == null || section.entries().isEmpty()) {
                continue;
            }
            List<DevReports.Entry> mains = section.entries().stream().filter(e -> {
                ResourceLocation id = ResourceLocation.tryParse(e.subject());
                Quest q = id == null ? null : book.quests().get(id);
                return q != null && !q.options().optional() && q.options().live();
            }).toList();
            if (mains.isEmpty()) {
                continue;
            }
            int last = Integer.parseInt(mains.getLast().detail());
            ResourceLocation stuck = null;
            int stuckFall = 0;
            int previous = 100;
            for (DevReports.Entry entry : mains) {
                int p = Integer.parseInt(entry.detail());
                if (previous - p > stuckFall) {
                    stuckFall = previous - p;
                    stuck = ResourceLocation.tryParse(entry.subject());
                }
                previous = p;
            }
            String note;
            if (last >= 100) {
                note = tr("rysquests.ed.check.funnel.all");
            } else if (section.entries().stream().allMatch(e -> e.detail().equals("0"))) {
                note = tr("rysquests.ed.check.funnel.closed");
            } else {
                int standing = teams == 0 ? 0 : Math.round(teams * (100 - last) / 100f);
                String name = stuck == null || book.quests().get(stuck) == null ? "" : book.quests().get(stuck).title();
                note = Ui.counted("rysquests.ed.check.funnel.teams", standing) + " " + tr("rysquests.ed.check.funnel.at", name);
            }
            out.add(new Funnel(chapter.id(), last, last >= 100 ? null : stuck, note));
        }
        return out;
    }

    private void renderStuck(Ui ui, QuestBook book, int x, int y, int w, int h) {
        float px = x + 18;
        float pen = y + 14;
        DevReports.Report report = ClientDev.report();
        ui.label(tr("rysquests.ed.check.stuck"), px, pen + 10, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelFit(tr("rysquests.ed.check.stuck_sub", Ui.counted("rysquests.ed.check.teams", teams())), px, pen + 32, w - 36, 12, Ui.REGULAR, ui.c("text.muted"));
        pen += 48;
        if (report == null) {
            ui.label(tr("rysquests.ed.check.waiting"), px, pen + 10, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        List<Funnel> funnels = funnels(book);
        if (funnels.isEmpty()) {
            ui.label(tr("rysquests.ed.check.funnel.empty"), px, pen + 10, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        int number = 0;
        for (Funnel funnel : funnels) {
            number++;
            Chapter chapter = book.chapters().get(funnel.chapter());
            boolean on = funnel.stuck() != null && funnel.stuck().equals(stuckQuest);
            float cy = pen + 12;
            if (on || funnel.stuck() != null && ui.over(px - 6, pen, w - 24, 24)) {
                ui.fill(px - 6, pen, w - 24, 24, 6, ui.c(on ? "tab.active.bg" : "button.bg"));
            }
            ui.labelFit(String.format("%02d ", number) + chapter.title(), px, cy, 120, 12.5f, Ui.REGULAR, ui.c("text.body"));
            ui.fill(px + 126, cy - 3, 80, 6, 3, ui.c("slot.bg"));
            if (funnel.percent() > 0) {
                ui.fill(px + 126, cy - 3, 80 * funnel.percent() / 100f, 6, 3, ui.c(funnel.percent() >= 50 ? "state.ok" : "text.primary"));
            }
            ui.labelRight(funnel.percent() + " %", px + 126 + 80 + 48, cy, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
            ui.labelFit(funnel.note(), px + 126 + 80 + 56, cy, w - 36 - (126 + 80 + 56), 11.5f, Ui.REGULAR, ui.c("text.muted"));
            if (funnel.stuck() != null) {
                ResourceLocation stuck = funnel.stuck();
                ui.hit(px - 6, pen, w - 24, 24, () -> stuckQuest = stuck);
                if (stuckQuest == null) {
                    stuckQuest = stuck;
                }
            }
            pen += 24;
        }
    }

    // --- the archive ---

    private void renderArchive(Ui ui, QuestBook book, int x, int y, int w, int h) {
        float px = x + 18;
        float pw = Math.min(w - 36, 720);
        float pen = y + 14;
        ui.label(tr("rysquests.ed.check.archive"), px, pen + 10, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.label(tr("rysquests.ed.check.archive_sub"), px, pen + 32, 12, Ui.REGULAR, ui.c("text.muted"));
        pen += 50;
        List<Quest> quests = book.quests().values().stream().filter(q -> q.options().archived() && q.chapter().map(id -> book.chapters().containsKey(id) && book.chapters().get(id).isStory()).orElse(false)).toList();
        List<Achievement> achievements = book.achievements().values().stream().filter(Achievement::archived).toList();
        if (quests.isEmpty() && achievements.isEmpty()) {
            ui.label(tr("rysquests.ed.check.archive_empty"), px, pen + 10, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        ui.clip(x, Math.round(pen), w, Math.round(y + h - pen));
        int offset = scroll.offset();
        float top = pen;
        pen -= offset;
        String restore = tr("rysquests.ed.check.restore");
        float bw = EdUi.buttonWidth(ui, restore, null, true);
        for (Quest quest : quests) {
            ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
            float rh = EdUi.row(ui, px, pen, pw, icon.isEmpty() ? null : icon, icon.isEmpty() ? Icons.Shape.ARCHIVE : null, quest.title(), EditorChecks.chapterTitle(book, quest), null, null);
            EdUi.button(ui, restore, null, px + pw - 10 - bw, pen + (rh - EdUi.SM_H) / 2f, true, false, false,
                    () -> book().editQuest(quest.id(), tr("rysquests.ed.log.restore", quest.title()), q -> QuestEdits.withArchived(q, false)));
            pen += rh + 6;
        }
        for (Achievement achievement : achievements) {
            String title = achievement.title().orElse(achievement.id().toString());
            float rh = EdUi.row(ui, px, pen, pw, achievement.icon().map(TaskText::stackOf).orElse(null), Icons.Shape.STAR, title, tr("rysquests.tab.achievements"), null, null);
            EdUi.button(ui, restore, null, px + pw - 10 - bw, pen + (rh - EdUi.SM_H) / 2f, true, false, false, () -> {
                EditorBook editor = book();
                Achievement a = editor.data().achievements().get(achievement.id());
                editor.putAchievement(new Achievement(a.id(), a.title(), a.text(), a.hint(), a.icon(), a.category(), a.hidden(), a.tiers(), false),
                        editor.achievementTask(a.id()), tr("rysquests.ed.log.restore", title));
            });
            pen += rh + 6;
        }
        ui.unclip();
        scroll.content(pen + offset - top, y + h - top);
    }

    // --- the panel on the right ---

    private void renderPanel(Ui ui, QuestBook book, List<EditorChecks.Issue> issues, int x, int y, int w, int h) {
        ui.rect(x, y, w, h, ui.c("panel.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float bx = x + 1 + 18;
        float bw = w - 1 - 36;
        float pen = y + 16;
        float footY = y + h - EdUi.FOOT_H;
        if (shown == Page.STUCK) {
            Quest quest = stuckQuest == null ? null : book.quests().get(stuckQuest);
            if (quest == null) {
                return;
            }
            pen += head(ui, book, quest, bx, pen, bw) + 12;
            int teams = teams();
            Funnel funnel = funnels(book).stream().filter(f -> quest.id().equals(f.stuck())).findFirst().orElse(null);
            int standing = funnel == null || teams == 0 ? 0 : Math.round(teams * (100 - funnel.percent()) / 100f);
            pen += EdUi.note(ui, bx, pen, bw, Icons.Shape.USERS, tr("rysquests.ed.check.standing", Ui.counted("rysquests.ed.check.teams_of", standing), teams)) + 14;
            pen += DetailPanel.rubric(ui, bx, pen, bw, tr("rysquests.ed.check.inside"), null) + 8;
            for (var task : quest.tasks()) {
                ui.label(tr("rysquests.ed.check.task"), bx, pen + 9, 12.5f, Ui.REGULAR, ui.c("text.muted"));
                ui.labelFit(EdText.task(task), bx + 74, pen + 9, bw - 74, 12.5f, Ui.REGULAR, ui.c("text.body"));
                pen += 22;
            }
            if (!quest.rewards().isEmpty()) {
                ui.label(tr("rysquests.ed.check.reward"), bx, pen + 9, 12.5f, Ui.REGULAR, ui.c("text.muted"));
                ui.labelFit(EdText.rewards(quest), bx + 74, pen + 9, bw - 74, 12.5f, Ui.REGULAR, ui.c("text.body"));
            }
            float by = EdUi.foot(ui, x + 1, footY, w - 1);
            EdUi.button(ui, tr("rysquests.ed.check.on_canvas"), Icons.Shape.MAP, bx, by, false, false, false, () -> EditorChecks.showOnMap(quest.id()));
            String open = tr("rysquests.ed.check.open_quest");
            float ow = EdUi.buttonWidth(ui, open, null, false);
            EdUi.button(ui, open, null, bx + bw - ow, by, false, false, true, () -> EditorChecks.showOnMap(quest.id()));
            return;
        }
        List<EditorChecks.Issue> list = lastVisible.isEmpty() ? issues : lastVisible;
        if (list.isEmpty()) {
            return;
        }
        EditorChecks.Issue issue = list.get(Math.min(chosen, list.size() - 1));
        Quest quest = issue.quest() == null ? null : book.quests().get(issue.quest());
        if (quest != null) {
            pen += head(ui, book, quest, bx, pen, bw) + 12;
        }
        // the callout: what goes wrong, in a warning box
        List<String> lines = ui.wrap(issue.detail(), bw - 24 - 25, 12.5f, Ui.REGULAR, 6);
        float ch = 20 + lines.size() * 18;
        String color = issue.level() == EditorChecks.Level.ERROR ? "state.bad" : "state.warn";
        ui.box(bx, pen, bw, ch, 10, ui.c("callout.bg"), ui.c("callout.border"));
        ui.icon(Icons.Shape.WARN, bx + 12, pen + 11, 15, ui.c(color));
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), bx + 12 + 15 + 10, pen + 10 + (i + 0.5f) * 18, 12.5f, Ui.REGULAR, ui.c("text.body"));
        }
        pen += ch + 14;
        pen += DetailPanel.rubric(ui, bx, pen, bw, tr("rysquests.ed.check.how"), null) + 8;
        EditorChecks.Fix primary = null;
        for (EditorChecks.Fix fix : issue.fixes()) {
            if (fix.primary() && primary == null) {
                primary = fix;
            }
            float rh = fix.sub() == null ? 46 : 50;
            boolean first = fix == issue.fixes().getFirst() && fix.sub() != null;
            boolean over = ui.over(bx, pen, bw, rh);
            ui.box(bx, pen, bw, rh, 10, ui.c(first ? "tab.active.bg" : over ? "slot.hover" : "card.bg"), ui.c(first ? "text.secondary" : "panel.border"));
            ui.box(bx + 8, pen + (rh - 30) / 2f, 30, 30, 6, ui.c("slot.bg"), ui.c("slot.border"));
            if (fix.icon() != null) {
                ui.icon(fix.icon(), bx + 8 + 8, pen + (rh - 14) / 2f, 14, ui.c("text.secondary"));
            }
            float tx = bx + 8 + 30 + 12;
            if (fix.sub() == null) {
                ui.labelFit(fix.title(), tx, pen + rh / 2f, bw - (tx - bx) - 30, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
                ui.icon(Icons.Shape.CHEVRON_RIGHT, bx + bw - 22, pen + rh / 2f - 6, 12, ui.c("text.muted"));
                ui.hit(bx, pen, bw, rh, fix.action());
            } else {
                String pick = tr("rysquests.ed.check.pick");
                float pw = EdUi.buttonWidth(ui, pick, null, true);
                ui.labelFit(fix.title(), tx, pen + rh / 2f - 8, bw - (tx - bx) - pw - 20, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
                ui.labelFit(fix.sub(), tx, pen + rh / 2f + 9, bw - (tx - bx) - pw - 20, 11.5f, Ui.REGULAR, ui.c("text.muted"));
                EdUi.button(ui, pick, null, bx + bw - 12 - pw, pen + (rh - EdUi.SM_H) / 2f, true, false, false, fix.action());
            }
            pen += rh + 7;
        }
        float by = EdUi.foot(ui, x + 1, footY, w - 1);
        if (quest != null) {
            ResourceLocation id = quest.id();
            EdUi.button(ui, tr("rysquests.ed.check.on_canvas"), Icons.Shape.MAP, bx, by, false, false, false, () -> EditorChecks.showOnMap(id));
        }
        if (primary != null) {
            EditorChecks.Fix fix = primary;
            String label = EdUi.buttonWidth(ui, fix.title(), null, false) > bw - 140 ? tr("rysquests.ed.check.fix") : fix.title();
            float lw = EdUi.buttonWidth(ui, label, null, false);
            EdUi.button(ui, label, null, bx + bw - lw, by, false, false, true, fix.action());
        }
    }

    private static float head(Ui ui, QuestBook book, Quest quest, float x, float y, float w) {
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElseGet(() -> quest.tasks().isEmpty() ? ItemStack.EMPTY : TaskText.icon(quest.tasks().getFirst()));
        String sub = EditorChecks.chapterTitle(book, quest) + " · " + EdText.kind(QuestEdits.kind(quest));
        return DetailPanel.header(ui, x, y, w, icon, quest.title(), sub, null, false, null);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll.wheel(amount, 40);
    }

    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        return new int[] {x + SIDE_W, y, w - SIDE_W - (shown == Page.ARCHIVE ? 0 : PANEL_W), h};
    }

    int issuesForTest() {
        return EditorChecks.find(book().view()).size();
    }
}
