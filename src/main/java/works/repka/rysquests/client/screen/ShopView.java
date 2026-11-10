package works.repka.rysquests.client.screen;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.network.ShopTradePayload;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.task.ItemTask;
import works.repka.ryscore.ui.Numbers;

/** The shop, from {@code design/Shop.dc.html}. What is on screen is only a preview: the server decides every trade. */
final class ShopView implements BookView {
    private static final int PANEL_W = 320;
    private static final int PAD_X = 20;
    private static final int PAD_Y = 16;
    private static final int GAP = 12;
    private static final int HEADER_H = 38;
    private static final int COLUMNS = 4;
    private static final int CARD_H = 104;
    private static final int CARD_GAP = 10;
    private static final int HISTORY_ROW = 36;
    private static final int MAX_TIMES = 64;
    private static final int WHEEL = 48;
    private static final int LAST_LINES = 3;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM");

    private enum Tab {
        BUY, SELL, HISTORY
    }

    private enum State {
        OK, POOR, OUT, LOCKED
    }

    private final Scroll gridScroll = new Scroll();
    private Tab tab = Tab.BUY;
    private String category = "";
    private boolean onlyAvailable;
    @Nullable
    private ResourceLocation selectedId;
    private int times = 1;
    @Nullable
    private String status;
    private boolean statusBad;

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    void onResult(boolean ok, String reason) {
        status = ok ? tr("rysquests.shop.done") : tr(reason);
        statusBad = !ok;
        times = 1;
    }

    @Nullable
    String statusForTest() {
        return status;
    }

    // --- what an offer is ---

    private static ShopOffer.Entry firstItem(List<ShopOffer.Entry> entries) {
        return entries.stream().filter(entry -> !entry.isCurrency()).findFirst().orElse(entries.getFirst());
    }

    private static ShopOffer.Entry goods(ShopOffer offer) {
        return firstItem(offer.isSale() ? offer.cost() : offer.gives());
    }

    @Nullable
    private static ShopOffer.Entry price(ShopOffer offer) {
        List<ShopOffer.Entry> side = offer.isSale() ? offer.gives() : offer.cost();
        return side.stream().filter(ShopOffer.Entry::isCurrency).findFirst().orElse(null);
    }

    private static ItemStack stackOf(ShopOffer.Entry entry) {
        return entry.item().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
    }

    private static ItemStack icon(ShopOffer offer) {
        return offer.icon().map(TaskText::stackOf).orElseGet(() -> stackOf(goods(offer)));
    }

    private static String name(ShopOffer offer) {
        return offer.title().map(ShopView::tr).orElseGet(() -> stackOf(goods(offer)).getHoverName().getString());
    }

    private static boolean locked(ShopOffer offer) {
        boolean stage = offer.stage().filter(required -> !ClientProgress.hasStage(required)).isPresent();
        return stage || offer.quest().filter(quest -> !ClientProgress.get(quest).completed()).isPresent();
    }

    private static int left(ShopOffer offer) {
        return offer.limit().map(limit -> limit.count() - ClientShop.used(offer.id())).orElse(MAX_TIMES);
    }

    /** Whether the player can pay for a number of trades. */
    private static boolean canPay(ShopOffer offer, int count, boolean treasury) {
        for (ShopOffer.Entry cost : offer.cost()) {
            long needed = (long) cost.amount() * count;
            long have = cost.isCurrency() ? treasury ? ClientShop.treasury(cost.id()) : ClientShop.balance(cost.id()) : RecipePreview.held(stackOf(cost));
            if (have < needed) {
                return false;
            }
        }
        return true;
    }

    private static State state(ShopOffer offer) {
        if (locked(offer)) {
            return State.LOCKED;
        }
        if (left(offer) <= 0) {
            return State.OUT;
        }
        return canPay(offer, 1, false) ? State.OK : State.POOR;
    }

