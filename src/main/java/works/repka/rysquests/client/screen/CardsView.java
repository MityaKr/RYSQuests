package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.CardsSection.Card;
import works.repka.rysquests.data.Quest;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;

/** The cards template, from {@code design/Cards.dc.html}: a grid with filters and the selected card on the right. */
final class CardsView implements BookView {
    private static final int PANEL_W = 320;
    private static final int PAD_X = 20;
    private static final int PAD_Y = 16;
    private static final int GAP = 12;
    private static final int HEADER_H = 38;
    private static final int COLUMNS = 3;
    private static final int ART_H = 112;
    private static final int ROW_H = 32;
    private static final int WHEEL = 48;
    private static final int OPAQUE = 0xFF000000;
    private static final int HEX = 16;

    private enum State {
        DONE, OPEN, LOCKED
    }

    private final Scroll gridScroll = new Scroll();
    private final Scroll panelScroll = new Scroll();
    private int panelLeft;

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** A pack's own wording where it gave one, otherwise the general one. */
    private static String own(String key, String fallback, Object... args) {
        return Language.getInstance().has(key) ? text(key, args) : text(fallback, args);
    }

    private static State state(CardsSection section, Card card) {
        if (card.done().isPresent() && ClientProgress.get(section.questId(card)).completed()) {
            return State.DONE;
        }
        return card.stage().map(ClientProgress::hasStage).orElse(true) ? State.OPEN : State.LOCKED;
    }

    private static ItemStack icon(Card card) {
        return card.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (!(BookState.customSection() instanceof CardsSection section)) {
            return;
        }
        List<Card> shown = shown(section);
        Card selected = selected(section, shown);
        panelLeft = x + w - PANEL_W;
        renderPanel(ui, section, selected, panelLeft, y, PANEL_W, h);
        float left = x + PAD_X;
        float inner = panelLeft - x - PAD_X * 2;
        float cursor = y + PAD_Y;
        renderHeader(ui, section, left, cursor, inner);
        cursor += HEADER_H + GAP;
        cursor = renderChips(ui, section, left, cursor, inner) + GAP;
        float bottom = y + h - PAD_Y;
        String hintKey = section.title() + ".hint";
        if (Language.getInstance().has(hintKey)) {
            List<String> hint = ui.wrap(tr(hintKey), inner, 11, Ui.REGULAR, 3);
            bottom -= hint.size() * 14;
            ui.lines(hint, left, bottom + hint.size() * 7f, 14, 11, Ui.REGULAR, ui.c("text.dim"));
            bottom -= GAP;
        }
        renderGrid(ui, section, shown, selected, left, cursor, inner, bottom - cursor);
    }

    private static List<Card> shown(CardsSection section) {
        List<Card> list = new ArrayList<>();
        for (Card card : section.cards()) {
            if (BookState.sectionCategory.isEmpty() || card.group().filter(BookState.sectionCategory::equals).isPresent()) {
                list.add(card);
            }
        }
        if (BookState.sectionByName) {
            list.sort(Comparator.comparing(card -> tr(section.cardKey(card, "name"))));
        }
        return list;
    }

    @Nullable
    private static Card selected(CardsSection section, List<Card> shown) {
        String id = BookState.CARD_PICKS.get(section.id());
        for (Card card : section.cards()) {
            if (card.id().equals(id)) {
                return card;
            }
        }
        return shown.stream().filter(card -> state(section, card) == State.OPEN).findFirst().orElse(shown.isEmpty() ? null : shown.getFirst());
    }

    private static void renderHeader(Ui ui, CardsSection section, float x, float y, float w) {
        ui.label(tr(section.title()), x, y + 12.5f, 19, Ui.DISPLAY, ui.c("text.primary"));
        section.subtitle().ifPresent(subtitle -> ui.labelFit(tr(subtitle), x, y + 25 + 7, w * 0.6f, 11.5f, Ui.REGULAR, ui.c("text.muted")));
        int withCondition = (int) section.cards().stream().filter(card -> card.done().isPresent()).count();
        if (withCondition > 0) {
            int done = (int) section.cards().stream().filter(card -> state(section, card) == State.DONE).count();
            String rest = own(section.title() + ".done_count", "rysquests.cards.done_count", withCondition);
            float cy = y + HEADER_H / 2f;
            float restW = ui.width(rest, 12, Ui.REGULAR);
            ui.labelRight(rest, x + w, cy, 12, Ui.REGULAR, ui.c("button.fg"));
            ui.labelRight(String.valueOf(done), x + w - restW - 4, cy, 16, Ui.DISPLAY, ui.c("text.primary"));
        }
    }

