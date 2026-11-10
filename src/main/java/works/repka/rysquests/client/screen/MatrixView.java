package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.GuideRef;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.MatrixSection.Column;
import works.repka.rysquests.data.MatrixSection.Link;
import works.repka.rysquests.data.MatrixSection.Row;
import works.repka.rysquests.data.MatrixSection.Way;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.network.PinWayPayload;

/**
 * The matrix template: {@code design/Mining.dc.html}, and with {@code compact} the grid of {@code design/Atlas.dc.html}.
 * Both use the panel of the mining atlas (ADR-34).
 */
final class MatrixView implements BookView {
    private static final int PANEL_W = 320;
    private static final int PAD_X = 20;
    private static final int PAD_Y = 16;
    private static final int HEADER_H = 38;
    private static final int GAP = 10;
    private static final int LABEL_W = 136;
    private static final int LABEL_W_COMPACT = 92;
    private static final int COLUMN_H = 33;
    private static final int COLUMN_H_COMPACT = 47;
    private static final int CELL_GAP = 8;
    private static final int CELL_GAP_COMPACT = 4;
    private static final int ROW_GAP = 6;
    private static final int CELL_MIN = 74;
    private static final int WAY_H = 28;
    private static final int WAY_GAP = 4;
    private static final int RING = 26;
    private static final int ROW_MIN_COMPACT = 44;
    private static final int LINK_H = 30;
    private static final int WHEEL = 48;
    private static final float PAST_VEIL = 0.2f;
    private static final float CLOSED_VEIL = 0.45f;

    private enum State {
        NONE, PAST, NOW, CLOSED
    }

    private record Selected(Row row, Column column, List<Way> ways, int way) {
    }

    private final BookScreen screen;
    private final Scroll tableScroll = new Scroll();
    private final Scroll panelScroll = new Scroll();
    private int panelLeft;

    MatrixView(BookScreen screen) {
        this.screen = screen;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (!(BookState.customSection() instanceof MatrixSection section)) {
            return;
        }
        State[] states = states(section);
        List<Row> rows = visibleRows(section);
        Selected selected = selected(section, rows, states);
        panelLeft = x + w - PANEL_W;
        renderPanel(ui, section, selected, states, panelLeft, y, PANEL_W, h);
        float left = x + PAD_X;
        float inner = panelLeft - x - PAD_X * 2;
        float cursor = y + PAD_Y;
        renderHeader(ui, section, left, cursor, inner);
        cursor += HEADER_H + GAP;
        if (!section.compact()) {
            cursor = renderChips(ui, section, states, left, cursor, inner) + GAP;
        }
        float bottom = y + h - PAD_Y;
        String hintKey = section.title() + ".hint";
        if (I18n.exists(hintKey)) {
            List<String> hint = ui.wrap(tr(hintKey), inner, 11, Ui.REGULAR, 3);
            bottom -= hint.size() * 14;
            ui.lines(hint, left, bottom + hint.size() * 7f, 14, 11, Ui.REGULAR, ui.c("text.dim"));
            bottom -= GAP;
        }
        renderTable(ui, section, rows, states, selected, left, cursor, inner, bottom - cursor);
    }

    // --- what is shown ---