    private static String limitLine(ShopOffer offer, State state) {
        if (state == State.LOCKED) {
            return offer.stageTitle().map(stage -> text("rysquests.guides.locked_until", tr(stage))).orElseGet(() -> offer.quest()
                    .map(id -> ClientBook.current().quests().get(id))
                    .map(quest -> text("rysquests.shop.after_quest", tr(quest.title())))
                    .orElseGet(() -> tr("rysquests.shop.closed")));
        }
        if (offer.limit().isEmpty()) {
            return tr("rysquests.shop.no_limit");
        }
        ShopOffer.Limit limit = offer.limit().get();
        String period = tr("rysquests.shop.limit." + limit.reset().getSerializedName());
        return text(limit.per() == ShopOffer.Per.TEAM ? "rysquests.shop.used_team" : "rysquests.shop.used", ClientShop.used(offer.id()), limit.count(), period);
    }

    private static String currencyName(String currency) {
        String key = "rysquests.currency." + currency;
        return Language.getInstance().has(key) ? tr(key) : currency;
    }

    private List<ShopOffer> shown() {
        List<ShopOffer> list = new ArrayList<>();
        for (ShopOffer offer : ClientBook.current().offers().values()) {
            boolean side = offer.isSale() == (tab == Tab.SELL);
            boolean inCategory = category.isEmpty() || offer.category().filter(category::equals).isPresent();
            if (side && inCategory && (!onlyAvailable || state(offer) == State.OK)) {
                list.add(offer);
            }
        }
        return list;
    }

