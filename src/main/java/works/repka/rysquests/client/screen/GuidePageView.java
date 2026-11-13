package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.Ponders;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.client.markup.GuideTopics;
import works.repka.rysquests.client.markup.Markup;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.StructureDef;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;

/**
 * A guide from the inside (plan, section 3, picture 24): on the left the way back, the guide and its pages; in the middle one column of
 * reading with its insets (a recipe, a showing in motion, a picture, a structure) and the quests of the page at its end; at the bottom
 * the previous page and the next one, the next as the screen's one amber button. In the compact layout the pages hide under a list.
 */
final class GuidePageView implements BookView {
    private static final int COLUMN_W = 640;
    private static final int PAD_X = 20;
    private static final int PAD_TOP = 22;
    private static final int NAV_H = 58;
    private static final float TEXT_PX = 13.5f;
    private static final float TEXT_LINE = 21.6f;
    private static final float TITLE_PX = 21;
    private static final int GAP = 12;
    private static final int LINK_H = 36;
    private static final int LINK_W = 420;
    private static final int SLOT = 34;
    private static final int WHEEL = 48;

    private final java.util.Map<ResourceLocation, StructureBlock> structures = new java.util.HashMap<>();
    private StructureBlock dragging;
    private StructureBlock hoveredStructure;

    private record Topics(QuestBook book, ResourceLocation guide, int page, List<Quest> quests) {
    }

    /** What the view shows: a guide of the library, or a section on the pages template. */
    interface Source {
        @Nullable
        Guide guide();

        int page();

        void open(Guide guide, int page);

        boolean tracksReading();

        /** The words of the way back on top of the column ("Все гайды"), or null when there is nowhere to go. */
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
    private final Kit.Dropdown pagesList = new Kit.Dropdown();
    private int tocRight;
    @Nullable
    private Topics topics;

