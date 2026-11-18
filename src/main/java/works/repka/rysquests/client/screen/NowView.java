package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.progress.NextStep;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.KillTask;
import works.repka.ryscore.ui.Icons;

/**
 * "Now" (plan, section 3; pictures 11, 12, 12b, 12c, 46b): the next step of the book in a card, rewards to take and quests in work on
 * the left, what can be done in the current chapter on the right, and the panel of the selected quest, the next step by default. The
 * one amber button of the page is the panel's. The columns stand the same in every state: the first opening adds three lines about
 * the book as the last block of the left column, so nothing above moves when "Понятно" takes them away; when every main quest is
 * done, a summary takes the place of the next step.
 */
final class NowView implements BookView {
    private static final float PAD_X = 20;
    private static final float PAD_Y = 18;
    private static final float GAP = 14;
    private static final float COL_GAP = 16;
    private static final float ROW_GAP = 8;
    private static final int CLAIM_ROWS = 3;
    private static final int WORK_ROWS = 3;
    private static final int CAN_ROWS = 4;
    private static final float WHEEL = 48;

    private final BookScreen screen;
    private final QuestPanel panel;
    private final Scroll scroll = new Scroll();
    @Nullable
    private ResourceLocation chosen;
    private boolean overlay;
    private boolean allClaims;
    private float panelLeft = Float.MAX_VALUE;

    NowView(BookScreen screen) {
        this.screen = screen;
        this.panel = new QuestPanel(new QuestPanel.Events() {
            @Override
            public void select(Quest quest) {
                choose(quest);
            }

            @Override
            public void close() {
                overlay = false;
            }
        });
    }

    private void choose(Quest quest) {
        chosen = quest.id();
        overlay = true;
    }

    // --- the next step, shared with the list ---

    private static Optional<NextStep.Result> cachedStep = Optional.empty();
    private static int stepVersion = -1;
    @Nullable
    private static QuestBook stepBook;

    /** The next step by the plan's one rule, worked out once per book and progress. */
    static Optional<NextStep.Result> nextStep() {
        if (stepBook != BookData.book() || stepVersion != ClientProgress.version()) {
            stepBook = BookData.book();
            stepVersion = ClientProgress.version();
            List<ResourceLocation> order = new ArrayList<>();
            for (Chapter chapter : BookData.chapters()) {
                if (!Sidebar.isOffMap(chapter)) {
                    order.add(chapter.id());
                }
            }
            // With nothing done (a reset, a new team) the mark is stale: the rule's second half, as for a new player.
            Optional<ResourceLocation> last = ClientProgress.lastChapter().filter(id -> BookData.book().chapters().containsKey(id)
                    && BookData.book().chapters().get(id).isStory() && anythingDone());
            cachedStep = NextStep.choose(last, BookTallies::questsOf, order, ClientProgress::get, Epochs::isOpen);
        }
        return cachedStep;
    }

    /** Whether the player has completed anything in this book: before that, "Now" is the start of the way. */
    private static boolean anythingDone() {
        for (Quest quest : BookData.book().quests().values()) {
            if (ClientProgress.get(quest.id()).everDone()) {
                return true;
            }
        }
        return false;
    }