    // --- drawing ---

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        int panelLeft = x + w - PANEL_W;
        List<ShopOffer> shown = tab == Tab.HISTORY ? List.of() : shown();
        ShopOffer selected = selected(shown);
        renderPanel(ui, selected, panelLeft, y, PANEL_W, h);
        float left = x + PAD_X;
        float inner = panelLeft - x - PAD_X * 2;
        float cursor = y + PAD_Y;
        renderHeader(ui, left, cursor, inner);
        cursor += HEADER_H + GAP;
        cursor = renderFilters(ui, left, cursor, inner) + GAP;
        float bottom = y + h - PAD_Y;
        if (tab == Tab.HISTORY) {
            renderHistory(ui, left, cursor, inner, bottom - cursor);
        } else {
            renderGrid(ui, shown, selected, left, cursor, inner, bottom - cursor);
        }
    }

    @Nullable
    private ShopOffer selected(List<ShopOffer> shown) {
        ShopOffer found = selectedId == null ? null : ClientBook.current().offers().get(selectedId);
        return found != null ? found : shown.isEmpty() ? null : shown.getFirst();
    }

    private static String mainCurrency() {
        for (ShopOffer offer : ClientBook.current().offers().values()) {
            ShopOffer.Entry price = price(offer);
            if (price != null) {
                return price.id();
            }
        }
        return "coin";
    }

    private static void renderHeader(Ui ui, float x, float y, float w) {
        ui.label(tr("rysquests.shop.title"), x, y + 12.5f, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelFit(tr("rysquests.shop.subtitle"), x, y + 25 + 7, w * 0.72f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        String currency = mainCurrency();
        String mine = Numbers.compact(ClientShop.balance(currency));
        String yours = tr("rysquests.shop.yours");
        float content = 18 + 10 + ui.width(mine, 18, Ui.DISPLAY) + 10 + ui.width(yours, 11, Ui.REGULAR);
        String treasury = Numbers.compact(ClientShop.treasury(currency));
        String treasuryLabel = tr("rysquests.shop.treasury");
        boolean team = ClientShop.inTeam();
        if (team) {
            content += 10 + 1 + 10 + ui.width(treasury, 15, Ui.DISPLAY) + 10 + ui.width(treasuryLabel, 11, Ui.REGULAR);
        }
        float boxW = content + 26;
        float boxX = x + w - boxW;
        float cy = y + HEADER_H / 2f;
        ui.box(boxX, cy - 17, boxW, 34, 10, ui.warmBorder(0.11f), ui.warmBorder(0.34f));
        float pen = boxX + 13;
        ui.circle(pen, cy - 9, 18, ui.c("accent"));
        pen += 18 + 10;
        ui.label(mine, pen, cy, 18, Ui.DISPLAY, ui.c("text.primary"));
        pen += ui.width(mine, 18, Ui.DISPLAY) + 10;
        ui.label(yours, pen, cy, 11, Ui.REGULAR, ui.c("button.fg"));
        pen += ui.width(yours, 11, Ui.REGULAR) + 10;
        if (team) {
            ui.rect(pen, cy - 9, 1, 18, ui.warmBorder(0.34f));
            pen += 11;
            ui.label(treasury, pen, cy, 15, Ui.DISPLAY, ui.c("button.fg"));
            pen += ui.width(treasury, 15, Ui.DISPLAY) + 10;
            ui.label(treasuryLabel, pen, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        }
    }

    /** @return the y below the filters */
    private float renderFilters(Ui ui, float x, float y, float w) {
        float segmentH = Chips.HEIGHT + 4;
        float pen = x;
        float segmentW = 8;
        for (Tab each : Tab.values()) {
            segmentW += ui.width(tr("rysquests.shop.tab." + each.name().toLowerCase(java.util.Locale.ROOT)), 12, Ui.REGULAR) + 28;
        }
        ui.box(x, y, segmentW - 4, segmentH, 9, ui.c("input.bg"), ui.c("panel.border"));
        pen += 3;
        for (Tab each : Tab.values()) {
            String label = tr("rysquests.shop.tab." + each.name().toLowerCase(java.util.Locale.ROOT));
            float tabW = ui.width(label, 12, Ui.REGULAR) + 24;
            if (each == tab) {
                ui.fill(pen, y + 3, tabW, segmentH - 6, 7, ui.c("button.bg"));
            }
            ui.label(label, pen + 12, y + segmentH / 2f, 12, Ui.REGULAR, ui.c(each == tab ? "text.primary" : "text.muted"));
            Tab target = each;
            ui.hit(pen, y + 3, tabW, segmentH - 6, () -> {
                tab = target;
                selectedId = null;
                times = 1;
                status = null;
                gridScroll.reset();
            });
            pen += tabW + 4;
        }
        if (tab == Tab.HISTORY) {
            return y + segmentH;
        }
        float chipsX = x + segmentW + 8;
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("", 0);
        ClientBook.current().offers().values().stream().filter(offer -> offer.isSale() == (tab == Tab.SELL)).forEach(offer -> {
            counts.merge("", 1, Integer::sum);
            offer.category().ifPresent(found -> counts.merge(found, 1, Integer::sum));
        });
        List<Chips.Chip> chips = new ArrayList<>();
        counts.keySet().forEach(key -> chips.add(new Chips.Chip(key, key.isEmpty() ? tr("rysquests.guides.category_all") : tr(key), -1)));
        String only = tr("rysquests.shop.only_available");
        float onlyW = ui.width(only, 12, Ui.REGULAR) + 24;
        float[] end = Chips.render(ui, chips, category, key -> category = key, chipsX, y + 2, w - (chipsX - x) - onlyW - 6);
        float onlyX = x + w - onlyW;
        ui.box(onlyX, y + 2, onlyW, Chips.HEIGHT, Chips.HEIGHT / 2f, onlyAvailable ? ui.c("button.bg") : 0, onlyAvailable ? ui.c("button.border") : ui.c("panel.border"));
        ui.label(only, onlyX + 12, y + 2 + Chips.HEIGHT / 2f, 12, Ui.REGULAR, ui.c(onlyAvailable ? "text.primary" : "text.muted"));
        ui.hit(onlyX, y + 2, onlyW, Chips.HEIGHT, () -> onlyAvailable = !onlyAvailable);
        return Math.max(y + segmentH, end[1] + Chips.HEIGHT);
    }

    private void renderGrid(Ui ui, List<ShopOffer> shown, @Nullable ShopOffer selected, float x, float y, float w, float h) {
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

    private void renderCard(Ui ui, ShopOffer offer, boolean selected, float x, float y, float w) {
        State state = state(offer);
        ui.box(x, y, w, CARD_H, 12, ui.c("card.bg"), ui.c("panel.border"));
        if (selected) {
            ui.strokeBold(x, y, w, CARD_H, 12, ui.c("accent"));
        }
        float left = x + 13;
        float inner = w - 26;
        ShopOffer.Entry goods = goods(offer);
        ui.cell(icon(offer), left, y + 13, 40, 40, 9, 24, ui.c("slot.bg"), ui.c("card.border"));
        ui.count(goods.amount(), left + 37, y + 13 + 38);
        float textX = left + 50;
        ui.labelFitTip(name(offer), textX, y + 13 + 11, inner - 50, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.labelFitTip(limitLine(offer, state), textX, y + 13 + 28, inner - 50, 11, Ui.REGULAR, ui.c(state == State.OUT ? "accent" : state == State.LOCKED ? "text.dim" : "text.muted"));
        float rowY = y + 13 + 40 + 8;
        float cy = rowY + 14;
        ShopOffer.Entry price = price(offer);
        float pen = left;
        if (price != null) {
            ui.circle(pen, cy - 6, 12, ui.c("accent"));
            pen += 18;
            ui.label(Numbers.compact(price.amount()), pen, cy, 15, Ui.DISPLAY, ui.c(state == State.POOR ? "state.bad" : "text.primary"));
        }
        renderCardButton(ui, offer, state, left + inner, rowY);
        if (state == State.LOCKED || state == State.OUT) {
            ui.veil(x, y, w, CARD_H, 12, ui.c("panel.bg"), state == State.OUT ? 0.4f : 0.5f);
        }
        ui.hit(x, y, w, CARD_H, () -> {
            selectedId = offer.id();
            times = 1;
            status = null;
        });
        if (state == State.OK) {
            String label = tr(offer.isSale() ? "rysquests.shop.sell" : "rysquests.shop.buy");
            float buttonW = ui.buttonWidth(label, 11.5f, Ui.SEMIBOLD, 12);
            ui.hit(left + inner - buttonW, rowY, buttonW, 28, () -> {
                selectedId = offer.id();
                times = 1;
                trade(offer, false);
            });
        }
    }

    private static void renderCardButton(Ui ui, ShopOffer offer, State state, float right, float y) {
        String label = tr(switch (state) {
            case OK -> offer.isSale() ? "rysquests.shop.sell" : "rysquests.shop.buy";
            case POOR -> "rysquests.shop.not_enough";
            case OUT -> offer.limit().filter(limit -> limit.reset() == ShopOffer.Reset.NEVER).isPresent() ? "rysquests.shop.sold_out" : "rysquests.shop.later";
            case LOCKED -> "rysquests.shop.closed_button";
        });
        float w = ui.buttonWidth(label, 11.5f, Ui.SEMIBOLD, 12);
        float x = right - w;
        if (state == State.OK) {
            ui.box(x, y, w, 28, 8, ui.over(x, y, w, 28) ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
            ui.labelCentered(label, x + w / 2f, y + 14, 11.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        } else {
            ui.stroke(x, y, w, 28, 8, ui.c("panel.border"));
            ui.labelCentered(label, x + w / 2f, y + 14, 11.5f, Ui.SEMIBOLD, ui.c(state == State.POOR ? "text.muted" : "text.dim"));
        }
    }

    private void trade(ShopOffer offer, boolean treasury) {
        status = null;
        PacketDistributor.sendToServer(new ShopTradePayload(offer.id(), times, treasury));
    }

    private void renderHistory(Ui ui, float x, float y, float w, float h) {
        List<ProgressStore.ShopLine> lines = ClientShop.history();
        if (lines.isEmpty()) {
            ui.label(tr("rysquests.shop.history_empty"), x, y + 12, 12, Ui.REGULAR, ui.c("text.dim"));
            return;
        }
        gridScroll.content(lines.size() * HISTORY_ROW, Math.round(h));
        int offset = gridScroll.offset();
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        for (int i = 0; i < lines.size(); i++) {
            float rowY = y + i * HISTORY_ROW - offset;
            ProgressStore.ShopLine line = lines.get(i);
            ui.box(x, rowY, w, HISTORY_ROW - 5, 8, ui.c("status.bg"), ui.c("status.border"));
            float cy = rowY + (HISTORY_ROW - 5) / 2f;
            ui.labelFit(lineText(line), x + 12, cy, w - 110, 12, Ui.REGULAR, ui.c("button.fg"));
            ui.labelRight(when(line.time()), x + w - 12, cy, 11, Ui.REGULAR, ui.c("text.dim"));
        }
        ui.unclip();
    }

    private static String lineText(ProgressStore.ShopLine line) {
        String amount = (line.delta() > 0 ? "+" : "−") + Numbers.compact(Math.abs(line.delta()));
        if (ProgressStore.ShopLine.EXCHANGE.equals(line.ref())) {
            return amount + " · " + tr("rysquests.shop.exchange");
        }
        if (ProgressStore.ShopLine.TRANSFER.equals(line.ref())) {
            return amount + " · " + tr(line.delta() > 0 ? "rysquests.shop.transfer_in" : "rysquests.shop.transfer_out");
        }
        ResourceLocation ref = ResourceLocation.tryParse(line.ref());
        ShopOffer offer = ref == null ? null : ClientBook.current().offers().get(ref);
        if (offer != null) {
            return amount + " · " + name(offer) + " ×" + line.times();
        }
        Quest quest = ref == null ? null : ClientBook.current().quests().get(ref);
        return amount + " · " + (quest != null ? text("rysquests.shop.quest_reward", tr(quest.title())) : tr("rysquests.shop.reward"));
    }

    private static String when(long time) {
        var moment = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        LocalDate day = moment.toLocalDate();
        LocalDate today = LocalDate.now();
        if (day.equals(today)) {
            return moment.format(TIME);
        }
        return day.equals(today.minusDays(1)) ? tr("rysquests.shop.yesterday") : moment.format(DATE);
    }

    // --- the selected offer ---

    private void renderPanel(Ui ui, @Nullable ShopOffer offer, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, true, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float left = x + 17;
        float inner = w - 33;
        float cursor = y + 16;
        float historyTop = y + h - 16 - lastHeight();
        renderLast(ui, left, historyTop, inner);
        if (offer == null) {
            return;
        }
        ui.cell(icon(offer), left, cursor, 48, 48, 11, 28, ui.c("slot.bg"), ui.c("card.border"));
        ShopOffer.Entry goods = goods(offer);
        ui.labelFitTip(name(offer) + " ×" + goods.amount(), left + 58, cursor + 6 + 11, inner - 58, 17, Ui.DISPLAY, ui.c("text.primary"));
        State state = state(offer);
        String category = offer.category().map(ShopView::tr).orElse("");
        String line = limitLine(offer, state);
        ui.labelFit(category.isEmpty() ? line : category + " · " + line, left + 58, cursor + 6 + 22 + 7, inner - 58, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        cursor += 48 + 12;
        cursor = renderForQuest(ui, offer, left, cursor, inner);
        cursor = renderStepper(ui, offer, left, cursor, inner) + 12;
        cursor = renderSummary(ui, offer, left, cursor, inner) + 12;
        renderActions(ui, offer, state, left, cursor, inner);
    }

    /** A hint when an open quest asks for what this offer gives. */
    private static float renderForQuest(Ui ui, ShopOffer offer, float x, float y, float w) {
        if (offer.isSale() || goods(offer).item().isEmpty()) {
            return y;
        }
        ResourceLocation item = goods(offer).item().get();
        ItemStack stack = TaskText.stackOf(item);
        for (ResourceLocation id : ClientBook.current().byItem().getOrDefault(item, List.of())) {
            Quest quest = ClientBook.current().quests().get(id);
            if (quest == null) {
                continue;
            }
            QuestStatus status = BookState.status(quest);
            if (status != QuestStatus.AVAILABLE && status != QuestStatus.IN_PROGRESS) {
                continue;
            }
            QuestProgress own = ClientProgress.get(id);
            for (int i = 0; i < quest.tasks().size(); i++) {
                if (quest.tasks().get(i) instanceof ItemTask task && task.itemRefs().stream().anyMatch(ref -> !ref.tag() && ref.id().equals(item))) {
                    int missing = task.target() - own.task(i) - RecipePreview.held(stack);
                    if (missing > 0) {
                        String caption = tr("rysquests.shop.for_quest");
                        String need = Component.translatable("rysquests.shop.missing", stack.getHoverName().getString(), missing).getString();
                        ui.box(x, y, w, 36, 10, ui.warmBorder(0.11f), ui.warmBorder(0.34f));
                        float captionW = ui.width(caption, 12, Ui.SEMIBOLD);
                        ui.label(caption, x + 12, y + 18, 12, Ui.SEMIBOLD, ui.c("accent"));
                        ui.labelFitTip(need, x + 12 + captionW + 8, y + 18, w - 32 - captionW, 12, Ui.REGULAR, ui.c("text.body"));
                        return y + 36 + 12;
                    }
                }
            }
        }
        return y;
    }

    private float renderStepper(Ui ui, ShopOffer offer, float x, float y, float w) {
        ui.label(tr("rysquests.shop.quantity"), x, y + 18, 12, Ui.REGULAR, ui.c("text.muted"));
        float boxW = 34 + 44 + 34;
        float boxX = x + w - boxW;
        int max = Math.max(1, Math.min(MAX_TIMES, left(offer)));
        times = Math.clamp(times, 1, max);
        ui.box(boxX, y, boxW, 36, 9, 0, ui.c("button.border"));
        stepButton(ui, "−", boxX, y, times > 1, () -> times--);
        ui.labelCentered(String.valueOf(times), boxX + 34 + 22, y + 18, 14, Ui.SEMIBOLD, ui.c("text.primary"));
        stepButton(ui, "+", boxX + 34 + 44, y, times < max, () -> times++);
        return y + 36;
    }

    private static void stepButton(Ui ui, String label, float x, float y, boolean enabled, Runnable action) {
        ui.fill(x + 1, y + 1, 32, 34, 8, ui.c(enabled && ui.over(x, y, 34, 36) ? "slot.hover" : "button.bg"));
        ui.labelCentered(label, x + 17, y + 18, 15, Ui.REGULAR, ui.c(enabled ? "text.primary" : "text.dim"));
        if (enabled) {
            ui.hit(x, y, 34, 36, action);
        }
    }

    private float renderSummary(Ui ui, ShopOffer offer, float x, float y, float w) {
        float h = 24 + 3 * 21;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("status.bg"));
        float cy = y + 12 + 10;
        ShopOffer.Entry price = price(offer);
        long total = price == null ? 0 : (long) price.amount() * times;
        String currency = price == null ? "" : currencyName(price.id());
        long balance = price == null ? 0 : ClientShop.balance(price.id());
        summaryRow(ui, tr(offer.isSale() ? "rysquests.shop.you_get" : "rysquests.shop.price"), Numbers.compact(total) + " " + currency, x, cy, w);
        cy += 21;
        summaryRow(ui, tr(offer.isSale() ? "rysquests.shop.will_be" : "rysquests.shop.remaining"), Numbers.compact(offer.isSale() ? balance + total : balance - total) + " " + currency, x, cy, w);
        cy += 21;
        ShopOffer.Entry goods = goods(offer);
        String pieces = text("rysquests.shop.pieces", Numbers.compact((long) goods.amount() * times));
        summaryRow(ui, tr(offer.isSale() ? "rysquests.shop.you_give" : "rysquests.shop.you_receive"), pieces, x, cy, w);
        return y + h;
    }

    private static void summaryRow(Ui ui, String label, String value, float x, float cy, float w) {
        ui.label(label, x + 13, cy, 12.5f, Ui.REGULAR, ui.c("button.fg"));
        ui.labelRight(value, x + w - 13, cy, 12.5f, Ui.REGULAR, ui.c("text.primary"));
    }

    private void renderActions(Ui ui, ShopOffer offer, State state, float x, float y, float w) {
        ShopOffer.Entry price = price(offer);
        long total = price == null ? 0 : (long) price.amount() * times;
        boolean can = state == State.OK && canPay(offer, times, false);
        String label = can ? text(offer.isSale() ? "rysquests.shop.sell_for" : "rysquests.shop.buy_for", Numbers.compact(total)) : tr(state == State.LOCKED ? "rysquests.shop.closed_button" : state == State.OUT ? "rysquests.shop.sold_out" : "rysquests.shop.not_enough");
        if (can) {
            ui.button(label, x, y, w, 44, 11, 14, Ui.SEMIBOLD, 12, Ui.Style.ACCENT, () -> trade(offer, false));
        } else {
            ui.stroke(x, y, w, 44, 11, ui.c("panel.border"));
            ui.labelCentered(label, x + w / 2f, y + 22, 14, Ui.SEMIBOLD, ui.c("text.muted"));
        }
        float rowY = y + 44 + 8;
        boolean treasury = ClientShop.inTeam() && !offer.isSale() && state != State.LOCKED && state != State.OUT && canPay(offer, times, true);
        boolean hasRecipe = !offer.isSale() && goods(offer).item().isPresent();
        float buttonW = treasury && hasRecipe ? (w - 6) / 2f : w;
        float pen = x;
        if (treasury) {
            ui.button(tr("rysquests.shop.from_treasury"), pen, rowY, buttonW, 34, 9, 12, Ui.REGULAR, 6, Ui.Style.PLAIN, () -> trade(offer, true));
            pen += buttonW + 6;
        }
        if (hasRecipe) {
            ItemStack goods = stackOf(goods(offer));
            ui.button(tr("rysquests.shop.recipe_instead"), pen, rowY, buttonW, 34, 9, 12, Ui.REGULAR, 6, Ui.Style.PLAIN, () -> Recipes.showRecipes(goods));
        }
        if (status != null) {
            ui.labelFitTip(status, x, rowY + 34 + 16, w, 12, Ui.REGULAR, ui.c(statusBad ? "state.bad" : "state.ok"));
        }
    }

    private static float lastHeight() {
        return Math.min(LAST_LINES, ClientShop.history().size()) * 20 + 40;
    }

    private static void renderLast(Ui ui, float x, float y, float w) {
        List<ProgressStore.ShopLine> lines = ClientShop.history();
        if (lines.isEmpty()) {
            return;
        }
        ui.box(x, y, w, lastHeight(), 10, ui.c("status.bg"), ui.c("status.border"));
        ui.label(tr("rysquests.shop.last"), x + 13, y + 11 + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float cy = y + 11 + 14 + 5 + 8;
        for (int i = 0; i < Math.min(LAST_LINES, lines.size()); i++) {
            ProgressStore.ShopLine line = lines.get(i);
            String time = when(line.time());
            float timeW = ui.width(time, 12, Ui.REGULAR);
            ui.labelFit(lineText(line), x + 13, cy, w - 26 - timeW - 8, 12, Ui.REGULAR, ui.c("button.fg"));
            ui.labelRight(time, x + w - 13, cy, 12, Ui.REGULAR, ui.c("text.dim"));
            cy += 20;
        }
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        gridScroll.wheel(amount, WHEEL);
    }

    // --- for the autotest ---

    void selectForTest(ResourceLocation offer) {
        selectedId = offer;
        times = 1;
        tab = Minecraft.getInstance() != null && ClientBook.current().offers().get(offer).isSale() ? Tab.SELL : Tab.BUY;
    }

    void showHistoryForTest() {
        tab = Tab.HISTORY;
    }

    void tradeForTest(ResourceLocation offer, boolean treasury) {
        times = 1;
        trade(ClientBook.current().offers().get(offer), treasury);
    }
}