    /** The current column is the last open one. A matrix without stages has no current column. */
    private static State[] states(MatrixSection section) {
        List<Column> columns = section.columns();
        State[] states = new State[columns.size()];
        if (columns.stream().allMatch(column -> column.stage().isEmpty())) {
            java.util.Arrays.fill(states, State.NONE);
            return states;
        }
        int current = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).stage().map(ClientProgress::hasStage).orElse(true)) {
                current = i;
            }
        }
        for (int i = 0; i < states.length; i++) {
            states[i] = i < current ? State.PAST : i == current ? State.NOW : State.CLOSED;
        }
        return states;
    }

    private static List<Row> visibleRows(MatrixSection section) {
        String query = BookState.sectionSearch.trim().toLowerCase(Locale.ROOT);
        String group = BookState.sectionCategory;
        List<Row> rows = new ArrayList<>();
        for (Row row : section.rows()) {
            boolean inGroup = group.isEmpty() || row.group().filter(group::equals).isPresent();
            if (inGroup && (query.isEmpty() || tr(section.rowTitle(row)).toLowerCase(Locale.ROOT).contains(query))) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static boolean hidden(MatrixSection section, State state) {
        return state == State.CLOSED && section.spoilers() == MatrixSection.Spoilers.HIDE;
    }

    @Nullable
    private static Selected selected(MatrixSection section, List<Row> rows, State[] states) {
        BookState.MatrixPick pick = BookState.MATRIX_PICKS.get(section.id());
        if (pick != null) {
            for (Row row : rows) {
                for (int c = 0; c < section.columns().size(); c++) {
                    Column column = section.columns().get(c);
                    if (row.id().equals(pick.row()) && column.id().equals(pick.column()) && !row.ways(column).isEmpty() && !hidden(section, states[c])) {
                        return new Selected(row, column, row.ways(column), Math.min(pick.way(), row.ways(column).size() - 1));
                    }
                }
            }
        }
        return firstFilled(section, rows, states);
    }

    @Nullable
    private static Selected firstFilled(MatrixSection section, List<Row> rows, State[] states) {
        int now = java.util.Arrays.asList(states).indexOf(State.NOW);
        for (Row row : rows) {
            if (now >= 0 && !row.ways(section.columns().get(now)).isEmpty()) {
                List<Way> ways = row.ways(section.columns().get(now));
                return new Selected(row, section.columns().get(now), ways, bestIndex(ways));
            }
        }
        for (Row row : rows) {
            for (int c = 0; c < section.columns().size(); c++) {
                Column column = section.columns().get(c);
                if (!row.ways(column).isEmpty() && !hidden(section, states[c])) {
                    return new Selected(row, column, row.ways(column), bestIndex(row.ways(column)));
                }
            }
        }
        return null;
    }

    private static int bestIndex(List<Way> ways) {
        for (int i = 0; i < ways.size(); i++) {
            if (ways.get(i).best()) {
                return i;
            }
        }
        return 0;
    }

    // --- the table ---

    private void renderHeader(Ui ui, MatrixSection section, float x, float y, float w) {
        ui.label(tr(section.title()), x, y + 12.5f, 19, Ui.DISPLAY, ui.c("text.primary"));
        section.subtitle().ifPresent(subtitle -> ui.labelFit(tr(subtitle), x, y + 25 + 7, w * 0.6f, 11.5f, Ui.REGULAR, ui.c("text.muted")));
        if (section.compact()) {
            renderLegend(ui, x + w, y + HEADER_H / 2f);
        } else {
            screen.renderSectionSearch(ui, x + w, y + HEADER_H / 2f, Math.round(Math.min(220, w / 2f)), section.search().map(MatrixView::tr).orElse(null));
        }
    }

    private static void renderLegend(Ui ui, float right, float cy) {
        float pen = right;
        for (String[] entry : new String[][] {{"rysquests.matrix.legend_ahead", "card.border"}, {"rysquests.matrix.legend_now", "accent"}, {"rysquests.matrix.legend_past", "state.ok"}}) {
            String label = tr(entry[0]);
            pen -= ui.width(label, 11.5f, Ui.REGULAR);
            ui.label(label, pen, cy, 11.5f, Ui.REGULAR, ui.c("button.fg"));
            pen -= 6 + 12;
            ui.strokeBold(pen, cy - 6, 12, 12, 3, ui.c(entry[1]));
            pen -= 14;
        }
    }

    /** @return the y below the chips */
    private float renderChips(Ui ui, MatrixSection section, State[] states, float x, float y, float w) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        section.groups().forEach(group -> counts.put(group, 0));
        section.rows().forEach(row -> row.group().ifPresent(group -> counts.merge(group, 1, Integer::sum)));
        List<Chips.Chip> chips = new ArrayList<>();
        chips.add(new Chips.Chip("", tr("rysquests.guides.category_all"), section.rows().size()));
        counts.forEach((group, count) -> chips.add(new Chips.Chip(group, tr(section.groupTitle(group)), count)));
        float[] end = counts.isEmpty() ? new float[] {x, y} : Chips.render(ui, chips, BookState.sectionCategory, key -> BookState.sectionCategory = key, x, y, w);
        int now = java.util.Arrays.asList(states).indexOf(State.NOW);
        if (now >= 0) {
            String prefix = text("rysquests.matrix.you_are", "");
            String current = tr(section.columns().get(now).title());
            float cy = end[1] + Chips.HEIGHT / 2f;
            ui.labelRight(current, x + w, cy, 11.5f, Ui.MEDIUM, ui.c("accent"));
            ui.labelRight(prefix, x + w - ui.width(current, 11.5f, Ui.MEDIUM), cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        return end[1] + Chips.HEIGHT;
    }

    private void renderTable(Ui ui, MatrixSection section, List<Row> rows, State[] states, @Nullable Selected selected, float x, float y, float w, float h) {
        boolean compact = section.compact();
        int columns = section.columns().size();
        float labelW = compact ? LABEL_W_COMPACT : LABEL_W;
        float gap = compact ? CELL_GAP_COMPACT : CELL_GAP;
        float columnW = (w - labelW - columns * gap) / columns;
        if (!compact) {
            section.rowTitle().ifPresent(title -> ui.caps(tr(title), x + 4, y + COLUMN_H - 13, ui.c("text.dim")));
        }
        for (int c = 0; c < columns; c++) {
            float cx = x + labelW + gap + c * (columnW + gap);
            if (compact) {
                renderCompactColumn(ui, section.columns().get(c), states[c], cx, y, columnW);
            } else {
                renderColumn(ui, section.columns().get(c), states[c], cx, y, columnW);
            }
        }
        float headH = compact ? COLUMN_H_COMPACT : COLUMN_H;
        float top = y + headH + (compact ? CELL_GAP_COMPACT : GAP);
        float room = y + h - top;
        float[] heights = new float[rows.size()];
        float total = Math.max(0, rows.size() - 1) * (compact ? CELL_GAP_COMPACT : ROW_GAP);
        float even = compact && !rows.isEmpty() ? Math.max(ROW_MIN_COMPACT, (room - total) / rows.size()) : 0;
        for (int r = 0; r < rows.size(); r++) {
            heights[r] = compact ? even : rowHeight(section, rows.get(r));
            total += heights[r];
        }
        tableScroll.content(Math.round(total), Math.round(room));
        int offset = tableScroll.offset();
        ui.clip(Math.round(x), Math.round(top), Math.round(w), Math.round(room));
        float cursor = top - offset;
        for (int r = 0; r < rows.size(); r++) {
            if (cursor + heights[r] > top && cursor < top + room) {
                renderRow(ui, section, rows.get(r), states, selected, x, cursor, labelW, columnW, gap, heights[r]);
            }
            cursor += heights[r] + (compact ? CELL_GAP_COMPACT : ROW_GAP);
        }
        ui.unclip();
    }

    private static float rowHeight(MatrixSection section, Row row) {
        int most = 0;
        for (Column column : section.columns()) {
            most = Math.max(most, row.ways(column).size());
        }
        return Math.max(CELL_MIN, 14 + most * WAY_H + Math.max(0, most - 1) * WAY_GAP);
    }

    private static void renderColumn(Ui ui, Column column, State state, float x, float y, float w) {
        if (state == State.NOW) {
            ui.fillCorners(x, y, w, COLUMN_H, 9, ui.warmBorder(0.1f), true, true, false, false);
        }
        ui.rect(x, y + COLUMN_H - 2, w, 2, state == State.NOW ? ui.c("accent") : ui.c("panel.border"));
        float cy = y + (COLUMN_H - 2) / 2f;
        String title = tr(column.title());
        ui.label(title, x + 10, cy, 13, Ui.DISPLAY, ui.c(state == State.NOW ? "text.primary" : state == State.CLOSED ? "text.muted" : "button.fg"));
        float pen = x + 10 + ui.width(title, 13, Ui.DISPLAY) + 8;
        String word = switch (state) {
            case PAST -> tr("rysquests.matrix.past");
            case NOW -> tr("rysquests.matrix.now");
            case CLOSED -> tr("rysquests.matrix.closed");
            case NONE -> "";
        };
        float wordW = ui.width(word, 10.5f, Ui.REGULAR);
        ui.labelRight(word, x + w - 10, cy, 10.5f, Ui.REGULAR, ui.c(state == State.PAST ? "state.ok" : state == State.NOW ? "accent" : "text.muted"));
        column.hint().ifPresent(hint -> ui.labelFit(tr(hint), pen, cy, x + w - 10 - wordW - 6 - pen, 11, Ui.REGULAR, ui.c("text.muted")));
    }

    private static void renderCompactColumn(Ui ui, Column column, State state, float x, float y, float w) {
        if (state == State.NOW) {
            ui.box(x, y, w, COLUMN_H_COMPACT, 8, ui.warmBorder(0.11f), ui.warmBorder(0.34f));
        }
        float center = x + w / 2f;
        ui.labelCentered(tr(column.title()), center, y + 7 + 9.5f, 15, Ui.DISPLAY, ui.c(state == State.PAST ? "state.ok" : state == State.NOW ? "accent" : "text.muted"));
        column.hint().ifPresent(hint -> {
            String text = ui.fit(tr(hint), w - 4, 9.5f, Ui.REGULAR);
            ui.labelCentered(text, center, y + 7 + 19 + 2 + 6, 9.5f, Ui.REGULAR, ui.c("text.muted"));
        });
    }

    private void renderRow(Ui ui, MatrixSection section, Row row, State[] states, @Nullable Selected selected, float x, float y, float labelW, float columnW, float gap, float h) {
        float cy = y + h / 2f;
        String title = tr(section.rowTitle(row));
        String group = row.group().map(section::groupTitle).map(MatrixView::tr).orElse("");
        if (section.compact()) {
            ui.labelFitTip(title, x + 6, group.isEmpty() ? cy : cy - 7, labelW - 8, 12, Ui.SEMIBOLD, ui.c("text.primary"));
            if (!group.isEmpty()) {
                ui.labelFit(group, x + 6, cy + 7, labelW - 8, 10, Ui.REGULAR, ui.c("text.muted"));
            }
        } else {
            ui.cell(row.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), x + 4, cy - 18, 36, 36, 8, 20, ui.c("slot.bg"), ui.c("card.border"));
            ui.labelFitTip(title, x + 50, group.isEmpty() ? cy : cy - 7, labelW - 50, 13, Ui.SEMIBOLD, ui.c("text.primary"));
            if (!group.isEmpty()) {
                ui.labelFit(group, x + 50, cy + 8, labelW - 50, 10.5f, Ui.REGULAR, ui.c("text.muted"));
            }
        }
        for (int c = 0; c < section.columns().size(); c++) {
            Column column = section.columns().get(c);
            float cellX = x + labelW + gap + c * (columnW + gap);
            boolean chosen = selected != null && selected.row == row && selected.column == column;
            if (section.compact()) {
                renderCompactCell(ui, section, row, column, states[c], chosen ? selected.way : -1, cellX, y, columnW, h);
            } else {
                renderCell(ui, section, row, column, states[c], chosen, cellX, y, columnW, h);
            }
        }
    }

    private static void pick(MatrixSection section, Row row, Column column, int way) {
        BookState.MATRIX_PICKS.put(section.id(), new BookState.MatrixPick(row.id(), column.id(), way));
    }

    private void renderCell(Ui ui, MatrixSection section, Row row, Column column, State state, boolean chosen, float x, float y, float w, float h) {
        boolean now = state == State.NOW;
        ui.box(x, y, w, h, 10, now ? ui.warmBorder(0.06f) : ui.c("card.bg"), now ? ui.warmBorder(0.2f) : ui.c("panel.border"));
        if (chosen) {
            ui.strokeBold(x, y, w, h, 10, ui.c("accent"));
        }
        List<Way> ways = hidden(section, state) ? List.of() : row.ways(column);
        float stack = ways.size() * WAY_H + Math.max(0, ways.size() - 1) * WAY_GAP;
        float wy = y + (h - stack) / 2f;
        for (Way way : ways) {
            renderWayChip(ui, section, row, column, way, x + 7, wy, w - 14);
            wy += WAY_H + WAY_GAP;
        }
        veil(ui, state, x, y, w, h, 10);
        if (!ways.isEmpty()) {
            ui.hit(x, y, w, h, () -> pick(section, row, column, bestIndex(ways)));
        }
    }

    private static void renderWayChip(Ui ui, MatrixSection section, Row row, Column column, Way way, float x, float y, float w) {
        ui.box(x, y, w, WAY_H, 7, ui.c("status.bg"), way.best() ? ui.warmBorder(0.34f) : ui.c("panel.border"));
        float cy = y + WAY_H / 2f;
        ui.item(way.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), x + 8, cy - 8, 16);
        String tag = tag(way, false);
        float tagW = tag.isEmpty() ? 0 : ui.width(tag, 10.5f, Ui.REGULAR) + 6;
        ui.labelFitTip(tr(section.wayName(row, column, way)), x + 30, cy, w - 30 - 8 - tagW, 12, Ui.REGULAR, ui.nameFg());
        if (!tag.isEmpty()) {
            ui.labelRight(tag, x + w - 8, cy, 10.5f, Ui.REGULAR, ui.c(way.best() ? "accent" : "text.muted"));
        }
    }

    private static String tag(Way way, boolean full) {
        if (way.mark().isPresent()) {
            return tr("rysquests.matrix.mark." + way.mark().get());
        }
        return way.best() ? tr(full ? "rysquests.matrix.best_now" : "rysquests.matrix.best") : "";
    }

    /** @param chosenWay the way selected in this cell, or -1 */
    private static void renderCompactCell(Ui ui, MatrixSection section, Row row, Column column, State state, int chosenWay, float x, float y, float w, float h) {
        boolean now = state == State.NOW;
        ui.box(x, y, w, h, 8, now ? ui.warmBorder(0.05f) : ui.mix("slot.bg", "card.bg", 0.4f), ui.mix("slot.bg", "panel.border", 0.5f));
        List<Way> ways = hidden(section, state) ? List.of() : row.ways(column);
        int perRow = Math.max(1, (int) ((w - 8 + WAY_GAP) / (RING + WAY_GAP)));
        int lines = (ways.size() + perRow - 1) / perRow;
        float top = y + (h - (lines * RING + Math.max(0, lines - 1) * WAY_GAP)) / 2f;
        int ring = state == State.PAST ? ui.c("state.ok") : now ? ui.c("accent") : ui.c("card.border");
        // The last hit wins, so the cell goes first and the icons pick their own way on top of it.
        if (!ways.isEmpty()) {
            ui.hit(x, y, w, h, () -> pick(section, row, column, bestIndex(ways)));
        }
        for (int i = 0; i < ways.size(); i++) {
            int inRow = i / perRow < lines - 1 ? perRow : ways.size() - (lines - 1) * perRow;
            float rowW = inRow * RING + (inRow - 1) * WAY_GAP;
            float rx = x + (w - rowW) / 2f + (i % perRow) * (RING + WAY_GAP);
            float ry = top + (i / perRow) * (RING + WAY_GAP);
            Way way = ways.get(i);
            ui.fill(rx, ry, RING, RING, 7, ui.c("slot.bg"));
            ui.strokeBold(rx, ry, RING, RING, 7, i == chosenWay ? ui.c("text.primary") : ring);
            ui.item(way.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), rx + 5, ry + 5, 16);
            ui.tooltipIfOver(rx, ry, RING, RING, Component.translatable(section.wayName(row, column, way)));
            int index = i;
            ui.hit(rx, ry, RING, RING, () -> pick(section, row, column, index));
        }
        veil(ui, state, x, y, w, h, 8);
    }

    private static void veil(Ui ui, State state, float x, float y, float w, float h, float r) {
        if (state == State.PAST) {
            ui.veil(x, y, w, h, r, ui.c("panel.bg"), PAST_VEIL);
        } else if (state == State.CLOSED) {
            ui.veil(x, y, w, h, r, ui.c("panel.bg"), CLOSED_VEIL);
        }
    }

    // --- the panel of the selected cell ---

    private void renderPanel(Ui ui, MatrixSection section, @Nullable Selected selected, State[] states, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, true, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        if (selected == null) {
            return;
        }
        float left = x + 17;
        float inner = w - 33;
        float top = y + 16;
        ItemStack headIcon = selected.row.icon().or(() -> selected.ways.get(selected.way).icon()).map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.cell(headIcon, left, top, 48, 48, 10, 26, ui.c("slot.bg"), ui.c("card.border"));
        ui.labelFit(tr(section.rowTitle(selected.row)), left + 60, top + 6 + 11, inner - 60, 17, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelFit(tr(selected.column.title()) + " · " + Ui.counted("rysquests.plural.way", selected.ways.size()), left + 60, top + 6 + 22 + 7, inner - 60, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        Way chosen = selected.ways.get(selected.way);
        String key = section.pinKey(selected.row, selected.column, chosen);
        boolean pinned = ClientProgress.pinnedWays().contains(key);
        float pinY = y + h - 16 - 34;
        ui.button(tr(pinned ? "rysquests.matrix.unpin" : "rysquests.matrix.pin"), left, pinY, inner, 34, 9, 12, Ui.REGULAR, 12, Ui.Style.PLAIN,
                () -> PacketDistributor.sendToServer(new PinWayPayload(key)));
        float listTop = top + 48 + 12;
        float listH = pinY - 12 - listTop;
        int offset = panelScroll.offset();
        float cursor = listTop - offset;
        ui.clip(Math.round(left - 2), Math.round(listTop), Math.round(inner + 4), Math.round(listH));
        for (int i = 0; i < selected.ways.size(); i++) {
            cursor = renderWayCard(ui, section, selected, i, left, cursor, inner) + 12;
        }
        cursor = renderNext(ui, section, selected, states, left, cursor, inner);
        ui.unclip();
        panelScroll.content(Math.round(cursor + offset - listTop), Math.round(listH));
    }

    private float renderWayCard(Ui ui, MatrixSection section, Selected selected, int index, float x, float y, float w) {
        Way way = selected.ways.get(index);
        String textKey = section.wayText(selected.row, selected.column, way);
        boolean hasText = I18n.exists(textKey) || way.text().isPresent();
        String body = hasText ? tr(textKey) : "";
        float inner = w - 26;
        TextBlock.Look look = new TextBlock.Look(12, 18, ui.c("button.fg"));
        float textH = hasText ? ui.measure(() -> Math.round(TextBlock.render(ui, body, 0, 0, inner, look, Recipes::showRecipes))) : 0;
        float needsH = way.needs().isEmpty() ? 0 : needs(ui, way, x + 13, 0, inner, false);
        float h = 26 + 18 + (hasText ? 8 + textH : 0) + (way.needs().isEmpty() ? 0 : 8 + needsH) + (way.links().isEmpty() ? 0 : 8 + LINK_H);
        ui.box(x, y, w, h, 10, ui.c("status.bg"), way.best() ? ui.warmBorder(0.34f) : ui.c("panel.border"));
        boolean several = selected.ways.size() > 1;
        if (several && index == selected.way) {
            ui.strokeBold(x, y, w, h, 10, ui.c("accent"));
        }
        if (several) {
            ui.hit(x, y, w, h, () -> pick(section, selected.row, selected.column, index));
        }
        float left = x + 13;
        float cursor = y + 13;
        ui.item(way.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), left, cursor, 18);
        String tag = tag(way, true);
        float tagW = tag.isEmpty() ? 0 : ui.width(tag, 10.5f, Ui.REGULAR) + 6;
        ui.labelFitTip(tr(section.wayName(selected.row, selected.column, way)), left + 26, cursor + 9, inner - 26 - tagW, 13, Ui.SEMIBOLD, ui.c("text.primary"));
        if (!tag.isEmpty()) {
            ui.labelRight(tag, left + inner, cursor + 9, 10.5f, Ui.REGULAR, ui.c(way.best() ? "accent" : "text.muted"));
        }
        cursor += 18;
        if (hasText) {
            cursor += 8;
            TextBlock.render(ui, body, left, cursor, inner, look, Recipes::showRecipes);
            cursor += textH;
        }
        if (!way.needs().isEmpty()) {
            cursor += 8;
            needs(ui, way, left, cursor, inner, true);
            cursor += needsH;
        }
        if (!way.links().isEmpty()) {
            cursor += 8;
            float linkW = (inner - (way.links().size() - 1) * 6f) / way.links().size();
            for (int i = 0; i < way.links().size(); i++) {
                Link link = way.links().get(i);
                ui.button(tr("rysquests.matrix.link." + link.type().getSerializedName()), left + i * (linkW + 6), cursor, linkW, LINK_H, 8, 11.5f, Ui.REGULAR, 6, Ui.Style.PLAIN, () -> follow(link));
            }
        }
        return y + h;
    }

    /** Wraps the items a way needs. @return the height used */
    private static float needs(Ui ui, Way way, float x, float y, float w, boolean draw) {
        float pen = x;
        float row = y;
        for (ResourceLocation id : way.needs()) {
            ItemStack stack = TaskText.stackOf(id);
            String name = stack.getHoverName().getString();
            float chipW = Math.min(w, ui.width(name, 11, Ui.REGULAR) + 16 + 12 + 6);
            if (pen + chipW > x + w && pen > x) {
                pen = x;
                row += 22 + 6;
            }
            if (draw) {
                ui.box(pen, row, chipW, 22, 6, ui.c("slot.bg"), ui.c("panel.border"));
                ui.item(stack, pen + 7, row + 5, 12);
                ui.labelFit(name, pen + 7 + 12 + 6, row + 11, chipW - 16 - 12 - 6, 11, Ui.REGULAR, ui.c("button.fg"));
                ui.interactive(stack, pen, row, chipW, 22);
            }
            pen += chipW + 6;
        }
        return row + 22 - y;
    }

    private static void follow(Link link) {
        switch (link.type()) {
            case QUEST -> {
                ResourceLocation id = ResourceLocation.tryParse(link.id());
                Quest quest = id == null ? null : ClientBook.current().quests().get(id);
                if (quest != null) {
                    BookState.select(quest);
                }
            }
            case RECIPE -> {
                ResourceLocation id = ResourceLocation.tryParse(link.id());
                if (id != null) {
                    Recipes.showRecipes(TaskText.stackOf(id));
                }
            }
            case GUIDE -> GuideRef.parse(link.id()).ifPresent(BookState::openRef);
        }
    }

    /** The first way further along the row, which is what the player has to look forward to. */
    private float renderNext(Ui ui, MatrixSection section, Selected selected, State[] states, float x, float y, float w) {
        int from = section.columns().indexOf(selected.column);
        for (int c = from + 1; c < section.columns().size(); c++) {
            Column column = section.columns().get(c);
            List<Way> ways = selected.row.ways(column);
            if (ways.isEmpty() || hidden(section, states[c])) {
                continue;
            }
            ui.caps(tr("rysquests.matrix.next"), x, y + 7, ui.c("text.dim"));
            float rowY = y + 14 + 6;
            float h = 36;
            float cy = rowY + h / 2f;
            boolean closed = states[c] == State.CLOSED;
            boolean over = ui.over(x, rowY, w, h);
            ui.box(x, rowY, w, h, 9, over ? ui.c("slot.hover") : ui.c("status.bg"), ui.c("panel.border"));
            float textX = x + 11;
            if (closed) {
                ui.lock(textX, cy - 7, 14, ui.c("text.muted"));
                textX += 20;
            }
            String hint = column.hint().map(MatrixView::tr).orElse("");
            float hintW = hint.isEmpty() ? 0 : ui.width(hint, 10.5f, Ui.REGULAR) + 8;
            ui.labelFitTip(tr(column.title()) + ": " + tr(section.wayName(selected.row, column, ways.get(bestIndex(ways)))), textX, cy, x + w - 11 - hintW - textX, 12, Ui.REGULAR, ui.c("button.fg"));
            if (!hint.isEmpty()) {
                ui.labelRight(hint, x + w - 11, cy, 10.5f, Ui.REGULAR, ui.c("text.muted"));
            }
            if (closed) {
                ui.veil(x, rowY, w, h, 9, ui.c("card.bg"), 0.3f);
            }
            ui.hit(x, rowY, w, h, () -> pick(section, selected.row, column, bestIndex(ways)));
            return rowY + h;
        }
        return y - 12;
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        (mouseX >= panelLeft ? panelScroll : tableScroll).wheel(amount, WHEEL);
    }
}
