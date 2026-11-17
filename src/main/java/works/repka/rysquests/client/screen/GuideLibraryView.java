package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Guide;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;

/**
 * The library of guides (plan, section 3, picture 23): to the right of the topics, a line that says what the guides are with the
 * filters "Все | Непрочитанные | Новые", the guide to continue reading, and the guides as cards in four columns. The mark "прочитан"
 * or "новое" sits on the bottom line of a card, so the name gets the whole width. The topic is picked in the column on the left,
 * which {@link GuidesTab} draws.
 */
final class GuideLibraryView implements BookView {
    private static final int PAD_X = 20;
    private static final int PAD_Y = 18;
    private static final int TOP_H = 28;
    private static final int GAP = 14;
    private static final int HERO_H = 72;
    private static final int COLUMNS = 4;
    private static final int COLUMNS_COMPACT = 3;
    private static final int CARD_H = 89;
    private static final int CARD_GAP = 10;
    private static final int WHEEL = 48;

    private enum Show {
        ALL, UNREAD, NEW
    }

    private final BookScreen screen;
    private final Guides guides;
    private final Scroll gridScroll = new Scroll();
    private Show show = Show.ALL;
    private final Kit.Dropdown topics = new Kit.Dropdown();

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

    /** New: opened by an epoch and not read yet. The library has no other memory of when a guide appeared. */
    static boolean isNew(Guide guide) {
        return guide.stage().isPresent() && !ClientProgress.isGuideLocked(guide.id()) && ClientProgress.readPages(guide) == 0;
    }

    private static boolean unread(Guide guide) {
        return !ClientProgress.isGuideLocked(guide.id()) && ClientProgress.readPages(guide) < guide.pages().size();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        float left = x + PAD_X;
        float inner = w - PAD_X * 2;
        float pen = y + PAD_Y;
        renderTop(ui, left, pen, inner);
        pen += TOP_H + GAP;
        Hero hero = hero();
        if (hero != null) {
            renderHero(ui, hero, left, pen, inner);
            pen += HERO_H + GAP;
        }
        List<Guide> shown = shown();
        float bottom = y + h - PAD_Y;
        if (shown.isEmpty()) {
            renderTopicList(ui);
            if (show == Show.UNREAD) {
                EmptyFilter.render(ui, left, pen, inner, bottom - pen, tr("rysquests.guides.empty.unread"), tr("rysquests.guides.empty.show_all"), () -> show = Show.ALL);
            } else {
                EmptyFilter.render(ui, left, pen, inner, bottom - pen, tr("rysquests.guides.empty.filter"), tr("rysquests.guides.empty.show_all"), () -> {
                    show = Show.ALL;
                    BookState.sectionCategory = "";
                });
            }
            return;
        }
        renderGrid(ui, shown, left, pen, inner, bottom - pen, screen.compact() ? COLUMNS_COMPACT : COLUMNS);
        renderTopicList(ui);
    }

    private void renderTopicList(Ui ui) {
        if (!screen.compact()) {
            topics.close();
            return;
        }
        List<String> keys = new ArrayList<>(List.of(""));
        List<Kit.Option> options = new ArrayList<>(List.of(new Kit.Option(tr("rysquests.guides.show.all"), guides.all().size())));
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        guides.all().forEach(guide -> guide.category().ifPresent(category -> counts.merge(category, 1, Integer::sum)));
        counts.forEach((key, count) -> {
            keys.add(key);
            options.add(new Kit.Option(tr(key), count));
        });
        Kit.dropdownList(ui.g, ui.theme, ui.kitHits, topics, options, Math.max(0, keys.indexOf(BookState.sectionCategory)), 120, ui.mouseX / 2, ui.mouseY / 2,
                index -> BookState.sectionCategory = keys.get(index));
    }

    // --- what is listed ---

    private List<Guide> shown() {
        List<Guide> list = new ArrayList<>();
        for (Guide guide : guides.all()) {
            boolean topic = BookState.sectionCategory.isEmpty() || guide.category().filter(BookState.sectionCategory::equals).isPresent();
            boolean match = switch (show) {
                case ALL -> true;
                case UNREAD -> unread(guide);
                case NEW -> isNew(guide);
            };
            if (topic && match) {
                list.add(guide);
            }
        }
        // Closed guides go last; the rest keeps the book's order, which is the author's.
        list.sort(Comparator.comparing(guide -> ClientProgress.isGuideLocked(guide.id())));
        return list;
    }

