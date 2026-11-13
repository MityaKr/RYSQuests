package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.editor.SectionEdits;

/**
 * The editor's "Вкладки" (picture 40): the sidebar lists the book's tabs as players see them, the author's tabs inside "Гайды", and the
 * matrices "Атлас сборки" and "Атлас добычи" that stay the author's working tables; under them "Новая вкладка: Карточки, Страницы,
 * Матрица". A matrix is edited cell by cell with the chosen way on the right and "Сделать страницей гайда" at its foot; a tab of
 * cards card by card; a tab of pages opens in "Гайды".
 */
final class TabsEditorView extends EditorPage {
    private static final int SIDE_W = 244;
    private static final int PANEL_W = 360;
    private static final List<String> PLAYER_TABS = List.of("now", "map", BookMeta.GUIDES, BookMeta.SHOP, BookMeta.ACHIEVEMENTS);

    @Nullable
    private String playerTab;
    @Nullable
    private ResourceLocation section;
    private int row = -1;
    private int column = -1;
    private int card = -1;
    private final Scroll scroll = new Scroll();

    TabsEditorView(BookScreen screen) {
        super(screen);
    }

    void select(ResourceLocation id) {
        SectionDef def = book().data().sections().get(id);
        if (def instanceof PagesSection) {
            EditorPages.current = EditorPages.GUIDES;
            ((GuideEditorView) screen.editor.page(EditorPages.GUIDES)).select(id, 0);
            return;
        }
        section = id;
        playerTab = null;
        row = -1;
        column = -1;
        card = -1;
        fields.clear();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        EditorBook editor = book();
        QuestBook book = editor.view();
        if (section == null && playerTab == null) {
            section = book.sections().values().stream().filter(s -> s instanceof MatrixSection).map(SectionDef::id).findFirst().orElse(null);
            if (section == null) {
                playerTab = "map";
            }
        }
        renderSide(ui, editor, book, x, y, h);
        int mx = x + SIDE_W;
        SectionDef def = section == null ? null : editor.data().sections().get(section);
        if (def instanceof MatrixSection matrix) {
            boolean panel = row >= 0 && column >= 0 && row < matrix.rows().size() && column < matrix.columns().size();
            renderMatrix(ui, editor, matrix, mx, y, w - SIDE_W - (panel ? PANEL_W : 0), h);
            if (panel) {
                renderWay(ui, editor, matrix, x + w - PANEL_W, y, PANEL_W, h);
            }
        } else if (def instanceof CardsSection cards) {
            boolean panel = card >= 0 && card < cards.cards().size();
            renderCards(ui, editor, cards, mx, y, w - SIDE_W - (panel ? PANEL_W : 0), h);
            if (panel) {
                renderCard(ui, editor, cards, x + w - PANEL_W, y, PANEL_W, h);
            }
        } else if (playerTab != null) {
            renderPlayerTab(ui, editor, book, mx, y, w - SIDE_W);
        }
        choices.render(ui, y + h);
    }

    // --- the sidebar ---

