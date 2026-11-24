package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientAchievements;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.progress.CollectionMarks;
import works.repka.rysquests.progress.PinLimit;
import works.repka.rysquests.progress.StatCount;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TaskDef;
import net.minecraft.ChatFormatting;
import works.repka.rysquests.task.StatTask;
import works.repka.rysquests.data.Achievement.Tier;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.network.SelectTitlePayload;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.TitleReward;
import works.repka.rysquests.reward.XpReward;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.Numbers;

/**
 * Achievements (plan, section 3, pictures 28, 45, 46d), laid out as the author's sample: categories and the totals on the left, cards in
 * the middle, the selected one with its levels on the right. A card has the ring with the icon and the level segments on its top line,
 * the name in full width under them, two lines of description and one line of count. A level's color comes from its place
 * ({@link Achievement#medal}), the ring takes the color of the highest level reached. The count is personal: no share of the server.
 */
final class AchievementsView implements BookView {
    private static final int PAD_X = 16;
    private static final int PAD_Y = 14;
    private static final int TOP_H = 28;
    private static final int TOP_GAP = 14;
    private static final int COLUMNS = 3;
    private static final int COLUMNS_COMPACT = 4;
    private static final int CARD_H = 151;
    private static final int CARD_GAP = 10;
    private static final int RING = 40;
    private static final int FOOT_H = 38;
    private static final int PANEL_COMPACT = 300;
    private static final int WHEEL = 48;
    private static final int PIN_MARK = 13;
    private static final int SLOT = 36;
    private static final int SLOT_GAP = 6;

    private enum Show {
        ALL, IN_WORK, GOT
    }

    private final BookScreen screen;
    private final DetailPanel panel = new DetailPanel();
    private final Scroll gridScroll = new Scroll();
    private final Scroll sideScroll = new Scroll();
    private final Kit.Dropdown categories = new Kit.Dropdown();
    private String category = "";
    private Show show = Show.ALL;
    @Nullable
    private ResourceLocation selectedId;
    /** The compact layout opens the detail over the page only after a click (picture 46d). */
    private boolean panelOpen;
    private int sideRight;

    AchievementsView(BookScreen screen) {
        this.screen = screen;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // --- what an achievement is ---

    private static boolean reached(Achievement achievement, Tier tier) {
        return ClientProgress.get(achievement.questId(tier)).completed();
    }

    /** The index of the highest level reached, or -1. */
    static int best(Achievement achievement) {
        int found = -1;
        for (int i = 0; i < achievement.tiers().size(); i++) {
            if (reached(achievement, achievement.tiers().get(i))) {
                found = i;
            }
        }
        return found;
    }

    /**
     * How rare the achievement is on this server, a line under its state: the server counts it once a minute. Before the
     * first level it is the share who have the achievement ("есть у 12 % сервера"), after it the share who have the
     * player's highest level ("Уровень 3 у 2 % сервера"). Nothing until the server has said.
     */
    @Nullable
    static String rarityLine(Achievement achievement) {
        if (achievement.tiers().isEmpty()) {
            return null;
        }
        int best = best(achievement);
        Integer known = ClientAchievements.rarityIfKnown(achievement.questId(achievement.tiers().get(Math.max(0, best))));
        if (known == null) {
            return null;
        }
        // the player is one of those who have it, even if the minute's count came before they got it
        int percent = best >= 0 ? Math.max(1, known) : known;
        if (percent <= 0) {
            return tr("rysquests.achievements.nobody");
        }
        if (best > 0) {
            return text("rysquests.achievements.tier_has", text("rysquests.achievements.level_n", best + 1), percent);
        }
        return text("rysquests.achievements.has", percent);
    }

    @Nullable
    private static Tier next(Achievement achievement) {
        for (Tier tier : achievement.tiers()) {
            if (!reached(achievement, tier)) {
                return tier;
            }
        }
        return null;
    }

    static int progress(Achievement achievement) {
        Tier next = next(achievement);
        StatTask stat = statTask(achievement);
        if (next != null && stat != null && StatCount.isStreak(stat)) {
            // A streak starts again from zero: the count is the current one, not the level it once reached (ADR-61).
            return ClientProgress.get(achievement.questId(next)).task(0);
        }
        // A collection counts its marks, which move before its level's quest is asked again (ADR-62).
        Quest marks = marks(achievement);
        int most = marks == null ? 0 : CollectionMarks.count(marks, ClientProgress.get(marks.id()));
        for (Tier tier : achievement.tiers()) {
            QuestProgress own = ClientProgress.get(achievement.questId(tier));
            most = Math.max(most, own.completed() ? tier.amount() : own.task(0));
        }
        return most;
    }

    /** The quest that keeps what of a collection is collected, or null for an achievement that is not a collection (ADR-62). */
    @Nullable
    static Quest marks(Achievement achievement) {
        Quest marks = ClientBook.current().quests().get(achievement.collectionQuestId());
        return marks == null || marks.tasks().isEmpty() ? null : marks;
    }

    /** Whether this level is pinned to the HUD. */
    private static boolean pinned(Achievement achievement) {
        Tier next = next(achievement);
        return next != null && ClientProgress.pinned().contains(achievement.questId(next));
    }

    private static float ratio(Achievement achievement) {
        Tier next = next(achievement);
        return next == null ? 1 : Math.min(1, (float) progress(achievement) / next.amount());
    }

    /** How full each level is: reached ones whole, the current one in part from the level before, later ones empty. */
    private static float[] fills(Achievement achievement) {
        int[] amounts = new int[achievement.tiers().size()];
        boolean[] reached = new boolean[amounts.length];
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = achievement.tiers().get(i).amount();
            reached[i] = reached(achievement, achievement.tiers().get(i));
        }
        return fills(progress(achievement), amounts, reached);
    }