    /** @return the y below the chips */
    private static float renderChips(Ui ui, CardsSection section, float x, float y, float w) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        section.groups().forEach(group -> counts.put(group, 0));
        section.cards().forEach(card -> card.group().ifPresent(group -> counts.merge(group, 1, Integer::sum)));
        List<Chips.Chip> chips = new ArrayList<>();
        chips.add(new Chips.Chip("", tr("rysquests.guides.category_all"), section.cards().size()));
        counts.forEach((group, count) -> chips.add(new Chips.Chip(group, tr(section.groupTitle(group)), count)));
        float[] end = Chips.render(ui, chips, BookState.sectionCategory, key -> BookState.sectionCategory = key, x, y, w);
        float row = end[1];
        String sort = tr(BookState.sectionByName ? "rysquests.guides.sort_name" : "rysquests.cards.sort_order");
        float sortW = ui.width(sort, 12, Ui.REGULAR) + 24 + 14;
        if (end[0] + sortW > x + w) {
            row += Chips.ROW;
        }
        float sortX = x + w - sortW;
        ui.box(sortX, row, sortW, Chips.HEIGHT, Chips.HEIGHT / 2f, ui.over(sortX, row, sortW, Chips.HEIGHT) ? ui.c("button.bg") : 0, ui.c("panel.border"));
        ui.label(sort, sortX + 12, row + Chips.HEIGHT / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
        ui.icon(Icons.Shape.CHEVRON_DOWN, sortX + sortW - 21, row + Chips.HEIGHT / 2f - 4.5f, 9, ui.c("text.muted"));
        ui.hit(sortX, row, sortW, Chips.HEIGHT, () -> BookState.sectionByName = !BookState.sectionByName);
        return row + Chips.HEIGHT;
    }

    private void renderGrid(Ui ui, CardsSection section, List<Card> shown, @Nullable Card selected, float x, float y, float w, float h) {
        float cardW = (w - (COLUMNS - 1) * GAP) / COLUMNS;
        int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
        int cardH = cardHeight(ui, section, shown, cardW - 26);
        gridScroll.content(rows * cardH + Math.max(0, rows - 1) * GAP, Math.round(h));
        int offset = gridScroll.offset();
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        for (int i = 0; i < shown.size(); i++) {
            float cy = y + (i / COLUMNS) * (cardH + GAP) - offset;
            if (cy + cardH > y && cy < y + h) {
                renderCard(ui, section, shown.get(i), shown.get(i) == selected, x + (i % COLUMNS) * (cardW + GAP), cy, cardW, cardH);
            }
        }
        ui.unclip();
    }

    private static List<String> description(Ui ui, CardsSection section, Card card, float width) {
        String key = section.cardKey(card, "desc");
        return Language.getInstance().has(key) ? ui.wrap(tr(key), width, 11.5f, Ui.REGULAR, 2) : List.of();
    }

    /** All cards are as tall as the one with the longest description. */
    private static int cardHeight(Ui ui, CardsSection section, List<Card> shown, float textWidth) {
        int lines = 1;
        for (Card card : shown) {
            lines = Math.max(lines, description(ui, section, card, textWidth).size());
        }
        return 1 + ART_H + 10 + 17 + 4 + lines * 16 + 4 + 13 + 12 + 1;
    }

    private static int art(Ui ui, Card card) {
        if (card.art().filter(art -> art.length() == 7).isPresent()) {
            try {
                return OPAQUE | Integer.parseInt(card.art().get().substring(1), HEX);
            } catch (NumberFormatException e) {
                return ui.c("slot.bg");
            }
        }
        return ui.c("slot.bg");
    }

    private static String word(CardsSection section, State state) {
        return switch (state) {
            case DONE -> own(section.title() + ".done", "rysquests.cards.done");
            case OPEN -> own(section.title() + ".open", "rysquests.cards.open");
            case LOCKED -> own(section.title() + ".locked", "rysquests.cards.locked");
        };
    }

