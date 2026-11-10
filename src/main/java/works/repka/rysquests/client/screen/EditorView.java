package works.repka.rysquests.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientAssets;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.markup.Markup;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.GuideRef;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.network.EditorSavePayload;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;

/**
 * Edits a guide or a section of pages: the shell of {@code design/PageEditor.dc.html} with a list of pages, the page as
 * text with insert buttons, and a preview on the right, as {@code docs/11-next-plan.md} describes after ADR-29.
 */
final class EditorView {
    private static final int BAR_H = 51;
    private static final int LEFT_W = 224;
    private static final int RIGHT_W = 340;
    private static final int PAGE_ROW = 28;
    private static final int PAGE_STEP = 30;
    private static final int PICK_ROW = 32;
    private static final int PICK_STEP = 37;
    private static final int UNDO_LIMIT = 100;
    private static final long UNDO_PAUSE_MS = 400;
    private static final int IMAGE_WIDTH = 960;
    private static final int MAX_SCREENSHOTS = 12;
    private static final int WHEEL = 48;
    private static final String SCREENSHOTS = "screenshots";

    private enum Panel {
        PREVIEW, GUIDE, ITEMS, RECIPES, IMAGES, QUESTS, LINKS
    }

    /** A line of a picker: what it shows and what choosing it does. */
    private record Choice(String label, String note, ItemStack icon, Runnable action) {
    }

    private final BookScreen screen;
    private final GuidePageView renderer;
    private final Guides guides;
    private final GuideDraft draft;
    private final TextArea pageTitle = new TextArea(false, 15, 22, Ui.DISPLAY, this::beforeEdit);
    private final TextArea pageText = new TextArea(true, 12.5f, 19, Ui.REGULAR, this::beforeEdit);
    private final TextArea guideTitle = new TextArea(false, 12, 18, Ui.REGULAR, this::beforeEdit);
    private final TextArea guideCategory = new TextArea(false, 12, 18, Ui.REGULAR, this::beforeEdit);
    private final TextArea filter = new TextArea(false, 12, 18, Ui.REGULAR, () -> { });
    private final Scroll listScroll = new Scroll();
    private final Scroll panelScroll = new Scroll();
    private final Deque<GuideDraft.State> undo = new ArrayDeque<>();
    private final Deque<GuideDraft.State> redo = new ArrayDeque<>();
    private GuideDraft.State saved;
    private int page;
    private Panel panel = Panel.PREVIEW;
    private boolean playerView;
    private boolean saving;
    private boolean exitArmed;
    private long lastEdit;
    @Nullable
    private String status;
    private boolean statusBad;
    private int leftEdge;
    private int rightEdge;
    private double pointerX;
    private double pointerY;
    private List<Choice> screenshots = List.of();