    /**
     * The fill of each level's segment for a count: a level counts from the one before it, so 4 400 of levels 1 000, 10 000, 50 000
     * fills the first, a third of the second and nothing of the third. A level reached by other means (a lower count after a reset)
     * stays full.
     */
    static float[] fills(int value, int[] amounts, boolean[] reached) {
        float[] out = new float[amounts.length];
        int previous = 0;
        for (int i = 0; i < out.length; i++) {
            if (reached[i] || value >= amounts[i]) {
                out[i] = 1;
            } else if (value > previous) {
                out[i] = (float) (value - previous) / Math.max(1, amounts[i] - previous);
            }
            previous = amounts[i];
        }
        return out;
    }

    private static boolean concealed(Achievement achievement) {
        return achievement.hidden() && best(achievement) < 0;
    }

    private static ItemStack icon(Achievement achievement) {
        return achievement.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
    }

    static String name(Achievement achievement) {
        return concealed(achievement) ? tr("rysquests.achievements.unknown") : tr(achievement.titleKey());
    }

    private static String description(Achievement achievement) {
        if (concealed(achievement)) {
            return Language.getInstance().has(achievement.hintKey()) ? text("rysquests.achievements.hint", tr(achievement.hintKey())) : tr("rysquests.achievements.secret_text");
        }
        return tr(achievement.textKey());
    }

    @Nullable
    private static String ringToken(Achievement achievement) {
        int best = best(achievement);
        return best < 0 ? null : LevelSegments.token(best, achievement.tiers().size());
    }

    private static List<Achievement> all() {
        return ClientBook.current().achievements().values().stream().filter(achievement -> !achievement.archived()).toList();
    }

    private List<Achievement> shown() {
        List<Achievement> list = new ArrayList<>();
        for (Achievement achievement : all()) {
            boolean inCategory = category.isEmpty() || achievement.category().filter(category::equals).isPresent();
            boolean match = switch (show) {
                case ALL -> true;
                case IN_WORK -> next(achievement) != null && progress(achievement) > 0 && !concealed(achievement);
                case GOT -> best(achievement) >= 0;
            };
            if (inCategory && match) {
                list.add(achievement);
            }
        }
        // One order: those closer to their goal first; finished ones and secrets at the end.
        list.sort(Comparator.comparing(AchievementsView::concealed)
                .thenComparing(achievement -> next(achievement) == null)
                .thenComparing(achievement -> -ratio(achievement)));
        return list;
    }

