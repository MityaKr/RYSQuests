package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Field;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.ScaledScreen;
import works.repka.rysquests.block.ModBlocks;
import works.repka.rysquests.block.ReceiverMenu;
import works.repka.rysquests.block.ReceiverProgram;
import works.repka.rysquests.block.ReceiverPrograms;
import works.repka.rysquests.block.ReceiverStatus;
import works.repka.rysquests.block.ReceiverView;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.network.ReceiverConfigPayload;

/**
 * The window of the intake block (shop plan, section 4.1, mockups 04 and 05): on the left the trades of its mode with a search, on the
 * right the mode, where the coins go, the trade in words, the buffer and the counters since the block was placed, or the rows of a
 * delivery; the status line with its lamp at the bottom. Every number is in mockup pixels, as in the book.
 *
 * <p>The window shows what the server sent ({@link ReceiverView}); a click asks the server, which checks it and sends the new view
 * back within a tick.
 */
public final class ReceiverScreen extends ScaledScreen implements MenuAccess<ReceiverMenu> {
    private static final int W = 860;
    private static final int H = 608;
    private static final int HEAD = 58;
    private static final int FOOT = 40;
    private static final int LEFT = 330;
    private static final int LIST_PAD = 12;
    private static final int SEARCH_H = 34;
    private static final int ROW_H = 44;
    private static final int ROW_STEP = 50;
    private static final int RIGHT_PAD_X = 16;
    private static final int RIGHT_PAD_Y = 14;
    private static final int GAP = 11;
    private static final float DIM = 0.55f;

    private final ReceiverMenu menu;
    private final List<Ui.Hit> hits = new ArrayList<>();
    private final List<Ui.Hit> rightHits = new ArrayList<>();
    private final Kit.Hits kitHits = new Kit.Hits();
    private @Nullable Field search;
    private @Nullable ItemStack hovered;
    private float listScroll;
    private float listMax;
    private int listTop;
    private int listBottom;
    private int windowX;

