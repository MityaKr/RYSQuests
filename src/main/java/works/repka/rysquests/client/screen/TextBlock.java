package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.markup.Markup;
import works.repka.rysquests.client.markup.Markup.Paragraph;
import works.repka.rysquests.client.markup.Markup.Span;
import works.repka.rysquests.data.Quest;
import works.repka.ryscore.ui.Type;

/** Lays out parsed quest text with word wrap, inline item icons, clickable links and spoilers. */
final class TextBlock {
    private static final float ICON_GAP = 4f;
    private static final char FORMAT = '§';

    /** The look of the text: its size, line height and color. */
    record Look(float px, float lineHeight, int color) {
        float iconSize() {
            return Math.round(px * 0.97f);
        }
    }

    private TextBlock() {
    }

    /** @return the y below the text */
    static float render(Ui ui, String text, float x, float y, float width, Look look, Consumer<ItemStack> onItem) {
        int paragraphIndex = 0;
        Format format = new Format();
        for (Paragraph paragraph : Markup.parse(text)) {
            int key = paragraphIndex++;
            if (paragraph.spans().isEmpty()) {
                y += look.lineHeight / 2;
            } else if (paragraph.warning()) {
                Look warning = new Look(look.px, look.lineHeight, ui.c("state.warn"));
                float bottom = flow(ui, paragraph, x + 12, y + 6, width - 16, warning, text, key, onItem, new Format(), true);
                ui.fill(x, y, width, bottom - y + 6, 6, works.repka.ryscore.ui.Draw.alpha(ui.c("state.warn"), 0.12f));
                ui.rect(x, y, 3, bottom - y + 6, ui.c("state.warn"));
                flow(ui, paragraph, x + 12, y + 6, width - 16, warning, text, key, onItem, new Format(), false);
                y = bottom + 12;
            } else {
                y = flow(ui, paragraph, x, y, width, look, text, key, onItem, format, false);
            }
        }
        return y;
    }

    /** The formatting codes of FTB texts stay active across words and lines of a paragraph. */
    private static final class Format {
        int color;
        boolean bold;

        void apply(char code) {
            ChatFormatting formatting = ChatFormatting.getByCode(code);
            if (formatting == null) {
                return;
            }
            if (formatting == ChatFormatting.RESET) {
                color = 0;
                bold = false;
            } else if (formatting == ChatFormatting.BOLD) {
                bold = true;
            } else if (formatting.isColor() && formatting.getColor() != null) {
                color = 0xFF000000 | formatting.getColor();
            }
        }
    }

    private record Piece(String text, int color, Type.Weight weight) {
    }

    /** @param attached sits right after the icon before it, instead of a word space away */
    private record Word(List<Piece> pieces, float width, Runnable click, ItemStack icon, boolean link, boolean hidden, boolean attached) {
    }

    private static float flow(Ui ui, Paragraph paragraph, float x, float y, float width, Look look, String source, int key,
            Consumer<ItemStack> onItem, Format format, boolean measure) {
        float cursor = x;
        float space = ui.width(" ", look.px, Ui.REGULAR);
        boolean endedWithSpace = true;
        Runnable previousClick = null;
        for (Span span : paragraph.spans()) {
            String spoilerKey = source.hashCode() + ":" + key + ":" + span.text();
            boolean hidden = span.spoiler() && !BookState.REVEALED.contains(spoilerKey);
            Runnable reveal = hidden ? () -> BookState.REVEALED.add(spoilerKey) : null;
            boolean glued = !endedWithSpace && !span.text().startsWith(" ");
            for (Word word : words(ui, span, reveal, hidden, onItem, look, format)) {
                float gap = word.attached ? ICON_GAP : glued ? 0 : space;
                glued = false;
                if (cursor > x && cursor + gap + word.width > x + width) {
                    cursor = x;
                    gap = 0;
                    y += look.lineHeight;
                }
                float at = cursor == x ? x : cursor + gap;
                if (!measure) {
                    // The underline of a link runs on under the spaces between its words.
                    if (word.link && !word.hidden && !word.attached && word.click != null && word.click == previousClick && at > cursor && cursor > x) {
                        ui.rect(cursor, y + look.lineHeight / 2f + look.px * 0.5f, at - cursor, 1, ui.c("chip.mod.fg"));
                    }
                    draw(ui, word, at, y, look);
                }
                previousClick = word.link && word.icon.isEmpty() ? word.click : null;
                cursor = at + word.width;
            }
            endedWithSpace = !span.isLink() && span.text().endsWith(" ");
        }
        return y + look.lineHeight;
    }

