package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.client.markup.GuideTopics;
import works.repka.rysquests.client.markup.Markup;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;

/**
 * A guide from the inside, from {@code design/GuidePage.dc.html} without what ADR-33 removed: the list of pages, the
 * page itself, and a panel that gathers itself from the page's text.
 */
final class GuidePageView implements BookView {
    private static final int PANEL_W = 300;
    private static final int PAD_X = 20;
    private static final int PAD_Y = 14;
    private static final int GAP = 12;
    private static final int HEADER_H = 38;
    private static final int TOC_W = 172;
    private static final int TOC_GAP = 16;
    private static final int TOC_ROW = 28;
    private static final int TOC_STEP = 30;
    private static final int NEXT_H = 40;
    private static final float TEXT_PX = 14.5f;
    private static final float TEXT_LINE = 23.2f;
    private static final int CELL = 22;
    private static final int CELL_GAP = 3;
    private static final int GRID = 9;
    private static final int RECIPE_H = 94;
    private static final int RECIPE_COLUMNS = 3;
    private static final int ROW_H = 32;
    private static final int WHEEL = 48;

    private record Topics(QuestBook book, ResourceLocation guide, int page, List<Quest> quests, List<ItemStack> items) {
    }

    /** What the view shows: a guide of the library, or a section on the pages template. */
    interface Source {
        @Nullable
        Guide guide();

        int page();

        void open(Guide guide, int page);

        boolean tracksReading();

        /** The first part of the path above the title, which leads back, or null when there is nowhere to go. */
        @Nullable
        String crumb();

        void back();

        /** Opens the editor on what is shown. */
        void edit(Guide guide, int page);
    }

    private final Guides guides;
    private final Source source;
    private final Scroll textScroll = new Scroll();
    private final Scroll tocScroll = new Scroll();
    private final Scroll panelScroll = new Scroll();
    private int tocRight;
    private int panelLeft;
    @Nullable
    private Topics topics;

    GuidePageView(Guides guides, Source source) {
        this.guides = guides;
        this.source = source;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        Guide guide = source.guide();
        if (guide == null) {
            source.back();
            return;
        }
        int index = Math.clamp(source.page(), 0, guide.pages().size() - 1);
        Guide.Page page = guide.pages().get(index);
        String body = tr(page.text());
        panelLeft = x + w - PANEL_W;
        renderPanel(ui, topicsOf(guide, index, page, body), panelLeft, y, PANEL_W, h);
        float left = x + PAD_X;
        float inner = panelLeft - x - PAD_X * 2;
        float top = y + PAD_Y;
        renderHeader(ui, guide, page, index, left, top, inner);
        float bodyTop = top + HEADER_H + GAP;
        float bottom = y + h - PAD_Y;
        renderContents(ui, guide, index, left, bodyTop, bottom - bodyTop);
        tocRight = Math.round(left + TOC_W + TOC_GAP / 2f);
        float textX = left + TOC_W + TOC_GAP;
        float textW = inner - TOC_W - TOC_GAP;
        float textBottom = bottom;
        if (index + 1 < guide.pages().size()) {
            renderNext(ui, guide, index + 1, textX, bottom - NEXT_H, textW);
            textBottom = bottom - NEXT_H - GAP;
        }
        int offset = textScroll.offset();
        ui.clip(Math.round(textX), Math.round(bodyTop), Math.round(textW), Math.round(textBottom - bodyTop));
        float end = renderBody(ui, body, textX, bodyTop - offset, textW);
        ui.unclip();
        textScroll.content(Math.round(end + offset - bodyTop), Math.round(textBottom - bodyTop));
    }

    private Topics topicsOf(Guide guide, int index, Guide.Page page, String body) {
        QuestBook book = ClientBook.current();
        if (topics == null || topics.book != book || !topics.guide.equals(guide.id()) || topics.page != index) {
            List<ItemStack> items = new ArrayList<>();
            GuideTopics.items(body).forEach(id -> items.add(TaskText.stackOf(id)));
            topics = new Topics(book, guide.id(), index, GuideTopics.quests(book, guide, page, body).stream().filter(BookScreen::inStory).toList(), items);
            textScroll.reset();
            panelScroll.reset();
        }
        return topics;
    }