    private void renderSide(Ui ui, EditorBook editor, QuestBook book, int x, int y, int h) {
        ui.rect(x, y, SIDE_W, h, ui.c("sidebar.bg"));
        ui.rect(x + SIDE_W - 1, y, 1, h, ui.c("panel.border"));
        float px = x + 8;
        float pw = SIDE_W - 17;
        ui.caps(tr("rysquests.ed.tabs.head"), x + 14, y + 20, ui.c("text.dim"));
        float pen = y + 36;
        ui.caps(tr("rysquests.ed.tabs.players"), x + 14, pen + 7, ui.c("text.dim"));
        pen += 20;
        List<String> allowed = book.meta().sections();
        Icons.Shape[] icons = {Icons.Shape.SPARK, Icons.Shape.MAP, Icons.Shape.BOOK, Icons.Shape.DOT, Icons.Shape.STAR};
        for (int i = 0; i < PLAYER_TABS.size(); i++) {
            String id = PLAYER_TABS.get(i);
            String sectionId = id.equals("now") || id.equals("map") ? Chapter.STORY : id;
            boolean shown = allowed.isEmpty() || allowed.contains(sectionId);
            pen += row(ui, icons[i], tr("rysquests.tab." + id), px, pen, pw, id.equals(playerTab), shown ? Icons.Shape.EYE : null, () -> {
                playerTab = id;
                section = null;
            }) + 1;
        }
        pen += 10;
        List<SectionDef> inside = book.sections().values().stream().filter(s -> !(s instanceof MatrixSection)).toList();
        ui.caps(tr("rysquests.ed.tabs.inside"), x + 14, pen + 7, ui.c("text.dim"));
        pen += 20;
        for (SectionDef def : inside) {
            boolean shown = allowed.isEmpty() || allowed.contains(def.id().toString());
            ResourceLocation id = def.id();
            pen += row(ui, def instanceof CardsSection ? Icons.Shape.LIST : Icons.Shape.FLAG, editor.text(def.title()), px, pen, pw, id.equals(section), shown ? Icons.Shape.EYE : null,
                    () -> select(id)) + 1;
        }
        pen += 10;
        ui.caps(tr("rysquests.ed.tabs.editor_only"), x + 14, pen + 7, ui.c("text.dim"));
        pen += 20;
        for (SectionDef def : book.sections().values()) {
            if (def instanceof MatrixSection) {
                ResourceLocation id = def.id();
                pen += row(ui, Icons.Shape.LIST, editor.text(def.title()), px, pen, pw, id.equals(section), null, () -> select(id)) + 1;
            }
        }
        pen += 10;
        ui.caps(tr("rysquests.ed.tabs.new"), x + 14, pen + 7, ui.c("text.dim"));
        pen += 18;
        String[] kinds = {"cards", "pages", "matrix"};
        float bw = (pw - 12) / 3f;
        for (int i = 0; i < kinds.length; i++) {
            String kind = kinds[i];
            float bx = px + i * (bw + 6);
            boolean over = ui.over(bx, pen, bw, EdUi.SM_H);
            ui.box(bx, pen, bw, EdUi.SM_H, 9, ui.c(over ? "slot.hover" : "button.bg"), ui.c("button.border"));
            ui.labelCentered(tr("rysquests.ed.tabs.new_" + kind), bx + bw / 2f, pen + EdUi.SM_H / 2f, 12, Ui.MEDIUM, ui.c(over ? "text.primary" : "text.secondary"));
            ui.hit(bx, pen, bw, EdUi.SM_H, () -> newSection(kind));
        }
    }

