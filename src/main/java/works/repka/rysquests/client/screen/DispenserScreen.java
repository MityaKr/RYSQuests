package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import works.repka.rysquests.block.ComparatorMode;
import works.repka.rysquests.block.Dispenser;
import works.repka.rysquests.block.DispenserMenu;
import works.repka.rysquests.block.DispenserSettings;
import works.repka.rysquests.block.DispenserView;
import works.repka.rysquests.block.ModBlocks;
import works.repka.rysquests.block.ReceiverStatus;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.network.DispenserConfigPayload;

/**
 * The window of the dispenser (shop plan, section 4.2, mockup 06): on the left the trades a dispenser can buy, with a search; on the
 * right where the coins come from, where the goods go, the limits, the lot and the counters, and the block's own settings; the status
 * line with its lamp at the bottom. Every number is in mockup pixels, as in the book.
 *
 * <p>The window shows what the server sent ({@link DispenserView}); a change sends the whole settings, which the server checks and
 * sends back within a tick. A number field is not overwritten while the player types in it.
 */
public final class DispenserScreen extends ScaledScreen implements MenuAccess<DispenserMenu> {
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
    private static final int VALUE_W = 95;
    private static final int VALUE_H = 30;
    private static final float DIM = 0.55f;
    /** The output choices in the order of the list: the own slot first, then the sides. */
    private static final List<Optional<Direction>> OUTPUTS = List.of(Optional.empty(), Optional.of(Direction.DOWN), Optional.of(Direction.UP),
            Optional.of(Direction.NORTH), Optional.of(Direction.SOUTH), Optional.of(Direction.WEST), Optional.of(Direction.EAST));

    private final DispenserMenu menu;
    private final List<Ui.Hit> hits = new ArrayList<>();
    private final List<Ui.Hit> rightHits = new ArrayList<>();
    private final Kit.Hits kitHits = new Kit.Hits();
    private final Kit.Dropdown outputList = new Kit.Dropdown();
    private @Nullable Field search;
    private @Nullable Field budgetField;
    private @Nullable Field reserveField;
    private @Nullable Field stockField;
    /** Set while the window writes the server's numbers into the fields, so that this writing is not sent back. */
    private boolean syncing;
    private @Nullable ItemStack hovered;
    private float listScroll;
    private float listMax;
    private int listTop;
    private int listBottom;
    private int windowX;