    EditorView(BookScreen screen, Guides guides, GuidePageView renderer, GuideDraft draft, boolean isNew) {
        this.screen = screen;
        this.guides = guides;
        this.renderer = renderer;
        this.draft = draft;
        saved = isNew ? null : draft.state;
        guideTitle.setText(draft.state.title().value());
        guideCategory.setText(draft.state.category().value());
        showPage(0);
        pageText.focus(true);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private void showPage(int index) {
        page = index;
        GuideDraft.Page current = draft.state.pages().get(index);
        pageTitle.setText(current.title().value());
        pageText.setText(current.text().value());
        pageText.place(0);
    }

    // --- edits ---

    /** Runs before a change, and sends what the fields held so far into the draft. Quick typing counts as one step of undo. */
    private void beforeEdit() {
        storeFields();
        long now = Util.getMillis();
        if (now - lastEdit > UNDO_PAUSE_MS) {
            remember();
        }
        lastEdit = now;
        exitArmed = false;
        status = null;
    }

    private void remember() {
        undo.push(draft.state);
        if (undo.size() > UNDO_LIMIT) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void storeFields() {
        draft.setPage(page, pageTitle.text(), pageText.text());
        draft.setTitle(guideTitle.text());
        if (!draft.section) {
            draft.setCategory(guideCategory.text());
        }
    }

    private void restore(GuideDraft.State state) {
        draft.state = state;
        guideTitle.setText(state.title().value());
        guideCategory.setText(state.category().value());
        int index = Math.min(page, state.pages().size() - 1);
        int keep = pageText.cursor();
        showPage(index);
        pageText.place(keep);
    }

    private void stepUndo() {
        storeFields();
        if (!undo.isEmpty()) {
            redo.push(draft.state);
            restore(undo.pop());
        }
    }

    private void stepRedo() {
        storeFields();
        if (!redo.isEmpty()) {
            undo.push(draft.state);
            restore(redo.pop());
        }
    }

    private void addPage() {
        storeFields();
        remember();
        showPage(draft.addPage(page));
        focus(pageTitle);
    }

    private void removePage(int index) {
        storeFields();
        if (draft.state.pages().size() < 2) {
            return;
        }
        remember();
        draft.removePage(index);
        showPage(Math.min(page, draft.state.pages().size() - 1));
    }

    private void movePage(int delta) {
        storeFields();
        remember();
        showPage(draft.movePage(page, delta));
    }

    private void selectPage(int index) {
        storeFields();
        showPage(index);
    }

    private void focus(TextArea field) {
        for (TextArea other : List.of(pageTitle, pageText, guideTitle, guideCategory, filter)) {
            other.focus(other == field);
        }
    }

    private boolean dirty() {
        return !draft.state.equals(saved) || !draft.images.isEmpty();
    }

    private int changedFiles() {
        if (!dirty()) {
            return 0;
        }
        return 1 + (draft.texts().isEmpty() ? 0 : 1) + draft.images.size();
    }

    // --- saving and leaving ---

    void save() {
        storeFields();
        if (saving) {
            return;
        }
        if (draft.state.title().value().isBlank()) {
            show(tr("rysquests.editor.error.no_title"), true);
            return;
        }
        if (!dirty()) {
            show(tr("rysquests.editor.no_changes"), false);
            return;
        }
        saving = true;
        show(tr("rysquests.editor.saving"), false);
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        PacketDistributor.sendToServer(new EditorSavePayload(
                draft.section ? Optional.empty() : Optional.of(draft.toGuide()),
                draft.section ? Optional.of(draft.toSection()) : Optional.empty(),
                language, draft.texts(), Map.copyOf(draft.images)));
    }

    void onResult(boolean ok, String reason) {
        saving = false;
        if (ok) {
            saved = draft.state;
            draft.images.clear();
            show(tr("rysquests.editor.saved"), false);
        } else {
            show(Component.translatable("rysquests.editor.failed", tr(reason)).getString(), true);
        }
    }

    boolean savedForTest() {
        return !saving && saved != null && saved.equals(draft.state) && draft.images.isEmpty();
    }

    @Nullable
    String statusForTest() {
        return status;
    }

    private void show(String text, boolean bad) {
        status = text;
        statusBad = bad;
    }

    void requestExit() {
        storeFields();
        if (dirty() && !exitArmed) {
            exitArmed = true;
            show(tr("rysquests.editor.confirm_exit"), true);
            return;
        }
        screen.closeEditor();
    }

    // --- inserts ---

    private void insertLink(String kind, String target, String label) {
        focus(pageText);
        pageText.insert("[[" + kind + ":" + target + (label.isEmpty() ? "" : "|" + label) + "]]");
        panel = Panel.PREVIEW;
    }

    private void insertBlock(String kind, String target) {
        focus(pageText);
        String before = pageText.text().substring(0, pageText.cursor());
        pageText.insert((before.isEmpty() || before.endsWith("\n") ? "" : "\n") + "[[" + kind + ":" + target + "]]\n");
        panel = Panel.PREVIEW;
    }

    private void openPicker(Panel picker) {
        panel = picker;
        filter.setText("");
        panelScroll.reset();
        focus(filter);
        if (picker == Panel.IMAGES) {
            screenshots = findScreenshots();
        }
    }

    private List<Choice> choices() {
        String query = filter.text().trim().toLowerCase(Locale.ROOT);
        List<Choice> all = switch (panel) {
            case ITEMS -> inventory(false);
            case RECIPES -> inventory(true);
            case IMAGES -> screenshots;
            case QUESTS -> quests();
            case LINKS -> links();
            default -> List.of();
        };
        return all.stream().filter(choice -> query.isEmpty() || choice.label.toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private List<Choice> inventory(boolean recipe) {
        var player = Minecraft.getInstance().player;
        Map<ResourceLocation, ItemStack> stacks = new LinkedHashMap<>();
        stacks.put(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()), player.getMainHandItem());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                stacks.putIfAbsent(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack);
            }
        }
        List<Choice> out = new ArrayList<>();
        stacks.forEach((id, stack) -> {
            if (!stack.isEmpty()) {
                out.add(new Choice(stack.getHoverName().getString(), "", stack, recipe ? () -> insertBlock("recipe", id.toString()) : () -> insertLink("item", id.toString(), "")));
            }
        });
        return out;
    }

    private List<Choice> quests() {
        List<Choice> out = new ArrayList<>();
        for (Quest quest : ClientBook.current().quests().values()) {
            if (BookScreen.inStory(quest)) {
                out.add(new Choice(tr(quest.title()), "", quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), () -> insertLink("quest", quest.id().toString(), "")));
            }
        }
        return out;
    }

