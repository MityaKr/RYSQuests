package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.ui.Type;

/** An editable text in the interface's own font, in one line or wrapped. Coordinates are mockup pixels. */
final class TextArea {
    private static final long BLINK_MS = 500;
    private static final int WHEEL = 40;

    private final boolean multiline;
    private final float px;
    private final float lineHeight;
    private final Type.Weight weight;
    private final Map<Character, Float> charWidths = new HashMap<>();
    private final Runnable beforeChange;
    private String text = "";
    private int cursor;
    private int anchor;
    private boolean focused;
    private List<int[]> lines = List.of(new int[] {0, 0});
    private String wrappedText;
    private float wrappedWidth = -1;
    private float scroll;
    private float left;
    private float top;
    private float width;
    private float height;
    private Ui lastUi;
    private boolean revealCursor;

    /** @param beforeChange called before every edit, so that the owner can remember the state for undo */
    TextArea(boolean multiline, float px, float lineHeight, Type.Weight weight, Runnable beforeChange) {
        this.multiline = multiline;
        this.px = px;
        this.lineHeight = lineHeight;
        this.weight = weight;
        this.beforeChange = beforeChange;
    }

    String text() {
        return text;
    }

    /** Replaces the text without counting as an edit: for undo and for switching pages. */
    void setText(String value) {
        text = multiline ? value : value.replace("\n", " ");
        cursor = Math.min(cursor, text.length());
        anchor = cursor;
        wrappedText = null;
    }

    int cursor() {
        return cursor;
    }

    void place(int position) {
        cursor = Math.clamp(position, 0, text.length());
        anchor = cursor;
        revealCursor = true;
    }

    boolean focused() {
        return focused;
    }

    void focus(boolean value) {
        focused = value;
    }

    private int selectionStart() {
        return Math.min(cursor, anchor);
    }

    private int selectionEnd() {
        return Math.max(cursor, anchor);
    }

    private boolean hasSelection() {
        return cursor != anchor;
    }

    /** Puts text in place of the selection. */
    void insert(String value) {
        beforeChange.run();
        replaceSelection(multiline ? value : value.replace("\n", " "));
    }

    /** Surrounds the selection with marks, such as the stars of bold text. */
    void surround(String open, String close) {
        beforeChange.run();
        int start = selectionStart();
        int end = selectionEnd();
        text = text.substring(0, start) + open + text.substring(start, end) + close + text.substring(end);
        cursor = end + open.length();
        anchor = start + open.length();
        revealCursor = true;
    }

    private void replaceSelection(String value) {
        int start = selectionStart();
        text = text.substring(0, start) + value + text.substring(selectionEnd());
        cursor = start + value.length();
        anchor = cursor;
        revealCursor = true;
    }

    // --- layout ---

    private float charWidth(Ui ui, char c) {
        return charWidths.computeIfAbsent(c, key -> ui.width(String.valueOf(key), px, weight));
    }

