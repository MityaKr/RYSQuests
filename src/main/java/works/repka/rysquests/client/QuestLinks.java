package works.repka.rysquests.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import works.repka.rysquests.data.Quest;
import works.repka.ryscore.theme.ThemeManager;

/** Turns {@code [[quest:id]]} in chat into a link that opens the book on that quest. */
public final class QuestLinks {
    private static final Pattern LINK = Pattern.compile("\\[\\[quest:([^\\]|]+)]]");
    private static final int RGB = 0xFFFFFF;

    private QuestLinks() {
    }

    public static void onChat(ClientChatReceivedEvent event) {
        Component expanded = expand(event.getMessage());
        if (expanded != null) {
            event.setMessage(expanded);
        }
    }

    /** @return the message with its links, or null when it has none the book knows */
    private static Component expand(Component message) {
        String text = message.getString();
        Matcher matcher = LINK.matcher(text);
        MutableComponent out = Component.empty();
        boolean found = false;
        int position = 0;
        while (matcher.find()) {
            ResourceLocation id = ResourceLocation.tryParse(matcher.group(1));
            Quest quest = id == null ? null : ClientBook.current().quests().get(id);
            if (quest != null) {
                found = true;
                out.append(text.substring(position, matcher.start()));
                out.append(link(quest));
                position = matcher.end();
            }
        }
        if (!found) {
            return null;
        }
        return out.append(text.substring(position));
    }

    private static Component link(Quest quest) {
        Component title = Component.translatable(quest.title());
        TextColor color = TextColor.fromRgb(ThemeManager.get().c("chip.mod.fg") & RGB);
        return Component.literal("[").append(title).append("]").withStyle(style -> style.withColor(color).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rysquests_book open " + quest.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, title)));
    }
}
