package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.QuestBook;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Field;
import works.repka.ryscore.ui.Icons;

/**
 * The header of the book (mockup {@code .head}, pictures 03, 08, 11, 32, 46): the book's icon and name ("Квесты" and the mod's
 * icon when the book names none), the tabs with the badge on "Now", search, the gear, close; for an operator "Редактор", and in the
 * editor "Сохранить · N". Compact: the name hides and search becomes an icon. Bare (an empty or loading book): name and close only.
 */
final class BookHeader {
    static final int HEIGHT = 56;
    private static final ResourceLocation MOD_ICON = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "textures/gui/sidebar_book.png");
    private static final int PAD_LEFT = 16;
    private static final int PAD_RIGHT = 14;
    private static final int GAP = 18;
    private static final int BUTTON = 34;
    private static final int SEARCH_W = 220;
    private static final float TAB_PX = 13.5f;
    private static final int TAB_PAD = 13;
    private static final int TAB_PAD_MIN = 6;

    /** What the header asks of the screen. */
    interface Host {
        List<BookTabs.Tab> tabs();

        @Nullable
        BookTabs.Tab activeTab();

        void openTab(BookTabs.Tab tab);

        /** Whether the settings page is open: the gear is lit and no tab is. */
        boolean settingsOpen();

        void toggleSettings();

        /** "Редактор": opens or leaves the editor. */
        void toggleEditor();

        /** The editor's unsaved work, or null outside the editor. */
        @Nullable
        EditorSession session();

        void focusSearch();

        void close();
    }

    /** What the header shows. */
    enum Mode {
        /** Name, tabs, search and buttons. */
        FULL,
        /** Name and close only: an empty book, a book still loading, a first-run step. */
        BARE
    }

    @Nullable
    private final Field search;
    private final Host host;

    BookHeader(Host host, @Nullable Field search) {
        this.host = host;
        this.search = search;
    }

    void render(Ui ui, int x, int y, int w, Mode mode, boolean compact) {
        render(ui, x, y, w, mode, compact, null);
    }

    /** @param page a page in a bare header, which may name the window and draw after the name (the import's steps), or null */
    void render(Ui ui, int x, int y, int w, Mode mode, boolean compact, @Nullable BookView page) {
        ui.rect(x, y + HEIGHT - 1, w, 1, ui.c("panel.border"));
        float cy = y + (HEIGHT - 1) / 2f;
        float pen = x + PAD_LEFT;
        String title = page != null ? page.headerTitle() : null;
        if (title != null) {
            ui.image(MOD_ICON, pen, cy - 14, 28, 28, 16, 16);
            ui.label(title, pen + 28 + 9, cy, 15, Ui.DISPLAY, ui.c("text.primary"));
            pen += 28 + 9 + ui.width(title, 15, Ui.DISPLAY);
        } else {
            pen = renderBrand(ui, pen, cy, compact);
        }
        float right = x + w - PAD_RIGHT;
        right = iconButton(ui, Icons.Shape.CROSS, right - BUTTON, cy, false, Component.translatable("rysquests.header.close"), host::close) - 8;
        if (mode == Mode.BARE) {
            if (page != null) {
                page.renderHeader(ui, pen, cy);
            }
            hideSearch();
            return;
        }
        EditorSession session = host.session();
        if (session != null) {
            right = renderEditorButtons(ui, session, right, cy, compact);
            hideSearch();
        } else {
            right = iconButton(ui, Icons.Shape.GEAR, right - BUTTON, cy, host.settingsOpen(), Component.translatable("rysquests.header.settings"), host::toggleSettings) - 8;
            if (BookRole.operator() && compact) {
                right = iconButton(ui, Icons.Shape.PENCIL, right - BUTTON, cy, false, Component.translatable("rysquests.header.editor"), host::toggleEditor) - 8;
            } else if (BookRole.operator()) {
                right = smallButton(ui, Component.translatable("rysquests.header.editor").getString(), Icons.Shape.PENCIL, right, cy, false, false, false,
                        host::toggleEditor) - 8;
            }
            if (compact) {
                right = iconButton(ui, Icons.Shape.MAGNIFIER, right - BUTTON, cy, false, Component.translatable("rysquests.header.search"), host::focusSearch) - 8;
                hideSearch();
            } else {
                renderSearch(ui, right - SEARCH_W, cy);
                right -= SEARCH_W + 8;
            }
        }
        renderTabs(ui, pen + GAP, y, right - GAP + 8 - (pen + GAP), session != null);
    }

    private float renderBrand(Ui ui, float x, float cy, boolean compact) {
        QuestBook book = BookData.book();
        ItemStack icon = book.meta().icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        if (!icon.isEmpty()) {
            ui.item(icon, x, cy - 14, 28);
        } else {
            ui.image(MOD_ICON, x, cy - 14, 28, 28, 16, 16);
        }
        if (compact) {
            return x + 28;
        }
        String title = book.meta().title().map(key -> Component.translatable(key).getString()).orElse(Component.translatable("rysquests.book.title").getString());
        float textX = x + 28 + 9;
        String shown = ui.fit(title, 180, 15, Ui.DISPLAY);
        ui.label(shown, textX, cy, 15, Ui.DISPLAY, ui.c("text.primary"));
        if (!shown.equals(title)) {
            ui.tooltipIfOver(textX, cy - 10, ui.width(shown, 15, Ui.DISPLAY), 20, Component.literal(title));
        }
        return textX + ui.width(shown, 15, Ui.DISPLAY);
    }

    private void renderTabs(Ui ui, float x, int y, float room, boolean editing) {
        List<BookTabs.Tab> tabs = editing ? BookTabs.ALL : host.tabs();
        if (tabs.isEmpty() || room <= 0) {
            return;
        }
        BookTabs.Tab active = host.settingsOpen() ? null : host.activeTab();
        QuestBook book = BookData.book();
        float[] widths = new float[tabs.size()];
        float text = 0;
        for (int i = 0; i < tabs.size(); i++) {
            widths[i] = tabContent(ui, tabs.get(i), book, editing);
            text += widths[i];
        }
        float gap = 2;
        float pad = Math.max(TAB_PAD_MIN, Math.min(TAB_PAD, (room - text - gap * (tabs.size() - 1)) / (tabs.size() * 2f)));
        float pen = x;
        float cy = y + (HEIGHT - 1) / 2f;
        for (int i = 0; i < tabs.size(); i++) {
            BookTabs.Tab tab = tabs.get(i);
            float tw = widths[i] + pad * 2;
            if (pen + tw > x + room + 1) {
                break;
            }
            boolean on = tab == active;
            boolean empty = editing && !tab.hasContent().test(book);
            boolean over = ui.over(pen, y, tw, HEIGHT - 1);
            if (on) {
                ui.theme.tabLine(ui.g, Math.round(pen / 2f), Math.round(y / 2f), Math.round(tw / 2f), (HEIGHT) / 2, true, false);
            }
            int fg = ui.c(empty ? "text.dim" : on || over ? "text.primary" : "text.muted");
            float tx = pen + pad;
            ui.label(tab.label(), tx, cy, TAB_PX, Ui.MEDIUM, fg);
            tx += ui.width(tab.label(), TAB_PX, Ui.MEDIUM);
            int badge = editing ? 0 : tab.badge().getAsInt();
            if (badge > 0) {
                String count = String.valueOf(badge);
                float bw = Math.max(17, ui.width(count, 11, Ui.SEMIBOLD) + 10);
                ui.fill(tx + 6, cy - 8.5f, bw, 17, 8.5f, ui.c("text.primary"));
                ui.labelCentered(count, tx + 6 + bw / 2f, cy, 11, Ui.SEMIBOLD, ui.c("accent.text"));
            } else if (empty) {
                ui.icon(Icons.Shape.PLUS, tx + 6, cy - 6, 12, ui.c("text.dim"));
            }
            ui.hit(pen, y, tw, HEIGHT - 1, () -> host.openTab(tab));
            pen += tw + gap;
        }
    }

    private static float tabContent(Ui ui, BookTabs.Tab tab, QuestBook book, boolean editing) {
        float w = ui.width(tab.label(), TAB_PX, Ui.MEDIUM);
        int badge = editing ? 0 : tab.badge().getAsInt();
        if (badge > 0) {
            w += 6 + Math.max(17, ui.width(String.valueOf(badge), 11, Ui.SEMIBOLD) + 10);
        } else if (editing && !tab.hasContent().test(book)) {
            w += 6 + 12;
        }
        return w;
    }

    private void renderSearch(Ui ui, float x, float cy) {
        boolean focused = search != null && search.isFocused();
        boolean over = ui.over(x, cy - 17, SEARCH_W, 34);
        ui.box(x, cy - 17, SEARCH_W, 34, 9, ui.c("input.bg"), ui.c(focused ? "text.secondary" : "panel.border"));
        ui.icon(Icons.Shape.MAGNIFIER, x + 11, cy - 7.5f, 15, ui.c("text.muted"));
        String key = focused ? "Esc" : "/";
        float kbdW = ui.width(key, 11, Ui.REGULAR) + 10;
        float kbdX = x + SEARCH_W - 10 - kbdW;
        ui.box(kbdX, cy - 9, kbdW, 18, 5, ui.c("button.bg"), ui.c("button.border"));
        ui.labelCentered(key, kbdX + kbdW / 2f, cy, 11, Ui.REGULAR, ui.c("text.muted"));
        if (search != null) {
            search.visible = true;
            search.setHint(Component.translatable("rysquests.header.search_hint"));
            search.setX(Math.round((x + 34) / 2f));
            search.setY(Math.round(cy / 2f) - 5);
            search.setWidth(Math.round((kbdX - 6 - x - 34) / 2f));
            search.render(ui.g, ui.mouseX / 2, ui.mouseY / 2, 0);
        }
        if (!focused && over) {
            ui.hit(x, cy - 17, SEARCH_W, 34, host::focusSearch);
        }
    }

    private void hideSearch() {
        if (search != null) {
            search.visible = false;
            if (search.isFocused()) {
                search.setFocused(false);
            }
        }
    }

    /** "Редактор" pressed, "Открыть игрокам" while the book is closed, undo, "Сохранить · N". @return the new right edge */
    private float renderEditorButtons(Ui ui, EditorSession session, float right, float cy, boolean compact) {
        int unsaved = session.unsaved();
        String save = Component.translatable("rysquests.header.save").getString() + (unsaved > 0 ? " · " + unsaved : "");
        right = smallButton(ui, save, Icons.Shape.SAVE, right, cy, false, true, false, unsaved > 0 ? session::save : null) - 8;
        right = iconButtonSmall(ui, Icons.Shape.UNDO, right - 28, cy, Component.translatable("rysquests.header.undo"), session.canUndo() ? session::undo : null) - 8;
        if (!BookData.book().meta().published()) {
            right = smallButton(ui, Component.translatable("rysquests.header.publish").getString(), Icons.Shape.EYE, right, cy, false, false, compact,
                    session::publish) - 8;
        }
        return smallButton(ui, Component.translatable("rysquests.header.editor").getString(), Icons.Shape.PENCIL, right, cy, true, false, compact,
                host::toggleEditor) - 8;
    }

    /** A 34 square button with a line icon (mockup {@code .ib}); lit draws it pressed. @return its left edge */
    static float iconButton(Ui ui, Icons.Shape icon, float x, float cy, boolean lit, Component tooltip, Runnable action) {
        float y = cy - BUTTON / 2f;
        boolean over = ui.over(x, y, BUTTON, BUTTON);
        ui.box(x, y, BUTTON, BUTTON, 9, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c(lit ? "text.secondary" : "button.border"));
        ui.icon(icon, x + 9, y + 9, 16, ui.c(lit || over ? "text.primary" : "text.secondary"));
        ui.tooltipIfOver(x, y, BUTTON, BUTTON, tooltip);
        ui.hit(x, y, BUTTON, BUTTON, action);
        return x;
    }

    private static float iconButtonSmall(Ui ui, Icons.Shape icon, float x, float cy, Component tooltip, @Nullable Runnable action) {
        float y = cy - 14;
        boolean over = action != null && ui.over(x, y, 28, 28);
        int fg = ui.c(over ? "text.primary" : "text.secondary");
        ui.box(x, y, 28, 28, 9, over ? ui.c("slot.hover") : ui.c("button.bg"), ui.c("button.border"));
        ui.icon(icon, x + 7, y + 7, 14, action == null ? Draw.alpha(fg, 0.45f) : fg);
        ui.tooltipIfOver(x, y, 28, 28, tooltip);
        if (action != null) {
            ui.hit(x, y, 28, 28, action);
        }
        return x;
    }

    /**
     * A small button with an icon and words (mockup {@code .btn.sm}), placed left of {@code right}. Pressed draws it as a toggle that
     * is on; primary is the amber one; iconOnly keeps the icon and moves the words to a tooltip (compact header).
     *
     * @return its left edge
     */
    private static float smallButton(Ui ui, String label, Icons.Shape icon, float right, float cy, boolean pressed, boolean primary, boolean iconOnly,
            @Nullable Runnable action) {
        float h = 28;
        float w = iconOnly ? 28 : 10 + 14 + 7 + ui.width(label, 12, primary ? Ui.SEMIBOLD : Ui.MEDIUM) + 10;
        float x = right - w;
        float y = cy - h / 2f;
        boolean over = action != null && ui.over(x, y, w, h);
        int fg;
        if (primary) {
            int fill = ui.c("accent");
            ui.fill(x, y, w, h, 9, action == null ? Draw.alpha(fill, 0.5f) : over ? Draw.mix(fill, 0xFFFFFFFF, 0.1f) : fill);
            fg = ui.c("accent.text");
        } else {
            ui.box(x, y, w, h, 9, pressed ? ui.c("tab.active.bg") : over ? ui.c("slot.hover") : ui.c("button.bg"),
                    ui.c(pressed ? "text.secondary" : "button.border"));
            fg = ui.c(pressed || over ? "text.primary" : "text.secondary");
        }
        if (iconOnly) {
            ui.icon(icon, x + 7, y + 7, 14, fg);
            ui.tooltipIfOver(x, y, w, h, Component.literal(label));
        } else {
            ui.icon(icon, x + 10, y + 7, 14, fg);
            ui.label(label, x + 10 + 14 + 7, cy, 12, primary ? Ui.SEMIBOLD : Ui.MEDIUM, fg);
        }
        if (action != null) {
            ui.hit(x, y, w, h, action);
        }
        return x;
    }
}