    /** Breaks lines at spaces. Summing single characters keeps this linear, also for a long page. */
    private void wrap(Ui ui, float w) {
        if (text.equals(wrappedText) && w == wrappedWidth) {
            return;
        }
        wrappedText = text;
        wrappedWidth = w;
        List<int[]> out = new ArrayList<>();
        int start = 0;
        int lastSpace = -1;
        float pen = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                out.add(new int[] {start, i});
                start = i + 1;
                lastSpace = -1;
                pen = 0;
                continue;
            }
            float charW = charWidth(ui, c);
            if (multiline && pen + charW > w && i > start) {
                int breakAt = lastSpace >= start ? lastSpace + 1 : i;
                out.add(new int[] {start, breakAt});
                start = breakAt;
                lastSpace = -1;
                pen = 0;
                for (int j = start; j < i; j++) {
                    pen += charWidth(ui, text.charAt(j));
                }
            }
            if (c == ' ') {
                lastSpace = i;
            }
            pen += charW;
        }
        out.add(new int[] {start, text.length()});
        lines = out;
    }

    private int lineOf(int position) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (position >= lines.get(i)[0]) {
                return i;
            }
        }
        return 0;
    }

    private float xOf(Ui ui, int line, int position) {
        return ui.width(text.substring(lines.get(line)[0], Math.min(position, lines.get(line)[1])), px, weight);
    }

    private int positionAt(Ui ui, int line, float x) {
        int[] range = lines.get(line);
        float pen = 0;
        for (int i = range[0]; i < range[1]; i++) {
            float charW = charWidth(ui, text.charAt(i));
            if (pen + charW / 2f > x) {
                return i;
            }
            pen += charW;
        }
        return range[1];
    }

    void render(Ui ui, float x, float y, float w, float h, int color, String hint) {
        lastUi = ui;
        left = x;
        top = y;
        width = w;
        height = h;
        wrap(ui, w);
        float content = lines.size() * lineHeight;
        if (revealCursor && multiline) {
            float caretY = lineOf(cursor) * lineHeight;
            if (caretY < scroll) {
                scroll = caretY;
            } else if (caretY + lineHeight > scroll + h) {
                scroll = caretY + lineHeight - h;
            }
        }
        revealCursor = false;
        scroll = Math.clamp(scroll, 0, Math.max(0, content - h));
        ui.clip(Math.round(x - 1), Math.round(y), Math.round(w + 2), Math.round(h));
        if (text.isEmpty()) {
            ui.label(hint, x, y + lineHeight / 2f, px, weight, ui.c("text.dim"));
        }
        int selectionColor = works.repka.ryscore.ui.Draw.alpha(ui.c("accent"), 0.3f);
        for (int i = 0; i < lines.size(); i++) {
            float lineY = y + i * lineHeight - scroll;
            if (lineY + lineHeight < y || lineY > y + h) {
                continue;
            }
            int[] range = lines.get(i);
            if (hasSelection() && selectionStart() <= range[1] && selectionEnd() >= range[0]) {
                float from = xOf(ui, i, Math.max(range[0], selectionStart()));
                float to = xOf(ui, i, Math.min(range[1], selectionEnd()));
                ui.rect(x + from, lineY, Math.max(2, to - from), lineHeight, selectionColor);
            }
            ui.label(text.substring(range[0], range[1]), x, lineY + lineHeight / 2f, px, weight, color);
        }
        if (focused && (Util.getMillis() / BLINK_MS) % 2 == 0) {
            int line = lineOf(cursor);
            ui.rect(x + xOf(ui, line, cursor), y + line * lineHeight - scroll + 2, 1, lineHeight - 4, ui.c("text.primary"));
        }
        ui.unclip();
    }

    // --- input ---

    private int pointerPosition(double mouseX, double mouseY) {
        int line = Math.clamp((int) ((mouseY - top + scroll) / lineHeight), 0, lines.size() - 1);
        return positionAt(lastUi, line, (float) (mouseX - left));
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    void click(double mouseX, double mouseY) {
        if (lastUi == null) {
            return;
        }
        focused = true;
        place(pointerPosition(mouseX, mouseY));
    }

    void drag(double mouseX, double mouseY) {
        if (lastUi != null && focused) {
            cursor = pointerPosition(mouseX, mouseY);
        }
    }

    void wheel(double notches) {
        scroll -= (float) notches * WHEEL;
    }

    boolean typed(char c) {
        if (!focused || c < ' ' || c == 127) {
            return false;
        }
        insert(String.valueOf(c));
        return true;
    }

    private void move(int position, boolean select) {
        cursor = Math.clamp(position, 0, text.length());
        if (!select) {
            anchor = cursor;
        }
        revealCursor = true;
    }

    private void moveLine(int delta, boolean select) {
        if (lastUi == null) {
            return;
        }
        int line = lineOf(cursor);
        int target = line + delta;
        if (target < 0) {
            move(0, select);
        } else if (target >= lines.size()) {
            move(text.length(), select);
        } else {
            move(positionAt(lastUi, target, xOf(lastUi, line, cursor)), select);
        }
    }

    private int wordLeft() {
        int i = cursor;
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ' && text.charAt(i - 1) != '\n') {
            i--;
        }
        return i;
    }

    private int wordRight() {
        int i = cursor;
        while (i < text.length() && text.charAt(i) != ' ' && text.charAt(i) != '\n') {
            i++;
        }
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private void delete(int from, int to) {
        if (from == to) {
            return;
        }
        beforeChange.run();
        text = text.substring(0, from) + text.substring(to);
        cursor = from;
        anchor = from;
        revealCursor = true;
    }

    /** @return whether the key did something here */
    boolean key(int key) {
        if (!focused) {
            return false;
        }
        boolean shift = Screen.hasShiftDown();
        boolean ctrl = Screen.hasControlDown();
        var keyboard = Minecraft.getInstance().keyboardHandler;
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> {
                    anchor = 0;
                    cursor = text.length();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    keyboard.setClipboard(text.substring(selectionStart(), selectionEnd()));
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    keyboard.setClipboard(text.substring(selectionStart(), selectionEnd()));
                    delete(selectionStart(), selectionEnd());
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    insert(keyboard.getClipboard().replace("\r", ""));
                    return true;
                }
                default -> {
                }
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> move(ctrl ? wordLeft() : hasSelection() && !shift ? selectionStart() : cursor - 1, shift);
            case GLFW.GLFW_KEY_RIGHT -> move(ctrl ? wordRight() : hasSelection() && !shift ? selectionEnd() : cursor + 1, shift);
            case GLFW.GLFW_KEY_UP -> moveLine(-1, shift);
            case GLFW.GLFW_KEY_DOWN -> moveLine(1, shift);
            case GLFW.GLFW_KEY_HOME -> move(ctrl ? 0 : lines.get(lineOf(cursor))[0], shift);
            case GLFW.GLFW_KEY_END -> move(ctrl ? text.length() : lines.get(lineOf(cursor))[1], shift);
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    delete(selectionStart(), selectionEnd());
                } else {
                    delete(ctrl ? wordLeft() : Math.max(0, cursor - 1), cursor);
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    delete(selectionStart(), selectionEnd());
                } else {
                    delete(cursor, ctrl ? wordRight() : Math.min(text.length(), cursor + 1));
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!multiline) {
                    return false;
                }
                insert("\n");
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