    private void renderHeader(Ui ui, Guide guide, Guide.Page page, int index, float x, float y, float w) {
        float cy = y + HEADER_H / 2f;
        ui.cell(GuideLibraryView.icon(guide), x, y, 38, 38, 8, 20, ui.c("slot.bg"), ui.c("card.border"));
        float textX = x + 38 + 12;
        String crumb = source.crumb();
        float pen = textX;
        if (crumb != null) {
            float crumbW = ui.width(crumb, 11.5f, Ui.REGULAR);
            boolean over = ui.over(textX, y, crumbW, 14);
            ui.label(crumb, textX, y + 7, 11.5f, Ui.REGULAR, ui.c(over ? "text.primary" : "text.muted"));
            ui.hit(textX, y, crumbW, 14, source::back);
            ui.icon(Icons.Shape.CHEVRON_RIGHT, textX + crumbW + 4, y + 3, 8, ui.c("text.dim"));
            pen += crumbW + 16;
        }
        ui.label(tr(guide.title()), pen, y + 7, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        float right = x + w;
        renderArrow(ui, "→", right - 32, cy - 16, index + 1 < guide.pages().size(), () -> source.open(guide, index + 1));
        renderArrow(ui, "←", right - 32 - 10 - 32, cy - 16, index > 0, () -> source.open(guide, index - 1));
        if (Minecraft.getInstance().player.hasPermissions(2)) {
            float pencilX = right - 32 - 10 - 32 - 10 - 32 - ui.width(Component.translatable("rysquests.guides.page_of", index + 1, guide.pages().size()).getString(), 11.5f, Ui.REGULAR) - 12;
            boolean overPencil = ui.over(pencilX, cy - 16, 32, 32);
            ui.box(pencilX, cy - 16, 32, 32, 8, overPencil ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
            ui.icon(Icons.Shape.PENCIL, pencilX + 10, cy - 6, 12, ui.c("button.fg"));
            ui.tooltipIfOver(pencilX, cy - 16, 32, 32, Component.translatable("rysquests.editor.edit"));
            ui.hit(pencilX, cy - 16, 32, 32, () -> source.edit(guide, index));
        }
        String pageOf = Component.translatable("rysquests.guides.page_of", index + 1, guide.pages().size()).getString();
        float pageOfW = ui.width(pageOf, 11.5f, Ui.REGULAR);
        float pageOfX = right - 32 - 10 - 32 - 10 - pageOfW;
        ui.label(pageOf, pageOfX, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.labelFit(tr(page.title()), textX, y + 14 + 11.5f, pageOfX - 12 - textX, 18, Ui.DISPLAY, ui.c("text.primary"));
    }

    private static void renderArrow(Ui ui, String arrow, float x, float y, boolean enabled, Runnable action) {
        ui.box(x, y, 32, 32, 8, enabled && ui.over(x, y, 32, 32) ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        ui.labelCentered(arrow, x + 16, y + 16, 13, Ui.REGULAR, ui.c(enabled ? "text.primary" : "text.dim"));
        if (enabled) {
            ui.hit(x, y, 32, 32, action);
        }
    }

    private void renderContents(Ui ui, Guide guide, int current, float x, float y, float h) {
        ui.caps(tr("rysquests.guides.pages"), x + 8, y + 7, ui.c("text.dim"));
        float listTop = y + 14 + 8;
        float listH = h - 22;
        tocScroll.content(guide.pages().size() * TOC_STEP - 2, Math.round(listH));
        int offset = tocScroll.offset();
        ui.clip(Math.round(x), Math.round(listTop), TOC_W, Math.round(listH));
        for (int i = 0; i < guide.pages().size(); i++) {
            float rowY = listTop + i * TOC_STEP - offset;
            if (rowY + TOC_ROW > listTop && rowY < listTop + listH) {
                renderTocRow(ui, guide, i, i == current, x, rowY);
            }
        }
        ui.unclip();
    }

    private void renderTocRow(Ui ui, Guide guide, int index, boolean now, float x, float y) {
        Guide.Page page = guide.pages().get(index);
        boolean read = source.tracksReading() && ClientProgress.isRead(guide, page);
        float cy = y + TOC_ROW / 2f;
        if (now) {
            ui.fill(x, y, TOC_W, TOC_ROW, 7, ui.c("button.bg"));
            ui.rect(x, y, 2, TOC_ROW, ui.c("accent"));
        } else if (ui.over(x, y, TOC_W, TOC_ROW)) {
            ui.fill(x, y, TOC_W, TOC_ROW, 7, ui.c("slot.hover"));
        }
        if (read && !now) {
            ui.check(x + 10, cy - 5.5f, 11, ui.c("state.ok"));
        }
        ui.labelFitTip(tr(page.title()), x + 30, cy, TOC_W - 38, 12, Ui.REGULAR, ui.c(now ? "text.primary" : read ? "text.muted" : "button.fg"));
        ui.hit(x, y, TOC_W, TOC_ROW, () -> source.open(guide, index));
    }

    private void renderNext(Ui ui, Guide guide, int next, float x, float y, float w) {
        boolean over = ui.over(x, y, w, NEXT_H);
        ui.box(x, y, w, NEXT_H, 9, over ? ui.c("slot.hover") : ui.c("card.bg"), ui.c("panel.border"));
        float cy = y + NEXT_H / 2f;
        String label = tr("rysquests.guides.next_page");
        ui.label(label, x + 13, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        float pen = x + 13 + ui.width(label, 11, Ui.REGULAR) + 10;
        ui.labelFit(tr(guide.pages().get(next).title()), pen, cy, x + w - 34 - pen, 12.5f, Ui.MEDIUM, ui.c("text.primary"));
        ui.labelRight("→", x + w - 13, cy, 13, Ui.REGULAR, ui.c("text.muted"));
        ui.hit(x, y, w, NEXT_H, () -> source.open(guide, next));
    }

    // --- the page ---

    /** @return the y below the text */
    float renderBody(Ui ui, String body, float x, float y, float w) {
        TextBlock.Look look = new TextBlock.Look(TEXT_PX, TEXT_LINE, ui.c("text.body"), ui.c("text.primary"));
        StringBuilder run = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            Markup.Span block = Markup.block(Markup.parse(line).getFirst());
            if (block == null || !canDraw(block)) {
                run.append(line).append('\n');
                continue;
            }
            y = flush(ui, run, x, y, w, look);
            ResourceLocation target = ResourceLocation.parse(block.target());
            y = block.kind().equals("image") ? renderImage(ui, target, block.text(), x, y, w) : renderRecipe(ui, target, x, y, w);
        }
        return flush(ui, run, x, y, w, look);
    }

    /** Draws and empties the text gathered so far. */
    private static float flush(Ui ui, StringBuilder run, float x, float y, float w, TextBlock.Look look) {
        if (run.length() == 0) {
            return y;
        }
        String text = run.substring(0, run.length() - 1);
        run.setLength(0);
        return TextBlock.render(ui, text, x, y, w, look, Recipes::showRecipes) + GAP;
    }

    /** A picture without a file or a recipe nobody knows stays an ordinary link in the text. */
    private boolean canDraw(Markup.Span block) {
        ResourceLocation target = ResourceLocation.tryParse(block.target());
        if (target == null) {
            return false;
        }
        if (block.kind().equals("image")) {
            int[] size = guides.imageSize(target);
            return size != null && size[0] > 0 && size[1] > 0;
        }
        return RecipePreview.of(TaskText.stackOf(target)) != null;
    }

    private float renderImage(Ui ui, ResourceLocation texture, String caption, float x, float y, float w) {
        int[] size = guides.imageSize(texture);
        float shownW = Math.min(w, size[0]);
        float shownH = shownW * size[1] / size[0];
        ui.image(texture, x, y, shownW, shownH, size[0], size[1]);
        y += shownH;
        if (!caption.isEmpty()) {
            ui.label(caption, x, y + 6 + 7, 11, Ui.REGULAR, ui.c("text.muted"));
            y += 6 + 14;
        }
        return y + GAP;
    }

    private static float renderRecipe(Ui ui, ResourceLocation item, float x, float y, float w) {
        ItemStack target = TaskText.stackOf(item);
        RecipePreview.Shown recipe = RecipePreview.of(target);
        ui.box(x, y, w, RECIPE_H, 10, ui.c("card.bg"), ui.c("panel.border"));
        float gridX = x + 11;
        float gridY = y + 11;
        for (int i = 0; i < GRID; i++) {
            float cellX = gridX + (i % RECIPE_COLUMNS) * (CELL + CELL_GAP);
            float cellY = gridY + (i / RECIPE_COLUMNS) * (CELL + CELL_GAP);
            ItemStack input = i < recipe.grid().size() ? recipe.grid().get(i) : ItemStack.EMPTY;
            ui.cell(input, cellX, cellY, CELL, CELL, 4, 16, ui.c("slot.bg"), ui.c("card.border"));
            ui.interactive(input, cellX, cellY, CELL, CELL);
        }
        float pen = gridX + RECIPE_COLUMNS * CELL + (RECIPE_COLUMNS - 1) * CELL_GAP + 12;
        float cy = y + RECIPE_H / 2f;
        ui.labelCentered("→", pen + 7, cy, 14, Ui.REGULAR, ui.c("text.dim"));
        pen += 14 + 12;
        ui.cell(recipe.output(), pen, cy - 17, 34, 34, 7, 20, ui.c("slot.bg"), ui.c("card.border"));
        ui.interactive(recipe.output(), pen, cy - 17, 34, 34);
        pen += 34 + 12;
        ui.labelFit(recipe.output().getHoverName().getString(), pen, cy - 8, x + w - 11 - pen, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        String link = Component.translatable("rysquests.guides.open_viewer", RecipePreview.viewerName()).getString();
        ui.link(link, pen, cy + 9, 11, ui.c("chip.mod.fg"));
        ui.hit(pen, cy + 2, ui.width(link, 11, Ui.REGULAR), 14, () -> Recipes.showRecipes(recipe.output()));
        return y + RECIPE_H + GAP;
    }

    // --- the panel that gathers itself ---

    private void renderPanel(Ui ui, Topics found, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, true, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float left = x + 17;
        float inner = w - 33;
        int offset = panelScroll.offset();
        float cursor = y + 16 - offset;
        ui.clip(x, y, w, h);
        if (!found.quests.isEmpty()) {
            ui.label(tr("rysquests.guides.topic_quests"), left, cursor + 9.5f, 15, Ui.DISPLAY, ui.c("text.primary"));
            cursor += 19 + 12;
            cursor = renderQuests(ui, found.quests, left, cursor, inner) + 16;
        }
        if (!found.items.isEmpty()) {
            ui.caps(tr("rysquests.guides.topic_items"), left, cursor + 7, ui.c("text.dim"));
            cursor += 14 + 5;
            for (ItemStack item : found.items) {
                renderItemRow(ui, item, left, cursor, inner);
                cursor += ROW_H + 5;
            }
        }
        ui.unclip();
        panelScroll.content(Math.round(cursor + offset - y + 11), h);
    }

    private static boolean active(Quest quest) {
        QuestStatus status = BookState.status(quest);
        return status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
    }

    private static float renderQuests(Ui ui, List<Quest> quests, float x, float y, float w) {
        Quest featured = quests.stream().filter(GuidePageView::active).findFirst().orElse(null);
        if (featured != null) {
            y = renderFeatured(ui, featured, x, y, w) + 8;
        }
        for (Quest quest : quests) {
            if (quest != featured && (!quest.options().hidden() || BookState.status(quest) != QuestStatus.LOCKED)) {
                renderQuestRow(ui, quest, x, y, w);
                y += ROW_H + 5;
            }
        }
        return y - 5;
    }

    private static float renderFeatured(Ui ui, Quest quest, float x, float y, float w) {
        float h = 133;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.warmBorder(0.34f));
        float left = x + 13;
        float inner = w - 26;
        ui.label(tr("rysquests.guides.your_quest"), left, y + 13 + 7, 11, Ui.REGULAR, ui.c("accent"));
        float rowY = y + 13 + 14 + 8;
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.cell(icon, left, rowY, 32, 32, 7, 18, ui.c("slot.bg"), ui.c("card.border"));
        QuestProgress own = ClientProgress.get(quest.id());
        ui.labelFit(tr(quest.title()), left + 42, rowY + 8, inner - 42, 13, Ui.SEMIBOLD, ui.c("text.primary"));
        String chapter = Ui.roman(BookScreen.chapterNumber(quest.chapter().orElse(null)));
        String line = Component.translatable("rysquests.guides.quest_line", chapter, QuestCard.tasksDone(quest, own), quest.tasks().size()).getString();
        ui.labelFit(line, left + 42, rowY + 24, inner - 42, 11, Ui.REGULAR, ui.c("text.muted"));
        float barY = rowY + 32 + 8;
        ui.bar(left, barY, inner, 5, 3, quest.tasks().isEmpty() ? 0 : (float) QuestCard.tasksDone(quest, own) / quest.tasks().size(), ui.c("accent"));
        ui.button(tr("rysquests.guides.to_quest"), left, barY + 5 + 8, inner, 32, 8, 12, Ui.SEMIBOLD, 12, Ui.Style.ACCENT, () -> BookState.select(quest));
        return y + h;
    }

    private static void renderQuestRow(Ui ui, Quest quest, float x, float y, float w) {
        boolean over = ui.over(x, y, w, ROW_H);
        ui.box(x, y, w, ROW_H, 8, over ? ui.c("slot.hover") : ui.c("status.bg"), ui.c("status.border"));
        float cy = y + ROW_H / 2f;
        ui.item(quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), x + 9, cy - 8, 16);
        QuestStatus status = BookState.status(quest);
        String word = tr(switch (status) {
            case LOCKED -> "rysquests.guides.quest_locked";
            case AVAILABLE, IN_PROGRESS -> "rysquests.guides.available";
            default -> "rysquests.guides.quest_done";
        });
        float wordW = ui.width(word, 11, Ui.REGULAR);
        ui.labelFitTip(tr(quest.title()), x + 32, cy, w - 32 - 9 - wordW - 8, 12, Ui.REGULAR, ui.nameFg());
        ui.labelRight(word, x + w - 9, cy, 11, Ui.REGULAR, ui.c(status == QuestStatus.LOCKED ? "text.dim" : status == QuestStatus.CLAIMED || status == QuestStatus.COMPLETED ? "state.ok" : "text.muted"));
        ui.hit(x, y, w, ROW_H, () -> BookState.select(quest));
    }

    private static void renderItemRow(Ui ui, ItemStack item, float x, float y, float w) {
        boolean over = ui.over(x, y, w, ROW_H);
        ui.box(x, y, w, ROW_H, 8, over ? ui.c("slot.hover") : ui.c("status.bg"), ui.c("status.border"));
        float cy = y + ROW_H / 2f;
        ui.item(item, x + 9, cy - 8, 16);
        int held = RecipePreview.held(item);
        String have = Numbers.compact(held);
        float haveW = ui.width(have, 11, Ui.REGULAR);
        ui.labelFitTip(item.getHoverName().getString(), x + 32, cy, w - 32 - 9 - haveW - 8, 12, Ui.REGULAR, ui.nameFg());
        ui.labelRight(have, x + w - 9, cy, 11, Ui.REGULAR, ui.c(held > 0 ? "state.ok" : "text.muted"));
        ui.interactive(item, x, y, w, ROW_H);
    }

    // --- input ---

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        (mouseX >= panelLeft ? panelScroll : mouseX < tocRight ? tocScroll : textScroll).wheel(amount, WHEEL);
    }

    @Override
    public boolean key(int key) {
        Guide guide = source.guide();
        if (guide == null) {
            return false;
        }
        int next = key == GLFW.GLFW_KEY_RIGHT ? source.page() + 1 : key == GLFW.GLFW_KEY_LEFT ? source.page() - 1 : -1;
        if (next < 0 || next >= guide.pages().size()) {
            return key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_LEFT;
        }
        source.open(guide, next);
        return true;
    }
}
