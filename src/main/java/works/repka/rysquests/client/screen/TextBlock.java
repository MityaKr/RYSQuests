package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.integration.Ponders;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.markup.Markup;
import works.repka.rysquests.client.markup.Markup.Paragraph;
import works.repka.rysquests.client.markup.Markup.Span;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.GuideRef;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.Quest;
import works.repka.ryscore.ui.Type;

/**
 * Lays out parsed quest text with word wrap, inline item icons, clickable links and spoilers.
 *
 * <p>A layout is worked out once per text, width and look and kept, so that a frame only places what it already knows:
 * the parse, the measuring of every word and the wrapping used to run every frame, twice where a block is measured
 * before it is drawn. Words of one style on one line are drawn as one string, and lines outside the clip are skipped.
 */
final class TextBlock {
    private static final float WARN_TEXT_X = 12 + 16 + 10;
    private static final float ICON_GAP = 4f;
    private static final char FORMAT = '§';
    private static final int CACHE_SIZE = 96;

    /** The look of the text: its size, line height and color. */
    record Look(float px, float lineHeight, int color, int boldColor) {
        Look(float px, float lineHeight, int color) {
            this(px, lineHeight, color, color);
        }

        float iconSize() {
            return Math.round(px * 0.97f);
        }
    }

    /** What a layout depends on besides the text: spoilers opened since, the book behind links, the language. */
    private record Key(String text, float width, Look look, int revealed, String book, String language) {
    }

    /** A finished layout in coordinates relative to the block's top left, and its height. */
    private record Laid(List<Item> items, float height) {
    }

    private sealed interface Item permits Placed, Underline, WarningBox {
    }

    /** A word, or several plain words of one style merged into one string, at its place. */
    private record Placed(Word word, float x, float y) implements Item {
    }

    /** The underline of a link running on under the space between two of its words. */
    private record Underline(float x, float y, float w) implements Item {
    }

    private record WarningBox(float x, float y, float w, float h) implements Item {
    }

    private static final Map<Key, Laid> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Laid> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private TextBlock() {
    }

    /** @return the y below the text */
    static float render(Ui ui, String text, float x, float y, float width, Look look, Consumer<ItemStack> onItem) {
        Laid laid = layout(ui, text, width, look);
        draw(ui, laid, x, y, look, onItem);
        return y + laid.height;
    }

    private static Laid layout(Ui ui, String text, float width, Look look) {
        Key key = new Key(text, width, look, BookState.REVEALED.size(), ClientBook.current().hash(),
                Minecraft.getInstance().getLanguageManager().getSelected());
        Laid laid = CACHE.get(key);
        if (laid == null) {
            laid = lay(ui, text, width, look);
            CACHE.put(key, laid);
        }
        return laid;
    }