    private List<Choice> links() {
        List<Choice> out = new ArrayList<>();
        for (Guide guide : ClientBook.current().guides().values()) {
            ItemStack icon = guide.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
            out.add(new Choice(tr(guide.title()), "", icon, () -> insertLink("guide", guide.id().toString(), "")));
            for (Guide.Page each : guide.pages()) {
                out.add(new Choice(tr(each.title()), tr(guide.title()), icon, () -> insertLink("guide", guide.id() + "#" + each.id(), "")));
            }
        }
        return out;
    }

    private List<Choice> findScreenshots() {
        Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve(SCREENSHOTS);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparingLong(EditorView::modified).reversed())
                    .limit(MAX_SCREENSHOTS)
                    .map(file -> new Choice(file.getFileName().toString(), "", ItemStack.EMPTY, () -> addPicture(file)))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Copying a screenshot takes a while, so it happens in the background. */
    void addPicture(Path file) {
        panel = Panel.PREVIEW;
        show(tr("rysquests.editor.adding_picture"), false);
        CompletableFuture.supplyAsync(() -> scaled(file), Util.backgroundExecutor()).whenCompleteAsync((png, failure) -> {
            if (failure != null) {
                show(tr("rysquests.editor.error.picture"), true);
                return;
            }
            status = null;
            ResourceLocation id = nextImageId();
            remember();
            draft.images.put(id, png);
            ClientAssets.addImage(id, png);
            insertBlock("image", id.toString());
        }, Minecraft.getInstance());
    }

    private ResourceLocation nextImageId() {
        String base = "textures/guides/" + draft.id.getPath().replace('/', '_') + "_";
        int number = draft.images.size() + 1;
        ResourceLocation id = draft.id.withPath(base + number + ".png");
        while (pageText.text().contains(id.toString()) || draft.images.containsKey(id)) {
            number++;
            id = draft.id.withPath(base + number + ".png");
        }
        return id;
    }