    // --- the line on top ---

    private void renderTop(Ui ui, float x, float y, float w) {
        long fresh = guides.all().stream().filter(GuideLibraryView::isNew).count();
        List<String> labels = new ArrayList<>(List.of(tr("rysquests.guides.show.all"), tr("rysquests.guides.show.unread")));
        if (fresh > 0 || show == Show.NEW) {
            labels.add(text("rysquests.guides.show.new", fresh));
        }
        float cw = FilterChips.width(ui, labels, false);
        if (screen.compact()) {
            // The topics of the column on the left become a list in this line (plan, section 7).
            String topic = BookState.sectionCategory.isEmpty() ? tr("rysquests.guides.show.all") : tr(BookState.sectionCategory);
            Kit.dropdown(ui.g, ui.theme, ui.kitHits, topics, Math.round(x / 2f), Math.round((y + TOP_H / 2f - Kit.DROP_H) / 2f), 120,
                    text("rysquests.guides.topic_dd", topic), !BookState.sectionCategory.isEmpty(), ui.mouseX / 2, ui.mouseY / 2);
        } else {
            ui.labelFit(tr("rysquests.guides.library_line"), x, y + TOP_H / 2f, w - cw - 12, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        FilterChips.render(ui, x + w - cw, y, labels, Math.min(show.ordinal(), labels.size() - 1), index -> {
            show = Show.values()[index];
            gridScroll.reset();
        }, false);
    }

    /** The guide to go on with: the page an open quest points at, or else the last guide left half read. */
    private record Hero(Guide guide, int page, @Nullable String quest) {
    }

    @Nullable
    private Hero hero() {
        Guides.Focus focus = guides.focus();
        if (focus != null) {
            return new Hero(focus.guide(), focus.page(), tr(focus.quest().title()));
        }
        Guide last = BookState.guide == null ? null : ClientBook.current().guides().get(BookState.guide);
        if (last != null && !ClientProgress.isGuideLocked(last.id()) && unread(last) && ClientProgress.readPages(last) > 0) {
            return new Hero(last, BookState.firstUnread(last), null);
        }
        return null;
    }

    private void renderHero(Ui ui, Hero hero, float x, float y, float w) {
        ui.box(x, y, w, HERO_H, 14, ui.c("card.bg"), ui.c("card.border"));
        float cy = y + HERO_H / 2f;
        float pen = x + 14;
        ui.box(pen, cy - 22, 44, 44, 6, ui.c("slot.bg"), ui.c("slot.border"));
        ui.item(icon(hero.guide), pen + 6, cy - 16, 32);
        pen += 44 + 14;
        String go = tr("rysquests.guides.read_on");
        float bw = ui.buttonWidth(go, 12.5f, Ui.SEMIBOLD, 14);
        float bx = x + w - 14 - bw;
        ui.button(go, bx, cy - 17, bw, 34, 9, 12.5f, Ui.SEMIBOLD, 14, Ui.Style.ACCENT, () -> BookState.openGuide(hero.guide, hero.page));
        float textW = bx - 14 - pen;
        Guide.Page page = hero.guide.pages().get(Math.clamp(hero.page, 0, hero.guide.pages().size() - 1));
        String title = text("rysquests.guides.hero_title", tr(hero.guide.title()), hero.page + 1, tr(page.title()));
        int read = ClientProgress.readPages(hero.guide);
        String sub = text("rysquests.count.read", read, hero.guide.pages().size());
        if (hero.quest != null) {
            sub += " · " + text("rysquests.guides.for_quest_line", hero.quest);
        }
        float top = cy - (Ui.lineHeight(11) + 3 + Ui.lineHeight(14) + 1 + Ui.lineHeight(12)) / 2f;
        ui.caps(tr("rysquests.guides.continue"), pen, top + Ui.lineHeight(11) / 2f, ui.c("text.dim"));
        top += Ui.lineHeight(11) + 3;
        ui.labelFit(title, pen, top + Ui.lineHeight(14) / 2f, textW, 14, Ui.SEMIBOLD, ui.c("text.primary"));
        top += Ui.lineHeight(14) + 1;
        ui.labelFit(sub, pen, top + Ui.lineHeight(12) / 2f, textW, 12, Ui.REGULAR, ui.c("text.muted"));
    }

    // --- the cards ---

    private void renderGrid(Ui ui, List<Guide> shown, float x, float y, float w, float h, int columns) {
        float cardW = (w - (columns - 1) * CARD_GAP) / columns;
        int rows = (shown.size() + columns - 1) / columns;
        gridScroll.content(rows * CARD_H + Math.max(0, rows - 1) * CARD_GAP, Math.round(h));
        int offset = gridScroll.offset();
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        for (int i = 0; i < shown.size(); i++) {
            float cy = y + (i / columns) * (CARD_H + CARD_GAP) - offset;
            if (cy + CARD_H > y && cy < y + h) {
                Guide guide = shown.get(i);
                boolean locked = ClientProgress.isGuideLocked(guide.id());
                GridCard.of("").off(locked).onClick(locked ? null : () -> BookState.openGuide(guide, BookState.firstUnread(guide)))
                        .body((u, ix, iy, iw, ih) -> renderCard(u, guide, locked, ix, iy, iw, ih))
                        .render(ui, x + (i % columns) * (cardW + CARD_GAP), cy, cardW, CARD_H);
            }
        }
        ui.unclip();
    }

    private static void renderCard(Ui ui, Guide guide, boolean locked, float x, float y, float w, float h) {
        ui.box(x, y, 38, 38, 6, ui.c("slot.bg"), ui.c("slot.border"));
        ui.item(icon(guide), x + 5, y + 5, 28);
        float textX = x + 38 + 10;
        float textW = w - 48;
        float block = Ui.lineHeight(13) + Ui.lineHeight(11.5f);
        float top = y + (38 - block) / 2f;
        ui.labelFit(tr(guide.title()), textX, top + Ui.lineHeight(13) / 2f, textW, 13, Ui.SEMIBOLD, ui.c("text.primary"));
        String sub = guide.category().map(GuideLibraryView::tr).orElse("");
        ui.labelFit(sub, textX, top + Ui.lineHeight(13) + Ui.lineHeight(11.5f) / 2f, textW, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        float cy = y + h - 9;
        int read = ClientProgress.readPages(guide);
        int pages = guide.pages().size();
        if (locked) {
            ui.lock(x, cy - 6, 12, ui.c("text.muted"));
            ui.labelFit(guide.stageTitle().map(stage -> text("rysquests.guides.locked_until", tr(stage))).orElseGet(() -> tr("rysquests.guides.locked")),
                    x + 14, cy, w - 14, 11.5f, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        String count = read > 0 ? text("rysquests.count.read", read, pages) : Plural.full("rysquests.plural.page_short", pages);
        ui.label(count, x, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        float right = x + w;
        if (read >= pages) {
            String tag = tr("rysquests.guides.tag_read");
            float tw = ui.width(tag, 11, Ui.SEMIBOLD) + 14 + 14;
            ui.fill(right - tw, cy - 9, tw, 18, 5, Draw.alpha(ui.c("state.ok"), 0.12f));
            ui.icon(Icons.Shape.CHECK, right - tw + 7, cy - 5, 10, ui.c("state.ok"));
            ui.label(tag, right - tw + 21, cy, 11, Ui.SEMIBOLD, ui.c("state.ok"));
        } else if (isNew(guide)) {
            String tag = tr("rysquests.guides.tag_new");
            float tw = ui.width(tag, 11, Ui.SEMIBOLD) + 14;
            ui.box(right - tw, cy - 9, tw, 18, 5, ui.c("button.bg"), ui.c("button.border"));
            ui.label(tag, right - tw + 7, cy, 11, Ui.SEMIBOLD, ui.c("text.primary"));
        } else {
            float countW = ui.width(count, 11.5f, Ui.REGULAR);
            float barW = Math.min(70, w - countW - 12);
            ui.bar(right - barW, cy - 2, barW, 4, 2, (float) read / Math.max(1, pages), ui.c("state.ok"));
        }
    }

    static ItemStack icon(Guide guide) {
        return guide.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (!topics.scrolled(mouseX / 2, mouseY / 2, amount)) {
            gridScroll.wheel(amount, WHEEL);
        }
    }

    @Override
    public boolean key(int key) {
        return topics.keyPressed(key);
    }

    void showForTest(int filter) {
        show = Show.values()[filter];
    }
}