    private static void draw(Ui ui, Word word, float x, float y, Look look) {
        float cy = y + look.lineHeight / 2f;
        float pen = x;
        if (!word.icon.isEmpty()) {
            float icon = look.iconSize();
            ui.item(word.icon, x, cy - icon / 2f, icon);
            if (ui.over(x, y, word.width, look.lineHeight)) {
                ui.hoveredStack = word.icon;
            }
        }
        if (word.hidden) {
            ui.fill(x, cy - look.px * 0.6f, word.width, look.px * 1.2f, 3, ui.c("text.dim"));
        } else {
            for (Piece piece : word.pieces) {
                int color = word.link ? ui.c("chip.mod.fg") : piece.color != 0 ? piece.color : look.color;
                ui.label(piece.text, pen, cy, look.px, piece.weight, color);
                float w = ui.width(piece.text, look.px, piece.weight);
                if (word.link) {
                    ui.rect(pen, cy + look.px * 0.5f, w, 1, color);
                }
                pen += w;
            }
        }
        if (word.click != null) {
            ui.hit(x, y, word.width, look.lineHeight, word.click);
        }
    }

    private static List<Word> words(Ui ui, Span span, Runnable reveal, boolean hidden, Consumer<ItemStack> onItem, Look look, Format format) {
        List<Word> out = new ArrayList<>();
        if (span.isLink()) {
            link(ui, span, reveal, hidden, onItem, look, out);
            return out;
        }
        for (String piece : span.text().split(" ")) {
            List<Piece> pieces = pieces(piece, span, format);
            float width = 0;
            for (Piece part : pieces) {
                width += ui.width(part.text, look.px, part.weight);
            }
            if (width > 0) {
                out.add(new Word(pieces, width, reveal, ItemStack.EMPTY, false, hidden, false));
            }
        }
        return out;
    }

    private static List<Piece> pieces(String word, Span span, Format format) {
        List<Piece> pieces = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == FORMAT && i + 1 < word.length()) {
                flush(pieces, text, span, format);
                format.apply(word.charAt(++i));
            } else {
                text.append(c);
            }
        }
        flush(pieces, text, span, format);
        return pieces;
    }

    private static void flush(List<Piece> pieces, StringBuilder text, Span span, Format format) {
        if (text.length() > 0) {
            pieces.add(new Piece(text.toString(), format.color, span.bold() || format.bold ? Ui.SEMIBOLD : Ui.REGULAR));
            text.setLength(0);
        }
    }

    /** An icon and the words of the label: each can start a new line, as inline content in the mockups does. */
    private static void link(Ui ui, Span span, Runnable reveal, boolean hidden, Consumer<ItemStack> onItem, Look look, List<Word> out) {
        ItemStack icon = ItemStack.EMPTY;
        String label = span.text();
        Runnable click = reveal;
        if ((span.kind().equals("item") || span.kind().equals("recipe")) && ResourceLocation.tryParse(span.target()) != null) {
            icon = TaskText.stackOf(ResourceLocation.parse(span.target()));
            if (label.isEmpty()) {
                label = icon.getHoverName().getString();
            }
            ItemStack target = icon;
            click = reveal != null ? reveal : () -> onItem.accept(target);
        } else if (span.kind().equals("quest") && ResourceLocation.tryParse(span.target()) != null) {
            Quest quest = ClientBook.current().quests().get(ResourceLocation.parse(span.target()));
            if (quest != null) {
                if (label.isEmpty()) {
                    label = Component.translatable(quest.title()).getString();
                }
                click = reveal != null ? reveal : () -> BookState.select(quest);
            }
        }
        if (label.isEmpty()) {
            label = span.target();
        }
        boolean attached = !icon.isEmpty();
        if (attached) {
            out.add(new Word(List.of(), look.iconSize(), click, icon, true, hidden, false));
        }
        for (String word : ChatFormatting.stripFormatting(label).split(" ")) {
            if (!word.isEmpty()) {
                out.add(new Word(List.of(new Piece(word, 0, Ui.REGULAR)), ui.width(word, look.px, Ui.REGULAR), click, ItemStack.EMPTY, true, hidden, attached));
                attached = false;
            }
        }
    }
}