    private static void renderCard(Ui ui, CardsSection section, Card card, boolean selected, float x, float y, float w, float cardH) {
        State state = state(section, card);
        ui.box(x, y, w, cardH, 12, ui.c("card.bg"), ui.c("panel.border"));
        ui.fillCorners(x + 1, y + 1, w - 2, ART_H, 11, art(ui, card), true, true, false, false);
        if (selected) {
            ui.strokeBold(x, y, w, cardH, 12, ui.c("accent"));
        }
        ui.cell(icon(card), x + w / 2f - 24, y + 1 + ART_H / 2f - 24, 48, 48, 10, 26, ui.c("slot.bg"), ui.c("card.border"));
        int tagBg = Draw.alpha(ui.c("slot.bg"), 0.8f);
        card.group().ifPresent(group -> {
            String label = tr(section.groupTitle(group));
            ui.pill(label, x + 11, y + 9 + 9, 10.5f, Ui.REGULAR, 7, 18, 5, tagBg, ui.c("button.fg"));
        });
        String word = word(section, state);
        float wordW = ui.width(word, 10.5f, Ui.REGULAR) + 14;
        ui.pill(word, x + w - 11 - wordW, y + 9 + 9, 10.5f, Ui.REGULAR, 7, 18, 5, tagBg, ui.c(state == State.DONE ? "state.ok" : state == State.OPEN ? "accent" : "text.muted"));
        float left = x + 13;
        float inner = w - 26;
        float cursor = y + 1 + ART_H + 10;
        ui.labelFitTip(tr(section.cardKey(card, "name")), left, cursor + 8.5f, inner, 13.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        cursor += 17 + 4;
        List<String> description = description(ui, section, card, inner);
        ui.lines(description, left, cursor + description.size() * 8f, 16, 11.5f, Ui.REGULAR, ui.c("button.fg"));
        cursor += Math.max(1, description.size()) * 16 + 4;
        String metaKey = section.cardKey(card, "meta");
        String meta = state == State.LOCKED && card.stageTitle().isPresent() ? text("rysquests.guides.locked_until", tr(card.stageTitle().get())) : Language.getInstance().has(metaKey) ? tr(metaKey) : "";
        ui.labelFit(meta, left, cursor + 6.5f, inner, 10.5f, Ui.REGULAR, ui.c("text.muted"));
        if (state == State.LOCKED) {
            ui.veil(x, y, w, cardH, 12, ui.c("panel.bg"), 0.45f);
        }
        ui.hit(x, y, w, cardH, () -> BookState.CARD_PICKS.put(section.id(), card.id()));
    }

    // --- the selected card ---

    private void renderPanel(Ui ui, CardsSection section, @Nullable Card card, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, true, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        if (card == null) {
            return;
        }
        float left = x + 17;
        float inner = w - 33;
        float top = y + 16;
        State state = state(section, card);
        ui.cell(icon(card), left, top, 48, 48, 10, 26, ui.c("slot.bg"), ui.c("card.border"));
        ui.labelFitTip(tr(section.cardKey(card, "name")), left + 60, top + 6 + 11, inner - 60, 17, Ui.DISPLAY, ui.c("text.primary"));
        String group = card.group().map(section::groupTitle).map(CardsView::tr).orElse("");
        String status = state == State.LOCKED && card.stageTitle().isPresent() ? text("rysquests.guides.locked_until", tr(card.stageTitle().get())) : word(section, state);
        ui.labelFit(group.isEmpty() ? status : group + " · " + status, left + 60, top + 6 + 22 + 7, inner - 60, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        float buttonsY = renderButtons(ui, card, left, y + h - 16 - 34, inner);
        float listTop = top + 48 + 12;
        float listH = buttonsY - 12 - listTop;
        int offset = panelScroll.offset();
        float cursor = listTop - offset;
        ui.clip(Math.round(left - 2), Math.round(listTop), Math.round(inner + 4), Math.round(listH));
        String textKey = section.cardKey(card, "text");
        if (Language.getInstance().has(textKey)) {
            cursor = TextBlock.render(ui, tr(textKey), left, cursor, inner, new TextBlock.Look(13, 19.5f, ui.readingFg()), Recipes::showRecipes) + 12;
        }
        if (!card.needs().isEmpty()) {
            ui.caps(own(section.cardKey(card, "needs_title"), "rysquests.cards.needs"), left, cursor + 7, ui.c("text.dim"));
            cursor += 14 + 5;
            for (CardsSection.Need need : card.needs()) {
                renderNeed(ui, need, left, cursor, inner);
                cursor += ROW_H + 5;
            }
            cursor += 7;
        }
        if (!card.gives().isEmpty()) {
            ui.caps(own(section.cardKey(card, "gives_title"), "rysquests.cards.gives"), left, cursor + 7, ui.c("text.dim"));
            cursor += 14 + 5;
            for (var item : card.gives()) {
                renderGift(ui, TaskText.stackOf(item), left, cursor, inner);
                cursor += ROW_H + 5;
            }
            cursor += 7;
        }
        String tipKey = section.cardKey(card, "tip");
        if (Language.getInstance().has(tipKey)) {
            cursor = renderTip(ui, tr(tipKey), left, cursor, inner) + 12;
        }
        ui.unclip();
        panelScroll.content(Math.round(cursor + offset - listTop), Math.round(listH));
    }

    /** @return the top of the buttons, or of where they would be */
    private static float renderButtons(Ui ui, Card card, float x, float y, float w) {
        Quest quest = card.quest().map(id -> ClientBook.current().quests().get(id)).orElse(null);
        boolean page = card.page().isPresent();
        if (!page && quest == null) {
            return y + 34 + 12;
        }
        float buttonW = page && quest != null ? (w - 6) / 2f : w;
        float pen = x;
        if (page) {
            ui.button(tr("rysquests.guides.open_page"), pen, y, buttonW, 34, 9, 12, Ui.SEMIBOLD, 8, Ui.Style.ACCENT, () -> BookState.openRef(card.page().get()));
            pen += buttonW + 6;
        }
        if (quest != null) {
            ui.button(tr("rysquests.guides.to_quest"), pen, y, buttonW, 34, 9, 12, Ui.REGULAR, 8, Ui.Style.PLAIN, () -> BookState.select(quest));
        }
        return y;
    }

    private static void renderNeed(Ui ui, CardsSection.Need need, float x, float y, float w) {
        ItemStack stack = TaskText.stackOf(need.item());
        int held = RecipePreview.held(stack);
        boolean enough = held >= need.count();
        String have = Numbers.compact(held) + " / " + Numbers.compact(need.count()) + (enough ? " ✓" : "");
        renderItemRow(ui, stack, have, ui.c(enough ? "state.ok" : "accent"), x, y, w);
    }

    private static void renderGift(Ui ui, ItemStack stack, float x, float y, float w) {
        renderItemRow(ui, stack, "", 0, x, y, w);
    }

    private static void renderItemRow(Ui ui, ItemStack stack, String right, int color, float x, float y, float w) {
        boolean over = ui.over(x, y, w, ROW_H);
        ui.box(x, y, w, ROW_H, 8, over ? ui.c("slot.hover") : ui.c("status.bg"), ui.c("status.border"));
        float cy = y + ROW_H / 2f;
        ui.item(stack, x + 9, cy - 8, 16);
        float rightW = right.isEmpty() ? 0 : ui.width(right, 11, Ui.REGULAR) + 8;
        ui.labelFitTip(stack.getHoverName().getString(), x + 32, cy, w - 32 - 9 - rightW, 12, Ui.REGULAR, ui.nameFg());
        if (!right.isEmpty()) {
            ui.labelRight(right, x + w - 9, cy, 11, Ui.REGULAR, color);
        }
        ui.interactive(stack, x, y, w, ROW_H);
    }

    private static float renderTip(Ui ui, String tip, float x, float y, float w) {
        String label = tr("rysquests.cards.tip");
        float labelW = ui.width(label, 12, Ui.SEMIBOLD);
        List<String> lines = ui.wrap(tip, w - 22 - labelW - 8, 12, Ui.REGULAR, 8);
        float h = 20 + lines.size() * 17.4f;
        ui.box(x, y, w, h, 10, ui.mix("card.bg", "chip.mod.fg", 0.07f), ui.mix("card.bg", "chip.mod.fg", 0.2f));
        ui.label(label, x + 11, y + 10 + 8.7f, 12, Ui.SEMIBOLD, ui.c("chip.mod.fg"));
        ui.lines(lines, x + 11 + labelW + 8, y + 10 + lines.size() * 8.7f, 17.4f, 12, Ui.REGULAR, ui.readingFg());
        return y + h;
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        (mouseX >= panelLeft ? panelScroll : gridScroll).wheel(amount, WHEEL);
    }
}
