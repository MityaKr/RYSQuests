package works.repka.rysquests.client.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientAssets;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.CardsSection.Card;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.GuideRef;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.MatrixSection.Column;
import works.repka.rysquests.data.MatrixSection.Link;
import works.repka.rysquests.data.MatrixSection.LinkType;
import works.repka.rysquests.data.MatrixSection.Row;
import works.repka.rysquests.data.MatrixSection.Way;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.editor.SectionEdits;
import works.repka.rysquests.network.EditorSavePayload;
import works.repka.rysquests.task.AdvancementTask;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Kit;

/** The constructor of custom sections, from {@code design/SectionEditor.dc.html}: the rail on the left, the table or cards, properties on the right. */
final class SectionEditorView {
    private static final int BAR_H = 51;
    private static final int LEFT_W = 224;
    private static final int RIGHT_W = 340;
    private static final int LABEL_W = 128;
    private static final int CELL_GAP = 6;
    private static final int WAY_H = 26;
    private static final int ADD_H = 22;
    private static final int CELL_MIN = 68;
    private static final int WHEEL = 48;
    private static final int UNDO_LIMIT = 100;
    private static final long UNDO_PAUSE_MS = 400;
    private static final int MAX_RESULTS = 60;
    private static final int MIN_QUERY = 2;
    private static final float OFF_VEIL = 0.4f;

    private enum Tab {
        WAY, ROW, COLUMN, SECTION, CARD
    }

    private record State(@Nullable SectionDef section, Map<String, String> texts, List<String> rail) {
    }

    /** A text field tied to a value of the section. */
    private static final class Bound {
        final TextArea area;
        final Supplier<String> value;
        final Consumer<String> apply;
        String shown;

        Bound(TextArea area, Supplier<String> value, Consumer<String> apply) {
            this.area = area;
            this.value = value;
            this.apply = apply;
            shown = value.get();
            area.setText(shown);
        }
    }

    private record Choice(String label, String note, ItemStack icon, Runnable action) {
    }

    private final BookScreen screen;
    private final Scroll tableScroll = new Scroll();
    private final Scroll leftScroll = new Scroll();
    private final Scroll panelScroll = new Scroll();
    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private final Map<String, Bound> bound = new HashMap<>();
    private final TextArea filter = new TextArea(false, 12, 18, Ui.REGULAR, () -> { });
    private State state;
    private State saved;
    private int row;
    private int column;
    private int way = -1;
    private int card;
    private Tab tab = Tab.SECTION;
    @Nullable
    private String pickerTitle;
    @Nullable
    private Supplier<List<Choice>> picker;
    private String lastQuery = "";
    private List<Choice> found = List.of();
    private boolean playerView;
    private boolean saving;
    private boolean exitArmed;
    private long lastEdit;
    @Nullable
    private String status;
    private boolean statusBad;
    private double pointerX;
    private double pointerY;
    private int leftEdge;
    private int rightEdge;

    SectionEditorView(BookScreen screen, @Nullable SectionDef section) {
        this.screen = screen;
        state = new State(section, Map.of(), initialRail());
        saved = state;
        tab = section instanceof CardsSection ? Tab.CARD : Tab.SECTION;
    }