    // --- drawing ---

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        boolean compact = screen.compact();
        Optional<NextStep.Result> step = nextStep();
        Quest hero = step.map(NextStep.Result::quest).orElse(null);
        Lists lists = new Lists(hero);
        Quest shown = shown(hero, lists);
        float panelW = compact ? 0 : DetailPanel.WIDTH;
        float mainW = w - panelW;
        ui.rect(x, y, mainW, h, ui.c("panel.bg"));
        ui.clip(x, y, Math.round(mainW), h);
        float left = x + PAD_X;
        float inner = mainW - PAD_X * 2;
        float pen = y + (compact ? 12 : PAD_Y) - scroll.offset();
        pen = top(ui, step.orElse(null), left, pen, inner) + (compact ? 10 : GAP);
        pen = heroCard(ui, step.orElse(null), shown, compact, left, pen, inner) + (compact ? 10 : GAP);
        float colW = (inner - COL_GAP) / 2f;
        float leftEnd = leftColumn(ui, lists, shown, left, pen, colW, compact);
        float rightEnd = rightColumn(ui, lists, hero, shown, left + colW + COL_GAP, pen, colW, compact);
        ui.unclip();
        scroll.content(Math.max(leftEnd, rightEnd) + scroll.position() - y + PAD_Y, h);
        panelLeft = Float.MAX_VALUE;
        if (!compact) {
            panelLeft = x + mainW;
            if (shown != null) {
                panel.render(ui, panelLeft, y, panelW, h, shown, QuestPanel.Look.NOW);
            } else {
                ui.rect(panelLeft, y, panelW, h, ui.c("panel.bg"));
                ui.rect(panelLeft, y, 1, h, ui.c("panel.border"));
                panel.clear();
            }
        } else if (overlay && shown != null) {
            panelLeft = x + w - DetailPanel.WIDTH;
            ui.shadow(panelLeft, y, DetailPanel.WIDTH, h, 0, 0.6f);
            panel.render(ui, panelLeft, y, DetailPanel.WIDTH, h, shown, QuestPanel.Look.OVER);
        } else {
            panel.clear();
        }
    }

    /** The quest in the panel: the one chosen, else the next step; when the book is done, the first quest in work. */
    @Nullable
    private Quest shown(@Nullable Quest hero, Lists lists) {
        Quest picked = chosen == null ? null : BookData.book().quests().get(chosen);
        if (picked != null) {
            return picked;
        }
        if (hero != null) {
            return hero;
        }
        if (!lists.work.isEmpty()) {
            return lists.work.getFirst();
        }
        return lists.can.isEmpty() ? null : lists.can.getFirst();
    }

    /** The lines of the two columns, gathered once per frame. */
    private static final class Lists {
        final List<Quest> claim = new ArrayList<>();
        final List<Quest> work = new ArrayList<>();
        final List<Quest> can = new ArrayList<>();
        int canTotal;
        boolean claimedEver;

        Lists(@Nullable Quest hero) {
            QuestBook book = BookData.book();
            List<ResourceLocation> pinned = ClientProgress.pinned();
            for (ResourceLocation id : pinned) {
                Quest quest = book.quests().get(id);
                if (quest != null && BookData.shown(quest) && !ClientProgress.get(id).completed() && quest != hero && !book.isEpochReward(id)) {
                    work.add(quest);
                }
            }
            Chapter here = hero == null ? null : QuestFacts.chapter(hero);
            for (Chapter chapter : BookData.chapters()) {
                for (Quest quest : BookTallies.questsOf(chapter.id())) {
                    if (!QuestLogic.isLive(quest)) {
                        continue;
                    }
                    QuestStatus status = BookState.status(quest);
                    QuestProgress own = ClientProgress.get(quest.id());
                    if (status == QuestStatus.COMPLETED) {
                        claim.add(quest);
                    }
                    if (!own.claimed().isEmpty()) {
                        claimedEver = true;
                    }
                    boolean open = status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
                    if (open) {
                        canTotal++;
                    }
                    if (status == QuestStatus.IN_PROGRESS && quest != hero && !work.contains(quest)) {
                        work.add(quest);
                    }
                    if (open && quest != hero && !work.contains(quest) && can.size() < CAN_ROWS) {
                        boolean inChapter = here != null && chapter.id().equals(here.id());
                        boolean sideWhenDone = hero == null && quest.options().optional();
                        if (inChapter || sideWhenDone) {
                            can.add(quest);
                        }
                    }
                }
            }
            // epochs passed wait for their reward like quests done (ADR-60)
            for (int epoch : EpochCards.waitingEpochs()) {
                Quest quest = EpochCards.questOf(epoch);
                if (quest != null && !claim.contains(quest)) {
                    claim.add(quest);
                }
            }
        }
    }

    // --- the head of the page ---

    /** "II Индустрия" and how far the next epoch is; the chapter in a book without epochs (pictures 11, 12, 12b, 12c). */
    private float top(Ui ui, @Nullable NextStep.Result step, float x, float y, float w) {
        String title;
        String sub;
        Sidebar.Model model = Sidebar.model();
        if (step == null) {
            Sidebar.Group last = model.groups().isEmpty() ? null : model.groups().getLast();
            title = last != null ? QuestListView.groupLabel(last) : BookData.book().meta().title().map(t -> Component.translatable(t).getString())
                    .orElse(t("rysquests.qlist.whole"));
            sub = t("rysquests.now.book_done");
        } else {
            Chapter chapter = BookData.book().chapters().get(step.chapter());
            Sidebar.Group group = groupOf(model, chapter);
            String chapterName = chapter == null ? "" : QuestFacts.plain(Component.translatable(chapter.title()).getString());
            BookTallies.Tally tally = chapter == null ? BookTallies.Tally.EMPTY : BookTallies.chapter(chapter.id());
            if (!anythingDone()) {
                title = group != null && model.numerals() ? QuestListView.groupLabel(group) : chapterName;
                sub = t("rysquests.now.start", chapterName);
            } else if (group != null && model.numerals()) {
                title = QuestListView.groupLabel(group);
                int index = model.groups().indexOf(group);
                String left = Ui.counted("rysquests.now.plural.main", Math.max(0, group.tally().total() - group.tally().done()));
                sub = index + 1 < model.groups().size()
                        ? t("rysquests.now.epoch_left", Ui.roman(model.groups().get(index + 1).number()), left)
                        : t("rysquests.now.book_left", left);
            } else {
                title = chapterName;
                String done = t("rysquests.now.chapter_done", Sidebar.full(tally.done()), Sidebar.full(tally.total()));
                sub = group != null ? t("rysquests.now.section", group.title()) + " · " + done : done;
            }
        }
        float titleH = Ui.headingHeight(19) - 3;
        ui.labelFit(title, x, y + titleH / 2f, w * 0.7f, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelFit(sub, x, y + titleH + 3 + Ui.lineHeight(12) / 2f, w * 0.7f, 12, Ui.REGULAR, ui.c("text.muted"));
        int members = ClientProgress.teamSize();
        if (members > 1) {
            // The team's own name when the team mod gives one ("Репка · 3 участника"), else the word "Команда".
            String name = ClientProgress.teamName();
            String counted = Ui.counted("rysquests.now.plural.member", members);
            String team = name.isBlank() ? t("rysquests.now.team", counted) : t("rysquests.now.team_named", name, counted);
            float tw = ui.width(team, 12, Ui.REGULAR);
            float cy = y + (titleH + 3 + Ui.lineHeight(12)) / 2f;
            ui.label(team, x + w - tw, cy, 12, Ui.REGULAR, ui.c("text.muted"));
            ui.icon(Icons.Shape.USERS, x + w - tw - 8 - 15, cy - 7.5f, 15, ui.c("text.muted"));
        }
        return y + titleH + 3 + Ui.lineHeight(12);
    }

    @Nullable
    private static Sidebar.Group groupOf(Sidebar.Model model, @Nullable Chapter chapter) {
        if (chapter == null) {
            return null;
        }
        for (Sidebar.Group group : model.groups()) {
            for (Sidebar.Entry entry : group.chapters()) {
                if (entry.chapter().id().equals(chapter.id())) {
                    return group;
                }
            }
        }
        return null;
    }

    /** The card of the next step (mockup {@code .hero}): icon, title and "Показать на карте"; or the summary of a finished book. */
    private float heroCard(Ui ui, @Nullable NextStep.Result step, @Nullable Quest shown, boolean compact, float x, float y, float w) {
        float iconS = 60;
        float pad = 16;
        Quest hero = step == null ? null : step.quest();
        String rubric;
        String title;
        String sub = null;
        String state = null;
        ItemStack icon;
        if (hero == null) {
            rubric = t("rysquests.now.none");
            title = t("rysquests.now.all_done");
            sub = doneSummary();
            icon = BookData.book().meta().icon().map(TaskText::stackOf).orElse(new ItemStack(Items.NETHER_STAR));
        } else {
            boolean story = !Sidebar.model().groups().isEmpty() && Sidebar.model().numerals();
            Chapter chapter = BookData.book().chapters().get(step.chapter());
            rubric = !anythingDone() ? t("rysquests.now.first")
                    : story ? t("rysquests.now.next_story")
                    : step.fromLast() && chapter != null ? t("rysquests.now.next_from_last", QuestFacts.plain(Component.translatable(chapter.title()).getString()))
                    : t("rysquests.now.next");
            title = QuestFacts.plain(Component.translatable(hero.title()).getString());
            icon = QuestFacts.icon(hero);
            if (compact) {
                sub = QuestPanel.subtitleOf(hero);
                state = panel.firstLine(hero);
            }
        }
        boolean selected = hero != null && shown == hero;
        // The compact card carries the main button and "show on the map" under it (46b): there is no panel on the right.
        DetailPanel.Action action = compact && hero != null ? panel.actionFor(hero) : null;
        float buttonsW = compact && hero != null ? Math.min(230, w * 0.42f) : 0;
        String mapLabel = t("rysquests.now.show_on_map");
        float mapW = ui.width(mapLabel, 12.5f, Ui.MEDIUM) + 14 * 2 + 14 + 7;
        float textX = x + pad + iconS + 14;
        float rightW = hero == null ? 0 : compact ? buttonsW : mapW;
        float textW = x + w - pad - (rightW > 0 ? rightW + 14 : 0) - textX;
        float rubricH = Ui.lineHeight(11) + 4;
        float titleH = Ui.headingHeight(20) - 3;
        float subH = sub == null ? 0 : 3 + Ui.lineHeight(12);
        float stateH = state == null ? 0 : 6 + Ui.lineHeight(12);
        float textH = rubricH + titleH + subH + stateH;
        float h = pad * 2 + Math.max(iconS, textH);
        if (compact && hero != null) {
            h = Math.max(h, pad * 2 + 34 + 8 + 28);
        }
        boolean over = hero != null && ui.over(x, y, w, h);
        ui.card(x, y, w, h, 14, selected, ui.c(over ? "button.border" : "card.border"));
        if (hero != null) {
            ui.hit(x, y, w, h, () -> chosen = null);
        }
        float cy = y + h / 2f;
        ui.plate(x + pad, cy - iconS / 2f, iconS, iconS, 8);
        if (!icon.isEmpty()) {
            ui.item(icon, x + pad + 12, cy - 18, 36);
            if (hero != null) {
                ui.interactive(icon, x + pad, cy - iconS / 2f, iconS, iconS);
            }
        }
        float ty = cy - textH / 2f;
        ui.caps(ui.fit(rubric.toUpperCase(java.util.Locale.ROOT), textW, 11, Ui.MEDIUM), textX, ty + Ui.lineHeight(11) / 2f, ui.c("text.dim"));
        ty += rubricH;
        ui.labelFit(title, textX, ty + titleH / 2f, textW, 20, Ui.DISPLAY, ui.c("text.primary"));
        ty += titleH;
        if (sub != null) {
            ui.labelFit(sub, textX, ty + 3 + Ui.lineHeight(12) / 2f, textW, 12, Ui.REGULAR, ui.c("text.muted"));
            ty += subH;
        }
        if (state != null) {
            ui.labelFit(state, textX, ty + 6 + Ui.lineHeight(12) / 2f, textW, 12, Ui.REGULAR, ui.c("text.secondary"));
        }
        if (hero == null) {
            return y + h;
        }
        Quest target = hero;
        if (compact) {
            float bx = x + w - pad - buttonsW;
            float by = cy - (34 + 8 + 28) / 2f;
            if (action != null && action.action() != null) {
                ui.button(action.label(), bx, by, buttonsW, 34, 9, 12.5f, Ui.SEMIBOLD, 12, action.plain() ? Ui.Style.PLAIN : Ui.Style.ACCENT, action.action());
            } else if (action != null) {
                List<String> words = ui.wrap(action.label(), buttonsW, 12, Ui.REGULAR, 2);
                for (int i = 0; i < words.size(); i++) {
                    ui.labelCentered(words.get(i), bx + buttonsW / 2f, by + 17 + (i - (words.size() - 1) / 2f) * 15, 12, Ui.REGULAR, ui.c("text.muted"));
                }
            }
            mapButton(ui, mapLabel, bx, by + 34 + 8, buttonsW, 28, target);
        } else {
            mapButton(ui, mapLabel, x + w - pad - mapW, cy - 17, mapW, 34, target);
        }
        return y + h;
    }

    /** "Показать на карте": a grey button with the map icon (mockup {@code .btn}). */
    private void mapButton(Ui ui, String label, float x, float y, float w, float h, Quest quest) {
        boolean over = ui.over(x, y, w, h);
        ui.box(x, y, w, h, 9, ui.c(over ? "slot.hover" : "button.bg"), ui.c("button.border"));
        float lw = ui.width(label, 12.5f, Ui.MEDIUM);
        float pen = x + (w - lw - 14 - 7) / 2f;
        ui.icon(Icons.Shape.ROUTE, pen, y + h / 2f - 7, 14, ui.c("text.secondary"));
        ui.label(label, pen + 14 + 7, y + h / 2f, 12.5f, Ui.MEDIUM, ui.c("text.secondary"));
        ui.hit(x, y, w, h, () -> showOnMap(quest));
    }

    private static void showOnMap(Quest quest) {
        BookState.select(quest);
        MapView.showNext(quest.id());
        BookState.section = Chapter.STORY;
        BookState.view = works.repka.rysquests.data.ViewMode.MAP;
    }

    /** "329 из 329 основных · побочных 533 из 745 · достижений 31 из 58". */
    private static String doneSummary() {
        BookTallies.Tally book = BookTallies.book();
        String text = t("rysquests.now.all_done_sub", Sidebar.full(book.done()), Sidebar.full(book.total()), Sidebar.full(book.optionalDone()),
                Sidebar.full(book.optionalTotal()));
        List<Achievement> achievements = new ArrayList<>(BookData.book().achievements().values());
        if (!achievements.isEmpty()) {
            long got = achievements.stream().filter(a -> !a.tiers().isEmpty() && ClientProgress.get(a.questId(a.tiers().getFirst())).completed()).count();
            text += " · " + t("rysquests.now.achievements", got, achievements.size());
        }
        return text;
    }

    // --- the columns ---

    private float leftColumn(Ui ui, Lists lists, @Nullable Quest shown, float x, float y, float w, boolean compact) {
        float pen = y;
        int claims = lists.claim.size();
        String head = claims > 0 ? t("rysquests.now.claim_count", claims) : t("rysquests.now.claim");
        boolean plain = lists.claim.stream().anyMatch(QuestFacts::hasUnclaimedPlain);
        pen += rubric(ui, head, claims > 1 && plain ? t("rysquests.now.claim_all") : null, screen::claimAll, x, pen, w) + ROW_GAP;
        if (claims == 0) {
            pen = note(ui, t(lists.claimedEver ? "rysquests.now.claim_none" : "rysquests.now.claim_none_new"), x, pen, w) + ROW_GAP;
        } else {
            int limit = allClaims || claims <= CLAIM_ROWS + 1 ? claims : CLAIM_ROWS;
            for (int i = 0; i < limit; i++) {
                Quest quest = lists.claim.get(i);
                int epoch = BookData.book().epochOf(quest.id());
                if (epoch > 0) {
                    pen += epochRow(quest, epoch, compact).render(ui, x, pen, w) + ROW_GAP;
                    continue;
                }
                ListRow row = row(quest, shown, compact).sub(claimSub(quest));
                if (QuestFacts.hasUnclaimedChoice(quest)) {
                    row.link(t(compact ? "rysquests.now.pick_short" : "rysquests.now.pick_link"), () -> choose(quest));
                } else {
                    row.link(t("rysquests.now.claim_link"), () -> QuestFacts.claim(quest));
                }
                pen += row.render(ui, x, pen, w) + ROW_GAP;
            }
            if (limit < claims) {
                pen = link(ui, t("rysquests.now.more", claims - limit), () -> allClaims = true, x, pen) + ROW_GAP;
            }
        }
        boolean intro = !ClientConfig.NOW_INTRO_SEEN.get();
        if (!lists.work.isEmpty() || !intro) {
            pen += 4;
            pen += rubric(ui, t("rysquests.now.in_work"), lists.work.isEmpty() ? null : String.valueOf(lists.work.size()), null, x, pen, w) + ROW_GAP;
            if (lists.work.isEmpty()) {
                pen = note(ui, t("rysquests.now.work_none"), x, pen, w) + ROW_GAP;
            }
            for (int i = 0; i < Math.min(WORK_ROWS, lists.work.size()); i++) {
                Quest quest = lists.work.get(i);
                ListRow row = row(quest, shown, compact).sub(workSub(quest));
                float ratio = workRatio(quest);
                if (ratio > 0) {
                    row.progress(ratio);
                }
                if (ClientProgress.pinned().contains(quest.id())) {
                    row.right(Icons.Shape.PIN, null, "text.muted");
                }
                pen += row.render(ui, x, pen, w) + ROW_GAP;
            }
        }
        if (intro) {
            // the pages "О книге" when the book has them, else its first guide (ADR-60)
            Guide guide = BookData.book().aboutGuide().orElseGet(() -> BookData.book().guides().values().stream().findFirst().orElse(null));
            if (guide != null) {
                pen += 4;
                pen += rubric(ui, t("rysquests.now.guide_start"), null, null, x, pen, w) + ROW_GAP;
                ListRow row = ListRow.of(QuestFacts.plain(Component.translatable(guide.title()).getString()))
                        .icon(guide.icon().map(TaskText::stackOf).orElse(new ItemStack(Items.WRITABLE_BOOK)))
                        .sub(Ui.counted("rysquests.plural.page", guide.pages().size()))
                        .link(t("rysquests.now.open"), () -> BookState.openGuide(guide, BookState.firstUnread(guide)))
                        .onClick(() -> BookState.openGuide(guide, BookState.firstUnread(guide)));
                pen += row.render(ui, x, pen, w) + ROW_GAP;
            }
            pen += 4;
            pen = introCard(ui, x, pen, w) + ROW_GAP;
        }
        return pen;
    }

    private float rightColumn(Ui ui, Lists lists, @Nullable Quest hero, @Nullable Quest shown, float x, float y, float w, boolean compact) {
        float pen = y;
        Chapter here = hero == null ? null : QuestFacts.chapter(hero);
        String right = hero == null ? t("rysquests.now.side_only") : here == null ? null : t("rysquests.now.in_chapter", QuestFacts.plain(Component.translatable(here.title()).getString()));
        pen += rubric(ui, t("rysquests.now.can_do"), right, null, x, pen, w) + ROW_GAP;
        for (Quest quest : lists.can) {
            String chapter = QuestFacts.chapterTitle(quest);
            String sub = quest.options().optional() ? chapter + " · " + t("rysquests.qlist.side") : chapter;
            pen += row(quest, shown, compact).sub(sub).chevron().render(ui, x, pen, w) + ROW_GAP;
        }
        if (lists.canTotal > 0) {
            pen = link(ui, t("rysquests.now.all_can", Sidebar.full(lists.canTotal)), () -> QuestListView.openWholeBook(QuestListView.Filter.CAN), x, pen) + ROW_GAP;
        }
        return pen;
    }

    private ListRow row(Quest quest, @Nullable Quest shown, boolean compact) {
        return ListRow.of(QuestFacts.plain(Component.translatable(quest.title()).getString())).icon(QuestFacts.icon(quest))
                .selected(shown == quest && chosen != null).look(compact ? ListRow.Look.TIGHT : ListRow.Look.CARD)
                .onClick(() -> choose(quest)).onRightClick(() -> BookData.togglePin(quest.id()));
    }

    /** An epoch's reward among the rewards to take (ADR-60): its card on the map takes it, where a choice is made too. */
    private ListRow epochRow(Quest quest, int epoch, boolean compact) {
        Runnable open = () -> screen.showEpochReward(epoch);
        String sub = t("rysquests.epoch.now_sub") + " · " + (QuestFacts.hasUnclaimedChoice(quest) ? t("rysquests.now.choice_of", choiceSize(quest)) : QuestPanel.rewardsSummary(quest));
        return ListRow.of(EpochCards.title(epoch)).icon(QuestFacts.icon(quest)).look(compact ? ListRow.Look.TIGHT : ListRow.Look.CARD).sub(sub)
                .link(t("rysquests.now.claim_link"), open).onClick(open);
    }

    private static int choiceSize(Quest quest) {
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (quest.rewards().get(i) instanceof ChoiceReward choice && !ClientProgress.get(quest.id()).claimed().contains(i)) {
                return choice.of().size();
            }
        }
        return 0;
    }

    /** "Ток · 25 опыта и 1 монета", "веха главы · награда на выбор из 4". */
    private static String claimSub(Quest quest) {
        String where = quest.options().milestone() ? t("rysquests.qpanel.sub.milestone") : QuestFacts.chapterTitle(quest);
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (quest.rewards().get(i) instanceof ChoiceReward choice && !ClientProgress.get(quest.id()).claimed().contains(i)) {
                return where + " · " + t("rysquests.now.choice_of", choice.of().size());
            }
        }
        return where + " · " + QuestPanel.rewardsSummary(quest);
    }

    /** "Андезит · побочный · сдано 40 из 64", "Летопись инженера · закреплён". */
    private static String workSub(Quest quest) {
        List<String> parts = new ArrayList<>();
        parts.add(QuestFacts.chapterTitle(quest));
        if (quest.options().optional()) {
            parts.add(t("rysquests.qlist.side"));
        }
        QuestProgress own = ClientProgress.get(quest.id());
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            int value = Math.min(own.task(i), task.target());
            if (value >= task.target()) {
                continue;
            }
            if (value > 0) {
                String counted = t("rysquests.count.of", works.repka.ryscore.ui.Numbers.compact(value), works.repka.ryscore.ui.Numbers.compact(task.target()));
                parts.add(task instanceof ItemTask item && item.mode() == ItemTask.Mode.CONSUME ? t("rysquests.now.given", counted)
                        : task instanceof KillTask ? t("rysquests.now.kills", counted) : counted);
                return String.join(" · ", parts);
            }
            break;
        }
        if (ClientProgress.pinned().contains(quest.id())) {
            parts.add(t("rysquests.now.pinned"));
        }
        return String.join(" · ", parts);
    }

    private static float workRatio(Quest quest) {
        QuestProgress own = ClientProgress.get(quest.id());
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            int value = Math.min(own.task(i), task.target());
            if (value < task.target()) {
                return task.target() > 1 && value > 0 ? (float) value / task.target() : 0;
            }
        }
        return 0;
    }

    /** A rubric over a block, with words or a link on the right. @return its height */
    private static float rubric(Ui ui, String left, @Nullable String right, @Nullable Runnable action, float x, float y, float w) {
        float h = Ui.lineHeight(11.5f);
        float cy = y + h / 2f;
        ui.caps(left, x, cy, ui.c("text.dim"));
        if (right != null) {
            float rw = ui.width(right, 11.5f, Ui.REGULAR);
            boolean over = action != null && ui.over(x + w - rw, y, rw, h);
            ui.label(right, x + w - rw, cy, 11.5f, Ui.REGULAR, ui.c(action == null ? "text.muted" : over ? "text.primary" : "link"));
            if (action != null) {
                ui.hit(x + w - rw, y - 2, rw, h + 4, action);
            }
        }
        return h;
    }

    /** A quiet note with the info icon (mockup {@code .note}). @return the y below */
    private static float note(Ui ui, String words, float x, float y, float w) {
        List<String> lines = ui.wrap(words, w - 24 - 15 - 10, 12.5f, Ui.REGULAR, 4);
        float lh = 18.1f;
        float h = 20 + lines.size() * lh;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        ui.info(x + 12 + 3f, y + 10 + 3f, 14, ui.c("text.muted"));
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x + 12 + 15 + 10, y + 10 + lh * (i + 0.5f), 12.5f, Ui.REGULAR, ui.c("text.secondary"));
        }
        return y + h;
    }

    private static float link(Ui ui, String text, Runnable action, float x, float y) {
        float h = Ui.lineHeight(12.5f) + 4;
        float w = ui.width(text, 12.5f, Ui.MEDIUM);
        boolean over = ui.over(x, y, w + 8, h);
        ui.label(text, x + 4, y + h / 2f, 12.5f, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
        ui.hit(x, y, w + 8, h, action);
        return y + h;
    }

    /** Three lines about the book at the first opening, and "Понятно" that takes them away for good (picture 12). */
    private static float introCard(Ui ui, float x, float y, float w) {
        float pad = 14;
        float textX = x + pad + 34 + 12;
        float textW = x + w - pad - textX;
        List<String> title = ui.wrap(t("rysquests.now.intro_title"), textW, 13.5f, Ui.SEMIBOLD, 3);
        float titleLh = 18;
        float lineLh = Ui.lineHeight(12.5f) + 2;
        String[] lines = {t("rysquests.now.intro_1"), t("rysquests.now.intro_2"), t("rysquests.now.intro_3")};
        float h = 12 + title.size() * titleLh + 4 + lines.length * lineLh + 4 + 28 + 12;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        ui.item(new ItemStack(Items.WRITABLE_BOOK), x + pad + 1, y + 12 + 1, 32);
        float pen = y + 12;
        for (String line : title) {
            ui.label(line, textX, pen + titleLh / 2f, 13.5f, Ui.SEMIBOLD, ui.c("text.primary"));
            pen += titleLh;
        }
        pen += 4;
        for (int i = 0; i < lines.length; i++) {
            String number = (i + 1) + ". ";
            float nw = ui.width(number, 12.5f, Ui.SEMIBOLD);
            ui.label(number, textX, pen + lineLh / 2f, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
            ui.labelFit(lines[i], textX + nw, pen + lineLh / 2f, textW - nw, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
            pen += lineLh;
        }
        pen += 4;
        String ok = t("rysquests.now.intro_ok");
        float bw = ui.buttonWidth(ok, 12, Ui.MEDIUM, 10);
        ui.button(ok, textX, pen, bw, 28, 7, 12, Ui.MEDIUM, 10, Ui.Style.PLAIN, () -> {
            ClientConfig.NOW_INTRO_SEEN.set(true);
            ClientConfig.NOW_INTRO_SEEN.save();
        });
        return y + h;
    }

    // --- input ---

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (mouseX >= panelLeft) {
            panel.scroll(amount);
        } else {
            scroll.wheel(amount, WHEEL);
        }
    }

    @Override
    public boolean key(int key) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && overlay && screen.compact()) {
            overlay = false;
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        int right = panelLeft == Float.MAX_VALUE ? x + w : Math.round(panelLeft);
        return new int[] {x, y, right - x, h};
    }

    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // --- for tests ---

    void chooseForTest(@Nullable ResourceLocation quest) {
        chosen = quest;
        overlay = quest != null;
    }
}
