package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.markup.Markup;
import works.repka.rysquests.client.markup.Markup.Paragraph;
import works.repka.rysquests.client.markup.Markup.Span;
import works.repka.rysquests.data.Quest;
import works.repka.ryscore.ui.Draw;

/** Lays out parsed quest text with word wrap, inline item icons, clickable links and spoilers. */
final class TextBlock {
    private static final int ICON = 9;

    private TextBlock() {
    }

    /** @return the y below the text */
    static int render(Ui ui, String text, int x, int y, int width, java.util.function.Consumer<ItemStack> onItem) {
        int paragraphIndex = 0;
        for (Paragraph paragraph : Markup.parse(text)) {
            int key = paragraphIndex++;
            if (paragraph.spans().isEmpty()) {
                y += Ui.LINE / 2;
            } else if (paragraph.warning()) {
                int bottom = flow(ui, paragraph, x + 6, y + 3, width - 8, "state.warn", text, key, onItem, true);
                Draw.round(ui.g, x, y, width, bottom - y + 3, 3f, Draw.alpha(ui.color("state.warn"), 0.12f));
                Draw.rect(ui.g, x, y, 2, bottom - y + 3, ui.color("state.warn"));
                flow(ui, paragraph, x + 6, y + 3, width - 8, "state.warn", text, key, onItem, false);
                y = bottom + 6;
            } else {
                y = flow(ui, paragraph, x, y, width, "text.body", text, key, onItem, false);
            }
        }
        return y;
    }

    private record Word(MutableComponent text, int width, Runnable click, ItemStack icon, boolean link, boolean hidden) {
    }

    private static int flow(Ui ui, Paragraph paragraph, int x, int y, int width, String token, String source, int key,
            java.util.function.Consumer<ItemStack> onItem, boolean measure) {
        int cursor = x;
        int spaceWidth = ui.font.width(" ");
        String carry = "";
        for (Span span : paragraph.spans()) {
            String spoilerKey = source.hashCode() + ":" + key + ":" + span.text();
            boolean hidden = span.spoiler() && !BookState.REVEALED.contains(spoilerKey);
            Runnable reveal = hidden ? () -> BookState.REVEALED.add(spoilerKey) : null;
            for (Word word : words(ui, span, reveal, hidden, onItem, carry)) {
                carry = formatting(word.text.getString(), carry);
                if (word.width == 0) {
                    continue;
                }
                if (cursor > x && cursor + word.width > x + width) {
                    cursor = x;
                    y += Ui.LINE;
                }
                if (!measure) {
                    draw(ui, word, cursor, y, token);
                }
                cursor += word.width + spaceWidth;
            }
        }
        return y + Ui.LINE;
    }

    private static void draw(Ui ui, Word word, int x, int y, String token) {
        int textX = x;
        if (!word.icon.isEmpty()) {
            ui.g.pose().pushPose();
            ui.g.pose().translate(x, y - 1, 0);
            ui.g.pose().scale(0.5f, 0.5f, 1f);
            ui.g.renderItem(word.icon, 0, 0);
            ui.g.pose().popPose();
            textX += ICON;
            if (ui.over(x, y, word.width, Ui.LINE)) {
                ui.hoveredStack = word.icon;
            }
        }
        if (word.hidden) {
            Draw.rect(ui.g, x, y - 1, word.width, Ui.LINE - 1, ui.color("text.dim"));
        } else {
            ui.text(word.text, textX, y, word.link ? "chip.mod.fg" : token);
        }
        if (word.click != null) {
            ui.hit(x, y - 1, word.width, Ui.LINE, word.click);
        }
    }

    private static List<Word> words(Ui ui, Span span, Runnable reveal, boolean hidden, java.util.function.Consumer<ItemStack> onItem, String carry) {
        List<Word> out = new ArrayList<>();
        if (span.isLink()) {
            out.add(link(ui, span, reveal, hidden, onItem));
            return out;
        }
        String active = carry;
        for (String piece : span.text().split(" ")) {
            if (piece.isEmpty()) {
                continue;
            }
            MutableComponent text = styled(active + piece, span);
            active = formatting(piece, active);
            out.add(new Word(text, ui.font.width(text), reveal, ItemStack.EMPTY, false, hidden));
        }
        return out;
    }

    private static Word link(Ui ui, Span span, Runnable reveal, boolean hidden, java.util.function.Consumer<ItemStack> onItem) {
        ItemStack icon = ItemStack.EMPTY;
        String label = span.text();
        Runnable click = reveal;
        if (span.kind().equals("item") && ResourceLocation.tryParse(span.target()) != null) {
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
        MutableComponent text = styled(label, span);
        return new Word(text, ui.font.width(text) + (icon.isEmpty() ? 0 : ICON), click, icon, true, hidden);
    }

    private static MutableComponent styled(String text, Span span) {
        MutableComponent component = Component.literal(text);
        if (span.bold()) {
            component.withStyle(ChatFormatting.BOLD);
        }
        if (span.italic()) {
            component.withStyle(ChatFormatting.ITALIC);
        }
        return component;
    }

    /** Colors in FTB texts apply across words, while each word is drawn on its own. */
    private static String formatting(String text, String current) {
        String active = current;
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '§') {
                String code = text.substring(i, i + 2);
                active = code.equals("§r") ? "" : Character.isLetter(code.charAt(1)) && code.charAt(1) > 'f' ? active + code : code;
            }
        }
        return active;
    }
}
