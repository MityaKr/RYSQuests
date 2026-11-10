package works.repka.rysquests.client.markup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.markup.Markup.Paragraph;
import works.repka.rysquests.client.markup.Markup.Span;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.GuideRef;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;

/** What a guide page is about, gathered from its text: nobody has to list it by hand. */
public final class GuideTopics {
    private GuideTopics() {
    }

    /** The items a text inserts, in the order they first appear. */
    public static List<ResourceLocation> items(String text) {
        return List.copyOf(targets(text, "item", "recipe"));
    }

    private static Set<ResourceLocation> targets(String text, String... kinds) {
        Set<ResourceLocation> found = new LinkedHashSet<>();
        for (Paragraph paragraph : Markup.parse(text)) {
            for (Span span : paragraph.spans()) {
                if (List.of(kinds).contains(span.kind())) {
                    ResourceLocation id = ResourceLocation.tryParse(span.target());
                    if (id != null) {
                        found.add(id);
                    }
                }
            }
        }
        return found;
    }

    /**
     * The quests on a page's topic, closest first: those that point at this page, at the whole guide, those the text
     * names, and those that ask for an item of the page.
     */
    public static List<Quest> quests(QuestBook book, Guide guide, Guide.Page page, String text) {
        Set<Quest> found = new LinkedHashSet<>();
        GuideRef exact = new GuideRef(guide.id(), java.util.Optional.of(page.id()));
        GuideRef whole = new GuideRef(guide.id(), java.util.Optional.empty());
        book.quests().values().stream().filter(quest -> quest.guide().filter(exact::equals).isPresent()).forEach(found::add);
        book.quests().values().stream().filter(quest -> quest.guide().filter(whole::equals).isPresent()).forEach(found::add);
        for (ResourceLocation id : targets(text, "quest")) {
            Quest quest = book.quests().get(id);
            if (quest != null) {
                found.add(quest);
            }
        }
        for (ResourceLocation item : items(text)) {
            for (ResourceLocation id : book.byItem().getOrDefault(item, List.of())) {
                found.add(book.quests().get(id));
            }
        }
        return new ArrayList<>(found);
    }
}