    private static List<String> initialRail() {
        List<String> rail = ClientBook.current().meta().sections();
        if (!rail.isEmpty()) {
            return rail;
        }
        return ClientBook.current().guides().isEmpty() ? List.of(Chapter.STORY) : List.of(Chapter.STORY, BookMeta.GUIDES);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    // --- texts ---

    private static boolean isKey(String stored) {
        return stored.indexOf(' ') < 0 && stored.indexOf('.') > 0;
    }

    private String text(String stored) {
        if (state.texts.containsKey(stored)) {
            return state.texts.get(stored);
        }
        return isKey(stored) && !Language.getInstance().has(stored) ? "" : tr(stored);
    }

    private void setText(String key, String value) {
        Map<String, String> texts = new HashMap<>(state.texts);
        texts.put(key, value);
        state = new State(state.section, texts, state.rail);
    }

    private void setSection(SectionDef section) {
        state = new State(section, state.texts, state.rail);
    }

    // --- undo ---

    private void beforeEdit() {
        long now = net.minecraft.Util.getMillis();
        if (now - lastEdit > UNDO_PAUSE_MS) {
            remember();
        }
        lastEdit = now;
        exitArmed = false;
        status = null;
    }

    private void remember() {
        undo.push(state);
        if (undo.size() > UNDO_LIMIT) {
            undo.removeLast();
        }
        redo.clear();
    }

    /** A structural change: its own step of undo, and the fields show the new values. */
    private void edit(Runnable change) {
        remember();
        lastEdit = 0;
        exitArmed = false;
        status = null;
        change.run();
        clampSelection();
        bound.clear();
    }

    private void restore(State target) {
        state = target;
        clampSelection();
        bound.clear();
    }

    private void stepUndo() {
        if (!undo.isEmpty()) {
            redo.push(state);
            restore(undo.pop());
        }
    }

    private void stepRedo() {
        if (!redo.isEmpty()) {
            undo.push(state);
            restore(redo.pop());
        }
    }

    private void clampSelection() {
        if (state.section instanceof MatrixSection matrix) {
            row = Math.clamp(row, 0, matrix.rows().size() - 1);
            column = Math.clamp(column, 0, matrix.columns().size() - 1);
            way = Math.min(way, matrix.rows().get(row).ways(matrix.columns().get(column)).size() - 1);
            if (tab == Tab.WAY && way < 0) {
                tab = Tab.SECTION;
            }
        } else if (state.section instanceof CardsSection cards) {
            card = Math.clamp(card, 0, cards.cards().size() - 1);
        }
    }

    private void select(Tab next, int newRow, int newColumn, int newWay) {
        tab = next;
        row = newRow;
        column = newColumn;
        way = newWay;
        picker = null;
        panelScroll.reset();
        bound.clear();
    }

    private boolean dirty() {
        return !state.equals(saved);
    }

    // --- opening, saving, leaving ---

    private void open(SectionDef section) {
        if (dirty() && !exitArmed) {
            exitArmed = true;
            show(tr("rysquests.editor.confirm_exit"), true);
            return;
        }
        exitArmed = false;
        status = null;
        if (section instanceof PagesSection pages) {
            screen.openPagesEditor(pages);
            return;
        }
        undo.clear();
        redo.clear();
        state = new State(section, Map.of(), state.rail);
        saved = state;
        row = 0;
        column = 0;
        way = -1;
        card = 0;
        tab = section instanceof CardsSection ? Tab.CARD : Tab.SECTION;
        picker = null;
        bound.clear();
    }

    private ResourceLocation nextId() {
        ResourceLocation any = ClientBook.current().chapters().keySet().stream().filter(id -> !id.getPath().startsWith("_cards")).findFirst().orElse(ResourceLocation.fromNamespaceAndPath("rysquests", "section"));
        int number = ClientBook.current().sections().size() + 1;
        ResourceLocation id = any.withPath("section_" + number);
        while (ClientBook.current().sections().containsKey(id) || (state.section != null && state.section.id().equals(id))) {
            number++;
            id = any.withPath("section_" + number);
        }
        return id;
    }

    private static String titleKey(ResourceLocation id) {
        return "section." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    private void create(String template) {
        ResourceLocation id = nextId();
        String key = titleKey(id);
        if (template.equals(PagesSection.TEMPLATE)) {
            screen.openNewPagesEditor(id);
            return;
        }
        edit(() -> {
            SectionDef created = template.equals(MatrixSection.TEMPLATE) ? SectionEdits.newMatrix(id, key) : SectionEdits.newCards(id, key);
            List<String> rail = new ArrayList<>(state.rail);
            rail.add(id.toString());
            state = new State(created, Map.of(), rail);
            row = 0;
            column = 0;
            way = -1;
            card = 0;
            tab = Tab.SECTION;
        });
    }

    void save() {
        if (saving) {
            return;
        }
        syncFields();
        if (state.section != null && text(state.section.title()).isBlank()) {
            show(tr("rysquests.editor.error.no_section_title"), true);
            tab = Tab.SECTION;
            bound.clear();
            return;
        }
        if (!dirty()) {
            show(tr("rysquests.editor.no_changes"), false);
            return;
        }
        saving = true;
        show(tr("rysquests.editor.saving"), false);
        Map<String, String> texts = new HashMap<>();
        state.texts.forEach((key, value) -> {
            if (isKey(key)) {
                texts.put(key, value);
            }
        });
        boolean railChanged = !state.rail.equals(ClientBook.current().meta().sections());
        PacketDistributor.sendToServer(new EditorSavePayload(Optional.empty(), Optional.ofNullable(state.section),
                Minecraft.getInstance().getLanguageManager().getSelected(), state.section == null ? Map.of() : texts, Map.of(), railChanged ? state.rail : List.of()));
    }

    void onResult(boolean ok, String reason) {
        saving = false;
        if (ok) {
            saved = state;
            show(tr("rysquests.editor.saved"), false);
        } else {
            show(Component.translatable("rysquests.editor.failed", tr(reason)).getString(), true);
        }
    }

    boolean savedForTest() {
        return !saving && saved.equals(state);
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
        syncFields();
        if (dirty() && !exitArmed) {
            exitArmed = true;
            show(tr("rysquests.editor.confirm_exit"), true);
            return;
        }
        leavePreview();
        screen.closeSectionEditor();
    }

    private void leavePreview() {
        playerView = false;
        BookState.previewSection = null;
        ClientAssets.setPreviewTexts(Map.of());
    }

    private void togglePlayerView() {
        syncFields();
        if (playerView || state.section == null) {
            leavePreview();
        } else {
            playerView = true;
            BookState.previewSection = state.section;
            ClientAssets.setPreviewTexts(state.texts);
        }
    }

    // --- fields ---

    /** Sends what was typed into the section. Runs every frame, which keeps typing free of any wiring. */
    private void syncFields() {
        for (Bound field : bound.values()) {
            if (!field.area.text().equals(field.shown)) {
                field.apply.accept(field.area.text());
                field.shown = field.area.text();
            }
        }
    }

    private Bound field(String id, boolean multiline, Supplier<String> value, Consumer<String> apply) {
        return bound.computeIfAbsent(id, key -> new Bound(multiline ? new TextArea(true, 12, 17, Ui.REGULAR, this::beforeEdit) : new TextArea(false, 12, 18, Ui.REGULAR, this::beforeEdit), value, apply));
    }

    private void focus(TextArea target) {
        bound.values().forEach(field -> field.area.focus(field.area == target));
        filter.focus(filter == target);
    }

    private float inputRow(Ui ui, String label, String id, float x, float y, float w, Supplier<String> value, Consumer<String> apply) {
        Bound bind = field(id, false, value, apply);
        ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        ui.box(x, boxY, w, 32, 8, ui.c("slot.bg"), bind.area.focused() ? ui.c("card.border") : ui.c("button.border"));
        bind.area.render(ui, x + 10, boxY + 7, w - 20, 18, ui.c("text.primary"), "");
        ui.hit(x, boxY, w, 32, () -> {
            focus(bind.area);
            bind.area.click(pointerX, pointerY);
        });
        return boxY + 32 + 11;
    }

    private float textBox(Ui ui, String label, String id, float x, float y, float w, Supplier<String> value, Consumer<String> apply) {
        Bound bind = field(id, true, value, apply);
        ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        float boxH = 74;
        ui.box(x, boxY, w, boxH, 8, ui.c("slot.bg"), bind.area.focused() ? ui.c("card.border") : ui.c("button.border"));
        bind.area.render(ui, x + 10, boxY + 8, w - 20, boxH - 16, ui.c("text.primary"), "");
        ui.hit(x, boxY, w, boxH, () -> {
            focus(bind.area);
            bind.area.click(pointerX, pointerY);
        });
        return boxY + boxH + 11;
    }

    /** A setting that steps through its values on a click. */
    private static float cycleRow(Ui ui, String label, String value, float x, float y, float w, Runnable next) {
        ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        boolean over = ui.over(x, boxY, w, 32);
        ui.box(x, boxY, w, 32, 8, over ? ui.c("slot.hover") : ui.c("slot.bg"), ui.c("button.border"));
        ui.labelFit(value, x + 10, boxY + 16, w - 34, 12, Ui.REGULAR, ui.c("button.fg"));
        ui.icon(Icons.Shape.CHEVRON_DOWN, x + w - 20, boxY + 11.5f, 9, ui.c("text.dim"));
        ui.hit(x, boxY, w, 32, next);
        return boxY + 32 + 11;
    }

    private static float switchRow(Ui ui, String label, boolean on, float x, float y, float w, Runnable toggle) {
        ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        ui.box(x, boxY, w, 32, 8, ui.c("slot.bg"), ui.c("button.border"));
        ui.label(tr(on ? "rysquests.editor.yes" : "rysquests.editor.no"), x + 10, boxY + 16, 12, Ui.REGULAR, ui.c("button.fg"));
        float knobX = x + w - 10 - 28;
        ui.fill(knobX, boxY + 8, 28, 16, 8, ui.c(on ? "accent" : "button.bg"));
        ui.circle(on ? knobX + 14 : knobX + 2, boxY + 10, 12, ui.c(on ? "accent.text" : "text.muted"));
        ui.hit(x, boxY, w, 32, toggle);
        return boxY + 32 + 11;
    }

    private float iconRow(Ui ui, String label, Optional<ResourceLocation> icon, float x, float y, float w, Consumer<ResourceLocation> set) {
        ui.label(label, x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        float boxY = y + 18;
        ui.cell(icon.map(TaskText::stackOf).orElse(ItemStack.EMPTY), x, boxY, 32, 32, 8, 18, ui.c("slot.bg"), ui.c("button.border"));
        String choose = tr("rysquests.editor.choose");
        float chooseW = ui.buttonWidth(choose, 12, Ui.REGULAR, 12) + 2;
        ui.button(choose, x + 40, boxY, chooseW, 32, 8, 12, Ui.REGULAR, 12, Ui.Style.PLAIN, () -> pickItem(label, set));
        icon.ifPresent(id -> ui.labelFit(id.toString(), x + 40 + chooseW + 10, boxY + 16, w - 50 - chooseW, 11, Ui.REGULAR, ui.c("text.dim")));
        return boxY + 32 + 11;
    }

    // --- pickers ---

    private void openPicker(String title, Supplier<List<Choice>> choices) {
        syncFields();
        pickerTitle = title;
        picker = choices;
        filter.setText("");
        lastQuery = "\0";
        panelScroll.reset();
        focus(filter);
    }

    private void closePicker() {
        picker = null;
    }

    /** The player's items first; typing searches every item of the game. */
    private void pickItem(String title, Consumer<ResourceLocation> chosen) {
        openPicker(title, () -> {
            String query = filter.text().trim().toLowerCase(Locale.ROOT);
            List<Choice> out = new ArrayList<>();
            if (query.length() < MIN_QUERY) {
                var inventory = Minecraft.getInstance().player.getInventory();
                List<Item> seen = new ArrayList<>();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (!stack.isEmpty() && !seen.contains(stack.getItem())) {
                        seen.add(stack.getItem());
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        out.add(new Choice(stack.getHoverName().getString(), "", stack, () -> choose(chosen, id)));
                    }
                }
                return out;
            }
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                ItemStack stack = new ItemStack(item);
                if (id.toString().contains(query) || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    out.add(new Choice(stack.getHoverName().getString(), "", stack, () -> choose(chosen, id)));
                    if (out.size() >= MAX_RESULTS) {
                        break;
                    }
                }
            }
            return out;
        });
    }

    private void choose(Consumer<ResourceLocation> chosen, ResourceLocation id) {
        edit(() -> chosen.accept(id));
        closePicker();
    }

    private void pickQuest(Consumer<ResourceLocation> chosen) {
        openPicker(tr("rysquests.editor.pick.quests"), () -> {
            String query = filter.text().trim().toLowerCase(Locale.ROOT);
            List<Choice> out = new ArrayList<>();
            for (Quest quest : ClientBook.current().quests().values()) {
                String name = tr(quest.title());
                if (BookScreen.inStory(quest) && (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)) && out.size() < MAX_RESULTS) {
                    out.add(new Choice(name, "", quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), () -> choose(chosen, quest.id())));
                }
            }
            return out;
        });
    }

    private void pickGuide(Consumer<String> chosen) {
        openPicker(tr("rysquests.editor.pick.links"), () -> {
            String query = filter.text().trim().toLowerCase(Locale.ROOT);
            List<Choice> out = new ArrayList<>();
            for (Guide guide : ClientBook.current().guides().values()) {
                addGuideChoices(guide, query, chosen, out);
            }
            for (SectionDef section : ClientBook.current().sections().values()) {
                if (section instanceof PagesSection pages) {
                    addGuideChoices(pages.asGuide(), query, chosen, out);
                }
            }
            return out;
        });
    }

    private void addGuideChoices(Guide guide, String query, Consumer<String> chosen, List<Choice> out) {
        ItemStack icon = guide.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        String title = tr(guide.title());
        if (query.isEmpty() || title.toLowerCase(Locale.ROOT).contains(query)) {
            out.add(new Choice(title, "", icon, () -> pickRef(chosen, new GuideRef(guide.id(), Optional.empty()))));
        }
        for (Guide.Page page : guide.pages()) {
            String name = tr(page.title());
            if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(new Choice(name, title, icon, () -> pickRef(chosen, new GuideRef(guide.id(), Optional.of(page.id())))));
            }
        }
    }

    private void pickRef(Consumer<String> chosen, GuideRef ref) {
        edit(() -> chosen.accept(ref.toString()));
        closePicker();
    }

    // --- drawing ---

    void render(Ui ui, int x, int y, int w, int h) {
        syncFields();
        renderBar(ui, x, y, w);
        int bodyY = y + BAR_H;
        int bodyH = h - BAR_H;
        if (playerView) {
            screen.renderSectionPreview(ui, x, bodyY, w, bodyH);
            return;
        }
        leftEdge = x + LEFT_W;
        rightEdge = x + w - RIGHT_W;
        renderLeft(ui, x, bodyY, LEFT_W, bodyH);
        renderCenter(ui, leftEdge, bodyY, rightEdge - leftEdge, bodyH);
        renderRight(ui, rightEdge, bodyY, RIGHT_W, bodyH);
    }

    private void renderBar(Ui ui, int x, int y, int w) {
        ui.fillCorners(x, y, w, BAR_H, 13, ui.c("sidebar.bg"), true, true, false, false);
        ui.rect(x, y + BAR_H - 1, w, 1, ui.c("panel.border"));
        float cy = y + (BAR_H - 1) / 2f;
        BookScreen.renderBrand(ui, x + 18, cy);
        float pen = x + 18 + 36 + ui.width("RYSQuests", 16, Ui.DISPLAY) + 10;
        pen += ui.pill(tr("rysquests.editor.badge_tab"), pen, cy, 11, Ui.SEMIBOLD, 8, 20, 10, ui.warmBorder(0.13f), ui.c("accent")) + 18;
        if (state.section instanceof MatrixSection) {
            pen = EditorView.barButton(ui, tr("rysquests.editor.new_row"), pen, cy, this::addRow) + 6;
            pen = EditorView.barButton(ui, tr("rysquests.editor.new_column"), pen, cy, this::addColumn) + 6;
        } else if (state.section instanceof CardsSection) {
            pen = EditorView.barButton(ui, tr("rysquests.editor.new_card"), pen, cy, this::addCard) + 6;
        }
        if (state.section != null) {
            pen = EditorView.barButton(ui, tr("rysquests.editor.player_view"), pen, cy, this::togglePlayerView) + 12;
        }
        float right = x + w - 18;
        String exit = tr("rysquests.editor.exit");
        float exitW = ui.buttonWidth(exit, 11.5f, Ui.REGULAR, 10) + 2;
        right -= exitW;
        ui.button(exit, right, cy - 15, exitW, 30, 8, 11.5f, Ui.REGULAR, 10, Ui.Style.PLAIN, this::requestExit);
        String save = tr("rysquests.editor.save");
        float saveW = ui.buttonWidth(save, 11.5f, Ui.SEMIBOLD, 12);
        right -= 6 + saveW;
        ui.button(save, right, cy - 15, saveW, 30, 8, 11.5f, Ui.SEMIBOLD, 12, Ui.Style.ACCENT, this::save);
        String note = status != null ? status : dirty() ? Ui.counted("rysquests.plural.file_changed", changedFiles()) : "";
        right -= 12;
        float noteW = Math.min(ui.width(note, 11.5f, Ui.REGULAR), Math.max(1, right - pen - 78));
        ui.labelFitTip(note, right - noteW, cy, noteW, 11.5f, Ui.REGULAR, ui.c(statusBad && status != null ? "state.bad" : "text.muted"));
        float undoX = right - noteW - (note.isEmpty() ? 0 : 12) - 66;
        EditorView.iconButton(ui, Icons.Shape.UNDO, undoX, cy, !undo.isEmpty(), this::stepUndo);
        EditorView.iconButton(ui, Icons.Shape.REDO, undoX + 36, cy, !redo.isEmpty(), this::stepRedo);
    }

    private int changedFiles() {
        int files = 0;
        if (saved.section == null || !saved.section.equals(state.section)) {
            files++;
        }
        if (!state.texts.equals(saved.texts)) {
            files++;
        }
        if (!state.rail.equals(saved.rail)) {
            files++;
        }
        return Math.max(1, files);
    }

    // --- left: the rail and the templates ---

    private void renderLeft(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.columnBg(), false, false, false, true);
        ui.rect(x + w - 1, y, 1, h, ui.c("panel.border"));
        float left = x + 10;
        float inner = w - 21;
        ui.caps(tr("rysquests.editor.rail"), left + 6, y + 19, ui.c("text.dim"));
        float cursor = y + 32;
        List<String> rail = state.rail;
        for (int i = 0; i < rail.size(); i++) {
            renderRailRow(ui, rail.get(i), i, true, left, cursor, inner);
            cursor += 35;
        }
        for (SectionDef other : ClientBook.current().sections().values()) {
            if (!rail.contains(other.id().toString())) {
                renderRailRow(ui, other.id().toString(), -1, false, left, cursor, inner);
                cursor += 35;
            }
        }
        cursor += 6;
        ui.caps(tr("rysquests.editor.new_from_template"), left + 6, cursor + 7, ui.c("text.dim"));
        cursor += 20;
        cursor = template(ui, MatrixSection.TEMPLATE, left, cursor, inner);
        cursor = template(ui, CardsSection.TEMPLATE, left, cursor, inner);
        template(ui, PagesSection.TEMPLATE, left, cursor, inner);
        if (state.section != null) {
            float boxY = y + h - 12 - 50;
            ui.box(left, boxY, inner, 50, 8, ui.c("slot.bg"), ui.mix("slot.bg", "panel.border", 0.6f));
            ui.labelFitTip("sections/" + state.section.id().getPath() + ".json", left + 10, boxY + 9 + 7, inner - 20, 10.5f, Ui.REGULAR, ui.c("text.muted"));
            ui.labelFit("lang/" + Minecraft.getInstance().getLanguageManager().getSelected() + ".json", left + 10, boxY + 9 + 7 + 16, inner - 20, 10.5f, Ui.REGULAR, ui.c("text.dim"));
        }
    }

    private String sectionName(String id) {
        if (id.equals(Chapter.STORY) || id.equals(BookMeta.GUIDES) || id.equals(BookMeta.SHOP) || id.equals(BookMeta.ACHIEVEMENTS)) {
            return tr("rysquests.section." + id);
        }
        if (state.section != null && state.section.id().toString().equals(id)) {
            String name = text(state.section.title());
            return name.isBlank() ? tr("rysquests.editor.untitled") : name;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        SectionDef section = parsed == null ? null : ClientBook.current().sections().get(parsed);
        return section == null ? id : tr(section.title());
    }

    private void renderRailRow(Ui ui, String id, int index, boolean onRail, float x, float y, float w) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        boolean builtIn = id.equals(Chapter.STORY) || id.equals(BookMeta.GUIDES) || id.equals(BookMeta.SHOP) || id.equals(BookMeta.ACHIEVEMENTS);
        SectionDef section = builtIn || parsed == null ? null : state.section != null && state.section.id().equals(parsed) ? state.section : ClientBook.current().sections().get(parsed);
        boolean selected = section != null && state.section != null && section.id().equals(state.section.id());
        boolean over = ui.over(x, y, w, 32);
        if (selected) {
            ui.box(x, y, w, 32, 7, ui.c("button.bg"), ui.c("button.border"));
        } else if (over) {
            ui.fill(x, y, w, 32, 7, ui.c("slot.hover"));
        }
        float cy = y + 16;
        if (section != null) {
            SectionDef target = section;
            ui.hit(x, y, w, 32, () -> open(target));
        }
        ui.icon(Icons.Shape.GRIP, x + 8, cy - 5, 10, ui.c("text.dim"));
        float reserve = over && onRail ? 54 : over ? 18 : 40;
        ui.labelFitTip(sectionName(id), x + 24, cy, w - 28 - reserve, 12, Ui.REGULAR, ui.c(selected ? "text.primary" : onRail ? "button.fg" : "text.muted"));
        if (over && onRail) {
            miniIcon(ui, Icons.Shape.CHEVRON_DOWN, 180, x + w - 52, cy, index > 0, () -> moveRail(index, -1));
            miniIcon(ui, Icons.Shape.CHEVRON_DOWN, 0, x + w - 35, cy, index + 1 < state.rail.size(), () -> moveRail(index, 1));
            miniIcon(ui, Icons.Shape.CROSS, 0, x + w - 18, cy, state.rail.size() > 1, () -> removeFromRail(index));
        } else if (over) {
            miniIcon(ui, Icons.Shape.PLUS, 0, x + w - 18, cy, true, () -> addToRail(id));
        } else {
            String kind = tr(builtIn ? "rysquests.editor.kind.built_in" : onRail ? "rysquests.editor.kind.custom" : "rysquests.editor.kind.off_rail");
            ui.labelRight(kind, x + w - 8, cy, 10, Ui.REGULAR, ui.c(builtIn || !onRail ? "text.dim" : "chip.mod.fg"));
        }
    }

    private static void miniIcon(Ui ui, Icons.Shape shape, float degrees, float x, float cy, boolean enabled, Runnable action) {
        boolean over = enabled && ui.over(x, cy - 8, 16, 16);
        if (over) {
            ui.fill(x, cy - 8, 16, 16, 4, ui.c("button.bg"));
        }
        ui.iconRotated(shape, x + 3.5f, cy - 4.5f, 9, degrees, ui.c(enabled ? "button.fg" : "text.dim"));
        if (enabled) {
            ui.hit(x, cy - 8, 16, 16, action);
        }
    }

    private void moveRail(int index, int delta) {
        edit(() -> state = new State(state.section, state.texts, SectionEdits.moved(state.rail, index, delta)));
    }

    private void removeFromRail(int index) {
        edit(() -> {
            List<String> rail = new ArrayList<>(state.rail);
            rail.remove(index);
            state = new State(state.section, state.texts, rail);
        });
    }

    private void addToRail(String id) {
        edit(() -> {
            List<String> rail = new ArrayList<>(state.rail);
            rail.add(id);
            state = new State(state.section, state.texts, rail);
        });
    }

    private float template(Ui ui, String template, float x, float y, float w) {
        boolean over = ui.over(x, y, w, 46);
        ui.dashed(x, y, w, 46, over ? ui.c("text.muted") : ui.c("button.border"));
        ui.label(tr("rysquests.editor.template." + template), x + 12, y + 15, 12, Ui.REGULAR, ui.nameFg());
        ui.labelFit(tr("rysquests.editor.template." + template + ".hint"), x + 12, y + 31, w - 24, 10.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.hit(x, y, w, 46, () -> create(template));
        return y + 46 + 6;
    }

    // --- centre ---

    private void renderCenter(Ui ui, int x, int y, int w, int h) {
        ui.rect(x, y, w, h, ui.mix("slot.bg", "panel.bg", 0.3f));
        if (state.section == null) {
            ui.labelCentered(tr("rysquests.editor.pick_section"), x + w / 2f, y + h / 2f, 13, Ui.REGULAR, ui.c("text.muted"));
            return;
        }
        float left = x + 16;
        float inner = w - 32;
        float cy = y + 22;
        String name = text(state.section.title());
        ui.labelFit(name.isBlank() ? tr("rysquests.editor.untitled") : name, left, cy, inner * 0.4f, 15, Ui.DISPLAY, ui.c("text.primary"));
        List<String> issues = issues();
        if (!issues.isEmpty()) {
            ui.labelRight(Ui.counted("rysquests.plural.issue", issues.size()), left + inner, cy, 11, Ui.REGULAR, ui.c("state.bad"));
        }
        float tableTop = y + 42;
        List<String> shown = issues.size() > 3 ? issues.subList(0, 3) : issues;
        float noteH = 16 + 15 * (shown.size() + 1);
        float noteY = y + h - 12 - noteH;
        if (state.section instanceof MatrixSection matrix) {
            renderTable(ui, matrix, left, tableTop, inner, noteY - 8 - tableTop);
        } else if (state.section instanceof CardsSection cards) {
            renderCards(ui, cards, left, tableTop, inner, noteY - 8 - tableTop);
        }
        ui.box(left, noteY, inner, noteH, 8, ui.c("slot.bg"), ui.mix("slot.bg", "panel.border", 0.6f));
        float line = noteY + 8 + 7.5f;
        for (String issue : shown) {
            ui.labelFitTip(issue, left + 10, line, inner - 20, 11, Ui.REGULAR, ui.c("state.bad"));
            line += 15;
        }
        ui.labelFit(tr("rysquests.editor.section_hint"), left + 10, line, inner - 20, 11, Ui.REGULAR, ui.c("text.dim"));
    }

    private List<String> issues() {
        List<String> out = new ArrayList<>();
        if (state.section instanceof MatrixSection matrix) {
            for (Row each : matrix.rows()) {
                String rowName = text(matrix.rowTitle(each));
                each.icon().filter(icon -> !BuiltInRegistries.ITEM.containsKey(icon)).ifPresent(icon -> out.add(Component.translatable("rysquests.editor.issue", icon.toString()).getString()));
                for (Column col : matrix.columns()) {
                    if (each.ways(col).isEmpty() && !matrix.compact()) {
                        out.add(Component.translatable("rysquests.editor.issue.empty_cell", rowName, text(col.title())).getString());
                    }
                    for (Way item : each.ways(col)) {
                        if (text(matrix.wayName(each, col, item)).isBlank()) {
                            out.add(Component.translatable("rysquests.editor.issue.no_name", rowName, text(col.title())).getString());
                        }
                    }
                }
            }
        } else if (state.section instanceof CardsSection cards) {
            for (Card each : cards.cards()) {
                if (text(cards.cardKey(each, "name")).isBlank()) {
                    out.add(Component.translatable("rysquests.editor.issue.no_card_name", each.id()).getString());
                }
            }
        }
        return out;
    }

    private void renderTable(Ui ui, MatrixSection matrix, float x, float y, float w, float h) {
        int columns = matrix.columns().size();
        float columnW = (w - LABEL_W - columns * CELL_GAP) / columns;
        for (int c = 0; c < columns; c++) {
            renderColumnHead(ui, matrix, c, x + LABEL_W + CELL_GAP + c * (columnW + CELL_GAP), y, columnW);
        }
        float top = y + 46 + CELL_GAP;
        float room = y + h - top;
        float total = 36;
        float[] heights = new float[matrix.rows().size()];
        for (int r = 0; r < heights.length; r++) {
            int most = 0;
            for (Column col : matrix.columns()) {
                most = Math.max(most, matrix.rows().get(r).ways(col).size());
            }
            heights[r] = Math.max(CELL_MIN, 12 + most * (WAY_H + 4) + ADD_H);
            total += heights[r] + 5;
        }
        tableScroll.content(Math.round(total), Math.round(room));
        int offset = tableScroll.offset();
        ui.clip(Math.round(x), Math.round(top), Math.round(w), Math.round(room));
        float cursor = top - offset;
        for (int r = 0; r < heights.length; r++) {
            if (cursor + heights[r] > top && cursor < top + room) {
                renderRow(ui, matrix, r, x, cursor, columnW, heights[r]);
            }
            cursor += heights[r] + 5;
        }
        boolean over = ui.over(x, cursor, w, 30);
        ui.dashed(x, cursor, w, 30, over ? ui.c("text.muted") : ui.c("button.border"));
        ui.labelCentered(tr("rysquests.editor.add_row"), x + w / 2f, cursor + 15, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.hit(x, cursor, w, 30, this::addRow);
        ui.unclip();
    }

    private void renderColumnHead(Ui ui, MatrixSection matrix, int index, float x, float y, float w) {
        Column col = matrix.columns().get(index);
        boolean selected = tab == Tab.COLUMN && column == index;
        ui.box(x, y, w, 46, 8, ui.c("panel.bg"), selected ? ui.c("accent") : ui.c("panel.border"));
        String name = text(col.title());
        ui.labelFit(name.isBlank() ? tr("rysquests.editor.untitled") : name, x + 9, y + 15, w - 32, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.icon(Icons.Shape.PENCIL, x + w - 20, y + 10, 10, ui.c("text.dim"));
        ui.labelFit(col.stage().map(stage -> "stage: " + stage).orElse(tr("rysquests.editor.from_start")), x + 9, y + 32, w - 18, 10.5f, Ui.REGULAR, ui.c("chip.mod.fg"));
        ui.hit(x, y, w, 46, () -> select(Tab.COLUMN, row, index, -1));
    }

    private void renderRow(Ui ui, MatrixSection matrix, int index, float x, float y, float columnW, float h) {
        Row item = matrix.rows().get(index);
        boolean selected = tab == Tab.ROW && row == index;
        float cy = y + h / 2f;
        if (selected) {
            ui.stroke(x, y, LABEL_W, h, 8, ui.c("accent"));
        }
        ui.cell(item.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), x + 4, cy - 15, 30, 30, 7, 16, ui.c("slot.bg"), ui.c("card.border"));
        String name = text(matrix.rowTitle(item));
        ui.labelFitTip(name.isBlank() ? tr("rysquests.editor.untitled") : name, x + 42, cy, LABEL_W - 46, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.hit(x, y, LABEL_W, h, () -> select(Tab.ROW, index, column, -1));
        for (int c = 0; c < matrix.columns().size(); c++) {
            renderCell(ui, matrix, index, c, x + LABEL_W + CELL_GAP + c * (columnW + CELL_GAP), y, columnW, h);
        }
    }

    private void renderCell(Ui ui, MatrixSection matrix, int rowIndex, int columnIndex, float x, float y, float w, float h) {
        Row item = matrix.rows().get(rowIndex);
        Column col = matrix.columns().get(columnIndex);
        List<Way> ways = item.ways(col);
        boolean selected = rowIndex == row && columnIndex == column && tab == Tab.WAY;
        boolean empty = ways.isEmpty() && !matrix.compact();
        ui.box(x, y, w, h, 9, ui.c("panel.bg"), selected ? ui.c("accent") : empty ? ui.mix("panel.border", "state.bad", 0.5f) : ui.c("panel.border"));
        float cursor = y + 6;
        for (int i = 0; i < ways.size(); i++) {
            Way entry = ways.get(i);
            boolean chosen = selected && i == way;
            ui.box(x + 6, cursor, w - 12, WAY_H, 6, ui.c("status.bg"), chosen ? ui.c("accent") : ui.c("panel.border"));
            ui.item(entry.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), x + 12, cursor + 5, 16);
            String name = text(matrix.wayName(item, col, entry));
            String tag = entry.mark().map(mark -> tr("rysquests.matrix.mark." + mark)).orElse(entry.best() ? tr("rysquests.matrix.best") : "");
            float tagW = tag.isEmpty() ? 0 : ui.width(tag, 10, Ui.REGULAR) + 6;
            ui.labelFit(name.isBlank() ? tr("rysquests.editor.untitled") : name, x + 33, cursor + WAY_H / 2f, w - 12 - 33 - tagW, 11.5f, Ui.REGULAR, ui.nameFg());
            if (!tag.isEmpty()) {
                ui.labelRight(tag, x + w - 12, cursor + WAY_H / 2f, 10, Ui.REGULAR, ui.c(entry.best() ? "accent" : "text.muted"));
            }
            int index = i;
            ui.hit(x + 6, cursor, w - 12, WAY_H, () -> select(Tab.WAY, rowIndex, columnIndex, index));
            cursor += WAY_H + 4;
        }
        boolean over = ui.over(x + 6, cursor, w - 12, ADD_H);
        int addColor = empty ? ui.c("state.bad") : over ? ui.c("text.muted") : ui.c("text.dim");
        ui.dashed(x + 6, cursor, w - 12, ADD_H, addColor);
        ui.labelCentered(tr(empty ? "rysquests.editor.empty_add_way" : "rysquests.editor.add_way"), x + w / 2f, cursor + ADD_H / 2f, 11, Ui.REGULAR, addColor);
        ui.hit(x + 6, cursor, w - 12, ADD_H, () -> addWay(rowIndex, columnIndex));
    }

    private void addRow() {
        syncFields();
        edit(() -> {
            MatrixSection next = SectionEdits.addRow((MatrixSection) state.section);
            setSection(next);
            row = next.rows().size() - 1;
            tab = Tab.ROW;
        });
    }

    private void addColumn() {
        syncFields();
        edit(() -> {
            MatrixSection next = SectionEdits.addColumn((MatrixSection) state.section);
            setSection(next);
            column = next.columns().size() - 1;
            tab = Tab.COLUMN;
        });
    }

    private void addWay(int rowIndex, int columnIndex) {
        syncFields();
        edit(() -> {
            MatrixSection current = (MatrixSection) state.section;
            Column col = current.columns().get(columnIndex);
            MatrixSection next = SectionEdits.addWay(current, rowIndex, col);
            setSection(next);
            row = rowIndex;
            column = columnIndex;
            way = next.rows().get(rowIndex).ways(col).size() - 1;
            tab = Tab.WAY;
        });
    }

    private void addCard() {
        syncFields();
        edit(() -> {
            CardsSection next = SectionEdits.addCard((CardsSection) state.section);
            setSection(next);
            card = next.cards().size() - 1;
            tab = Tab.CARD;
        });
    }

    private void renderCards(Ui ui, CardsSection cards, float x, float y, float w, float h) {
        int perRow = 3;
        float gap = 8;
        float tileW = (w - (perRow - 1) * gap) / perRow;
        float tileH = 56;
        int rows = (cards.cards().size() + perRow) / perRow;
        tableScroll.content(Math.round(rows * (tileH + gap)), Math.round(h));
        int offset = tableScroll.offset();
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        for (int i = 0; i <= cards.cards().size(); i++) {
            float tx = x + (i % perRow) * (tileW + gap);
            float ty = y + (i / perRow) * (tileH + gap) - offset;
            if (i == cards.cards().size()) {
                boolean over = ui.over(tx, ty, tileW, tileH);
                ui.dashed(tx, ty, tileW, tileH, over ? ui.c("text.muted") : ui.c("button.border"));
                ui.labelCentered(tr("rysquests.editor.new_card"), tx + tileW / 2f, ty + tileH / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
                ui.hit(tx, ty, tileW, tileH, this::addCard);
                continue;
            }
            Card item = cards.cards().get(i);
            boolean selected = tab == Tab.CARD && card == i;
            ui.box(tx, ty, tileW, tileH, 9, ui.c("panel.bg"), selected ? ui.c("accent") : ui.c("panel.border"));
            ui.cell(item.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), tx + 10, ty + 12, 32, 32, 7, 18, ui.c("slot.bg"), ui.c("card.border"));
            String name = text(cards.cardKey(item, "name"));
            ui.labelFit(name.isBlank() ? tr("rysquests.editor.untitled") : name, tx + 52, ty + 21, tileW - 62, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
            ui.labelFit(item.group().map(cards::groupTitle).map(this::text).orElse(""), tx + 52, ty + 37, tileW - 62, 10.5f, Ui.REGULAR, ui.c("text.muted"));
            int index = i;
            ui.hit(tx, ty, tileW, tileH, () -> {
                card = index;
                tab = Tab.CARD;
                picker = null;
                bound.clear();
            });
        }
        ui.unclip();
    }

    // --- right: properties ---

    private void renderRight(Ui ui, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, false, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float left = x + 15;
        float inner = w - 30;
        if (picker != null) {
            renderPicker(ui, left, y + 12, inner, h - 24);
            return;
        }
        if (state.section == null) {
            return;
        }
        List<Kit.Tab> tabs = new ArrayList<>();
        if (state.section instanceof MatrixSection) {
            if (way >= 0) {
                tabs.add(tabEntry("rysquests.editor.tab.way", Tab.WAY));
            }
            tabs.add(tabEntry("rysquests.editor.tab.row", Tab.ROW));
            tabs.add(tabEntry("rysquests.editor.tab.column", Tab.COLUMN));
        } else if (state.section instanceof CardsSection) {
            tabs.add(tabEntry("rysquests.editor.tab.card", Tab.CARD));
        }
        tabs.add(tabEntry("rysquests.editor.tab.section", Tab.SECTION));
        ui.tabs(tabs, left, y + 8, 28, inner);
        ui.rect(x + 1, y + 40, w - 1, 1, ui.c("panel.border"));
        float top = y + 50;
        float room = h - 60;
        int offset = panelScroll.offset();
        ui.clip(x + 1, Math.round(top), w - 1, Math.round(room));
        float cursor = top - offset;
        if (state.section instanceof MatrixSection matrix) {
            cursor = switch (tab) {
                case WAY -> renderWayProps(ui, matrix, left, cursor, inner);
                case ROW -> renderRowProps(ui, matrix, left, cursor, inner);
                case COLUMN -> renderColumnProps(ui, matrix, left, cursor, inner);
                default -> renderSectionProps(ui, left, cursor, inner);
            };
        } else if (state.section instanceof CardsSection cards) {
            cursor = tab == Tab.CARD ? renderCardProps(ui, cards, left, cursor, inner) : renderSectionProps(ui, left, cursor, inner);
        }
        ui.unclip();
        panelScroll.content(Math.round(cursor + offset - top + 10), Math.round(room));
    }

    private Kit.Tab tabEntry(String key, Tab target) {
        return new Kit.Tab(tr(key), -1, tab == target, () -> {
            syncFields();
            tab = target;
            panelScroll.reset();
            bound.clear();
        });
    }

    private void updateWay(MatrixSection matrix, java.util.function.UnaryOperator<Way> change) {
        setSection(SectionEdits.updateWay(matrix, row, matrix.columns().get(column), way, change));
    }

    private static Way wayWith(Way w, Optional<String> name, Optional<String> text, Optional<ResourceLocation> icon, Optional<String> mark, List<ResourceLocation> needs, List<Link> links) {
        return new Way(w.id(), name, text, icon, mark, w.best(), needs, links);
    }

    /** A string stored right in the file stays there; a lang key gets its text in the lang file. */
    private void putString(String stored, String value, Consumer<String> literal) {
        if (isKey(stored)) {
            setText(stored, value);
        } else {
            literal.accept(value);
        }
    }

    private float renderWayProps(Ui ui, MatrixSection matrix, float x, float y, float w) {
        Row item = matrix.rows().get(row);
        Column col = matrix.columns().get(column);
        Way entry = item.ways(col).get(way);
        ui.labelFit(text(matrix.rowTitle(item)) + " · " + text(col.title()) + " · " + (way + 1) + " / " + item.ways(col).size(), x, y + 7, w, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        float cursor = y + 22;
        String prefix = "way:" + row + ":" + column + ":" + way + ":";
        String nameKey = matrix.wayName(item, col, entry);
        cursor = inputRow(ui, tr("rysquests.editor.field.title"), prefix + "name", x, cursor, w, () -> text(nameKey), value -> putString(nameKey, value,
                literal -> updateWay((MatrixSection) state.section, way2 -> wayWith(way2, Optional.of(literal), way2.text(), way2.icon(), way2.mark(), way2.needs(), way2.links()))));
        cursor = iconRow(ui, tr("rysquests.editor.field.icon"), entry.icon(), x, cursor, w, id -> updateWay((MatrixSection) state.section, way2 -> wayWith(way2, way2.name(), way2.text(), Optional.of(id), way2.mark(), way2.needs(), way2.links())));
        String textKey = matrix.wayText(item, col, entry);
        cursor = textBox(ui, tr("rysquests.editor.field.description"), prefix + "text", x, cursor, w, () -> text(textKey), value -> putString(textKey, value,
                literal -> updateWay((MatrixSection) state.section, way2 -> wayWith(way2, way2.name(), Optional.of(literal), way2.icon(), way2.mark(), way2.needs(), way2.links()))));
        String mark = entry.mark().map(found2 -> tr("rysquests.matrix.mark." + found2)).orElse(tr("rysquests.editor.none"));
        cursor = cycleRow(ui, tr("rysquests.editor.field.mark"), mark, x, cursor, w, () -> edit(() -> updateWay((MatrixSection) state.section, way2 -> {
            Optional<String> next = way2.mark().isEmpty() ? Optional.of("auto") : way2.mark().get().equals("auto") ? Optional.of("manual") : Optional.empty();
            return wayWith(way2, way2.name(), way2.text(), way2.icon(), next, way2.needs(), way2.links());
        })));
        cursor = switchRow(ui, tr("rysquests.editor.field.best"), entry.best(), x, cursor, w, () -> edit(() -> setSection(SectionEdits.markBest((MatrixSection) state.section, row, col, way))));
        cursor = renderNeeds(ui, entry, x, cursor, w);
        cursor = renderLinks(ui, entry, x, cursor, w);
        return dangerRow(ui, x, cursor, w, way > 0, way + 1 < item.ways(col).size(),
                () -> edit(() -> {
                    setSection(SectionEdits.moveWay((MatrixSection) state.section, row, col, way, -1));
                    way--;
                }), () -> edit(() -> {
                    setSection(SectionEdits.moveWay((MatrixSection) state.section, row, col, way, 1));
                    way++;
                }), true, () -> edit(() -> {
                    setSection(SectionEdits.removeWay((MatrixSection) state.section, row, col, way));
                    way = -1;
                    tab = Tab.ROW;
                }));
    }

    private float rubric(Ui ui, String title, String button, float x, float y, float w, Runnable action) {
        ui.caps(title, x, y + 12, ui.c("text.dim"));
        float buttonW = ui.buttonWidth(button, 11, Ui.REGULAR, 8) + 2;
        ui.button(button, x + w - buttonW, y, buttonW, 24, 6, 11, Ui.REGULAR, 8, Ui.Style.PLAIN, action);
        return y + 24 + 6;
    }

    private float renderNeeds(Ui ui, Way entry, float x, float y, float w) {
        float cursor = rubric(ui, tr("rysquests.editor.field.needs") + " · " + entry.needs().size(), tr("rysquests.editor.add_item"), x, y, w,
                () -> pickItem(tr("rysquests.editor.field.needs"), id -> updateWay((MatrixSection) state.section, way2 -> {
                    List<ResourceLocation> needs = new ArrayList<>(way2.needs());
                    needs.add(id);
                    return wayWith(way2, way2.name(), way2.text(), way2.icon(), way2.mark(), needs, way2.links());
                })));
        for (int i = 0; i < entry.needs().size(); i++) {
            ItemStack stack = TaskText.stackOf(entry.needs().get(i));
            int index = i;
            cursor = removableRow(ui, stack, stack.getHoverName().getString(), "", x, cursor, w, () -> edit(() -> updateWay((MatrixSection) state.section, way2 -> {
                List<ResourceLocation> needs = new ArrayList<>(way2.needs());
                needs.remove(index);
                return wayWith(way2, way2.name(), way2.text(), way2.icon(), way2.mark(), needs, way2.links());
            })));
        }
        return cursor + 8;
    }

    private float renderLinks(Ui ui, Way entry, float x, float y, float w) {
        ui.caps(tr("rysquests.editor.field.links") + " · " + entry.links().size(), x, y + 12, ui.c("text.dim"));
        float pen = x + w;
        for (LinkType type : List.of(LinkType.GUIDE, LinkType.RECIPE, LinkType.QUEST)) {
            String label = "+ " + tr("rysquests.matrix.link." + type.getSerializedName());
            float buttonW = ui.buttonWidth(label, 11, Ui.REGULAR, 6) + 2;
            pen -= buttonW;
            ui.button(label, pen, y, buttonW, 24, 6, 11, Ui.REGULAR, 6, Ui.Style.PLAIN, () -> pickLink(type));
            pen -= 4;
        }
        float cursor = y + 24 + 6;
        for (int i = 0; i < entry.links().size(); i++) {
            Link link = entry.links().get(i);
            int index = i;
            cursor = removableRow(ui, ItemStack.EMPTY, link.id(), tr("rysquests.matrix.link." + link.type().getSerializedName()), x, cursor, w, () -> edit(() -> changeLinks(links -> links.remove(index))));
        }
        return cursor + 8;
    }

    private void changeLinks(Consumer<List<Link>> change) {
        updateWay((MatrixSection) state.section, way2 -> {
            List<Link> links = new ArrayList<>(way2.links());
            change.accept(links);
            return wayWith(way2, way2.name(), way2.text(), way2.icon(), way2.mark(), way2.needs(), links);
        });
    }

    private void pickLink(LinkType type) {
        switch (type) {
            case QUEST -> pickQuest(id -> changeLinks(links -> links.add(new Link(LinkType.QUEST, id.toString()))));
            case RECIPE -> pickItem(tr("rysquests.editor.pick.recipes"), id -> changeLinks(links -> links.add(new Link(LinkType.RECIPE, id.toString()))));
            case GUIDE -> pickGuide(id -> changeLinks(links -> links.add(new Link(LinkType.GUIDE, id))));
        }
    }

    private static float removableRow(Ui ui, ItemStack icon, String label, String note, float x, float y, float w, Runnable remove) {
        ui.box(x, y, w, 28, 7, ui.c("status.bg"), ui.c("status.border"));
        float cy = y + 14;
        ui.item(icon, x + 8, cy - 7, 14);
        float textX = icon.isEmpty() ? x + 9 : x + 28;
        float noteW = note.isEmpty() ? 0 : ui.width(note, 10.5f, Ui.REGULAR) + 8;
        ui.labelFitTip(label, textX, cy, x + w - 26 - noteW - textX, 11.5f, Ui.REGULAR, ui.nameFg());
        if (!note.isEmpty()) {
            ui.labelRight(note, x + w - 26, cy, 10.5f, Ui.REGULAR, ui.c("chip.mod.fg"));
        }
        miniIcon(ui, Icons.Shape.CROSS, 0, x + w - 20, cy, true, remove);
        return y + 28 + 5;
    }

    /** Move and remove, at the end of the properties of what is selected. */
    private static float dangerRow(Ui ui, float x, float y, float w, boolean up, boolean down, Runnable moveUp, Runnable moveDown, boolean removable, Runnable remove) {
        float h = 30;
        EditorView.iconButton(ui, Icons.Shape.CHEVRON_LEFT, x, y + h / 2f, up, moveUp);
        EditorView.iconButton(ui, Icons.Shape.CHEVRON_RIGHT, x + 36, y + h / 2f, down, moveDown);
        String label = tr("rysquests.editor.remove");
        float removeW = ui.buttonWidth(label, 11.5f, Ui.REGULAR, 10) + 2;
        if (removable) {
            ui.button(label, x + w - removeW, y, removeW, h, 8, 11.5f, Ui.REGULAR, 10, Ui.Style.PLAIN, remove);
        }
        return y + h + 8;
    }

    private float renderRowProps(Ui ui, MatrixSection matrix, float x, float y, float w) {
        Row item = matrix.rows().get(row);
        String prefix = "row:" + row + ":";
        String titleKey = matrix.rowTitle(item);
        float cursor = inputRow(ui, tr("rysquests.editor.field.title"), prefix + "title", x, y, w, () -> text(titleKey), value -> putString(titleKey, value,
                literal -> setSection(SectionEdits.updateRow((MatrixSection) state.section, row, r -> new Row(r.id(), Optional.of(literal), r.icon(), r.group(), r.cells())))));
        cursor = iconRow(ui, tr("rysquests.editor.field.icon"), item.icon(), x, cursor, w, id -> setSection(SectionEdits.updateRow((MatrixSection) state.section, row, r -> new Row(r.id(), r.title(), Optional.of(id), r.group(), r.cells()))));
        String groupName = item.group().map(matrix::groupTitle).map(this::text).orElse(tr("rysquests.editor.none"));
        cursor = cycleRow(ui, tr("rysquests.editor.field.group"), groupName, x, cursor, w, () -> edit(() -> setSection(SectionEdits.updateRow((MatrixSection) state.section, row, r -> {
            List<String> groups = ((MatrixSection) state.section).groups();
            int at = r.group().map(groups::indexOf).orElse(-1) + 1;
            return new Row(r.id(), r.title(), r.icon(), at < groups.size() ? Optional.of(groups.get(at)) : Optional.empty(), r.cells());
        }))));
        return dangerRow(ui, x, cursor, w, row > 0, row + 1 < matrix.rows().size(), () -> edit(() -> {
            setSection(SectionEdits.moveRow((MatrixSection) state.section, row, -1));
            row--;
        }), () -> edit(() -> {
            setSection(SectionEdits.moveRow((MatrixSection) state.section, row, 1));
            row++;
        }), matrix.rows().size() > 1, () -> edit(() -> setSection(SectionEdits.removeRow((MatrixSection) state.section, row))));
    }

    private float renderColumnProps(Ui ui, MatrixSection matrix, float x, float y, float w) {
        Column col = matrix.columns().get(column);
        String prefix = "column:" + column + ":";
        float cursor = inputRow(ui, tr("rysquests.editor.field.title"), prefix + "title", x, y, w, () -> text(col.title()), value -> putString(col.title(), value,
                literal -> setSection(SectionEdits.updateColumn((MatrixSection) state.section, column, c -> new Column(c.id(), literal, c.hint(), c.stage())))));
        String hintKey = col.hint().orElse(col.title() + ".hint");
        cursor = inputRow(ui, tr("rysquests.editor.field.hint"), prefix + "hint", x, cursor, w, () -> col.hint().isPresent() ? text(hintKey) : "", value -> {
            setSection(SectionEdits.updateColumn((MatrixSection) state.section, column, c -> new Column(c.id(), c.title(), value.isBlank() ? Optional.empty() : Optional.of(isKey(hintKey) ? hintKey : value), c.stage())));
            if (isKey(hintKey)) {
                setText(hintKey, value);
            }
        });
        cursor = inputRow(ui, tr("rysquests.editor.field.stage"), prefix + "stage", x, cursor, w, () -> col.stage().orElse(""),
                value -> setSection(SectionEdits.updateColumn((MatrixSection) state.section, column, c -> new Column(c.id(), c.title(), c.hint(), value.isBlank() ? Optional.empty() : Optional.of(value.trim())))));
        return dangerRow(ui, x, cursor, w, column > 0, column + 1 < matrix.columns().size(), () -> edit(() -> {
            setSection(SectionEdits.moveColumn((MatrixSection) state.section, column, -1));
            column--;
        }), () -> edit(() -> {
            setSection(SectionEdits.moveColumn((MatrixSection) state.section, column, 1));
            column++;
        }), matrix.columns().size() > 1, () -> edit(() -> setSection(SectionEdits.removeColumn((MatrixSection) state.section, column))));
    }

    private float renderSectionProps(Ui ui, float x, float y, float w) {
        SectionDef section = state.section;
        String title = section.title();
        float cursor = inputRow(ui, tr("rysquests.editor.field.title"), "section:title", x, y, w, () -> text(title), value -> putString(title, value, literal -> setSection(SectionEdits.withHeader(state.section, literal, state.section.shortTitle(), state.section.subtitle(), state.section.icon()))));
        cursor = optionalText(ui, tr("rysquests.editor.field.short_title"), "section:short", section.shortTitle(), title + ".short", x, cursor, w, value -> setSection(SectionEdits.withHeader(state.section, state.section.title(), value, state.section.subtitle(), state.section.icon())));
        cursor = optionalText(ui, tr("rysquests.editor.field.subtitle"), "section:subtitle", section.subtitle(), title + ".subtitle", x, cursor, w, value -> setSection(SectionEdits.withHeader(state.section, state.section.title(), state.section.shortTitle(), value, state.section.icon())));
        cursor = iconRow(ui, tr("rysquests.editor.field.icon"), section.icon(), x, cursor, w, id -> setSection(SectionEdits.withHeader(state.section, state.section.title(), state.section.shortTitle(), state.section.subtitle(), Optional.of(id))));
        cursor = groupsRow(ui, x, cursor, w);
        if (section instanceof MatrixSection matrix) {
            cursor = cycleRow(ui, tr("rysquests.editor.field.spoilers"), tr("rysquests.editor.spoilers." + matrix.spoilers().getSerializedName()), x, cursor, w, () -> edit(() -> {
                MatrixSection current = (MatrixSection) state.section;
                MatrixSection.Spoilers[] all = MatrixSection.Spoilers.values();
                setSection(SectionEdits.matrixOptions(current, all[(current.spoilers().ordinal() + 1) % all.length], current.compact()));
            }));
            cursor = switchRow(ui, tr("rysquests.editor.field.compact"), matrix.compact(), x, cursor, w, () -> edit(() -> {
                MatrixSection current = (MatrixSection) state.section;
                setSection(SectionEdits.matrixOptions(current, current.spoilers(), !current.compact()));
            }));
        }
        return cursor;
    }

    private float optionalText(Ui ui, String label, String id, Optional<String> stored, String newKey, float x, float y, float w, Consumer<Optional<String>> set) {
        return inputRow(ui, label, id, x, y, w, () -> stored.map(this::text).orElse(""), value -> {
            String key = stored.orElse(newKey);
            if (value.isBlank()) {
                set.accept(Optional.empty());
            } else if (isKey(key)) {
                set.accept(Optional.of(key));
                setText(key, value);
            } else {
                set.accept(Optional.of(value));
            }
        });
    }

    /** Groups are typed as a list of names; a group's id comes from its name. */
    private float groupsRow(Ui ui, float x, float y, float w) {
        SectionDef section = state.section;
        List<String> groups = section instanceof MatrixSection matrix ? matrix.groups() : ((CardsSection) section).groups();
        return inputRow(ui, tr("rysquests.editor.field.groups"), "section:groups", x, y, w, () -> {
            List<String> names = new ArrayList<>();
            for (String group : groups) {
                names.add(text(groupTitle(group)));
            }
            return String.join(", ", names);
        }, value -> {
            List<String> ids = new ArrayList<>();
            for (String part : value.split(",")) {
                String name = part.trim();
                if (!name.isEmpty()) {
                    String id = name.toLowerCase(Locale.ROOT).replace(' ', '_').replace('.', '_');
                    ids.add(id);
                    setText(groupTitle(id), name);
                }
            }
            setSection(SectionEdits.withGroups(state.section, ids));
        });
    }

    private String groupTitle(String group) {
        return state.section instanceof MatrixSection matrix ? matrix.groupTitle(group) : ((CardsSection) state.section).groupTitle(group);
    }

    private void updateCard(java.util.function.UnaryOperator<Card> change) {
        setSection(SectionEdits.updateCard((CardsSection) state.section, card, change));
    }

    private float cardText(Ui ui, String label, CardsSection cards, Card item, String part, boolean multiline, float x, float y, float w) {
        String key = cards.cardKey(item, part);
        String id = "card:" + card + ":" + part;
        return multiline ? textBox(ui, label, id, x, y, w, () -> text(key), value -> setText(key, value)) : inputRow(ui, label, id, x, y, w, () -> text(key), value -> setText(key, value));
    }

    private float renderCardProps(Ui ui, CardsSection cards, float x, float y, float w) {
        Card item = cards.cards().get(card);
        String prefix = "card:" + card + ":";
        float cursor = cardText(ui, tr("rysquests.editor.field.title"), cards, item, "name", false, x, y, w);
        cursor = cardText(ui, tr("rysquests.editor.field.short_description"), cards, item, "desc", false, x, cursor, w);
        cursor = cardText(ui, tr("rysquests.editor.field.meta"), cards, item, "meta", false, x, cursor, w);
        cursor = cardText(ui, tr("rysquests.editor.field.description"), cards, item, "text", true, x, cursor, w);
        cursor = cardText(ui, tr("rysquests.editor.field.tip"), cards, item, "tip", false, x, cursor, w);
        cursor = iconRow(ui, tr("rysquests.editor.field.icon"), item.icon(), x, cursor, w, id -> updateCard(c -> copy(c, Optional.of(id), c.group(), c.art(), c.stage(), c.stageTitle(), c.done(), c.page(), c.quest(), c.needs(), c.gives())));
        String groupName = item.group().map(cards::groupTitle).map(this::text).orElse(tr("rysquests.editor.none"));
        cursor = cycleRow(ui, tr("rysquests.editor.field.group"), groupName, x, cursor, w, () -> edit(() -> updateCard(c -> {
            List<String> groups = ((CardsSection) state.section).groups();
            int at = c.group().map(groups::indexOf).orElse(-1) + 1;
            return copy(c, c.icon(), at < groups.size() ? Optional.of(groups.get(at)) : Optional.empty(), c.art(), c.stage(), c.stageTitle(), c.done(), c.page(), c.quest(), c.needs(), c.gives());
        })));
        int artColor = artPreview(ui, item);
        ui.fill(x + w - 28, cursor + 26, 18, 16, 4, artColor);
        cursor = inputRow(ui, tr("rysquests.editor.field.art"), prefix + "art", x, cursor, w, () -> item.art().orElse(""),
                value -> updateCard(c -> copy(c, c.icon(), c.group(), value.isBlank() ? Optional.empty() : Optional.of(value.trim()), c.stage(), c.stageTitle(), c.done(), c.page(), c.quest(), c.needs(), c.gives())));
        cursor = inputRow(ui, tr("rysquests.editor.field.stage"), prefix + "stage", x, cursor, w, () -> item.stage().orElse(""),
                value -> updateCard(c -> copy(c, c.icon(), c.group(), c.art(), value.isBlank() ? Optional.empty() : Optional.of(value.trim()), c.stageTitle(), c.done(), c.page(), c.quest(), c.needs(), c.gives())));
        cursor = renderDone(ui, item, prefix, x, cursor, w);
        cursor = pickRow(ui, tr("rysquests.editor.field.page"), item.page().map(GuideRef::toString).orElse(tr("rysquests.editor.none")), x, cursor, w, () -> pickGuide(ref -> updateCard(c -> copy(c, c.icon(), c.group(), c.art(), c.stage(), c.stageTitle(), c.done(), GuideRef.parse(ref), c.quest(), c.needs(), c.gives()))));
        cursor = pickRow(ui, tr("rysquests.editor.field.quest"), item.quest().map(ResourceLocation::toString).orElse(tr("rysquests.editor.none")), x, cursor, w, () -> pickQuest(id -> updateCard(c -> copy(c, c.icon(), c.group(), c.art(), c.stage(), c.stageTitle(), c.done(), c.page(), Optional.of(id), c.needs(), c.gives()))));
        cursor = renderCardItems(ui, item, x, cursor, w);
        return dangerRow(ui, x, cursor, w, card > 0, card + 1 < cards.cards().size(), () -> edit(() -> {
            setSection(SectionEdits.moveCard((CardsSection) state.section, card, -1));
            card--;
        }), () -> edit(() -> {
            setSection(SectionEdits.moveCard((CardsSection) state.section, card, 1));
            card++;
        }), cards.cards().size() > 1, () -> edit(() -> setSection(SectionEdits.removeCard((CardsSection) state.section, card))));
    }

    private static int artPreview(Ui ui, Card item) {
        try {
            return item.art().filter(art -> art.length() == 7 && art.startsWith("#")).map(art -> 0xFF000000 | Integer.parseInt(art.substring(1), 16)).orElse(ui.c("slot.bg"));
        } catch (NumberFormatException e) {
            return ui.c("slot.bg");
        }
    }

    private static Card copy(Card c, Optional<ResourceLocation> icon, Optional<String> group, Optional<String> art, Optional<String> stage, Optional<String> stageTitle,
            Optional<works.repka.rysquests.data.TaskDef> done, Optional<GuideRef> page, Optional<ResourceLocation> quest, List<CardsSection.Need> needs, List<ResourceLocation> gives) {
        return new Card(c.id(), icon, group, art, stage, stageTitle, done, page, quest, needs, gives);
    }

    /** Only the advancement condition can be typed here. A condition of another kind is kept as the file has it. */
    private float renderDone(Ui ui, Card item, String prefix, float x, float y, float w) {
        if (item.done().isPresent() && !(item.done().get() instanceof AdvancementTask)) {
            ui.label(tr("rysquests.editor.field.done"), x, y + 7, 11, Ui.REGULAR, ui.c("text.muted"));
            ui.labelFit(tr("rysquests.editor.done_in_file"), x, y + 25, w, 12, Ui.REGULAR, ui.c("button.fg"));
            return y + 44;
        }
        return inputRow(ui, tr("rysquests.editor.field.done"), prefix + "done", x, y, w, () -> item.done().map(task -> ((AdvancementTask) task).advancement().toString()).orElse(""), value -> {
            ResourceLocation id = ResourceLocation.tryParse(value.trim());
            Optional<works.repka.rysquests.data.TaskDef> done = value.isBlank() || id == null ? Optional.empty() : Optional.of(new AdvancementTask(id, Optional.empty()));
            updateCard(c -> copy(c, c.icon(), c.group(), c.art(), c.stage(), c.stageTitle(), done, c.page(), c.quest(), c.needs(), c.gives()));
        });
    }

    private static float pickRow(Ui ui, String label, String value, float x, float y, float w, Runnable open) {
        return cycleRow(ui, label, value, x, y, w, open);
    }

    private float renderCardItems(Ui ui, Card item, float x, float y, float w) {
        float cursor = rubric(ui, tr("rysquests.cards.needs") + " · " + item.needs().size(), tr("rysquests.editor.add_item"), x, y, w, () -> pickItem(tr("rysquests.cards.needs"), id -> updateCard(c -> {
            List<CardsSection.Need> needs = new ArrayList<>(c.needs());
            needs.add(new CardsSection.Need(id, 1));
            return copy(c, c.icon(), c.group(), c.art(), c.stage(), c.stageTitle(), c.done(), c.page(), c.quest(), needs, c.gives());
        })));
        for (int i = 0; i < item.needs().size(); i++) {
            CardsSection.Need need = item.needs().get(i);
            ItemStack stack = TaskText.stackOf(need.item());
            int index = i;
            float rowY = cursor;
            cursor = removableRow(ui, stack, stack.getHoverName().getString(), "", x, cursor, w, () -> edit(() -> updateCard(c -> {
                List<CardsSection.Need> needs = new ArrayList<>(c.needs());
                needs.remove(index);
                return copy(c, c.icon(), c.group(), c.art(), c.stage(), c.stageTitle(), c.done(), c.page(), c.quest(), needs, c.gives());
            })));
            String count = "×" + need.count();
            float countW = ui.width(count, 11, Ui.REGULAR) + 10;
            ui.labelRight(count, x + w - 30, rowY + 14, 11, Ui.REGULAR, ui.c("button.fg"));
            ui.hit(x + w - 30 - countW, rowY, countW, 28, () -> edit(() -> updateCard(c -> {
                List<CardsSection.Need> needs = new ArrayList<>(c.needs());
                needs.set(index, new CardsSection.Need(need.item(), Screen.hasShiftDown() ? Math.max(1, need.count() - 1) : need.count() + 1));
                return copy(c, c.icon(), c.group(), c.art(), c.stage(), c.stageTitle(), c.done(), c.page(), c.quest(), needs, c.gives());
            })));
        }
        cursor += 8;
        cursor = rubric(ui, tr("rysquests.cards.gives") + " · " + item.gives().size(), tr("rysquests.editor.add_item"), x, cursor, w, () -> pickItem(tr("rysquests.cards.gives"), id -> updateCard(c -> {
            List<ResourceLocation> gives = new ArrayList<>(c.gives());
            gives.add(id);
            return copy(c, c.icon(), c.group(), c.art(), c.stage(), c.stageTitle(), c.done(), c.page(), c.quest(), c.needs(), gives);
        })));
        for (int i = 0; i < item.gives().size(); i++) {
            ItemStack stack = TaskText.stackOf(item.gives().get(i));
            int index = i;
            cursor = removableRow(ui, stack, stack.getHoverName().getString(), "", x, cursor, w, () -> edit(() -> updateCard(c -> {
                List<ResourceLocation> gives = new ArrayList<>(c.gives());
                gives.remove(index);
                return copy(c, c.icon(), c.group(), c.art(), c.stage(), c.stageTitle(), c.done(), c.page(), c.quest(), c.needs(), gives);
            })));
        }
        return cursor + 8;
    }

    private void renderPicker(Ui ui, float x, float y, float w, float h) {
        boolean over = ui.over(x, y, 28, 28);
        ui.box(x, y, 28, 28, 7, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        ui.icon(Icons.Shape.CHEVRON_LEFT, x + 9, y + 9, 10, ui.c("button.fg"));
        ui.hit(x, y, 28, 28, this::closePicker);
        ui.labelFit(pickerTitle, x + 38, y + 14, w - 38, 15, Ui.DISPLAY, ui.c("text.primary"));
        float boxY = y + 38;
        ui.box(x, boxY, w, 32, 8, ui.c("slot.bg"), filter.focused() ? ui.c("card.border") : ui.c("button.border"));
        filter.render(ui, x + 10, boxY + 7, w - 20, 18, ui.c("text.primary"), tr("rysquests.editor.filter"));
        ui.hit(x, boxY, w, 32, () -> {
            focus(filter);
            filter.click(pointerX, pointerY);
        });
        if (!filter.text().equals(lastQuery)) {
            lastQuery = filter.text();
            found = picker.get();
            panelScroll.reset();
        }
        float listTop = boxY + 32 + 10;
        float listH = y + h - listTop;
        if (found.isEmpty()) {
            ui.label(tr("rysquests.editor.pick.empty"), x, listTop + 12, 12, Ui.REGULAR, ui.c("text.dim"));
            return;
        }
        panelScroll.content(found.size() * 37 - 5, Math.round(listH));
        int offset = panelScroll.offset();
        ui.clip(Math.round(x), Math.round(listTop), Math.round(w), Math.round(listH));
        List<Choice> list = found;
        for (int i = 0; i < list.size(); i++) {
            float rowY = listTop + i * 37 - offset;
            if (rowY + 32 > listTop && rowY < listTop + listH) {
                Choice choice = list.get(i);
                boolean overRow = ui.over(x, rowY, w, 32);
                ui.box(x, rowY, w, 32, 8, overRow ? ui.c("slot.hover") : ui.c("status.bg"), ui.c("status.border"));
                ui.item(choice.icon, x + 9, rowY + 8, 16);
                float textX = choice.icon.isEmpty() ? x + 11 : x + 32;
                float noteW = choice.note.isEmpty() ? 0 : Math.min(100, ui.width(choice.note, 11, Ui.REGULAR)) + 8;
                ui.labelFitTip(choice.label, textX, rowY + 16, x + w - 9 - noteW - textX, 12, Ui.REGULAR, ui.nameFg());
                if (!choice.note.isEmpty()) {
                    ui.labelFit(choice.note, x + w - 9 - noteW + 8, rowY + 16, noteW - 8, 11, Ui.REGULAR, ui.c("text.muted"));
                }
                ui.hit(x, rowY, w, 32, choice.action);
            }
        }
        ui.unclip();
    }

    // --- input ---

    void pointer(double mouseX, double mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
    }

    void drag(double mouseX, double mouseY) {
        bound.values().forEach(field -> field.area.drag(mouseX, mouseY));
        filter.drag(mouseX, mouseY);
    }

    void scroll(double mouseX, double mouseY, double amount) {
        if (playerView) {
            screen.scrollSectionPreview(mouseX, mouseY, amount);
        } else if (mouseX < leftEdge) {
            leftScroll.wheel(amount, WHEEL);
        } else if (mouseX < rightEdge) {
            tableScroll.wheel(amount, WHEEL);
        } else {
            panelScroll.wheel(amount, WHEEL);
        }
    }

    boolean typed(char c) {
        for (Bound field : bound.values()) {
            if (field.area.typed(c)) {
                return true;
            }
        }
        return filter.typed(c);
    }

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
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (picker != null) {
                closePicker();
            } else if (playerView) {
                leavePreview();
            } else {
                requestExit();
            }
            return true;
        }
        for (Bound field : bound.values()) {
            if (field.area.key(key)) {
                return true;
            }
        }
        filter.key(key);
        return true;
    }

    // --- for the autotest ---

    void newFromTemplateForTest(String template) {
        create(template);
    }

    /** Fills the matrix through the same steps the buttons take. */
    void buildMatrixForTest(String title, List<String> rows, List<String> columns, ResourceLocation icon) {
        setText(state.section.title(), title);
        for (int c = 0; c < columns.size(); c++) {
            MatrixSection matrix = (MatrixSection) state.section;
            if (c >= matrix.columns().size()) {
                setSection(SectionEdits.addColumn(matrix));
                matrix = (MatrixSection) state.section;
            }
            setText(matrix.columns().get(c).title(), columns.get(c));
        }
        for (int r = 0; r < rows.size(); r++) {
            MatrixSection matrix = (MatrixSection) state.section;
            if (r >= matrix.rows().size()) {
                setSection(SectionEdits.addRow(matrix));
                matrix = (MatrixSection) state.section;
            }
            Row item = matrix.rows().get(r);
            setText(matrix.rowTitle(item), rows.get(r));
            int rowIndex = r;
            setSection(SectionEdits.updateRow(matrix, r, target -> new Row(target.id(), target.title(), Optional.of(icon), target.group(), target.cells())));
            for (int c = 0; c < columns.size(); c++) {
                MatrixSection current = (MatrixSection) state.section;
                Column col = current.columns().get(c);
                setSection(SectionEdits.addWay(current, rowIndex, col));
                current = (MatrixSection) state.section;
                Row target = current.rows().get(rowIndex);
                Way added = target.ways(col).getFirst();
                setText(current.wayName(target, col, added), rows.get(r) + " / " + columns.get(c));
                setText(current.wayText(target, col, added), "A way.");
                setSection(SectionEdits.updateWay(current, rowIndex, col, 0, each -> wayWith(each, each.name(), each.text(), Optional.of(icon), each.mark(), each.needs(), each.links())));
            }
        }
        bound.clear();
    }

    void selectWayForTest(int newRow, int newColumn, int newWay) {
        select(Tab.WAY, newRow, newColumn, newWay);
    }

    ResourceLocation sectionIdForTest() {
        return state.section.id();
    }
}