    GuidePageView(Guides guides, Source source) {
        this.guides = guides;
        this.source = source;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static boolean compact() {
        return net.minecraft.client.Minecraft.getInstance().screen instanceof BookScreen screen && screen.compact();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        hoveredStructure = null;
        Guide guide = source.guide();
        if (guide == null || guide.pages().isEmpty()) {
            source.back();
            return;
        }
        int index = Math.clamp(source.page(), 0, guide.pages().size() - 1);
        Guide.Page page = guide.pages().get(index);
        String body = tr(page.text());
        Topics found = topicsOf(guide, index, page, body);
        boolean compact = compact();
        int sideW = compact ? 0 : SideList.WIDTH;
        tocRight = x + sideW;
        if (!compact) {
            renderSide(ui, guide, index, x, y, h);
        }
        float mainX = x + sideW;
        float mainW = w - sideW;
        float columnW = Math.min(COLUMN_W, mainW - PAD_X * 2);
        float columnX = mainX + (mainW - columnW) / 2f;
        float top = y + PAD_TOP;
        float bottom = y + h - NAV_H;
        renderNav(ui, guide, index, mainX, bottom, mainW);
        int offset = textScroll.offset();
        ui.clip(Math.round(mainX), y, Math.round(mainW), Math.round(bottom - y));
        float pen = top - offset;
        pen = renderHead(ui, guide, page, index, columnX, pen, columnW, compact);
        pen = renderBody(ui, body, columnX, pen, columnW);
        pen = renderQuests(ui, found.quests, columnX, pen + 2, columnW);
        ui.unclip();
        textScroll.content(pen + offset - top + GAP, bottom - top);
        if (compact) {
            renderPagesList(ui, guide, index);
        }
    }

    private Topics topicsOf(Guide guide, int index, Guide.Page page, String body) {
        QuestBook book = ClientBook.current();
        if (topics == null || topics.book != book || !topics.guide.equals(guide.id()) || topics.page != index) {
            topics = new Topics(book, guide.id(), index, GuideTopics.quests(book, guide, page, body).stream().filter(BookData::inStory).toList());
            textScroll.reset();
            // Meshes of the page left behind go now, not when the book closes: leafing through would pile them up.
            closeStructures();
        }
        return topics;
    }

    // --- the column on the left ---

    private void renderSide(Ui ui, Guide guide, int current, int x, int y, int h) {
        SideList.plate(ui, x, y, h);
        float pen = y;
        String back = source.crumb();
        if (back != null) {
            float bx = x + 14;
            float bw = ui.width(back, 12.5f, Ui.MEDIUM) + 14 + 6;
            boolean over = ui.over(bx, pen + 10, bw, 20);
            int color = ui.c(over ? "text.primary" : "link");
            ui.icon(Icons.Shape.CHEVRON_LEFT, bx, pen + SideList.HEAD_H / 2f - 7, 14, color);
            ui.label(back, bx + 14 + 6, pen + SideList.HEAD_H / 2f, 12.5f, Ui.MEDIUM, color);
            ui.hit(bx, pen + 10, bw, 20, source::back);
        }
        pen += SideList.HEAD_H;
        float gx = x + 12;
        ui.box(gx, pen, 36, 36, 6, ui.c("slot.bg"), ui.c("slot.border"));
        ui.item(GuideLibraryView.icon(guide), gx + 4, pen + 4, 28);
        float tx = gx + 36 + 10;
        float tw = x + SideList.WIDTH - 12 - tx;
        ui.labelFit(tr(guide.title()), tx, pen + 9, tw, 14, Ui.SEMIBOLD, ui.c("text.primary"));
        List<String> sub = new ArrayList<>();
        guide.category().ifPresent(category -> sub.add(tr(category)));
        if (source.tracksReading()) {
            sub.add(text("rysquests.count.of", ClientProgress.readPages(guide), guide.pages().size()));
        }
        ui.labelFit(String.join(" · ", sub), tx, pen + 27, tw, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        pen += 36 + 10;
        float listTop = pen;
        int offset = tocScroll.offset();
        ui.clip(x, Math.round(listTop), SideList.WIDTH - 1, Math.round(y + h - listTop));
        float row = listTop - offset;
        for (int i = 0; i < guide.pages().size(); i++) {
            if (ui.rowVisible(row, SideList.SUB_H)) {
                renderTocRow(ui, guide, i, i == current, x, row);
            }
            row += SideList.SUB_H + 1;
        }
        ui.unclip();
        tocScroll.content(row + offset - listTop + 8, y + h - listTop);
    }

    private void renderTocRow(Ui ui, Guide guide, int index, boolean now, float x, float y) {
        Guide.Page page = guide.pages().get(index);
        boolean read = source.tracksReading() && ClientProgress.isRead(guide, page);
        float rx = x + SideList.PAD;
        float rw = SideList.WIDTH - 1 - SideList.PAD * 2;
        float h = SideList.SUB_H;
        boolean over = ui.over(rx, y, rw, h);
        if (now) {
            ui.fill(rx, y, rw, h, 8, ui.c("tab.active.bg"));
        } else if (over) {
            ui.fill(rx, y, rw, h, 8, ui.c("button.bg"));
        }
        float cy = y + h / 2f;
        ui.label(String.valueOf(index + 1), rx + 10, cy, 11, Ui.REGULAR, ui.c("text.dim"));
        float textX = rx + 10 + 16 + 8;
        ui.labelFit(tr(page.title()), textX, cy, rx + rw - 10 - 14 - 6 - textX, 12.5f, now ? Ui.MEDIUM : Ui.REGULAR, ui.c(now ? "text.primary" : "text.secondary"));
        if (read && !now) {
            ui.icon(Icons.Shape.CHECK, rx + rw - 10 - 12, cy - 6, 12, ui.c("state.ok"));
        }
        ui.hit(rx, y, rw, h, () -> source.open(guide, index));
    }

    /** The compact layout: the pages under a list in the head of the column. */
    private void renderPagesList(Ui ui, Guide guide, int current) {
        List<Kit.Option> options = new ArrayList<>();
        for (Guide.Page page : guide.pages()) {
            options.add(new Kit.Option(tr(page.title())));
        }
        Kit.dropdownList(ui.g, ui.theme, ui.kitHits, pagesList, options, current, 150, ui.mouseX / 2, ui.mouseY / 2, index -> source.open(guide, index));
    }

    // --- the column of reading ---

    /** "Create · страница 2 из 23", the title, and for an operator the pencil. @return the y under it */
    private float renderHead(Ui ui, Guide guide, Guide.Page page, int index, float x, float y, float w, boolean compact) {
        float cy = y + 8;
        float pen = x;
        if (compact) {
            String back = source.crumb();
            if (back != null) {
                boolean over = ui.over(pen, cy - 9, 14, 18);
                ui.icon(Icons.Shape.CHEVRON_LEFT, pen, cy - 7, 14, ui.c(over ? "text.primary" : "link"));
                ui.hit(pen, cy - 9, 14, 18, source::back);
                pen += 14 + 8;
            }
            String label = tr("rysquests.guides.pages_button");
            int ddW = Kit.dropdown(ui.g, ui.theme, ui.kitHits, pagesList, Math.round(pen / 2f), Math.round((cy - Kit.DROP_H) / 2f), 80, label, false, ui.mouseX / 2, ui.mouseY / 2) * 2;
            pen += ddW + 10;
        }
        String crumb = text("rysquests.guides.page_crumb", tr(guide.title()), index + 1, guide.pages().size());
        ui.labelFit(crumb, pen, cy, x + w - pen - 40, 12, Ui.REGULAR, ui.c("text.muted"));
        if (BookRole.operator()) {
            float px = x + w - 30;
            boolean over = ui.over(px, cy - 15, 30, 30);
            ui.box(px, cy - 15, 30, 30, 8, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
            ui.icon(Icons.Shape.PENCIL, px + 9, cy - 6, 12, ui.c("button.fg"));
            ui.tooltipIfOver(px, cy - 15, 30, 30, Component.translatable("rysquests.editor.edit"));
            ui.hit(px, cy - 15, 30, 30, () -> source.edit(guide, index));
        }
        float top = y + 16 + 10;
        List<String> title = ui.wrap(tr(page.title()), w, TITLE_PX, Ui.DISPLAY, 2);
        float titleLine = Ui.headingHeight(TITLE_PX);
        for (int i = 0; i < title.size(); i++) {
            ui.label(title.get(i), x, top + i * titleLine + titleLine / 2f, TITLE_PX, Ui.DISPLAY, ui.c("text.primary"));
        }
        return top + title.size() * titleLine + 8;
    }

    /** @return the y below the text */
    float renderBody(Ui ui, String body, float x, float y, float w) {
        TextBlock.Look look = new TextBlock.Look(TEXT_PX, TEXT_LINE, ui.c("text.secondary"), ui.c("text.primary"));
        StringBuilder run = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            List<Markup.Paragraph> parsed = Markup.parse(line);
            Markup.Paragraph paragraph = parsed.isEmpty() ? null : parsed.getFirst();
            Markup.Span showing = paragraph == null ? null : showing(paragraph);
            if (showing != null) {
                y = flush(ui, run, x, y, w, look);
                y = renderShowing(ui, showing, x, y, Math.min(LINK_W, w)) + 6;
                continue;
            }
            Markup.Span block = paragraph == null ? null : Markup.block(paragraph);
            if (block == null || !canDraw(block)) {
                if (run.isEmpty() && y > 0 && !line.isEmpty() || !run.isEmpty() || !line.isEmpty()) {
                    run.append(line).append('\n');
                }
                continue;
            }
            y = flush(ui, run, x, y, w, look);
            y = switch (block.kind()) {
                case "image" -> renderImage(ui, ResourceLocation.parse(block.target()), block.text(), x, y, w);
                case "structure" -> renderStructure(ui, ResourceLocation.parse(block.target()), block.text(), x, y, w);
                default -> renderRecipe(ui, block.target(), x, y, w);
            };
        }
        return flush(ui, run, x, y, w, look);
    }

    /** A line that is one link to a showing in motion becomes its own row with "показать" (mockup {@code .lk}). */
    @Nullable
    private static Markup.Span showing(Markup.Paragraph paragraph) {
        if (paragraph.warning()) {
            return null;
        }
        Markup.Span found = null;
        for (Markup.Span span : paragraph.spans()) {
            if (span.kind().equals("ponder") && ResourceLocation.tryParse(span.target()) != null) {
                if (found != null) {
                    return null;
                }
                found = span;
            } else if (!span.text().isBlank()) {
                return null;
            }
        }
        return found;
    }

    private static float renderShowing(Ui ui, Markup.Span span, float x, float y, float w) {
        ResourceLocation scene = ResourceLocation.parse(span.target());
        ItemStack icon = TaskText.stackOf(scene);
        String label = span.text().isEmpty() ? icon.getHoverName().getString() : span.text();
        boolean can = Ponders.exists(scene);
        boolean over = can && ui.over(x, y, w, LINK_H);
        ui.box(x, y, w, LINK_H, 9, ui.c(over ? "slot.hover" : "card.bg"), ui.c("panel.border"));
        float cy = y + LINK_H / 2f;
        ui.box(x + 4, cy - 14, 28, 28, 6, ui.c("slot.bg"), ui.c("slot.border"));
        ui.item(icon, x + 8, cy - 10, 20);
        String act = tr(can ? "rysquests.guides.show" : "rysquests.guides.show_missing");
        float actW = ui.width(act, 11.5f, Ui.REGULAR) + (can ? 16 : 0);
        ui.labelFit(label, x + 4 + 28 + 9, cy, w - 41 - actW - 18, 12.5f, Ui.REGULAR, ui.c("text.primary"));
        float ax = x + w - 10 - actW;
        if (can) {
            ui.icon(Icons.Shape.PLAY, ax, cy - 6, 12, ui.c("text.muted"));
            ax += 16;
        }
        ui.label(act, ax, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        if (can) {
            ui.hit(x, y, w, LINK_H, () -> Ponders.open(scene));
        }
        return y + LINK_H;
    }

    /** Draws and empties the text gathered so far. */
    private static float flush(Ui ui, StringBuilder run, float x, float y, float w, TextBlock.Look look) {
        if (run.length() == 0) {
            return y;
        }
        String text = run.substring(0, run.length() - 1);
        run.setLength(0);
        if (text.isBlank()) {
            return y;
        }
        return TextBlock.render(ui, text, x, y, w, look, Recipes::showRecipes) + GAP;
    }

    /** A picture without a file or a recipe nobody knows stays an ordinary link in the text. */
    private boolean canDraw(Markup.Span block) {
        ResourceLocation target = ResourceLocation.tryParse(block.kind().equals("recipe") ? Markup.recipeItem(block.target()) : block.target());
        if (target == null) {
            return false;
        }
        if (block.kind().equals("image")) {
            int[] size = guides.imageSize(target);
            return size != null && size[0] > 0 && size[1] > 0;
        }
        if (block.kind().equals("structure")) {
            return ClientBook.current().structures().containsKey(target);
        }
        ItemStack stack = TaskText.stackOf(target);
        return RecipePreview.of(stack) != null;
    }

    /** A structure is kept while the book is open, so that it stays turned as the reader left it. */
    private float renderStructure(Ui ui, ResourceLocation id, String caption, float x, float y, float w) {
        StructureDef definition = ClientBook.current().structures().get(id);
        StructureBlock block = structures.get(id);
        if (block == null || !block.definition.equals(definition)) {
            if (block != null) {
                block.close();
            }
            block = new StructureBlock(definition);
            structures.put(id, block);
        }
        StructureBlock shown = block;
        float bottom = shown.render(ui, caption, x, y, w, () -> dragging = shown);
        if (ui.over(x, y, w, bottom - y)) {
            hoveredStructure = shown;
        }
        return bottom + GAP;
    }

    boolean dragStructure(double deltaX, double deltaY) {
        if (dragging == null) {
            return false;
        }
        dragging.drag(deltaX, deltaY);
        return true;
    }

    void releaseStructure() {
        dragging = null;
    }

    void closeStructures() {
        structures.values().forEach(StructureBlock::close);
        structures.clear();
        dragging = null;
        hoveredStructure = null;
    }

    StructureBlock structureForTest(ResourceLocation id) {
        return structures.get(id);
    }

    private float renderImage(Ui ui, ResourceLocation texture, String caption, float x, float y, float w) {
        int[] size = guides.imageSize(texture);
        float shownW = Math.min(w, size[0]);
        float shownH = shownW * size[1] / size[0];
        ui.image(texture, x, y, shownW, shownH, size[0], size[1]);
        y += shownH;
        if (!caption.isEmpty()) {
            ui.label(caption, x, y + 6 + 7, 11.5f, Ui.REGULAR, ui.c("text.muted"));
            y += 6 + 14;
        }
        return y + GAP;
    }

    /** A recipe as one compact row (picture 24): what goes in, an arrow, what comes out, its name and the link "рецепт". */
    private static float renderRecipe(Ui ui, String reference, float x, float y, float w) {
        ItemStack target = TaskText.stackOf(ResourceLocation.parse(Markup.recipeItem(reference)));
        RecipePreview.Shown recipe = RecipePreview.of(target);
        List<ItemStack> inputs = new ArrayList<>();
        List<Item> seen = new ArrayList<>();
        for (ItemStack input : recipe.grid().isEmpty() ? recipe.inputs() : recipe.grid()) {
            if (!input.isEmpty() && !seen.contains(input.getItem())) {
                seen.add(input.getItem());
                inputs.add(input);
            }
        }
        String name = recipe.output().getHoverName().getString();
        String link = tr("rysquests.guides.recipe_link");
        float content = inputs.size() * (SLOT + 6) + 4 + 14 + 4 + 6 + SLOT + 6 + ui.width(name, 12.5f, Ui.REGULAR) + 14 + ui.width(link, 12, Ui.MEDIUM);
        float boxW = Math.min(w, 10 + content + 10);
        float h = 8 + SLOT + 8;
        ui.box(x, y, boxW, h, 10, ui.c("card.bg"), ui.c("panel.border"));
        float cy = y + h / 2f;
        float pen = x + 10;
        for (ItemStack input : inputs) {
            if (pen + SLOT > x + boxW - 10) {
                break;
            }
            ui.cell(input, pen, cy - SLOT / 2f, SLOT, SLOT, 6, 24, ui.c("slot.bg"), ui.c("slot.border"));
            pen += SLOT + 6;
        }
        pen += 4;
        ui.icon(Icons.Shape.CHEVRON_RIGHT, pen, cy - 7, 14, ui.c("text.muted"));
        pen += 14 + 4 + 6;
        ui.cell(recipe.output(), pen, cy - SLOT / 2f, SLOT, SLOT, 6, 24, ui.c("slot.bg"), ui.c("slot.border"));
        ui.count(recipe.output().getCount(), pen + SLOT - 1, cy + SLOT / 2f + 2);
        pen += SLOT + 6;
        float linkW = ui.width(link, 12, Ui.MEDIUM);
        ui.labelFit(name, pen, cy, x + boxW - 10 - linkW - 14 - pen, 12.5f, Ui.REGULAR, ui.c("text.secondary"));
        float lx = x + boxW - 10 - linkW;
        boolean over = ui.over(lx, cy - 9, linkW, 18);
        ui.label(link, lx, cy, 12, Ui.MEDIUM, ui.c(over ? "text.primary" : "link"));
        ui.hit(lx, cy - 9, linkW, 18, () -> Recipes.showRecipes(recipe.output()));
        return y + h + GAP + 2;
    }

    /** The quests of the page, one row each, the state on the right. @return the y under them */
    private static float renderQuests(Ui ui, List<Quest> quests, float x, float y, float w) {
        for (Quest quest : quests) {
            QuestStatus status = BookState.status(quest);
            if (quest.options().hidden() && status == QuestStatus.LOCKED) {
                continue;
            }
            boolean over = ui.over(x, y, w, LINK_H);
            ui.box(x, y, w, LINK_H, 9, ui.c(over ? "slot.hover" : "card.bg"), ui.c("panel.border"));
            float cy = y + LINK_H / 2f;
            ui.box(x + 4, cy - 14, 28, 28, 6, ui.c("slot.bg"), ui.c("slot.border"));
            ui.item(quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), x + 8, cy - 10, 20);
            boolean done = status == QuestStatus.COMPLETED || status == QuestStatus.CLAIMED;
            String word = tr(switch (status) {
                case LOCKED -> "rysquests.guides.quest_locked";
                case AVAILABLE, IN_PROGRESS -> "rysquests.guides.quest_open";
                default -> "rysquests.guides.quest_done";
            });
            float wordW = ui.width(word, 11.5f, Ui.REGULAR) + (done ? 16 : 0);
            ui.labelFit(text("rysquests.guides.quest_row", tr(quest.title())), x + 41, cy, w - 41 - wordW - 20, 12.5f, Ui.REGULAR, ui.c("text.primary"));
            float wx = x + w - 10 - wordW;
            if (done) {
                ui.icon(Icons.Shape.CHECK, wx, cy - 6, 12, ui.c("state.ok"));
                wx += 16;
            }
            ui.label(word, wx, cy, 11.5f, Ui.REGULAR, ui.c(done ? "state.ok" : "text.muted"));
            ui.hit(x, y, w, LINK_H, () -> BookState.select(quest));
            y += LINK_H + 6;
        }
        return y;
    }

    // --- the line at the bottom ---

    private void renderNav(Ui ui, Guide guide, int index, float x, float y, float w) {
        ui.rect(x, y, w, 1, ui.c("panel.border"));
        float cy = y + NAV_H / 2f;
        float left = x + 18;
        float right = x + w - 18;
        if (index > 0) {
            String label = tr(guide.pages().get(index - 1).title());
            float bw = Math.min((w - 54) / 2f, ui.width(label, 12.5f, Ui.MEDIUM) + 14 + 7 + 28);
            boolean over = ui.over(left, cy - 17, bw, 34);
            ui.box(left, cy - 17, bw, 34, 9, ui.c(over ? "slot.hover" : "button.bg"), ui.c("button.border"));
            ui.icon(Icons.Shape.CHEVRON_LEFT, left + 14, cy - 7, 14, ui.c("text.secondary"));
            ui.labelFit(label, left + 14 + 14 + 7, cy, bw - 14 - 14 - 7 - 14, 12.5f, Ui.MEDIUM, ui.c("text.secondary"));
            ui.hit(left, cy - 17, bw, 34, () -> source.open(guide, index - 1));
        }
        if (index + 1 < guide.pages().size()) {
            String label = tr(guide.pages().get(index + 1).title());
            float bw = Math.min((w - 54) / 2f, ui.width(label, 12.5f, Ui.SEMIBOLD) + 14 + 7 + 28);
            float bx = right - bw;
            boolean over = ui.over(bx, cy - 17, bw, 34);
            ui.fill(bx, cy - 17, bw, 34, 9, over ? works.repka.ryscore.ui.Draw.mix(ui.c("accent"), 0xFFFFFFFF, 0.1f) : ui.c("accent"));
            ui.labelFit(label, bx + 14, cy, bw - 14 - 14 - 7 - 14, 12.5f, Ui.SEMIBOLD, ui.c("accent.text"));
            ui.icon(Icons.Shape.CHEVRON_RIGHT, right - 14 - 14, cy - 7, 14, ui.c("accent.text"));
            ui.hit(bx, cy - 17, bw, 34, () -> source.open(guide, index + 1));
        }
    }

    // --- input ---

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (pagesList.scrolled(mouseX / 2, mouseY / 2, amount)) {
            return;
        }
        if (net.minecraft.client.gui.screens.Screen.hasControlDown() && hoveredStructure != null) {
            hoveredStructure.zoom(amount);
            return;
        }
        (mouseX < tocRight ? tocScroll : textScroll).wheel(amount, WHEEL);
    }

    @Override
    public boolean key(int key) {
        if (pagesList.keyPressed(key)) {
            return true;
        }
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
