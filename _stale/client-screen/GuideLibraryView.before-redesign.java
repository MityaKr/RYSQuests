package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.ryscore.ui.Icons;

/** The library of guides, from {@code design/Guides.dc.html}: cards, and the contents of the selected guide on the right. */
final class GuideLibraryView implements BookView {
    private static final int PANEL_W = 330;
    private static final int PAD_X = 20;
    private static final int PAD_Y = 16;
    private static final int GAP = 12;
    private static final int HEADER_H = 38;
    private static final int SEARCH_W = 286;
    private static final int CHIP_H = Chips.HEIGHT;
    private static final int CHIP_ROW = Chips.ROW;
    private static final int BANNER_H = 54;
    private static final int COLUMNS = 4;
    private static final int CARD_H = 104;
    private static final int CARD_GAP = 10;
    private static final int ROW_H = 34;
    private static final int ROW_STEP = 44;
    private static final int WHEEL = 48;

    private final BookScreen screen;
    private final Guides guides;
    private final Scroll gridScroll = new Scroll();
    private final Scroll contentsScroll = new Scroll();
    private int panelLeft;

    GuideLibraryView(BookScreen screen, Guides guides) {
        this.screen = screen;
        this.guides = guides;
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        panelLeft = x + w - PANEL_W;
        List<Guide> shown = shown();
        Guide selected = selected(shown);
        renderPanel(ui, selected, panelLeft, y, PANEL_W, h);
        float left = x + PAD_X;
        float inner = panelLeft - x - PAD_X * 2;
        float cursor = y + PAD_Y;
        renderHeader(ui, left, cursor, inner);
        cursor += HEADER_H + GAP;
        cursor = renderChips(ui, left, cursor, inner) + GAP;
        Guides.Focus focus = guides.focus();
        if (focus != null) {
            renderBanner(ui, focus, left, cursor, inner);
            cursor += BANNER_H + GAP;
        }
        List<String> hint = ui.wrap(tr("rysquests.guides.hint"), inner, 11, Ui.REGULAR, 3);
        float bottom = y + h - PAD_Y;
        float hintTop = bottom - hint.size() * 14;
        ui.lines(hint, left, hintTop + hint.size() * 7f, 14, 11, Ui.REGULAR, ui.c("text.dim"));
        renderGrid(ui, shown, selected, left, cursor, inner, hintTop - GAP - cursor);
    }

    // --- what is listed ---

    private List<Guide> shown() {
        String query = BookState.sectionSearch.trim();
        List<Guide> list = new ArrayList<>();
        for (Guide guide : guides.all()) {
            boolean category = BookState.sectionCategory.isEmpty() || guide.category().filter(BookState.sectionCategory::equals).isPresent();
            if (category && (query.isEmpty() || guides.matches(guide, query))) {
                list.add(guide);
            }
        }
        Comparator<Guide> byName = Comparator.comparing(guide -> tr(guide.title()));
        list.sort(BookState.sectionByName ? byName : Comparator.<Guide, Integer>comparing(guide -> rank(guides.state(guide))).thenComparing(byName));
        return list;
    }

    private static int rank(Guides.State state) {
        return switch (state) {
            case NEEDED -> 0;
            case OPEN -> 1;
            case DONE -> 2;
            case LOCKED -> 3;
        };
    }

    @Nullable
    private static Guide selected(List<Guide> shown) {
        Guide chosen = BookState.guide == null ? null : ClientBook.current().guides().get(BookState.guide);
        return chosen != null ? chosen : shown.isEmpty() ? null : shown.getFirst();
    }

    // --- the column of cards ---

