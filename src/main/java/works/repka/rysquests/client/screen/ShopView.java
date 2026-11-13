package works.repka.rysquests.client.screen;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.network.ShopTradePayload;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.task.ItemTask;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.Numbers;

/**
 * The shop (plan, section 3, pictures 25 to 27b, 46c). One line on top: "Купить | Продать | История" on the left, the filters and the
 * coins on the right. Under it a grid of four columns (three in the compact layout) and the detail on the right, always open: choosing
 * an offer rebuilds nothing. Three filters stay whatever the book holds ("Все", "Для квестов" and the current epoch's section); every
 * other section lives in the "Раздел" list. What is on screen is only a preview: the server decides every trade.
 */
final class ShopView implements BookView {
    private static final int PAD_X = 16;
    private static final int PAD_Y = 12;
    private static final int TOP_H = 34;
    private static final int TOP_GAP = 14;
    private static final int GAP = 8;
    private static final int COLUMNS = 4;
    private static final int COLUMNS_COMPACT = 3;
    private static final int CARD_H = 110;
    private static final int CARD_GAP = 10;
    private static final int PANEL_COMPACT = 300;
    private static final int TABLE_HEAD = 32;
    private static final int TABLE_ROW = 38;
    private static final int MAX_TIMES = 64;
    private static final int WHEEL = 48;
    private static final int LAST_LINES = 3;
    private static final int SECTION_MAX_UNITS = 130;
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM");

    private enum Tab {
        BUY, SELL, HISTORY
    }

    private enum State {
        OK, POOR, OUT, LOCKED
    }

    /** The filters of buying: the three that always stand, and a section picked in the list. */
    private enum Filter {
        ALL, FOR_QUESTS, EPOCH, SECTION
    }

    /** An open quest that lacks what an offer gives. */
    private record Need(Quest quest, int missing) {
    }

    private final BookScreen screen;
    private final DetailPanel panel = new DetailPanel();
    private final Scroll gridScroll = new Scroll();
    private final Kit.Dropdown dropdown = new Kit.Dropdown();
    private Tab tab = Tab.BUY;
    private Filter filter = Filter.ALL;
    private String section = "";
    private boolean onlyAvailable;
    private int historyFilter;
    private boolean fromTreasury;
    @Nullable
    private ResourceLocation selectedId;
    private int times = 1;
    @Nullable
    private String status;
    private boolean statusBad;