    private static Laid lay(Ui ui, String text, float width, Look look) {
        List<Item> items = new ArrayList<>();
        float y = 0;
        int paragraphIndex = 0;
        Format format = new Format();
        for (Paragraph paragraph : Markup.parse(text)) {
            int key = paragraphIndex++;
            if (paragraph.spans().isEmpty()) {
                y += look.lineHeight / 2;
            } else if (paragraph.warning()) {
                List<Item> inner = new ArrayList<>();
                // A callout (mockup .call): its own plate and border, a warning sign, the words in the body color.
                float bottom = flow(ui, paragraph, WARN_TEXT_X, y + 10, width - WARN_TEXT_X - 12, look, text, key, new Format(), inner);
                items.add(new WarningBox(0, y, width, bottom - y + 10));
                items.addAll(inner);
                y = bottom + 10 + 12;
            } else {
                y = flow(ui, paragraph, 0, y, width, look, text, key, format, items);
            }
        }
        return new Laid(List.copyOf(items), y);
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

    /**
     * @param click what a click does, or null; an item link instead names {@code item}, opened with the caller's handler
     * @param attached sits right after the icon before it, instead of a word space away
     * @param warning the word belongs to a warning block and takes its color
     */
    private record Word(List<Piece> pieces, float width, @Nullable Runnable click, ItemStack item, ItemStack icon, boolean link, boolean hidden,
            boolean attached, boolean warning) {
        boolean plain() {
            return !link && !hidden && icon.isEmpty() && click == null && item.isEmpty() && pieces.size() == 1;
        }
    }

    private static float flow(Ui ui, Paragraph paragraph, float x, float y, float width, Look look, String source, int key, Format format, List<Item> out) {
        boolean warning = paragraph.warning();
        float cursor = x;
        float space = ui.width(" ", look.px, Ui.REGULAR);
        boolean endedWithSpace = true;
        Object previousLink = null;
        for (Span span : paragraph.spans()) {
            String spoilerKey = source.hashCode() + ":" + key + ":" + span.text();
            boolean hidden = span.spoiler() && !BookState.REVEALED.contains(spoilerKey);
            Runnable reveal = hidden ? () -> BookState.REVEALED.add(spoilerKey) : null;
            boolean glued = !endedWithSpace && !span.text().startsWith(" ");
            Object linkIdentity = new Object();
            for (Word word : words(ui, span, reveal, hidden, look, format, warning)) {
                float gap = word.attached ? ICON_GAP : glued ? 0 : space;
                glued = false;
                if (cursor > x && cursor + gap + word.width > x + width) {
                    cursor = x;
                    gap = 0;
                    y += look.lineHeight;
                }
                float at = cursor == x ? x : cursor + gap;
                // The underline of a link runs on under the spaces between its words.
                boolean continuesLink = word.link && !word.hidden && !word.attached && word.icon.isEmpty() && previousLink == linkIdentity;
                if (continuesLink && at > cursor && cursor > x) {
                    out.add(new Underline(cursor, y + look.lineHeight / 2f + look.px * 0.5f, at - cursor));
                }
                if (!merge(out, word, at, y, gap, cursor)) {
                    out.add(new Placed(word, at, y));
                }
                previousLink = word.link && word.icon.isEmpty() ? linkIdentity : null;
                cursor = at + word.width;
            }
            endedWithSpace = !span.isLink() && span.text().endsWith(" ");
        }
        return y + look.lineHeight;
    }

    /** Joins a plain word to the plain word before it on the same line when both have one style: one string to draw. */
    private static boolean merge(List<Item> out, Word word, float at, float y, float gap, float cursor) {
        if (!word.plain() || out.isEmpty() || !(out.getLast() instanceof Placed last) || last.y != y || !last.word.plain()
                || last.word.warning != word.warning || Math.abs(last.x + last.word.width - cursor) > 0.01f) {
            return false;
        }
        Piece before = last.word.pieces.getFirst();
        Piece next = word.pieces.getFirst();
        if (before.color != next.color || before.weight != next.weight) {
            return false;
        }
        String joined = before.text + (at > cursor ? " " : "") + next.text;
        Word merged = new Word(List.of(new Piece(joined, before.color, before.weight)), at + word.width - last.x, null, ItemStack.EMPTY,
                ItemStack.EMPTY, false, false, false, word.warning);
        out.set(out.size() - 1, new Placed(merged, last.x, y));
        return true;
    }

    private static void draw(Ui ui, Laid laid, float x, float y, Look look, Consumer<ItemStack> onItem) {
        int warn = ui.c("state.warn");
        for (Item item : laid.items) {
            switch (item) {
                case WarningBox box -> {
                    ui.box(x + box.x, y + box.y, box.w, box.h, 10, ui.c("callout.bg"), ui.c("callout.border"));
                    ui.icon(works.repka.ryscore.ui.Icons.Shape.WARN, x + box.x + 12, y + box.y + 10 + (look.lineHeight - 16) / 2f, 16, warn);
                }
                case Underline line -> {
                    if (ui.rowVisible(y + line.y, 1)) {
                        ui.rect(x + line.x, y + line.y, line.w, 1, ui.c("chip.mod.fg"));
                    }
                }
                case Placed placed -> {
                    if (ui.rowVisible(y + placed.y, look.lineHeight)) {
                        drawWord(ui, placed.word, x + placed.x, y + placed.y, look, ui.c("text.body"), onItem);
                    }
                }
            }
        }
    }

    private static void drawWord(Ui ui, Word word, float x, float y, Look look, int warn, Consumer<ItemStack> onItem) {
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
            int base = word.warning ? warn : look.color;
            int bold = word.warning ? warn : look.boldColor;
            for (Piece piece : word.pieces) {
                int color = word.link ? ui.c("chip.mod.fg") : piece.color != 0 ? piece.color : piece.weight == Ui.SEMIBOLD ? bold : base;
                ui.label(piece.text, pen, cy, look.px, piece.weight, color);
                float w = ui.width(piece.text, look.px, piece.weight);
                if (word.link) {
                    ui.rect(pen, cy + look.px * 0.5f, w, 1, color);
                }
                pen += w;
            }
        }
        Runnable click = word.click != null ? word.click : word.item.isEmpty() ? null : () -> onItem.accept(word.item);
        if (click != null) {
            ui.hit(x, y, word.width, look.lineHeight, click);
        }
    }