    private void renderHeader(Ui ui, float x, float y, float w) {
        ui.label(tr("rysquests.guides.title"), x, y + 12.5f, 19, Ui.DISPLAY, ui.c("text.primary"));
        int pages = guides.all().stream().mapToInt(guide -> guide.pages().size()).sum();
        int read = guides.all().stream().mapToInt(ClientProgress::readPages).sum();
        ui.label(text("rysquests.guides.summary", Ui.counted("rysquests.plural.guide", guides.all().size()), Ui.counted("rysquests.plural.page", pages), read),
                x, y + 25 + 7, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        int searchW = Math.round(Math.min(SEARCH_W, w / 2f));
        screen.renderSectionSearch(ui, x + w, y + HEADER_H / 2f, searchW, null);
        if (net.minecraft.client.Minecraft.getInstance().player.hasPermissions(2)) {
            float size = 34;
            float plusX = x + w - searchW - 8 - size;
            float plusY = y + HEADER_H / 2f - size / 2f;
            boolean over = ui.over(plusX, plusY, size, size);
            ui.box(plusX, plusY, size, size, 9, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
            ui.icon(Icons.Shape.PLUS, plusX + 11, plusY + 11, 12, ui.c("button.fg"));
            ui.tooltipIfOver(plusX, plusY, size, size, Component.translatable("rysquests.editor.new_guide"));
            ui.hit(plusX, plusY, size, size, screen::newGuide);
        }
    }

    /** @return the y below the chips */
    private float renderChips(Ui ui, float x, float y, float w) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Integer> named = new TreeMap<>();
        for (Guide guide : guides.all()) {
            guide.category().ifPresent(category -> named.merge(category, 1, Integer::sum));
        }
        counts.put("", guides.all().size());
        named.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(entry -> tr(entry.getKey())))
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        List<Chips.Chip> chips = new ArrayList<>();
        counts.forEach((key, count) -> chips.add(new Chips.Chip(key, key.isEmpty() ? tr("rysquests.guides.category_all") : tr(key), count)));
        float[] end = Chips.render(ui, chips, BookState.sectionCategory, key -> BookState.sectionCategory = key, x, y, w);
        float pen = end[0];
        float row = end[1];
        String sort = tr(BookState.sectionByName ? "rysquests.guides.sort_name" : "rysquests.guides.sort_needed");
        float sortW = ui.width(sort, 12, Ui.REGULAR) + 24 + 14;
        if (pen + sortW > x + w) {
            row += CHIP_ROW;
        }
        float sortX = x + w - sortW;
        ui.box(sortX, row, sortW, CHIP_H, CHIP_H / 2f, ui.over(sortX, row, sortW, CHIP_H) ? ui.c("button.bg") : 0, ui.c("panel.border"));
        ui.label(sort, sortX + 12, row + CHIP_H / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
        ui.icon(Icons.Shape.CHEVRON_DOWN, sortX + sortW - 21, row + CHIP_H / 2f - 4.5f, 9, ui.c("text.muted"));
        ui.hit(sortX, row, sortW, CHIP_H, () -> BookState.sectionByName = !BookState.sectionByName);
        return row + CHIP_H;
    }

    private void renderBanner(Ui ui, Guides.Focus focus, float x, float y, float w) {
        ui.box(x, y, w, BANNER_H, 12, ui.warmBorder(0.11f), ui.warmBorder(0.34f));
        float cy = y + BANNER_H / 2f;
        String caption = tr("rysquests.guides.for_quest");
        float pen = x + 15;
        ui.caps(caption, pen, cy, ui.c("accent"));
        pen += ui.capsWidth(caption) + 12;
        ui.item(icon(focus.guide()), pen, cy - 12, 24);
        pen += 24 + 12;
        String open = tr("rysquests.guides.open_page");
        float buttonW = ui.buttonWidth(open, 12, Ui.SEMIBOLD, 12);
        float buttonX = x + w - 15 - buttonW;
        ui.button(open, buttonX, cy - 15, buttonW, 30, 8, 12, Ui.SEMIBOLD, 12, Ui.Style.ACCENT, () -> BookState.openGuide(focus.guide(), focus.page()));
        Guide.Page page = focus.guide().pages().get(focus.page());
        String where = text("rysquests.guides.page_ref", tr(focus.guide().title()), focus.page() + 1, tr(page.title()));
        String why = text("rysquests.guides.needed_for", tr(focus.quest().title()));
        float[] widths = Ui.shrink(buttonX - 12 - pen, 12, new float[] {ui.width(where, 13, Ui.REGULAR), ui.width(why, 12, Ui.REGULAR)},
                new float[] {ui.minWidth(where, 13, Ui.REGULAR), ui.minWidth(why, 12, Ui.REGULAR)});
        ui.lines(ui.wrap(where, widths[0] + 1, 13, Ui.REGULAR, 2), pen, cy, 16, 13, Ui.REGULAR, ui.c("text.primary"));
        ui.lines(ui.wrap(why, widths[1] + 1, 12, Ui.REGULAR, 2), pen + widths[0] + 12, cy, 15, 12, Ui.REGULAR, ui.c("button.fg"));
    }

    private void renderGrid(Ui ui, List<Guide> shown, @Nullable Guide selected, float x, float y, float w, float h) {
        float cardW = (w - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
        gridScroll.content(rows * CARD_H + Math.max(0, rows - 1) * CARD_GAP, Math.round(h));
        int offset = gridScroll.offset();
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        for (int i = 0; i < shown.size(); i++) {
            float cy = y + (i / COLUMNS) * (CARD_H + CARD_GAP) - offset;
            if (cy + CARD_H > y && cy < y + h) {
                renderCard(ui, shown.get(i), shown.get(i) == selected, x + (i % COLUMNS) * (cardW + CARD_GAP), cy, cardW);
            }
        }
        ui.unclip();
    }

    private void renderCard(Ui ui, Guide guide, boolean selected, float x, float y, float w) {
        Guides.State state = guides.state(guide);
        ui.box(x, y, w, CARD_H, 12, ui.c("card.bg"), state == Guides.State.NEEDED ? ui.warmBorder(0.34f) : ui.c("panel.border"));
        if (selected) {
            ui.strokeBold(x, y, w, CARD_H, 12, ui.c("accent"));
        }
        float left = x + 13;
        float inner = w - 26;
        ui.cell(icon(guide), left, y + 13, 34, 34, 9, 20, ui.c("slot.bg"), ui.c("card.border"));
        float textX = left + 34 + 10;
        float textW = inner - 44;
        ui.labelFitTip(tr(guide.title()), textX, y + 13 + 9, textW, 13, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.labelFit(subtitle(guide), textX, y + 13 + 16 + 7 + 1, textW, 11, Ui.REGULAR, ui.c("text.muted"));
        int read = ClientProgress.readPages(guide);
        String count = read + " / " + guide.pages().size();
        float countW = ui.width(count, 11, Ui.REGULAR);
        float barY = y + 13 + 34 + 8;
        ui.bar(left, barY + 5, inner - countW - 8, 4, 2, (float) read / guide.pages().size(), barColor(ui, state));
        ui.labelRight(count, left + inner, barY + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        ui.labelFit(tag(guide, state), left, barY + 14 + 8 + 7, inner, 11, Ui.REGULAR, tagColor(ui, state));
        if (state == Guides.State.LOCKED) {
            ui.veil(x, y, w, CARD_H, 12, ui.c("panel.bg"), 0.45f);
        }
        ui.hit(x, y, w, CARD_H, () -> {
            if (selected && state != Guides.State.LOCKED) {
                BookState.openGuide(guide, BookState.firstUnread(guide));
            } else {
                BookState.guide = guide.id();
            }
        });
    }

    private static String subtitle(Guide guide) {
        List<String> parts = new ArrayList<>();
        guide.category().ifPresent(category -> parts.add(tr(category)));
        guide.stageTitle().ifPresent(stage -> parts.add(tr(stage)));
        return String.join(" · ", parts);
    }

    private static int barColor(Ui ui, Guides.State state) {
        return ui.c(switch (state) {
            case NEEDED -> "accent";
            case DONE -> "state.ok";
            case OPEN -> "chip.mod.fg";
            case LOCKED -> "text.dim";
        });
    }

    private static int tagColor(Ui ui, Guides.State state) {
        return ui.c(switch (state) {
            case NEEDED -> "accent";
            case DONE -> "state.ok";
            case OPEN -> "text.muted";
            case LOCKED -> "text.dim";
        });
    }

    private static String tag(Guide guide, Guides.State state) {
        return switch (state) {
            case NEEDED -> tr("rysquests.guides.needed");
            case DONE -> tr("rysquests.guides.read");
            case OPEN -> tr("rysquests.guides.available");
            case LOCKED -> guide.stageTitle().map(stage -> text("rysquests.guides.locked_until", tr(stage))).orElseGet(() -> tr("rysquests.guides.locked"));
        };
    }

    static ItemStack icon(Guide guide) {
        return guide.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
    }

    // --- the contents on the right ---

    private void renderPanel(Ui ui, @Nullable Guide guide, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, true, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        if (guide == null) {
            return;
        }
        float left = x + 17;
        float inner = w - 33;
        float top = y + 16;
        ui.cell(icon(guide), left, top, 40, 40, 10, 24, ui.c("slot.bg"), ui.c("card.border"));
        ui.labelFit(tr(guide.title()), left + 52, top + 11, inner - 52, 17, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelFit(text("rysquests.guides.panel_summary", Ui.counted("rysquests.plural.page", guide.pages().size()), ClientProgress.readPages(guide), guides.minutes(guide)),
                left + 52, top + 22 + 7, inner - 52, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        float listTop = top + 40 + 10;
        ui.caps(tr("rysquests.guides.toc"), left, listTop + 7, ui.c("text.dim"));
        listTop += 14 + 10;
        String related = related(guide);
        float bottom = y + h - 16;
        if (related != null) {
            List<String> lines = ui.wrap(related, inner - 26, 12, Ui.REGULAR, 3);
            float boxH = 26 + 14 + 6 + lines.size() * 15;
            float boxY = bottom - boxH;
            ui.box(left, boxY, inner, boxH, 10, ui.c("status.bg"), ui.c("status.border"));
            ui.label(tr("rysquests.guides.related"), left + 13, boxY + 11 + 7, 11, Ui.REGULAR, ui.c("text.muted"));
            ui.lines(lines, left + 13, boxY + 11 + 14 + 6 + lines.size() * 7.5f, 15, 12, Ui.REGULAR, ui.c("button.fg"));
            bottom = boxY - 10;
        }
        float listH = bottom - listTop;
        contentsScroll.content(guide.pages().size() * ROW_STEP - 10, Math.round(listH));
        int offset = contentsScroll.offset();
        boolean locked = ClientProgress.isGuideLocked(guide.id());
        int questPage = guides.questPage(guide);
        ui.clip(Math.round(left), Math.round(listTop), Math.round(inner), Math.round(listH));
        for (int i = 0; i < guide.pages().size(); i++) {
            float rowY = listTop + i * ROW_STEP - offset;
            if (rowY + ROW_H > listTop && rowY < listTop + listH) {
                renderRow(ui, guide, i, i == questPage, locked, left, rowY, inner);
            }
        }
        ui.unclip();
    }

    private void renderRow(Ui ui, Guide guide, int index, boolean forQuest, boolean locked, float x, float y, float w) {
        Guide.Page page = guide.pages().get(index);
        boolean read = ClientProgress.isRead(guide, page);
        if (forQuest) {
            ui.box(x, y, w, ROW_H, 8, ui.warmBorder(0.11f), ui.warmBorder(0.34f));
        } else if (!locked && ui.over(x, y, w, ROW_H)) {
            ui.fill(x, y, w, ROW_H, 8, ui.c("button.bg"));
        }
        float cy = y + ROW_H / 2f;
        ui.box(x + 11, cy - 9, 18, 18, 5, read ? ui.c("state.ok") : ui.c("slot.bg"), ui.c("card.border"));
        if (read) {
            ui.check(x + 14, cy - 6, 12, ui.c("accent.text"));
        }
        String note = forQuest ? tr("rysquests.guides.for_quest_note") : "";
        float noteW = note.isEmpty() ? 0 : ui.width(note, 10.5f, Ui.REGULAR) + 10;
        ui.labelFitTip((index + 1) + " · " + tr(page.title()), x + 39, cy, w - 39 - 11 - noteW, 12.5f, Ui.REGULAR, ui.c(forQuest ? "text.primary" : read ? "text.muted" : "text.body"));
        if (!note.isEmpty()) {
            ui.labelRight(note, x + w - 11, cy, 10.5f, Ui.REGULAR, ui.c("accent"));
        }
        if (locked) {
            ui.veil(x, y, w, ROW_H, 8, ui.c("card.bg"), 0.45f);
        } else {
            ui.hit(x, y, w, ROW_H, () -> BookState.openGuide(guide, index));
        }
    }

    /** Which chapters have quests that point at the guide. */
    @Nullable
    private String related(Guide guide) {
        Map<Integer, Integer> perChapter = new TreeMap<>();
        for (Quest quest : guides.referencing(guide.id())) {
            ResourceLocation chapter = quest.chapter().orElse(null);
            if (BookData.inStory(quest) && BookData.chapterNumber(chapter) > 0) {
                perChapter.merge(BookData.chapterNumber(chapter), 1, Integer::sum);
            }
        }
        if (perChapter.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        perChapter.forEach((number, count) -> parts.add(text("rysquests.guides.related_chapter", count, Ui.roman(number))));
        return text("rysquests.guides.related_quests", String.join(", ", parts));
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        (mouseX >= panelLeft ? contentsScroll : gridScroll).wheel(amount, WHEEL);
    }
}