    ShopView(BookScreen screen) {
        this.screen = screen;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    void onResult(boolean ok, String reason) {
        status = ok ? null : tr(reason);
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

    private static long priceOf(ShopOffer offer) {
        ShopOffer.Entry price = price(offer);
        return price == null ? 0 : price.amount();
    }

    private static ItemStack stackOf(ShopOffer.Entry entry) {
        return entry.item().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
    }

    private static ItemStack icon(ShopOffer offer) {
        return offer.icon().map(TaskText::stackOf).orElseGet(() -> stackOf(goods(offer)));
    }

    static String name(ShopOffer offer) {
        return offer.title().map(ShopView::tr).orElseGet(() -> stackOf(goods(offer)).getHoverName().getString());
    }

    private static boolean locked(ShopOffer offer) {
        boolean stage = offer.stage().filter(required -> !ClientProgress.hasStage(required)).isPresent();
        return stage || offer.quest().filter(quest -> !ClientProgress.get(quest).everDone()).isPresent();
    }

    private static int left(ShopOffer offer) {
        return offer.limit().map(limit -> limit.count() - ClientShop.used(offer.id())).orElse(MAX_TIMES);
    }

    private static long wallet(String currency, boolean treasury) {
        return treasury ? ClientShop.treasury(currency) : ClientShop.balance(currency);
    }

    /** Whether the player can pay for a number of trades. */
    private static boolean canPay(ShopOffer offer, int count, boolean treasury) {
        for (ShopOffer.Entry cost : offer.cost()) {
            long needed = (long) cost.amount() * count;
            long have = cost.isCurrency() ? wallet(cost.id(), treasury) : paying(stackOf(cost));
            if (have < needed) {
                return false;
            }
        }
        return true;
    }

    private State state(ShopOffer offer) {
        if (locked(offer)) {
            return State.LOCKED;
        }
        if (left(offer) <= 0) {
            return State.OUT;
        }
        return canPay(offer, 1, payFromTreasury(offer)) ? State.OK : State.POOR;
    }

    private boolean payFromTreasury(ShopOffer offer) {
        return fromTreasury && ClientShop.inTeam() && !offer.isSale();
    }

    private static String lockedLine(ShopOffer offer) {
        return offer.stageTitle().map(stage -> text("rysquests.guides.locked_until", tr(stage))).orElseGet(() -> offer.quest()
                .map(id -> ClientBook.current().quests().get(id))
                .map(quest -> text("rysquests.shop.after_quest", tr(quest.title())))
                .orElseGet(() -> tr("rysquests.shop.closed")));
    }

    /** The limit on a card, short: "1 из 3 сегодня". Null without a limit: the name then takes two lines. */
    @Nullable
    private static String limitShort(ShopOffer offer) {
        return offer.limit().map(limit -> text(limit.per() == ShopOffer.Per.TEAM ? "rysquests.shop.limit_short.team" : "rysquests.shop.limit_short." + limit.reset().getSerializedName(),
                ClientShop.used(offer.id()), limit.count())).orElse(null);
    }

    /** The limit in the detail, with when it renews: "лимит 3 в день, обновится в полночь сервера", or "без лимита". */
    private static String limitLong(ShopOffer offer) {
        if (offer.limit().isEmpty()) {
            return tr("rysquests.shop.no_limit");
        }
        ShopOffer.Limit limit = offer.limit().get();
        String line = text("rysquests.shop.limit_long." + limit.reset().getSerializedName(), limit.count());
        return limit.per() == ShopOffer.Per.TEAM ? line + tr("rysquests.shop.limit_long.team") : line;
    }

    private static String currencyName(String currency) {
        String key = "rysquests.currency." + currency;
        return Language.getInstance().has(key) ? tr(key) : currency;
    }

    /** An amount of money in words: "2 монеты", "12 монет", another currency by its name. */
    static String money(String currency, long amount) {
        return "coin".equals(currency) ? Plural.full("rysquests.plural.coin", amount) : Sidebar.full(amount) + " " + currencyName(currency);
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

    /** The first open quest that still lacks what an offer gives, compared on the client (plan, section 3). */
    @Nullable
    private static Need need(ShopOffer offer) {
        if (offer.isSale() || goods(offer).item().isEmpty()) {
            return null;
        }
        ResourceLocation item = goods(offer).item().get();
        ItemStack stack = TaskText.stackOf(item);
        for (ResourceLocation id : ClientBook.current().byItem().getOrDefault(item, List.of())) {
            Quest quest = ClientBook.current().quests().get(id);
            // Only quests of the story: the hidden quests that count achievement levels ask for items too.
            if (quest == null || !BookData.inStory(quest) || quest.options().hidden()) {
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
                        return new Need(quest, missing);
                    }
                }
            }
        }
        return null;
    }

    /** A quest that asks for what a sale takes: an open one warns, a finished one only says so. */
    @Nullable
    private static Quest usedIn(ShopOffer offer, boolean open) {
        ResourceLocation item = goods(offer).item().orElse(null);
        if (item == null) {
            return null;
        }
        for (ResourceLocation id : ClientBook.current().byItem().getOrDefault(item, List.of())) {
            Quest quest = ClientBook.current().quests().get(id);
            if (quest == null || !BookData.inStory(quest)) {
                continue;
            }
            QuestStatus status = BookState.status(quest);
            boolean isOpen = status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS || status == QuestStatus.LOCKED;
            if (isOpen == open) {
                return quest;
            }
        }
        return null;
    }

    // --- sections ---

    /** The section of the current epoch, by its name ("II Индустрия"), or empty when the shop has none such. */
    private static String epochSection(List<String> sections) {
        Sidebar.Model model = Sidebar.model();
        for (Sidebar.Group group : model.groups()) {
            if (!group.current()) {
                continue;
            }
            String full = Ui.roman(group.number()) + " " + group.title();
            for (String key : sections) {
                String name = tr(key);
                if (name.equals(full) || name.equals(group.title())) {
                    return key;
                }
            }
        }
        return "";
    }

    /** The sections of one side of the shop in the order the book lists their offers, with how many each holds. */
    private static Map<String, Integer> sections(boolean sale) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ShopOffer offer : ClientBook.current().offers().values()) {
            if (offer.isSale() == sale) {
                offer.category().ifPresent(key -> out.merge(key, 1, Integer::sum));
            }
        }
        return out;
    }

    private List<ShopOffer> shown() {
        boolean sale = tab == Tab.SELL;
        String epoch = epochSection(List.copyOf(sections(sale).keySet()));
        List<ShopOffer> list = new ArrayList<>();
        for (ShopOffer offer : ClientBook.current().offers().values()) {
            if (offer.isSale() != sale) {
                continue;
            }
            Filter used = sale && (filter == Filter.FOR_QUESTS || filter == Filter.EPOCH) ? Filter.ALL : filter;
            boolean match = switch (used) {
                case ALL -> true;
                case FOR_QUESTS -> need(offer) != null;
                case EPOCH -> !epoch.isEmpty() && offer.category().filter(epoch::equals).isPresent();
                case SECTION -> offer.category().filter(section::equals).isPresent();
            };
            if (match && (sale || !onlyAvailable || !locked(offer))) {
                list.add(offer);
            }
        }
        if (!sale) {
            // What an open quest lacks comes first; the sort is stable, so the rest keeps the book's order.
            list.sort(Comparator.comparing(offer -> need(offer) == null));
        }
        return list;
    }

    // --- drawing ---

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        boolean compact = screen.compact();
        int panelW = compact ? PANEL_COMPACT : DetailPanel.WIDTH;
        int panelLeft = x + w - panelW;
        List<ShopOffer> shown = tab == Tab.HISTORY ? List.of() : shown();
        ShopOffer selected = selected(shown);
        float left = x + PAD_X;
        float inner = panelLeft - x - PAD_X * 2;
        float top = y + PAD_Y;
        float bodyTop = top + TOP_H + TOP_GAP;
        float bottom = y + h - PAD_Y;
        if (tab == Tab.HISTORY) {
            renderHistory(ui, left, bodyTop, inner, bottom - bodyTop);
        } else if (shown.isEmpty()) {
            renderEmpty(ui, left, bodyTop, inner, bottom - bodyTop);
        } else {
            renderGrid(ui, shown, selected, left, bodyTop, inner, bottom - bodyTop, compact ? COLUMNS_COMPACT : COLUMNS);
        }
        renderPanel(ui, selected, panelLeft, y, panelW, h);
        renderTop(ui, left, top, inner, compact);
    }

    @Nullable
    private ShopOffer selected(List<ShopOffer> shown) {
        ShopOffer found = selectedId == null ? null : ClientBook.current().offers().get(selectedId);
        if (found != null && shown.contains(found)) {
            return found;
        }
        return shown.isEmpty() ? null : shown.getFirst();
    }

    private void select(ShopOffer offer) {
        if (!offer.id().equals(selectedId)) {
            times = 1;
            status = null;
            panel.reset();
        }
        selectedId = offer.id();
    }

    private void openTab(Tab target) {
        tab = target;
        selectedId = null;
        times = 1;
        status = null;
        dropdown.close();
        gridScroll.reset();
        panel.reset();
        if (target == Tab.SELL && (filter == Filter.FOR_QUESTS || filter == Filter.EPOCH)) {
            filter = Filter.ALL;
        }
    }

    // --- the line on top ---

    private void renderTop(Ui ui, float x, float y, float w, boolean compact) {
        List<Segments.Item> tabs = List.of(Segments.item(tr("rysquests.shop.tab.buy")), Segments.item(tr("rysquests.shop.tab.sell")), Segments.item(tr("rysquests.shop.tab.history")));
        float pen = x + Segments.render(ui, x, y, tabs, tab.ordinal(), index -> openTab(Tab.values()[index]));
        float cy = y + TOP_H / 2f;
        String tip = tab == Tab.SELL ? tr("rysquests.shop.sell_tip") : tab == Tab.HISTORY ? tr("rysquests.shop.history_tip") : null;
        if (tip != null && !compact) {
            pen += GAP + 4;
            int ix = Math.round(pen / 2f);
            int iy = Math.round((cy - 10) / 2f);
            Draw.info(ui.g, ix, iy, ui.c("text.muted"));
            ui.tooltipIfOver(ix * 2f, iy * 2f, 20, 20, Component.literal(tip));
        }
        float right = x + w - renderWallet(ui, x + w, cy, compact) - GAP;
        if (tab == Tab.HISTORY) {
            if (historyHasTreasury()) {
                List<String> labels = List.of(tr("rysquests.shop.history.all"), tr("rysquests.shop.history.own"), tr("rysquests.shop.history.treasury"));
                float cw = FilterChips.width(ui, labels, false);
                FilterChips.render(ui, right - cw, cy - FilterChips.HEIGHT / 2f, labels, historyFilter, index -> historyFilter = index, false);
            }
            return;
        }
        boolean sale = tab == Tab.SELL;
        Map<String, Integer> sections = sections(sale);
        List<String> keys = List.copyOf(sections.keySet());
        String epoch = sale ? "" : epochSection(keys);
        if (compact) {
            renderCompactFilter(ui, right, cy, sections, epoch, sale);
            return;
        }
        if (!sale) {
            float lockX = right - 28;
            renderLockToggle(ui, lockX, cy - 14);
            right = lockX - GAP;
        }
        String ddLabel = filter == Filter.SECTION ? tr(section) : tr("rysquests.shop.section");
        int ddW = Kit.dropdownWidth(ddLabel, SECTION_MAX_UNITS) * 2;
        float ddX = right - ddW;
        int unitX = Math.round(ddX / 2f);
        int unitY = Math.round((cy - Kit.DROP_H) / 2f);
        Kit.dropdown(ui.g, ui.theme, ui.kitHits, dropdown, unitX, unitY, SECTION_MAX_UNITS, ddLabel, filter == Filter.SECTION, ui.mouseX / 2, ui.mouseY / 2);
        right = ddX - GAP;
        List<String> labels = new ArrayList<>(List.of(tr("rysquests.shop.filter.all")));
        if (!sale) {
            labels.add(tr("rysquests.shop.filter.for_quests"));
            if (!epoch.isEmpty()) {
                labels.add(tr(epoch));
            }
        }
        int chosen = switch (filter) {
            case ALL -> 0;
            case FOR_QUESTS -> sale ? -1 : 1;
            case EPOCH -> sale || epoch.isEmpty() ? -1 : 2;
            case SECTION -> -1;
        };
        float cw = FilterChips.width(ui, labels, true);
        FilterChips.render(ui, Math.max(pen + GAP, right - cw), cy - FilterChips.HEIGHT / 2f, labels, chosen, index -> {
            filter = index == 0 ? Filter.ALL : index == 1 ? Filter.FOR_QUESTS : Filter.EPOCH;
            gridScroll.reset();
        }, true);
        List<Kit.Option> options = new ArrayList<>();
        sections.forEach((key, count) -> options.add(new Kit.Option(tr(key), count)));
        int picked = filter == Filter.SECTION ? keys.indexOf(section) : -1;
        Kit.dropdownList(ui.g, ui.theme, ui.kitHits, dropdown, options, picked, 125, ui.mouseX / 2, ui.mouseY / 2, index -> {
            filter = Filter.SECTION;
            section = keys.get(index);
            gridScroll.reset();
        });
    }

    /** The compact layout (picture 46c): every filter in the one list, "Только доступное" among them. */
    private void renderCompactFilter(Ui ui, float right, float cy, Map<String, Integer> sections, String epoch, boolean sale) {
        List<String> keys = new ArrayList<>();
        List<Kit.Option> options = new ArrayList<>();
        options.add(new Kit.Option(tr("rysquests.shop.filter.all")));
        keys.add("\0all");
        if (!sale) {
            options.add(new Kit.Option(tr("rysquests.shop.filter.for_quests")));
            keys.add("\0quests");
        }
        sections.forEach((key, count) -> {
            options.add(new Kit.Option(tr(key), count));
            keys.add(key);
        });
        if (!sale) {
            options.add(new Kit.Option(tr("rysquests.shop.only_available"), onlyAvailable ? tr("rysquests.shop.on") : "", true));
            keys.add("\0only");
        }
        int picked = switch (filter) {
            case ALL -> 0;
            case FOR_QUESTS -> sale ? 0 : 1;
            case EPOCH -> keys.indexOf(epoch);
            case SECTION -> keys.indexOf(section);
        };
        String label = picked >= 0 ? options.get(picked).label() : options.getFirst().label();
        int ddW = Kit.dropdownWidth(label, SECTION_MAX_UNITS) * 2;
        Kit.dropdown(ui.g, ui.theme, ui.kitHits, dropdown, Math.round((right - ddW) / 2f), Math.round((cy - Kit.DROP_H) / 2f), SECTION_MAX_UNITS, label,
                picked > 0, ui.mouseX / 2, ui.mouseY / 2);
        Kit.dropdownList(ui.g, ui.theme, ui.kitHits, dropdown, options, picked, 125, ui.mouseX / 2, ui.mouseY / 2, index -> {
            String key = keys.get(index);
            switch (key) {
                case "\0all" -> filter = Filter.ALL;
                case "\0quests" -> filter = Filter.FOR_QUESTS;
                case "\0only" -> onlyAvailable = !onlyAvailable;
                default -> {
                    filter = key.equals(epoch) ? Filter.EPOCH : Filter.SECTION;
                    section = key;
                }
            }
            gridScroll.reset();
        });
    }

    /** "Только доступное" as a visible switch: a padlock, pressed when offers of closed epochs are hidden (picture 25c). */
    private void renderLockToggle(Ui ui, float x, float y) {
        boolean over = ui.over(x, y, 28, 28);
        ui.box(x, y, 28, 28, 9, ui.c(over ? "slot.hover" : "button.bg"), ui.c(onlyAvailable ? "text.secondary" : "button.border"));
        ui.lock(x + 9.5f, y + 8, 12, ui.c(onlyAvailable ? "text.primary" : "text.secondary"));
        ui.tooltipIfOver(x, y, 28, 28, Component.translatable("rysquests.shop.only_available_tip"));
        ui.hit(x, y, 28, 28, () -> {
            onlyAvailable = !onlyAvailable;
            gridScroll.reset();
        });
    }

    /** The coins beside the filters. @return its width */
    private static float renderWallet(Ui ui, float right, float cy, boolean compact) {
        String currency = mainCurrency();
        String amount = Numbers.compact(ClientShop.balance(currency));
        String word = compact ? "" : "coin".equals(currency) ? tr("rysquests.shop.coins_word") : currencyName(currency);
        float w = 12 + 15 + 7 + ui.width(amount, 15, Ui.DISPLAY) + (word.isEmpty() ? 0 : 7 + ui.width(word, 11, Ui.REGULAR)) + 12;
        float x = right - w;
        ui.box(x, cy - 17, w, 34, 9, ui.c("status.bg"), ui.c("panel.border"));
        float pen = x + 12;
        ui.circle(pen, cy - 7.5f, 15, ui.c("coin"));
        pen += 15 + 7;
        ui.label(amount, pen, cy, 15, Ui.DISPLAY, ui.c("text.primary"));
        pen += ui.width(amount, 15, Ui.DISPLAY) + 7;
        if (!word.isEmpty()) {
            ui.label(word, pen, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        }
        return w;
    }

    // --- the grid ---

    private void renderEmpty(Ui ui, float x, float y, float w, float h) {
        if (tab == Tab.SELL && sections(true).isEmpty() && ClientBook.current().offers().values().stream().noneMatch(ShopOffer::isSale)) {
            EmptyFilter.render(ui, x, y, w, h, tr("rysquests.shop.empty.sell"), null, null);
        } else if (filter == Filter.FOR_QUESTS) {
            EmptyFilter.render(ui, x, y, w, h, tr("rysquests.shop.empty.for_quests"), tr("rysquests.shop.empty.show_all"), this::showAll);
        } else {
            EmptyFilter.render(ui, x, y, w, h, tr("rysquests.shop.empty.filter"), tr("rysquests.shop.empty.show_all"), this::showAll);
        }
    }

    private void showAll() {
        filter = Filter.ALL;
        onlyAvailable = false;
    }

    private void renderGrid(Ui ui, List<ShopOffer> shown, @Nullable ShopOffer selected, float x, float y, float w, float h, int columns) {
        float cardW = (w - (columns - 1) * CARD_GAP) / columns;
        int rows = (shown.size() + columns - 1) / columns;
        gridScroll.content(rows * CARD_H + Math.max(0, rows - 1) * CARD_GAP, Math.round(h));
        int offset = gridScroll.offset();
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        for (int i = 0; i < shown.size(); i++) {
            float cy = y + (i / columns) * (CARD_H + CARD_GAP) - offset;
            if (cy + CARD_H > y && cy < y + h) {
                renderCard(ui, shown.get(i), shown.get(i) == selected, x + (i % columns) * (cardW + CARD_GAP), cy, cardW);
            }
        }
        ui.unclip();
    }

    private void renderCard(Ui ui, ShopOffer offer, boolean selected, float x, float y, float w) {
        ShopOffer.Entry goods = goods(offer);
        GridCard card = GridCard.of(name(offer)).icon(icon(offer), goods.amount()).selected(selected).onClick(() -> select(offer));
        if (offer.isSale()) {
            long have = paying(stackOf(goods));
            int rounds = (int) Math.min(MAX_TIMES, have / Math.max(1, goods.amount()));
            card.meta(rounds > 0 ? text("rysquests.shop.have_rounds", Sidebar.full(have), Plural.full("rysquests.plural.round", rounds))
                    : text("rysquests.shop.have_need", Sidebar.full(have), Sidebar.full(goods.amount())));
            card.gain(priceOf(offer));
            if (rounds > 0 && left(offer) > 0) {
                card.button(tr("rysquests.shop.sell"), () -> {
                    select(offer);
                    trade(offer, 1, false);
                });
            } else {
                card.buttonOff(tr(rounds > 0 ? "rysquests.shop.later" : "rysquests.shop.nothing_to_sell"));
                card.off(!selected);
            }
            card.render(ui, x, y, w, CARD_H);
            return;
        }
        State state = state(offer);
        Need need = need(offer);
        if (state == State.LOCKED) {
            card.meta(lockedLine(offer));
        } else if (need != null) {
            card.meta(tr("rysquests.shop.needed_for_quest"), "text.secondary");
        } else {
            card.meta(limitShort(offer));
        }
        card.price(priceOf(offer), state == State.POOR);
        switch (state) {
            case OK -> card.button(tr("rysquests.shop.buy"), () -> {
                select(offer);
                trade(offer, 1, payFromTreasury(offer));
            });
            case POOR -> card.buttonOff(tr("rysquests.shop.not_enough"));
            case OUT -> card.buttonOff(tr(offer.limit().filter(limit -> limit.reset() == ShopOffer.Reset.NEVER).isPresent() ? "rysquests.shop.sold_out" : "rysquests.shop.later"));
            case LOCKED -> card.buttonOff(tr("rysquests.shop.closed_button"));
        }
        card.off(state == State.LOCKED && !selected);
        card.render(ui, x, y, w, CARD_H);
    }

    private void trade(ShopOffer offer, int count, boolean treasury) {
        status = null;
        PacketDistributor.sendToServer(new ShopTradePayload(offer.id(), count, treasury));
    }

    // --- history ---

    private boolean historyHasTreasury() {
        return ClientShop.inTeam() || ClientShop.history().stream().anyMatch(ProgressStore.ShopLine::treasury);
    }

    private List<ProgressStore.ShopLine> historyShown() {
        List<ProgressStore.ShopLine> out = new ArrayList<>();
        for (ProgressStore.ShopLine line : ClientShop.history()) {
            if (historyFilter == 0 || (historyFilter == 2) == line.treasury()) {
                out.add(line);
            }
        }
        return out;
    }

    private void renderHistory(Ui ui, float x, float y, float w, float h) {
        if (ClientShop.history().isEmpty()) {
            EmptyFilter.render(ui, x, y, w, h, tr("rysquests.shop.empty.history"), null, null);
            return;
        }
        List<ProgressStore.ShopLine> lines = historyShown();
        if (lines.isEmpty()) {
            EmptyFilter.render(ui, x, y, w, h, tr("rysquests.shop.empty.filter"), tr("rysquests.shop.empty.show_all"), () -> historyFilter = 0);
            return;
        }
        boolean source = historyHasTreasury();
        float tableH = Math.min(h, TABLE_HEAD + lines.size() * TABLE_ROW);
        ui.box(x, y, w, tableH, 12, ui.c("card.bg"), ui.c("panel.border"));
        ui.fillCorners(x + 1, y + 1, w - 2, TABLE_HEAD - 1, 11, ui.c("sidebar.bg"), true, true, false, false);
        float whenX = x + 14;
        float whatX = whenX + 120 + 12;
        float sourceX = x + w - 14 - (source ? 90 : 0);
        float coinsRight = sourceX - (source ? 12 : 0);
        float headCy = y + TABLE_HEAD / 2f;
        ui.caps(tr("rysquests.shop.history.when"), whenX, headCy, ui.c("text.dim"));
        ui.caps(tr("rysquests.shop.history.what"), whatX, headCy, ui.c("text.dim"));
        ui.capsRight(tr("rysquests.shop.history.coins"), coinsRight, headCy, ui.c("text.dim"));
        if (source) {
            ui.caps(tr("rysquests.shop.history.source"), sourceX, headCy, ui.c("text.dim"));
        }
        float listTop = y + TABLE_HEAD;
        float listH = tableH - TABLE_HEAD;
        gridScroll.content(lines.size() * TABLE_ROW, Math.round(listH));
        int offset = gridScroll.offset();
        ui.clip(Math.round(x), Math.round(listTop), Math.round(w), Math.round(listH));
        for (int i = 0; i < lines.size(); i++) {
            float rowY = listTop + i * TABLE_ROW - offset;
            if (rowY + TABLE_ROW < listTop || rowY > listTop + listH) {
                continue;
            }
            ProgressStore.ShopLine line = lines.get(i);
            ui.rect(x + 1, rowY, w - 2, 1, ui.c("panel.border"));
            float cy = rowY + TABLE_ROW / 2f;
            ui.labelFit(whenLong(line.time()), whenX, cy, 120, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            ItemStack stack = lineIcon(line);
            float textX = whatX;
            if (!stack.isEmpty()) {
                ui.box(whatX, cy - 12, 24, 24, 5, ui.c("slot.bg"), ui.c("slot.border"));
                ui.item(stack, whatX + 4, cy - 8, 16);
                textX += 24 + 8;
            }
            String amount = (line.delta() > 0 ? "+" : "−") + Sidebar.full(Math.abs(line.delta()));
            float amountW = ui.width(amount, 12.5f, Ui.SEMIBOLD);
            ui.labelFit(lineLong(line), textX, cy, coinsRight - amountW - 12 - textX, 12.5f, Ui.REGULAR, ui.c("text.primary"));
            ui.labelRight(amount, coinsRight, cy, 12.5f, Ui.SEMIBOLD, ui.c(line.delta() > 0 ? "state.ok" : "text.primary"));
            if (source) {
                String tag = tr(line.treasury() ? "rysquests.shop.history.tag_treasury" : "rysquests.shop.history.tag_own");
                float tagW = ui.width(tag, 11, Ui.SEMIBOLD) + 14;
                ui.fill(sourceX, cy - 9, tagW, 18, 5, ui.c(line.treasury() ? "chip.mod.bg" : "button.bg"));
                ui.label(tag, sourceX + 7, cy, 11, Ui.SEMIBOLD, ui.c(line.treasury() ? "chip.mod.fg" : "text.secondary"));
            }
        }
        ui.unclip();
    }

    @Nullable
    private static Object refOf(ProgressStore.ShopLine line) {
        ResourceLocation ref = ResourceLocation.tryParse(line.ref());
        if (ref == null) {
            return null;
        }
        ShopOffer offer = ClientBook.current().offers().get(ref);
        if (offer != null) {
            return offer;
        }
        if (Achievement.isTierQuest(ref)) {
            for (Achievement achievement : ClientBook.current().achievements().values()) {
                for (Achievement.Tier tier : achievement.tiers()) {
                    if (achievement.questId(tier).equals(ref)) {
                        return new Object[] {achievement, achievement.tiers().indexOf(tier)};
                    }
                }
            }
        }
        return ClientBook.current().quests().get(ref);
    }

    private static ItemStack lineIcon(ProgressStore.ShopLine line) {
        Object ref = refOf(line);
        if (ref instanceof ShopOffer offer) {
            return icon(offer);
        }
        if (ref instanceof Object[] pair && pair[0] instanceof Achievement achievement) {
            return achievement.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        }
        if (ref instanceof Quest quest) {
            return quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    /** A line of the history table: "Покупка: Ящик меди", "Награда за квест «Медный провод»". */
    private static String lineLong(ProgressStore.ShopLine line) {
        if (ProgressStore.ShopLine.EXCHANGE.equals(line.ref())) {
            return tr("rysquests.shop.exchange_long");
        }
        if (ProgressStore.ShopLine.TRANSFER.equals(line.ref())) {
            return tr(line.delta() > 0 ? "rysquests.shop.transfer_in" : "rysquests.shop.transfer_out");
        }
        Object ref = refOf(line);
        if (ref instanceof ShopOffer offer) {
            String what = name(offer) + (goods(offer).amount() > 1 ? " ×" + goods(offer).amount() : "");
            if (offer.isSale()) {
                return line.times() > 1 ? text("rysquests.shop.history.sale_times", what, Plural.full("rysquests.plural.time", line.times()))
                        : text("rysquests.shop.history.sale", what);
            }
            return text("rysquests.shop.history.purchase", line.times() > 1 ? name(offer) + " ×" + line.times() : name(offer));
        }
        if (ref instanceof Object[] pair && pair[0] instanceof Achievement achievement) {
            return text("rysquests.shop.history.achievement", tr(achievement.titleKey()), (int) pair[1] + 1);
        }
        if (ref instanceof Quest quest) {
            return text(line.treasury() ? "rysquests.shop.history.team_reward" : "rysquests.shop.history.reward", tr(quest.title()));
        }
        return tr("rysquests.shop.reward");
    }

    /** A line of the last operations, short: "+1 · награда «Медный провод»", "−6 · Ящик меди". */
    private static String lineShort(ProgressStore.ShopLine line) {
        String amount = (line.delta() > 0 ? "+" : "−") + Numbers.compact(Math.abs(line.delta()));
        Object ref = refOf(line);
        String what;
        if (ref instanceof ShopOffer offer) {
            what = offer.isSale() ? text("rysquests.shop.last.sale", name(offer)) : name(offer);
        } else if (ref instanceof Object[] pair && pair[0] instanceof Achievement achievement) {
            what = text("rysquests.shop.last.achievement", tr(achievement.titleKey()));
        } else if (ref instanceof Quest quest) {
            what = text("rysquests.shop.last.reward", tr(quest.title()));
        } else {
            what = lineLong(line);
        }
        return amount + " · " + what;
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

    private static String whenLong(long time) {
        var moment = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        LocalDate day = moment.toLocalDate();
        LocalDate today = LocalDate.now();
        String date = day.equals(today) ? tr("rysquests.shop.today") : day.equals(today.minusDays(1)) ? tr("rysquests.shop.yesterday") : moment.format(DATE);
        return date + ", " + moment.format(TIME);
    }

    // --- the detail on the right ---

    private void renderPanel(Ui ui, @Nullable ShopOffer offer, int x, int y, int w, int h) {
        if (tab == Tab.HISTORY || offer == null) {
            panel.render(ui, x, y, w, h, (u, bx, by, bw) -> renderWallets(u, bx, by, bw, tab == Tab.HISTORY), null);
            return;
        }
        if (offer.isSale()) {
            renderSaleDetail(ui, offer, x, y, w, h);
        } else {
            renderBuyDetail(ui, offer, x, y, w, h);
        }
    }

    /** The wallets, and under them the week (history) or the last operations (an empty filter, picture 25d). */
    private float renderWallets(Ui ui, float x, float y, float w, boolean week) {
        String currency = mainCurrency();
        float pen = y + DetailPanel.rubric(ui, x, y, w, tr("rysquests.shop.wallets"), null) + 6;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {tr("rysquests.shop.wallet_own"), money(currency, ClientShop.balance(currency))});
        if (ClientShop.inTeam()) {
            rows.add(new String[] {tr("rysquests.shop.wallet_treasury"), money(currency, ClientShop.treasury(currency))});
        }
        pen += summary(ui, x, pen, w, rows, null, true) + 12;
        if (week) {
            long now = System.currentTimeMillis();
            long got = 0;
            long spent = 0;
            for (ProgressStore.ShopLine line : ClientShop.history()) {
                if (now - line.time() <= WEEK_MS && line.currency().equals(currency)) {
                    if (line.delta() > 0) {
                        got += line.delta();
                    } else {
                        spent -= line.delta();
                    }
                }
            }
            pen += DetailPanel.rubric(ui, x, pen, w, tr("rysquests.shop.week"), null) + 6;
            List<String[]> totals = List.of(new String[] {tr("rysquests.shop.week_got"), "+" + Sidebar.full(got)}, new String[] {tr("rysquests.shop.week_spent"), "−" + Sidebar.full(spent)});
            pen += summary(ui, x, pen, w, totals, got > 0 ? "state.ok" : null, false);
            return pen - y;
        }
        pen = renderLast(ui, x, pen, w);
        return pen - y;
    }

    /**
     * A block of rows "name ... value" (mockup {@code .sum}). @param firstToken the first value's color, or null
     * @param coins a coin before each value (the wallets)
     * @return its height
     */
    private static float summary(Ui ui, float x, float y, float w, List<String[]> rows, @Nullable String firstToken, boolean coins) {
        float rowH = Ui.lineHeight(12.5f);
        float h = 20 + rows.size() * rowH + Math.max(0, rows.size() - 1) * 6;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        float cy = y + 10 + rowH / 2f;
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String token = row.length > 2 && row[2] != null ? row[2] : i == 0 && firstToken != null ? firstToken : "text.primary";
            float valueW = ui.width(row[1], 12.5f, Ui.MEDIUM);
            ui.labelFit(row[0], x + 12, cy, w - 24 - valueW - 8 - (coins ? 15 : 0), 12.5f, Ui.REGULAR, ui.c("text.muted"));
            ui.labelRight(row[1], x + w - 12, cy, 12.5f, Ui.MEDIUM, ui.c(token));
            if (coins) {
                ui.circle(x + w - 12 - valueW - 15, cy - 5, 10, ui.c("coin"));
            }
            cy += rowH + 6;
        }
        return h;
    }

    private static float lastHeight() {
        int lines = Math.min(LAST_LINES, ClientShop.history().size());
        return lines == 0 ? 0 : 20 + Ui.lineHeight(11) + lines * (Ui.lineHeight(12.5f) + 6);
    }

    /** The last operations, at the foot of the panel's body. @return the y under it */
    private float renderLast(Ui ui, float x, float y, float w) {
        List<ProgressStore.ShopLine> lines = ClientShop.history();
        if (lines.isEmpty()) {
            return y;
        }
        float h = lastHeight();
        float top = Math.max(y, panel.bodyBottom() - 14 - h);
        ui.box(x, top, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        ui.caps(tr("rysquests.shop.last"), x + 12, top + 10 + Ui.lineHeight(11) / 2f, ui.c("text.dim"));
        float rowH = Ui.lineHeight(12.5f);
        float cy = top + 10 + Ui.lineHeight(11) + 6 + rowH / 2f;
        for (int i = 0; i < Math.min(LAST_LINES, lines.size()); i++) {
            ProgressStore.ShopLine line = lines.get(i);
            String time = when(line.time());
            float timeW = ui.width(time, 12.5f, Ui.REGULAR);
            ui.labelFit(lineShort(line), x + 12, cy, w - 24 - timeW - 8, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            ui.labelRight(time, x + w - 12, cy, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            cy += rowH + 6;
        }
        return top + h;
    }

    private void renderBuyDetail(Ui ui, ShopOffer offer, int x, int y, int w, int h) {
        State state = state(offer);
        boolean treasury = payFromTreasury(offer);
        int max = Math.max(1, Math.min(MAX_TIMES, left(offer)));
        times = Math.clamp(times, 1, max);
        ShopOffer.Entry price = price(offer);
        String currency = price == null ? mainCurrency() : price.id();
        long total = priceOf(offer) * times;
        long have = wallet(currency, treasury);
        boolean canBuy = state != State.LOCKED && state != State.OUT && canPay(offer, times, treasury);
        boolean shortOfMoney = state != State.LOCKED && state != State.OUT && !canBuy;
        DetailPanel.Action action;
        if (state == State.LOCKED) {
            action = DetailPanel.Action.state(lockedLine(offer));
        } else if (state == State.OUT) {
            action = DetailPanel.Action.state(tr(offer.limit().filter(limit -> limit.reset() == ShopOffer.Reset.NEVER).isPresent() ? "rysquests.shop.sold_out_long" : "rysquests.shop.limit_used"));
        } else if (canBuy) {
            String label = times > 1 ? text("rysquests.shop.buy_times_for", times, Sidebar.full(total)) : text("rysquests.shop.buy_for", Sidebar.full(total));
            action = DetailPanel.Action.button(label, () -> trade(offer, times, treasury));
        } else {
            action = DetailPanel.Action.state(text("rysquests.shop.short_by", money(currency, Math.max(1, total - have))));
        }
        ItemStack goodsStack = stackOf(goods(offer));
        if (!goodsStack.isEmpty()) {
            action = action.withLink(tr("rysquests.shop.recipe_instead"), () -> Recipes.showRecipes(goodsStack));
        }
        panel.render(ui, x, y, w, h, (u, bx, by, bw) -> {
            float pen = by;
            String category = offer.category().map(ShopView::tr).orElse("");
            String sub = category.isEmpty() ? limitLong(offer) : category + " · " + limitLong(offer);
            float headH = DetailPanel.header(u, bx, pen, bw, icon(offer), name(offer), sub, null, false, null);
            u.count(goods(offer).amount() * times, bx + 45, pen + 48);
            pen += headH + DetailPanel.GAP;
            Need need = need(offer);
            if (need != null) {
                pen += note(u, bx, pen, bw, Icons.Shape.PIN, text("rysquests.shop.need_note", tr(need.quest.title()), need.missing), tr(need.quest.title()), false) + DetailPanel.GAP;
            }
            if (offer.gives().stream().filter(entry -> !entry.isCurrency()).count() > 1) {
                pen += DetailPanel.rubric(u, bx, pen, bw, tr("rysquests.shop.inside"), null) + 6;
                for (ShopOffer.Entry entry : offer.gives()) {
                    if (!entry.isCurrency()) {
                        ItemStack stack = stackOf(entry);
                        u.cell(stack, bx, pen, 26, 26, 5, 18, u.c("slot.bg"), u.c("slot.border"));
                        u.count(entry.amount() * times, bx + 25, pen + 28);
                        u.labelFit(stack.getHoverName().getString(), bx + 34, pen + 13, bw - 34, 12.5f, Ui.REGULAR, u.c("text.secondary"));
                        pen += 26 + 6;
                    }
                }
                pen += DetailPanel.GAP - 6;
            }
            pen += stepperRow(u, bx, pen, bw, tr("rysquests.shop.quantity"), max, false) + DetailPanel.GAP;
            if (ClientShop.inTeam()) {
                float cy = pen + Segments.HEIGHT / 2f;
                u.label(tr("rysquests.shop.pay_from"), bx, cy, 12.5f, Ui.REGULAR, u.c("text.muted"));
                List<Segments.Item> wallets = List.of(Segments.item(text("rysquests.shop.pay_own", Numbers.compact(ClientShop.balance(currency)))),
                        Segments.item(text("rysquests.shop.pay_treasury", Numbers.compact(ClientShop.treasury(currency)))));
                float sw = Segments.width(wallets, fromTreasury ? 1 : 0);
                Segments.render(u, bx + bw - sw, pen, wallets, fromTreasury ? 1 : 0, index -> fromTreasury = index == 1);
                pen += Segments.HEIGHT + DetailPanel.GAP;
            }
            long rest = have - total;
            List<String[]> rows = List.of(
                    new String[] {tr("rysquests.shop.you_receive"), text("rysquests.shop.pieces", Sidebar.full((long) goods(offer).amount() * times))},
                    new String[] {tr("rysquests.shop.price"), money(currency, total)},
                    new String[] {tr("rysquests.shop.remaining"), rest < 0 ? "−" + Sidebar.full(-rest) : money(currency, rest), rest < 0 ? "state.bad" : null});
            pen += summary(u, bx, pen, bw, rows, null, false) + DetailPanel.GAP;
            if (status != null) {
                u.labelFit(status, bx, pen + 8, bw, 12, Ui.REGULAR, u.c(statusBad ? "state.bad" : "state.ok"));
                pen += 16 + DetailPanel.GAP;
            }
            if (shortOfMoney) {
                pen = renderHowToGet(u, bx, pen, bw, currency);
            } else {
                pen = renderLast(u, bx, pen + 2, bw);
            }
            return pen - by;
        }, action);
    }

    /** "Как набрать" (picture 26): sell what the shop takes, take rewards that wait. @return the y under it */
    private float renderHowToGet(Ui ui, float x, float y, float w, String currency) {
        List<Runnable> rows = new ArrayList<>();
        ShopOffer best = null;
        long bestGain = 0;
        for (ShopOffer sale : ClientBook.current().offers().values()) {
            ShopOffer.Entry pays = price(sale);
            if (!sale.isSale() || pays == null || !pays.id().equals(currency) || locked(sale) || left(sale) <= 0) {
                continue;
            }
            long have = paying(stackOf(goods(sale)));
            long gain = Math.min(left(sale), have / Math.max(1, goods(sale).amount())) * pays.amount();
            if (gain > bestGain) {
                bestGain = gain;
                best = sale;
            }
        }
        float pen = y;
        boolean any = best != null || BookTallies.readyToClaim() > 0;
        if (!any) {
            return pen;
        }
        pen += DetailPanel.rubric(ui, x, pen, w, tr("rysquests.shop.how_to_get"), null) + 4;
        if (best != null) {
            ShopOffer sale = best;
            long have = paying(stackOf(goods(sale)));
            int rounds = (int) Math.min(MAX_TIMES, Math.min(left(sale), have / Math.max(1, goods(sale).amount())));
            pen += gainRow(ui, x, pen, w, icon(sale), text("rysquests.shop.sell_what", name(sale), Sidebar.full((long) rounds * goods(sale).amount())),
                    text("rysquests.shop.you_have", Sidebar.full(have)), "+" + Numbers.compact(bestGain), () -> {
                        openTab(Tab.SELL);
                        selectedId = sale.id();
                        times = rounds;
                    });
        }
        int claim = BookTallies.readyToClaim();
        if (claim > 0) {
            long coins = waitingCoins(currency);
            List<ItemStack> stacks = BookTallies.claimStacks();
            pen += gainRow(ui, x, pen, w, stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst(), text("rysquests.shop.claim_rewards", Plural.full("rysquests.plural.reward_acc", claim)),
                    tr("rysquests.shop.on_now_tab"), coins > 0 ? "+" + Numbers.compact(coins) : null, () -> screen.openTab(BookTabs.byId(BookTabs.NOW)));
        }
        return pen;
    }

    /** Coins in rewards that wait to be taken. */
    private static long waitingCoins(String currency) {
        long sum = 0;
        for (Quest quest : BookData.book().quests().values()) {
            if (BookData.inStory(quest) && BookState.status(quest) == QuestStatus.COMPLETED) {
                for (RewardDef reward : quest.rewards()) {
                    if (reward instanceof CurrencyReward coins && coins.id().equals(currency)) {
                        sum += coins.amount();
                    }
                }
            }
        }
        return sum;
    }

    private static float gainRow(Ui ui, float x, float y, float w, ItemStack icon, String title, String sub, @Nullable String gain, Runnable action) {
        if (gain == null) {
            return ListRow.of(title).icon(icon).sub(sub).chevron().look(ListRow.Look.FLAT).onClick(action).render(ui, x, y, w);
        }
        // The gain in green, then the chevron: the row keeps the room of both on its right.
        float h = ListRow.of(title).icon(icon).sub(sub).right(null, gain, "state.ok").look(ListRow.Look.FLAT).onClick(action).render(ui, x, y, w - 18);
        ui.icon(Icons.Shape.CHEVRON_RIGHT, x + w - 14, y + h / 2f - 6, 12, ui.c("text.dim"));
        ui.hit(x + w - 18, y, 18, h, action);
        return h;
    }

    /**
     * A note in the panel (mockup {@code .note}): a small icon and text, a part of it bold. @param warn painted as a warning
     * @return its height
     */
    private static float note(Ui ui, float x, float y, float w, Icons.Shape icon, String line, @Nullable String bold, boolean warn) {
        float textX = x + 12 + 14 + 10;
        float textW = w - (textX - x) - 12;
        float lineH = Math.round(12.5f * 1.45f);
        List<String> lines = ui.wrap(line, textW, 12.5f, Ui.REGULAR, 4);
        float h = 16 + lines.size() * lineH;
        ui.box(x, y, w, h, 10, ui.c(warn ? "callout.bg" : "status.bg"), ui.c(warn ? "callout.border" : "panel.border"));
        ui.icon(icon, x + 12, y + 8 + (lineH - 14) / 2f, 14, ui.c(warn ? "state.warn" : "text.muted"));
        int plain = ui.c(warn ? "text.primary" : "text.secondary");
        for (int i = 0; i < lines.size(); i++) {
            float cy = y + 8 + i * lineH + lineH / 2f;
            String part = lines.get(i);
            int at = bold == null ? -1 : part.indexOf(bold);
            if (at < 0) {
                ui.label(part, textX, cy, 12.5f, Ui.REGULAR, plain);
                continue;
            }
            // The quest's name in bold, the rest around it plain: three runs side by side.
            String before = part.substring(0, at);
            float pen = textX;
            ui.label(before, pen, cy, 12.5f, Ui.REGULAR, plain);
            pen += ui.width(before, 12.5f, Ui.REGULAR);
            ui.label(bold, pen, cy, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
            pen += ui.width(bold, 12.5f, Ui.SEMIBOLD);
            ui.label(part.substring(at + bold.length()), pen, cy, 12.5f, Ui.REGULAR, plain);
        }
        return h;
    }

    /** "Количество" or "Сколько раз" with the stepper, and for a sale "Всё". @return its height */
    private float stepperRow(Ui ui, float x, float y, float w, String label, int max, boolean all) {
        float cy = y + 17;
        ui.label(label, x, cy, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        float allW = all ? ui.buttonWidth(tr("rysquests.shop.all"), 12, Ui.MEDIUM, 10) : 0;
        float boxW = 32 + 44 + 32 + 2;
        float boxX = x + w - boxW - (all ? allW + 8 : 0);
        ui.box(boxX, y, boxW, 34, 9, ui.c("button.bg"), ui.c("button.border"));
        stepButton(ui, "−", boxX + 1, y, times > 1, () -> times--);
        ui.labelCentered(String.valueOf(times), boxX + 1 + 32 + 22, cy, 14, Ui.SEMIBOLD, ui.c("text.primary"));
        stepButton(ui, "+", boxX + 1 + 32 + 44, y, times < max, () -> times++);
        if (all) {
            ui.button(tr("rysquests.shop.all"), x + w - allW, cy - 14, allW, 28, 9, 12, Ui.MEDIUM, 10, Ui.Style.PLAIN, () -> times = max);
        }
        return 34;
    }

    private static void stepButton(Ui ui, String label, float x, float y, boolean enabled, Runnable action) {
        if (enabled && ui.over(x, y, 32, 34)) {
            ui.fill(x, y + 1, 32, 32, 8, ui.c("slot.hover"));
        }
        ui.labelCentered(label, x + 16, y + 17, 15, Ui.REGULAR, ui.c(enabled ? "text.secondary" : "text.dim"));
        if (enabled) {
            ui.hit(x, y, 32, 34, action);
        }
    }

    private void renderSaleDetail(Ui ui, ShopOffer offer, int x, int y, int w, int h) {
        ShopOffer.Entry goods = goods(offer);
        ShopOffer.Entry pays = price(offer);
        String currency = pays == null ? mainCurrency() : pays.id();
        long have = paying(stackOf(goods));
        int rounds = (int) Math.min(Math.min(MAX_TIMES, left(offer)), have / Math.max(1, goods.amount()));
        int max = Math.max(1, rounds);
        times = Math.clamp(times, 1, max);
        long give = (long) goods.amount() * times;
        long gain = priceOf(offer) * times;
        DetailPanel.Action action = rounds > 0 && !locked(offer)
                ? DetailPanel.Action.button(text("rysquests.shop.sell_pieces_for", Sidebar.full(give), Sidebar.full(gain)), () -> trade(offer, times, false))
                : DetailPanel.Action.state(tr(left(offer) <= 0 ? "rysquests.shop.limit_used" : "rysquests.shop.nothing_to_sell"));
        panel.render(ui, x, y, w, h, (u, bx, by, bw) -> {
            float pen = by;
            String category = offer.category().map(ShopView::tr).orElse("");
            String sub = tr("rysquests.shop.sale_word") + (category.isEmpty() ? "" : " · " + category) + " · " + limitLong(offer);
            String title = name(offer) + (goods.amount() > 1 ? " ×" + goods.amount() : "");
            float headH = DetailPanel.header(u, bx, pen, bw, icon(offer), title, sub, null, false, null);
            u.count(goods.amount(), bx + 45, pen + 48);
            pen += headH + DetailPanel.GAP;
            pen += stepperRow(u, bx, pen, bw, tr("rysquests.shop.times"), max, true) + DetailPanel.GAP;
            long balance = ClientShop.balance(currency);
            List<String[]> rows = List.of(
                    new String[] {tr("rysquests.shop.you_give"), text("rysquests.shop.pieces", Sidebar.full(give))},
                    new String[] {tr("rysquests.shop.stays_in_inventory"), Sidebar.full(Math.max(0, have - give))},
                    new String[] {tr("rysquests.shop.you_get"), money(currency, gain)},
                    new String[] {tr("rysquests.shop.will_be"), money(currency, balance + gain)});
            pen += summary(u, bx, pen, bw, rows, null, false) + DetailPanel.GAP;
            Quest open = usedIn(offer, true);
            Quest done = open == null ? usedIn(offer, false) : null;
            if (open != null) {
                pen += note(u, bx, pen, bw, Icons.Shape.WARN, text("rysquests.shop.sell_warn", tr(open.title())), null, true) + DetailPanel.GAP;
            } else if (done != null) {
                pen += note(u, bx, pen, bw, Icons.Shape.CHECK, text("rysquests.shop.sell_done", tr(done.title())), null, false) + DetailPanel.GAP;
            }
            if (status != null) {
                u.labelFit(status, bx, pen + 8, bw, 12, Ui.REGULAR, u.c(statusBad ? "state.bad" : "state.ok"));
                pen += 16 + DetailPanel.GAP;
            }
            pen = renderLast(u, bx, pen + 2, bw);
            return pen - by;
        }, action);
    }

    // --- input ---

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (dropdown.scrolled(mouseX / 2, mouseY / 2, amount)) {
            return;
        }
        if (panel.contains(mouseX, mouseY)) {
            panel.scroll(amount);
        } else {
            gridScroll.wheel(amount, WHEEL);
        }
    }

    @Override
    public boolean key(int key) {
        return dropdown.keyPressed(key);
    }

    @Override
    public void closed() {
        dropdown.close();
    }

    // --- for the autotest ---

    void selectForTest(ResourceLocation offer) {
        ShopOffer found = ClientBook.current().offers().get(offer);
        if (found == null) {
            return;
        }
        Tab target = found.isSale() ? Tab.SELL : Tab.BUY;
        if (tab != target) {
            openTab(target);
        }
        selectedId = offer;
        times = 1;
    }

    void showHistoryForTest() {
        openTab(Tab.HISTORY);
    }

    void tradeForTest(ResourceLocation offer, boolean treasury) {
        times = 1;
        trade(ClientBook.current().offers().get(offer), 1, treasury);
    }

    /** Sets what the autotest shows: the filter ("all", "quests", "epoch", or a section key), the count, the wallet, the list. */
    void stateForTest(String filterName, int count, boolean treasury, boolean openList) {
        switch (filterName) {
            case "all" -> filter = Filter.ALL;
            case "quests" -> filter = Filter.FOR_QUESTS;
            case "epoch" -> filter = Filter.EPOCH;
            default -> {
                filter = Filter.SECTION;
                section = filterName;
            }
        }
        times = count;
        fromTreasury = treasury;
        dropdown.open = openList;
    }

    void onlyAvailableForTest(boolean on) {
        onlyAvailable = on;
    }

    /** What the server would take as payment: the main inventory only, and plain stacks without data of their own. */
    private static long paying(ItemStack cost) {
        if (cost.isEmpty() || Minecraft.getInstance().player == null) {
            return 0;
        }
        long have = 0;
        for (ItemStack stack : Minecraft.getInstance().player.getInventory().items) {
            if (stack.is(cost.getItem()) && stack.getComponentsPatch().isEmpty()) {
                have += stack.getCount();
            }
        }
        return have;
    }
}