    private static float row(Ui ui, Icons.Shape icon, String label, float x, float y, float w, boolean on, @Nullable Icons.Shape right, Runnable action) {
        boolean over = ui.over(x, y, w, 30);
        if (on || over) {
            ui.fill(x, y, w, 30, 7, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        ui.icon(Icons.Shape.GRIP, x + 4, y + 9, 12, ui.c("text.dim"));
        ui.icon(icon, x + 22, y + 8, 14, ui.c(on ? "text.primary" : "text.secondary"));
        ui.labelFitTip(label, x + 46, y + 15, w - 46 - 30, 12.5f, on ? Ui.MEDIUM : Ui.REGULAR, ui.c(on || over ? "text.primary" : "text.secondary"));
        if (right != null) {
            ui.icon(right, x + w - 22, y + 8, 14, ui.c("text.muted"));
        } else {
            ui.lock(x + w - 20, y + 9, 11, ui.c("text.dim"));
        }
        ui.hit(x, y, w, 30, action);
        return 30;
    }

    void newSection(String template) {
        EditorBook editor = book();
        QuestBook data = editor.data();
        String namespace = EditorBook.namespace(data);
        int number = data.sections().size() + 1;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, template + "_" + number);
        while (data.sections().containsKey(id)) {
            number++;
            id = ResourceLocation.fromNamespaceAndPath(namespace, template + "_" + number);
        }
        String key = "section." + namespace + "." + template + "_" + number;
        String title = tr("rysquests.ed.tabs.new_title." + template);
        SectionDef made = switch (template) {
            case "cards" -> SectionEdits.newCards(id, key);
            case "pages" -> new PagesSection(id, key, Optional.empty(), Optional.empty(), Optional.empty(),
                    List.of(new Guide.Page("page_1", key + ".page_1.title", key + ".page_1.text")));
            default -> SectionEdits.newMatrix(id, key);
        };
        ResourceLocation made_ = id;
        editor.edit("section:" + id, tr("rysquests.ed.log.new_tab", title), d -> {
            var out = d.withSection(made).withText(key, title);
            if (made instanceof PagesSection) {
                out = out.withText(key + ".page_1.title", tr("rysquests.ed.guide.new_page")).withText(key + ".page_1.text", "");
            }
            if (made instanceof MatrixSection matrix) {
                for (MatrixSection.Column c : matrix.columns()) {
                    out = out.withText(c.title(), tr("rysquests.ed.tabs.column." + c.id()));
                }
                out = out.withText(matrix.rowTitle(matrix.rows().getFirst()), tr("rysquests.ed.tabs.row_n", 1));
            }
            return out;
        });
        select(made_);
    }

    // --- a tab players see ---

    private void renderPlayerTab(Ui ui, EditorBook editor, QuestBook book, int x, int y, int w) {
        float px = x + 18;
        float pen = y + 14;
        String id = playerTab;
        String sectionId = id.equals("now") || id.equals("map") ? Chapter.STORY : id;
        List<String> allowed = book.meta().sections();
        boolean shown = allowed.isEmpty() || allowed.contains(sectionId);
        ui.label(tr("rysquests.tab." + id), px, pen + 11, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.label(tr("rysquests.ed.tabs.player_sub"), px, pen + 32, 12, Ui.REGULAR, ui.c("text.muted"));
        pen += 54;
        EdUi.setting(ui, px, pen, Math.min(520, w - 36), tr("rysquests.ed.tabs.shown"), tr(id.equals("now") || id.equals("map") ? "rysquests.ed.tabs.shown_story" : "rysquests.ed.tabs.shown_sub"),
                shown, () -> toggleShown(book, sectionId, !shown));
    }

    /** The book's list of tabs works as an allow list: hiding one writes all the others. */
    private void toggleShown(QuestBook book, String sectionId, boolean show) {
        List<String> list = new ArrayList<>(book.meta().sections());
        if (list.isEmpty()) {
            list.add(Chapter.STORY);
            list.add(BookMeta.GUIDES);
            list.add(BookMeta.SHOP);
            list.add(BookMeta.ACHIEVEMENTS);
            book.sections().keySet().forEach(id -> list.add(id.toString()));
        }
        if (show) {
            if (!list.contains(sectionId)) {
                list.add(sectionId);
            }
        } else {
            list.remove(sectionId);
        }
        book().setMeta(tr(show ? "rysquests.ed.log.tab_shown" : "rysquests.ed.log.tab_hidden", sectionId), meta -> meta.withSections(list));
    }

    // --- a matrix ---

    private void renderMatrix(Ui ui, EditorBook editor, MatrixSection matrix, int x, int y, int w, int h) {
        float px = x + 18;
        float pw = w - 36;
        float pen = y + 14;
        ui.label(editor.text(matrix.title()), px, pen + 11, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.label(matrix.subtitle().map(editor::text).map(s -> s + " · ").orElse("") + tr("rysquests.ed.tabs.matrix"), px, pen + 32, 12, Ui.REGULAR, ui.c("text.muted"));
        String addCol = tr("rysquests.ed.tabs.add_column");
        String addRow = tr("rysquests.ed.tabs.add_row");
        float cw = EdUi.buttonWidth(ui, addCol, Icons.Shape.PLUS, true);
        float rw = EdUi.buttonWidth(ui, addRow, Icons.Shape.PLUS, true);
        EdUi.button(ui, addCol, Icons.Shape.PLUS, px + pw - cw, pen + 6, true, false, false, () -> put(matrix, SectionEdits.addColumn(matrix), tr("rysquests.ed.log.column", editor.text(matrix.title()))));
        EdUi.button(ui, addRow, Icons.Shape.PLUS, px + pw - cw - 6 - rw, pen + 6, true, false, false, () -> put(matrix, SectionEdits.addRow(matrix), tr("rysquests.ed.log.row", editor.text(matrix.title()))));
        pen += 50;
        // the lock: players do not see matrices
        List<String> lines = ui.wrap(tr("rysquests.ed.tabs.lock_text"), pw - 24 - 25, 12.5f, Ui.REGULAR, 3);
        float noteH = 20 + lines.size() * 18;
        ui.box(px, pen, pw, noteH, 10, ui.c("status.bg"), ui.c("panel.border"));
        ui.lock(px + 14, pen + 12, 13, ui.c("text.secondary"));
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), px + 12 + 15 + 12, pen + 10 + (i + 0.5f) * 18, 12.5f, i == 0 ? Ui.SEMIBOLD : Ui.REGULAR, ui.c(i == 0 ? "text.primary" : "text.secondary"));
        }
        pen += noteH + 14;
        float headW = 150;
        int columns = matrix.columns().size();
        float cellW = columns == 0 ? 0 : (pw - headW - 8 * columns) / columns;
        ui.caps(tr("rysquests.ed.tabs.resource"), px, pen + 7, ui.c("text.dim"));
        for (int c = 0; c < columns; c++) {
            ui.caps(editor.text(matrix.columns().get(c).title()), px + headW + 8 + c * (cellW + 8) + 8, pen + 7, ui.c("text.dim"));
        }
        pen += 22;
        ui.clip(x, Math.round(pen), w, Math.round(y + h - pen));
        int offset = scroll.offset();
        float top = pen;
        pen -= offset;
        for (int r = 0; r < matrix.rows().size(); r++) {
            MatrixSection.Row row = matrix.rows().get(r);
            float cy = pen + 19;
            EdUi.plate(ui, row.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), Icons.Shape.DOT, px, pen + 4, 30);
            ui.labelFitTip(editor.text(matrix.rowTitle(row)), px + 38, cy, headW - 44, 13, Ui.MEDIUM, ui.c("text.primary"));
            for (int c = 0; c < columns; c++) {
                MatrixSection.Column column = matrix.columns().get(c);
                List<MatrixSection.Way> ways = row.ways(column);
                float cx = px + headW + 8 + c * (cellW + 8);
                boolean on = r == this.row && c == this.column;
                boolean over = ui.over(cx, pen, cellW, 38);
                ui.box(cx, pen, cellW, 38, 8, ui.c(on ? "tab.active.bg" : over ? "slot.hover" : "card.bg"), ui.c(on ? "text.secondary" : "panel.border"));
                String key = ways.isEmpty() ? "" : matrix.wayName(row, column, ways.getFirst());
                boolean named = !ways.isEmpty() && !editor.text(key).equals(key);
                String text = ways.isEmpty() ? tr("rysquests.ed.tabs.empty_cell") : named ? editor.text(key) : tr("rysquests.ed.tabs.way_name");
                ui.labelFitTip(text, cx + 9, cy, cellW - 18, 12.5f, Ui.REGULAR, ui.c(named ? "text.primary" : "text.dim"));
                int ri = r;
                int ci = c;
                ui.hit(cx, pen, cellW, 38, () -> chooseCell(matrix, ri, ci));
            }
            pen += 38 + 8;
        }
        ui.unclip();
        scroll.content(pen + offset - top, y + h - top);
    }

    private void chooseCell(MatrixSection matrix, int r, int c) {
        row = r;
        column = c;
        fields.clear();
        MatrixSection.Row target = matrix.rows().get(r);
        MatrixSection.Column col = matrix.columns().get(c);
        if (target.ways(col).isEmpty()) {
            put(matrix, SectionEdits.addWay(matrix, r, col), tr("rysquests.ed.log.way", book().text(matrix.rowTitle(target))));
        }
    }

    private void put(SectionDef old, SectionDef changed, String line) {
        book().putSection(changed, line);
    }

    private void renderWay(Ui ui, EditorBook editor, MatrixSection matrix, int x, int y, int w, int h) {
        MatrixSection.Row r = matrix.rows().get(row);
        MatrixSection.Column c = matrix.columns().get(column);
        List<MatrixSection.Way> ways = r.ways(c);
        ui.rect(x, y, w, h, ui.c("panel.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        if (ways.isEmpty()) {
            return;
        }
        MatrixSection.Way way = ways.getFirst();
        float bx = x + 1 + 18;
        float bw = w - 1 - 36;
        float pen = y + 16;
        DetailPanel.rubric(ui, bx, pen, bw, tr("rysquests.ed.tabs.way"), editor.text(matrix.rowTitle(r)) + " · " + editor.text(c.title()));
        pen += Ui.lineHeight(11.5f) + 10;
        String nameKey = matrix.wayName(r, c, way);
        String textKey = matrix.wayText(r, c, way);
        String scope = "section:" + matrix.id();
        pen += EdUi.field(ui, fields, "way-name:" + nameKey, tr("rysquests.ed.tabs.way_name"), bx, pen, bw, EdUi.INPUT_H, editor.text(nameKey).equals(nameKey) ? "" : editor.text(nameKey), "",
                value -> editor.typeText("way-name:" + nameKey, scope, tr("rysquests.ed.log.way_name", editor.text(matrix.rowTitle(r))), nameKey, value)) + 12;
        pen += EdUi.field(ui, fields, "way-text:" + textKey, tr("rysquests.ed.tabs.way_text"), bx, pen, bw, 54, editor.text(textKey).equals(textKey) ? "" : editor.text(textKey), "",
                value -> editor.typeText("way-text:" + textKey, scope, tr("rysquests.ed.log.way_text", editor.text(matrix.rowTitle(r))), textKey, value)) + 12;
        pen += DetailPanel.rubric(ui, bx, pen, bw, tr("rysquests.ed.tabs.needs"), String.valueOf(way.needs().size())) + 6;
        for (int i = 0; i < way.needs().size(); i++) {
            ResourceLocation item = way.needs().get(i);
            int index = i;
            pen += EdUi.row(ui, bx, pen, bw, TaskText.stackOf(item), null, EdText.item(item), null, null, () -> {
                List<ResourceLocation> needs = new ArrayList<>(way.needs());
                needs.remove(index);
                put(matrix, SectionEdits.updateWay(matrix, row, c, 0, wy -> withNeeds(wy, needs)), tr("rysquests.ed.log.way_need", editor.text(nameKey)));
            }) + 6;
        }
        pen += EdUi.add(ui, tr("rysquests.ed.tabs.add_item"), bx, pen, bw, false, () -> {
            var player = Minecraft.getInstance().player;
            if (player == null || player.getMainHandItem().isEmpty()) {
                works.repka.rysquests.client.ClientMessages.show("rysquests.ed.hand_empty", List.of(), false);
                return;
            }
            List<ResourceLocation> needs = new ArrayList<>(way.needs());
            needs.add(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()));
            put(matrix, SectionEdits.updateWay(matrix, row, c, 0, wy -> withNeeds(wy, needs)), tr("rysquests.ed.log.way_need", editor.text(nameKey)));
        }) + 12;
        pen += EdUi.label(ui, tr("rysquests.ed.tabs.stage"), bx, pen);
        EdUi.select(ui, bx, pen, bw, c.stage().orElse(editor.text(c.title())), false, () -> { });
        pen += EdUi.INPUT_H + 12;
        EdUi.setting(ui, bx, pen, bw, tr("rysquests.ed.tabs.best"), null, way.best(),
                () -> put(matrix, SectionEdits.markBest(matrix, row, c, 0), tr("rysquests.ed.log.way_best", editor.text(nameKey))));
        float footY = y + h - EdUi.FOOT_H;
        float by = EdUi.foot(ui, x + 1, footY, w - 1);
        String label = tr("rysquests.ed.tabs.to_guide");
        boolean over = ui.over(bx, by, bw, EdUi.BTN_H);
        ui.box(bx, by, bw, EdUi.BTN_H, 9, ui.c(over ? "slot.hover" : "button.bg"), ui.c("button.border"));
        float tw = 14 + 8 + ui.width(label, 12.5f, Ui.MEDIUM);
        ui.icon(Icons.Shape.BOOK, bx + (bw - tw) / 2f, by + 10, 14, ui.c("text.secondary"));
        ui.label(label, bx + (bw - tw) / 2f + 22, by + EdUi.BTN_H / 2f, 12.5f, Ui.MEDIUM, ui.c(over ? "text.primary" : "text.secondary"));
        ui.hit(bx, by, bw, EdUi.BTN_H, () -> toGuide(matrix, r, c, way));
    }

    private static MatrixSection.Way withNeeds(MatrixSection.Way w, List<ResourceLocation> needs) {
        return new MatrixSection.Way(w.id(), w.name(), w.text(), w.icon(), w.mark(), w.best(), List.copyOf(needs), w.links());
    }

    /** "Сделать страницей гайда": the way becomes a page of a new guide with its name, text and what it needs. */
    private void toGuide(MatrixSection matrix, MatrixSection.Row r, MatrixSection.Column c, MatrixSection.Way way) {
        EditorBook editor = book();
        QuestBook data = editor.data();
        String namespace = EditorBook.namespace(data);
        int number = data.guides().size() + 1;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, "guide_" + number);
        while (data.guides().containsKey(id)) {
            number++;
            id = ResourceLocation.fromNamespaceAndPath(namespace, "guide_" + number);
        }
        String prefix = "guide." + namespace + ".guide_" + number;
        String name = editor.text(matrix.wayName(r, c, way));
        StringBuilder text = new StringBuilder(editor.text(matrix.wayText(r, c, way)).equals(matrix.wayText(r, c, way)) ? "" : editor.text(matrix.wayText(r, c, way)));
        for (ResourceLocation item : way.needs()) {
            text.append("\n[[item:").append(item).append("]]");
        }
        Guide guide = new Guide(id, prefix + ".title", r.icon(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(new Guide.Page("page_1", prefix + ".page_1.title", prefix + ".page_1.text")));
        String body = text.toString().strip();
        editor.edit("guide:" + id, tr("rysquests.ed.log.to_guide", name), d -> d.withGuide(guide).withText(guide.title(), editor.text(matrix.rowTitle(r)))
                .withText(prefix + ".page_1.title", name).withText(prefix + ".page_1.text", body));
        EditorPages.current = EditorPages.GUIDES;
        ((GuideEditorView) screen.editor.page(EditorPages.GUIDES)).select(id, 0);
    }

    // --- cards ---

    private void renderCards(Ui ui, EditorBook editor, CardsSection cards, int x, int y, int w, int h) {
        float px = x + 18;
        float pw = w - 36;
        float pen = y + 14;
        ui.label(editor.text(cards.title()), px, pen + 11, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.label(tr("rysquests.ed.tabs.cards"), px, pen + 32, 12, Ui.REGULAR, ui.c("text.muted"));
        String add = tr("rysquests.ed.tabs.add_card");
        float aw = EdUi.buttonWidth(ui, add, Icons.Shape.PLUS, true);
        EdUi.button(ui, add, Icons.Shape.PLUS, px + pw - aw, pen + 6, true, false, false, () -> put(cards, SectionEdits.addCard(cards), tr("rysquests.ed.log.card", editor.text(cards.title()))));
        pen += 50;
        int columns = 3;
        float gap = 10;
        float cw = (pw - gap * (columns - 1)) / columns;
        for (int i = 0; i < cards.cards().size(); i++) {
            CardsSection.Card each = cards.cards().get(i);
            float cx = px + (i % columns) * (cw + gap);
            float cy = pen + (i / columns) * (64 + gap);
            int index = i;
            String name = editor.text(cards.cardKey(each, "name"));
            GridCard.of(name.equals(cards.cardKey(each, "name")) ? each.id() : name).icon(each.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), 1)
                    .selected(i == card).onClick(() -> {
                        card = index;
                        fields.clear();
                    }).render(ui, cx, cy, cw, 64);
        }
    }

    private void renderCard(Ui ui, EditorBook editor, CardsSection cards, int x, int y, int w, int h) {
        CardsSection.Card each = cards.cards().get(card);
        ui.rect(x, y, w, h, ui.c("panel.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float bx = x + 1 + 18;
        float bw = w - 1 - 36;
        float pen = y + 16;
        DetailPanel.rubric(ui, bx, pen, bw, tr("rysquests.ed.tabs.card"), each.id());
        pen += Ui.lineHeight(11.5f) + 10;
        String scope = "section:" + cards.id();
        for (String part : List.of("name", "desc")) {
            String key = cards.cardKey(each, part);
            String shown = editor.text(key).equals(key) ? "" : editor.text(key);
            pen += EdUi.field(ui, fields, "card-" + part + ":" + key, tr("rysquests.ed.tabs.card_" + part), bx, pen, bw, part.equals("desc") ? 72 : EdUi.INPUT_H, shown, "", value -> {
                // the section goes into the draft with its strings, so that the save sends them
                editor.type("card-" + part + ":" + key, scope, tr("rysquests.ed.log.card_text", each.id()), d -> d.withSection(cards).withText(key, value));
            }) + 12;
        }
        EdUi.plate(ui, each.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), Icons.Shape.DOT, bx, pen, 46);
        EdUi.button(ui, tr("rysquests.ed.chapter.icon"), Icons.Shape.HAND, bx + 56, pen + 9, true, false, false, () -> {
            var player = Minecraft.getInstance().player;
            if (player != null && !player.getMainHandItem().isEmpty()) {
                ResourceLocation item = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
                put(cards, SectionEdits.updateCard(cards, card, cd -> new CardsSection.Card(cd.id(), Optional.of(item), cd.group(), cd.art(), cd.stage(), cd.stageTitle(), cd.done(),
                        cd.page(), cd.quest(), cd.needs(), cd.gives())), tr("rysquests.ed.log.card_icon", each.id()));
            }
        });
        float footY = y + h - EdUi.FOOT_H;
        float by = EdUi.foot(ui, x + 1, footY, w - 1);
        EdUi.ghost(ui, tr("rysquests.ed.tabs.remove_card"), Icons.Shape.CROSS, bx - 14, by, () -> {
            put(cards, SectionEdits.removeCard(cards, card), tr("rysquests.ed.log.card_removed", each.id()));
            card = -1;
        });
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll.wheel(amount, 46);
    }

    // --- tests ---

    @Nullable
    ResourceLocation sectionForTest() {
        return section;
    }

    void cellForTest(int r, int c) {
        SectionDef def = section == null ? null : book().data().sections().get(section);
        if (def instanceof MatrixSection matrix) {
            chooseCell(matrix, r, c);
        }
    }
}
