package works.repka.rysquests.client.screen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.reward.ItemReward;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;

/**
 * The window of results under the search field (plan, section 3, pictures 30, 30b): groups of quests, items, guides, offers and
 * achievements, three rows each and "ещё N" for the rest; a group shows only when something was found in it. Every row has its
 * action on the right ("На карте", "Сдать", "Рецепт", "Где взять", "Купить", "Читать", "Открыть"), Enter runs the chosen one, the arrows
 * move the choice. Nothing found: "может быть" by close words of the index, and the recent queries, kept on this computer only.
 */
final class SearchPopover {
    private static final int WIDTH = 580;
    private static final int WIDTH_EMPTY = 496;
    private static final int PAD = 10;
    private static final int ROW_H = 38;
    private static final int QUERY_H = 34;
    private static final int ROWS = 3;
    private static final int ACTION_W = 112;
    private static final int RECENT = 5;
    private static final float Z = 400;
    private static final Path RECENT_FILE = Path.of("config", "rysquests", "recent_searches.txt");

    private record Row(String title, @Nullable String bold, @Nullable ItemStack icon, @Nullable Icons.Shape glyph, String detail, String action, Runnable run) {
    }

    private final BookScreen screen;
    private final Set<SearchIndex.Kind> expanded = EnumSet.noneOf(SearchIndex.Kind.class);
    private String expandedFor = "";
    private int chosen;
    private final List<Runnable> actions = new ArrayList<>();
    /** Kept open after a click inside ("ещё N") although the field lost the keyboard; a click outside or Esc closes it. */
    private boolean pinned;
    @Nullable
    private static List<String> recent;

