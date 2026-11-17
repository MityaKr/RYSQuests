package works.repka.rysquests.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ShopOffer;

/**
 * The editor's "Магазин" (picture 39): the offers as the player's grid with "+ Товар" last, "Купить | Продать" and the same "Раздел"
 * as the player has; on the right the chosen offer: name, who buys, what the player gets, the price, the section, when it opens and
 * the limit. An offer is removed at once rather than archived (plan, section 6).
 */
final class ShopEditorView extends EditorPage {
    private static final int PANEL_W = 360;
    private static final int CARD_H = 94;
    private static final String COIN = "coin";

    private boolean selling;
    @Nullable
    private String section;
    @Nullable
    private ResourceLocation chosen;
    private final Scroll scroll = new Scroll();
    private final Scroll panelScroll = new Scroll();

    ShopEditorView(BookScreen screen) {
        super(screen);
    }

    void select(ResourceLocation id) {
        chosen = id;
        fields.clear();
    }

    private static int price(ShopOffer offer) {
        List<ShopOffer.Entry> side = offer.isSale() ? offer.gives() : offer.cost();
        return side.stream().filter(ShopOffer.Entry::isCurrency).mapToInt(ShopOffer.Entry::amount).sum();
    }

    private static List<ShopOffer.Entry> items(ShopOffer offer) {
        return (offer.isSale() ? offer.cost() : offer.gives()).stream().filter(e -> !e.isCurrency()).toList();
    }

