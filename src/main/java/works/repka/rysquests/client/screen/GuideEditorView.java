package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;

/**
 * The editor's "Гайды" (picture 38): on the left the guide with its pages and "+ Страница" (or the list of guides), in the middle the
 * page's title and its text with markup in a {@link TextArea}, the insert buttons above it, and on the right "так увидит игрок", drawn
 * by the player's own page view. A tab of pages from "Вкладки" is edited here the same way.
 */
final class GuideEditorView extends EditorPage {
    private static final int SIDE_W = 244;
    private static final int PREVIEW_W = 400;

    /** A guide or a tab of pages: what the editor needs of either. */
    private record Doc(ResourceLocation id, String title, Optional<ResourceLocation> icon, Optional<String> category, List<Guide.Page> pages, boolean section) {
    }

    @Nullable
    private ResourceLocation doc;
    private int pageIndex;
    private boolean props;
    private final Scroll listScroll = new Scroll();
    private final Scroll previewScroll = new Scroll();

    GuideEditorView(BookScreen screen) {
        super(screen);
    }

    void select(ResourceLocation id, int page) {
        doc = id;
        pageIndex = page;
        props = false;
        fields.clear();
    }

    void select(ResourceLocation id) {
        select(id, 0);
    }

    /** "+ Гайд": a guide with one empty page, in the book's namespace. */
    void newGuide() {
        EditorBook editor = book();
        QuestBook book = editor.data();
        String namespace = EditorBook.namespace(book);
        int number = book.guides().size() + 1;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, "guide_" + number);
        while (book.guides().containsKey(id)) {
            number++;
            id = ResourceLocation.fromNamespaceAndPath(namespace, "guide_" + number);
        }
        String prefix = "guide." + namespace + ".guide_" + number;
        String title = tr("rysquests.ed.guide.new_title");
        Guide guide = new Guide(id, prefix + ".title", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(new Guide.Page("page_1", prefix + ".page_1.title", prefix + ".page_1.text")));
        editor.edit("guide:" + id, tr("rysquests.ed.log.new_guide", title), d -> d.withGuide(guide).withText(guide.title(), title)
                .withText(prefix + ".page_1.title", tr("rysquests.ed.guide.new_page")).withText(prefix + ".page_1.text", ""));
        select(id, 0);
    }

    @Nullable
    private Doc doc(QuestBook book) {
        if (doc == null) {
            return null;
        }
        Guide guide = book.guides().get(doc);
        if (guide != null) {
            return new Doc(guide.id(), guide.title(), guide.icon(), guide.category(), guide.pages(), false);
        }
        SectionDef section = book.sections().get(doc);
        if (section instanceof PagesSection pages) {
            return new Doc(pages.id(), pages.title(), pages.icon(), Optional.empty(), pages.pages(), true);
        }
        return null;
    }

    /** Puts the document back with new pages or icon. */
    private void put(Doc d, String line, List<Guide.Page> pages, Optional<ResourceLocation> icon) {
        EditorBook editor = book();
        QuestBook data = editor.data();
        if (d.section()) {
            PagesSection old = (PagesSection) data.sections().get(d.id());
            editor.putSection(new PagesSection(old.id(), old.title(), old.shortTitle(), old.subtitle(), icon, List.copyOf(pages)), line);
        } else {
            Guide old = data.guides().get(d.id());
            editor.putGuide(new Guide(old.id(), old.title(), icon, old.category(), old.stage(), old.stageTitle(), List.copyOf(pages)), line);
        }
    }

    private static String prefix(Doc d) {
        return (d.section() ? "section." : "guide.") + d.id().getNamespace() + "." + d.id().getPath().replace('/', '.');
    }

    private static String scope(Doc d) {
        return (d.section() ? "section:" : "guide:") + d.id();
    }

    /** The draft with the document's pages replaced. */
    private static works.repka.rysquests.editor.BookDraft withPages(works.repka.rysquests.editor.BookDraft draft, QuestBook data, Doc d, List<Guide.Page> pages) {
        if (d.section()) {
            PagesSection old = (PagesSection) data.sections().get(d.id());
            return draft.withSection(new PagesSection(old.id(), old.title(), old.shortTitle(), old.subtitle(), old.icon(), List.copyOf(pages)));
        }
        Guide old = data.guides().get(d.id());
        return draft.withGuide(new Guide(old.id(), old.title(), old.icon(), old.category(), old.stage(), old.stageTitle(), List.copyOf(pages)));
    }

    /** Types the title or the text of a page; a page whose string was the text itself gets a key. */
    private void typePage(int index, boolean text, String value) {
        EditorBook editor = book();
        QuestBook data = editor.data();
        Doc d = doc(data);
        if (d == null || index >= d.pages().size()) {
            return;
        }
        Guide.Page page = d.pages().get(index);
        String current = text ? page.text() : page.title();
        String key = EditorBook.isKey(current) ? current : prefix(d) + "." + page.id() + (text ? ".text" : ".title");
        List<Guide.Page> pages = new ArrayList<>(d.pages());
        pages.set(index, new Guide.Page(page.id(), text ? page.title() : key, text ? key : page.text()));
        String line = tr(text ? "rysquests.ed.log.page_text" : "rysquests.ed.log.page_title", editor.text(page.title()));
        editor.type("page-" + (text ? "text" : "title") + ":" + d.id() + ":" + page.id(), scope(d), line, dr -> withPages(dr, data, d, pages).withText(key, value));
    }

    private void addPage(Doc d) {
        EditorBook editor = book();
        QuestBook data = editor.data();
        int number = d.pages().size() + 1;
        while (hasPage(d, "page_" + number)) {
            number++;
        }
        String id = "page_" + number;
        String prefix = prefix(d) + "." + id;
        List<Guide.Page> pages = new ArrayList<>(d.pages());
        int at = Math.min(pages.size(), pageIndex + 1);
        pages.add(at, new Guide.Page(id, prefix + ".title", prefix + ".text"));
        String title = tr("rysquests.ed.guide.new_page");
        editor.edit(scope(d), tr("rysquests.ed.log.new_page", title), dr -> withPages(dr, data, d, pages).withText(prefix + ".title", title).withText(prefix + ".text", ""));
        pageIndex = at;
        fields.clear();
    }

    private static boolean hasPage(Doc d, String id) {
        return d.pages().stream().anyMatch(page -> page.id().equals(id));
    }

    // --- drawing ---

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        EditorBook editor = book();
        QuestBook book = editor.view();
        Doc d = doc(editor.data());
        if (doc != null && d == null) {
            doc = null;
        }
        renderSide(ui, editor, book, d, x, y, h);
        int mx = x + SIDE_W;
        int mw = w - SIDE_W - PREVIEW_W;
        if (d == null) {
            ui.label(tr(book.guides().isEmpty() ? "rysquests.ed.guide.none" : "rysquests.ed.guide.pick"), mx + 20, y + 30, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        pageIndex = Math.clamp(pageIndex, 0, Math.max(0, d.pages().size() - 1));
        if (props) {
            renderProps(ui, editor, d, mx, y, mw);
        } else if (!d.pages().isEmpty()) {
            renderPage(ui, editor, d, mx, y, mw, h);
        }
        renderPreview(ui, editor, d, x + w - PREVIEW_W, y, PREVIEW_W, h);
        choices.render(ui, y + h);
    }

    private void renderSide(Ui ui, EditorBook editor, QuestBook book, @Nullable Doc d, int x, int y, int h) {
        ui.rect(x, y, SIDE_W, h, ui.c("sidebar.bg"));
        ui.rect(x + SIDE_W - 1, y, 1, h, ui.c("panel.border"));
        float px = x + 8;
        float pw = SIDE_W - 17;
        float pen = y + 10;
        if (d == null) {
            ui.caps(tr("rysquests.ed.guide.all"), x + 14, y + 20, ui.c("text.dim"));
            pen = y + 40;
            List<Guide> guides = new ArrayList<>(book.guides().values());
            guides.sort(Comparator.comparing(Guide::title));
            for (Guide guide : guides) {
                boolean over = ui.over(px, pen, pw, 34);
                if (over) {
                    ui.fill(px, pen, pw, 34, 7, ui.c("button.bg"));
                }
                float rowY = pen;
                guide.icon().ifPresent(icon -> ui.item(TaskText.stackOf(icon), px + 8, rowY + 9, 16));
                ui.labelFitTip(guide.title(), px + 32, pen + 17, pw - 32 - 40, 12.5f, Ui.REGULAR, ui.c(over ? "text.primary" : "text.secondary"));
                ui.labelRight(String.valueOf(guide.pages().size()), px + pw - 8, pen + 17, 11, Ui.REGULAR, ui.c("text.muted"));
                ResourceLocation id = guide.id();
                ui.hit(px, pen, pw, 34, () -> select(id, 0));
                pen += 34 + 1;
            }
            addRow(ui, tr("rysquests.ed.guide.add"), px, pen, pw, this::newGuide);
            return;
        }
        boolean overBack = ui.over(px, pen, 160, 22);
        ui.icon(Icons.Shape.CHEVRON_LEFT, px + 6, pen + 4, 14, ui.c(overBack ? "text.primary" : "link"));
        ui.label(tr(d.section() ? "rysquests.ed.guide.all_tabs" : "rysquests.ed.guide.all_back"), px + 26, pen + 11, 12.5f, Ui.MEDIUM, ui.c(overBack ? "text.primary" : "link"));
        ui.hit(px, pen, 160, 22, () -> {
            if (d.section()) {
                EditorPages.current = EditorPages.TABS;
            }
            doc = null;
        });
        pen += 22 + 10;
        ItemStack icon = d.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        EdUi.plate(ui, icon, Icons.Shape.BOOK, px + 4, pen, 34);
        String title = editor.text(d.title());
        ui.labelFitTip(title, px + 4 + 34 + 10, pen + 9, pw - 34 - 10 - 34, 14, Ui.SEMIBOLD, ui.c("text.primary"));
        String sub = d.category().map(editor::text).filter(s -> !s.isBlank()).map(c -> c + " · ").orElse("") + Ui.counted("rysquests.ed.guide.pages", d.pages().size());
        ui.labelFit(sub, px + 4 + 34 + 10, pen + 26, pw - 34 - 10 - 34, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        boolean overPen = ui.over(px + pw - 26, pen + 4, 26, 26);
        ui.icon(Icons.Shape.PENCIL, px + pw - 20, pen + 10, 13, ui.c(props || overPen ? "text.primary" : "text.muted"));
        ui.tooltipIfOver(px + pw - 26, pen + 4, 26, 26, Component.translatable("rysquests.ed.guide.props"));
        ui.hit(px + pw - 26, pen + 4, 26, 26, () -> props = !props);
        pen += 34 + 12;
        float listTop = pen;
        float listH = y + h - listTop - 8;
        ui.clip(x, Math.round(listTop), SIDE_W - 1, Math.round(listH));
        int offset = listScroll.offset();
        pen -= offset;
        for (int i = 0; i < d.pages().size(); i++) {
            Guide.Page page = d.pages().get(i);
            boolean on = i == pageIndex && !props;
            boolean over = ui.over(px, pen, pw, 34);
            if (on || over) {
                ui.fill(px, pen, pw, 34, 7, ui.c(on ? "tab.active.bg" : "button.bg"));
            }
            ui.icon(Icons.Shape.GRIP, px + 4, pen + 11, 12, ui.c("text.dim"));
            ui.labelRight(String.valueOf(i + 1), px + 4 + 12 + 20, pen + 17, 11, Ui.REGULAR, ui.c("text.dim"));
            ui.labelFitTip(editor.text(page.title()), px + 52, pen + 17, pw - 52 - 8, 12.5f, on ? Ui.MEDIUM : Ui.REGULAR, ui.c(on || over ? "text.primary" : "text.secondary"));
            int index = i;
            ui.hit(px, pen, pw, 34, () -> {
                pageIndex = index;
                props = false;
                fields.clear();
            });
            pen += 34;
        }
        addRow(ui, tr("rysquests.ed.guide.add_page"), px, pen, pw, () -> addPage(d));
        pen += 34;
        ui.unclip();
        listScroll.content(pen + offset - listTop, listH);
    }

    private static void addRow(Ui ui, String label, float x, float y, float w, Runnable action) {
        boolean over = ui.over(x, y, w, 34);
        int fg = ui.c(over ? "text.primary" : "text.muted");
        ui.icon(Icons.Shape.PLUS, x + 12, y + 11, 12, fg);
        ui.label(label, x + 32, y + 17, 12.5f, Ui.REGULAR, fg);
        ui.hit(x, y, w, 34, action);
    }

    private void renderPage(Ui ui, EditorBook editor, Doc d, int x, int y, int w, int h) {
        Guide.Page page = d.pages().get(pageIndex);
        float px = x + 18;
        float pw = w - 36;
        float pen = y + 14;
        EdUi.input(ui, fields, "page-title:" + d.id() + ":" + page.id(), px, pen, pw, 38, 14, Ui.SEMIBOLD, editor.text(page.title()), tr("rysquests.ed.guide.page_title"),
                value -> typePage(pageIndex, false, value));
        pen += 38 + 10;
        String textId = "page-text:" + d.id() + ":" + page.id();
        TextArea area = fields.area(textId, true, 13, Ui.REGULAR, editor.text(page.text()), value -> typePage(pageIndex, true, value));
        float row = px;
        String[][] tools = {{"item", "HAND"}, {"recipe", "LIST"}, {"image", "MAP"}, {"quest", "FLAG"}, {"link", "ROUTE"}, {"ponder", "PLAY"}, {"bold", ""}, {"warning", "WARN"}};
        for (String[] tool : tools) {
            String label = tr("rysquests.ed.guide.insert." + tool[0]);
            Icons.Shape icon = tool[1].isEmpty() ? null : Icons.Shape.valueOf(tool[1]);
            float bw = EdUi.buttonWidth(ui, label, icon, true);
            if (row + bw > px + pw) {
                row = px;
                pen += EdUi.SM_H + 6;
            }
            float bx = row;
            float by = pen;
            row += EdUi.button(ui, label, icon, row, pen, true, false, false, () -> insert(tool[0], area, bx, by + EdUi.SM_H + 4)) + 6;
        }
        Draw.info(ui.g, Math.round((row + 4) / 2f), Math.round((pen + 7) / 2f), ui.c("text.muted"));
        ui.tooltipIfOver(row, pen, 20, EdUi.SM_H, Component.translatable("rysquests.ed.guide.markup_help"));
        pen += EdUi.SM_H + 12;
        float ah = y + h - 14 - pen;
        ui.box(px, pen, pw, ah, 9, ui.c("input.bg"), ui.c(area.focused() ? "text.secondary" : "panel.border"));
        area.render(ui, px + 14, pen + 12, pw - 28, ah - 24, ui.c("text.body"), tr("rysquests.ed.guide.page_text"));
        ui.hit(px, pen, pw, ah, () -> fields.click(area));
    }

    /** The insert buttons: from the hand where an item is meant, from a list where a quest or a guide is. */
    private void insert(String kind, TextArea area, float x, float y) {
        ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        String item = held.isEmpty() ? "minecraft:stone" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        fields.blur();
        area.focus(true);
        switch (kind) {
            case "item" -> area.insert("[[item:" + item + "]]");
            case "recipe" -> area.insert("\n[[recipe:" + item + "]]\n");
            case "ponder" -> area.insert("[[ponder:" + item + "]]");
            case "image" -> area.insert("\n[[image:" + EditorBook.namespace(book().data()) + ":textures/guides/picture.png]]\n");
            case "bold" -> area.surround("**", "**");
            case "warning" -> area.surround("!!", "!!");
            case "quest" -> {
                List<Quest> quests = book().view().quests().values().stream().filter(q -> q.options().live()).sorted(Comparator.comparing(Quest::title)).limit(80).toList();
                choices.toggle("insert-quest", x, y, 280, quests.stream().map(Quest::title).toList(), -1, index -> {
                    area.focus(true);
                    area.insert("[[quest:" + quests.get(index).id() + "]]");
                    typePage(pageIndex, true, area.text());
                });
                return;
            }
            case "link" -> {
                List<Guide> guides = book().view().guides().values().stream().sorted(Comparator.comparing(Guide::title)).toList();
                choices.toggle("insert-link", x, y, 280, guides.stream().map(Guide::title).toList(), -1, index -> {
                    area.focus(true);
                    area.insert("[[guide:" + guides.get(index).id() + "]]");
                    typePage(pageIndex, true, area.text());
                });
                return;
            }
            default -> {
            }
        }
        typePage(pageIndex, true, area.text());
    }

    /** The guide's own fields: title, category, icon from the hand. */
    private void renderProps(Ui ui, EditorBook editor, Doc d, int x, int y, int w) {
        float px = x + 18;
        float pw = w - 36;
        float pen = y + 14;
        ui.label(tr("rysquests.ed.guide.props"), px, pen + 10, 17, Ui.DISPLAY, ui.c("text.primary"));
        pen += 34;
        pen += EdUi.field(ui, fields, "guide-title:" + d.id(), tr("rysquests.ed.guide.title"), px, pen, pw, EdUi.INPUT_H, editor.text(d.title()), "",
                value -> typeDocString(d, "title", value)) + 12;
        if (!d.section()) {
            pen += EdUi.field(ui, fields, "guide-category:" + d.id(), tr("rysquests.ed.guide.category"), px, pen, pw, EdUi.INPUT_H, d.category().map(editor::text).orElse(""), "",
                    value -> typeDocString(d, "category", value)) + 12;
        }
        pen += EdUi.label(ui, tr("rysquests.ed.guide.icon"), px, pen);
        EdUi.plate(ui, d.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), Icons.Shape.BOOK, px, pen, 46);
        EdUi.button(ui, tr("rysquests.ed.chapter.icon"), Icons.Shape.HAND, px + 56, pen + 9, true, false, false, () -> {
            var player = Minecraft.getInstance().player;
            if (player != null && !player.getMainHandItem().isEmpty()) {
                ResourceLocation item = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
                put(d, tr("rysquests.ed.log.guide_icon", editor.text(d.title())), d.pages(), Optional.of(item));
            }
        });
    }

    private void typeDocString(Doc d, String which, String value) {
        EditorBook editor = book();
        String current = which.equals("title") ? d.title() : d.category().orElse("");
        String key = EditorBook.isKey(current) ? current : prefix(d) + "." + which;
        String title = which.equals("title") ? key : d.title();
        Optional<String> category = which.equals("category") ? Optional.of(key) : d.category();
        QuestBook data = editor.data();
        editor.type("guide-" + which + ":" + d.id(), scope(d), tr("rysquests.ed.log.guide_" + which, editor.text(d.title())), dr -> {
            var out = dr.withText(key, value);
            if (d.section()) {
                PagesSection old = (PagesSection) data.sections().get(d.id());
                return out.withSection(new PagesSection(old.id(), title, old.shortTitle(), old.subtitle(), old.icon(), old.pages()));
            }
            Guide old = data.guides().get(d.id());
            return out.withGuide(new Guide(old.id(), title, old.icon(), category, old.stage(), old.stageTitle(), old.pages()));
        });
    }

    /** "Так увидит игрок": the page as the player's guide view draws it. */
    private void renderPreview(Ui ui, EditorBook editor, Doc d, int x, int y, int w, int h) {
        ui.rect(x, y, w, h, ui.c("panel.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float px = x + 1 + 18;
        float pw = w - 1 - 36;
        float pen = y + 16;
        ui.caps(tr("rysquests.ed.guide.as_player"), px, pen + 7, ui.c("text.dim"));
        pen += 26;
        if (d.pages().isEmpty()) {
            return;
        }
        Guide.Page page = d.pages().get(pageIndex);
        ui.clip(x + 1, Math.round(pen), w - 1, Math.round(y + h - pen));
        int offset = previewScroll.offset();
        float top = pen;
        pen -= offset;
        ui.label(editor.text(page.title()), px, pen + 11, 18, Ui.DISPLAY, ui.c("text.primary"));
        float end = screen.guidesTab().guidePage.renderBody(ui, editor.text(page.text()), px, pen + 32, pw);
        ui.unclip();
        previewScroll.content(end + offset - top, y + h - top);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        previewScroll.wheel(amount, 40);
        listScroll.wheel(amount, 34);
    }

    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        return new int[] {x + SIDE_W, y, w - SIDE_W - PREVIEW_W, h};
    }

    @Override
    public void closed() {
        fields.blur();
    }

    // --- tests ---

    void writeForTest(String title, String text) {
        typePage(pageIndex, false, title);
        book().endTyping();
        typePage(pageIndex, true, text);
        book().endTyping();
    }

    @Nullable
    ResourceLocation docForTest() {
        return doc;
    }
}