    private static Map<String, Integer> categoryCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Achievement achievement : all()) {
            achievement.category().ifPresent(key -> out.merge(key, 1, Integer::sum));
        }
        return out;
    }

    // --- drawing ---

    /** The middle column between the categories and the detail: where the book's message lies (picture 48). */
    private int messageLeft = Integer.MIN_VALUE;
    private int messageRight = Integer.MAX_VALUE;

    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        int left = Math.max(x, messageLeft);
        return new int[] {left, y, Math.min(x + w, messageRight) - left, h};
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        boolean compact = screen.compact();
        List<Achievement> shown = shown();
        Achievement selected = selectedId == null ? null : ClientBook.current().achievements().get(selectedId);
        if (selected == null && !shown.isEmpty()) {
            selected = shown.getFirst();
        }
        int sideW = compact ? 0 : SideList.WIDTH;
        sideRight = x + sideW;
        int panelW = compact ? 0 : DetailPanel.WIDTH;
        messageLeft = x + sideW;
        messageRight = x + w - (compact ? panelOpen && selected != null ? PANEL_COMPACT : 0 : panelW);
        if (!compact) {
            renderSide(ui, x, y, h);
        }
        float left = x + sideW + PAD_X;
        float inner = w - sideW - panelW - PAD_X * 2;
        float top = y + PAD_Y;
        float bodyTop = top + TOP_H + TOP_GAP;
        float bottom = y + h - PAD_Y;
        if (shown.isEmpty()) {
            if (show == Show.GOT) {
                EmptyFilter.render(ui, left, bodyTop, inner, bottom - bodyTop, tr("rysquests.achievements.empty.got"), tr("rysquests.achievements.empty.show_all"), () -> show = Show.ALL);
            } else {
                EmptyFilter.render(ui, left, bodyTop, inner, bottom - bodyTop, tr("rysquests.achievements.empty.filter"), tr("rysquests.achievements.empty.show_all"), () -> {
                    show = Show.ALL;
                    category = "";
                });
            }
        } else {
            renderGrid(ui, shown, compact && !panelOpen ? null : selected, left, bodyTop, inner, bottom - bodyTop, compact ? COLUMNS_COMPACT : COLUMNS);
        }
        if (!compact) {
            renderPanel(ui, selected, x + w - panelW, y, panelW, h, false);
        }
        renderTop(ui, left, top, inner, compact);
        if (compact && panelOpen && selected != null) {
            // Over the page, as the quest's panel on the map: above the cards' items too.
            Achievement shownAchievement = selected;
            ui.aboveItems(() -> {
                ui.shadow(x + w - PANEL_COMPACT, y, PANEL_COMPACT, h, 0, 0.6f);
                ui.hit(x + w - PANEL_COMPACT, y, PANEL_COMPACT, h, () -> { });
                renderPanel(ui, shownAchievement, x + w - PANEL_COMPACT, y, PANEL_COMPACT, h, true);
            });
        }
        if (compact) {
            renderCategoryList(ui);
        } else {
            renderTitleList(ui);
        }
    }

    // --- the column on the left ---

    private void renderSide(Ui ui, int x, int y, int h) {
        SideList.plate(ui, x, y, h);
        float pen = y + 12;
        pen += renderTotals(ui, x + 12, pen, SideList.WIDTH - 1 - 24) + 6 + 4;
        float listTop = pen;
        float footTop = SideList.footer(ui, x, y + h, FOOT_H);
        int offset = sideScroll.offset();
        ui.clip(x, Math.round(listTop), SideList.WIDTH - 1, Math.round(footTop - listTop));
        float row = listTop - offset;
        row += SideList.row(ui, x, row, tr("rysquests.achievements.category_all"), String.valueOf(all().size()), category.isEmpty(), false, null, () -> select(""));
        for (Map.Entry<String, Integer> entry : categoryCounts().entrySet()) {
            String key = entry.getKey();
            row += SideList.row(ui, x, row, tr(key), String.valueOf(entry.getValue()), key.equals(category), false, null, () -> select(key));
        }
        ui.unclip();
        sideScroll.content(row + offset - listTop, footTop - listTop);
        renderTitleLine(ui, x + 12, footTop + 1, SideList.WIDTH - 1 - 24, FOOT_H - 1);
    }

    private void select(String key) {
        category = key;
        gridScroll.reset();
    }

    /** "7 из 58 достижений", the bar, "уровней 10 из 139" and three rings with counts by color. @return its height */
    private static float renderTotals(Ui ui, float x, float y, float w) {
        List<Achievement> all = all();
        int got = 0;
        int levels = 0;
        int levelsGot = 0;
        int[] byMedal = new int[3];
        for (Achievement achievement : all) {
            int n = achievement.tiers().size();
            levels += n;
            got += best(achievement) >= 0 ? 1 : 0;
            for (int i = 0; i < n; i++) {
                if (reached(achievement, achievement.tiers().get(i))) {
                    levelsGot++;
                    byMedal[Achievement.medal(i, n).ordinal()]++;
                }
            }
        }
        float h = 12 + 24 + 8 + 5 + 8 + Ui.lineHeight(12) + 4 + 14 + 12;
        ui.box(x, y, w, h, 12, ui.c("card.bg"), ui.c("panel.border"));
        float pen = y + 12;
        String count = String.valueOf(got);
        ui.label(count, x + 12, pen + 12, 20, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelFit(text("rysquests.achievements.of_total", all.size()), x + 12 + ui.width(count, 20, Ui.DISPLAY) + 8, pen + 12, w - 32, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        pen += 24 + 8;
        ui.bar(x + 12, pen, w - 24, 5, 3, all.isEmpty() ? 0 : (float) got / all.size(), ui.c("state.ok"));
        pen += 5 + 8;
        ui.label(text("rysquests.achievements.levels_of", levelsGot, levels), x + 12, pen + Ui.lineHeight(12) / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
        pen += Ui.lineHeight(12) + 4;
        float rx = x + 12;
        String[] tokens = {"medal.bronze", "medal.silver", "medal.gold"};
        for (int i = 0; i < 3; i++) {
            AchievementRing.dot(ui, rx, pen + 1.5f, 11, tokens[i]);
            String n = String.valueOf(byMedal[i]);
            ui.label(n, rx + 15, pen + 7, 12, Ui.REGULAR, ui.c("text.secondary"));
            rx += 15 + ui.width(n, 12, Ui.REGULAR) + 12;
        }
        return h;
    }

    /** The titles the player owns, from every level that gives one. */
    private static List<String> ownedTitles() {
        Set<String> out = new LinkedHashSet<>();
        for (Achievement achievement : all()) {
            for (Tier tier : achievement.tiers()) {
                for (RewardDef reward : tier.rewards()) {
                    if (reward instanceof TitleReward title && ClientAchievements.owns(title.id())) {
                        out.add(title.id());
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    private float titleLeft;
    private float titleTop;
    private boolean titleListOpen;

    /** "⚑ Титул «Кладовщик» · сменить" at the foot of the column. */
    private void renderTitleLine(Ui ui, float x, float y, float w, float h) {
        float cy = y + h / 2f;
        String worn = ClientAchievements.activeTitle();
        String line = worn == null || worn.isEmpty() ? tr("rysquests.achievements.no_title") : text("rysquests.achievements.title_line", tr(worn));
        ui.icon(Icons.Shape.FLAG, x, cy - 7, 14, ui.c("text.secondary"));
        List<String> owned = ownedTitles();
        String change = tr("rysquests.achievements.change_title");
        float changeW = owned.isEmpty() ? 0 : ui.width(change, 12.5f, Ui.MEDIUM);
        ui.labelFit(line, x + 22, cy, w - 22 - changeW - 8, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
        if (!owned.isEmpty()) {
            float lx = x + w - changeW;
            boolean over = ui.over(lx, cy - 9, changeW, 18);
            ui.label(change, lx, cy, 12.5f, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
            ui.hit(lx, cy - 9, changeW, 18, () -> titleListOpen = !titleListOpen);
            titleLeft = x;
            titleTop = y;
        }
    }

    /** The list of owned titles over the column's foot, the worn one marked. A click outside closes it. */
    private void renderTitleList(Ui ui) {
        if (!titleListOpen) {
            return;
        }
        List<String> owned = ownedTitles();
        List<String> labels = new ArrayList<>();
        labels.add("");
        labels.addAll(owned);
        String worn = ClientAchievements.activeTitle();
        float rowH = 30;
        float w = SideList.WIDTH - 24;
        float h = 8 + labels.size() * rowH;
        float x = titleLeft;
        float y = titleTop - 6 - h;
        ui.aboveItems(() -> {
            ui.hit(0, 0, 100000, 100000, () -> titleListOpen = false);
            ui.shadow(x, y, w, h, 12, 0.7f);
            ui.box(x, y, w, h, 12, ui.c("sidebar.bg"), ui.c("button.border"));
            for (int i = 0; i < labels.size(); i++) {
                String title = labels.get(i);
                boolean on = title.isEmpty() ? worn == null || worn.isEmpty() : title.equals(worn);
                float ry = y + 4 + i * rowH;
                boolean over = ui.over(x + 4, ry, w - 8, rowH);
                if (on || over) {
                    ui.fill(x + 4, ry, w - 8, rowH, 7, ui.c(on ? "row.selected.bg" : "button.bg"));
                }
                if (on) {
                    ui.icon(Icons.Shape.CHECK, x + 12, ry + rowH / 2f - 6, 12, ui.c("text.primary"));
                }
                String label = title.isEmpty() ? tr("rysquests.achievements.no_title") : tr(title);
                ui.labelFit(label, x + 32, ry + rowH / 2f, w - 44, 12.5f, on ? Ui.SEMIBOLD : Ui.REGULAR, ui.c("text.primary"));
                ui.hit(x + 4, ry, w - 8, rowH, () -> {
                    titleListOpen = false;
                    PacketDistributor.sendToServer(new SelectTitlePayload(title));
                });
            }
        });
    }

    // --- the line on top and the grid ---

    private void renderTop(Ui ui, float x, float y, float w, boolean compact) {
        float cy = y + TOP_H / 2f;
        List<String> labels = List.of(tr("rysquests.achievements.show.all"), tr("rysquests.achievements.show.in_work"), tr("rysquests.achievements.show.got"));
        if (!compact) {
            float cw = FilterChips.width(ui, labels, false);
            ui.labelFit(tr("rysquests.achievements.order_line"), x, cy, w - cw - 12, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            FilterChips.render(ui, x + w - cw, y, labels, show.ordinal(), index -> {
                show = Show.values()[index];
                gridScroll.reset();
            }, false);
            return;
        }
        Map<String, Integer> counts = categoryCounts();
        List<String> keys = new ArrayList<>(List.of(""));
        List<Kit.Option> options = new ArrayList<>(List.of(new Kit.Option(tr("rysquests.achievements.category_all"), all().size())));
        counts.forEach((key, count) -> {
            keys.add(key);
            options.add(new Kit.Option(tr(key), count));
        });
        int picked = Math.max(0, keys.indexOf(category));
        String label = text("rysquests.achievements.category_dd", options.get(picked).label(), options.get(picked).count());
        int ddW = Kit.dropdown(ui.g, ui.theme, ui.kitHits, categories, Math.round(x / 2f), Math.round((cy - Kit.DROP_H) / 2f), 120, label, !category.isEmpty(), ui.mouseX / 2, ui.mouseY / 2) * 2;
        float pen = x + ddW + 8;
        pen += FilterChips.render(ui, pen, y, labels, show.ordinal(), index -> {
            show = Show.values()[index];
            gridScroll.reset();
        }, true) + 12;
        int got = (int) all().stream().filter(achievement -> best(achievement) >= 0).count();
        int levels = all().stream().mapToInt(achievement -> achievement.tiers().size()).sum();
        int levelsGot = all().stream().mapToInt(achievement -> best(achievement) + 1).sum();
        String worn = ClientAchievements.activeTitle();
        String title = worn == null || worn.isEmpty() ? "" : "«" + tr(worn) + "»";
        float titleW = title.isEmpty() ? 0 : 14 + 6 + ui.width(title, 12.5f, Ui.MEDIUM);
        String totals = text("rysquests.achievements.totals_compact", got, all().size(), levelsGot, levels);
        float right = x + w;
        if (!title.isEmpty()) {
            ui.icon(Icons.Shape.FLAG, right - titleW, cy - 7, 14, ui.c("text.secondary"));
            ui.label(title, right - titleW + 20, cy, 12.5f, Ui.MEDIUM, ui.c("link"));
            right -= titleW + 12;
        }
        ui.labelFit(totals, Math.max(pen, right - ui.width(totals, 12.5f, Ui.REGULAR)), cy, right - pen, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
    }

    /** The open list of categories, last in the frame. */
    private void renderCategoryList(Ui ui) {
        List<String> keys = new ArrayList<>(List.of(""));
        List<Kit.Option> options = new ArrayList<>(List.of(new Kit.Option(tr("rysquests.achievements.category_all"), all().size())));
        categoryCounts().forEach((key, count) -> {
            keys.add(key);
            options.add(new Kit.Option(tr(key), count));
        });
        Kit.dropdownList(ui.g, ui.theme, ui.kitHits, categories, options, Math.max(0, keys.indexOf(category)), 120, ui.mouseX / 2, ui.mouseY / 2, index -> select(keys.get(index)));
    }

    private void renderGrid(Ui ui, List<Achievement> shown, @Nullable Achievement selected, float x, float y, float w, float h, int columns) {
        float cardW = (w - (columns - 1) * CARD_GAP) / columns;
        int rows = (shown.size() + columns - 1) / columns;
        gridScroll.content(rows * CARD_H + Math.max(0, rows - 1) * CARD_GAP, Math.round(h));
        int offset = gridScroll.offset();
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        for (int i = 0; i < shown.size(); i++) {
            float cy = y + (i / columns) * (CARD_H + CARD_GAP) - offset;
            if (cy + CARD_H > y && cy < y + h) {
                Achievement achievement = shown.get(i);
                GridCard.of("").selected(achievement == selected).onClick(() -> {
                    if (!achievement.id().equals(selectedId)) {
                        panel.reset();
                    }
                    selectedId = achievement.id();
                    panelOpen = true;
                }).body((u, ix, iy, iw, ih) -> renderCard(u, achievement, ix, iy, iw)).render(ui, x + (i % columns) * (cardW + CARD_GAP), cy, cardW, CARD_H);
            }
        }
        ui.unclip();
    }

    /** The inside of a card (mockup {@code .gc.ach}), in the padded rectangle. */
    private static void renderCard(Ui ui, Achievement achievement, float x, float y, float w) {
        boolean concealed = concealed(achievement);
        AchievementRing.render(ui, x, y, RING, concealed ? null : icon(achievement), ringToken(achievement));
        float segX = x + RING + 12;
        float segW = w - RING - 12;
        if (!concealed && pinned(achievement)) {
            // pinned to the HUD: the pin at the end of the levels' line, as the panel's button shows it
            ui.icon(Icons.Shape.PIN, x + w - PIN_MARK, y + RING / 2f - PIN_MARK / 2f, PIN_MARK, ui.c("text.secondary"));
            ui.tooltipIfOver(x + w - PIN_MARK - 2, y + RING / 2f - PIN_MARK / 2f - 2, PIN_MARK + 4, PIN_MARK + 4, Component.translatable("rysquests.achievements.pinned_mark"));
            segW -= PIN_MARK + 8;
        }
        if (concealed) {
            LevelSegments.render(ui, segX, y + RING / 2f - LevelSegments.HEIGHT / 2f, segW, new float[] {0f});
        } else {
            LevelSegments.render(ui, segX, y + RING / 2f - LevelSegments.HEIGHT / 2f, segW, fills(achievement));
        }
        float pen = y + RING + 3 + 5;
        ui.labelFit(name(achievement), x, pen + Ui.lineHeight(13) / 2f, w, 13, Ui.SEMIBOLD, ui.c("text.primary"));
        pen += Ui.lineHeight(13) + 5;
        String description = description(achievement);
        List<String> lines = ui.wrap(description, w, 11.5f, Ui.REGULAR, 2);
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x, pen + i * 15.5f + 7.75f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        if (lines.size() == 2 && !String.join(" ", lines).equals(description)) {
            ui.tooltipIfOver(x, pen, w, 31, Component.literal(description));
        }
        pen += 31 + 4 + 5;
        Tier next = next(achievement);
        String count;
        String token;
        if (concealed) {
            count = tr("rysquests.achievements.secret");
            token = "text.muted";
        } else if (next == null) {
            count = tr("rysquests.achievements.all_levels");
            token = "state.ok";
        } else {
            count = text("rysquests.count.of", amount(achievement, progress(achievement), true), withUnit(achievement, next.amount(), true));
            token = "text.secondary";
        }
        ui.labelFit(count, x, pen + Ui.lineHeight(11.5f) / 2f, w, 11.5f, Ui.REGULAR, ui.c(token));
    }

    // --- the detail on the right ---

    private void renderPanel(Ui ui, @Nullable Achievement achievement, int x, int y, int w, int h, boolean overlay) {
        if (achievement == null) {
            panel.render(ui, x, y, w, h, (u, bx, by, bw) -> 0, null);
            return;
        }
        boolean concealed = concealed(achievement);
        Tier next = next(achievement);
        int n = achievement.tiers().size();
        TitleReward title = lastTitle(achievement);
        DetailPanel.Action action = null;
        if (title != null) {
            boolean owns = ClientAchievements.owns(title.id());
            boolean worn = title.id().equals(ClientAchievements.activeTitle());
            if (owns && !worn) {
                action = DetailPanel.Action.button(text("rysquests.achievements.wear_title", tr(title.id())), () -> PacketDistributor.sendToServer(new SelectTitlePayload(title.id())));
            } else if (owns) {
                action = DetailPanel.Action.state(text("rysquests.achievements.wearing", tr(title.id())));
            } else if (!concealed) {
                action = DetailPanel.Action.state(text("rysquests.achievements.title_for_last", tr(title.id())));
            }
        }
        panel.render(ui, x, y, w, h, (u, bx, by, bw) -> {
            float pen = by;
            String category = achievement.category().map(AchievementsView::tr).orElse("");
            if (!category.isEmpty()) {
                pen += DetailPanel.rubric(u, bx, pen, bw, category, null) + 8;
            }
            float ring = 56;
            AchievementRing.render(u, bx, pen, ring, concealed ? null : icon(achievement), ringToken(achievement));
            float textX = bx + ring + 12;
            float buttons = 0;
            if (overlay) {
                float cx = bx + bw - 28;
                boolean over = u.over(cx, pen + 14, 28, 28);
                u.icon(Icons.Shape.CROSS, cx + 7, pen + 21, 14, u.c(over ? "text.primary" : "text.muted"));
                u.hit(cx, pen + 14, 28, 28, () -> panelOpen = false);
                buttons += 36;
            }
            if (next != null && !concealed) {
                ResourceLocation quest = achievement.questId(next);
                Pin pin = pin(quest);
                float px = bx + bw - buttons - 34;
                boolean over = pin != Pin.FULL && u.over(px, pen + 11, 34, 34);
                int border = u.c(pin == Pin.PINNED ? "text.secondary" : "button.border");
                u.box(px, pen + 11, 34, 34, 9, u.c(over ? "slot.hover" : pin == Pin.FULL ? "card.bg" : "button.bg"), border);
                u.icon(Icons.Shape.PIN, px + 9, pen + 20, 16, u.c(pin == Pin.PINNED ? "text.primary" : pin == Pin.FULL ? "text.dim" : "text.secondary"));
                int limit = ClientBook.current().meta().hud().maxPinned();
                u.tooltipIfOver(px, pen + 11, 34, 34, switch (pin) {
                    case PINNED -> Component.translatable("rysquests.achievements.unpin");
                    case FREE -> Component.translatable("rysquests.achievements.pin");
                    case FULL -> Component.translatable("rysquests.achievements.pin_full", limit, limit);
                });
                pinButton = new float[] {px, pen + 11, 34, 34};
                if (pin != Pin.FULL) {
                    u.hit(px, pen + 11, 34, 34, () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of())));
                }
                buttons += 34 + 8;
            }
            float textW = bx + bw - buttons - textX;
            u.labelFit(name(achievement), textX, pen + 17, textW, 18, Ui.DISPLAY, u.c("text.primary"));
            int best = best(achievement);
            String state;
            if (concealed) {
                state = tr("rysquests.achievements.secret");
            } else if (next == null) {
                state = tr("rysquests.achievements.all_levels");
            } else {
                state = text("rysquests.achievements.level_state", best + 1, n, withUnit(achievement, next.amount() - progress(achievement), false));
            }
            List<String> sub = u.wrap(state, textW, 12, Ui.REGULAR, 2);
            for (int i = 0; i < sub.size(); i++) {
                u.label(sub.get(i), textX, pen + 34 + i * 15 + 7, 12, Ui.REGULAR, u.c("text.muted"));
            }
            int lines0 = sub.size();
            String rarity = concealed ? null : rarityLine(achievement);
            if (rarity != null) {
                u.labelFit(rarity, textX, pen + 34 + lines0 * 15 + 7, textW, 12, Ui.REGULAR, u.c("text.muted"));
                lines0++;
            }
            pen += Math.max(ring, 34 + lines0 * 15) + DetailPanel.GAP;
            List<String> lines = u.wrap(description(achievement), bw, 12.5f, Ui.REGULAR, 8);
            for (int i = 0; i < lines.size(); i++) {
                u.label(lines.get(i), bx, pen + i * 19 + 9.5f, 12.5f, Ui.REGULAR, u.c("text.secondary"));
            }
            pen += lines.size() * 19 + DetailPanel.GAP;
            if (concealed) {
                return pen - by;
            }
            Quest marks = marks(achievement);
            if (marks != null) {
                pen += renderCollection(u, marks, bx, pen, bw) + DetailPanel.GAP;
            }
            pen += DetailPanel.rubric(u, bx, pen, bw, tr("rysquests.achievements.tiers"), null) + 6;
            for (int i = 0; i < n; i++) {
                pen += renderTier(u, achievement, i, bx, pen, bw) + 6;
            }
            if (next != null) {
                pen += 6;
                u.bar(bx, pen, bw, 6, 3, ratio(achievement), u.c("text.secondary"));
                pen += 6 + 6;
                int index = achievement.tiers().indexOf(next);
                u.labelFit(text("rysquests.achievements.until_level", amount(achievement, progress(achievement), false), withUnit(achievement, next.amount(), false), index + 1), bx, pen + 8, bw, 12, Ui.REGULAR, u.c("text.muted"));
                pen += 16;
            }
            return pen - by;
        }, action);
    }

    /** The pin of a level: pinned, free to pin, or not, since the HUD holds as many cards as the book allows. */
    enum Pin {
        PINNED, FREE, FULL
    }

    static Pin pin(ResourceLocation quest) {
        if (ClientProgress.pinned().contains(quest)) {
            return Pin.PINNED;
        }
        boolean free = PinLimit.canPin(quest, ClientProgress.pinned(), ClientProgress.pinnedWays().size(), ClientBook.current().meta().hud().maxPinned(),
                ClientBook.current().quests()::get, ClientProgress::get);
        return free ? Pin.FREE : Pin.FULL;
    }

    /**
     * The items of a collection (ADR-62): "Коллекция · 5 из 8" and a slot per item, a collected one with a check in its corner and a
     * green edge, one still missing faded. The tooltip is the item's own with a line of its state. @return its height
     */
    private static float renderCollection(Ui ui, Quest marks, float x, float y, float w) {
        QuestProgress own = ClientProgress.get(marks.id());
        int size = marks.tasks().size();
        float pen = y;
        pen += DetailPanel.rubric(ui, x, pen, w, tr("rysquests.achievements.collection"),
                text("rysquests.count.of", CollectionMarks.count(marks, own), size)) + 8;
        int perRow = Math.max(1, (int) ((w + SLOT_GAP) / (SLOT + SLOT_GAP)));
        for (int i = 0; i < size; i++) {
            float sx = x + (i % perRow) * (SLOT + SLOT_GAP);
            float sy = pen + (i / perRow) * (SLOT + SLOT_GAP);
            TaskDef task = marks.tasks().get(i);
            boolean got = CollectionMarks.collected(marks, own, i);
            ItemStack stack = TaskText.icon(task);
            ui.cell(stack, sx, sy, SLOT, SLOT, 8, 22, ui.c("slot.bg"), ui.c(got ? "state.ok" : "slot.border"));
            if (got) {
                ui.aboveItems(() -> {
                    ui.circle(sx + SLOT - 13, sy + 1, 12, ui.c("state.ok"));
                    ui.icon(Icons.Shape.CHECK, sx + SLOT - 11, sy + 3, 8, ui.c("accent.text"));
                });
            } else {
                ui.veil(sx + 1, sy + 1, SLOT - 2, SLOT - 2, 7, ui.c("slot.bg"), 0.55f);
            }
            if (ui.over(sx, sy, SLOT, SLOT)) {
                Component note = collectionNote(task, own.task(i), got);
                ui.hoveredNote = note;
                if (stack.isEmpty()) {
                    ui.hoveredText = Component.literal(TaskText.name(task) + " · ").append(note);
                }
            }
        }
        int rows = (size + perRow - 1) / perRow;
        pen += rows * SLOT + Math.max(0, rows - 1) * SLOT_GAP;
        return pen - y;
    }

    /** The line under an item of a collection: collected, or what is missing, "нужно 64 сразу" for an item counted by the stack. */
    private static Component collectionNote(TaskDef task, int value, boolean got) {
        if (got) {
            return Component.translatable("rysquests.achievements.collected").withStyle(ChatFormatting.GREEN);
        }
        if (task.target() > 1) {
            return Component.translatable("rysquests.achievements.collect_at_once", task.target(), Math.min(value, task.target())).withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("rysquests.achievements.not_collected").withStyle(ChatFormatting.GRAY);
    }

    /** How a vanilla statistic is counted for players: ticks as hours or minutes, centimetres as kilometres or metres, damage in tenths as points. */
    private record Unit(double scale, @Nullable String key) {
        static final Unit NONE = new Unit(1, null);
    }

    /** The statistic an achievement counts, or null when it counts something else. */
    @Nullable
    private static StatTask statTask(Achievement achievement) {
        if (achievement.tiers().isEmpty()) {
            return null;
        }
        works.repka.rysquests.data.Quest quest = BookData.book().quests().get(achievement.questId(achievement.tiers().get(0)));
        return quest != null && !quest.tasks().isEmpty() && quest.tasks().get(0) instanceof StatTask stat ? stat : null;
    }

    private static Unit unit(Achievement achievement) {
        StatTask stat = statTask(achievement);
        if (stat == null || !stat.stat().equals(ResourceLocation.withDefaultNamespace("custom"))) {
            return Unit.NONE;
        }
        String path = stat.entry().getPath();
        int top = achievement.tiers().get(achievement.tiers().size() - 1).amount();
        if (path.endsWith("_one_cm")) {
            return top >= 100_000 ? new Unit(1e-5, "rysquests.unit.km") : new Unit(0.01, "rysquests.unit.m");
        }
        if (path.contains("time")) {
            return top >= 72_000 ? new Unit(1 / 72_000.0, "rysquests.unit.h") : new Unit(1 / 1_200.0, "rysquests.unit.min");
        }
        if (path.startsWith("damage_")) {
            return new Unit(0.1, null);
        }
        return Unit.NONE;
    }

    /** An amount of the achievement's counter in its units, without the unit: "18.4", "4.4k" on a card, "4 400" in the panel. */
    private static String amount(Achievement achievement, long value, boolean compact) {
        Unit unit = unit(achievement);
        if (unit.scale() == 1) {
            return compact ? Numbers.compact(value) : Sidebar.full(value);
        }
        double scaled = value * unit.scale();
        return scaled >= 1000 ? (compact ? Numbers.compact(scaled) : Sidebar.full(Math.round(scaled))) : Numbers.trim(scaled);
    }

    /** The same with its unit: "50 км", "24 ч". */
    private static String withUnit(Achievement achievement, long value, boolean compact) {
        Unit unit = unit(achievement);
        String number = amount(achievement, value, compact);
        return unit.key() == null ? number : text(unit.key(), number);
    }

    /** One level (mockup {@code .tier}): its ring, "Уровень 2 · 10 000", the rewards, and its state on the right. @return its height */
    private static float renderTier(Ui ui, Achievement achievement, int index, float x, float y, float w) {
        Tier tier = achievement.tiers().get(index);
        int n = achievement.tiers().size();
        boolean got = reached(achievement, tier);
        boolean current = tier == next(achievement);
        float h = 16 + Ui.lineHeight(13) + Ui.lineHeight(11.5f);
        ui.box(x, y, w, h, 10, ui.c("card.bg"), ui.c("panel.border"));
        float cy = y + h / 2f;
        AchievementRing.dot(ui, x + 12, cy - 7, 14, LevelSegments.token(index, n));
        String status = got ? tr("rysquests.achievements.level_got") : current ? text("rysquests.count.of", amount(achievement, progress(achievement), false), withUnit(achievement, tier.amount(), false)) : "";
        float statusW = status.isEmpty() ? 0 : ui.width(status, 12, Ui.REGULAR) + 10;
        float textX = x + 12 + 14 + 10;
        float textW = x + w - 12 - statusW - textX;
        ui.labelFit(text("rysquests.achievements.level_name", index + 1, withUnit(achievement, tier.amount(), false)), textX, y + 8 + Ui.lineHeight(13) / 2f, textW, 13, Ui.MEDIUM, ui.c("text.primary"));
        ui.labelFit(rewards(tier), textX, y + 8 + Ui.lineHeight(13) + Ui.lineHeight(11.5f) / 2f, textW, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        if (!status.isEmpty()) {
            ui.labelRight(status, x + w - 12, cy, 12, Ui.REGULAR, ui.c(got ? "state.ok" : "text.muted"));
        }
        return h;
    }

    private static String rewards(Tier tier) {
        List<String> parts = new ArrayList<>();
        for (RewardDef reward : tier.rewards()) {
            if (reward instanceof CurrencyReward currency) {
                parts.add("+" + ShopView.money(currency.id(), currency.amount()));
            } else if (reward instanceof XpReward xp && xp.points().isPresent()) {
                parts.add(Plural.full("rysquests.plural.xp", xp.points().get()));
            } else if (reward instanceof XpReward xp && xp.levels().isPresent()) {
                parts.add(Plural.full("rysquests.plural.level", xp.levels().get()));
            } else if (reward instanceof TitleReward title) {
                parts.add(text("rysquests.achievements.title_reward", tr(title.id())));
            }
        }
        return parts.isEmpty() ? tr("rysquests.achievements.no_reward") : String.join(" · ", parts);
    }

    /** The title given by the achievement's last level that gives one. */
    @Nullable
    private static TitleReward lastTitle(Achievement achievement) {
        TitleReward found = null;
        for (Tier tier : achievement.tiers()) {
            for (RewardDef reward : tier.rewards()) {
                if (reward instanceof TitleReward title) {
                    found = title;
                }
            }
        }
        return found;
    }

    // --- input ---

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (categories.scrolled(mouseX / 2, mouseY / 2, amount)) {
            return;
        }
        if (panel.contains(mouseX, mouseY)) {
            panel.scroll(amount);
        } else if (mouseX < sideRight) {
            sideScroll.wheel(amount, WHEEL);
        } else {
            gridScroll.wheel(amount, WHEEL);
        }
    }

    @Override
    public boolean key(int key) {
        if (categories.keyPressed(key)) {
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && titleListOpen) {
            titleListOpen = false;
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && panelOpen && screen.compact()) {
            panelOpen = false;
            return true;
        }
        return false;
    }

    @Override
    public void closed() {
        categories.close();
        titleListOpen = false;
    }

    /** Where the pin of the selected achievement was last drawn, in the book's pixels, for the autotest to hover it. */
    @Nullable
    private float[] pinButton;

    @Nullable
    float[] pinButtonForTest() {
        return pinButton;
    }

    void selectForTest(ResourceLocation id) {
        selectedId = id;
        panelOpen = true;
    }

    void showForTest(int filter, String categoryKey) {
        show = Show.values()[filter];
        category = categoryKey;
    }

    void closePanelForTest() {
        panelOpen = false;
    }
}