    private static ShopOffer with(ShopOffer o, Optional<String> title, Optional<ResourceLocation> icon, Optional<String> category, List<ShopOffer.Entry> cost,
            List<ShopOffer.Entry> gives, Optional<ShopOffer.Limit> limit, Optional<ResourceLocation> quest) {
        return new ShopOffer(o.id(), title, icon, category, cost, gives, limit, o.stage(), o.stageTitle(), quest);
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        EditorBook editor = book();
        QuestBook book = editor.view();
        int mw = chosen == null ? w : w - PANEL_W;
        List<ShopOffer> offers = book.offers().values().stream().filter(o -> o.isSale() == selling)
                .filter(o -> section == null || o.category().filter(section::equals).isPresent()).toList();
        float px = x + 16;
        float pw = mw - 32;
        float pen = y + 12;
        List<Segments.Item> modes = List.of(Segments.item(tr("rysquests.ed.shop.buy")), Segments.item(tr("rysquests.ed.shop.sell")));
        Segments.render(ui, px, pen, modes, selling ? 1 : 0, i -> {
            selling = i == 1;
            scroll.reset();
        });
        // the sections: "Все", the list, "+ раздел"
        Set<String> sections = new LinkedHashSet<>();
        book.offers().values().forEach(o -> o.category().ifPresent(sections::add));
        float right = px + pw;
        float popY = pen + 36;
        String add = tr("rysquests.ed.shop.add_section");
        float aw = EdUi.buttonWidth(ui, add, Icons.Shape.PLUS, true);
        right -= aw;
        float addX = right;
        EdUi.button(ui, add, Icons.Shape.PLUS, right, pen + 3, true, false, false, () -> newSection(addX, popY));
        String label = section == null ? tr("rysquests.ed.shop.section") : editor.text(section);
        float dw = EdUi.buttonWidth(ui, label, Icons.Shape.CHEVRON_DOWN, true);
        right -= dw + 6;
        float ddX = right;
        List<String> list = new ArrayList<>(sections);
        EdUi.button(ui, label, Icons.Shape.CHEVRON_DOWN, right, pen + 3, true, section != null, false, () -> choices.toggle("section", ddX, popY, 240,
                list.stream().map(editor::text).toList(), section == null ? -1 : list.indexOf(section), i -> section = list.get(i)));
        String all = tr("rysquests.ed.shop.all");
        float allW = EdUi.chipsWidth(ui, List.of(all));
        right -= allW + 6;
        EdUi.chips(ui, right, pen + 3, List.of(all), section == null ? 0 : -1, i -> section = null);
        pen += 34 + 14;
        // the grid
        int columns = 4;
        float gap = 10;
        float cw = (pw - gap * (columns - 1)) / columns;
        ui.clip(x, Math.round(pen), mw, Math.round(y + h - pen));
        int offset = scroll.offset();
        float top = pen;
        pen -= offset;
        int i = 0;
        for (ShopOffer offer : offers) {
            float cx = px + (i % columns) * (cw + gap);
            float cy = pen + (i / columns) * (CARD_H + gap);
            List<ShopOffer.Entry> things = items(offer);
            ItemStack icon = offer.icon().map(TaskText::stackOf).orElseGet(() -> things.isEmpty() ? ItemStack.EMPTY : things.getFirst().item().map(TaskText::stackOf).orElse(ItemStack.EMPTY));
            int count = things.isEmpty() ? 1 : things.getFirst().amount();
            String meta = offer.limit().map(limit -> tr("rysquests.ed.shop.limit_short." + limit.reset().getSerializedName(), limit.count())
                    + (limit.per() == ShopOffer.Per.TEAM ? tr("rysquests.shop.limit_long.team") : "")).orElse(null);
            ResourceLocation id = offer.id();
            GridCard.of(offer.title().orElse(things.isEmpty() ? id.getPath() : EdText.item(things.getFirst().item().orElse(id))))
                    .icon(icon, count).meta(meta).price(price(offer), false).selected(id.equals(chosen)).onClick(() -> select(id)).render(ui, cx, cy, cw, CARD_H);
            ui.icon(Icons.Shape.GRIP, cx + cw - 22, cy + CARD_H - 22, 12, ui.c("text.dim"));
            i++;
        }
        float cx = px + (i % columns) * (cw + gap);
        float cy = pen + (i / columns) * (CARD_H + gap);
        boolean over = ui.over(cx, cy, cw, CARD_H);
        ui.dashed(cx, cy, cw, CARD_H, ui.c(over ? "text.secondary" : "button.border"));
        ui.icon(Icons.Shape.PLUS, cx + cw / 2f - 8, cy + CARD_H / 2f - 18, 16, ui.c(over ? "text.primary" : "text.secondary"));
        ui.labelCentered(tr("rysquests.ed.shop.add"), cx + cw / 2f, cy + CARD_H / 2f + 10, 12.5f, Ui.REGULAR, ui.c(over ? "text.primary" : "text.secondary"));
        ui.hit(cx, cy, cw, CARD_H, this::newOffer);
        float end = cy + CARD_H;
        ui.unclip();
        scroll.content(end + offset - top + 12, y + h - top);
        if (chosen != null) {
            ShopOffer offer = editor.data().offers().get(chosen);
            if (offer == null) {
                chosen = null;
            } else {
                renderPanel(ui, editor, offer, list, x + w - PANEL_W, y, PANEL_W, h);
            }
        }
        choices.render(ui, y + h);
    }