    public DispenserScreen(DispenserMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public DispenserMenu getMenu() {
        return menu;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private DispenserView view() {
        return menu.view();
    }

    private DispenserSettings settings() {
        return view().settings();
    }

    @Override
    protected void init() {
        super.init();
        search = new Field(80, 10);
        search.setHint(Component.translatable("rysquests.receiver.search"));
        search.setResponder(value -> listScroll = 0);
        addWidget(search);
        budgetField = numberField(value -> settings().withBudget(settings().budgetOn(), value));
        reserveField = numberField(value -> settings().withReserve(settings().reserveOn(), value));
        stockField = numberField(value -> settings().withStock(settings().stockOn(), value));
    }

    /** A field that takes only digits and sends the settings it makes at every change the player types. */
    private Field numberField(LongFunction<DispenserSettings> with) {
        Field field = new Field(40, 10);
        field.setMaxLength(13);
        field.setFilter(value -> value.chars().allMatch(Character::isDigit));
        field.setResponder(value -> {
            if (!syncing) {
                ask(with.apply(value.isEmpty() ? 0 : Long.parseLong(value)), false);
            }
        });
        addWidget(field);
        return field;
    }

    private List<Field> numberFields() {
        List<Field> out = new ArrayList<>();
        for (Field field : new Field[] {budgetField, reserveField, stockField}) {
            if (field != null) {
                out.add(field);
            }
        }
        return out;
    }

    private boolean typing() {
        if (search != null && search.isFocused()) {
            return true;
        }
        return numberFields().stream().anyMatch(Field::isFocused);
    }

    private void unfocusAll() {
        if (search != null) {
            search.setFocused(false);
        }
        numberFields().forEach(field -> field.setFocused(false));
        setFocused(null);
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
        if (outputList.keyPressed(key)) {
            return true;
        }
        boolean typing = typing();
        if (typing && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            unfocusAll();
            return true;
        }
        if (!typing && minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // --- what the server is asked ---

    private void ask(DispenserSettings next, boolean reset) {
        PacketDistributor.sendToServer(new DispenserConfigPayload(menu.containerId, next, reset));
    }

    private void choose(ShopOffer offer) {
        ask(settings().withOffer(Optional.of(offer.id())), false);
    }

    // --- the trades ---

    /** Closed to the team: a stage it lacks, or a quest it has not done. The block could not buy such a trade either. */
    private static boolean locked(ShopOffer offer) {
        boolean stage = offer.stage().filter(required -> !ClientProgress.hasStage(required)).isPresent();
        return stage || offer.quest().filter(quest -> !ClientProgress.get(quest).everDone()).isPresent();
    }

    private static List<ShopOffer> open() {
        List<ShopOffer> out = new ArrayList<>();
        for (ShopOffer offer : ClientBook.current().offers().values()) {
            if (Dispenser.fits(offer) && !locked(offer)) {
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
        ShopOffer offer = settings().offer().map(id -> ClientBook.current().offers().get(id)).orElse(null);
        return offer != null && Dispenser.fits(offer) ? offer : null;
    }

    private static String outputLabel(Optional<Direction> side) {
        return tr("rysquests.dispenser.output." + side.map(Direction::getSerializedName).orElse("self"));
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
        syncFields();
        Ui ui = new Ui(g, mouseX, mouseY, hits, rightHits, kitHits);
        int x = (width * 2 - W) / 2;
        int windowH = windowH();
        int y = Math.max(0, (height * 2 - windowH) / 2);
        windowX = x;
        ui.shadow(x, y, W, windowH, 14, 1f);
        ui.box(x, y, W, windowH, 14, ui.c("panel.bg"), ui.c("panel.border"));
        renderHeader(ui, x, y);
        int bodyTop = y + HEAD;
        int bodyH = windowH - HEAD - FOOT;
        ui.rect(x + LEFT, bodyTop, 1, bodyH, ui.c("panel.border"));
        renderList(ui, x, bodyTop, LEFT, bodyH);
        float rightX = x + LEFT + 1 + RIGHT_PAD_X;
        float rightW = W - LEFT - 1 - RIGHT_PAD_X * 2;
        float end = renderRight(ui, rightX, bodyTop + RIGHT_PAD_Y, rightW);
        float settingsY = Math.max(end + GAP, bodyTop + bodyH - RIGHT_PAD_Y - BlockSettings.height());
        DispenserSettings now = settings();
        BlockSettings.render(ui, rightX, settingsY, rightW, now.comparator(), tr("rysquests.dispenser.comparator.fill"), now.notices(),
                mode -> ask(settings().withComparator(mode), false), () -> ask(settings().withNotices(!settings().notices()), false));
        renderFooter(ui, x, y + windowH - FOOT);
        outputMenu(ui);
        hovered = ui.hoveredStack;
        if (outputList.open) {
            return;
        }
        if (ui.hoveredStack != null) {
            g.renderTooltip(font, ui.hoveredStack, mouseX, mouseY);
        } else if (ui.hoveredText != null) {
            g.renderTooltip(font, ui.hoveredText, mouseX, mouseY);
        }
    }

    /** The server's numbers into the fields the player is not typing in. */
    private void syncFields() {
        DispenserSettings s = settings();
        syncing = true;
        sync(budgetField, s.budget());
        sync(reserveField, s.reserve());
        sync(stockField, s.stock());
        syncing = false;
    }

    private static void sync(@Nullable Field field, long value) {
        String text = Long.toString(value);
        if (field != null && !field.isFocused() && !field.getValue().equals(text)) {
            field.setValue(text);
        }
    }

    private void renderHeader(Ui ui, int x, int y) {
        float cy = y + HEAD / 2f;
        ui.plate(x + 16, cy - 18, 36, 36, 9);
        ui.item(new ItemStack(ModBlocks.DISPENSER_ITEM.get()), x + 16 + 6, cy - 12, 24);
        float textX = x + 16 + 36 + 12;
        ui.label(tr("block.rysquests.shop_dispenser"), textX, cy - 8, 16, Ui.DISPLAY, ui.c("text.primary"));
        ui.label(tr("rysquests.dispenser.sub"), textX, cy + 10, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        int closeX = x + W - 14 - 36;
        Kit.close(ui.g, ui.theme, kitHits, Math.round(closeX / 2f), Math.round((cy - 18) / 2f), ui.mouseX / 2, ui.mouseY / 2, this::onClose);
        DispenserView view = view();
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
        ui.caps(tr("rysquests.dispenser.list"), left, rubricCy, ui.c("text.dim"));
        List<ShopOffer> open = open();
        List<ShopOffer> shown = shown(open);
        listTop = Math.round(rubricCy + Ui.lineHeight(11) / 2f + 6);
        listBottom = y + h - LIST_PAD;
        if (shown.isEmpty()) {
            boolean filtered = !open.isEmpty();
            EmptyFilter.render(ui, left, listTop, inner, listBottom - listTop, tr(filtered ? "rysquests.receiver.list.nothing_found" : "rysquests.dispenser.list.empty"),
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
        ShopOffer.Entry entry = Dispenser.goods(offer);
        ItemStack stack = entry == null ? ItemStack.EMPTY : entry.stack();
        float iconY = y + (ROW_H - 30) / 2f;
        ui.plate(x + 6, iconY, 30, 30, 6);
        ui.item(stack, x + 6 + 7, iconY + 7, 16);
        ui.hoverItem(stack, x + 6, iconY, 30, 30);
        if (entry != null) {
            ui.count(entry.amount(), x + 6 + 30, iconY + 30);
        }
        float textX = x + 6 + 30 + 11;
        String sub = offer.category().map(DispenserScreen::tr).orElse("");
        String right = Sidebar.full(Dispenser.price(offer));
        float rightW = ui.width(right, 12, Ui.REGULAR) + 8 + 10;
        float textW = w - (textX - x) - 10 - rightW - 8;
        ui.labelFit(ShopView.name(offer), textX, y + ROW_H / 2f - (sub.isEmpty() ? 0 : 8), textW, 13, Ui.MEDIUM, ui.c("text.primary"));
        if (!sub.isEmpty()) {
            ui.labelFit(sub, textX, y + ROW_H / 2f + 9, textW, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        // The coin first and the price after it, as mockup 06 lays a purchase out.
        float priceRight = x + w - 10;
        float coinX = priceRight - ui.width(right, 12, Ui.REGULAR) - 8 - 10;
        ui.circle(coinX, y + ROW_H / 2f - 5, 10, ui.c("coin"));
        ui.labelRight(right, priceRight, y + ROW_H / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
        if (!chosen) {
            ui.hit(x, y, w, ROW_H, () -> choose(offer));
        }
    }

    /**
     * The window's height: the mockup's 608, taller when the right column with the block's settings under it would reach into the
     * bottom padding (the counters grow a row "В своём слоте" with the own slot). Measured with the same numbers it is drawn with,
     * rounded up to whole GUI pixels.
     */
    private int windowH() {
        float limits = Ui.lineHeight(11.5f) + 6 + 3 * BlockSettings.ROW_STEP + BlockSettings.ROW_H;
        ShopOffer offer = selected();
        float tail = offer == null ? 20 : summaryHeight(counterRows(offer).size());
        float column = Segments.HEIGHT + GAP + Kit.DROP_H * 2 + GAP + limits + GAP + tail;
        int need = (int) Math.ceil(HEAD + RIGHT_PAD_Y + column + GAP + BlockSettings.height() + RIGHT_PAD_Y + FOOT);
        return Math.max(H, need + (need & 1));
    }

    /** @return where the column's content ends */
    private float renderRight(Ui ui, float x, float y, float w) {
        float pen = y;
        pen += payRow(ui, x, pen, w) + GAP;
        pen += outputRow(ui, x, pen, w) + GAP;
        pen += limits(ui, x, pen, w) + GAP;
        ShopOffer offer = selected();
        if (offer == null) {
            ui.label(tr("rysquests.receiver.pick"), x, pen + 10, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            return pen + 20;
        }
        return pen + counters(ui, offer, x, pen, w);
    }

    /** "Платить из": the owner's wallet or the team's treasury, with what each holds. @return its height */
    private float payRow(Ui ui, float x, float y, float w) {
        DispenserView view = view();
        boolean mine = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(view.owner());
        String own = mine || !view.owned() ? text("rysquests.receiver.coins.mine", Sidebar.full(view.wallet()))
                : text("rysquests.receiver.coins.owner", view.ownerName(), Sidebar.full(view.wallet()));
        String treasury = text("rysquests.receiver.coins.treasury", view.ownerInTeam() ? Sidebar.full(view.treasuryBalance()) : "0");
        boolean on = settings().treasury();
        Component why = !view.ownerInTeam() ? Component.translatable("rysquests.receiver.coins.no_team")
                : view.mayTreasury() || on ? null : Component.translatable("rysquests.dispenser.pay.no_right");
        List<Segments.Item> items = List.of(Segments.item(own), new Segments.Item(treasury, null, why != null, why));
        int at = on ? 1 : 0;
        float sw = Segments.width(items, at);
        ui.label(tr("rysquests.dispenser.pay_from"), x, y + Segments.HEIGHT / 2f, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        Segments.render(ui, x + w - sw, y, items, at, index -> {
            if ((index == 1) != settings().treasury()) {
                ask(settings().withTreasury(index == 1), false);
            }
        });
        return Segments.HEIGHT;
    }

    /** "Отдавать в": the own slot or a side, as a list. @return its height */
    private float outputRow(Ui ui, float x, float y, float w) {
        float h = Kit.DROP_H * 2;
        ui.label(tr("rysquests.dispenser.output"), x, y + h / 2f, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        String label = outputLabel(settings().output());
        int maxW = Math.round(w / 2f / 2f);
        int dw = Kit.dropdownWidth(label, maxW);
        Kit.dropdown(ui.g, ui.theme, kitHits, outputList, Math.round((x + w) / 2f) - dw, Math.round(y / 2f), maxW, label, settings().output().isPresent(),
                ui.mouseX / 2, ui.mouseY / 2);
        return h;
    }

    /** The open list of outputs, last in the frame, over everything. */
    private void outputMenu(Ui ui) {
        if (!outputList.open) {
            return;
        }
        DispenserView view = view();
        List<Kit.Option> options = new ArrayList<>();
        for (Optional<Direction> side : OUTPUTS) {
            String note = side.isPresent() && !view.hasInventory(side.get()) ? tr("rysquests.dispenser.output.empty") : "";
            options.add(new Kit.Option(outputLabel(side), note, true));
        }
        Kit.dropdownList(ui.g, ui.theme, kitHits, outputList, options, OUTPUTS.indexOf(settings().output()), 110, ui.mouseX / 2, ui.mouseY / 2, index -> {
            outputList.close();
            ask(settings().withOutput(OUTPUTS.get(index)), false);
        });
    }

    /** "Ограничения": the three limits and the redstone switch (mockup 06). @return its height */
    private float limits(Ui ui, float x, float y, float w) {
        DispenserSettings s = settings();
        DispenserView view = view();
        float pen = y;
        float rubricH = Ui.lineHeight(11.5f);
        ui.caps(tr("rysquests.dispenser.limits"), x, pen + rubricH / 2f, ui.c("text.dim"));
        pen += rubricH + 6;
        float room = w - 36 - 12 - VALUE_W - 12;
        String spent = text("rysquests.dispenser.budget.sub", Sidebar.full(view.spent()));
        BlockSettings.switchRow(ui, x, pen, room, s.budgetOn(), tr("rysquests.dispenser.budget"), spent, () -> ask(settings(), true),
                () -> ask(settings().withBudget(!settings().budgetOn(), settings().budget()), false));
        valueBox(ui, budgetField, x + w - VALUE_W, pen, s.budgetOn(), null);
        pen += BlockSettings.ROW_STEP;
        BlockSettings.switchRow(ui, x, pen, room, s.reserveOn(), tr("rysquests.dispenser.reserve"), tr("rysquests.dispenser.reserve.sub"), null,
                () -> ask(settings().withReserve(!settings().reserveOn(), settings().reserve()), false));
        valueBox(ui, reserveField, x + w - VALUE_W, pen, s.reserveOn(), null);
        pen += BlockSettings.ROW_STEP;
        BlockSettings.switchRow(ui, x, pen, room, s.stockOn(), tr("rysquests.dispenser.stock"),
                tr(s.output().isPresent() ? "rysquests.dispenser.stock.sub" : "rysquests.dispenser.stock.sub_self"), null,
                () -> ask(settings().withStock(!settings().stockOn(), settings().stock()), false));
        valueBox(ui, stockField, x + w - VALUE_W, pen, s.stockOn(), tr("rysquests.dispenser.pcs"));
        pen += BlockSettings.ROW_STEP;
        BlockSettings.switchRow(ui, x, pen, room, s.redstone(), tr("rysquests.dispenser.redstone"), tr("rysquests.dispenser.redstone.sub"), null,
                () -> ask(settings().withRedstone(!settings().redstone()), false));
        ui.box(x + w - VALUE_W, pen, VALUE_W, VALUE_H, 8, ui.c("input.bg"), ui.c("input.border"));
        pen += BlockSettings.ROW_H;
        return pen - y;
    }

    /** The plate of a number on the right of a limit's row, with its field inside; dim while the limit is off. */
    private static void valueBox(Ui ui, @Nullable Field field, float x, float y, boolean on, @Nullable String unit) {
        boolean focused = field != null && field.isFocused();
        ui.box(x, y, VALUE_W, VALUE_H, 8, ui.c("input.bg"), focused ? ui.c("card.border") : ui.c("input.border"));
        float unitW = unit == null ? 0 : ui.width(unit, 11, Ui.REGULAR) + 6;
        if (unit != null) {
            ui.labelRight(unit, x + VALUE_W - 10, y + VALUE_H / 2f, 11, Ui.REGULAR, ui.c("text.muted"));
        }
        if (field != null) {
            field.setX(Math.round((x + 10) / 2f));
            field.setY(Math.round((y + VALUE_H / 2f) / 2f) - 5);
            field.setWidth(Math.round((VALUE_W - 20 - unitW) / 2f));
            field.setTextColor(ui.c(on ? "text.primary" : "text.dim"));
            field.render(ui.g, ui.mouseX / 2, ui.mouseY / 2, 0);
        }
    }

    /** The lot, the counters since the last reset and what lies in the own slot. @return its height */
    private float counters(Ui ui, ShopOffer offer, float x, float y, float w) {
        return summary(ui, x, y, w, counterRows(offer));
    }

    /** The rows of the counters: the lot, bought, spent, and what lies in the own slot when the goods go there or something is left. */
    private List<String[]> counterRows(ShopOffer offer) {
        DispenserView view = view();
        ShopOffer.Entry goods = Dispenser.goods(offer);
        String what = goods == null ? "" : goods.stack().getHoverName().getString();
        String lot = text("rysquests.dispenser.lot_value", Sidebar.full(goods == null ? 0 : goods.amount()), what, ShopView.money(view.currency(), Dispenser.price(offer)));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {tr("rysquests.receiver.lot"), lot, null});
        rows.add(new String[] {tr("rysquests.dispenser.bought"), Plural.full("rysquests.plural.lot", view.lots()), null});
        rows.add(new String[] {tr(settings().treasury() ? "rysquests.dispenser.spent_treasury" : "rysquests.dispenser.spent"), ShopView.money(view.currency(), view.spent()), null});
        if (settings().output().isEmpty() || view.held() > 0) {
            rows.add(new String[] {tr("rysquests.dispenser.held"), text("rysquests.receiver.pieces", Sidebar.full(view.held())), null});
        }
        return rows;
    }

    /** The height of the status plate with this many rows. */
    private static float summaryHeight(int rows) {
        return 20 + rows * Ui.lineHeight(12.5f) + Math.max(0, rows - 1) * 6;
    }

    /** Rows of a label and a value on the status plate (mockup {@code .sum}). @return its height */
    private static float summary(Ui ui, float x, float y, float w, List<String[]> rows) {
        float rowH = Ui.lineHeight(12.5f);
        float h = summaryHeight(rows.size());
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

    private void renderFooter(Ui ui, int x, int y) {
        ui.fillCorners(x + 1, y, W - 2, FOOT - 1, 13, ui.c("status.bg"), false, false, true, true);
        ui.rect(x, y, W, 1, ui.c("panel.border"));
        ReceiverStatus status = view().status();
        float cy = y + FOOT / 2f;
        String lamp = switch (status.lamp()) {
            case GREEN -> "state.ok";
            case YELLOW -> "state.warn";
            case RED -> "state.bad";
            case OFF -> "text.dim";
        };
        ui.circle(x + 16, cy - 4.5f, 9, ui.c(lamp));
        String note = tr("rysquests.dispenser.footer");
        float noteW = ui.width(note, 12.5f, Ui.REGULAR);
        ui.labelRight(note, x + W - 16, cy, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        String line = tr(status.key("dispenser"));
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
        if (kitHits.click(mouseX, mouseY, button)) {
            unfocusAll();
            return true;
        }
        int x = (int) Math.round(mouseX * 2);
        int y = (int) Math.round(mouseY * 2);
        List<Ui.Hit> list = button == 0 ? hits : button == 1 ? rightHits : List.of();
        for (int i = list.size() - 1; i >= 0; i--) {
            Ui.Hit hit = list.get(i);
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                hit.action().run();
                unfocusAll();
                return true;
            }
        }
        if (button == 1 && hovered != null) {
            Recipes.showUsages(hovered);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boolean overField = search != null && search.isMouseOver(mouseX, mouseY) || numberFields().stream().anyMatch(field -> field.isMouseOver(mouseX, mouseY));
            if (!overField && typing()) {
                unfocusAll();
            }
        }
        return super.onClick(mouseX, mouseY, button);
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (outputList.scrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
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

    /** Opens the list of outputs, for the autotest's screenshot. */
    public void openOutputsForTest() {
        outputList.open = true;
    }

    /** The comparator mode the window shows, for the autotest. */
    public ComparatorMode comparatorForTest() {
        return settings().comparator();
    }
}
