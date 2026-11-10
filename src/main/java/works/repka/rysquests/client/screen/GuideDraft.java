package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.PagesSection;

/** A guide or a section of pages while it is being edited: the texts themselves, next to the keys they are saved under. */
final class GuideDraft {
    /** @param literal the book holds the text itself rather than a lang key, and that stays so */
    record Text(String key, boolean literal, String value) {
        Text with(String newValue) {
            return new Text(key, literal, newValue);
        }

        String field() {
            return literal ? value : key;
        }
    }

    record Page(String id, Text title, Text text) {
    }

    /** All that can change, for undo and for telling whether anything did. */
    record State(Text title, Optional<ResourceLocation> icon, Text category, List<Page> pages) {
    }

    final ResourceLocation id;
    final boolean section;
    private final Optional<String> stage;
    private final Optional<String> stageTitle;
    private final Optional<String> shortTitle;
    private final Optional<String> subtitle;
    final Map<ResourceLocation, byte[]> images = new LinkedHashMap<>();
    State state;

    private GuideDraft(ResourceLocation id, boolean section, Optional<String> stage, Optional<String> stageTitle, Optional<String> shortTitle, Optional<String> subtitle, State state) {
        this.id = id;
        this.section = section;
        this.stage = stage;
        this.stageTitle = stageTitle;
        this.shortTitle = shortTitle;
        this.subtitle = subtitle;
        this.state = state;
    }

    private static String prefix(ResourceLocation id, boolean section) {
        return (section ? "section." : "guide.") + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** A string of the book is either a lang key or the text itself: a key has no spaces and has a dot. */
    private static Text load(String stored) {
        boolean key = stored.indexOf(' ') < 0 && stored.indexOf('.') > 0;
        return new Text(stored, !key, key ? Component.translatable(stored).getString() : stored);
    }

    private static List<Page> pages(List<Guide.Page> pages) {
        List<Page> out = new ArrayList<>();
        pages.forEach(page -> out.add(new Page(page.id(), load(page.title()), load(page.text()))));
        return out;
    }

    private static Text emptyCategory(ResourceLocation id) {
        return new Text("guide." + id.getNamespace() + ".category.", false, "");
    }

    static GuideDraft of(Guide guide) {
        Text category = guide.category().map(GuideDraft::load).orElse(emptyCategory(guide.id()));
        return new GuideDraft(guide.id(), false, guide.stage(), guide.stageTitle(), Optional.empty(), Optional.empty(),
                new State(load(guide.title()), guide.icon(), category, pages(guide.pages())));
    }

    static GuideDraft of(PagesSection section) {
        return new GuideDraft(section.id(), true, Optional.empty(), Optional.empty(), section.shortTitle(), section.subtitle(),
                new State(load(section.title()), section.icon(), emptyCategory(section.id()), pages(section.pages())));
    }

    /** A new guide starts with one empty page. */
    static GuideDraft newGuide(ResourceLocation id) {
        String prefix = prefix(id, false);
        return new GuideDraft(id, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                new State(new Text(prefix + ".title", false, ""), Optional.empty(), emptyCategory(id), List.of(newPage(id, false, "page_1"))));
    }

    private static Page newPage(ResourceLocation id, boolean section, String pageId) {
        String prefix = prefix(id, section) + "." + pageId;
        return new Page(pageId, new Text(prefix + ".title", false, ""), new Text(prefix + ".text", false, ""));
    }

    /** Adds an empty page after the given one. @return its position */
    int addPage(int after) {
        int number = state.pages.size() + 1;
        while (hasPage("page_" + number)) {
            number++;
        }
        List<Page> pages = new ArrayList<>(state.pages);
        pages.add(after + 1, newPage(id, section, "page_" + number));
        state = new State(state.title, state.icon, state.category, pages);
        return after + 1;
    }

    private boolean hasPage(String pageId) {
        return state.pages.stream().anyMatch(page -> page.id().equals(pageId));
    }

    void removePage(int index) {
        List<Page> pages = new ArrayList<>(state.pages);
        pages.remove(index);
        state = new State(state.title, state.icon, state.category, pages);
    }

    /** @return the page's new position */
    int movePage(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= state.pages.size()) {
            return index;
        }
        List<Page> pages = new ArrayList<>(state.pages);
        pages.add(target, pages.remove(index));
        state = new State(state.title, state.icon, state.category, pages);
        return target;
    }

    void setPage(int index, String title, String text) {
        List<Page> pages = new ArrayList<>(state.pages);
        Page page = pages.get(index);
        pages.set(index, new Page(page.id(), page.title().with(title), page.text().with(text)));
        state = new State(state.title, state.icon, state.category, pages);
    }

    void setTitle(String title) {
        state = new State(state.title.with(title), state.icon, state.category, state.pages);
    }

    void setCategory(String category) {
        String slug = category.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        Text next = state.category.literal() ? state.category.with(category) : new Text("guide." + id.getNamespace() + ".category." + slug, false, category);
        state = new State(state.title, state.icon, next, state.pages);
    }

    void setIcon(ResourceLocation icon) {
        state = new State(state.title, Optional.of(icon), state.category, state.pages);
    }

    private List<Guide.Page> savedPages() {
        List<Guide.Page> out = new ArrayList<>();
        state.pages.forEach(page -> out.add(new Guide.Page(page.id(), page.title().field(), page.text().field())));
        return out;
    }

    Guide toGuide() {
        Optional<String> category = state.category.value().isBlank() ? Optional.empty() : Optional.of(state.category.field());
        return new Guide(id, state.title.field(), state.icon, category, stage, stageTitle, savedPages());
    }

    PagesSection toSection() {
        return new PagesSection(id, state.title.field(), shortTitle, subtitle, state.icon, savedPages());
    }

    /** The strings to put into the lang file of the author's language. */
    Map<String, String> texts() {
        Map<String, String> texts = new HashMap<>();
        put(texts, state.title);
        if (!state.category.value().isBlank()) {
            put(texts, state.category);
        }
        for (Page page : state.pages) {
            put(texts, page.title());
            put(texts, page.text());
        }
        return texts;
    }

    private static void put(Map<String, String> texts, Text text) {
        if (!text.literal()) {
            texts.put(text.key(), text.value());
        }
    }

    /** The file's path inside its namespace, as shown to the author. */
    String filePath() {
        return (section ? "sections/" : "guides/") + id.getPath() + ".json";
    }
}