    private static byte[] scaled(Path file) {
        try (var in = Files.newInputStream(file); NativeImage source = NativeImage.read(in)) {
            if (source.getWidth() <= IMAGE_WIDTH) {
                return source.asByteArray();
            }
            int height = Math.round(source.getHeight() * (float) IMAGE_WIDTH / source.getWidth());
            try (NativeImage target = new NativeImage(source.format(), IMAGE_WIDTH, height, false)) {
                source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), target);
                return target.asByteArray();
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // --- drawing ---

    void render(Ui ui, int x, int y, int w, int h) {
        renderBar(ui, x, y, w);
        int bodyY = y + BAR_H;
        int bodyH = h - BAR_H;
        if (playerView) {
            renderPlayerView(ui, x, bodyY, w, bodyH);
            return;
        }
        leftEdge = x + LEFT_W;
        rightEdge = x + w - RIGHT_W;
        renderList(ui, x, bodyY, LEFT_W, bodyH);
        renderPage(ui, leftEdge, bodyY, rightEdge - leftEdge, bodyH);
        renderRight(ui, rightEdge, bodyY, RIGHT_W, bodyH);
    }

    private void renderBar(Ui ui, int x, int y, int w) {
        ui.fillCorners(x, y, w, BAR_H, 13, ui.c("sidebar.bg"), true, true, false, false);
        ui.rect(x, y + BAR_H - 1, w, 1, ui.c("panel.border"));
        float cy = y + (BAR_H - 1) / 2f;
        BookScreen.renderBrand(ui, x + 18, cy);
        float pen = x + 18 + 36 + ui.width("RYSQuests", 16, Ui.DISPLAY) + 10;
        pen += ui.pill(tr(draft.section ? "rysquests.editor.badge_section" : "rysquests.editor.badge"), pen, cy, 11, Ui.SEMIBOLD, 8, 20, 10, ui.warmBorder(0.13f), ui.c("accent")) + 18;
        pen = barButton(ui, tr("rysquests.editor.new_page"), pen, cy, this::addPage) + 6;
        pen = barButton(ui, tr("rysquests.editor.player_view"), pen, cy, () -> {
            storeFields();
            playerView = !playerView;
        }) + 12;
        float right = x + w - 18;
        String exit = tr("rysquests.editor.exit");
        float exitW = ui.buttonWidth(exit, 11.5f, Ui.REGULAR, 10) + 2;
        right -= exitW;
        ui.button(exit, right, cy - 15, exitW, 30, 8, 11.5f, Ui.REGULAR, 10, Ui.Style.PLAIN, this::requestExit);
        String save = tr("rysquests.editor.save");
        float saveW = ui.buttonWidth(save, 11.5f, Ui.SEMIBOLD, 12);
        right -= 6 + saveW;
        ui.button(save, right, cy - 15, saveW, 30, 8, 11.5f, Ui.SEMIBOLD, 12, Ui.Style.ACCENT, this::save);
        int files = changedFiles();
        String note = status != null ? status : files > 0 ? Ui.counted("rysquests.plural.file_changed", files) : "";
        right -= 12;
        float noteW = Math.min(ui.width(note, 11.5f, Ui.REGULAR), Math.max(1, right - pen - 78));
        ui.labelFitTip(note, right - noteW, cy, noteW, 11.5f, Ui.REGULAR, ui.c(statusBad && status != null ? "state.bad" : "text.muted"));
        float undoX = right - noteW - (note.isEmpty() ? 0 : 12) - 66;
        iconButton(ui, Icons.Shape.UNDO, undoX, cy, !undo.isEmpty(), this::stepUndo);
        iconButton(ui, Icons.Shape.REDO, undoX + 36, cy, !redo.isEmpty(), this::stepRedo);
    }

    private static float barButton(Ui ui, String label, float x, float cy, Runnable action) {
        float w = ui.buttonWidth(label, 11.5f, Ui.REGULAR, 10) + 2;
        ui.button(label, x, cy - 15, w, 30, 8, 11.5f, Ui.REGULAR, 10, Ui.Style.PLAIN, action);
        return x + w;
    }

    private static void iconButton(Ui ui, Icons.Shape shape, float x, float cy, boolean enabled, Runnable action) {
        ui.box(x, cy - 15, 30, 30, 8, enabled && ui.over(x, cy - 15, 30, 30) ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        ui.icon(shape, x + 9, cy - 6, 12, ui.c(enabled ? "button.fg" : "text.dim"));
        if (enabled) {
            ui.hit(x, cy - 15, 30, 30, action);
        }
    }

    private void renderList(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.columnBg(), false, false, false, true);
        ui.rect(x + w - 1, y, 1, h, ui.c("panel.border"));
        float left = x + 10;
        float inner = w - 21;
        String heading = tr(draft.section ? "rysquests.editor.pages_of_section" : "rysquests.editor.pages_of_guide");
        ui.caps(heading, left + 6, y + 19, ui.c("text.dim"));
        int boxH = 66;
        float listTop = y + 32;
        float listH = y + h - 12 - boxH - 8 - listTop;
        List<GuideDraft.Page> pages = draft.state.pages();
        listScroll.content(pages.size() * PAGE_STEP - 2, Math.round(listH));
        int offset = listScroll.offset();
        ui.clip(Math.round(left), Math.round(listTop), Math.round(inner), Math.round(listH));
        for (int i = 0; i < pages.size(); i++) {
            float rowY = listTop + i * PAGE_STEP - offset;
            if (rowY + PAGE_ROW > listTop && rowY < listTop + listH) {
                renderListRow(ui, pages, i, left, rowY, inner);
            }
        }
        ui.unclip();
        float boxY = y + h - 12 - boxH;
        ui.box(left, boxY, inner, boxH, 8, ui.c("slot.bg"), ui.mix("slot.bg", "panel.border", 0.6f));
        ui.labelFitTip(draft.filePath(), left + 10, boxY + 9 + 7, inner - 20, 10.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.labelFit("lang/" + Minecraft.getInstance().getLanguageManager().getSelected() + ".json", left + 10, boxY + 9 + 7 + 16, inner - 20, 10.5f, Ui.REGULAR, ui.c("text.dim"));
        ui.labelFit(draft.id.getNamespace(), left + 10, boxY + 9 + 7 + 32, inner - 20, 10.5f, Ui.REGULAR, ui.c("text.dim"));
    }

    private void renderListRow(Ui ui, List<GuideDraft.Page> pages, int index, float x, float y, float w) {
        boolean selected = index == page;
        boolean over = ui.over(x, y, w, PAGE_ROW);
        if (selected) {
            ui.box(x, y, w, PAGE_ROW, 7, ui.c("button.bg"), ui.c("button.border"));
        } else if (over) {
            ui.fill(x, y, w, PAGE_ROW, 7, ui.c("slot.hover"));
        }
        float cy = y + PAGE_ROW / 2f;
        String title = selected ? pageTitle.text() : pages.get(index).title().value();
        String shown = title.isBlank() ? tr("rysquests.editor.untitled") : title;
        boolean changed = saved == null || index >= saved.pages().size() || !saved.pages().get(index).equals(pages.get(index));
        ui.hit(x, y, w, PAGE_ROW, () -> selectPage(index));
        boolean removable = over && pages.size() > 1;
        float reserve = (changed ? 14 : 0) + (removable ? 16 : 0);
        ui.labelFitTip(shown, x + 8, cy, w - 16 - reserve, 12, Ui.REGULAR, ui.c(selected ? "text.primary" : "button.fg"));
        if (changed) {
            ui.circle(x + w - (removable ? 30 : 14), cy - 3, 6, ui.c("accent"));
        }
        if (removable) {
            ui.icon(Icons.Shape.CROSS, x + w - 17, cy - 5, 10, ui.c("text.muted"));
            ui.hit(x + w - 21, y, 19, PAGE_ROW, () -> removePage(index));
        }
    }

    private void renderPage(Ui ui, int x, int y, int w, int h) {
        ui.rect(x, y, w, h, ui.mix("slot.bg", "panel.bg", 0.3f));
        float left = x + 18;
        float inner = w - 36;
        float cursor = y + 12;
        pageTitle.render(ui, left, cursor, inner, 22, ui.c("text.primary"), tr("rysquests.editor.page_title"));
        ui.hit(left, cursor, inner, 22, () -> {
            focus(pageTitle);
            pageTitle.click(pointerX, pointerY);
        });
        cursor += 22 + 8;
        float pen = left;
        pen = insertButton(ui, tr("rysquests.editor.insert.item"), pen, cursor, () -> openPicker(Panel.ITEMS));
        pen = insertButton(ui, tr("rysquests.editor.insert.recipe"), pen, cursor, () -> openPicker(Panel.RECIPES));
        pen = insertButton(ui, tr("rysquests.editor.insert.image"), pen, cursor, () -> openPicker(Panel.IMAGES));
        pen = insertButton(ui, tr("rysquests.editor.insert.quest"), pen, cursor, () -> openPicker(Panel.QUESTS));
        pen = insertButton(ui, tr("rysquests.editor.insert.link"), pen, cursor, () -> openPicker(Panel.LINKS));
        insertButton(ui, tr("rysquests.editor.insert.bold"), pen, cursor, () -> {
            focus(pageText);
            pageText.surround("**", "**");
        });
        cursor += 30 + 8;
        List<String> issues = issues();
        List<String> shown = issues.size() > 3 ? issues.subList(0, 3) : issues;
        float noteH = 16 + 15 * (shown.size() + 1);
        float noteY = y + h - 12 - noteH;
        float areaH = noteY - 8 - cursor;
        boolean focused = pageText.focused();
        ui.box(left, cursor, inner, areaH, 9, ui.c("slot.bg"), focused ? ui.c("card.border") : ui.mix("slot.bg", "panel.border", 0.6f));
        pageText.render(ui, left + 12, cursor + 10, inner - 24, areaH - 20, ui.readingFg(), tr("rysquests.editor.page_text"));
        ui.hit(left, cursor, inner, areaH, () -> {
            focus(pageText);
            pageText.click(pointerX, pointerY);
        });
        ui.box(left, noteY, inner, noteH, 8, ui.c("slot.bg"), ui.mix("slot.bg", "panel.border", 0.6f));
        float line = noteY + 8 + 7.5f;
        for (String issue : shown) {
            ui.labelFitTip(issue, left + 10, line, inner - 20, 11, Ui.REGULAR, ui.c("state.bad"));
            line += 15;
        }
        ui.labelFit(tr("rysquests.editor.hint"), left + 10, line, inner - 20, 11, Ui.REGULAR, ui.c("text.dim"));
    }

    private static float insertButton(Ui ui, String label, float x, float y, Runnable action) {
        float w = ui.buttonWidth(label, 11.5f, Ui.REGULAR, 10) + 2;
        ui.button(label, x, y, w, 30, 8, 11.5f, Ui.REGULAR, 10, Ui.Style.PLAIN, action);
        return x + w + 6;
    }

    /** What is wrong with the page being edited, while the author can still fix it. */
    private List<String> issues() {
        List<String> found = new ArrayList<>();
        for (Markup.Paragraph paragraph : Markup.parse(pageText.text())) {
            for (Markup.Span span : paragraph.spans()) {
                if (span.isLink() && !isKnown(span)) {
                    found.add(Component.translatable("rysquests.editor.issue", span.kind() + ":" + span.target()).getString());
                }
            }
        }
        return found;
    }

    private boolean isKnown(Markup.Span span) {
        if (span.kind().equals("guide")) {
            boolean thisGuide = GuideRef.parse(span.target()).filter(ref -> ref.guide().equals(draft.id)).isPresent();
            if (thisGuide) {
                return true;
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(span.target());
        if (span.kind().equals("image") && id != null && guides.imageSize(id) != null) {
            return true;
        }
        return GuideCheck.valid(ClientBook.current(), span);
    }

    private void renderRight(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, false, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float left = x + 15;
        float inner = w - 30;
        if (panel == Panel.PREVIEW || panel == Panel.GUIDE) {
            List<Kit.Tab> tabs = List.of(
                    new Kit.Tab(tr("rysquests.editor.tab.preview"), -1, panel == Panel.PREVIEW, () -> panel = Panel.PREVIEW),
                    new Kit.Tab(tr(draft.section ? "rysquests.editor.tab.section" : "rysquests.editor.tab.guide"), -1, panel == Panel.GUIDE, () -> panel = Panel.GUIDE));
            ui.tabs(tabs, left, y + 8, 28, inner);
            ui.rect(x + 1, y + 40, w - 1, 1, ui.c("panel.border"));
            if (panel == Panel.PREVIEW) {
                renderPreview(ui, left, y + 52, inner, h - 64);
            } else {
                renderGuideFields(ui, left, y + 52, inner);
            }
            return;
        }
        renderPicker(ui, left, y + 12, inner, h - 24);
    }

    private void renderPreview(Ui ui, float x, float y, float w, float h) {
        int offset = panelScroll.offset();
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        ui.label(pageTitle.text().isBlank() ? tr("rysquests.editor.untitled") : pageTitle.text(), x, y - offset + 11, 18, Ui.DISPLAY, ui.c("text.primary"));
        float end = renderer.renderBody(ui, pageText.text(), x, y - offset + 34, w);
        ui.unclip();
        panelScroll.content(Math.round(end + offset - y), Math.round(h));
    }

    private void renderGuideFields(Ui ui, float x, float y, float w) {
        float cursor = field(ui, tr("rysquests.editor.field.title"), guideTitle, x, y, w);
        if (!draft.section) {
            cursor = field(ui, tr("rysquests.editor.field.category"), guideCategory, x, cursor, w);
        }
        ui.label(tr("rysquests.editor.field.icon"), x, cursor + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        cursor += 18;
        ui.cell(draft.state.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), x, cursor, 40, 40, 10, 24, ui.c("slot.bg"), ui.c("card.border"));
        String take = tr("rysquests.editor.from_hand");
        float takeW = ui.buttonWidth(take, 12, Ui.REGULAR, 12) + 2;
        ui.button(take, x + 50, cursor + 4, takeW, 32, 8, 12, Ui.REGULAR, 12, Ui.Style.PLAIN, this::iconFromHand);
    }

    private void iconFromHand() {
        ItemStack held = Minecraft.getInstance().player.getMainHandItem();
        if (!held.isEmpty()) {
            beforeEdit();
            draft.setIcon(BuiltInRegistries.ITEM.getKey(held.getItem()));
        }
    }

    private float field(Ui ui, String label, TextArea area, float x, float y, float w) {
        ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        ui.box(x, boxY, w, 32, 8, ui.c("slot.bg"), area.focused() ? ui.c("card.border") : ui.c("button.border"));
        area.render(ui, x + 10, boxY + 7, w - 20, 18, ui.c("text.primary"), "");
        ui.hit(x, boxY, w, 32, () -> {
            focus(area);
            area.click(pointerX, pointerY);
        });
        return boxY + 32 + 11;
    }

    private void renderPicker(Ui ui, float x, float y, float w, float h) {
        boolean over = ui.over(x, y, 28, 28);
        ui.box(x, y, 28, 28, 7, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        ui.icon(Icons.Shape.CHEVRON_LEFT, x + 9, y + 9, 10, ui.c("button.fg"));
        ui.hit(x, y, 28, 28, () -> panel = Panel.PREVIEW);
        ui.label(tr("rysquests.editor.pick." + panel.name().toLowerCase(Locale.ROOT)), x + 38, y + 14, 15, Ui.DISPLAY, ui.c("text.primary"));
        float boxY = y + 38;
        ui.box(x, boxY, w, 32, 8, ui.c("slot.bg"), filter.focused() ? ui.c("card.border") : ui.c("button.border"));
        filter.render(ui, x + 10, boxY + 7, w - 20, 18, ui.c("text.primary"), tr("rysquests.editor.filter"));
        ui.hit(x, boxY, w, 32, () -> {
            focus(filter);
            filter.click(pointerX, pointerY);
        });
        float listTop = boxY + 32 + 10;
        float listH = y + h - listTop;
        if (panel == Panel.IMAGES) {
            ui.lines(ui.wrap(tr("rysquests.editor.pick.images_hint"), w, 11, Ui.REGULAR, 3), x, listTop + 14, 14, 11, Ui.REGULAR, ui.c("text.muted"));
            listTop += 36;
            listH -= 36;
        }
        List<Choice> choices = choices();
        if (choices.isEmpty()) {
            ui.label(tr("rysquests.editor.pick.empty"), x, listTop + 12, 12, Ui.REGULAR, ui.c("text.dim"));
            return;
        }
        panelScroll.content(choices.size() * PICK_STEP - 5, Math.round(listH));
        int offset = panelScroll.offset();
        ui.clip(Math.round(x), Math.round(listTop), Math.round(w), Math.round(listH));
        for (int i = 0; i < choices.size(); i++) {
            float rowY = listTop + i * PICK_STEP - offset;
            if (rowY + PICK_ROW > listTop && rowY < listTop + listH) {
                renderChoice(ui, choices.get(i), x, rowY, w);
            }
        }
        ui.unclip();
    }

    private static void renderChoice(Ui ui, Choice choice, float x, float y, float w) {
        boolean over = ui.over(x, y, w, PICK_ROW);
        ui.box(x, y, w, PICK_ROW, 8, over ? ui.c("slot.hover") : ui.c("status.bg"), ui.c("status.border"));
        float cy = y + PICK_ROW / 2f;
        ui.item(choice.icon, x + 9, cy - 8, 16);
        float textX = choice.icon.isEmpty() ? x + 11 : x + 32;
        float noteW = choice.note.isEmpty() ? 0 : Math.min(100, ui.width(choice.note, 11, Ui.REGULAR)) + 8;
        ui.labelFitTip(choice.label, textX, cy, x + w - 9 - noteW - textX, 12, Ui.REGULAR, ui.nameFg());
        if (!choice.note.isEmpty()) {
            ui.labelFit(choice.note, x + w - 9 - noteW + 8, cy, noteW - 8, 11, Ui.REGULAR, ui.c("text.muted"));
        }
        ui.hit(x, y, w, PICK_ROW, choice.action);
    }

    private void renderPlayerView(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("panel.bg"), false, false, true, true);
        int inner = Math.min(760, w - 80);
        float left = x + (w - inner) / 2f;
        int offset = panelScroll.offset();
        ui.clip(x, y, w, h);
        ui.label(pageTitle.text(), left, y + 20 - offset + 11, 18, Ui.DISPLAY, ui.c("text.primary"));
        float end = renderer.renderBody(ui, pageText.text(), left, y + 20 - offset + 36, inner);
        ui.unclip();
        panelScroll.content(Math.round(end + offset - y + 20), h);
    }

    // --- input ---

    void pointer(double mouseX, double mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
    }

    void drag(double mouseX, double mouseY) {
        for (TextArea area : List.of(pageTitle, pageText, guideTitle, guideCategory, filter)) {
            area.drag(mouseX, mouseY);
        }
    }

    void scroll(double mouseX, double mouseY, double amount) {
        if (mouseX < leftEdge) {
            listScroll.wheel(amount, PAGE_STEP);
        } else if (mouseX < rightEdge && !playerView) {
            pageText.wheel(amount);
        } else {
            panelScroll.wheel(amount, WHEEL);
        }
    }

    boolean typed(char c) {
        return pageText.typed(c) || pageTitle.typed(c) || guideTitle.typed(c) || guideCategory.typed(c) || filter.typed(c);
    }

    /** @return whether the key was used; while the editor is open nothing else reacts to typing */
    boolean key(int key) {
        boolean ctrl = Screen.hasControlDown();
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            stepUndo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            stepRedo();
            return true;
        }
        if (Screen.hasAltDown() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            movePage(key == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (panel != Panel.PREVIEW && panel != Panel.GUIDE) {
                panel = Panel.PREVIEW;
                focus(pageText);
            } else if (playerView) {
                playerView = false;
            } else {
                requestExit();
            }
            return true;
        }
        for (TextArea area : List.of(pageTitle, pageText, guideTitle, guideCategory, filter)) {
            if (area.key(key)) {
                return true;
            }
        }
        return true;
    }

    // --- for the autotest ---

    void writeForTest(String title, String text) {
        focus(pageText);
        pageTitle.setText(title);
        pageText.setText(text);
        beforeEdit();
        storeFields();
    }

    void writeGuideForTest(String title, ResourceLocation icon) {
        guideTitle.setText(title);
        storeFields();
        draft.setIcon(icon);
    }

    String pageTextForTest() {
        return pageText.text();
    }

    void undoForTest() {
        stepUndo();
    }

    int pagesForTest() {
        return draft.state.pages().size();
    }

    void selectPageForTest(int index) {
        selectPage(index);
    }

    void addPageForTest() {
        addPage();
    }

    void addImageForTest(ResourceLocation id, byte[] png) {
        draft.images.put(id, png);
        ClientAssets.addImage(id, png);
    }

    ResourceLocation idForTest() {
        return draft.id;
    }
}
