package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientMessages;
import works.repka.ryscore.ui.Icons;

/**
 * The book's message line (picture 48, plan section 3): the server's answer or refusal as one line over the bottom edge of the page,
 * with a red border, its words, its actions and a cross. It moves nothing: the grid and buttons stay where they were, the line only
 * covers what is under it until closed. One message at a time; a new one replaces the old ({@link ClientMessages}).
 *
 * <p>Actions belong to a message's lang key. A screen that knows what to offer registers it once:
 *
 * <pre>
 * BookMessageLine.action("rysquests.claim.inventory_full", "rysquests.message.claim_again", message -> claimAgain());
 * </pre>
 *
 * The first sentence of the text is drawn bold, as in the mockup: "Покупка не прошла. «Жетон инженера» сегодня уже куплен…".
 */
final class BookMessageLine {
    static final int MARGIN = 12;
    private static final float PX = 12.5f;
    private static final float LINE = 17.5f;
    private static final int PAD_Y = 7;
    private static final int PAD_LEFT = 12;
    private static final int PAD_RIGHT = 8;
    private static final int GAP = 10;

    /** One button of a message. @param labelKey its words */
    record Action(String labelKey, Consumer<ClientMessages.Message> run) {
    }

    private static final Map<String, List<Action>> ACTIONS = new HashMap<>();

    private BookMessageLine() {
    }

    /** Adds a button to every message with this lang key. */
    static void action(String messageKey, String labelKey, Consumer<ClientMessages.Message> run) {
        ACTIONS.computeIfAbsent(messageKey, key -> new ArrayList<>()).add(new Action(labelKey, run));
    }

    /** Draws the latest message over the bottom of the area {x, y, w, h}, if there is one. */
    static void render(Ui ui, int[] area) {
        ClientMessages.Message message = ClientMessages.latest();
        if (message == null) {
            return;
        }
        float x = area[0] + MARGIN;
        float w = area[2] - MARGIN * 2;
        List<Action> actions = ACTIONS.getOrDefault(message.key(), List.of());
        float buttonsW = 0;
        for (Action action : actions) {
            buttonsW += buttonWidth(ui, action) + 6;
        }
        float closeW = 28;
        float textX = x + PAD_LEFT + 16 + GAP;
        float textW = x + w - PAD_RIGHT - closeW - buttonsW - GAP - textX;
        String text = message.text().getString();
        int split = boldEnd(text);
        List<List<Word>> lines = wrap(ui, text, split, textW);
        float h = Math.max(42, PAD_Y * 2 + lines.size() * LINE);
        float y = area[1] + area[3] - MARGIN - h;
        ui.shadow(x, y, w, h, 10, 0.55f);
        ui.box(x, y, w, h, 10, ui.c("sidebar.bg"), ui.c(message.ok() ? "button.border" : "state.bad"));
        // Clicks on the line itself go nowhere: it covers what is under it. Its own buttons come after and win.
        ui.hit(x, y, w, h, () -> { });
        float cy = y + h / 2f;
        if (message.ok()) {
            ui.check(x + PAD_LEFT, cy - 8, 16, ui.c("state.ok"));
        } else {
            ui.icon(Icons.Shape.WARN, x + PAD_LEFT, cy - 8, 16, ui.c("state.bad"));
        }
        float top = cy - lines.size() * LINE / 2f;
        for (int i = 0; i < lines.size(); i++) {
            float pen = textX;
            for (Word word : lines.get(i)) {
                ui.label(word.text, pen, top + LINE * i + LINE / 2f, PX, word.bold ? Ui.SEMIBOLD : Ui.REGULAR, ui.c(word.bold ? "text.primary" : "text.body"));
                pen += word.width;
            }
        }
        float right = x + w - PAD_RIGHT;
        boolean overClose = ui.over(right - closeW, cy - 14, closeW, 28);
        if (overClose) {
            ui.fill(right - closeW, cy - 14, closeW, 28, 9, ui.c("button.bg"));
        }
        ui.icon(Icons.Shape.CROSS, right - closeW + 7, cy - 7, 14, ui.c(overClose ? "text.primary" : "text.dim"));
        ui.tooltipIfOver(right - closeW, cy - 14, closeW, 28, Component.translatable("rysquests.message.close"));
        ui.hit(right - closeW, cy - 14, closeW, 28, ClientMessages::dismiss);
        right -= closeW + 6;
        for (int i = actions.size() - 1; i >= 0; i--) {
            Action action = actions.get(i);
            float bw = buttonWidth(ui, action);
            ui.button(Component.translatable(action.labelKey()).getString(), right - bw, cy - 14, bw, 28, 9, 12, Ui.MEDIUM, 10, Ui.Style.PLAIN,
                    () -> action.run().accept(message));
            right -= bw + 6;
        }
    }

    private static float buttonWidth(Ui ui, Action action) {
        return ui.buttonWidth(Component.translatable(action.labelKey()).getString(), 12, Ui.MEDIUM, 10);
    }

    /** Where the first sentence ends: after its full stop, or 0 when there is no second sentence. */
    static int boldEnd(String text) {
        int stop = text.indexOf(". ");
        return stop < 0 ? 0 : stop + 1;
    }

    private record Word(String text, boolean bold, float width) {
    }

    private static List<List<Word>> wrap(Ui ui, String text, int boldEnd, float width) {
        List<List<Word>> lines = new ArrayList<>();
        List<Word> line = new ArrayList<>();
        float pen = 0;
        int index = 0;
        for (String part : text.split("(?<= )")) {
            boolean bold = index < boldEnd;
            index += part.length();
            float w = ui.width(part, PX, bold ? Ui.SEMIBOLD : Ui.REGULAR);
            if (pen + w > width && !line.isEmpty() && !part.isBlank()) {
                lines.add(line);
                line = new ArrayList<>();
                pen = 0;
            }
            line.add(new Word(part, bold, w));
            pen += w;
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines.isEmpty() ? List.of(List.of()) : lines;
    }

    @Nullable
    static ClientMessages.Message latest() {
        return ClientMessages.latest();
    }
}