    private void renderPanel(Ui ui, EditorBook editor, ShopOffer offer, List<String> sections, int x, int y, int w, int h) {
        ui.rect(x, y, w, h, ui.c("panel.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float bx = x + 1 + 18;
        float bw = w - 1 - 36;
        float footY = y + h - EdUi.FOOT_H;
        ui.clip(x + 1, y, w - 1, Math.round(footY - y));
        int offset = panelScroll.offset();
        float pen = y + 16 - offset;
        DetailPanel.rubric(ui, bx, pen, bw - 30, tr("rysquests.ed.shop.offer"), null);
        EdUi.close(ui, bx + bw - 22, pen - 7, () -> chosen = null);
        pen += Ui.lineHeight(11.5f) + 10;
        List<ShopOffer.Entry> things = items(offer);
        ItemStack icon = offer.icon().map(TaskText::stackOf).orElseGet(() -> things.isEmpty() ? ItemStack.EMPTY : things.getFirst().item().map(TaskText::stackOf).orElse(ItemStack.EMPTY));
        EdUi.plate(ui, icon, Icons.Shape.DOT, bx, pen, 46);
        String shownTitle = offer.title().map(editor::text).orElse("");
        EdUi.input(ui, fields, "offer-title:" + offer.id(), bx + 56, pen + 6, bw - 56, EdUi.INPUT_H, 13, Ui.SEMIBOLD, shownTitle, tr("rysquests.ed.shop.title_hint"),
                value -> typeTitle(offer, value));
        pen += 46 + 12;
        // who buys
        ui.label(tr("rysquests.ed.shop.kind"), bx, pen + 17, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        List<Segments.Item> kinds = List.of(Segments.item(tr("rysquests.ed.shop.player_buys")), Segments.item(tr("rysquests.ed.shop.player_sells")));
        float sw = Segments.width(kinds, offer.isSale() ? 1 : 0);
        Segments.render(ui, bx + bw - sw, pen, kinds, offer.isSale() ? 1 : 0, i -> flip(offer, i == 1));
        pen += 34 + 12;
        pen += DetailPanel.rubric(ui, bx, pen, bw, tr(offer.isSale() ? "rysquests.ed.shop.player_gives" : "rysquests.ed.shop.player_gets"), String.valueOf(things.size())) + 6;
        for (int i = 0; i < things.size(); i++) {
            ShopOffer.Entry entry = things.get(i);
            int index = i;
            ItemStack stack = entry.stack();
            String sub = entry.components().map(patch -> tr("rysquests.ed.shop.with_data") + " " + componentNames(patch)).orElse(null);
            float rowY = pen;
            pen += EdUi.row(ui, bx, pen, bw, stack, null, EdText.item(entry.item().orElse(offer.id())) + " ×" + entry.amount(), sub, null, () -> removeItem(offer, index)) + 6;
            if (!stack.isEmpty()) {
                ui.hoverItem(stack, bx + 9, rowY + (sub == null ? EdUi.ROW_H - 4 : EdUi.ROW_H + 2) / 2f - 10, 20, 20);
            }
            // Only what the player gets can carry components: a player pays with plain items.
            if (!offer.isSale()) {
                EdUi.input(ui, fields, "offer-data:" + offer.id() + ":" + i, bx, pen, bw, EdUi.INPUT_H, 12, Ui.REGULAR, componentsJson(entry), tr("rysquests.ed.shop.data_hint"),
                        value -> setComponents(offer, index, value));
                pen += EdUi.INPUT_H + 6;
            }
        }
        pen += EdUi.add(ui, tr("rysquests.ed.shop.add_item"), bx, pen, bw, false, () -> addItemFromHand(offer)) + 12;
        // the price, with its coin
        ui.label(tr("rysquests.ed.shop.price"), bx, pen + 17, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        float iw = 110;
        ui.box(bx + bw - iw, pen, iw, EdUi.INPUT_H, 8, ui.c("input.bg"), ui.c("panel.border"));
        ui.circle(bx + bw - iw + 10, pen + 12.5f, 9, ui.c("coin"));
        TextArea priceArea = fields.area("offer-price:" + offer.id(), false, 13, Ui.REGULAR, String.valueOf(price(offer)), value -> setPrice(offer, value));
        priceArea.render(ui, bx + bw - iw + 26, pen + 8, iw - 36, 18, ui.inputText("text.primary"), "");
        ui.hit(bx + bw - iw, pen, iw, EdUi.INPUT_H, () -> fields.click(priceArea));
        pen += EdUi.INPUT_H + 12;
        pen += EdUi.label(ui, tr("rysquests.ed.shop.section_field"), bx, pen);
        float sy = pen;
        String current = offer.category().map(editor::text).orElse(tr("rysquests.ed.shop.no_section"));
        pen += EdUi.select(ui, bx, pen, bw, current, choices.isOpen("offer-section"), () -> {
            List<String> labels = new ArrayList<>();
            labels.add(tr("rysquests.ed.shop.no_section"));
            sections.forEach(s -> labels.add(editor.text(s)));
            choices.toggle("offer-section", bx, sy + EdUi.INPUT_H + 4, bw, labels, offer.category().map(c -> sections.indexOf(c) + 1).orElse(0), i -> {
                Optional<String> category = i == 0 ? Optional.empty() : Optional.of(sections.get(i - 1));
                editor.putOffer(with(offer, offer.title(), offer.icon(), category, offer.cost(), offer.gives(), offer.limit(), offer.quest()),
                        tr("rysquests.ed.log.offer_section", name(editor, offer), labels.get(i)));
            });
        }) + 12;
        pen += EdUi.label(ui, tr("rysquests.ed.shop.opens"), bx, pen);
        float oy = pen;
        String opens = offer.quest().map(q -> editor.view().quests().get(q)).map(q -> tr("rysquests.ed.shop.after_quest", editor.text(q.title()))).orElse(tr("rysquests.ed.shop.at_once"));
        pen += EdUi.select(ui, bx, pen, bw, opens, choices.isOpen("offer-opens"), () -> {
            List<Quest> points = opensAfter(editor.view(), offer.quest().orElse(null));
            List<String> labels = new ArrayList<>();
            labels.add(tr("rysquests.ed.shop.at_once"));
            points.forEach(q -> labels.add(tr("rysquests.ed.shop.after_quest", editor.text(q.title()))));
            int chosenIndex = offer.quest().map(id -> points.stream().map(Quest::id).toList().indexOf(id) + 1).orElse(0);
            choices.toggle("offer-opens", bx, oy + EdUi.INPUT_H + 4, bw, labels, chosenIndex, i -> editor.putOffer(with(offer, offer.title(), offer.icon(), offer.category(), offer.cost(),
                    offer.gives(), offer.limit(), i == 0 ? Optional.empty() : Optional.of(points.get(i - 1).id())), tr("rysquests.ed.log.offer_opens", name(editor, offer))));
        }) + 12;
        pen += EdUi.label(ui, tr("rysquests.ed.shop.limit"), bx, pen);
        List<Segments.Item> limits = List.of(Segments.item(tr("rysquests.ed.shop.limit.none")), Segments.item(tr("rysquests.ed.shop.limit.daily")),
                Segments.item(tr("rysquests.ed.shop.limit.weekly")), Segments.item(tr("rysquests.ed.shop.limit.never")));
        int sel = offer.limit().map(l -> switch (l.reset()) {
            case DAILY -> 1;
            case WEEKLY -> 2;
            default -> 3;
        }).orElse(0);
        Segments.render(ui, bx, pen, limits, sel, i -> setLimit(offer, i));
        pen += 34;
        if (offer.limit().isPresent()) {
            pen += 10;
            ui.label(tr("rysquests.ed.shop.limit_per"), bx, pen + 17, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            List<Segments.Item> pers = List.of(Segments.item(tr("rysquests.ed.shop.limit_per.player")), Segments.item(tr("rysquests.ed.shop.limit_per.team")));
            int per = offer.limit().get().per() == ShopOffer.Per.TEAM ? 1 : 0;
            float pw2 = Segments.width(pers, per);
            Segments.render(ui, bx + bw - pw2, pen, pers, per, i -> setLimitPer(offer, i == 1));
            pen += 34;
        }
        if (offer.limit().isPresent()) {
            pen += 10;
            ui.label(tr("rysquests.ed.shop.limit_count"), bx, pen + 17, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            ui.box(bx + bw - 80, pen, 80, EdUi.INPUT_H, 8, ui.c("input.bg"), ui.c("panel.border"));
            TextArea countArea = fields.area("offer-limit:" + offer.id(), false, 13, Ui.REGULAR, String.valueOf(offer.limit().get().count()), value -> setLimitCount(offer, value));
            countArea.render(ui, bx + bw - 70, pen + 8, 60, 18, ui.inputText("text.primary"), "");
            ui.hit(bx + bw - 80, pen, 80, EdUi.INPUT_H, () -> fields.click(countArea));
            pen += EdUi.INPUT_H;
        }
        ui.unclip();
        panelScroll.content(pen + offset - y - 16 + 12, footY - y - 16);
        float by = EdUi.foot(ui, x + 1, footY, w - 1);
        EdUi.ghost(ui, tr("rysquests.ed.shop.remove"), Icons.Shape.CROSS, bx - 14, by, () -> {
            editor.removeOffer(offer.id(), tr("rysquests.ed.log.offer_removed", name(editor, offer)));
            chosen = null;
        });
    }

    /** At most this many points in the list "Открывается": it does not scroll. */
    private static final int OPENS_MAX = 16;

    /**
     * What an offer can open after, from the data the book already has: the milestones, and for a chapter without one its last main
     * quest, in book order; the offer's own quest always among them. A book without milestones (a new one, a carried over FTB book)
     * so still has points to choose.
     */
    static List<Quest> opensAfter(QuestBook book, @Nullable ResourceLocation current) {
        List<Quest> out = new ArrayList<>();
        for (works.repka.rysquests.data.Chapter chapter : book.chapters().values()) {
            if (!chapter.isStory() || chapter.hidden()) {
                continue;
            }
            List<Quest> ordered = ChapterOrder.of(book, chapter.id()).stream().filter(q -> q.options().live()).toList();
            List<Quest> milestones = ordered.stream().filter(q -> q.options().milestone()).toList();
            if (!milestones.isEmpty()) {
                out.addAll(milestones);
            } else {
                ordered.stream().filter(q -> !q.options().optional()).reduce((first, second) -> second).ifPresent(out::add);
            }
        }
        if (out.size() > OPENS_MAX) {
            out = new ArrayList<>(out.subList(0, OPENS_MAX));
        }
        Quest own = current == null ? null : book.quests().get(current);
        if (own != null && out.stream().noneMatch(q -> q.id().equals(current))) {
            out.addFirst(own);
        }
        return out;
    }

    private static String name(EditorBook editor, ShopOffer offer) {
        return offer.title().map(editor::text).orElseGet(() -> items(offer).stream().findFirst().flatMap(ShopOffer.Entry::item).map(EdText::item).orElse(offer.id().getPath()));
    }

    // --- changes ---

    private void newOffer() {
        EditorBook editor = book();
        QuestBook data = editor.data();
        String namespace = EditorBook.namespace(data);
        int number = data.offers().size() + 1;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, "offer_" + number);
        while (data.offers().containsKey(id)) {
            number++;
            id = ResourceLocation.fromNamespaceAndPath(namespace, "offer_" + number);
        }
        var player = Minecraft.getInstance().player;
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        String item = held.isEmpty() ? "minecraft:bread" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int count = held.isEmpty() ? 1 : held.getCount();
        List<ShopOffer.Entry> coins = List.of(new ShopOffer.Entry(ShopOffer.Kind.CURRENCY, COIN, 10));
        List<ShopOffer.Entry> things = List.of(new ShopOffer.Entry(ShopOffer.Kind.ITEM, item, count));
        String titleKey = "shop." + namespace + ".offer_" + number;
        String title = TaskText.stackOf(ResourceLocation.parse(item)).getHoverName().getString();
        ShopOffer offer = new ShopOffer(id, Optional.of(titleKey), Optional.empty(), Optional.ofNullable(section), selling ? things : coins, selling ? coins : things,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        editor.edit("offer:" + id, tr("rysquests.ed.log.new_offer", title), d -> d.withOffer(offer).withText(titleKey, title));
        select(id);
    }

    private void newSection(float x, float y) {
        EditorBook editor = book();
        String namespace = EditorBook.namespace(editor.data());
        Set<String> taken = new LinkedHashSet<>();
        editor.data().offers().values().forEach(o -> o.category().ifPresent(taken::add));
        int number = taken.size() + 1;
        String key = "shop." + namespace + ".section_" + number;
        while (taken.contains(key)) {
            number++;
            key = "shop." + namespace + ".section_" + number;
        }
        section = key;
        String title = tr("rysquests.ed.shop.new_section", number);
        String made = key;
        editor.type("shop-section:" + key, "meta", tr("rysquests.ed.log.new_section", title), d -> d.withText(made, title));
        editor.endTyping();
    }

    private void typeTitle(ShopOffer offer, String value) {
        EditorBook editor = book();
        String key = offer.title().filter(EditorBook::isKey).orElse("shop." + offer.id().getNamespace() + "." + offer.id().getPath().replace('/', '.'));
        ShopOffer changed = with(offer, Optional.of(key), offer.icon(), offer.category(), offer.cost(), offer.gives(), offer.limit(), offer.quest());
        editor.type("offer-title:" + offer.id(), "offer:" + offer.id(), tr("rysquests.ed.log.offer_title", name(editor, offer)), d -> d.withOffer(changed).withText(key, value));
    }

    private void flip(ShopOffer offer, boolean sale) {
        if (offer.isSale() == sale) {
            return;
        }
        book().putOffer(with(offer, offer.title(), offer.icon(), offer.category(), offer.gives(), offer.cost(), offer.limit(), offer.quest()),
                tr("rysquests.ed.log.offer_kind", name(book(), offer)));
    }

    private void setPrice(ShopOffer offer, String value) {
        int amount;
        try {
            amount = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (amount <= 0) {
            return;
        }
        List<ShopOffer.Entry> coins = List.of(new ShopOffer.Entry(ShopOffer.Kind.CURRENCY, COIN, amount));
        List<ShopOffer.Entry> things = items(offer);
        ShopOffer changed = offer.isSale() ? with(offer, offer.title(), offer.icon(), offer.category(), things, coins, offer.limit(), offer.quest())
                : with(offer, offer.title(), offer.icon(), offer.category(), coins, things, offer.limit(), offer.quest());
        book().type("offer-price:" + offer.id(), "offer:" + offer.id(), tr("rysquests.ed.log.offer_price", name(book(), offer), amount), d -> d.withOffer(changed));
    }

    private void addItemFromHand(ShopOffer offer) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            works.repka.rysquests.client.ClientMessages.show("rysquests.ed.hand_empty", List.of(), false);
            return;
        }
        ItemStack held = player.getMainHandItem();
        List<ShopOffer.Entry> things = new ArrayList<>(items(offer));
        // What the player gets keeps the held item's components (a schematic with its file); what the player gives is always plain.
        Optional<DataComponentPatch> components = offer.isSale() || held.getComponentsPatch().isEmpty() ? Optional.empty() : Optional.of(held.getComponentsPatch());
        things.add(new ShopOffer.Entry(ShopOffer.Kind.ITEM, BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), held.getCount(), components));
        replaceItems(offer, things, tr("rysquests.ed.log.offer_item", name(book(), offer)));
    }

    private void removeItem(ShopOffer offer, int index) {
        List<ShopOffer.Entry> things = new ArrayList<>(items(offer));
        if (things.size() <= 1 || index >= things.size()) {
            return;
        }
        things.remove(index);
        replaceItems(offer, things, tr("rysquests.ed.log.offer_item_removed", name(book(), offer)));
    }

    private void replaceItems(ShopOffer offer, List<ShopOffer.Entry> things, String line) {
        List<ShopOffer.Entry> coins = (offer.isSale() ? offer.gives() : offer.cost()).stream().filter(ShopOffer.Entry::isCurrency).toList();
        ShopOffer changed = offer.isSale() ? with(offer, offer.title(), offer.icon(), offer.category(), things, coins, offer.limit(), offer.quest())
                : with(offer, offer.title(), offer.icon(), offer.category(), coins, things, offer.limit(), offer.quest());
        book().putOffer(changed, line);
    }

    /** The names of an item's components, for the line under it: "create:schematic_file, minecraft:custom_name". */
    private static String componentNames(DataComponentPatch patch) {
        List<String> names = new ArrayList<>();
        for (var entry : patch.entrySet()) {
            ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
            names.add((entry.getValue().isPresent() ? "" : "!") + (key == null ? "?" : key.toString()));
        }
        return String.join(", ", names);
    }

    @Nullable
    private static RegistryOps<JsonElement> jsonOps() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    }

    /** An item's components as the data file writes them: {"create:schematic_file": "repys_steel_converter.nbt"}. */
    private static String componentsJson(ShopOffer.Entry entry) {
        RegistryOps<JsonElement> ops = jsonOps();
        if (entry.components().isEmpty() || ops == null) {
            return "";
        }
        return DataComponentPatch.CODEC.encodeStart(ops, entry.components().get()).result().map(JsonElement::toString).orElse("");
    }

    /** Typed components: an empty field takes them away, text that does not read yet is left until it does. */
    private void setComponents(ShopOffer offer, int index, String value) {
        List<ShopOffer.Entry> things = new ArrayList<>(items(offer));
        RegistryOps<JsonElement> ops = jsonOps();
        if (index >= things.size() || ops == null) {
            return;
        }
        Optional<DataComponentPatch> components;
        if (value.isBlank()) {
            components = Optional.empty();
        } else {
            try {
                components = DataComponentPatch.CODEC.parse(ops, JsonParser.parseString(value)).result().filter(patch -> !patch.isEmpty());
            } catch (RuntimeException e) {
                return;
            }
            if (components.isEmpty()) {
                return;
            }
        }
        ShopOffer.Entry old = things.get(index);
        if (old.components().equals(components)) {
            return;
        }
        things.set(index, new ShopOffer.Entry(old.type(), old.id(), old.amount(), components));
        List<ShopOffer.Entry> coins = offer.cost().stream().filter(ShopOffer.Entry::isCurrency).toList();
        ShopOffer changed = with(offer, offer.title(), offer.icon(), offer.category(), coins, things, offer.limit(), offer.quest());
        book().type("offer-data:" + offer.id() + ":" + index, "offer:" + offer.id(), tr("rysquests.ed.log.offer_components", name(book(), offer)), d -> d.withOffer(changed));
    }

    private void setLimitPer(ShopOffer offer, boolean team) {
        ShopOffer.Limit old = offer.limit().orElse(null);
        if (old == null || (old.per() == ShopOffer.Per.TEAM) == team) {
            return;
        }
        ShopOffer.Limit limit = new ShopOffer.Limit(team ? ShopOffer.Per.TEAM : ShopOffer.Per.PLAYER, old.count(), old.reset());
        book().putOffer(with(offer, offer.title(), offer.icon(), offer.category(), offer.cost(), offer.gives(), Optional.of(limit), offer.quest()), tr("rysquests.ed.log.offer_limit", name(book(), offer)));
    }

    private void setLimit(ShopOffer offer, int index) {
        ShopOffer.Per per = offer.limit().map(ShopOffer.Limit::per).orElse(ShopOffer.Per.PLAYER);
        Optional<ShopOffer.Limit> limit = index == 0 ? Optional.empty() : Optional.of(new ShopOffer.Limit(per, offer.limit().map(ShopOffer.Limit::count).orElse(1),
                index == 1 ? ShopOffer.Reset.DAILY : index == 2 ? ShopOffer.Reset.WEEKLY : ShopOffer.Reset.NEVER));
        book().putOffer(with(offer, offer.title(), offer.icon(), offer.category(), offer.cost(), offer.gives(), limit, offer.quest()), tr("rysquests.ed.log.offer_limit", name(book(), offer)));
    }

    private void setLimitCount(ShopOffer offer, String value) {
        int count;
        try {
            count = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (count <= 0 || offer.limit().isEmpty()) {
            return;
        }
        ShopOffer.Limit old = offer.limit().get();
        ShopOffer changed = with(offer, offer.title(), offer.icon(), offer.category(), offer.cost(), offer.gives(), Optional.of(new ShopOffer.Limit(old.per(), count, old.reset())), offer.quest());
        book().type("offer-limit:" + offer.id(), "offer:" + offer.id(), tr("rysquests.ed.log.offer_limit", name(book(), offer)), d -> d.withOffer(changed));
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll.wheel(amount, 50);
        panelScroll.wheel(amount, 48);
    }

    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        return chosen == null ? null : new int[] {x, y, w - PANEL_W, h};
    }

    // --- tests ---

    void newOfferForTest() {
        newOffer();
    }

    @Nullable
    ResourceLocation chosenForTest() {
        return chosen;
    }
}
