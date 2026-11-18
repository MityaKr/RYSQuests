package works.repka.rysquests.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.TaskDef;

/** The book's words and icons for a quest, for what draws a quest outside the book's window: the goals on the map (RYSMap, step T3). */
public final class QuestWords {
    private QuestWords() {
    }

    /** The task's name as the quest panel has it: "Посетить: Старый форт". */
    public static String task(Quest quest, TaskDef task) {
        return QuestPanel.taskTitle(quest, task);
    }

    /** The quest's rewards as one short line, at most three: "25 опыта · 1 мон."; empty when there are none. */
    public static String rewards(Quest quest) {
        return EdText.rewards(quest);
    }

    /** The quest's icon as the book's map draws it: its own icon, else the icon of its first task. */
    public static ItemStack icon(Quest quest) {
        return quest.icon().map(TaskText::stackOf).orElseGet(() -> quest.tasks().isEmpty() ? ItemStack.EMPTY : TaskText.icon(quest.tasks().getFirst()));
    }

    /** The chapter's name, or empty when the quest has none the book knows. */
    public static Component chapter(Quest quest) {
        return quest.chapter().map(id -> BookData.book().chapters().get(id)).<Component>map(c -> Component.translatable(c.title())).orElse(Component.empty());
    }
}