    private static List<Word> words(Ui ui, Span span, Runnable reveal, boolean hidden, Look look, Format format, boolean warning) {
        List<Word> out = new ArrayList<>();
        if (span.isLink()) {
            link(ui, span, reveal, hidden, look, out, warning);
            return out;
        }
        for (String piece : span.text().split(" ")) {
            List<Piece> pieces = pieces(piece, span, format);
            float width = 0;
            for (Piece part : pieces) {
                width += ui.width(part.text, look.px, part.weight);
            }
            if (width > 0) {
                out.add(new Word(pieces, width, reveal, ItemStack.EMPTY, ItemStack.EMPTY, false, hidden, false, warning));
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
    private static void link(Ui ui, Span span, Runnable reveal, boolean hidden, Look look, List<Word> out, boolean warning) {
        ItemStack icon = ItemStack.EMPTY;
        ItemStack item = ItemStack.EMPTY;
        String label = span.text();
        Runnable click = reveal;
        String itemId = span.kind().equals("recipe") ? Markup.recipeItem(span.target()) : span.target();
        if (span.kind().equals("ponder") && ResourceLocation.tryParse(span.target()) != null) {
            ResourceLocation scene = ResourceLocation.parse(span.target());
            icon = TaskText.stackOf(scene);
            if (label.isEmpty()) {
                label = icon.getHoverName().getString();
            }
            if (Ponders.exists(scene)) {
                click = reveal != null ? reveal : () -> Ponders.open(scene);
            }
        } else if ((span.kind().equals("item") || span.kind().equals("recipe")) && ResourceLocation.tryParse(itemId) != null) {
            icon = TaskText.stackOf(ResourceLocation.parse(itemId));
            if (label.isEmpty()) {
                label = icon.getHoverName().getString();
            }
            // Opened with the caller's handler when drawn, so that the layout does not depend on who shows it.
            if (reveal == null) {
                item = icon;
            }
        } else if (span.kind().equals("quest") && ResourceLocation.tryParse(span.target()) != null) {
            Quest quest = ClientBook.current().quests().get(ResourceLocation.parse(span.target()));
            if (quest != null) {
                if (label.isEmpty()) {
                    label = Component.translatable(quest.title()).getString();
                }
                click = reveal != null ? reveal : () -> BookState.select(quest);
            }
        } else if (span.kind().equals("guide") && GuideRef.parse(span.target()).isPresent()) {
            GuideRef ref = GuideRef.parse(span.target()).get();
            Guide guide = ClientBook.current().guides().get(ref.guide());
            if (guide != null) {
                if (label.isEmpty()) {
                    int page = ref.page().map(guide::pageIndex).orElse(-1);
                    label = Component.translatable(page >= 0 ? guide.pages().get(page).title() : guide.title()).getString();
                }
                click = reveal != null ? reveal : () -> BookState.openRef(ref);
            } else if (ClientBook.current().sections().get(ref.guide()) instanceof PagesSection pages) {
                if (label.isEmpty()) {
                    label = Component.translatable(pages.title()).getString();
                }
                click = reveal != null ? reveal : () -> BookState.openRef(ref);
            }
        }
        if (label.isEmpty()) {
            label = span.target();
        }
        boolean attached = !icon.isEmpty();
        if (attached) {
            out.add(new Word(List.of(), look.iconSize(), click, item, icon, true, hidden, false, warning));
        }
        for (String word : ChatFormatting.stripFormatting(label).split(" ")) {
            if (!word.isEmpty()) {
                out.add(new Word(List.of(new Piece(word, 0, Ui.REGULAR)), ui.width(word, look.px, Ui.REGULAR), click, item, ItemStack.EMPTY, true, hidden, attached, warning));
                attached = false;
            }
        }
    }
}