    public ReceiverScreen(ReceiverMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public ReceiverMenu getMenu() {
        return menu;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private ReceiverView view() {
        return menu.view();
    }

    private ReceiverProgram program() {
        ReceiverProgram program = ReceiverProgram.Registry.get(view().program());
        return program == null ? ReceiverPrograms.SELL : program;
    }

    private boolean selling() {
        return program() == ReceiverPrograms.SELL;
    }

    @Override
    protected void init() {
        super.init();
        search = new Field(80, 10);
        search.setHint(Component.translatable("rysquests.receiver.search"));
        search.setResponder(value -> listScroll = 0);
        addWidget(search);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // The server's side of the window closes too, as for any container.
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean typing = search != null && search.isFocused();
        if (!typing && minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // --- what the server is asked ---

    private void ask(ResourceLocation program, Optional<ResourceLocation> offer, boolean treasury, boolean reset) {
        PacketDistributor.sendToServer(new ReceiverConfigPayload(menu.containerId, program, offer, treasury, reset));
    }

    private void choose(ShopOffer offer) {
        ask(view().program(), Optional.of(offer.id()), view().treasury(), false);
    }

    private void mode(ReceiverProgram program) {
        if (!program.id().equals(view().program())) {
            ask(program.id(), Optional.empty(), view().treasury(), false);
        }
    }

    // --- the trades ---

    /** Closed to the team: a stage it lacks, or a quest it has not done. The block could not make such a trade either. */
    private static boolean locked(ShopOffer offer) {
        boolean stage = offer.stage().filter(required -> !ClientProgress.hasStage(required)).isPresent();
        return stage || offer.quest().filter(quest -> !ClientProgress.get(quest).everDone()).isPresent();
    }

    private List<ShopOffer> open() {
        ReceiverProgram program = program();
        List<ShopOffer> out = new ArrayList<>();
        for (ShopOffer offer : ClientBook.current().offers().values()) {
            if (program.fits(offer) && !locked(offer)) {
                out.add(offer);
            }
        }
        return out;
    }

    private List<ShopOffer> shown(List<ShopOffer> open) {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return open;
        }
        return open.stream().filter(offer -> ShopView.name(offer).toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private @Nullable ShopOffer selected() {
        ShopOffer offer = view().offer().map(id -> ClientBook.current().offers().get(id)).orElse(null);
        return offer != null && program().fits(offer) ? offer : null;
    }

    private static ShopOffer.Entry firstItem(List<ShopOffer.Entry> side) {
        return side.stream().filter(entry -> !entry.isCurrency()).findFirst().orElse(side.getFirst());
    }

    /** What the row shows: the item sold, or the goods a delivery gives. */
    private static ShopOffer.Entry shownEntry(ShopOffer offer) {
        return firstItem(offer.delivery() ? offer.gives() : offer.cost());
    }

    private static long coins(ShopOffer offer) {
        return ReceiverPrograms.Sell.coins(offer);
    }

    private String currency() {
        return view().currency();
    }

    private static Map<String, Long> delivered(ShopOffer offer) {
        return ClientShop.delivered(offer.id());
    }

    /** How far a delivery has come in whole percent: 100 only when every row is full. */
    private static int percent(ShopOffer offer) {
        Map<String, Long> delivered = delivered(offer);
        return offer.deliveryComplete(delivered) ? 100 : Math.min(99, (int) Math.floor(offer.deliveryProgress(delivered) * 100));
    }

    // --- drawing ---

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Draw.rect(g, 0, 0, width, height, Draw.alpha(works.repka.ryscore.theme.ThemeManager.get().c("overlay"), DIM));
    }

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        hits.clear();
        rightHits.clear();
        kitHits.clear();
        Ui ui = new Ui(g, mouseX, mouseY, hits, rightHits, kitHits);
        int x = (width * 2 - W) / 2;
        int y = Math.max(0, (height * 2 - H) / 2);
        windowX = x;
        ui.shadow(x, y, W, H, 14, 1f);
        ui.box(x, y, W, H, 14, ui.c("panel.bg"), ui.c("panel.border"));
        renderHeader(ui, x, y);
        int bodyTop = y + HEAD;
        int bodyH = H - HEAD - FOOT;
        ui.rect(x + LEFT, bodyTop, 1, bodyH, ui.c("panel.border"));
        renderList(ui, x, bodyTop, LEFT, bodyH);
        renderRight(ui, x + LEFT + 1 + RIGHT_PAD_X, bodyTop + RIGHT_PAD_Y, W - LEFT - 1 - RIGHT_PAD_X * 2);
        renderFooter(ui, x, y + H - FOOT);
        hovered = ui.hoveredStack;
        if (ui.hoveredStack != null) {
            g.renderTooltip(font, ui.hoveredStack, mouseX, mouseY);
        } else if (ui.hoveredText != null) {
            g.renderTooltip(font, ui.hoveredText, mouseX, mouseY);
        }
    }

    private void renderHeader(Ui ui, int x, int y) {
        float cy = y + HEAD / 2f;
        ui.plate(x + 16, cy - 18, 36, 36, 9);
        ui.item(new ItemStack(ModBlocks.RECEIVER_ITEM.get()), x + 16 + 6, cy - 12, 24);
        float textX = x + 16 + 36 + 12;
        ui.label(tr("block.rysquests.shop_receiver"), textX, cy - 8, 16, Ui.DISPLAY, ui.c("text.primary"));
        ui.label(tr(selling() ? "rysquests.receiver.sub.sell" : "rysquests.receiver.sub.delivery"), textX, cy + 10, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        int closeX = x + W - 14 - 36;
        Kit.close(ui.g, ui.theme, kitHits, Math.round(closeX / 2f), Math.round((cy - 18) / 2f), ui.mouseX / 2, ui.mouseY / 2, this::onClose);
        ReceiverView view = view();
        String owner = !view.owned() ? tr("rysquests.receiver.nobody") : view.ownerInTeam() ? text("rysquests.receiver.owner_team", view.ownerName()) : view.ownerName();
        float ownerRight = closeX - 12;
        float ownerW = ui.width(owner, 12, Ui.REGULAR);
        ui.labelRight(owner, ownerRight, cy, 12, Ui.REGULAR, ui.c("text.muted"));
        ui.icon(Icons.Shape.USERS, ownerRight - ownerW - 6 - 14, cy - 7, 14, ui.c("text.muted"));
        ui.rect(x, y + HEAD, W, 1, ui.c("panel.border"));
    }

    private void renderList(Ui ui, int x, int y, int w, int h) {
        int left = x + LIST_PAD;
        int inner = w - LIST_PAD * 2;
        int top = y + LIST_PAD;
        boolean focused = search != null && search.isFocused();
        ui.box(left, top, inner, SEARCH_H, 9, ui.c("input.bg"), focused ? ui.c("card.border") : ui.c("input.border"));
        ui.icon(Icons.Shape.MAGNIFIER, left + 11, top + SEARCH_H / 2f - 7.5f, 15, ui.c("text.muted"));
        if (search != null) {
            search.setX(Math.round((left + 34) / 2f));
            search.setY(Math.round((top + SEARCH_H / 2f) / 2f) - 5);
            search.setWidth(Math.round((inner - 34 - 10) / 2f));
            search.render(ui.g, ui.mouseX / 2, ui.mouseY / 2, 0);
        }
        float rubricCy = top + SEARCH_H + 6 + Ui.lineHeight(11) / 2f;
        ui.caps(tr(selling() ? "rysquests.receiver.list.sell" : "rysquests.receiver.list.delivery"), left, rubricCy, ui.c("text.dim"));
        List<ShopOffer> open = open();
        List<ShopOffer> shown = shown(open);
        listTop = Math.round(rubricCy + Ui.lineHeight(11) / 2f + 6);
        listBottom = y + h - LIST_PAD;
        if (shown.isEmpty()) {
            boolean filtered = !open.isEmpty();
            EmptyFilter.render(ui, left, listTop, inner, listBottom - listTop,
                    tr(filtered ? "rysquests.receiver.list.nothing_found" : selling() ? "rysquests.receiver.list.empty.sell" : "rysquests.receiver.list.empty.delivery"),
                    filtered ? tr("rysquests.receiver.list.show_all") : null, filtered ? () -> search.setValue("") : null);
            return;
        }
        listMax = Math.max(0, shown.size() * ROW_STEP - ROW_STEP + ROW_H - (listBottom - listTop));
        listScroll = Math.max(0, Math.min(listScroll, listMax));
        ShopOffer chosen = selected();
        ui.clip(left - 2, listTop, inner + 4, listBottom - listTop);
        float rowY = listTop - listScroll;
        for (ShopOffer offer : shown) {
            if (rowY + ROW_H >= listTop && rowY <= listBottom) {
                renderRow(ui, offer, offer == chosen, left, rowY, inner);
            }
            rowY += ROW_STEP;
        }
        ui.unclip();
    }

    private void renderRow(Ui ui, ShopOffer offer, boolean chosen, float x, float y, float w) {
        boolean over = ui.over(x, y, w, ROW_H);
        if (chosen) {
            ui.card(x, y, w, ROW_H, 10, true);
        } else if (over) {
            ui.fill(x, y, w, ROW_H, 10, Draw.alpha(ui.c("filter.active.bg"), 0.5f));
        }
        ShopOffer.Entry entry = shownEntry(offer);
        ItemStack stack = entry.stack();
        float iconY = y + (ROW_H - 30) / 2f;
        ui.plate(x + 6, iconY, 30, 30, 6);
        ui.item(stack, x + 6 + 7, iconY + 7, 16);
        ui.hoverItem(stack, x + 6, iconY, 30, 30);
        if (!offer.delivery()) {
            ui.count(entry.amount(), x + 6 + 30, iconY + 30);
        }
        float textX = x + 6 + 30 + 11;
        String right = null;
        String sub;
        if (offer.delivery()) {
            Map<String, Long> delivered = delivered(offer);
            sub = offer.deliveryComplete(delivered) ? tr("rysquests.receiver.list.ready")
                    : delivered.isEmpty() ? tr("rysquests.receiver.list.not_started") : text("rysquests.receiver.list.percent", percent(offer));
        } else {
            sub = offer.category().map(ReceiverScreen::tr).orElse("");
            right = "+" + Sidebar.full(coins(offer));
        }
        float rightW = right == null ? 0 : ui.width(right, 12, Ui.REGULAR) + 8 + 10;
        float textW = w - (textX - x) - 10 - rightW - 8;
        ui.labelFit(ShopView.name(offer), textX, y + ROW_H / 2f - (sub.isEmpty() ? 0 : 8), textW, 13, Ui.MEDIUM, ui.c("text.primary"));
        if (!sub.isEmpty()) {
            ui.labelFit(sub, textX, y + ROW_H / 2f + 9, textW, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        if (right != null) {
            float coinX = x + w - 10 - 10;
            ui.circle(coinX, y + ROW_H / 2f - 5, 10, ui.c("coin"));
            ui.labelRight(right, coinX - 8, y + ROW_H / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        if (!chosen) {
            ui.hit(x, y, w, ROW_H, () -> choose(offer));
        }
    }

    private void renderRight(Ui ui, float x, float y, float w) {
        float pen = y;
        pen += modeRow(ui, x, pen, w) + GAP;
        ShopOffer offer = selected();
        if (selling()) {
            pen += coinsRow(ui, x, pen, w) + GAP;
        }
        if (offer == null) {
            ui.label(tr("rysquests.receiver.pick"), x, pen + 10, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        if (selling()) {
            renderSale(ui, offer, x, pen, w);
        } else {
            renderDelivery(ui, offer, x, pen, w);
        }
    }

    /** "Режим" and its switch: sell, deliver, and the greyed "trial" kept for later (plan, section 4.7). @return its height */
    private float modeRow(Ui ui, float x, float y, float w) {
        List<Segments.Item> items = List.of(Segments.item(tr("rysquests.receiver.mode.sell")), Segments.item(tr("rysquests.receiver.mode.delivery")),
                new Segments.Item(tr("rysquests.receiver.mode.trial"), null, true, Component.translatable("rysquests.receiver.mode.trial_tip")));
        int at = selling() ? 0 : 1;
        float sw = Segments.width(items, at);
        ui.label(tr("rysquests.receiver.mode"), x, y + Segments.HEIGHT / 2f, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        Segments.render(ui, x + w - sw, y, items, at, index -> mode(index == 0 ? ReceiverPrograms.SELL : ReceiverPrograms.DELIVERY));
        return Segments.HEIGHT;
    }

    /** "Монеты в": the owner's wallet or the team's treasury, with what each holds. @return its height */
    private float coinsRow(Ui ui, float x, float y, float w) {
        ReceiverView view = view();
        boolean mine = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(view.owner());
        String own = mine || !view.owned() ? text("rysquests.receiver.coins.mine", Sidebar.full(view.wallet()))
                : text("rysquests.receiver.coins.owner", view.ownerName(), Sidebar.full(view.wallet()));
        String treasury = text("rysquests.receiver.coins.treasury", view.ownerInTeam() ? Sidebar.full(view.treasuryBalance()) : "0");
        List<Segments.Item> items = List.of(Segments.item(own),
                new Segments.Item(treasury, null, !view.ownerInTeam(), view.ownerInTeam() ? null : Component.translatable("rysquests.receiver.coins.no_team")));
        int at = view.treasury() ? 1 : 0;
        float sw = Segments.width(items, at);
        ui.label(tr("rysquests.receiver.coins_to"), x, y + Segments.HEIGHT / 2f, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        Segments.render(ui, x + w - sw, y, items, at, index -> {
            if ((index == 1) != view.treasury()) {
                ask(view.program(), view.offer(), index == 1, false);
            }
        });
        return Segments.HEIGHT;
    }

    private void renderSale(Ui ui, ShopOffer offer, float x, float y, float w) {
        ReceiverView view = view();
        List<String> names = new ArrayList<>();
        List<String> amounts = new ArrayList<>();
        ShopOffer.Entry first = null;
        for (ShopOffer.Entry entry : offer.cost()) {
            if (!entry.isCurrency()) {
                first = first == null ? entry : first;
                names.add(entry.stack().getHoverName().getString());
                amounts.add(Sidebar.full(entry.amount()));
            }
        }
        float pen = y;
        pen += DetailPanel.rubric(ui, x, pen, w, tr("rysquests.receiver.trade"), null) + 6;
        pen += summary(ui, x, pen, w, List.of(
                new String[] {tr("rysquests.receiver.takes"), text("rysquests.receiver.takes_item", String.join(", ", names)), null},
                new String[] {tr("rysquests.receiver.lot"), text("rysquests.receiver.lot_value", String.join(" + ", amounts), ShopView.money(currency(), coins(offer))), null},
                new String[] {tr("rysquests.receiver.foreign_item"), tr("rysquests.receiver.foreign_item_value"), null})) + GAP;
        if (first != null) {
            long lot = first.amount();
            long held = view.buffered(first.id());
            pen += DetailPanel.rubric(ui, x, pen, w, tr("rysquests.receiver.buffer"), text("rysquests.receiver.buffer_value", Sidebar.full(held), Sidebar.full(lot))) + 6;
            ui.bar(x, pen, w, 6, 3, Math.min(1f, (float) held / Math.max(1, lot)), ui.c("text.secondary"));
            pen += 6 + GAP;
        }
        float rubricH = Ui.lineHeight(11.5f);
        ui.caps(tr("rysquests.receiver.since"), x, pen + rubricH / 2f, ui.c("text.dim"));
        String reset = tr("rysquests.receiver.reset");
        float resetW = ui.width(reset, 11.5f, Ui.REGULAR);
        boolean overReset = ui.over(x + w - resetW, pen, resetW, rubricH);
        ui.labelRight(reset, x + w, pen + rubricH / 2f, 11.5f, Ui.REGULAR, ui.c(overReset ? "text.primary" : "text.muted"));
        ui.hit(x + w - resetW - 4, pen - 4, resetW + 8, rubricH + 8, () -> ask(view.program(), view.offer(), view.treasury(), true));
        pen += rubricH + 6;
        String earned = (view.earned() > 0 ? "+" : "") + ShopView.money(currency(), view.earned());
        summary(ui, x, pen, w, List.of(
                new String[] {tr("rysquests.receiver.accepted"), text("rysquests.receiver.pieces", Sidebar.full(view.accepted())), null},
                new String[] {tr("rysquests.receiver.lots"), Sidebar.full(view.lots()), null},
                new String[] {tr(view.treasury() ? "rysquests.receiver.earned_treasury" : "rysquests.receiver.earned"), earned, view.earned() > 0 ? "state.ok" : null}));
    }

    private void renderDelivery(Ui ui, ShopOffer offer, float x, float y, float w) {
        Map<String, Long> delivered = delivered(offer);
        float pen = y;
        pen += DetailPanel.rubric(ui, x, pen, w, text("rysquests.receiver.delivery_title", ShopView.name(offer)), percent(offer) + "%") + 10;
        for (Map.Entry<String, Long> row : offer.deliveryRows().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(row.getKey());
            ItemStack stack = id == null ? ItemStack.EMPTY : TaskText.stackOf(id);
            ui.cell(stack, x, pen, 28, 28, 6, 16, ui.c("slot.bg"), ui.c("slot.border"));
            float cy = pen + 14;
            long got = delivered.getOrDefault(row.getKey(), 0L);
            String of = text("rysquests.receiver.of", Sidebar.full(row.getValue()));
            String count = Sidebar.full(got);
            float ofW = ui.width(of, 12, Ui.REGULAR);
            float countW = ui.width(count, 12, Ui.SEMIBOLD);
            ui.labelRight(of, x + w, cy, 12, Ui.REGULAR, ui.c("text.muted"));
            ui.labelRight(count, x + w - ofW - 4, cy, 12, Ui.SEMIBOLD, ui.c(got >= row.getValue() ? "state.ok" : "text.primary"));
            ui.labelFit(stack.getHoverName().getString(), x + 28 + 10, cy, w - 38 - ofW - countW - 16, 12.5f, Ui.MEDIUM, ui.c("text.primary"));
            pen += 28 + 14;
        }
        pen += GAP - 14 + 6;
        pen += summary(ui, x, pen, w, List.of(
                new String[] {tr("rysquests.receiver.takes"), tr("rysquests.receiver.delivery.takes_value"), null},
                new String[] {tr("rysquests.receiver.delivery.row_full"), tr("rysquests.receiver.delivery.row_full_value"), null},
                new String[] {tr("rysquests.receiver.delivery.when_full"), tr("rysquests.receiver.delivery.when_full_value"), null})) + GAP;
        note(ui, x, pen, w, tr("rysquests.receiver.trial_note"));
    }

    /** Rows of a label and a value on the status plate (mockup {@code .sum}). @return its height */
    private static float summary(Ui ui, float x, float y, float w, List<String[]> rows) {
        float rowH = Ui.lineHeight(12.5f);
        float h = 20 + rows.size() * rowH + Math.max(0, rows.size() - 1) * 6;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        float cy = y + 10 + rowH / 2f;
        for (String[] row : rows) {
            float valueW = Math.min(ui.width(row[1], 12.5f, Ui.MEDIUM), w * 0.65f);
            ui.labelFit(row[0], x + 12, cy, w - 24 - valueW - 8, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            ui.labelFit(row[1], x + w - 12 - valueW, cy, valueW, 12.5f, Ui.MEDIUM, ui.c(row[2] != null ? row[2] : "text.primary"));
            cy += rowH + 6;
        }
        return h;
    }

    /** A plate with the round "i" and a few lines (mockup {@code .note}). */
    private static void note(Ui ui, float x, float y, float w, String line) {
        float textX = x + 12 + 15 + 10;
        float lineH = Math.round(12.5f * 1.45f);
        List<String> lines = ui.wrap(line, w - (textX - x) - 12, 12.5f, Ui.REGULAR, 4);
        float h = 16 + lines.size() * lineH;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        ui.info(x + 12, y + 8 + (lineH - 15) / 2f, 15, ui.c("text.muted"));
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), textX, y + 8 + i * lineH + lineH / 2f, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
        }
    }

    private void renderFooter(Ui ui, int x, int y) {
        ui.fillCorners(x + 1, y, W - 2, FOOT - 1, 13, ui.c("status.bg"), false, false, true, true);
        ui.rect(x, y, W, 1, ui.c("panel.border"));
        ReceiverView view = view();
        ReceiverStatus status = view.status();
        float cy = y + FOOT / 2f;
        String lamp = switch (status.lamp()) {
            case GREEN -> "state.ok";
            case YELLOW -> "state.warn";
            case RED -> "state.bad";
            case OFF -> "text.dim";
        };
        ui.circle(x + 16, cy - 4.5f, 9, ui.c(lamp));
        String note = tr(selling() ? "rysquests.receiver.footer.sell" : "rysquests.receiver.footer.delivery");
        float noteW = ui.width(note, 12.5f, Ui.REGULAR);
        ui.labelRight(note, x + W - 16, cy, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        String line = status == ReceiverStatus.WORKING && !selling()
                ? text("rysquests.receiver.status.working_delivery", Plural.full("rysquests.plural.item", view.accepted()))
                : tr(status.key());
        float textX = x + 16 + 9 + 9;
        float room = W - (textX - x) - 16 - noteW - 16;
        ui.labelFit(line, textX, cy, room, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
        if (ui.width(line, 12.5f, Ui.REGULAR) > room) {
            ui.tooltipIfOver(textX, y, room, FOOT, Component.literal(line));
        }
    }

    // --- input ---

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (button == 0 && kitHits.click(mouseX, mouseY, button)) {
            return true;
        }
        int x = (int) Math.round(mouseX * 2);
        int y = (int) Math.round(mouseY * 2);
        List<Ui.Hit> list = button == 0 ? hits : button == 1 ? rightHits : List.of();
        for (int i = list.size() - 1; i >= 0; i--) {
            Ui.Hit hit = list.get(i);
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                hit.action().run();
                if (search != null && !search.isMouseOver(mouseX, mouseY)) {
                    search.setFocused(false);
                    setFocused(null);
                }
                return true;
            }
        }
        if (button == 1 && hovered != null) {
            Recipes.showUsages(hovered);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && search != null && !search.isMouseOver(mouseX, mouseY) && search.isFocused()) {
            search.setFocused(false);
            setFocused(null);
        }
        return super.onClick(mouseX, mouseY, button);
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX * 2;
        double y = mouseY * 2;
        if (x >= windowX && x < windowX + LEFT && y >= listTop && y < listBottom) {
            listScroll = (float) Math.max(0, Math.min(listMax, listScroll - scrollY * ROW_STEP));
            return true;
        }
        return super.onScroll(mouseX, mouseY, scrollX, scrollY);
    }

    // --- for the in-game autotest ---

    /** The status line as the player reads it. */
    public String statusForTest() {
        return view().status().name();
    }
}