    SearchPopover(BookScreen screen) {
        this.screen = screen;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    boolean open(boolean focused) {
        return focused || pinned;
    }

    // --- recent queries ---

    static List<String> recent() {
        if (recent == null) {
            recent = new ArrayList<>();
            try {
                Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(RECENT_FILE);
                if (Files.exists(file)) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (!line.isBlank() && recent.size() < RECENT) {
                            recent.add(line.strip());
                        }
                    }
                }
            } catch (IOException ignored) {
                // Recent queries are a convenience: without the file there are none.
            }
        }
        return recent;
    }

    /** Remembers a query that led somewhere, newest first. */
    static void remember(String query) {
        String clean = query.strip();
        if (clean.isEmpty()) {
            return;
        }
        List<String> list = recent();
        list.removeIf(clean::equalsIgnoreCase);
        list.addFirst(clean);
        while (list.size() > RECENT) {
            list.removeLast();
        }
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(RECENT_FILE);
            Files.createDirectories(file.getParent());
            Files.write(file, list, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // As above.
        }
    }

    // --- what a result does ---

    private void act(Runnable action) {
        remember(BookState.search);
        pinned = false;
        action.run();
    }

    private Row questRow(SearchIndex.Named named, String query) {
        Quest quest = (Quest) named.target();
        QuestStatus status = BookState.status(quest);
        String chapter = quest.chapter().map(id -> ClientBook.current().chapters().get(id)).map(Chapter::title).map(SearchPopover::tr).orElse("");
        String state = tr(switch (status) {
            case COMPLETED -> "rysquests.search.state.waiting";
            case CLAIMED -> "rysquests.search.state.done";
            case LOCKED -> "rysquests.search.state.locked";
            default -> quest.options().optional() ? "rysquests.search.state.side" : "rysquests.search.state.open";
        });
        String detail = chapter.isEmpty() ? state : chapter + " · " + state;
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        boolean submit = (status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS) && QuestCard.readyToSubmit(quest, ClientProgress.get(quest.id()));
        if (submit) {
            return new Row(named.name(), query, icon, null, detail, tr("rysquests.search.action.submit"),
                    () -> QuestCard.send(QuestActionPayload.SUBMIT, quest, QuestActionPayload.ALL_TASKS, List.of()));
        }
        return new Row(named.name(), query, icon, null, detail, tr("rysquests.search.action.map"), () -> {
            screen.closePage();
            BookState.select(quest);
            BookState.view = BookState.mapMode;
        });
    }

    private Row itemRow(SearchIndex.Named named, String query) {
        ItemStack stack = (ItemStack) named.target();
        String detail = named.uses() > 0 ? Plural.full("rysquests.search.used_in", named.uses()) : "";
        for (ShopOffer offer : ClientBook.current().offers().values()) {
            if (!offer.isSale() && offer.gives().stream().anyMatch(entry -> entry.item().filter(id -> stack.is(TaskText.stackOf(id).getItem())).isPresent())) {
                return new Row(named.name(), query, stack, null, detail, tr("rysquests.search.action.buy"), () -> openOffer(offer));
            }
        }
        for (Quest quest : ClientBook.current().quests().values()) {
            if (!BookData.inStory(quest) || quest.options().hidden()) {
                continue;
            }
            for (RewardDef reward : quest.rewards()) {
                if (reward instanceof ItemReward item && stack.is(TaskText.stackOf(item.item()).getItem())) {
                    return new Row(named.name(), query, stack, null, detail, tr("rysquests.search.action.where"), () -> {
                        screen.closePage();
                        BookState.select(quest);
                        BookState.view = BookState.mapMode;
                    });
                }
            }
        }
        return new Row(named.name(), query, stack, null, detail, tr("rysquests.search.action.recipe"), () -> Recipes.showRecipes(stack));
    }

    private void openOffer(ShopOffer offer) {
        screen.openTab(BookTabs.byId(BookTabs.SHOP));
        screen.shopView().selectForTest(offer.id());
    }

    private Row guideRow(SearchIndex.Named named, String query) {
        Guide guide = (Guide) named.target();
        String detail = guide.category().map(SearchPopover::tr).map(topic -> topic + " · ").orElse("") + Plural.full("rysquests.plural.page_short", guide.pages().size());
        return new Row(named.name(), query, GuideLibraryView.icon(guide), null, detail, tr("rysquests.search.action.read"), () -> {
            screen.closePage();
            if (ClientProgress.isGuideLocked(guide.id())) {
                BookState.guide = guide.id();
                BookState.openLibrary();
            } else {
                BookState.openGuide(guide, BookState.firstUnread(guide));
            }
        });
    }

    private Row offerRow(SearchIndex.Named named, String query) {
        ShopOffer offer = (ShopOffer) named.target();
        ShopOffer.Entry price = offer.cost().stream().filter(ShopOffer.Entry::isCurrency).findFirst().orElse(null);
        String detail = price == null ? "" : ShopView.money(price.id(), price.amount());
        ItemStack icon = offer.icon().map(TaskText::stackOf).orElseGet(() -> offer.gives().stream().flatMap(entry -> entry.item().stream()).findFirst().map(TaskText::stackOf).orElse(ItemStack.EMPTY));
        return new Row(named.name(), query, icon, null, detail, tr("rysquests.search.action.buy"), () -> openOffer(offer));
    }

    private Row achievementRow(SearchIndex.Named named, String query) {
        Achievement achievement = (Achievement) named.target();
        String category = achievement.category().map(SearchPopover::tr).orElse("");
        int value = AchievementsView.progress(achievement);
        Achievement.Tier next = achievement.tiers().stream().filter(tier -> !ClientProgress.get(achievement.questId(tier)).completed()).findFirst().orElse(null);
        String count = next == null ? tr("rysquests.achievements.all_levels") : text("rysquests.count.of", Numbers.compact(value), Numbers.compact(next.amount()));
        ItemStack icon = achievement.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        return new Row(named.name(), query, icon, null, category.isEmpty() ? count : category + " · " + count, tr("rysquests.search.action.open"), () -> {
            screen.openTab(BookTabs.byId(BookTabs.ACHIEVEMENTS));
            screen.achievementsView().selectForTest(achievement.id());
        });
    }

    private Row row(SearchIndex.Named named, String query) {
        return switch (named.kind()) {
            case QUEST -> questRow(named, query);
            case ITEM -> itemRow(named, query);
            case GUIDE -> guideRow(named, query);
            case OFFER -> offerRow(named, query);
            case ACHIEVEMENT -> achievementRow(named, query);
        };
    }

    // --- drawing ---

    /** Draws the window under the header, if there is anything to show. @param winX the book window */
    void render(Ui ui, int winX, int winY, int winW, int winH, boolean focused, boolean compact) {
        actions.clear();
        if (!open(focused)) {
            return;
        }
        String query = BookState.search.strip();
        if (!query.equals(expandedFor)) {
            expanded.clear();
            expandedFor = query;
            chosen = 0;
        }
        List<Object> lines = new ArrayList<>();
        boolean empty = false;
        if (query.isEmpty()) {
            if (recent().isEmpty()) {
                return;
            }
            lines.add(new String[] {tr("rysquests.search.recent"), null});
            recent().forEach(each -> lines.add(each));
        } else {
            Map<SearchIndex.Kind, List<SearchIndex.Named>> found = SearchIndex.book(query);
            if (found.isEmpty() && !SearchIndex.bookReady()) {
                return;
            }
            if (found.isEmpty()) {
                empty = true;
                List<String> maybe = SearchIndex.maybe(query, 3);
                if (!maybe.isEmpty()) {
                    lines.add(new String[] {tr("rysquests.search.maybe"), null});
                    maybe.forEach(word -> lines.add(new Object[] {word, SearchIndex.count(word)}));
                }
                if (!recent().isEmpty()) {
                    lines.add(new String[] {tr("rysquests.search.recent"), null});
                    recent().forEach(each -> lines.add(each));
                }
            } else {
                for (Map.Entry<SearchIndex.Kind, List<SearchIndex.Named>> group : found.entrySet()) {
                    List<SearchIndex.Named> all = group.getValue();
                    lines.add(new String[] {tr("rysquests.search.group." + group.getKey().name().toLowerCase(java.util.Locale.ROOT)), String.valueOf(all.size())});
                    boolean open = expanded.contains(group.getKey());
                    int shown = open ? all.size() : Math.min(ROWS, all.size());
                    for (int i = 0; i < shown; i++) {
                        lines.add(row(all.get(i), query));
                    }
                    if (shown < all.size()) {
                        lines.add(group.getKey());
                        lines.add(Integer.valueOf(all.size() - shown));
                    }
                }
            }
        }
        float w = compact ? winW - 4 : empty ? WIDTH_EMPTY : WIDTH;
        float x = compact ? winX + 2 : winX + winW - 2 - w;
        float y = winY + BookHeader.HEIGHT - 2;
        float headH = empty ? 10 + Ui.lineHeight(13.5f) + 4 + 2 * Ui.lineHeight(12) + 6 : 0;
        float h = PAD * 2 + headH;
        for (int i = 0; i < lines.size(); i++) {
            h += lineHeight(lines.get(i)) + (i > 0 ? 5 : 0);
        }
        h = Math.min(h, winH - BookHeader.HEIGHT - 8);
        float height = h;
        ui.g.pose().pushPose();
        ui.g.pose().translate(0, 0, Z);
        if (pinned && !focused) {
            ui.hit(winX, winY, winW, winH, () -> pinned = false);
        }
        ui.hit(x, y, w, height, () -> pinned = true);
        ui.shadow(x, y, w, height, 12, 0.7f);
        ui.box(x, y, w, height, 12, ui.c("sidebar.bg"), ui.c("button.border"));
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(height));
        float pen = y + PAD;
        float ix = x + PAD;
        float iw = w - PAD * 2;
        if (empty) {
            pen += 10;
            ui.labelFit(text("rysquests.search.nothing", query), ix + 8, pen + Ui.lineHeight(13.5f) / 2f, iw - 16, 13.5f, Ui.SEMIBOLD, ui.c("text.primary"));
            pen += Ui.lineHeight(13.5f) + 4;
            List<String> about = ui.wrap(tr("rysquests.search.nothing_about"), iw - 16, 12, Ui.REGULAR, 2);
            for (String line : about) {
                ui.label(line, ix + 8, pen + Ui.lineHeight(12) / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
                pen += Ui.lineHeight(12);
            }
            pen += (2 - about.size()) * Ui.lineHeight(12) + 6 + 5;
        }
        int rowIndex = 0;
        for (int i = 0; i < lines.size(); i++) {
            Object line = lines.get(i);
            if (i > 0) {
                pen += 5;
            }
            float lh = lineHeight(line);
            if (line instanceof String[] rubric) {
                float cy = pen + lh / 2f;
                ui.caps(rubric[0], ix, cy, ui.c("text.dim"));
                if (rubric[1] != null) {
                    ui.labelRight(rubric[1], ix + iw, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
                }
            } else if (line instanceof Row row) {
                drawRow(ui, row, rowIndex++, ix, pen, iw);
            } else if (line instanceof SearchIndex.Kind kind) {
                int more = (Integer) lines.get(i + 1);
                String label = Plural.full("rysquests.search.more." + kind.name().toLowerCase(java.util.Locale.ROOT), more);
                float lw = ui.width(label, 12, Ui.MEDIUM);
                boolean over = ui.over(ix + 44, pen, lw, lh);
                ui.label(label, ix + 44, pen + lh / 2f, 12, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
                ui.hit(ix + 44, pen, lw, lh, () -> {
                    expanded.add(kind);
                    pinned = true;
                    screen.focusSearch();
                });
                i++;
            } else if (line instanceof Object[] maybe) {
                String word = (String) maybe[0];
                int index = rowIndex++;
                String count = Plural.full("rysquests.search.results", (Integer) maybe[1]);
                drawQuery(ui, word, count, index, ix, pen, iw, true, () -> setQuery(word));
            } else if (line instanceof String recentQuery) {
                int index = rowIndex++;
                drawQuery(ui, recentQuery, "", index, ix, pen, iw, false, () -> setQuery(recentQuery));
            }
            pen += lh;
        }
        ui.unclip();
        ui.g.pose().popPose();
        chosen = actions.isEmpty() ? 0 : Math.clamp(chosen, 0, actions.size() - 1);
    }

    private void setQuery(String query) {
        screen.setSearch(query);
        pinned = false;
        screen.focusSearch();
    }

    private static float lineHeight(Object line) {
        if (line instanceof String[]) {
            return Ui.lineHeight(11.5f);
        }
        if (line instanceof Row) {
            return ROW_H;
        }
        if (line instanceof SearchIndex.Kind) {
            return Ui.lineHeight(12) + 4;
        }
        if (line instanceof Integer) {
            return -5;
        }
        return QUERY_H;
    }

    private void drawRow(Ui ui, Row row, int index, float x, float y, float w) {
        boolean on = index == chosen;
        boolean over = ui.over(x, y, w, ROW_H);
        if (on || over) {
            ui.fill(x, y, w, ROW_H, 10, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        float cy = y + ROW_H / 2f;
        float pen = x + 6;
        ui.box(pen, cy - 14, 28, 28, 6, ui.c("slot.bg"), ui.c("slot.border"));
        if (row.icon != null && !row.icon.isEmpty()) {
            ui.item(row.icon, pen + 4, cy - 10, 20);
        } else if (row.glyph != null) {
            ui.icon(row.glyph, pen + 7, cy - 7, 14, ui.c("text.secondary"));
        }
        pen += 28 + 11;
        float actionX = x + w - 10 - ACTION_W;
        String kbd = on ? "Enter" : null;
        float kbdW = kbd == null ? 0 : ui.width(kbd, 11, Ui.REGULAR) + 10;
        float actionW = ui.width(row.action, 12, Ui.MEDIUM);
        float ax = x + w - 10 - kbdW - (kbd == null ? 0 : 4) - actionW;
        int actionColor = ui.c(on ? "text.primary" : over && ui.over(ax, y, actionW, ROW_H) ? "text.primary" : "link");
        ui.label(row.action, ax, cy, 12, Ui.MEDIUM, actionColor);
        if (kbd != null) {
            float kx = x + w - 10 - kbdW;
            ui.box(kx, cy - 8, kbdW, 16, 5, ui.c("button.bg"), ui.c("button.border"));
            ui.label(kbd, kx + 5, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        }
        float detailW = Math.min(ui.width(row.detail, 11.5f, Ui.REGULAR), (actionX - 14 - pen) * 0.45f);
        float detailX = actionX - 14 - detailW;
        if (!row.detail.isEmpty()) {
            ui.labelFit(row.detail, detailX, cy, detailW, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        drawName(ui, row.title, row.bold, pen, cy, detailX - 12 - pen);
        Runnable run = () -> act(row.run);
        actions.add(run);
        ui.hit(x, y, w, ROW_H, run);
    }

    /** The name with the found part in bold, as the mockup marks it. */
    private static void drawName(Ui ui, String name, @Nullable String bold, float x, float cy, float w) {
        String shown = ui.fit(name, w, 13, Ui.MEDIUM);
        int at = bold == null || bold.isEmpty() ? -1 : shown.toLowerCase(java.util.Locale.ROOT).indexOf(bold.toLowerCase(java.util.Locale.ROOT));
        if (at < 0) {
            ui.labelFit(name, x, cy, w, 13, Ui.MEDIUM, ui.c("text.primary"));
            return;
        }
        String before = shown.substring(0, at);
        String match = shown.substring(at, at + bold.length());
        String after = shown.substring(at + bold.length());
        float pen = x;
        ui.label(before, pen, cy, 13, Ui.MEDIUM, ui.c("text.secondary"));
        pen += ui.width(before, 13, Ui.MEDIUM);
        ui.label(match, pen, cy, 13, Ui.SEMIBOLD, ui.c("text.primary"));
        pen += ui.width(match, 13, Ui.SEMIBOLD);
        ui.label(after, pen, cy, 13, Ui.MEDIUM, ui.c("text.secondary"));
        if (!shown.equals(name)) {
            ui.tooltipIfOver(x, cy - 9, w, 18, Component.literal(name));
        }
    }

    private void drawQuery(Ui ui, String query, String count, int index, float x, float y, float w, boolean bold, Runnable action) {
        boolean on = index == chosen;
        boolean over = ui.over(x, y, w, QUERY_H);
        if (on || over) {
            ui.fill(x, y, w, QUERY_H, 10, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        float cy = y + QUERY_H / 2f;
        ui.icon(Icons.Shape.MAGNIFIER, x + 8, cy - 7, 14, ui.c("text.muted"));
        float countW = count.isEmpty() ? 0 : ui.width(count, 11.5f, Ui.REGULAR);
        ui.labelFit(query, x + 8 + 14 + 10, cy, w - 32 - countW - 20, 13, bold ? Ui.SEMIBOLD : Ui.MEDIUM, ui.c("text.primary"));
        if (!count.isEmpty()) {
            ui.labelRight(count, x + w - 10, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        actions.add(action);
        ui.hit(x, y, w, QUERY_H, action);
    }

    // --- keys ---

    /** Arrows, Enter and Esc while the window is open. @return whether the key was taken */
    boolean key(int key, boolean focused) {
        if (!open(focused)) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> {
                chosen = actions.isEmpty() ? 0 : (chosen + 1) % actions.size();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                chosen = actions.isEmpty() ? 0 : (chosen - 1 + actions.size()) % actions.size();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (actions.isEmpty()) {
                    return false;
                }
                actions.get(Math.clamp(chosen, 0, actions.size() - 1)).run();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (pinned && !focused) {
                    pinned = false;
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    void close() {
        pinned = false;
    }
}
